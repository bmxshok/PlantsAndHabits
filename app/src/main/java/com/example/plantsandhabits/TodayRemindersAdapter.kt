package com.example.plantsandhabits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TodayRemindersAdapter(
    private val reminders: List<ReminderWithDetails>,
    private val onCheckClick: (Int) -> Unit
) : RecyclerView.Adapter<TodayRemindersAdapter.ReminderViewHolder>() {

    private val checkedReminders = mutableSetOf<Int>()

    inner class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvWorkType: TextView = itemView.findViewById(R.id.tvWorkType)
        private val tvPlantName: TextView = itemView.findViewById(R.id.tvPlantName)
        private val btnCheck: ImageView = itemView.findViewById(R.id.btnCheck)

        fun bind(reminder: ReminderWithDetails) {
            tvWorkType.text = reminder.workType
            val plantName = reminder.customName ?: reminder.plantName
            tvPlantName.text = plantName

            val isChecked = checkedReminders.contains(reminder.id)
            btnCheck.setImageResource(
                if (isChecked) R.drawable.ic_checkbox_checked
                else R.drawable.ic_checkbox_empty
            )

            btnCheck.setOnClickListener {
                if (!isChecked) {
                    onCheckClick(reminder.id)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_today_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(reminders[position])
    }

    override fun getItemCount(): Int = reminders.size

    fun markAsChecked(reminderId: Int) {
        checkedReminders.add(reminderId)
        notifyDataSetChanged()
    }

    fun restoreCheckedState(checkedIds: Set<Int>) {
        checkedReminders.clear()
        checkedReminders.addAll(checkedIds)
    }
}

