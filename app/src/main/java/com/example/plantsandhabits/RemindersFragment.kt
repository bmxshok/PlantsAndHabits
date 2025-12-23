package com.example.plantsandhabits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RemindersFragment : Fragment() {

    private lateinit var database: AppDatabase
    private val reminderItems = mutableListOf<ReminderListItem>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RemindersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = (requireActivity() as DatabaseProvider).database
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reminders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Не добавляем отступы для статус-бара, так как этот фрагмент показывается
        // внутри MyPlantsFragment, который уже обрабатывает отступы

        recyclerView = view.findViewById(R.id.rvReminders)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = RemindersAdapter(reminderItems) { reminderId ->
            deleteReminder(reminderId)
        }
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
    }

    private fun loadReminders() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val allReminders = withContext(Dispatchers.IO) {
                    database.plantDao().getRemindersWithDetails()
                }
                
                // Фильтруем прошедшие напоминания - оставляем только будущие и сегодняшние
                val now = System.currentTimeMillis()
                val reminders = allReminders.filter { reminder ->
                    reminder.nextTriggerAt >= now
                }
                
                // Сортируем напоминания по дате по возрастанию (ближайшие сверху)
                val sortedReminders = reminders.sortedBy { it.nextTriggerAt }
                
                val userPlants = withContext(Dispatchers.IO) {
                    database.plantDao().getUserPlantsWithDetails()
                }
                
                val userPlantsMap = userPlants.associateBy { it.userPlantId.toInt() }
                
                val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                
                // Группируем по датам (уже отсортированные)
                val groupedByDate = sortedReminders.groupBy { 
                    dateFormat.format(Date(it.nextTriggerAt))
                }
                
                // Сортируем группы по дате (по nextTriggerAt первого элемента в группе)
                val sortedGroups = groupedByDate.toList().sortedBy { (_, remindersInGroup) ->
                    remindersInGroup.minOfOrNull { it.nextTriggerAt } ?: Long.MAX_VALUE
                }
                
                reminderItems.clear()
                
                sortedGroups.forEach { (date, dateReminders) ->
                    // Добавляем заголовок даты
                    reminderItems.add(ReminderListItem.DateHeader(date))
                    
                    // Добавляем напоминания для этой даты (уже отсортированные по времени)
                    val sortedDateReminders = dateReminders.sortedBy { it.nextTriggerAt }
                    sortedDateReminders.forEach { reminder ->
                        val userPlant = userPlantsMap[reminder.userPlantId]
                        reminderItems.add(ReminderListItem.Reminder(reminder, userPlant))
                    }
                }
                
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun deleteReminder(reminderId: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    // Отменяем запланированное уведомление
                    ReminderScheduler.cancelReminder(requireContext(), reminderId)
                    // Удаляем из БД
                    database.plantDao().deleteReminderById(reminderId)
                }
                // Перезагружаем список напоминаний после удаления
                loadReminders()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}