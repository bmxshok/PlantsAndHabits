package com.example.plantsandhabits

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = UserPlant::class,
            parentColumns = ["id"],
            childColumns = ["userPlantId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userPlantId: Int,
    val workType: String,
    val periodValue: Int,
    val periodUnit: String, // "days", "weeks", "months"
    val hour: Int,
    val minute: Int,
    val nextTriggerAt: Long
)


