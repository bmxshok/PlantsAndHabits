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

class UserPlantDetailFragment : Fragment() {

    private lateinit var userPlantWithDetails: UserPlantWithDetails
    private lateinit var database: AppDatabase

    companion object {
        fun newInstance(userPlantWithDetails: UserPlantWithDetails): UserPlantDetailFragment {
            val fragment = UserPlantDetailFragment()
            val args = Bundle()
            args.putParcelable("userPlant", userPlantWithDetails)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPlantWithDetails = arguments?.getParcelable("userPlant") ?: getTestUserPlant()
        database = (requireActivity() as DatabaseProvider).database
        Log.d("UserPlantDetail", "Opening user plant: ${userPlantWithDetails.plant.name}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_plant_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Кнопка "Назад"
        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressed()
        }

        // Заполняем данными о растении
        setupPlantData(view)

        // Кнопка "Редактировать"
        view.findViewById<Button>(R.id.btnEditPlant).setOnClickListener {
            editUserPlant()
        }

        // Кнопка "Добавить фото"
        view.findViewById<Button>(R.id.btnAddPhoto).setOnClickListener {
            addPhotoToPlant()
        }

        // Кнопка "Создать таймлапс"
        view.findViewById<Button>(R.id.btnCreateTimelapse).setOnClickListener {
            createTimelapse()
        }
    }

    private fun setupPlantData(view: View) {
        val plant = userPlantWithDetails.plant

        // Используем кастомное название если есть, иначе стандартное
        val displayName = userPlantWithDetails.customName ?: plant.name
        view.findViewById<TextView>(R.id.tvPlantNameDetail).text = displayName

        view.findViewById<TextView>(R.id.tvScientificName).text = plant.scientificName
        view.findViewById<TextView>(R.id.tvDescription).text = plant.description
        view.findViewById<TextView>(R.id.tvCareTips).text = plant.careTips

        // Можно показать дату добавления
        //val addedDate = userPlantWithDetails.addedDate
        //Log.d("UserPlantDetail", "Plant added on: $addedDate")

        // Загрузка фотографий (пока заглушка)
        loadGalleryPhotos()
    }

    private fun editUserPlant() {
        // Здесь будет логика редактирования (изменение названия, фото и т.д.)
        showMessage("Редактирование растения (в разработке)")
        Log.d("UserPlantDetail", "Edit plant: ${userPlantWithDetails.plant.name}")
    }

    private fun addPhotoToPlant() {
        // Здесь будет логика добавления фото
        showMessage("Добавление фото (в разработке)")
        Log.d("UserPlantDetail", "Add photo to plant")
    }

    private fun createTimelapse() {
        // Здесь будет создание таймлапса
        showMessage("Создание таймлапса (в разработке)")
        Log.d("UserPlantDetail", "Create timelapse")
    }

    private fun loadGalleryPhotos() {
        // Здесь будет загрузка фотографий из галереи
        CoroutineScope(Dispatchers.Main).launch {
            // Пока просто показываем/скрываем элементы
            val emptyText = view?.findViewById<TextView>(R.id.tvEmptyGallery)
            val recyclerView = view?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvGallery)

            // TODO: Загрузить реальные фото
            emptyText?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        }
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun getTestUserPlant(): UserPlantWithDetails {
        val plant = Plant(
            id = 1,
            categoryId = 1,
            name = "Тестовое растение",
            scientificName = "Testus plantus",
            description = "Тестовое описание",
            careTips = "Тестовые советы по уходу",
            imageResName = "sample_category"
        )
        return UserPlantWithDetails(plant, "Мое растение", null)
    }
}