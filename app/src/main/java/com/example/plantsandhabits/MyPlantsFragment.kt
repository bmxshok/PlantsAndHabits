package com.example.plantsandhabits

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyPlantsFragment : Fragment() {

    private lateinit var database: AppDatabase
    private  lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserPlantAdapter
    private val userPlants = mutableListOf<UserPlantWithDetails>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = (requireActivity() as DatabaseProvider).database
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_plants, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnGoToDirectory).setOnClickListener {
            (requireActivity() as MainActivity).showDirectoryFragment()
        }

        setupRecyclerView(view)
        loadUserPlants()
        Log.d("MyPlantsFragment", "Fragment created, loading user plants...")
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.rvPlants)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = UserPlantAdapter(userPlants) { userPlant ->
            onUserPlantClick(userPlant)
        }
        recyclerView.adapter = adapter
    }

    private fun loadUserPlants() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val plants = withContext(Dispatchers.IO) {
                    database.plantDao().getUserPlantsWithDetails()
                }
                Log.d("MyPlantsFragment", "Loaded ${plants.size} user plants")
                userPlants.clear()
                userPlants.addAll(plants)
                adapter.notifyDataSetChanged()
                if(plants.isEmpty()) {
                    Log.d("MyPlantsFragment", "No plants in garden yet")
                }
            } catch (e: Exception) {
                Log.e("MyPlantsFragment", "Error loading user plants", e)
            }
        }
    }

    private fun onUserPlantClick(userPlant: UserPlantWithDetails) {
        Log.d("MyPlantsFragment", "Opening plant: ${userPlant.plant.name}")
        val intent = Intent(requireActivity(), NoMenuActivity::class.java).apply {
            putExtra("screen_type", "user_plant_detail")
            putExtra("user_plant", userPlant)
        }
        startActivity(intent)
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        loadUserPlants()
        Log.d("MyPlantsFragment", "Fragment resumed, refreshing data")
    }
}