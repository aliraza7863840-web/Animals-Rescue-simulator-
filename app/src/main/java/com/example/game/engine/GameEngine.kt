package com.example.game.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.game.audio.GameAudioSynth
import com.example.game.data.AnimalEntity
import com.example.game.data.GameProgress
import kotlin.math.*
import kotlin.random.Random

// Render elements list
data class RenderObject(
    val depth: Float,
    val draw: (canvasWidth: Float, canvasHeight: Float) -> Unit
)

// Gold Coin entity
data class GameCoin(
    val id: Int,
    val x: Float,
    val y: Float,
    val amount: Int = 5,
    val isCollected: Boolean = false,
    val scalePulse: Float = 1f,
    val bounceOffset: Float = 0f
)

// Active Particle Model
data class GameParticle(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var color: Color,
    var radius: Float,
    var alpha: Float,
    var life: Int,
    val maxLife: Int
)

// Map boundaries
const val MAP_LIMIT = 2000f

class GameEngine {
    // Player horizontal map coords + elevation z
    var playerX = 0f
    var playerY = -120f // start slightly below camp
    var playerZ = 0f
    var velocityX = 0f
    var velocityY = 0f
    var velocityZ = 0f
    var isJumping = false

    // Player direction angle
    var playerAngle = 0f
    var playerScaleX = 1f
    var playerMovingTicks = 0

    // Player stats
    var stamina = 100f
    var maxStamina = 100f
    var health = 100f
    var coins = 0

    // Camera targets & lag smooth values
    var cameraX = 0f
    var cameraY = -120f

    // Day & Night Cycle params (0.0 to 1.0)
    var dayCycleProgress = 0.25f // Start at sunrise/morning
    var cycleSpeed = 0.0003f // day increment per tick (~2 min cycle)

    // Upgrades modifiers
    var upgradeSpeedLvl = 1
    var upgradeStaminaLvl = 1
    
    // Vehicle mechanics
    var hasVehicle = false
    var useVehicle = false

    // Camera angles mechanic: 0 = 2.5D Isometric, 1 = Top-Down Ortho, 2 = Immersive Chase
    var cameraAngleMode = 0

    // Core arrays
    var activeMissionsCompleted = 0
    var activeMissionId = 0 // 0=Rabbit, 1=Dog, 2=Deer, 3=Fox, 4=Panda

    // Static environmental colliders
    val treesList = ArrayList<Offset>()
    val rocksList = ArrayList<Offset>()
    val coinsList = ArrayList<GameCoin>()
    val particlesList = ArrayList<GameParticle>()

    // Local cached animals (synchronized with Room)
    var gameAnimals = ArrayList<AnimalEntity>()

    // Historic player path tracking for follow train
    private val playerPathHistory = ArrayList<Offset>()
    private val historyMaxSize = 300

    init {
        generateStaticMap()
    }

    private fun generateStaticMap() {
        // Seeds deterministically so forests look identical every play session
        val random = Random(42)

        // Generate Trees scattered across coordinates (avoid camp at center 0,0)
        for (i in 0 until 120) {
            val r = 180f + random.nextFloat() * 1750f
            val angle = random.nextFloat() * 2f * Math.PI
            val tx = (r * cos(angle)).toFloat()
            val ty = (r * sin(angle)).toFloat()
            treesList.add(Offset(tx, ty))
        }

        // Generate Rocks
        for (i in 0 until 45) {
            val r = 220f + random.nextFloat() * 1650f
            val angle = random.nextFloat() * 2f * Math.PI
            val rx = (r * cos(angle)).toFloat()
            val ry = (r * sin(angle)).toFloat()
            rocksList.add(Offset(rx, ry))
        }

        // Programmatic Gold Coin Spawns scattered
        spawnCoins()
    }

    private fun spawnCoins() {
        coinsList.clear()
        val random = Random(123)
        for (i in 0 until 80) {
            val cx = -MAP_LIMIT + random.nextFloat() * (MAP_LIMIT * 2)
            val cy = -MAP_LIMIT + random.nextFloat() * (MAP_LIMIT * 2)
            // check distance from camp campfire
            if (sqrt(cx * cx + cy * cy) > 130f) {
                coinsList.add(
                    GameCoin(
                        id = i,
                        x = cx,
                        y = cy,
                        amount = 5 + random.nextInt(6),
                        bounceOffset = random.nextFloat() * 2f * Math.PI.toFloat()
                    )
                )
            }
        }
    }

