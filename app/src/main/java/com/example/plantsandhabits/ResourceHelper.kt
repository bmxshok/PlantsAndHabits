package com.example.plantsandhabits

import android.content.Context

object ResourceHelper {
    fun getDrawableId(context: Context, drawableName: String): Int {
        return context.resources.getIdentifier( //поискать более правильный вариант
            drawableName,
            "drawable",
            context.packageName
        )
    }
}