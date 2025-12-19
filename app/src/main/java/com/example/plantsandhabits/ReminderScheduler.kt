package com.example.plantsandhabits

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    fun scheduleReminder(context: Context, reminder: Reminder) {
        val now = Calendar.getInstance().timeInMillis
        val delay = reminder.nextTriggerAt - now

        // Если время уже прошло, планируем на следующий период
        val actualDelay = if (delay > 0) delay else {
            val cal = Calendar.getInstance().apply {
                timeInMillis = reminder.nextTriggerAt
                when (reminder.periodUnit) {
                    "days" -> add(Calendar.DAY_OF_MONTH, reminder.periodValue)
                    "weeks" -> add(Calendar.WEEK_OF_YEAR, reminder.periodValue)
                    "months" -> add(Calendar.MONTH, reminder.periodValue)
                }
            }
            cal.timeInMillis - now
        }

        val inputData = workDataOf(
            "reminder_id" to reminder.id
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(inputData)
            .setInitialDelay(actualDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag("reminder_${reminder.id}")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun cancelReminder(context: Context, reminderId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag("reminder_$reminderId")
    }
}

