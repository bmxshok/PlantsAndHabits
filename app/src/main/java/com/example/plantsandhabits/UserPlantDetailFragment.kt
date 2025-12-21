package com.example.plantsandhabits

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class UserPlantDetailFragment : Fragment() {

    private lateinit var userPlantWithDetails: UserPlantWithDetails
    private lateinit var database: AppDatabase
    private lateinit var photoAdapter: PlantPhotoAdapter
    private var currentPhotoPath: String? = null

    companion object {
        private const val REQUEST_CODE_CAMERA = 1002

        fun newInstance(userPlantWithDetails: UserPlantWithDetails): UserPlantDetailFragment {
            val fragment = UserPlantDetailFragment()
            val args = Bundle()
            args.putParcelable("userPlant", userPlantWithDetails)
            fragment.arguments = args
            return fragment
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            handleImageUri(it)
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoPath != null) {
            val file = File(currentPhotoPath!!)
            if (file.exists()) {
                savePhotoToDatabase(file.absolutePath)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            showMessage("Разрешение на использование камеры необходимо для добавления фото")
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

        // Инициализация RecyclerView для фотогалереи
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvGallery)
        recyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        
        // Оптимизация производительности RecyclerView
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(20) // Кэшируем больше элементов для плавной прокрутки
        
        photoAdapter = PlantPhotoAdapter(mutableListOf()) { photo ->
            showDeletePhotoDialog(photo)
        }
        recyclerView.adapter = photoAdapter

        // Кнопка "Добавить фото"
        view.findViewById<Button>(R.id.btnAddPhoto).setOnClickListener {
            addPhotoToPlant()
        }

        // Кнопка "Создать таймлапс"
        val btnCreateTimelapse = view.findViewById<Button>(R.id.btnCreateTimelapse)
        btnCreateTimelapse.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val photoCount = withContext(Dispatchers.IO) {
                    database.plantDao().getPlantPhotos(userPlantWithDetails.userPlantId.toInt()).size
                }
                
                if (photoCount >= 10) {
                    createTimelapse()
                } else {
                    val remaining = 10 - photoCount
                    val message = if (photoCount == 0) {
                        "Для создания таймлапса необходимо минимум 10 фотографий"
                    } else {
                        "Для создания таймлапса необходимо ещё $remaining ${getPhotoWord(remaining)}"
                    }
                    android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
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
        val options = arrayOf("Камера", "Галерея")
        AlertDialog.Builder(requireContext())
            .setTitle("Добавить фото")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        launchCamera()
    }

    private fun launchCamera() {
        try {
            val photoFile = createImageFile()
            currentPhotoPath = photoFile.absolutePath

            val photoURI = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )

            cameraLauncher.launch(photoURI)
        } catch (e: IOException) {
            Log.e("UserPlantDetail", "Error creating image file", e)
            showMessage("Ошибка при создании файла")
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    private fun handleImageUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val savedPath = saveBitmapToFile(bitmap)
            if (savedPath != null) {
                savePhotoToDatabase(savedPath)
            } else {
                showMessage("Ошибка при сохранении фото")
            }
        } catch (e: Exception) {
            Log.e("UserPlantDetail", "Error handling image URI", e)
            showMessage("Ошибка при обработке фото")
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap): String? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)

            val outputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()

            imageFile.absolutePath
        } catch (e: Exception) {
            Log.e("UserPlantDetail", "Error saving bitmap to file", e)
            null
        }
    }

    private fun savePhotoToDatabase(photoPath: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val photoId = withContext(Dispatchers.IO) {
                    database.plantDao().insertPlantPhoto(
                        PlantPhoto(
                            userPlantId = userPlantWithDetails.userPlantId.toInt(),
                            photoPath = photoPath,
                            dateAdded = System.currentTimeMillis()
                        )
                    )
                }
                Log.d("UserPlantDetail", "Photo saved with id: $photoId")
                loadGalleryPhotos()
                showMessage("Фото добавлено")
            } catch (e: Exception) {
                Log.e("UserPlantDetail", "Error saving photo to database", e)
                showMessage("Ошибка при сохранении фото")
            }
        }
    }

    private fun createTimelapse() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Загружаем все фото
                val photos = withContext(Dispatchers.IO) {
                    database.plantDao().getPlantPhotos(userPlantWithDetails.userPlantId.toInt())
                }

                if (photos.size < 10) {
                    showMessage("Для создания таймлапса необходимо минимум 10 фотографий")
                    return@launch
                }

                // Сортируем фото по дате
                val sortedPhotos = photos.sortedBy { it.dateAdded }
                val photoPaths = sortedPhotos.map { it.photoPath }

                // Создаем диалог прогресса
                val progressDialog = android.app.ProgressDialog(requireContext()).apply {
                    setTitle("Создание таймлапса")
                    setMessage("Обработка фотографий...")
                    setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
                    max = photoPaths.size
                    progress = 0
                    setCancelable(false)
                    show()
                }

                // Создаем путь для выходного файла (сначала во временную директорию)
                val tempDir = File(requireContext().getExternalFilesDir(null), "timelapses")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                val timestamp = System.currentTimeMillis()
                val plantName = (userPlantWithDetails.customName ?: userPlantWithDetails.plant.name)
                    .replace(" ", "_")
                    .replace(Regex("[^a-zA-Z0-9_]"), "")
                val tempOutputPath = File(tempDir, "timelapse_${plantName}_$timestamp.mp4").absolutePath

                // Создаем таймлапс
                val success = withContext(Dispatchers.IO) {
                    TimelapseCreator.createTimelapse(
                        requireContext(),
                        photoPaths,
                        tempOutputPath
                    ) { current, total ->
                        CoroutineScope(Dispatchers.Main).launch {
                            progressDialog.progress = current
                            progressDialog.setMessage("Обработка фотографий: $current из $total")
                        }
                    }
                }

                progressDialog.dismiss()

                if (success) {
                    val tempFile = File(tempOutputPath)
                    if (tempFile.exists() && tempFile.length() > 0) {
                        // Сохраняем видео в галерею
                        progressDialog.setMessage("Сохранение в галерею...")
                        progressDialog.show()
                        
                        val galleryUri = withContext(Dispatchers.IO) {
                            saveVideoToGallery(tempOutputPath, plantName, timestamp)
                        }
                        
                        progressDialog.dismiss()
                        
                        if (galleryUri != null) {
                            val fileSizeKB = tempFile.length() / 1024
                            val fileSizeMB = if (fileSizeKB > 1024) "${fileSizeKB / 1024} MB" else "$fileSizeKB KB"
                            
                            AlertDialog.Builder(requireContext())
                                .setTitle("Таймлапс создан!")
                                .setMessage("Видео сохранено в галерею:\n${tempFile.name}\n\nРазмер: $fileSizeMB")
                                .setPositiveButton("Открыть") { _, _ ->
                                    openVideoFromUri(galleryUri)
                                }
                                .setNeutralButton("Поделиться") { _, _ ->
                                    shareVideoFromUri(galleryUri)
                                }
                                .setNegativeButton("OK", null)
                                .show()
                        } else {
                            // Если не удалось сохранить в галерею, показываем файл из временной директории
                            val fileSizeKB = tempFile.length() / 1024
                            val fileSizeMB = if (fileSizeKB > 1024) "${fileSizeKB / 1024} MB" else "$fileSizeKB KB"
                            
                            AlertDialog.Builder(requireContext())
                                .setTitle("Таймлапс создан!")
                                .setMessage("Видео сохранено:\n${tempFile.name}\n\nРазмер: $fileSizeMB\n\nНе удалось сохранить в галерею")
                                .setPositiveButton("Открыть") { _, _ ->
                                    openVideo(tempOutputPath)
                                }
                                .setNeutralButton("Поделиться") { _, _ ->
                                    shareVideo(tempOutputPath)
                                }
                                .setNegativeButton("OK", null)
                                .show()
                        }
                    } else {
                        showMessage("Ошибка: файл видео не был создан")
                    }
                } else {
                    showMessage("Ошибка при создании таймлапса")
                }
            } catch (e: Exception) {
                Log.e("UserPlantDetail", "Error creating timelapse", e)
                showMessage("Ошибка: ${e.message}")
            }
        }
    }

    private fun openVideo(videoPath: String) {
        try {
            val videoFile = File(videoPath)
            if (!videoFile.exists()) {
                showMessage("Файл видео не найден")
                return
            }

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                videoFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Открыть видео"))
        } catch (e: Exception) {
            Log.e("UserPlantDetail", "Error opening video", e)
            showMessage("Ошибка при открытии видео")
        }
    }

    private fun saveVideoToGallery(videoPath: String, plantName: String, timestamp: Long): Uri? {
        return try {
            val videoFile = File(videoPath)
            if (!videoFile.exists()) {
                Log.e("UserPlantDetail", "Video file not found: $videoPath")
                return null
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "timelapse_${plantName}_$timestamp.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PlantsAndHabits")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val resolver = requireContext().contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    videoFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                Log.d("UserPlantDetail", "Video saved to gallery: $uri")
                uri
            } else {
                Log.e("UserPlantDetail", "Failed to create MediaStore entry")
                null
            }
        } catch (e: Exception) {
            Log.e("UserPlantDetail", "Error saving video to gallery", e)
            null
        }
    }

    private fun openVideoFromUri(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Открыть видео"))
        } catch (e: Exception) {
            Log.e("UserPlantDetail", "Error opening video from URI", e)
            showMessage("Ошибка при открытии видео")
        }
    }

    private fun shareVideoFromUri(uri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Поделиться таймлапсом"))
        } catch (e: Exception) {
            Log.e("UserPlantDetail", "Error sharing video from URI", e)
            showMessage("Ошибка при открытии видео")
        }
    }

    private fun shareVideo(videoPath: String) {
        try {
            val videoFile = File(videoPath)
            if (!videoFile.exists()) {
                showMessage("Файл видео не найден")
                return
            }

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                videoFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Поделиться таймлапсом"))
        } catch (e: Exception) {
            Log.e("UserPlantDetail", "Error sharing video", e)
            showMessage("Ошибка при открытии видео")
        }
    }

    private fun loadGalleryPhotos() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val photos = withContext(Dispatchers.IO) {
                    database.plantDao().getPlantPhotos(userPlantWithDetails.userPlantId.toInt())
                }

                val emptyText = view?.findViewById<TextView>(R.id.tvEmptyGallery)
                val recyclerView = view?.findViewById<RecyclerView>(R.id.rvGallery)

                if (photos.isEmpty()) {
                    emptyText?.visibility = View.VISIBLE
                    recyclerView?.visibility = View.GONE
                } else {
                    emptyText?.visibility = View.GONE
                    recyclerView?.visibility = View.VISIBLE
                    photoAdapter.updatePhotos(photos)
                }
                
                // Обновляем состояние кнопки создания таймлапса
                updateTimelapseButtonState(photos.size)
            } catch (e: Exception) {
                Log.e("UserPlantDetail", "Error loading gallery photos", e)
            }
        }
    }
    
    private fun updateTimelapseButtonState(photoCount: Int) {
        val btnCreateTimelapse = view?.findViewById<Button>(R.id.btnCreateTimelapse)
        
        val minPhotos = 10
        val hasEnoughPhotos = photoCount >= minPhotos
        
        // Не отключаем кнопку полностью, только визуально, чтобы клики работали
        btnCreateTimelapse?.alpha = if (hasEnoughPhotos) 1.0f else 0.5f
    }
    
    private fun getPhotoWord(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "фотография"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "фотографии"
            else -> "фотографий"
        }
    }

    private fun showDeletePhotoDialog(photo: PlantPhoto) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удаление фото")
            .setMessage("Вы уверены, что хотите удалить это фото?")
            .setPositiveButton("Да, удалить") { _, _ ->
                deletePhoto(photo)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deletePhoto(photo: PlantPhoto) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Удаляем файл
                val photoFile = File(photo.photoPath)
                if (photoFile.exists()) {
                    photoFile.delete()
                }

                // Удаляем из БД
                val deletedRows = withContext(Dispatchers.IO) {
                    database.plantDao().deletePlantPhoto(photo.id)
                }

                if (deletedRows > 0) {
                    loadGalleryPhotos()
                    showMessage("Фото удалено")
                } else {
                    showMessage("Не удалось удалить фото")
                }
            } catch (e: Exception) {
                Log.e("UserPlantDetail", "Error deleting photo", e)
                showMessage("Ошибка при удалении фото")
            }
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