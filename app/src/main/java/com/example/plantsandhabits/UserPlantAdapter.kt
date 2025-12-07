package com.example.plantsandhabits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserPlantAdapter(
    private val userPlants: List<UserPlantWithDetails>,
    private val onItemClick: (UserPlantWithDetails) -> Unit
) : RecyclerView.Adapter<UserPlantAdapter.UserPlantViewHolder>() {

    inner class UserPlantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgPlant: ImageView = itemView.findViewById(R.id.imgPlant)
        private val tvPlantName: TextView = itemView.findViewById(R.id.tvPlantName)
        private val tvPlantDescription: TextView = itemView.findViewById(R.id.tvScientificName)

        fun bind(userPlant: UserPlantWithDetails) {
            val displayName = userPlant.customName ?: userPlant.plant.name
            tvPlantName.text = displayName
            // Показываем тип растения вместо латинского названия
            tvPlantDescription.text = userPlant.plantType ?: userPlant.plant.scientificName ?: ""
            
            // Загружаем изображение: сначала пытаемся загрузить кастомное, затем стандартное
            try {
                if (!userPlant.customImage.isNullOrEmpty()) {
                    // Загружаем кастомное изображение из файла
                    val imageFile = java.io.File(userPlant.customImage)
                    if (imageFile.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                        imgPlant.setImageBitmap(bitmap)
                    } else {
                        // Если файл не найден, используем стандартное изображение
                        loadDefaultImage(userPlant.plant.imageResName)
                    }
                } else {
                    loadDefaultImage(userPlant.plant.imageResName)
                }
            } catch (e: Exception) {
                imgPlant.setImageResource(R.drawable.sample_category)
            }

            itemView.setOnClickListener {
                onItemClick(userPlant)
            }
        }

        private fun loadDefaultImage(imageResName: String) {
            try {
                val drawableId = ResourceHelper.getDrawableId(
                    itemView.context,
                    imageResName
                )
                if (drawableId != 0) {
                    imgPlant.setImageResource(drawableId)
                } else {
                    imgPlant.setImageResource(R.drawable.sample_category)
                }
            } catch (e: Exception) {
                imgPlant.setImageResource(R.drawable.sample_category)
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