    // Proportions mapping to dynamic grid depending on cameraAngleMode
    fun mapToScreen(
        x: Float,
        y: Float,
        z: Float,
        width: Float,
        height: Float
    ): Offset {
        val dx = x - cameraX
        val dy = y - cameraY
        return when (cameraAngleMode) {
            1 -> {
                // Camera Angle 1: Direct overhead Top-Down Orthogonal view
                val sx = dx * 0.65f
                val sy = dy * 0.65f - z
                Offset(width / 2f + sx, height / 2f + sy)
            }
            2 -> {
                // Camera Angle 2: Close-up Immersive Chase view (heightened depth)
                val sx = (dx - dy) * 1.25f
                val sy = (dx + dy) * 0.5f - z * 1.25f
                Offset(width / 2f + sx, height / 2f + sy)
            }
            else -> {
                // Camera Angle 0: Classic 2.5D Isometric (Standard projection)
                val sx = (dx - dy) * 0.75f
                val sy = (dx + dy) * 0.433f - z
                Offset(width / 2f + sx, height / 2f + sy)
            }
        }
    }

    // Quick radial bounds collision check with slide vectors
    private fun resolveObstacleCollisions(targetRadius: Float) {
        // Wall boundaries
        if (playerX < -MAP_LIMIT) playerX = -MAP_LIMIT
        if (playerX > MAP_LIMIT) playerX = MAP_LIMIT
        if (playerY < -MAP_LIMIT) playerY = -MAP_LIMIT
        if (playerY > MAP_LIMIT) playerY = MAP_LIMIT

        // River boundary: River crosses at Y horizontal band [+350 to +430]
        // log bridge is at X range [-50, 50]
        val riverMinY = 320f
        val riverMaxY = 400f
        val onBridge = playerX in -45f..45f

        if (!onBridge && playerY in riverMinY..riverMaxY) {
            // Push out of river based on previous position representation
            val distToTop = abs(playerY - riverMinY)
            val distToBottom = abs(playerY - riverMaxY)
            playerY = if (distToTop < distToBottom) riverMinY - 2f else riverMaxY + 2f
            health = (health - 0.5f).coerceAtLeast(10f) // Small health reduction for water dampening
        }

        // Tree bounds checks (tree collider radius ~30f)
        for (tree in treesList) {
            val dx = playerX - tree.x
            val dy = playerY - tree.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < targetRadius + 22f) {
                // simple vector force separation
                val overlap = (targetRadius + 22f) - dist
                val pushX = (dx / dist) * overlap
                val pushY = (dy / dist) * overlap
                playerX += pushX
                playerY += pushY
            }
        }

