package com.example.plantsandhabits

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AddReminderFragment : Fragment() {

    private lateinit var database: AppDatabase

    private lateinit var tvPlantSummary: TextView
    private lateinit var tvWorkSummary: TextView
    private lateinit var tvPeriodSummary: TextView
    private lateinit var tvTimeSummary: TextView
    private lateinit var btnBack: ImageView

    private val workTypes = listOf(
        "Полив",
        "Пересадка",
        "Удобрение",
        "Опрыскивание",
        "Обрезка",
        "Проверка вредителей",
        "Рыхление почвы"
    )
    private val periodUnits = listOf("день", "неделя", "месяц")
    private val periodUnitCodes = listOf("days", "weeks", "months")

    private var userPlants: List<UserPlantWithDetails> = emptyList()
    
    // Текущие значения
    private var selectedPlantIndex: Int = -1
    private var selectedWorkIndex: Int = -1
    private var periodValue: Int = 7
    private var periodUnitIndex: Int = 0
    private var selectedHour: Int = 9
    private var selectedMinute: Int = 30

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_reminder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Добавляем отступы для статус-бара
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop + systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        database = (requireActivity() as DatabaseProvider).database

        tvPlantSummary = view.findViewById(R.id.tvPlantSummary)
        tvWorkSummary = view.findViewById(R.id.tvWorkSummary)
        tvPeriodSummary = view.findViewById(R.id.tvPeriodSummary)
        tvTimeSummary = view.findViewById(R.id.tvTimeSummary)
        btnBack = view.findViewById(R.id.btnBack)

        loadPlants()
        updatePeriodSummary()
        updateTimeSummary()

        btnBack.setOnClickListener { requireActivity().onBackPressed() }
        view.findViewById<Button>(R.id.btnSaveReminder).setOnClickListener { saveReminder() }
        
        // Обработчики кликов для выбора
        tvPlantSummary.setOnClickListener { showPlantDialog() }
        tvWorkSummary.setOnClickListener { showWorkDialog() }
        tvPeriodSummary.setOnClickListener { showPeriodDialog() }
        tvTimeSummary.setOnClickListener { showTimeDialog() }
    }

    private fun loadPlants() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                userPlants = withContext(Dispatchers.IO) {
                    database.plantDao().getUserPlantsWithDetails()
                }
                if (userPlants.isEmpty()) {
                    Toast.makeText(requireContext(), "Сначала добавьте растения в сад", Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressed()
                    return@launch
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка загрузки растений: ${e.message}", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressed()
            }
        }
    }

    private fun showPlantDialog() {
        if (userPlants.isEmpty()) {
            Toast.makeText(requireContext(), "Сначала добавьте растения в сад", Toast.LENGTH_SHORT).show()
            return
        }
        
        val plantNames = userPlants.mapIndexed { index, plant ->
            plant.customName ?: plant.plant.name
        }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Выберите растение")
            .setItems(plantNames) { _, which ->
                selectedPlantIndex = which
                val plantName = plantNames[which]
                tvPlantSummary.text = plantName
            }
            .show()
    }

    private fun showWorkDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Выберите вид работ")
            .setItems(workTypes.toTypedArray()) { _, which ->
                selectedWorkIndex = which
                tvWorkSummary.text = workTypes[which]
            }
            .show()
    }

    private fun showPeriodDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_period_picker, null)
        val npValue = dialogView.findViewById<NumberPicker>(R.id.npPeriodValue)
        val npUnit = dialogView.findViewById<NumberPicker>(R.id.npPeriodUnit)

        npValue.minValue = 1
        npValue.maxValue = 30
        npValue.value = periodValue

        npUnit.minValue = 0
        npUnit.maxValue = periodUnits.size - 1
        npUnit.displayedValues = periodUnits.toTypedArray()
        npUnit.value = periodUnitIndex

        AlertDialog.Builder(requireContext())
            .setTitle("Периодичность")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                periodValue = npValue.value
                periodUnitIndex = npUnit.value
                updatePeriodSummary()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showTimeDialog() {
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                updateTimeSummary()
            },
            selectedHour,
            selectedMinute,
            true
        )
        timePickerDialog.show()
    }

    private fun updatePeriodSummary() {
        val unitWord = when (periodUnitIndex) {
            0 -> if (periodValue == 1) "день" else "дней"
            1 -> if (periodValue == 1) "неделю" else "недель"
            else -> if (periodValue == 1) "месяц" else "месяцев"
        }
        tvPeriodSummary.text = "Раз в $periodValue $unitWord"
    }

    private fun updateTimeSummary() {
        tvTimeSummary.text = String.format("%02d:%02d", selectedHour, selectedMinute)
    }

    private fun saveReminder() {
        if (userPlants.isEmpty()) {
            Toast.makeText(requireContext(), "Сначала добавьте растения в сад", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedPlantIndex == -1 || selectedPlantIndex !in userPlants.indices) {
            Toast.makeText(requireContext(), "Выберите растение", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedWorkIndex == -1 || selectedWorkIndex !in workTypes.indices) {
            Toast.makeText(requireContext(), "Выберите вид работ", Toast.LENGTH_SHORT).show()
            return
        }

        val userPlant = userPlants[selectedPlantIndex]
        val workType = workTypes[selectedWorkIndex]
        val periodUnitCode = periodUnitCodes[periodUnitIndex]

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val context = requireContext()
                val createdCount = withContext(Dispatchers.IO) {
                    createRemindersForMonth(
                        context = context,
                        userPlant = userPlant,
                        workType = workType,
                        periodValue = periodValue,
                        periodUnitCode = periodUnitCode,
                        hour = selectedHour,
                        minute = selectedMinute
                    )
                }
                
                Toast.makeText(requireContext(), "Создано $createdCount напоминаний на месяц вперёд", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressed()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Создаёт все напоминания на месяц вперёд на основе периода
     */
    private suspend fun createRemindersForMonth(
        context: android.content.Context,
        userPlant: UserPlantWithDetails,
        workType: String,
        periodValue: Int,
        periodUnitCode: String,
        hour: Int,
        minute: Int
    ): Int {
        val now = Calendar.getInstance()
        val endDate = Calendar.getInstance().apply {
            add(Calendar.MONTH, 1) // Месяц вперёд
        }
        
        // Вычисляем первую дату напоминания
        val firstDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // Если время уже прошло сегодня, начинаем со следующего периода
            if (timeInMillis < now.timeInMillis) {
                when (periodUnitCode) {
                    "days" -> add(Calendar.DAY_OF_MONTH, periodValue)
                    "weeks" -> add(Calendar.WEEK_OF_YEAR, periodValue)
                    "months" -> add(Calendar.MONTH, periodValue)
                }
            }
        }
        
        val remindersToCreate = mutableListOf<Reminder>()
        val currentDate = Calendar.getInstance().apply {
            timeInMillis = firstDate.timeInMillis
        }
        
        // Генерируем все даты напоминаний до конца месяца
        while (currentDate.timeInMillis <= endDate.timeInMillis) {
            remindersToCreate.add(
                Reminder(
                    userPlantId = userPlant.userPlantId.toInt(),
                    workType = workType,
                    periodValue = periodValue,
                    periodUnit = periodUnitCode,
                    hour = hour,
                    minute = minute,
                    nextTriggerAt = currentDate.timeInMillis,
                    isCompleted = false
                )
            )
            
            // Переходим к следующей дате на основе периода
            when (periodUnitCode) {
                "days" -> currentDate.add(Calendar.DAY_OF_MONTH, periodValue)
                "weeks" -> currentDate.add(Calendar.WEEK_OF_YEAR, periodValue)
                "months" -> currentDate.add(Calendar.MONTH, periodValue)
            }
        }
        
        // Вставляем все напоминания в БД
        var createdCount = 0
        for (reminder in remindersToCreate) {
            val reminderId = database.plantDao().insertReminder(reminder)
            if (reminderId > 0) {
                createdCount++
                // Планируем уведомление для каждого напоминания
                val createdReminder = database.plantDao().getReminderById(reminderId.toInt())
                if (createdReminder != null) {
                    ReminderScheduler.scheduleReminder(context, createdReminder)
                }
            }
        }
        
        return createdCount
    }
}