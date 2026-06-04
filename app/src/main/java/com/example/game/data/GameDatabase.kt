package com.example.game.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_progress")
data class GameProgress(
    @PrimaryKey val id: Int = 1,
    val coins: Int = 0,
    val unlockedAreas: String = "Camp", // Comma-separated: Camp,River,EastGrove,WestHill,PandaValley
    val upgradeSpeed: Int = 1, // Upgrade levels progress
    val upgradeStamina: Int = 1,
    val activeMissionId: Int = 0, // 0 to 4 corresponding to animal rescue missions
    val soundEnabled: Boolean = true,
    val totalAnimalsRescuedCount: Int = 0,
    val staminaValue: Float = 100f,
    val healthValue: Float = 100f,
    val hasVehicle: Boolean = false,
    val useVehicle: Boolean = false
)

@Entity(tableName = "animal_entities")
data class AnimalEntity(
    @PrimaryKey val id: Int, // 0 = Rabbit, 1 = Dog, 2 = Deer, 3 = Fox, 4 = Panda
    val name: String,
    val mapX: Float,
    val mapY: Float,
    val trapType: String, // CAGE, NET, LOGS, ROCKS, GRID
    val isTrapped: Boolean = true,
    val isRescued: Boolean = false, // Is currently following player to camp
    val isSaved: Boolean = false // Successfully delivered to camp
)

@Dao
interface GameProgressDao {
    @Query("SELECT * FROM game_progress WHERE id = 1 LIMIT 1")
    fun getProgress(): Flow<GameProgress?>

    @Query("SELECT * FROM game_progress WHERE id = 1 LIMIT 1")
    suspend fun getProgressDirect(): GameProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: GameProgress)
}

@Dao
interface AnimalDao {
    @Query("SELECT * FROM animal_entities")
    fun getAllAnimals(): Flow<List<AnimalEntity>>

    @Query("SELECT * FROM animal_entities")
    suspend fun getAllAnimalsDirect(): List<AnimalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnimals(animals: List<AnimalEntity>)

    @Update
    suspend fun updateAnimal(animal: AnimalEntity)

    @Query("UPDATE animal_entities SET isTrapped = 1, isRescued = 0, isSaved = 0 WHERE id = :id")
    suspend fun resetAnimal(id: Int)

    @Query("UPDATE animal_entities SET isTrapped = 1, isRescued = 0, isSaved = 0")
    suspend fun resetAllAnimals()
}

@Database(entities = [GameProgress::class, AnimalEntity::class], version = 1, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun progressDao(): GameProgressDao
    abstract fun animalDao(): AnimalDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: android.content.Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "animal_rescue_game_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
