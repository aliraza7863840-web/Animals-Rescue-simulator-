package com.example.game.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class GameRepository(private val database: GameDatabase) {
    private val progressDao = database.progressDao()
    private val animalDao = database.animalDao()

    val progress: Flow<GameProgress?> = progressDao.getProgress()
    val animals: Flow<List<AnimalEntity>> = animalDao.getAllAnimals()

    suspend fun getProgressDirect(): GameProgress {
        var prog = progressDao.getProgressDirect()
        if (prog == null) {
            prog = GameProgress()
            progressDao.saveProgress(prog)
            initializeDefaultAnimals()
        }
        return prog
    }

    suspend fun saveProgress(progress: GameProgress) {
        progressDao.saveProgress(progress)
    }

    suspend fun updateAnimal(animal: AnimalEntity) {
        animalDao.updateAnimal(animal)
    }

    suspend fun resetGame() {
        val defaultProg = GameProgress()
        progressDao.saveProgress(defaultProg)
        initializeDefaultAnimals()
    }

    suspend fun initializeDefaultAnimals() {
        val defaultAnimals = listOf(
            AnimalEntity(
                id = 0,
                name = "Rabbit",
                mapX = -250f, // North West meadow
                mapY = 400f,
                trapType = "CAGE"
            ),
            AnimalEntity(
                id = 1,
                name = "Dog",
                mapX = 500f, // Near the River
                mapY = -150f,
                trapType = "LOGS"
            ),
            AnimalEntity(
                id = 2,
                name = "Deer",
                mapX = -300f, // Inside East Grove
                mapY = -450f,
                trapType = "NET"
            ),
            AnimalEntity(
                id = 3,
                name = "Fox",
                mapX = 650f, // West Rocky Hills
                mapY = 600f,
                trapType = "ROCKS"
            ),
            AnimalEntity(
                id = 4,
                name = "Panda",
                mapX = 100f, // Deep in Bamboo Valley
                mapY = 900f,
                trapType = "GRID"
            )
        )
        // Overwrite or create animals
        animalDao.insertAnimals(defaultAnimals)
    }
}
