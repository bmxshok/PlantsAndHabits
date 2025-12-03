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
            // Используем кастомное название если есть
            val displayName = userPlant.customName ?: userPlant.plant.name
            tvPlantName.text = displayName
            tvPlantDescription.text = userPlant.plant.scientificName

            // Можно показать дату добавления
            // tvPlantDescription.text = "Добавлено: ${formatDate(userPlant.addedDate)}"

            itemView.setOnClickListener {
                onItemClick(userPlant)
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