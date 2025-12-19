package com.example.plantsandhabits

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderManager {
    
    /**
     * Перепланирует все напоминания из БД
     * Вызывается при запуске приложения для восстановления уведомлений
     */
    fun rescheduleAllReminders(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val reminders = database.plantDao().getRemindersWithDetails()
                
                reminders.forEach { reminderWithDetails ->
                    val reminder = database.plantDao().getReminderById(reminderWithDetails.id)
                    if (reminder != null) {
                        // Отменяем старое уведомление
                        ReminderScheduler.cancelReminder(context, reminder.id)
                        // Планируем новое
                        ReminderScheduler.scheduleReminder(context, reminder)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

