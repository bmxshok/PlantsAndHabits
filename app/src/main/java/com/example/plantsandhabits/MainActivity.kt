package com.example.plantsandhabits

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            showFragment(HomeFragment(), addToBackStack = false)
        }

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

    fun showPlantsListFragment(categoryName: String) {
        val fragment = PlantListFragment.newInstance(categoryName)
        showFragment(fragment, addToBackStack = true)
    }
}