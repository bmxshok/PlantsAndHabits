package com.example.plantsandhabits

import android.os.Parcelable
import androidx.room.Embedded
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserPlantWithDetails(
    @Embedded val plant: Plant,
    val customName: String?,
    val customImage: String?,
    val addedDate: Long? = null
) : Parcelable