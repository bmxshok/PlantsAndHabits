package com.example.plantsandhabits

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlantDetailFragment : Fragment() {

    private lateinit var plant: Plant
    private lateinit var database: AppDatabase  // Будем получать БД через requireActivity()

    companion object {
        fun newInstance(plant: Plant): PlantDetailFragment {
            val fragment = PlantDetailFragment()
            val args = Bundle()
            args.putParcelable("plant", plant)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        plant = arguments?.getParcelable("plant") ?: getTestPlant()
        database = (requireActivity() as DatabaseProvider).database  // Получаем БД
        Log.d("PlantDetailFragment", "Opening plant: ${plant.name}, id: ${plant.id}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plant_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Кнопка "Назад"
        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressed()
        }

        // Заполняем данными о растении
        setupPlantData(view)

        // Кнопка "Добавить в сад"
        view.findViewById<Button>(R.id.btnAddToGarden).setOnClickListener {
            addPlantToGarden()
        }
    }

    private fun setupPlantData(view: View) {
        view.findViewById<TextView>(R.id.tvPlantNameDetail).text = plant.name
        view.findViewById<TextView>(R.id.tvScientificName).text = plant.scientificName
        view.findViewById<TextView>(R.id.tvDescription).text = plant.description
        view.findViewById<TextView>(R.id.tvCareTips).text = plant.careTips

        loadPlantImage(view)
        Log.d("PlantDetailFragment", "Data setup for: ${plant.name}")
    }

    private fun loadPlantImage(view: View) {
        try {
            val imageView = view.findViewById<ImageView>(R.id.imgPlant)
            val drawableId = ResourceHelper.getDrawableId(requireContext(), plant.imageResName)
            if(drawableId != 0) {
                imageView.setImageResource(drawableId)
                Log.d("PlantDetailFragment", "Loaded image: ${plant.imageResName}")
            } else {
                imageView.setImageResource(R.drawable.sample_category)
                Log.w("PlantDetailFragment", "Image not found: ${plant.imageResName}, using default")
            }
        } catch (e: Exception) {
            Log.e("PlantDetailFragment", "Error loading plant image", e)
            view.findViewById<ImageView>(R.id.imgPlant).setImageResource(R.drawable.sample_category)
        }
    }

    private fun addPlantToGarden() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Добавляем растение в сад
                val result = withContext(Dispatchers.IO) {
                    database.plantDao().insertUserPlant(
                        UserPlant(plantId = plant.id)
                    )
                }

                if (result > 0) {
                    showMessage("Растение добавлено в ваш сад!")
                    Log.d("PlantDetailFragment", "Plant added to garden, result id: $result")

                    // Перенаправляем на экран "Мой сад"
                    navigateToMyGarden()

                } else {
                    showMessage("Ошибка при добавлении растения")
                    Log.e("PlantDetailFragment", "Failed to add plant to garden")
                }

            } catch (e: Exception) {
                showMessage("Ошибка: ${e.message}")
                Log.e("PlantDetailFragment", "Error adding plant to garden", e)
            }
        }
    }

    private fun navigateToMyGarden() {
        // Закрываем текущую Activity и возвращаемся в MainActivity
        // MainActivity автоматически покажет MyPlantsFragment при нажатии на кнопку "Мой сад"
        val intent = android.content.Intent(requireContext(), MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("show_my_garden", true)
        }
        startActivity(intent)
        requireActivity().finish()
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun getTestPlant(): Plant {
        return Plant(
            id = 1,
            categoryId = 1,
            name = "Тестовое растение",
            scientificName = "Testus plantus",
            description = "Тестовое описание",
            careTips = "Тестовые советы по уходу",
            imageResName = "sample_category"
        )
    }
}