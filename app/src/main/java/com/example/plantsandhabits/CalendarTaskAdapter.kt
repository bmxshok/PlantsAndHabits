package com.example.plantsandhabits

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarTaskAdapter(
    private val tasks: List<ReminderWithDetails>,
    private val userPlants: Map<Int, UserPlantWithDetails>
) : RecyclerView.Adapter<CalendarTaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgPlant: ImageView = itemView.findViewById(R.id.imgPlant)
        private val tvPlantName: TextView = itemView.findViewById(R.id.tvPlantName)
        private val tvTaskDate: TextView = itemView.findViewById(R.id.tvTaskDate)

        fun bind(reminder: ReminderWithDetails, date: Date) {
            val userPlant = userPlants[reminder.userPlantId]
            val plantName = reminder.customName ?: reminder.plantName
            tvPlantName.text = plantName

            val dateFormat = SimpleDateFormat("d MMMM", Locale("ru"))
            val dateStr = dateFormat.format(date)
            tvTaskDate.text = "${reminder.workType} - $dateStr"

            // Загружаем изображение растения
            loadPlantImage(userPlant)
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

    private var selectedDate: Date = Date()

    fun setSelectedDate(date: Date) {
        selectedDate = date
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position], selectedDate)
    }

    override fun getItemCount(): Int = tasks.size
}

