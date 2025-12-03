package com.example.plantsandhabits

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Database(
    entities = [Category::class, Plant::class, UserPlant::class],
    version = 1,
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
                    .addCallback(AppDatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
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

            private suspend fun populateDatabase() {
                val database = getDatabase(context)
                val dao = database.plantDao()

                // 1. Вставляем категории
                val roomCategoryId = dao.insertCategory(
                    Category(name = "Комнатные растения", imageResName = "sample_category")
                ).toInt()

                val gardenCategoryId = dao.insertCategory(
                    Category(name = "Садовые цветы", imageResName = "sample_category")
                ).toInt()

                val cactusCategoryId = dao.insertCategory(
                    Category(name = "Кактусы и суккуленты", imageResName = "sample_category")
                ).toInt()

                val orchidCategoryId = dao.insertCategory(
                    Category(name = "Орхидеи", imageResName = "sample_category")
                ).toInt()

                // 2. Вставляем растения
                // Комнатные растения
                dao.insertPlant(
                    Plant(
                        categoryId = roomCategoryId,
                        name = "Диффенбахия",
                        scientificName = "Dieffenbachia",
                        description = "Популярное комнатное растение с красивыми листьями, относящееся к декоративно-лиственным. Известна своими крупными, овальными листьями с разнообразными зелеными и кремовыми узорами.",
                        careTips = "• Освещение: Яркий рассеянный свет\n• Полив: Умеренный, летом 2-3 раза в неделю\n• Температура: 18-25°C\n• Влажность: Высокая, рекомендуется опрыскивание",
                        imageResName = "sample_category"
                    )
                )

                dao.insertPlant(
                    Plant(
                        categoryId = roomCategoryId,
                        name = "Фикус Бенджамина",
                        scientificName = "Ficus benjamina",
                        description = "Вечнозеленое дерево с мелкими листьями. Популярное комнатное растение, которое очищает воздух.",
                        careTips = "• Освещение: Яркий свет, но не прямые лучи\n• Полив: Умеренный, зимой реже\n• Обрезка: Можно формировать крону\n• Пересадка: Раз в 2-3 года",
                        imageResName = "sample_category"
                    )
                )

                dao.insertPlant(
                    Plant(
                        categoryId = roomCategoryId,
                        name = "Монстера",
                        scientificName = "Monstera deliciosa",
                        description = "Крупное тропическое растение с резными листьями. Очень популярно в интерьерах.",
                        careTips = "• Освещение: Яркий рассеянный свет\n• Полив: Регулярный, но не переувлажнять\n• Опора: Нуждается в опоре для роста\n• Листья: Можно протирать влажной тряпкой",
                        imageResName = "sample_category"
                    )
                )

                // Садовые цветы
                dao.insertPlant(
                    Plant(
                        categoryId = gardenCategoryId,
                        name = "Роза",
                        scientificName = "Rosa",
                        description = "Королева цветов. Бывает разных сортов и цветов. Требует внимательного ухода.",
                        careTips = "• Местоположение: Солнечное место\n• Почва: Плодородная, дренированная\n• Обрезка: Весной и после цветения\n• Зимовка: Требует укрытия в холодных регионах",
                        imageResName = "sample_category"
                    )
                )

                dao.insertPlant(
                    Plant(
                        categoryId = gardenCategoryId,
                        name = "Тюльпан",
                        scientificName = "Tulipa",
                        description = "Весенний луковичный цветок. Бывает разных цветов и форм.",
                        careTips = "• Посадка: Осенью\n• Глубина: 3 высоты луковицы\n• После цветения: Дать листьям засохнуть естественно\n• Выкопка: Каждые 2-3 года",
                        imageResName = "sample_category"
                    )
                )

                // Кактусы и суккуленты
                dao.insertPlant(
                    Plant(
                        categoryId = cactusCategoryId,
                        name = "Кактус Опунция",
                        scientificName = "Opuntia",
                        description = "Суккулент с плоскими сегментами. Легок в уходе, подходит для начинающих.",
                        careTips = "• Освещение: Максимально яркое\n• Полив: Очень редкий, зимой почти не поливать\n• Температура: Зимой прохладное содержание (10-15°C)\n• Почва: Специальная для кактусов",
                        imageResName = "sample_category"
                    )
                )

                dao.insertPlant(
                    Plant(
                        categoryId = cactusCategoryId,
                        name = "Алоэ Вера",
                        scientificName = "Aloe vera",
                        description = "Лечебное суккулентное растение. Сок применяется для заживления ран и ожогов.",
                        careTips = "• Освещение: Яркое\n• Полив: Очень умеренный\n• Размножение: Детками\n• Пересадка: Когда горшок становится мал",
                        imageResName = "sample_category"
                    )
                )

                // Орхидеи
                dao.insertPlant(
                    Plant(
                        categoryId = orchidCategoryId,
                        name = "Фаленопсис",
                        scientificName = "Phalaenopsis",
                        description = "Самая популярная орхидея для дома. Цветет несколько месяцев.",
                        careTips = "• Освещение: Яркий рассеянный свет\n• Полив: Методом погружения\n• Субстрат: Кора, не обычная земля\n• Цветение: После периода покоя",
                        imageResName = "sample_category"
                    )
                )

                /* 3. Добавляем тестовое растение пользователю
                dao.insertUserPlant(
                    UserPlant(
                        plantId = 1,  // Диффенбахия
                        customName = "Моя любимая Диффка",
                        customImage = null
                    )
                )*/

                // 4. Логируем успех
                withContext(Dispatchers.Main) {
                    android.util.Log.d("AppDatabase", "Database populated with test data")
                }
            }
        }
    }
}