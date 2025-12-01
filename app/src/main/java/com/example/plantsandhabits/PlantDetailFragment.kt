package com.example.plantsandhabits

import android.os.Bundle
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
            requireActivity().finish()
        }

        // Получение данных о растении из аргументов
        setupPlantData(view)

        // Кнопка "Добавить в сад"
        view.findViewById<Button>(R.id.btnAddToGarden).setOnClickListener {
            addPlantToGarden()
        }
    }

    private fun setupPlantData(view: View) {
        // Устанавливаем изображение
        // val drawableId = ResourceHelper.getDrawableId(requireContext(), plant.imageResName)
        // view.findViewById<ImageView>(R.id.imgPlant).setImageResource(drawableId)

        view.findViewById<TextView>(R.id.tvPlantNameDetail).text = plant.name
        view.findViewById<TextView>(R.id.tvScientificName).text = plant.scientificName
        view.findViewById<TextView>(R.id.tvDescription).text = plant.description
        view.findViewById<TextView>(R.id.tvCareTips).text = plant.careTips
    }

    private fun addPlantToGarden() {
        CoroutineScope(Dispatchers.Main).launch {
            val isAlreadyAdded = withContext(Dispatchers.IO) {
                (requireActivity() as MainActivity).database.plantDao().isPlantInGarden(plant.id) > 0
            }

            if (isAlreadyAdded) {
                showMessage("Это растение уже в вашем саду!")
            } else {
                withContext(Dispatchers.IO) {
                    (requireActivity() as MainActivity).database.plantDao().insertUserPlant(
                        UserPlant(plantId = plant.id)
                    )
                }
                showMessage("Растение добавлено в ваш сад!")

                // Меняем текст кнопки
                view?.findViewById<Button>(R.id.btnAddToGarden)?.text = "Уже в саду"
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