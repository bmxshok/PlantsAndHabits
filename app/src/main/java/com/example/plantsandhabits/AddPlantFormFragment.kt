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

class AddPlantFormFragment : Fragment() {

    private lateinit var database: AppDatabase
    private var selectedImageUri: Uri? = null
    private var selectedImagePath: String? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = (requireActivity() as DatabaseProvider).database
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.add_plant_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Добавляем отступы для статус-бара (без фиксации, чтобы клавиатура могла работать)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBars.top,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        val btnAddPhoto = view.findViewById<ImageView>(R.id.btnAddPhoto)
        val etPlantType = view.findViewById<TextInputEditText>(R.id.etPlantType)
        val etPlantName = view.findViewById<TextInputEditText>(R.id.etPlantName)
        val btnSavePlant = view.findViewById<Button>(R.id.btnSavePlant)
        val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollView)

        btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        btnAddPhoto.setOnClickListener {
            openImagePicker()
        }

        btnSavePlant.setOnClickListener {
            savePlant(view)
        }
        
        // Настройка перехода между полями через клавиатуру
        etPlantType.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                etPlantName.requestFocus()
                true
            } else {
                false
            }
        }
        
        etPlantName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                etPlantName.clearFocus()
                // Скрываем клавиатуру
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(etPlantName.windowToken, 0)
                true
            } else {
                false
            }
        }

        // Автоматическая прокрутка к полю при фокусе, чтобы клавиатура не перекрывала
        // Используем простой и надёжный метод requestRectangleOnScreen
        etPlantType.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Небольшая задержка для того, чтобы клавиатура успела открыться
                scrollView.postDelayed({
                    val scrollBounds = android.graphics.Rect()
                    scrollView.getHitRect(scrollBounds)
                    if (!v.getLocalVisibleRect(scrollBounds)) {
                        // Прокручиваем так, чтобы поле было видно с отступом сверху
                        val location = IntArray(2)
                        v.getLocationInWindow(location)
                        val scrollY = location[1] - 200 // Отступ сверху
                        if (scrollY > 0) {
                            scrollView.smoothScrollTo(0, scrollY)
                        }
                    }
                }, 300)
            }
        }

        etPlantName.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Небольшая задержка для того, чтобы клавиатура успела открыться
                scrollView.postDelayed({
                    val scrollBounds = android.graphics.Rect()
                    scrollView.getHitRect(scrollBounds)
                    if (!v.getLocalVisibleRect(scrollBounds)) {
                        // Прокручиваем так, чтобы поле было видно с отступом сверху
                        val location = IntArray(2)
                        v.getLocationInWindow(location)
                        val scrollY = location[1] - 200 // Отступ сверху
                        if (scrollY > 0) {
                            scrollView.smoothScrollTo(0, scrollY)
                        }
                    }
                }, 300)
            }
        }
        
        // Дополнительная обработка через ViewTreeObserver для отслеживания изменений клавиатуры
        val rootView = view.rootView
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.height
            val keypadHeight = screenHeight - rect.bottom
            
            if (keypadHeight > screenHeight * 0.15) { // Клавиатура открыта
                val focusedView = requireActivity().currentFocus
                if (focusedView != null && (focusedView == etPlantType || focusedView == etPlantName)) {
                    scrollView.postDelayed({
                        val location = IntArray(2)
                        focusedView.getLocationInWindow(location)
                        val scrollY = location[1] - 200 // Отступ сверху
                        if (scrollY > 0) {
                            scrollView.smoothScrollTo(0, scrollY)
                        }
                    }, 100)
                }
            }
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
            Log.e("AddPlantFormFragment", "Error loading image", e)
            Toast.makeText(requireContext(), "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savePlant(view: View) {
        val etPlantType = view.findViewById<TextInputEditText>(R.id.etPlantType)
        val etPlantName = view.findViewById<TextInputEditText>(R.id.etPlantName)
        val plantTypeContainer = view.findViewById<TextInputLayout>(R.id.plantTypeContainer)
        val plantNameContainer = view.findViewById<TextInputLayout>(R.id.plantNameContainer)

        val plantType = etPlantType.text?.toString()?.trim()
        val plantName = etPlantName.text?.toString()?.trim()

        // Валидация
        var isValid = true

        if (plantType.isNullOrEmpty()) {
            plantTypeContainer.error = "Поле обязательно для заполнения"
            isValid = false
        } else {
            plantTypeContainer.error = null
        }

        if (plantName.isNullOrEmpty()) {
            plantNameContainer.error = "Поле обязательно для заполнения"
            isValid = false
        } else {
            plantNameContainer.error = null
        }

        if (!isValid) {
            Toast.makeText(requireContext(), "Заполните все обязательные поля", Toast.LENGTH_SHORT).show()
            return
        }

        // Сохранение растения
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Сохраняем изображение, если оно выбрано
                var savedImagePath: String? = null
                if (selectedImageUri != null) {
                    savedImagePath = saveImageToFile(selectedImageUri!!)
                }

                // Получаем или создаем фиктивный Plant для ручных растений
                val placeholderPlant = withContext(Dispatchers.IO) {
                    var plant = database.plantDao().getManualPlantPlaceholder()
                    if (plant == null) {
                        // Если фиктивного Plant нет, создаем его (используем первую категорию)
                        val categories = database.plantDao().getAllCategories()
                        if (categories.isEmpty()) {
                            throw IllegalStateException("Нет доступных категорий")
                        }
                        val plantId = database.plantDao().insertPlant(
                            Plant(
                                categoryId = categories.first().id,
                                name = "_MANUAL_PLACEHOLDER_",
                                scientificName = null,
                                description = "",
                                careTips = "",
                                imageResName = "sample_category"
                            )
                        )
                        plant = database.plantDao().getPlantById(plantId.toInt())
                            ?: throw IllegalStateException("Не удалось создать фиктивный Plant")
                    }
                    plant
                }

                // Создаем UserPlant с фиктивным Plant
                val userPlantId = withContext(Dispatchers.IO) {
                    database.plantDao().insertUserPlant(
                        UserPlant(
                            plantId = placeholderPlant.id,
                            customName = plantName,
                            customImage = savedImagePath,
                            plantType = plantType
                        )
                    )
                }

                if (userPlantId > 0) {
                    Toast.makeText(requireContext(), "Растение успешно добавлено", Toast.LENGTH_SHORT).show()
                    Log.d("AddPlantFormFragment", "Plant saved with id: $userPlantId")
                    // Переходим на экран "Мой сад" вместо возврата назад
                    navigateToMyGarden()
                } else {
                    Toast.makeText(requireContext(), "Ошибка при сохранении растения", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("AddPlantFormFragment", "Error saving plant", e)
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
            Log.e("AddPlantFormFragment", "Error saving image", e)
            null
        }
    }
    
    /**
     * Переходит на экран "Мой сад" в MainActivity
     */
    private fun navigateToMyGarden() {
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("show_my_garden", true)
        }
        startActivity(intent)
        requireActivity().finish()
    }
}

