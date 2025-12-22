package com.example.plantsandhabits

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Database(
    entities = [Category::class, Plant::class, UserPlant::class, Reminder::class, PlantPhoto::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "plants_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(AppDatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE user_plants ADD COLUMN plantType TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userPlantId INTEGER NOT NULL,
                        workType TEXT NOT NULL,
                        periodValue INTEGER NOT NULL,
                        periodUnit TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        nextTriggerAt INTEGER NOT NULL,
                        FOREIGN KEY(userPlantId) REFERENCES user_plants(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plant_photos(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userPlantId INTEGER NOT NULL,
                        photoPath TEXT NOT NULL,
                        dateAdded INTEGER NOT NULL,
                        FOREIGN KEY(userPlantId) REFERENCES user_plants(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        private class AppDatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Этот метод вызывается ТОЛЬКО при первом создании БД
                CoroutineScope(Dispatchers.IO).launch {
                    populateDatabase()
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Этот метод вызывается при каждом открытии БД
                // Проверяем и добавляем недостающие категории и растения
                CoroutineScope(Dispatchers.IO).launch {
                    ensureCategoriesExist()
                    ensurePlantsExist()
                }
            }

            /**
             * Проверяет наличие всех необходимых категорий и добавляет недостающие
             */
            private suspend fun ensureCategoriesExist() {
                val database = getDatabase(context)
                val dao = database.plantDao()

                // Получаем все существующие категории
                val existingCategories = dao.getAllCategories()
                val existingCategoriesMap = existingCategories.associateBy { it.name }

                // Список всех необходимых категорий
                val requiredCategories = listOf(
                    Category(name = "Цветущие", imageResName = "ic_flowering"),
                    Category(name = "Лиственные", imageResName = "ic_leafy"),
                    Category(name = "Суккуленты", imageResName = "ic_succulent"),
                    Category(name = "Съедобные", imageResName = "ic_edible"),
                    Category(name = "Папоротники", imageResName = "ic_fern")
                )

                // Добавляем новые категории или обновляем параметры существующих
                for (category in requiredCategories) {
                    val existingCategory = existingCategoriesMap[category.name]
                    if (existingCategory == null) {
                        // Категории нет - добавляем
                        dao.insertCategory(category)
                    } else if (existingCategory.imageResName != category.imageResName) {
                        // Категория есть, но параметры изменились - обновляем
                        dao.updateCategoryImage(existingCategory.id, category.imageResName)
                    }
                }
            }

            /**
             * Проверяет наличие всех необходимых растений и добавляет недостающие
             */
            private suspend fun ensurePlantsExist() {
                val database = getDatabase(context)
                val dao = database.plantDao()

                // Сначала убеждаемся, что категории существуют
                ensureCategoriesExist()
                
                // Получаем все существующие растения
                val existingPlants = dao.getAllPlants()
                val existingPlantsMap = existingPlants.associateBy { it.name }

                // Получаем ID категорий для использования при создании растений
                val allCategories = dao.getAllCategories()
                val floweringCategoryId = allCategories.find { it.name == "Цветущие" }?.id ?: 0
                val leafyCategoryId = allCategories.find { it.name == "Лиственные" }?.id ?: 0
                val succulentCategoryId = allCategories.find { it.name == "Суккуленты" }?.id ?: 0
                val edibleCategoryId = allCategories.find { it.name == "Съедобные" }?.id ?: 0
                val fernCategoryId = allCategories.find { it.name == "Папоротники" }?.id ?: 0

                // Получаем список всех необходимых растений
                val requiredPlants = getAllRequiredPlants(
                    floweringCategoryId,
                    leafyCategoryId,
                    succulentCategoryId,
                    edibleCategoryId,
                    fernCategoryId
                )

                // Добавляем новые растения или обновляем параметры существующих
                for (plant in requiredPlants) {
                    val existingPlant = existingPlantsMap[plant.name]
                    if (existingPlant == null) {
                        // Растения нет - добавляем
                        dao.insertPlant(plant)
                    } else {
                        // Проверяем, изменились ли какие-либо параметры
                        val hasChanges = existingPlant.categoryId != plant.categoryId ||
                                existingPlant.scientificName != plant.scientificName ||
                                existingPlant.description != plant.description ||
                                existingPlant.careTips != plant.careTips ||
                                existingPlant.imageResName != plant.imageResName
                        
                        if (hasChanges) {
                            // Растение есть, но параметры изменились - обновляем все поля
                            dao.updatePlant(
                                existingPlant.id,
                                plant.categoryId,
                                plant.scientificName,
                                plant.description,
                                plant.careTips,
                                plant.imageResName
                            )
                        }
                    }
                }

                // Проверяем наличие placeholder для ручных растений
                val existingPlaceholder = dao.getManualPlantPlaceholder()
                if (existingPlaceholder == null && floweringCategoryId != 0) {
                    dao.insertPlant(
                        Plant(
                            categoryId = floweringCategoryId,
                            name = "_MANUAL_PLACEHOLDER_",
                            scientificName = null,
                            description = "",
                            careTips = "",
                            imageResName = "sample_category"
                        )
                    )
                }
            }

            /**
             * Возвращает список всех необходимых растений
             * Добавляйте сюда новые растения для автоматического добавления при запуске
             */
            private fun getAllRequiredPlants(
                floweringCategoryId: Int,
                leafyCategoryId: Int,
                succulentCategoryId: Int,
                edibleCategoryId: Int,
                fernCategoryId: Int
            ): List<Plant> {
                return listOf(
                    // Комнатные растения
                    Plant(
                        categoryId = floweringCategoryId,
                        name = "Диффенбахия",
                        scientificName = "Dieffenbachia",
                        description = "Популярное комнатное растение с красивыми листьями, относящееся к декоративно-лиственным. Известна своими крупными, овальными листьями с разнообразными зелеными и кремовыми узорами.",
                        careTips = "• Освещение: Яркий рассеянный свет\n• Полив: Умеренный, летом 2-3 раза в неделю\n• Температура: 18-25°C\n• Влажность: Высокая, рекомендуется опрыскивание",
                        imageResName = "ic_leaf"
                    ),
                    Plant(
                        categoryId = floweringCategoryId,
                        name = "Фикус Бенджамина",
                        scientificName = "Ficus benjamina",
                        description = "Вечнозеленое дерево с мелкими листьями. Популярное комнатное растение, которое очищает воздух.",
                        careTips = "• Освещение: Яркий свет, но не прямые лучи\n• Полив: Умеренный, зимой реже\n• Обрезка: Можно формировать крону\n• Пересадка: Раз в 2-3 года",
                        imageResName = "ic_leaf"
                    ),
                    Plant(
                        categoryId = floweringCategoryId,
                        name = "Монстера",
                        scientificName = "Monstera deliciosa",
                        description = "Крупное тропическое растение с резными листьями. Очень популярно в интерьерах.",
                        careTips = "• Освещение: Яркий рассеянный свет\n• Полив: Регулярный, но не переувлажнять\n• Опора: Нуждается в опоре для роста\n• Листья: Можно протирать влажной тряпкой",
                        imageResName = "ic_leaf"
                    ),
                    // Садовые цветы
                    Plant(
                        categoryId = leafyCategoryId,
                        name = "Роза",
                        scientificName = "Rosa",
                        description = "Королева цветов. Бывает разных сортов и цветов. Требует внимательного ухода.",
                        careTips = "• Местоположение: Солнечное место\n• Почва: Плодородная, дренированная\n• Обрезка: Весной и после цветения\n• Зимовка: Требует укрытия в холодных регионах",
                        imageResName = "ic_leaf"
                    ),
                    Plant(
                        categoryId = leafyCategoryId,
                        name = "Тюльпан",
                        scientificName = "Tulipa",
                        description = "Весенний луковичный цветок. Бывает разных цветов и форм.",
                        careTips = "• Посадка: Осенью\n• Глубина: 3 высоты луковицы\n• После цветения: Дать листьям засохнуть естественно\n• Выкопка: Каждые 2-3 года",
                        imageResName = "ic_leaf"
                    ),
                    // Кактусы и суккуленты
                    Plant(
                        categoryId = succulentCategoryId,
                        name = "Кактус Опунция",
                        scientificName = "Opuntia",
                        description = "Суккулент с плоскими сегментами. Легок в уходе, подходит для начинающих.",
                        careTips = "• Освещение: Максимально яркое\n• Полив: Очень редкий, зимой почти не поливать\n• Температура: Зимой прохладное содержание (10-15°C)\n• Почва: Специальная для кактусов",
                        imageResName = "ic_leaf"
                    ),
                    Plant(
                        categoryId = succulentCategoryId,
                        name = "Алоэ Вера",
                        scientificName = "Aloe vera",
                        description = "Лечебное суккулентное растение. Сок применяется для заживления ран и ожогов.",
                        careTips = "• Освещение: Яркое\n• Полив: Очень умеренный\n• Размножение: Детками\n• Пересадка: Когда горшок становится мал",
                        imageResName = "ic_leaf"
                    ),
                    // Орхидеи
                    Plant(
                        categoryId = edibleCategoryId,
                        name = "Фаленопсис",
                        scientificName = "Phalaenopsis",
                        description = "Самая популярная орхидея для дома. Цветет несколько месяцев.",
                        careTips = "• Освещение: Яркий рассеянный свет\n• Полив: Методом погружения\n• Субстрат: Кора, не обычная земля\n• Цветение: После периода покоя",
                        imageResName = "ic_leafy"
                    )
                )
            }

            private suspend fun populateDatabase() {
                // Используем ensurePlantsExist для консистентности
                // Это создаст все категории и растения при первом запуске
                ensurePlantsExist()
            }    }
    }
}