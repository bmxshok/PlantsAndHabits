package com.example.plantsandhabits

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class NoMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_menu)

        // Получаем растение из Intent
        val plant = intent.getParcelableExtra<Plant>("plant")

        if (plant != null) {
            // Загружаем фрагмент с данными растения
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PlantDetailFragment.newInstance(plant))
                .commit()
        } else {
            finish()  // Если растение не передано, закрываем Activity
        }
    }
}