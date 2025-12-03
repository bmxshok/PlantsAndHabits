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

        Log.d("PlantDetailFragment", "Data setup for: ${plant.name}")
    }

    /*private fun checkPlantStatus() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val isInGarden = withContext(Dispatchers.IO) {
                    database.plantDao().isPlantInGarden(plant.id) > 0
                }

                val button = view?.findViewById<Button>(R.id.btnAddToGarden)

                Log.d("PlantDetailFragment", "Plant ${plant.name} in garden: $isInGarden")

                if (isInGarden) {
                    button?.text = "Уже в саду"
                    button?.isEnabled = false
                    // Можно сделать серый фон
                    // button?.background = context?.getDrawable(R.drawable.bg_gray_rounded)
                } else {
                    button?.text = "Добавить в сад"
                    button?.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e("PlantDetailFragment", "Error checking plant status", e)
            }
        }
    }*/

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

                    // Логируем все растения в саду для проверки
                    val allUserPlants = withContext(Dispatchers.IO) {
                        database.plantDao().getUserPlantsWithDetails()
                    }
                    Log.d("PlantDetailFragment", "Now have ${allUserPlants.size} plants in garden:")
                    allUserPlants.forEach {
                        Log.d("PlantDetailFragment", " - ${it.plant.name} (id: ${it.plant.id})")
                    }

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