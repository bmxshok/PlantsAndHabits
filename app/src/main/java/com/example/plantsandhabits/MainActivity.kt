package com.example.plantsandhabits

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), DatabaseProvider {
    override val database by lazy { AppDatabase.getDatabase(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            showFragment(HomeFragment(), addToBackStack = false)
        }
        debugUserPlants()
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        findViewById<ImageView>(R.id.btnHome).setOnClickListener {
            showFragment(HomeFragment(), addToBackStack = false)
        }

        findViewById<ImageView>(R.id.btnCalendar).setOnClickListener {
            showFragment(CalendarFragment(), addToBackStack = false)
        }

        findViewById<ImageView>(R.id.btnPlants).setOnClickListener {
            showFragment(MyPlantsFragment(), addToBackStack = false)
        }
    }

    private fun showFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)

        if(addToBackStack) {
            transaction.addToBackStack(null)
        }

        transaction.commit()
    }

    fun showDirectoryFragment() {
        showFragment(DirectoryFragment(), addToBackStack = true)
    }

    fun showPlantsListFragment(categoryName: String, plants: List<Plant>) {
        val fragment = PlantListFragment.newInstance(categoryName, plants)
        showFragment(fragment, addToBackStack = true)
    }

    fun debugUserPlants() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val userPlants = withContext(Dispatchers.IO) {
                    database.plantDao().getUserPlantsWithDetails()
                }

                Log.d("DEBUG", "=== USER PLANTS ===")
                Log.d("DEBUG", "Total: ${userPlants.size}")

                userPlants.forEachIndexed { index, userPlant ->
                    Log.d("DEBUG", "${index + 1}. ${userPlant.plant.name} (id: ${userPlant.plant.id})")
                }

                if (userPlants.isEmpty()) {
                    Log.d("DEBUG", "No plants in user garden")
                }

            } catch (e: Exception) {
                Log.e("DEBUG", "Error getting user plants", e)
            }
        }
    }
}