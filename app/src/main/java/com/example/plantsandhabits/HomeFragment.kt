package com.example.plantsandhabits

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class HomeFragment : Fragment() {

    private lateinit var database: AppDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TodayRemindersAdapter
    private val reminders = mutableListOf<ReminderWithDetails>()
    private val checkedReminderIds = mutableSetOf<Int>() // Сохраняем отмеченные напоминания
    private val originalTodayReminders = mutableListOf<ReminderWithDetails>() // Сохраняем оригинальные напоминания на сегодня
    private lateinit var sharedPreferences: SharedPreferences
    private var lastCheckedDate: String = "" // Дата последней проверки для очистки старых отметок

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = (requireActivity() as DatabaseProvider).database
        sharedPreferences = requireContext().getSharedPreferences("today_reminders", Context.MODE_PRIVATE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Сохраняем исходные отступы из layout
        val originalPaddingTop = view.paddingTop
        val originalPaddingLeft = view.paddingLeft
        val originalPaddingRight = view.paddingRight
        val originalPaddingBottom = view.paddingBottom

        // Добавляем отступы для статус-бара
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                originalPaddingLeft,
                originalPaddingTop + systemBars.top,
                originalPaddingRight,
                originalPaddingBottom
            )
            insets
        }

        recyclerView = view.findViewById(R.id.rvTodayReminders)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = TodayRemindersAdapter(reminders) { reminderId ->
            onReminderChecked(reminderId)
        }
        recyclerView.adapter = adapter

        // Загружаем сохраненные отмеченные напоминания
        loadCheckedReminders()
        
        // Проверяем, не изменился ли день перед загрузкой
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayString = "${today.get(Calendar.YEAR)}-${today.get(Calendar.MONTH)}-${today.get(Calendar.DAY_OF_MONTH)}"
        
        // Если день изменился, очищаем старые отметки и обновляем напоминания
        if (lastCheckedDate != todayString && lastCheckedDate.isNotEmpty()) {
            // День изменился - обновляем nextTriggerAt для отмеченных напоминаний
            updateCheckedRemindersForNewDay()
            lastCheckedDate = todayString
            checkedReminderIds.clear()
            originalTodayReminders.clear()
            loadCheckedReminders()
        } else if (lastCheckedDate.isEmpty()) {
            lastCheckedDate = todayString
        }
        
        loadTodayReminders()
    }

    override fun onResume() {
        super.onResume()
        // Проверяем, не изменился ли день
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayString = "${today.get(Calendar.YEAR)}-${today.get(Calendar.MONTH)}-${today.get(Calendar.DAY_OF_MONTH)}"
        
        // Если день изменился, очищаем старые отметки и обновляем напоминания
        if (lastCheckedDate != todayString) {
            if (lastCheckedDate.isNotEmpty()) {
                // День изменился - обновляем nextTriggerAt для отмеченных напоминаний
                updateCheckedRemindersForNewDay()
            }
            lastCheckedDate = todayString
            checkedReminderIds.clear()
            originalTodayReminders.clear()
            loadCheckedReminders()
            loadTodayReminders()
        } else {
            // День не изменился - просто обновляем список без перезагрузки из БД
            reminders.clear()
            reminders.addAll(originalTodayReminders)
            adapter.restoreCheckedState(checkedReminderIds)
            adapter.notifyDataSetChanged()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Сохраняем отмеченные напоминания
        saveCheckedReminders()
    }

    private fun loadTodayReminders() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Вычисляем начало и конец сегодняшнего дня
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis

                calendar.add(Calendar.DAY_OF_MONTH, 1)
                val endOfDay = calendar.timeInMillis

                android.util.Log.d("HomeFragment", "Loading reminders for today: startOfDay=$startOfDay, endOfDay=$endOfDay")

                // Если оригинальный список пуст (первая загрузка), загружаем напоминания на сегодня
                if (originalTodayReminders.isEmpty()) {
                    val todayReminders = withContext(Dispatchers.IO) {
                        database.plantDao().getRemindersForDate(startOfDay, endOfDay)
                    }

                    android.util.Log.d("HomeFragment", "Found ${todayReminders.size} reminders for today (initial load)")

                    // Очищаем список перед добавлением (на случай, если он не был пуст)
                    originalTodayReminders.clear()
                    
                    // Создаем копии, чтобы не зависеть от изменений в БД
                    // Убираем дубликаты по ID
                    val existingIds = mutableSetOf<Int>()
                    todayReminders.forEach { reminder ->
                        if (reminder.id !in existingIds) {
                            existingIds.add(reminder.id)
                            originalTodayReminders.add(
                                ReminderWithDetails(
                                    id = reminder.id,
                                    userPlantId = reminder.userPlantId,
                                    workType = reminder.workType,
                                    periodValue = reminder.periodValue,
                                    periodUnit = reminder.periodUnit,
                                    hour = reminder.hour,
                                    minute = reminder.minute,
                                    nextTriggerAt = reminder.nextTriggerAt,
                                    plantName = reminder.plantName,
                                    customName = reminder.customName,
                                    plantType = reminder.plantType
                                )
                            )
                        }
                    }
                    android.util.Log.d("HomeFragment", "Initial load: saved ${originalTodayReminders.size} unique reminders")
                } else {
                    // После первой загрузки НЕ добавляем новые напоминания из БД
                    // Это предотвращает добавление напоминаний, которые были перенесены на сегодня из-за ошибок
                    android.util.Log.d("HomeFragment", "Skipping DB query - using cached reminders. Current count: ${originalTodayReminders.size}")
                }

                // Формируем финальный список: все оригинальные напоминания (включая отмеченные, даже если их nextTriggerAt уже не сегодня)
                // Убираем дубликаты по ID
                reminders.clear()
                val displayedIds = mutableSetOf<Int>()
                originalTodayReminders.forEach { reminder ->
                    if (reminder.id !in displayedIds) {
                        displayedIds.add(reminder.id)
                        reminders.add(reminder)
                    }
                }
                
                android.util.Log.d("HomeFragment", "Displaying ${reminders.size} unique reminders (${checkedReminderIds.size} checked)")
                
                // Восстанавливаем состояние отмеченных в адаптере
                adapter.restoreCheckedState(checkedReminderIds)
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error loading reminders", e)
                e.printStackTrace()
            }
        }
    }

    private fun loadCheckedReminders() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayString = "${today.get(Calendar.YEAR)}-${today.get(Calendar.MONTH)}-${today.get(Calendar.DAY_OF_MONTH)}"
        lastCheckedDate = sharedPreferences.getString("last_checked_date", "") ?: ""
        
        // Загружаем отмеченные напоминания только для сегодняшнего дня
        if (lastCheckedDate == todayString) {
            val checkedIdsString = sharedPreferences.getString("checked_reminder_ids", "") ?: ""
            if (checkedIdsString.isNotEmpty()) {
                checkedReminderIds.clear()
                checkedIdsString.split(",").forEach { idStr ->
                    idStr.toIntOrNull()?.let { checkedReminderIds.add(it) }
                }
                android.util.Log.d("HomeFragment", "Loaded ${checkedReminderIds.size} checked reminders from preferences")
            }
        } else {
            checkedReminderIds.clear()
        }
    }
    
    private fun saveCheckedReminders() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayString = "${today.get(Calendar.YEAR)}-${today.get(Calendar.MONTH)}-${today.get(Calendar.DAY_OF_MONTH)}"
        
        sharedPreferences.edit().apply {
            putString("last_checked_date", todayString)
            putString("checked_reminder_ids", checkedReminderIds.joinToString(","))
            apply()
        }
        android.util.Log.d("HomeFragment", "Saved ${checkedReminderIds.size} checked reminders to preferences")
    }
    
    private fun updateCheckedRemindersForNewDay() {
        // Обновляем nextTriggerAt для всех отмеченных напоминаний при смене дня
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    val savedCheckedIds = sharedPreferences.getString("checked_reminder_ids", "") ?: ""
                    if (savedCheckedIds.isNotEmpty()) {
                        savedCheckedIds.split(",").forEach { idStr ->
                            val reminderId = idStr.toIntOrNull() ?: return@forEach
                            val reminder = database.plantDao().getReminderById(reminderId)
                            if (reminder != null) {
                                val calendar = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, reminder.hour)
                                    set(Calendar.MINUTE, reminder.minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                    
                                    // Добавляем период
                                    when (reminder.periodUnit) {
                                        "days" -> add(Calendar.DAY_OF_MONTH, reminder.periodValue)
                                        "weeks" -> add(Calendar.WEEK_OF_YEAR, reminder.periodValue)
                                        "months" -> add(Calendar.MONTH, reminder.periodValue)
                                    }
                                }
                                
                                database.plantDao().updateReminderNextTrigger(reminderId, calendar.timeInMillis)
                                
                                // Перепланируем уведомление
                                val updatedReminder = reminder.copy(nextTriggerAt = calendar.timeInMillis)
                                ReminderScheduler.cancelReminder(requireContext(), reminderId)
                                ReminderScheduler.scheduleReminder(requireContext(), updatedReminder)
                                
                                android.util.Log.d("HomeFragment", "Updated reminder $reminderId for new day: nextTriggerAt=${calendar.timeInMillis}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error updating reminders for new day", e)
            }
        }
    }

    private fun onReminderChecked(reminderId: Int) {
        // Показываем диалог подтверждения
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Подтверждение")
            .setMessage("Отметить напоминание как выполненное?")
            .setPositiveButton("Да") { _, _ ->
                // Сохраняем ID отмеченного напоминания
                checkedReminderIds.add(reminderId)
                // Отмечаем в адаптере
                adapter.markAsChecked(reminderId)
                // Сохраняем в SharedPreferences
                saveCheckedReminders()
                
                android.util.Log.d("HomeFragment", "Marked reminder $reminderId as checked, keeping it on screen")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateReminderInBackground(reminderId: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    // Обновляем nextTriggerAt на следующий период ТОЛЬКО для отмеченного напоминания
                    val reminder = database.plantDao().getReminderById(reminderId)
                    if (reminder != null) {
                        android.util.Log.d("HomeFragment", "Updating ONLY reminder $reminderId, current nextTriggerAt=${reminder.nextTriggerAt}")
                        
                        // Проверяем, сколько напоминаний в БД до обновления
                        val allRemindersBefore = database.plantDao().getRemindersWithDetails()
                        android.util.Log.d("HomeFragment", "Reminders in DB before update: ${allRemindersBefore.size}")
                        
                        val calendar = Calendar.getInstance().apply {
                            // Используем текущее время как базовое, а не nextTriggerAt
                            set(Calendar.HOUR_OF_DAY, reminder.hour)
                            set(Calendar.MINUTE, reminder.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            
                            // Если время уже прошло сегодня, планируем на завтра
                            if (timeInMillis < System.currentTimeMillis()) {
                                add(Calendar.DAY_OF_MONTH, 1)
                            }
                            
                            // Добавляем период
                            when (reminder.periodUnit) {
                                "days" -> add(Calendar.DAY_OF_MONTH, reminder.periodValue)
                                "weeks" -> add(Calendar.WEEK_OF_YEAR, reminder.periodValue)
                                "months" -> add(Calendar.MONTH, reminder.periodValue)
                            }
                        }
                        
                        val newNextTriggerAt = calendar.timeInMillis
                        android.util.Log.d("HomeFragment", "Updating reminder $reminderId: new nextTriggerAt=$newNextTriggerAt")
                        
                        // Обновляем ТОЛЬКО это напоминание в БД (SQL запрос с WHERE id = :reminderId)
                        val rowsAffected = database.plantDao().updateReminderNextTrigger(reminderId, newNextTriggerAt)
                        android.util.Log.d("HomeFragment", "Rows affected by update: $rowsAffected (should be 1)")
                        
                        // Проверяем, сколько напоминаний в БД после обновления
                        val allRemindersAfter = database.plantDao().getRemindersWithDetails()
                        android.util.Log.d("HomeFragment", "Reminders in DB after update: ${allRemindersAfter.size}")
                        
                        // Проверяем, что обновилось только нужное напоминание
                        val updatedReminder = database.plantDao().getReminderById(reminderId)
                        if (updatedReminder != null) {
                            android.util.Log.d("HomeFragment", "Updated reminder $reminderId: nextTriggerAt=${updatedReminder.nextTriggerAt}")
                        }
                        
                        // Перепланируем уведомление
                        val updatedReminderForScheduler = reminder.copy(nextTriggerAt = newNextTriggerAt)
                        ReminderScheduler.cancelReminder(requireContext(), reminderId)
                        ReminderScheduler.scheduleReminder(requireContext(), updatedReminderForScheduler)
                        
                        android.util.Log.d("HomeFragment", "Successfully updated reminder $reminderId")
                    } else {
                        android.util.Log.e("HomeFragment", "Reminder $reminderId not found in database")
                    }
                }
                // НЕ перезагружаем список - напоминание должно остаться с галочкой в originalTodayReminders
                // originalTodayReminders содержит копии, поэтому они не изменятся при обновлении БД
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error updating reminder", e)
                e.printStackTrace()
            }
        }
    }
}
