package com.example.plantsandhabits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.ImageRequest
import coil.size.Size
import java.io.File

class UserPlantAdapter(
    private val userPlants: List<UserPlantWithDetails>,
    private val onItemClick: (UserPlantWithDetails) -> Unit
) : RecyclerView.Adapter<UserPlantAdapter.UserPlantViewHolder>() {

    inner class UserPlantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgPlant: ImageView = itemView.findViewById(R.id.imgPlant)
        private val imgPlaceholder: ImageView = itemView.findViewById(R.id.imgPlaceholder)
        private val containerPhoto: android.view.ViewGroup = itemView.findViewById(R.id.containerPhoto)
        private val tvPlantName: TextView = itemView.findViewById(R.id.tvPlantName)
        private val tvPlantDescription: TextView = itemView.findViewById(R.id.tvScientificName)

        fun bind(userPlant: UserPlantWithDetails) {
            val displayName = userPlant.customName ?: userPlant.plant.name
            tvPlantName.text = displayName
            // Показываем тип растения вместо латинского названия
            tvPlantDescription.text = userPlant.plantType ?: userPlant.plant.scientificName ?: ""
            
            // Загружаем изображение с помощью Coil
            loadPlantImage(userPlant)

            itemView.setOnClickListener {
                onItemClick(userPlant)
            }
        }

        private fun loadPlantImage(userPlant: UserPlantWithDetails) {
            val hasCustomImage = !userPlant.customImage.isNullOrEmpty() && 
                                 File(userPlant.customImage).exists()
            
            // Проверяем, является ли это ручным растением (созданным вручную)
            val isManualPlant = !userPlant.plantType.isNullOrEmpty() || 
                               userPlant.plant.name == "_MANUAL_PLACEHOLDER_"
            
            if (hasCustomImage) {
                // Загружаем кастомное изображение
                imgPlaceholder.visibility = View.GONE
                imgPlant.visibility = View.VISIBLE
                // Восстанавливаем зелёный фон для фото
                containerPhoto.setBackgroundResource(R.drawable.bg_plant_photo_container)
                
                imgPlant.load(File(userPlant.customImage)) {
                    size(Size(160, 160)) // Загружаем в нужном размере для оптимизации
                    crossfade(true)
                    listener(
                        onError = { _, _ ->
                            // Если ошибка загрузки, показываем заглушку
                            imgPlaceholder.visibility = View.VISIBLE
                            imgPlant.visibility = View.GONE
                            // Оставляем зелёный фон с иконкой листика
                            containerPhoto.setBackgroundResource(R.drawable.bg_plant_photo_container)
                        }
                    )
                }
            } else if (isManualPlant) {
                // Для ручных растений без фото всегда показываем иконку листика в зелёном квадрате
                imgPlant.visibility = View.GONE
                imgPlaceholder.visibility = View.VISIBLE
                containerPhoto.setBackgroundResource(R.drawable.bg_plant_photo_container)
            } else {
                // Для обычных растений пытаемся загрузить стандартное изображение
                val drawableId = try {
                    ResourceHelper.getDrawableId(itemView.context, userPlant.plant.imageResName)
                } catch (e: Exception) {
                    0
                }
                
                if (drawableId != 0) {
                    // Есть стандартное изображение
                    imgPlaceholder.visibility = View.GONE
                    imgPlant.visibility = View.VISIBLE
                    // Восстанавливаем зелёный фон для фото
                    containerPhoto.setBackgroundResource(R.drawable.bg_plant_photo_container)
                    imgPlant.load(drawableId) {
                        size(Size(160, 160))
                        crossfade(true)
                        listener(
                            onError = { _, _ ->
                                // Если ошибка загрузки, показываем заглушку
                                imgPlaceholder.visibility = View.VISIBLE
                                imgPlant.visibility = View.GONE
                                // Оставляем зелёный фон с иконкой листика
                                containerPhoto.setBackgroundResource(R.drawable.bg_plant_photo_container)
                            }
                        )
                    }
                } else {
                    // Нет изображения - показываем иконку листика в зелёном квадрате
                    imgPlant.visibility = View.GONE
                    imgPlaceholder.visibility = View.VISIBLE
                    containerPhoto.setBackgroundResource(R.drawable.bg_plant_photo_container)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserPlantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plant, parent, false)  // Используем тот же layout
        return UserPlantViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserPlantViewHolder, position: Int) {
        holder.bind(userPlants[position])
    }

    override fun getItemCount(): Int = userPlants.size

    /* Функция для форматирования даты (опционально)
    private fun formatDate(timestamp: Long?): String {
        if (timestamp == null) return ""
        // Можно реализовать форматирование даты
        return "Недавно"
    }*/
}