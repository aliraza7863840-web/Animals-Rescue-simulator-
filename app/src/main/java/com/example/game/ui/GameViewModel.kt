package com.example.game.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.GameAudioSynth
import com.example.game.data.AnimalEntity
import com.example.game.data.GameDatabase
import com.example.game.data.GameProgress
import com.example.game.data.GameRepository
import com.example.game.engine.GameEngine
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

enum class GameScreenState {
    MAIN_MENU,
    PLAYING,
    SETTINGS,
    UPGRADES,
    VICTORY,
    GAME_OVER
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val database = GameDatabase.getDatabase(application)
    private val repository = GameRepository(database)
    val audioSynth = GameAudioSynth()

    // Screen navigation flow
    private val _screenState = MutableStateFlow(GameScreenState.MAIN_MENU)
    val screenState: StateFlow<GameScreenState> = _screenState.asStateFlow()

    // Expose local high frame rate properties to prevent Compose latency
    var playerStamina by mutableStateOf(100f)
    var playerCoins by mutableStateOf(0)
    var activeMissionName by mutableStateOf("Rabbit")
    var activeMissionDistance by mutableStateOf(0f)
    var activeMissionUnlocked by mutableStateOf(true)

    // Game engine core
    val gameEngine = GameEngine()

    // Database reactive loops
    val progressFlow = repository.progress
    val animalsFlow = repository.animals

    init {
        // Force pre-init defaults or fetch
        viewModelScope.launch {
            val prog = repository.getProgressDirect()
            syncEngineWithDb(prog, repository.animals.run { 
                // Collect first state
                val list = database.animalDao().getAllAnimalsDirect()
                if (list.isEmpty()) {
                    repository.initializeDefaultAnimals()
                    database.animalDao().getAllAnimalsDirect()
                } else list
            })
            
            // Sync settings
            audioSynth.soundEnabled = prog.soundEnabled
        }

        // Observe continuous database changes
        viewModelScope.launch {
            progressFlow.collect { progress ->
                progress?.let {
                    audioSynth.soundEnabled = it.soundEnabled
                    playerCoins = it.coins
                    // Sync engine
                    gameEngine.coins = it.coins
                    gameEngine.upgradeSpeedLvl = it.upgradeSpeed
                    gameEngine.upgradeStaminaLvl = it.upgradeStamina
                    gameEngine.activeMissionId = it.activeMissionId
                    gameEngine.hasVehicle = it.hasVehicle
                    gameEngine.useVehicle = it.useVehicle
                }
            }
        }

        viewModelScope.launch {
            animalsFlow.collect { list ->
                if (list.isNotEmpty()) {
                    gameEngine.gameAnimals.clear()
                    gameEngine.gameAnimals.addAll(list)
                }
            }
        }
    }

    private fun syncEngineWithDb(progress: GameProgress, animals: List<AnimalEntity>) {
        gameEngine.syncWithDatabaseProgress(progress, animals)
        playerCoins = progress.coins
        playerStamina = gameEngine.stamina
    }

    // High frequency joystick movement delta feeds tick loop
    fun onGameTick(joystickDx: Float, joystickDy: Float) {
        if (_screenState.value != GameScreenState.PLAYING) return

        gameEngine.updateTick(joystickDx, -joystickDy, audioSynth) { updatedProgress ->
            // Update Room in background scope
            viewModelScope.launch(Dispatchers.IO) {
                // save
                val current = repository.getProgressDirect()
                repository.saveProgress(
                    current.copy(
                        coins = updatedProgress.coins,
                        activeMissionId = updatedProgress.activeMissionId,
                        totalAnimalsRescuedCount = updatedProgress.totalAnimalsRescuedCount
                    )
                )
                // update local targets
                database.animalDao().insertAnimals(gameEngine.gameAnimals)
            }
        }

        playerStamina = gameEngine.stamina

        // Update active mission HUD helpers
        val activeId = gameEngine.activeMissionId
        val targetAnimal = gameEngine.gameAnimals.find { it.id == activeId }
        if (targetAnimal != null) {
            activeMissionName = targetAnimal.name
            val dx = gameEngine.playerX - targetAnimal.mapX
            val dy = gameEngine.playerY - targetAnimal.mapY
            activeMissionDistance = sqrt(dx * dx + dy * dy)
            activeMissionUnlocked = isAreaUnlocked(activeId, gameEngine.upgradeSpeedLvl, gameEngine.upgradeStaminaLvl)
        } else {
            activeMissionName = "Completed All!"
            activeMissionDistance = -1f
            activeMissionUnlocked = true
        }

        // In case all 5 animals are saved, prompt victory!
        if (gameEngine.activeMissionsCompleted >= 5 && _screenState.value == GameScreenState.PLAYING) {
            _screenState.value = GameScreenState.VICTORY
        }
    }

