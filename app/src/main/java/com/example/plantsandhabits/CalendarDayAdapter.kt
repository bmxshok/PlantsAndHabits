package com.example.plantsandhabits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class CalendarDayAdapter(
    private val days: List<CalendarDay>,
    private val onDayClick: (CalendarDay) -> Unit
) : RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder>() {

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDay: TextView = itemView.findViewById(R.id.tvDay)

        fun bind(day: CalendarDay) {
            android.util.Log.d("CalendarDayAdapter", "Binding day: ${day.dayNumber}, isSelected: ${day.isSelected}")
            if (day.dayNumber > 0) {
                tvDay.text = day.dayNumber.toString()
                tvDay.visibility = View.VISIBLE
                tvDay.isSelected = day.isSelected
                
                // Устанавливаем цвет текста
                val textColor = if (day.isSelected) {
                    android.graphics.Color.WHITE
                } else {
                    itemView.context.getColor(R.color.dark_green)
                }
                tvDay.setTextColor(textColor)
                
                // Убеждаемся, что текст виден
                tvDay.alpha = 1f
                
                android.util.Log.d("CalendarDayAdapter", "Set text: ${tvDay.text}, color: $textColor, visibility: ${tvDay.visibility}")
                
                itemView.setOnClickListener { onDayClick(day) }
                itemView.isClickable = true
            } else {
                tvDay.text = ""
                tvDay.visibility = View.INVISIBLE
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount(): Int = days.size
}

data class CalendarDay(
    val dayNumber: Int,
    val calendar: Calendar,
    val isSelected: Boolean = false
)

