package com.example.plantsandhabits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private lateinit var database: AppDatabase
    private lateinit var rvCalendar: RecyclerView
    private lateinit var rvTasks: RecyclerView
    private lateinit var tvMonthYear: TextView
    private lateinit var tvDateSubtitle: TextView
    private lateinit var btnPrevMonth: ImageView
    private lateinit var btnNextMonth: ImageView

    private lateinit var calendarDayAdapter: CalendarDayAdapter
    private lateinit var calendarTaskAdapter: CalendarTaskAdapter

    private var currentCalendar = Calendar.getInstance()
    private var selectedCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private val monthNames = arrayOf(
        "Янв.", "Фев.", "Мар.", "Апр.", "Май", "Июн.",
        "Июл.", "Авг.", "Сен.", "Окт.", "Нояб.", "Дек."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = (requireActivity() as DatabaseProvider).database
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Добавляем отступы для статус-бара
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop + systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        rvCalendar = view.findViewById(R.id.rvCalendar)
        rvTasks = view.findViewById(R.id.rvTasks)
        tvMonthYear = view.findViewById(R.id.tvMonthYear)
        tvDateSubtitle = view.findViewById(R.id.tvDateSubtitle)
        btnPrevMonth = view.findViewById(R.id.btnPrevMonth)
        btnNextMonth = view.findViewById(R.id.btnNextMonth)

        setupCalendar()
        setupTasks()

        btnPrevMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            // Если выбранная дата не в текущем месяце, сбрасываем на первый день месяца
            if (selectedCalendar.get(Calendar.MONTH) != currentCalendar.get(Calendar.MONTH) ||
                selectedCalendar.get(Calendar.YEAR) != currentCalendar.get(Calendar.YEAR)) {
                selectedCalendar = currentCalendar.clone() as Calendar
                selectedCalendar.set(Calendar.DAY_OF_MONTH, 1)
                selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
                selectedCalendar.set(Calendar.MINUTE, 0)
                selectedCalendar.set(Calendar.SECOND, 0)
                selectedCalendar.set(Calendar.MILLISECOND, 0)
            }
            updateCalendar()
            loadTasksForSelectedDate()
        }

        btnNextMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            // Если выбранная дата не в текущем месяце, сбрасываем на первый день месяца
            if (selectedCalendar.get(Calendar.MONTH) != currentCalendar.get(Calendar.MONTH) ||
                selectedCalendar.get(Calendar.YEAR) != currentCalendar.get(Calendar.YEAR)) {
                selectedCalendar = currentCalendar.clone() as Calendar
                selectedCalendar.set(Calendar.DAY_OF_MONTH, 1)
                selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
                selectedCalendar.set(Calendar.MINUTE, 0)
                selectedCalendar.set(Calendar.SECOND, 0)
                selectedCalendar.set(Calendar.MILLISECOND, 0)
            }
            updateCalendar()
            loadTasksForSelectedDate()
        }

        updateCalendar()
        loadTasksForSelectedDate()
    }

    private fun setupCalendar() {
        rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        calendarDayAdapter = CalendarDayAdapter(emptyList()) { day ->
            selectedCalendar = day.calendar.clone() as Calendar
            selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
            selectedCalendar.set(Calendar.MINUTE, 0)
            selectedCalendar.set(Calendar.SECOND, 0)
            selectedCalendar.set(Calendar.MILLISECOND, 0)
            updateCalendar()
            loadTasksForSelectedDate()
        }
        rvCalendar.adapter = calendarDayAdapter
    }

    private fun setupTasks() {
        rvTasks.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        calendarTaskAdapter = CalendarTaskAdapter(emptyList(), emptyMap())
        rvTasks.adapter = calendarTaskAdapter
    }

    override fun onResume() {
        super.onResume()
        // При возврате на экран проверяем и продлеваем напоминания
        CoroutineScope(Dispatchers.IO).launch {
            ReminderManager.rescheduleAllReminders(requireContext())
        }
        loadTasksForSelectedDate()
    }

    private fun updateCalendar() {
        val days = mutableListOf<CalendarDay>()

        // Устанавливаем календарь на первый день месяца
        val firstDayOfMonth = currentCalendar.clone() as Calendar
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)

        // Определяем день недели первого дня (1 = понедельник, 7 = воскресенье)
        // В Calendar: воскресенье = 1, понедельник = 2, ..., суббота = 7
        val firstDayOfWeek = when (firstDayOfMonth.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        // Добавляем пустые дни до первого дня месяца
        for (i in 1 until firstDayOfWeek) {
            days.add(CalendarDay(0, Calendar.getInstance(), false))
        }

        // Получаем количество дней в месяце
        val daysInMonth = firstDayOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Добавляем дни месяца
        for (day in 1..daysInMonth) {
            val dayCalendar = firstDayOfMonth.clone() as Calendar
            dayCalendar.set(Calendar.DAY_OF_MONTH, day)
            val isSelected = isSameDay(dayCalendar, selectedCalendar)
            days.add(CalendarDay(day, dayCalendar, isSelected))
        }

        android.util.Log.d("Calendar", "Creating adapter with ${days.size} days, firstDayOfWeek=$firstDayOfWeek, daysInMonth=$daysInMonth")

        val newAdapter = CalendarDayAdapter(days) { day ->
            selectedCalendar = day.calendar.clone() as Calendar
            selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
            selectedCalendar.set(Calendar.MINUTE, 0)
            selectedCalendar.set(Calendar.SECOND, 0)
            selectedCalendar.set(Calendar.MILLISECOND, 0)
            updateCalendar()
            loadTasksForSelectedDate()
        }
        calendarDayAdapter = newAdapter
        rvCalendar.adapter = newAdapter

        // Обновляем заголовок месяца
        val month = currentCalendar.get(Calendar.MONTH)
        val year = currentCalendar.get(Calendar.YEAR)
        tvMonthYear.text = "${monthNames[month]} $year"

        // Подзаголовок всегда показывает "Список дел"
        tvDateSubtitle.text = "Список дел"
    }

    // Функция updateDateSubtitle() больше не используется - подзаголовок всегда "Список дел"

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun loadTasksForSelectedDate() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Вычисляем начало и конец выбранного дня
                val startOfDay = selectedCalendar.clone() as Calendar
                startOfDay.set(Calendar.HOUR_OF_DAY, 0)
                startOfDay.set(Calendar.MINUTE, 0)
                startOfDay.set(Calendar.SECOND, 0)
                startOfDay.set(Calendar.MILLISECOND, 0)
                
                val endOfDay = selectedCalendar.clone() as Calendar
                endOfDay.set(Calendar.HOUR_OF_DAY, 23)
                endOfDay.set(Calendar.MINUTE, 59)
                endOfDay.set(Calendar.SECOND, 59)
                endOfDay.set(Calendar.MILLISECOND, 999)
                
                // Получаем все напоминания для выбранной даты (включая прошедшие)
                val reminders = withContext(Dispatchers.IO) {
                    database.plantDao().getRemindersForDate(
                        startOfDay.timeInMillis,
                        endOfDay.timeInMillis
                    )
                }

                val userPlants = withContext(Dispatchers.IO) {
                    database.plantDao().getUserPlantsWithDetails()
                }

                val userPlantsMap = userPlants.associateBy { it.userPlantId.toInt() }

                calendarTaskAdapter = CalendarTaskAdapter(reminders, userPlantsMap)
                calendarTaskAdapter.setSelectedDate(selectedCalendar.time)
                rvTasks.adapter = calendarTaskAdapter
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
