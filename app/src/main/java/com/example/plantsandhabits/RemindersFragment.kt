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
                val reminders = withContext(Dispatchers.IO) {
                    database.plantDao().getRemindersWithDetails()
                }
                
                val userPlants = withContext(Dispatchers.IO) {
                    database.plantDao().getUserPlantsWithDetails()
                }
                
                val userPlantsMap = userPlants.associateBy { it.userPlantId.toInt() }
                
                val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                
                // Группируем по датам
                val groupedByDate = reminders.groupBy { 
                    dateFormat.format(Date(it.nextTriggerAt))
                }.toSortedMap()
                
                reminderItems.clear()
                
                groupedByDate.forEach { (date, dateReminders) ->
                    // Добавляем заголовок даты
                    reminderItems.add(ReminderListItem.DateHeader(date))
                    
                    // Добавляем напоминания для этой даты
                    dateReminders.forEach { reminder ->
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