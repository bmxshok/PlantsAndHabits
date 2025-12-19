package com.example.plantsandhabits

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyPlantsFragment : Fragment() {

    private lateinit var database: AppDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserPlantAdapter
    private val userPlants = mutableListOf<UserPlantWithDetails>()
    
    private lateinit var btnPlants: Button
    private lateinit var btnReminders: Button
    private lateinit var btnAddAction: Button
    private lateinit var containerContent: FrameLayout
    
    private var isPlantsTab = true
    private var remindersFragment: RemindersFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = (requireActivity() as DatabaseProvider).database
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_plants, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Сохраняем исходные отступы из layout
        val originalPaddingTop = view.paddingTop
        val originalPaddingLeft = view.paddingLeft
        val originalPaddingRight = view.paddingRight
        val originalPaddingBottom = view.paddingBottom

        // Добавляем отступы для статус-бара (используем исходные значения, чтобы избежать накопления)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                originalPaddingLeft,
                originalPaddingTop + systemBars.top,
                originalPaddingRight,
                originalPaddingBottom
            )
            insets
        }

        btnPlants = view.findViewById(R.id.btnPlants)
        btnReminders = view.findViewById(R.id.btnReminders)
        btnAddAction = view.findViewById(R.id.btnAddAction)
        containerContent = view.findViewById(R.id.containerContent)

        setupTabs()
        setupRecyclerView(view)
        loadUserPlants()
        showPlantsTab()
        
        Log.d("MyPlantsFragment", "Fragment created, loading user plants...")
    }

    private fun setupTabs() {
        btnPlants.setOnClickListener {
            if (!isPlantsTab) {
                isPlantsTab = true
                showPlantsTab()
            }
        }

        btnReminders.setOnClickListener {
            if (isPlantsTab) {
                isPlantsTab = false
                showRemindersTab()
            }
        }

        btnAddAction.setOnClickListener {
            if (isPlantsTab) {
                // Открыть справочник для добавления растения
                (requireActivity() as MainActivity).showDirectoryFragment()
            } else {
                // Открыть экран добавления напоминания
                openAddReminder()
            }
        }
    }

    private fun showPlantsTab() {
        // Обновляем стили кнопок
        btnPlants.setBackgroundResource(R.drawable.bg_tab_selected)
        btnReminders.setBackgroundResource(R.drawable.bg_tab_unselected)
        // Цвет текста всегда dark_green для обеих кнопок
        
        // Обновляем текст кнопки действия
        btnAddAction.text = "Добавить растение"
        
        // Показываем RecyclerView с растениями, скрываем контейнер с напоминаниями
        recyclerView.visibility = View.VISIBLE
        containerContent.visibility = View.GONE
    }

    private fun showRemindersTab() {
        // Обновляем стили кнопок
        btnReminders.setBackgroundResource(R.drawable.bg_tab_selected)
        btnPlants.setBackgroundResource(R.drawable.bg_tab_unselected)
        // Цвет текста всегда dark_green для обеих кнопок
        
        // Обновляем текст кнопки действия
        btnAddAction.text = "Добавить напоминание"
        
        // Скрываем RecyclerView с растениями, показываем контейнер с напоминаниями
        recyclerView.visibility = View.GONE
        containerContent.visibility = View.VISIBLE
        
        // Показываем фрагмент с напоминаниями
        if (remindersFragment == null) {
            remindersFragment = RemindersFragment()
            childFragmentManager.beginTransaction()
                .replace(R.id.containerContent, remindersFragment!!)
                .commit()
        }
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.rvPlants)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = UserPlantAdapter(userPlants) { userPlant ->
            onUserPlantClick(userPlant)
        }
        recyclerView.adapter = adapter
    }

    private fun loadUserPlants() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val plants = withContext(Dispatchers.IO) {
                    database.plantDao().getUserPlantsWithDetails()
                }
                Log.d("MyPlantsFragment", "Loaded ${plants.size} user plants")
                userPlants.clear()
                userPlants.addAll(plants)
                adapter.notifyDataSetChanged()
                if (plants.isEmpty()) {
                    Log.d("MyPlantsFragment", "No plants in garden yet")
                }
            } catch (e: Exception) {
                Log.e("MyPlantsFragment", "Error loading user plants", e)
            }
        }
    }

    private fun onUserPlantClick(userPlant: UserPlantWithDetails) {
        Log.d("MyPlantsFragment", "Opening plant: ${userPlant.plant.name}")
        val intent = Intent(requireActivity(), NoMenuActivity::class.java).apply {
            putExtra("screen_type", "user_plant_detail")
            putExtra("user_plant", userPlant)
        }
        startActivity(intent)
    }

    private fun openAddReminder() {
        val intent = Intent(requireActivity(), NoMenuActivity::class.java).apply {
            putExtra("screen_type", "add_reminder")
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadUserPlants()
        remindersFragment?.onResume()
        Log.d("MyPlantsFragment", "Fragment resumed, refreshing data")
    }
}