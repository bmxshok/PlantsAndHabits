package com.example.plantsandhabits

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList

class PlantListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlantAdapter
    private val plants = mutableListOf<Plant>()

    companion object {
        fun newInstance(categoryName: String, plantList: List<Plant>): PlantListFragment {
            val fragment = PlantListFragment()
            val args = Bundle()
            args.putString("category_name", categoryName)
            args.putParcelableArrayList("plants", ArrayList(plantList))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Получаем растения из аргументов
        arguments?.getParcelableArrayList<Plant>("plants")?.let {
            plants.clear()
            plants.addAll(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plant_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val categoryName = arguments?.getString("category_name") ?: "Растения"
        view.findViewById<TextView>(R.id.tvTitle).text = categoryName
        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressed()
        }

        setupRecyclerView(view)
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.rvPlants)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = PlantAdapter(plants) { plant ->
            onPlantClick(plant)
        }
        recyclerView.adapter = adapter
    }

    private fun onPlantClick(plant: Plant) {
        val intent = Intent(requireActivity(), NoMenuActivity::class.java)
        intent.putExtra("plant", plant)
        intent.putExtra("from_garden", true)
        startActivity(intent)
    }
}