package com.example.plantsandhabits

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

object ReminderManager {
    
    /**
     * Перепланирует все напоминания из БД
     * Вызывается при запуске приложения для восстановления уведомлений
     */
    fun rescheduleAllReminders(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                
                // Сначала продлеваем напоминания, если нужно
                extendRemindersForNextMonth(context, database)
                
                // Затем перепланируем все активные напоминания
                val reminders = database.plantDao().getRemindersWithDetails()
                
                reminders.forEach { reminderWithDetails ->
                    // Планируем только невыполненные напоминания
                    if (!reminderWithDetails.isCompleted) {
                        val reminder = database.plantDao().getReminderById(reminderWithDetails.id)
                        if (reminder != null) {
                            // Отменяем старое уведомление
                            ReminderScheduler.cancelReminder(context, reminder.id)
                            // Планируем новое
                            ReminderScheduler.scheduleReminder(context, reminder)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Продлевает напоминания на месяц вперёд, если прошёл месяц с момента создания
     */
    private suspend fun extendRemindersForNextMonth(context: Context, database: AppDatabase) {
        try {
            val now = Calendar.getInstance()
            val oneMonthAgo = Calendar.getInstance().apply {
                add(Calendar.MONTH, -1)
            }
            
            // Получаем все напоминания, которые были созданы месяц назад или раньше
            // и ещё не продлены на следующий месяц
            val allReminders = database.plantDao().getRemindersWithDetails()
            
            // Группируем напоминания по userPlantId, workType, periodValue, periodUnit, hour, minute
            val reminderGroups = allReminders
                .filter { !it.isCompleted }
                .groupBy { 
                    "${it.userPlantId}_${it.workType}_${it.periodValue}_${it.periodUnit}_${it.hour}_${it.minute}"
                }
            
            for ((_, groupReminders) in reminderGroups) {
                if (groupReminders.isEmpty()) continue
                
                val firstReminder = groupReminders.first()
                
                // Проверяем, есть ли напоминания на следующий месяц
                val nextMonthStart = Calendar.getInstance().apply {
                    add(Calendar.MONTH, 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                
                val nextMonthEnd = Calendar.getInstance().apply {
                    timeInMillis = nextMonthStart.timeInMillis
                    add(Calendar.MONTH, 1)
                }
                
                // Проверяем, есть ли уже напоминания на следующий месяц
                val existingNextMonthReminders = groupReminders.filter { reminder ->
                    reminder.nextTriggerAt >= nextMonthStart.timeInMillis && 
                    reminder.nextTriggerAt < nextMonthEnd.timeInMillis
                }
                
                // Если нет напоминаний на следующий месяц, создаём их
                if (existingNextMonthReminders.isEmpty()) {
                    val latestReminder = groupReminders.maxByOrNull { it.nextTriggerAt }
                    if (latestReminder != null) {
                        val latestDate = Calendar.getInstance().apply {
                            timeInMillis = latestReminder.nextTriggerAt
                        }
                        
                        // Проверяем, что последнее напоминание в текущем месяце или раньше
                        val currentMonthStart = Calendar.getInstance().apply {
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        
                        // Если последнее напоминание в текущем месяце или раньше, создаём новые на следующий месяц
                        if (latestDate.timeInMillis < nextMonthStart.timeInMillis) {
                            createRemindersForNextMonth(
                                context = context,
                                database = database,
                                userPlantId = firstReminder.userPlantId,
                                workType = firstReminder.workType,
                                periodValue = firstReminder.periodValue,
                                periodUnit = firstReminder.periodUnit,
                                hour = firstReminder.hour,
                                minute = firstReminder.minute
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReminderManager", "Error extending reminders", e)
        }
    }

    /**
     * Создаёт напоминания на следующий месяц
     */
    private suspend fun createRemindersForNextMonth(
        context: Context,
        database: AppDatabase,
        userPlantId: Int,
        workType: String,
        periodValue: Int,
        periodUnit: String,
        hour: Int,
        minute: Int
    ) {
        val now = Calendar.getInstance()
        val nextMonthStart = Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val nextMonthEnd = Calendar.getInstance().apply {
            timeInMillis = nextMonthStart.timeInMillis
            add(Calendar.MONTH, 1)
        }
        
        // Вычисляем первую дату напоминания в следующем месяце
        val firstDate = Calendar.getInstance().apply {
            timeInMillis = nextMonthStart.timeInMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val remindersToCreate = mutableListOf<Reminder>()
        val currentDate = Calendar.getInstance().apply {
            timeInMillis = firstDate.timeInMillis
        }
        
        // Генерируем все даты напоминаний до конца следующего месяца
        while (currentDate.timeInMillis < nextMonthEnd.timeInMillis) {
            remindersToCreate.add(
                Reminder(
                    userPlantId = userPlantId,
                    workType = workType,
                    periodValue = periodValue,
                    periodUnit = periodUnit,
                    hour = hour,
                    minute = minute,
                    nextTriggerAt = currentDate.timeInMillis,
                    isCompleted = false
                )
            )
            
            // Переходим к следующей дате на основе периода
            when (periodUnit) {
                "days" -> currentDate.add(Calendar.DAY_OF_MONTH, periodValue)
                "weeks" -> currentDate.add(Calendar.WEEK_OF_YEAR, periodValue)
                "months" -> currentDate.add(Calendar.MONTH, periodValue)
            }
        }
        
        // Вставляем все напоминания в БД
        for (reminder in remindersToCreate) {
            val reminderId = database.plantDao().insertReminder(reminder)
            if (reminderId > 0) {
                // Планируем уведомление для каждого напоминания
                val createdReminder = database.plantDao().getReminderById(reminderId.toInt())
                if (createdReminder != null) {
                    ReminderScheduler.scheduleReminder(context, createdReminder)
                }
            }
        }
        
        android.util.Log.d("ReminderManager", "Created ${remindersToCreate.size} reminders for next month")
    }
}

