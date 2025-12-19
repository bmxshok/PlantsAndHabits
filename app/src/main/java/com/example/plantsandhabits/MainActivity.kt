package com.example.plantsandhabits

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
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
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Создаем канал уведомлений
        NotificationHelper.createNotificationChannel(this)
        
        // Перепланируем все напоминания при запуске
        ReminderManager.rescheduleAllReminders(this)

        val btnHome = findViewById<ImageView>(R.id.btnHome)
        val btnCalendar = findViewById<ImageView>(R.id.btnCalendar)
        val btnPlants = findViewById<ImageView>(R.id.btnPlants)
        
        if (savedInstanceState == null) {
            // Проверяем, нужно ли показать экран "Мой сад"
            if (intent.getBooleanExtra("show_my_garden", false)) {
                showFragment(MyPlantsFragment(), addToBackStack = false)
                setButtonSelected(btnPlants)
            } else {
                showFragment(HomeFragment(), addToBackStack = false)
                setButtonSelected(btnHome)
            }
        } else {
            // Восстанавливаем состояние кнопки на основе текущего фрагмента
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            when (currentFragment) {
                is HomeFragment -> setButtonSelected(btnHome)
                is CalendarFragment -> setButtonSelected(btnCalendar)
                is MyPlantsFragment -> setButtonSelected(btnPlants)
                else -> setButtonSelected(btnHome)
            }
        }
        
        setupBottomNavigation()
        debugUserPlants()
    }

    private var currentSelectedButton: ImageView? = null

    private fun setupBottomNavigation() {
        val btnHome = findViewById<ImageView>(R.id.btnHome)
        val btnCalendar = findViewById<ImageView>(R.id.btnCalendar)
        val btnPlants = findViewById<ImageView>(R.id.btnPlants)

        btnHome.setOnClickListener {
            setButtonSelected(btnHome)
            showFragment(HomeFragment(), addToBackStack = false)
        }

        btnCalendar.setOnClickListener {
            setButtonSelected(btnCalendar)
            showFragment(CalendarFragment(), addToBackStack = false)
        }

        btnPlants.setOnClickListener {
            setButtonSelected(btnPlants)
            showFragment(MyPlantsFragment(), addToBackStack = false)
        }
    }

    private fun setButtonSelected(button: ImageView) {
        // Сбрасываем предыдущую кнопку
        currentSelectedButton?.let {
            it.isSelected = false
            it.alpha = 1.0f
            it.setColorFilter(null)
            // Скрываем круг предыдущей кнопки
            val prevContainer = it.parent as? android.view.ViewGroup
            prevContainer?.findViewById<View>(when (it.id) {
                R.id.btnHome -> R.id.circleHome
                R.id.btnCalendar -> R.id.circleCalendar
                R.id.btnPlants -> R.id.circlePlants
                else -> 0
            })?.visibility = View.GONE
        }

        // Устанавливаем новую выбранную кнопку
        button.isSelected = true
        button.alpha = 1.0f
        // Делаем иконку белой
        button.setColorFilter(resources.getColor(android.R.color.white, null))
        // Показываем круг для выбранной кнопки
        val container = button.parent as? android.view.ViewGroup
        container?.findViewById<View>(when (button.id) {
            R.id.btnHome -> R.id.circleHome
            R.id.btnCalendar -> R.id.circleCalendar
            R.id.btnPlants -> R.id.circlePlants
            else -> 0
        })?.visibility = View.VISIBLE
        currentSelectedButton = button
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

    fun showRemindersFragment() {
        showFragment(RemindersFragment(), addToBackStack = true)
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