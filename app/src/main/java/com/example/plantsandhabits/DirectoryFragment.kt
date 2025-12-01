package com.example.plantsandhabits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DirectoryFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoryAdapter
    private val categories = mutableListOf<Category>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_directory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Настройка кнопки "Назад"
        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressed()
        }

        // Настройка RecyclerView
        recyclerView = view.findViewById(R.id.rvCategories)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Создаем адаптер с пустым списком
        adapter = CategoryAdapter(categories) { category ->
            onCategoryClick(category)
        }
        recyclerView.adapter = adapter

        // Загрузка категорий из БД
        loadCategories()
    }

    private fun loadCategories() {
        CoroutineScope(Dispatchers.Main).launch {
            // Получаем категории из БД в фоновом потоке
            val categoriesList = withContext(Dispatchers.IO) {
                (requireActivity() as MainActivity).database.plantDao().getAllCategories()
            }

            // Обновляем UI в главном потоке
            categories.clear()
            categories.addAll(categoriesList)
            adapter.notifyDataSetChanged()
        }
    }

    private fun onCategoryClick(category: Category) {
        CoroutineScope(Dispatchers.Main).launch {
            // Получаем растения этой категории из БД
            val plants = withContext(Dispatchers.IO) {
                (requireActivity() as MainActivity).database.plantDao().getPlantsByCategory(category.id)
            }

            // Переходим к списку растений
            (requireActivity() as MainActivity).showPlantsListFragment(category.name, plants)
        }
    }
}