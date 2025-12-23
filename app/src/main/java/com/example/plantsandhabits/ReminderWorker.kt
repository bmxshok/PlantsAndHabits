package com.example.plantsandhabits

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getInt("reminder_id", -1)
        if (reminderId == -1) return Result.failure()

        return try {
            withContext(Dispatchers.IO) {
                val database = AppDatabase.getDatabase(applicationContext)
                
                // Получаем напоминание из БД
                val reminders = database.plantDao().getRemindersWithDetails()
                val reminder = reminders.find { it.id == reminderId }
                
                if (reminder != null) {
                    val plantName = reminder.customName ?: reminder.plantName

                    // Показываем уведомление
                    withContext(Dispatchers.Main) {
                        val notificationHelper = NotificationHelper(applicationContext)
                        notificationHelper.showReminderNotification(
                            reminderId,
                            plantName,
                            reminder.workType
                        )
                    }

                    // Напоминание уже создано как отдельный экземпляр, 
                    // не нужно обновлять или перепланировать - просто показываем уведомление
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

