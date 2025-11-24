package com.example.plantsandhabits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlantListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlantAdapter

    //тестовые данные растений (одинаковые для всех категорий)
    private val testPlants = listOf(
        Plant(1, "Фикус Бенджамина", "Ficus benjamina"),
        Plant(2, "Спатифиллум", "Spathiphyllum"),
        Plant(3, "Замиокулькас", "Zamioculcas"),
        Plant(4, "Монстера", "Monstera"),
        Plant(5, "Сансевиерия", "Sansevieria")
    )

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
        adapter = PlantAdapter(testPlants) { plant ->
            onPlantClick(plant)
        }
        recyclerView.adapter = adapter
    }

    private fun onPlantClick(plant: Plant) {
        showMessage("Выбрано растение: ${plant.name}")
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance(categoryName: String): PlantListFragment {
            val fragment = PlantListFragment()
            val args = Bundle()
            args.putString("category_name", categoryName)
            fragment.arguments = args
            return fragment
        }
    }
}