package com.example.plantsandhabits

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class NoMenuActivity : AppCompatActivity(), DatabaseProvider {
    override val database by lazy { AppDatabase.getDatabase(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_menu)

        // Получаем растение из Intent
        val screenType = intent.getStringExtra("screen_type")

        when(screenType) {
            "plant_detail" -> {
                val plant = intent.getParcelableExtra<Plant>("plant")
                if(plant != null) {
                    showPlantDetailFragment(plant)
                } else {
                    finish()
                }
            }
            "user_plant_detail" -> {
                val userPlant = intent.getParcelableExtra<UserPlantWithDetails>("user_plant")
                if(userPlant != null) {
                    showUserPlantDetailFragment(userPlant)
                } else {
                    finish()
                }
            }
            "add_plant_form" -> {
                showAddPlantFormFragment()
            }
            else -> finish()
        }
    }

    private fun showPlantDetailFragment(plant: Plant) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PlantDetailFragment.newInstance(plant))
            .commit()
    }

    private fun showUserPlantDetailFragment(userPlant: UserPlantWithDetails) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, UserPlantDetailFragment.newInstance(userPlant))
            .commit()
    }

    private fun showAddPlantFormFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AddPlantFormFragment())
            .commit()
    }
}