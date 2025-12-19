package com.example.plantsandhabits

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EditPlantFragment : Fragment() {

    private lateinit var database: AppDatabase
    private lateinit var userPlantWithDetails: UserPlantWithDetails
    private var selectedImageUri: Uri? = null
    private var currentImagePath: String? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                loadImageFromUri(uri)
            }
        }
    }

    companion object {
        fun newInstance(userPlantWithDetails: UserPlantWithDetails): EditPlantFragment {
            val fragment = EditPlantFragment()
            val args = Bundle()
            args.putParcelable("userPlant", userPlantWithDetails)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = (requireActivity() as DatabaseProvider).database
        userPlantWithDetails = arguments?.getParcelable("userPlant")
            ?: throw IllegalStateException("UserPlantWithDetails is required")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.edit_plant_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Добавляем отступы для статус-бара
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop + systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        val btnAddPhoto = view.findViewById<ImageView>(R.id.btnAddPhoto)
        val etPlantName = view.findViewById<TextInputEditText>(R.id.etPlantName)
        val btnSavePlant = view.findViewById<Button>(R.id.btnSavePlant)

        // Заполняем поля текущими данными
        val currentName = userPlantWithDetails.customName ?: userPlantWithDetails.plant.name
        etPlantName.setText(currentName)

        // Загружаем текущее изображение, если оно есть
        currentImagePath = userPlantWithDetails.customImage
        if (!currentImagePath.isNullOrEmpty()) {
            loadImageFromFile(currentImagePath!!, btnAddPhoto)
        }

        btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        btnAddPhoto.setOnClickListener {
            openImagePicker()
        }

        btnSavePlant.setOnClickListener {
            saveChanges(view)
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            view?.findViewById<ImageView>(R.id.btnAddPhoto)?.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("EditPlantFragment", "Error loading image", e)
            Toast.makeText(requireContext(), "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImageFromFile(imagePath: String, imageView: ImageView) {
        try {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                imageView.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.e("EditPlantFragment", "Error loading image from file", e)
        }
    }

    private fun saveChanges(view: View) {
        val etPlantName = view.findViewById<TextInputEditText>(R.id.etPlantName)
        val plantNameContainer = view.findViewById<TextInputLayout>(R.id.plantNameContainer)

        val plantName = etPlantName.text?.toString()?.trim()

        // Валидация
        if (plantName.isNullOrEmpty()) {
            plantNameContainer.error = "Поле обязательно для заполнения"
            Toast.makeText(requireContext(), "Заполните имя растения", Toast.LENGTH_SHORT).show()
            return
        } else {
            plantNameContainer.error = null
        }

        // Сохранение изменений
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Сохраняем новое изображение, если оно выбрано
                var savedImagePath: String? = currentImagePath
                if (selectedImageUri != null) {
                    // Удаляем старое изображение, если оно было
                    if (!currentImagePath.isNullOrEmpty()) {
                        try {
                            val oldFile = File(currentImagePath!!)
                            if (oldFile.exists()) {
                                oldFile.delete()
                            }
                        } catch (e: Exception) {
                            Log.e("EditPlantFragment", "Error deleting old image", e)
                        }
                    }
                    // Сохраняем новое изображение
                    savedImagePath = saveImageToFile(selectedImageUri!!)
                }

                // Обновляем UserPlant в БД
                val updatedRows = withContext(Dispatchers.IO) {
                    database.plantDao().updateUserPlant(
                        userPlantId = userPlantWithDetails.userPlantId,
                        customName = plantName,
                        customImage = savedImagePath
                    )
                }

                if (updatedRows > 0) {
                    Toast.makeText(requireContext(), "Изменения сохранены", Toast.LENGTH_SHORT).show()
                    Log.d("EditPlantFragment", "Plant updated successfully")
                    requireActivity().onBackPressed()
                } else {
                    Toast.makeText(requireContext(), "Ошибка при сохранении изменений", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("EditPlantFragment", "Error saving changes", e)
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveImageToFile(uri: Uri): String? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Создаем директорию для изображений, если её нет
            val imagesDir = File(requireContext().filesDir, "plant_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            // Создаем уникальное имя файла
            val fileName = "plant_${System.currentTimeMillis()}.jpg"
            val imageFile = File(imagesDir, fileName)

            // Сохраняем bitmap в файл
            val outputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()

            imageFile.absolutePath
        } catch (e: IOException) {
            Log.e("EditPlantFragment", "Error saving image", e)
            null
        }
    }
}