    // Area boundaries unlock constraints based on upgrades progress
    fun isAreaUnlocked(animalId: Int, speedLvl: Int, staminaLvl: Int): Boolean {
        return when (animalId) {
            0, 1 -> true // Rabbit & Dog in starting base pastures
            2 -> speedLvl >= 2 || staminaLvl >= 2 // Deer needs Level 2 boots or heart
            3 -> speedLvl >= 3 || staminaLvl >= 3 // Fox needs Level 3 gears
            4 -> speedLvl >= 4 && staminaLvl >= 4 // Panda Valley needs Level 4 both!
            else -> true
        }
    }

    fun setScreenState(state: GameScreenState) {
        audioSynth.playClickSfx()
        _screenState.value = state
    }

    fun jumpAction() {
        gameEngine.triggerPlayerJump()
    }

    fun interactAction() {
        gameEngine.handleInteractAction(audioSynth)
        // Sync back state
        viewModelScope.launch(Dispatchers.IO) {
            database.animalDao().insertAnimals(gameEngine.gameAnimals)
        }
    }

    // Purchase attributes updates
    fun upgradeSpeedAttribute() {
        viewModelScope.launch(Dispatchers.IO) {
            val prog = repository.getProgressDirect()
            val cost = prog.upgradeSpeed * 80
            if (prog.coins >= cost && prog.upgradeSpeed < 5) {
                repository.saveProgress(
                    prog.copy(
                        coins = prog.coins - cost,
                        upgradeSpeed = prog.upgradeSpeed + 1
                    )
                )
                audioSynth.playRescueSfx()
            }
        }
    }

    fun upgradeStaminaAttribute() {
        viewModelScope.launch(Dispatchers.IO) {
            val prog = repository.getProgressDirect()
            val cost = prog.upgradeStamina * 80
            if (prog.coins >= cost && prog.upgradeStamina < 5) {
                repository.saveProgress(
                    prog.copy(
                        coins = prog.coins - cost,
                        upgradeStamina = prog.upgradeStamina + 1
                    )
                )
                audioSynth.playRescueSfx()
            }
        }
    }

    fun unlockVehicle() {
        viewModelScope.launch(Dispatchers.IO) {
            val prog = repository.getProgressDirect()
            val cost = 250
            if (prog.coins >= cost && !prog.hasVehicle) {
                repository.saveProgress(
                    prog.copy(
                        coins = prog.coins - cost,
                        hasVehicle = true,
                        useVehicle = true
                    )
                )
                audioSynth.playRescueSfx()
            }
        }
    }

    fun toggleVehicleRiding() {
        viewModelScope.launch(Dispatchers.IO) {
            val prog = repository.getProgressDirect()
            if (prog.hasVehicle) {
                val updatedUse = !prog.useVehicle
                repository.saveProgress(prog.copy(useVehicle = updatedUse))
                audioSynth.playClickSfx()
            }
        }
    }

    fun cycleCameraAngle() {
        viewModelScope.launch {
            gameEngine.cameraAngleMode = (gameEngine.cameraAngleMode + 1) % 3
            audioSynth.playClickSfx()
            // trigger sparkling particle bursts on screen context
            gameEngine.spawnBurstParticles(gameEngine.playerX, gameEngine.playerY, 15f, Color(0xFFFFD54F), 6)
        }
    }

    fun toggleAudioState() {
        viewModelScope.launch(Dispatchers.IO) {
            val prog = repository.getProgressDirect()
            val updated = !prog.soundEnabled
            repository.saveProgress(prog.copy(soundEnabled = updated))
            audioSynth.soundEnabled = updated
        }
    }

    fun restartWholeProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.resetGame()
            _screenState.value = GameScreenState.MAIN_MENU
        }
    }
}