        // Rock bounds checks (rock collider radius ~40f)
        for (rock in rocksList) {
            val dx = playerX - rock.x
            val dy = playerY - rock.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < targetRadius + 30f) {
                val overlap = (targetRadius + 30f) - dist
                val pushX = (dx / dist) * overlap
                val pushY = (dy / dist) * overlap
                playerX += pushX
                playerY += pushY
            }
        }
    }

    // Game single tick cycle loop updates
    fun updateTick(
        joystickDx: Float,
        joystickDy: Float,
        audio: GameAudioSynth,
        onMissionsUpdated: (GameProgress) -> Unit // db hook
    ) {
        // 1. DayCycle Progress Increment
        dayCycleProgress = (dayCycleProgress + cycleSpeed) % 1f

        // Upgrades stats factoring
        val baseSpeed = 4.5f + (upgradeSpeedLvl.toFloat() * 0.7f)
        val speedMultiplier = if (useVehicle) baseSpeed * 1.8f else baseSpeed
        val staminaDrain = 0.3f - (upgradeStaminaLvl.toFloat() * 0.04f)

        // 2. Character Movement Force & Joysticks
        val forceMagnitude = sqrt(joystickDx * joystickDx + joystickDy * joystickDy)
        val isMoving = forceMagnitude > 0.01f

        if (isMoving) {
            // Check Stamina limit
            val forceScale = if (useVehicle) 1f else (if (stamina > 5f) 1f else 0.45f) // Fatigue penalty bypassed on ATV

            velocityX = joystickDx * speedMultiplier * forceScale
            velocityY = joystickDy * speedMultiplier * forceScale

            // Angle determination
            playerAngle = (atan2(joystickDy, joystickDx) * 180 / Math.PI).toFloat()
            playerScaleX = if (joystickDx < 0) -1f else 1f
            playerMovingTicks++

            // Stamina depletion while running
            if (useVehicle) {
                stamina = (stamina + 0.15f).coerceAtMost(maxStamina)
            } else {
                stamina = (stamina - staminaDrain * forceMagnitude).coerceAtLeast(0f)
            }
        } else {
            // Sliding friction decay
            velocityX *= 0.70f
            velocityY *= 0.70f
            playerMovingTicks = 0
            // Stamina recovery
            stamina = (stamina + 0.35f).coerceAtMost(maxStamina)
        }

        playerX += velocityX
        playerY += velocityY

        // Resolve basic solid collisions
        resolveObstacleCollisions(24f)

        // 3. Jump Physics State Machine
        if (isJumping) {
            playerZ += velocityZ
            velocityZ -= 1.0f // Gravity constant

            // Landing check
            if (playerZ <= 0.0f) {
                playerZ = 0.0f
                velocityZ = 0.0f
                isJumping = false
                // Spark physical puff particles
                spawnBurstParticles(playerX, playerY, 0f, Color(0xFFC29F78), 10)
            }
        }

        // 4. Camera Smooth Lerping
        cameraX += (playerX - cameraX) * 0.12f
        cameraY += (playerY - cameraY) * 0.12f

        // 5. Track player history for follow train mechanics
        playerPathHistory.add(Offset(playerX, playerY - (playerZ * 0.2f))) // project z partly on historical follow lines
        if (playerPathHistory.size > historyMaxSize) {
            playerPathHistory.removeAt(0)
        }

        // 6. Coins Capture Mechanic & Anim Pulse Ticks
        for (i in coinsList.indices) {
            val coin = coinsList[i]
            if (!coin.isCollected) {
                val dx = playerX - coin.x
                val dy = playerY - coin.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < 40f && playerZ < 30f) { // coin collected!
                    coinsList[i] = coin.copy(isCollected = true)
                    coins += coin.amount
                    audio.playCoinSfx()
                    // Burst glowing particles at receipt
                    spawnBurstParticles(coin.x, coin.y, 10f, Color(0xFFFFD700), 12)
                }
            }
        }

        // 7. Update Rescued Animals follow logic & delivery check
        var followIndex = 1
        for (i in gameAnimals.indices) {
            val animal = gameAnimals[i]
            if (animal.isRescued && !animal.isSaved) {
                // Retrieve a delayed offset coordinate from path history queue
                val historyOffset = (followIndex * 40).coerceAtMost(playerPathHistory.size - 1)
                if (historyOffset < playerPathHistory.size && playerPathHistory.isNotEmpty()) {
                    val pastPos = playerPathHistory[playerPathHistory.size - 1 - historyOffset]
                    // Write follow updates directly
                    gameAnimals[i] = animal.copy(
                        mapX = pastPos.x,
                        mapY = pastPos.y
                    )
                }
                followIndex++
                
                // Camp delivery checkpoint: check if this animal reached the camp base!
                // camp center is at 0,0, radius ~130
                val distToCamp = sqrt(animal.mapX * animal.mapX + animal.mapY * animal.mapY)
                if (distToCamp < 140f) {
                    // Success! Saved completely inside Veterinary camp
                    gameAnimals[i] = animal.copy(
                        isRescued = false,
                        isSaved = true,
                        isTrapped = false,
                        mapX = -80f + (followIndex * 30f), // corral inside paddock
                        mapY = -120f + (Random.nextFloat() * 40f)
                    )
                    coins += 80 // massive bonus!
                    activeMissionsCompleted++
                    audio.playRescueSfx()
                    spawnBurstParticles(0f, -100f, 20f, Color(0xFF4CAF50), 25)

                    // Forward callbacks to persistent scope binder
                    val nextMissionId = (activeMissionId + 1).coerceAtMost(4)
                    onMissionsUpdated(
                        GameProgress(
                            coins = coins,
                            upgradeSpeed = upgradeSpeedLvl,
                            upgradeStamina = upgradeStaminaLvl,
                            activeMissionId = nextMissionId,
                            totalAnimalsRescuedCount = activeMissionsCompleted
                        )
                    )
                }
            }
        }

        // 8. Update active particle physics animation ticks
        updateParticles()
    }

    // Burst Particle Generator helper
    fun spawnBurstParticles(mx: Float, my: Float, mz: Float, color: Color, count: Int) {
        val random = Random.Default
        for (p in 0 until count) {
            val rPower = 2f + random.nextFloat() * 4f
            val rads = random.nextFloat() * 2 * Math.PI
            particlesList.add(
                GameParticle(
                    x = mx,
                    y = my,
                    z = mz,
                    vx = (rPower * cos(rads)).toFloat(),
                    vy = (rPower * sin(rads)).toFloat(),
                    vz = 4f + random.nextFloat() * 8f,
                    color = color,
                    radius = 4f + random.nextFloat() * 6f,
                    alpha = 1f,
                    life = 25 + random.nextInt(20),
                    maxLife = 45
                )
            )
        }
    }

    private fun updateParticles() {
        val iterator = particlesList.iterator()
        while (iterator.hasNext()) {
            val part = iterator.next()
            part.x += part.vx
            part.y += part.vy
            part.z += part.vz
            part.vz -= 0.5f // Gravity pulling particles

            part.life--
            part.alpha = part.life.toFloat() / part.maxLife.toFloat()

            if (part.z <= 0f) {
                part.z = 0f
                part.vx *= 0.5f // ground friction
                part.vy *= 0.5f
            }

            if (part.life <= 0) {
                iterator.remove()
            }
        }
    }

    // Triggers Jump sequence if player has stamina and isn't already jump-airborne
    fun triggerPlayerJump() {
        if (!isJumping && stamina > 15f) {
            isJumping = true
            velocityZ = 13.5f // Launch impulse
            stamina = (stamina - 12f).coerceAtLeast(0f)
            spawnBurstParticles(playerX, playerY, 1f, Color(0xEEDDDDDD), 6)
        }
    }

    // Handles the core action of freeing animals
    fun handleInteractAction(audio: GameAudioSynth) {
        // Search if player is in range of their current untrapped mission animal, or any trapped animal,
        for (i in gameAnimals.indices) {
            val animal = gameAnimals[i]
            if (animal.isTrapped && !animal.isRescued && !animal.isSaved) {
                // Must be current mission target to rescue (or lets allow free rescue anytime!)
                val isTarget = (animal.id == activeMissionId)
                if (isTarget) {
                    val dx = playerX - animal.mapX
                    val dy = playerY - animal.mapY
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < 80f && playerZ < 40f) { // In range of interaction!
                        // Rescue successful! Transition status
                        gameAnimals[i] = animal.copy(
                            isTrapped = false,
                            isRescued = true
                        )
                        audio.playRescueSfx()
                        
                        // Fire magnificent confetti effects at rescue point!
                        spawnBurstParticles(animal.mapX, animal.mapY, 12f, Color(0xFF00FFCC), 30)
                        spawnBurstParticles(animal.mapX, animal.mapY, 20f, Color(0xFFFF4081), 20)
                        break
                    }
                }
            }
        }
    }

    // Synergize local memory state from persistence flows
    fun syncWithDatabaseProgress(progress: GameProgress, syncedAnimalsList: List<AnimalEntity>) {
        this.coins = progress.coins
        this.upgradeSpeedLvl = progress.upgradeSpeed
        this.upgradeStaminaLvl = progress.upgradeStamina
        this.activeMissionsCompleted = progress.totalAnimalsRescuedCount
        this.activeMissionId = progress.activeMissionId
        this.hasVehicle = progress.hasVehicle
        this.useVehicle = progress.useVehicle
        
        // Sync static animal objects
        gameAnimals.clear()
        gameAnimals.addAll(syncedAnimalsList)
    }
}
