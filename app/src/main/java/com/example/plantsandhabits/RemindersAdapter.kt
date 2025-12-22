package com.example.plantsandhabits

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class RemindersAdapter(
    private val items: List<ReminderListItem>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_REMINDER = 1
    }

    inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)

        fun bind(date: String) {
            tvDate.text = date
        }
    }

    inner class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgPlant: ImageView = itemView.findViewById(R.id.imgPlant)
        private val tvPlantName: TextView = itemView.findViewById(R.id.tvPlantName)
        private val tvTask: TextView = itemView.findViewById(R.id.tvTask)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteReminder)

        fun bind(reminder: ReminderWithDetails, userPlant: UserPlantWithDetails?) {
            val displayName = reminder.customName ?: reminder.plantName
            tvPlantName.text = displayName
            tvTask.text = reminder.workType.replaceFirstChar { it.uppercase() }

            // Загружаем изображение растения
            loadPlantImage(userPlant)

            btnDelete.setOnClickListener {
                onDeleteClick(reminder.id)
            }
        }

        private fun loadPlantImage(userPlant: UserPlantWithDetails?) {
            try {
                if (userPlant != null && !userPlant.customImage.isNullOrEmpty()) {
                    val imageFile = File(userPlant.customImage)
                    if (imageFile.exists()) {
                        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                        imgPlant.setImageBitmap(bitmap)
                        return
                    }
                }
                // Загружаем стандартное изображение
                if (userPlant != null) {
                    val drawableId = ResourceHelper.getDrawableId(
                        itemView.context,
                        userPlant.plant.imageResName
                    )
                    if (drawableId != 0) {
                        imgPlant.setImageResource(drawableId)
                    } else {
                        imgPlant.setImageResource(R.drawable.sample_category)
                    }
                } else {
                    imgPlant.setImageResource(R.drawable.sample_category)
                }
            } catch (e: Exception) {
                imgPlant.setImageResource(R.drawable.sample_category)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ReminderListItem.DateHeader -> TYPE_DATE_HEADER
            is ReminderListItem.Reminder -> TYPE_REMINDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_reminder_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            TYPE_REMINDER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_reminder, parent, false)
                ReminderViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ReminderListItem.DateHeader -> {
                (holder as DateHeaderViewHolder).bind(item.date)
            }
            is ReminderListItem.Reminder -> {
                (holder as ReminderViewHolder).bind(item.reminder, item.userPlant)
            }
        }
    }

    override fun getItemCount(): Int = items.size
}

sealed class ReminderListItem {
    data class DateHeader(val date: String) : ReminderListItem()
    data class Reminder(val reminder: ReminderWithDetails, val userPlant: UserPlantWithDetails?) : ReminderListItem()
}