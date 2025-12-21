package com.example.plantsandhabits

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PlantDao {
    @Insert
    suspend fun insertCategory(category: Category): Long

    @Query("SELECT * FROM categories ORDER BY name")
    suspend fun getAllCategories(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoriesCount(): Int

    // Растения
    @Insert
    suspend fun insertPlant(plant: Plant): Long

    @Query("SELECT * FROM plants WHERE categoryId = :categoryId AND name != '_MANUAL_PLACEHOLDER_' ORDER BY name")
    suspend fun getPlantsByCategory(categoryId: Int): List<Plant>

    @Query("SELECT * FROM plants WHERE id = :plantId")
    suspend fun getPlantById(plantId: Int): Plant?

    @Query("SELECT * FROM plants WHERE name != '_MANUAL_PLACEHOLDER_'")
    suspend fun getAllPlants(): List<Plant>

    @Query("SELECT * FROM plants WHERE name = '_MANUAL_PLACEHOLDER_' LIMIT 1")
    suspend fun getManualPlantPlaceholder(): Plant?

    // Растения пользователя
    @Insert
    suspend fun insertUserPlant(userPlant: UserPlant): Long

    @Query("UPDATE user_plants SET customName = :customName, customImage = :customImage WHERE id = :userPlantId")
    suspend fun updateUserPlant(userPlantId: Long, customName: String?, customImage: String?): Int

    @Query("DELETE FROM user_plants WHERE id = :userPlantId")
    suspend fun removeUserPlantById(userPlantId: Long): Int

    @Query("SELECT * FROM user_plants WHERE plantId = :plantId LIMIT 1")
    suspend fun getUserPlantByPlantId(plantId: Int): UserPlant?

    @Query("SELECT COUNT(*) FROM user_plants WHERE plantId = :plantId")
    suspend fun isPlantInGarden(plantId: Int): Int

    @Transaction
    @Query("SELECT p.*, up.id as userPlantId, up.customName, up.customImage, up.plantType, up.addedDate FROM plants p INNER JOIN user_plants up ON p.id = up.plantId ORDER BY up.addedDate DESC")
    suspend fun getUserPlantsWithDetails(): List<UserPlantWithDetails>

    // Напоминания
    @Insert
    suspend fun insertReminder(reminder: Reminder): Long

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: Int): Int

    @Query("UPDATE reminders SET nextTriggerAt = :nextTriggerAt WHERE id = :reminderId")
    suspend fun updateReminderNextTrigger(reminderId: Int, nextTriggerAt: Long): Int

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getReminderById(reminderId: Int): Reminder?

    @Query(
        """
        SELECT r.id, r.userPlantId, r.workType, r.periodValue, r.periodUnit, r.hour, r.minute, r.nextTriggerAt,
               p.name AS plantName, up.customName AS customName, up.plantType AS plantType
        FROM reminders r
        INNER JOIN user_plants up ON r.userPlantId = up.id
        INNER JOIN plants p ON up.plantId = p.id
        ORDER BY r.nextTriggerAt ASC
        """
    )
    suspend fun getRemindersWithDetails(): List<ReminderWithDetails>

    @Query(
        """
        SELECT r.id, r.userPlantId, r.workType, r.periodValue, r.periodUnit, r.hour, r.minute, r.nextTriggerAt,
               p.name AS plantName, up.customName AS customName, up.plantType AS plantType
        FROM reminders r
        INNER JOIN user_plants up ON r.userPlantId = up.id
        INNER JOIN plants p ON up.plantId = p.id
        WHERE r.nextTriggerAt >= :startOfDay AND r.nextTriggerAt < :endOfDay
        ORDER BY r.hour, r.minute
        """
    )
    suspend fun getRemindersForDate(startOfDay: Long, endOfDay: Long): List<ReminderWithDetails>

    // Фотографии растений
    @Insert
    suspend fun insertPlantPhoto(plantPhoto: PlantPhoto): Long

    @Query("SELECT * FROM plant_photos WHERE userPlantId = :userPlantId ORDER BY dateAdded DESC")
    suspend fun getPlantPhotos(userPlantId: Int): List<PlantPhoto>

    @Query("DELETE FROM plant_photos WHERE id = :photoId")
    suspend fun deletePlantPhoto(photoId: Int): Int

    @Query("SELECT * FROM plant_photos WHERE id = :photoId")
    suspend fun getPlantPhotoById(photoId: Int): PlantPhoto?
}