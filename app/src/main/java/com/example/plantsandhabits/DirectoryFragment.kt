package com.example.plantsandhabits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DirectoryFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoryAdapter

    //тестовые данные
    private val testCategories = listOf(
        Category(1, "Комнатные растения"),
        Category(2, "Садовые цветы"),
        Category(3, "Овощные культуры"),
        Category(4, "Лекарственные травы"),
        Category(5, "Кактусы и суккуленты"),
        Category(6, "Орхидеи"),
        Category(7, "Пальмы"),
        Category(8, "Бонсай"),
        Category(9, "Ядовитые"),
        Category(10, "Ягоды")
    )
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_directory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        setupRecyclerView(view)
        setupAddPlantButton(view)
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.rvCategories)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = CategoryAdapter(testCategories) { category ->
            onCategoryClick(category)
        }
        recyclerView.adapter = adapter
    }

    private fun setupAddPlantButton(view: View) {
        view.findViewById<View>(R.id.btnAddPlant).setOnClickListener {
            showMessage("wip")
        }
    }

    private fun onCategoryClick(category: Category) {
        (requireActivity() as MainActivity).showPlantsListFragment(category.name)
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
}