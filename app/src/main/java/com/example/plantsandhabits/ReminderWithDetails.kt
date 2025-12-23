package com.example.plantsandhabits

data class ReminderWithDetails(
    val id: Int,
    val userPlantId: Int,
    val workType: String,
    val periodValue: Int,
    val periodUnit: String,
    val hour: Int,
    val minute: Int,
    val nextTriggerAt: Long,
    val isCompleted: Boolean = false,
    val plantName: String,
    val customName: String?,
    val plantType: String?
)


