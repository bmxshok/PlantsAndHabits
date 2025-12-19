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

                    // Обновляем nextTriggerAt для следующего срабатывания
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = reminder.nextTriggerAt
                        when (reminder.periodUnit) {
                            "days" -> add(Calendar.DAY_OF_MONTH, reminder.periodValue)
                            "weeks" -> add(Calendar.WEEK_OF_YEAR, reminder.periodValue)
                            "months" -> add(Calendar.MONTH, reminder.periodValue)
                        }
                    }
                    val nextTriggerAt = cal.timeInMillis

                    // Обновляем напоминание в БД
                    database.plantDao().updateReminderNextTrigger(reminder.id, nextTriggerAt)
                    
                    // Перепланируем следующее уведомление
                    val updatedReminder = database.plantDao().getReminderById(reminder.id)
                    if (updatedReminder != null) {
                        ReminderScheduler.scheduleReminder(applicationContext, updatedReminder)
                    }
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

