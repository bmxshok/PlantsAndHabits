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

    @Query("SELECT * FROM plants WHERE categoryId = :categoryId ORDER BY name")
    suspend fun getPlantsByCategory(categoryId: Int): List<Plant>

    @Query("SELECT * FROM plants WHERE id = :plantId")
    suspend fun getPlantById(plantId: Int): Plant?

    @Query("SELECT * FROM plants")
    suspend fun getAllPlants(): List<Plant>

    // Растения пользователя
    @Insert
    suspend fun insertUserPlant(userPlant: UserPlant): Long

    @Query("DELETE FROM user_plants WHERE plantId = :plantId")
    suspend fun removeUserPlant(plantId: Int)

    @Query("SELECT COUNT(*) FROM user_plants WHERE plantId = :plantId")
    suspend fun isPlantInGarden(plantId: Int): Int

    @Transaction
    @Query("SELECT p.*, up.customName, up.customImage, up.addedDate FROM plants p INNER JOIN user_plants up ON p.id = up.plantId ORDER BY up.addedDate DESC")
    suspend fun getUserPlantsWithDetails(): List<UserPlantWithDetails>
}