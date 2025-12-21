package com.example.plantsandhabits

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "plant_photos",
    foreignKeys = [
        ForeignKey(
            entity = UserPlant::class,
            parentColumns = ["id"],
            childColumns = ["userPlantId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlantPhoto(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userPlantId: Int,
    val photoPath: String,
    val dateAdded: Long = System.currentTimeMillis()
)

