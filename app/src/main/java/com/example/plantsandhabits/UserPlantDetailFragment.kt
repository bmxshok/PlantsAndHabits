package com.example.plantsandhabits

import android.os.Bundle
import  android.app.AlertDialog
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        // Добавляем отступы для статус-бара
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop + systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

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

        // Кнопка "Удалить"
        view.findViewById<Button>(R.id.btnDeletePlant).setOnClickListener {
            showDeleteConfirmationDialog()
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

    override fun onResume() {
        super.onResume()
        // Обновляем данные после возврата из экрана редактирования
        refreshPlantData()
    }

    private fun refreshPlantData() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Загружаем обновленные данные из БД
                val updatedPlant = withContext(Dispatchers.IO) {
                    database.plantDao().getUserPlantsWithDetails()
                        .find { it.userPlantId == userPlantWithDetails.userPlantId }
                }

                if (updatedPlant != null) {
                    userPlantWithDetails = updatedPlant
                    view?.let { setupPlantData(it) }
                }
            } catch (e: Exception) {
                Log.e("UserPlantDetail", "Error refreshing plant data", e)
            }
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

        // Загружаем изображение растения
        loadPlantImage(view)

        // Загрузка фотографий (пока заглушка)
        loadGalleryPhotos()
    }

    private fun loadPlantImage(view: View) {
        val imageView = view.findViewById<ImageView>(R.id.imgPlant)
        
        try {
            // Сначала пытаемся загрузить кастомное изображение
            if (!userPlantWithDetails.customImage.isNullOrEmpty()) {
                val imageFile = java.io.File(userPlantWithDetails.customImage)
                if (imageFile.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                    imageView.setImageBitmap(bitmap)
                    Log.d("UserPlantDetail", "Loaded custom image from: ${userPlantWithDetails.customImage}")
                    return
                } else {
                    Log.w("UserPlantDetail", "Custom image file not found: ${userPlantWithDetails.customImage}")
                }
            }
            
            // Если кастомного изображения нет, загружаем стандартное
            loadDefaultImage(view, userPlantWithDetails.plant.imageResName)
        } catch (e: Exception) {
            Log.e("UserPlantDetail", "Error loading plant image", e)
            imageView.setImageResource(R.drawable.sample_category)
        }
    }

    private fun loadDefaultImage(view: View, imageResName: String) {
        val imageView = view.findViewById<ImageView>(R.id.imgPlant)
        try {
            val drawableId = ResourceHelper.getDrawableId(requireContext(), imageResName)
            if (drawableId != 0) {
                imageView.setImageResource(drawableId)
                Log.d("UserPlantDetail", "Loaded default image: $imageResName")
            } else {
                imageView.setImageResource(R.drawable.sample_category)
                Log.w("UserPlantDetail", "Image not found: $imageResName, using default")
            }
        } catch (e: Exception) {
            Log.e("UserPlantDetail", "Error loading default image", e)
            imageView.setImageResource(R.drawable.sample_category)
        }
    }

    private fun showDeleteConfirmationDialog() {
        val plantName = userPlantWithDetails.customName ?: userPlantWithDetails.plant.name

        AlertDialog.Builder(requireContext())
            .setTitle("Удаление растения")
            .setMessage("Вы уверены, что хотите удалить \"$plantName\" из своего сада?")
            .setPositiveButton("Да, удалить") { dialog, _ ->
                deleteUserPlant()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun deleteUserPlant() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Удаляем КОНКРЕТНОЕ растение по userPlantId
                val deletedRows = withContext(Dispatchers.IO) {
                    database.plantDao().removeUserPlantById(userPlantWithDetails.userPlantId)
                }

                if(deletedRows > 0) {
                    showMessage("Растение удалено из вашего сада")
                    Log.d("UserPlantDetail", "Deleted user plant with id: ${userPlantWithDetails.userPlantId}")

                    // Закрываем экран и возвращаемся назад
                    requireActivity().finish()

                } else {
                    showMessage("Не удалось удалить растение")
                    Log.e("UserPlantDetail", "Failed to delete user plant")
                }

            } catch (e: Exception) {
                showMessage("Ошибка при удалении: ${e.message}")
                Log.e("UserPlantDetail", "Error deleting user plant", e)
            }
        }
    }

    private fun editUserPlant() {
        val intent = Intent(requireActivity(), NoMenuActivity::class.java).apply {
            putExtra("screen_type", "edit_plant_form")
            putExtra("user_plant", userPlantWithDetails)
        }
        startActivity(intent)
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
        return UserPlantWithDetails(plant, "Мое растение", null, "Тестовый тип", System.currentTimeMillis(), 1)
    }
}