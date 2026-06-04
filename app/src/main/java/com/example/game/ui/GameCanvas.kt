package com.example.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.example.game.engine.GameEngine
import com.example.game.engine.RenderObject
import com.example.game.engine.MAP_LIMIT
import com.example.game.data.AnimalEntity
import kotlin.math.*
import kotlin.random.Random

@Composable
fun GameCanvas(
    gameEngine: GameEngine,
    modifier: Modifier = Modifier,
    onJoystickMoved: (dx: Float, dy: Float) -> Unit
) {
    // Keep track of touch drag for virtual joystick placement
    var joystickOuterCenter by remember { mutableStateOf(Offset.Zero) }
    var joystickInnerCenter by remember { mutableStateOf(Offset.Zero) }
    var joystickActive by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF293B22)) // fallback grass color
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Virtual joystick activated on the LEFT half of the screen
                            if (offset.x < width / 2f) {
                                joystickOuterCenter = offset
                                joystickInnerCenter = offset
                                joystickActive = true
                            }
                        },
                        onDrag = { _, dragAmount ->
                            if (joystickActive) {
                                val dx = joystickInnerCenter.x + dragAmount.x - joystickOuterCenter.x
                                val dy = joystickInnerCenter.y + dragAmount.y - joystickOuterCenter.y
                                val dist = sqrt(dx * dx + dy * dy)
                                val maxRadius = 110f // bounds for stick dragging

                                if (dist <= maxRadius) {
                                    joystickInnerCenter = Offset(
                                        joystickOuterCenter.x + dx,
                                        joystickOuterCenter.y + dy
                                    )
                                } else {
                                    joystickInnerCenter = Offset(
                                        joystickOuterCenter.x + (dx / dist) * maxRadius,
                                        joystickOuterCenter.y + (dy / dist) * maxRadius
                                    )
                                }

                                // Send normalized movement values
                                val normX = (joystickInnerCenter.x - joystickOuterCenter.x) / maxRadius
                                val normY = (joystickInnerCenter.y - joystickOuterCenter.y) / maxRadius
                                onJoystickMoved(normX, normY)
                            }
                        },
                        onDragEnd = {
                            joystickActive = false
                            onJoystickMoved(0f, 0f)
                        },
                        onDragCancel = {
                            joystickActive = false
                            onJoystickMoved(0f, 0f)
                        }
                    )
                }
        ) {
            // Draw standard visual assets
            drawGameWorld(gameEngine, width, height)

            // Draw Virtual Joystick UI inside canvas
            if (joystickActive) {
                // Outer circle
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f),
                    radius = 90f,
                    center = joystickOuterCenter
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = 90f,
                    center = joystickOuterCenter,
                    style = Stroke(width = 4f)
                )
                // Inner button
                drawCircle(
                    color = Color(0xFFFFCC00),
                    radius = 42f,
                    center = joystickInnerCenter
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = 42f,
                    center = joystickInnerCenter,
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}

// Draw entire stylized low poly Isometric game world
private fun DrawScope.drawGameWorld(engine: GameEngine, width: Float, height: Float) {
    val dayProg = engine.dayCycleProgress

    // 1. Calculate Day-Night Ambient Tints
    // Sunrise (0.2), Noon (0.5), Sunset (0.75), Night (0.95)
    val ambientColor: Color
    val ambientAlpha: Float
    val starAlpha: Float

    if (dayProg in 0.2f..0.6f) { // Day
        ambientColor = Color(0xFFF7F4EB)
        ambientAlpha = 0.12f
        starAlpha = 0.0f
    } else if (dayProg in 0.6f..0.8f) { // Sunset transition
        val ratio = (dayProg - 0.6f) / 0.2f
        ambientColor = Color(0xFFE0533C).copy(alpha = 1f)
        ambientAlpha = 0.15f + ratio * 0.25f
        starAlpha = ratio * 0.3f
    } else if (dayProg in 0.8f..0.95f) { // Night transition
        val ratio = (dayProg - 0.8f) / 0.15f
        ambientColor = Color(0xFF0F0E26)
        ambientAlpha = 0.40f + ratio * 0.35f
        starAlpha = 0.3f + ratio * 0.7f
    } else if (dayProg in 0.95f..1.0f) { // Midnight
        ambientColor = Color(0xFF000311)
        ambientAlpha = 0.75f
        starAlpha = 1.0f
    } else { // Midnight to sunrise morning transition (0.0 to 0.2)
        val ratio = dayProg / 0.2f
        ambientColor = Color(0xFF1D1B40)
        ambientAlpha = 0.75f - ratio * 0.63f
        starAlpha = 1.0f - ratio * 1.0f
    }

    // Ground grass base fill + grid lines for low poly perspective
    drawRect(color = Color(0xFF45633C)) // Lush organic grass

    // Draw grid mesh lines under map bounds to give pseudo-3D grid depth
    val gridDist = 120f
    val centerScreen = Offset(width / 2f, height / 2f)
    
    // Draw River (Behind isometric layers)
    drawIsometricRiver(engine, width, height)

    // Gather and build render items with depth sorting (sorted by Y)
    val renderList = ArrayList<RenderObject>()

    // Core Safe Camp camp components atcenter (0,0) with campfire
    val campCenterOffset = engine.mapToScreen(0f, 0f, 0f, width, height)
    renderList.add(
        RenderObject(depth = 0f) { w, h ->
            drawRescueCampLayout(engine, campCenterOffset)
        }
    )

    // Add Trees
    for (tree in engine.treesList) {
        val screenPos = engine.mapToScreen(tree.x, tree.y, 0f, width, height)
        // Only render if visible on screen to target 60FPS
        if (screenPos.x in -100f..(width + 100f) && screenPos.y in -100f..(height + 100f)) {
            renderList.add(
                RenderObject(depth = tree.y) { w, h ->
                    drawLowPolyTree(screenPos)
                }
            )
        }
    }

    // Add Rocks
    for (rock in engine.rocksList) {
        val screenPos = engine.mapToScreen(rock.x, rock.y, 0f, width, height)
        if (screenPos.x in -100f..(width + 100f) && screenPos.y in -100f..(height + 100f)) {
            renderList.add(
                RenderObject(depth = rock.y) { w, h ->
                    drawLowPolyRock(screenPos)
                }
            )
        }
    }

    // Add Coins
    for (coin in engine.coinsList) {
        if (!coin.isCollected) {
            val screenPos = engine.mapToScreen(coin.x, coin.y, 0f, width, height)
            if (screenPos.x in -50f..(width + 50f) && screenPos.y in -50f..(height + 50f)) {
                // dynamic bounce animation modifier
                val bob = sin((System.currentTimeMillis() / 250f) + coin.bounceOffset) * 10f
                val finalPos = Offset(screenPos.x, screenPos.y + bob)
                
                renderList.add(
                    RenderObject(depth = coin.y) { w, h ->
                        drawCoinItem(finalPos)
                    }
                )
            }
        }
    }

    // Add Animal entities
    for (animal in engine.gameAnimals) {
        if (!animal.isSaved) {
            val screenPos = engine.mapToScreen(animal.mapX, animal.mapY, 0f, width, height)
            if (screenPos.x in -100f..(width + 100f) && screenPos.y in -100f..(height + 100f)) {
                renderList.add(
                    RenderObject(depth = animal.mapY) { w, h ->
                        drawAnimalEntity(animal, screenPos, engine)
                    }
                )
            }
        } else {
            // Already saved animals inside the camp paddock
            val screenPos = engine.mapToScreen(animal.mapX, animal.mapY, 0f, width, height)
            renderList.add(
                RenderObject(depth = animal.mapY) { w, h ->
                    drawAnimalInPaddock(animal, screenPos)
                }
            )
        }
    }

    // Add Player Character Entity
    val playerScreenPos = engine.mapToScreen(engine.playerX, engine.playerY, engine.playerZ, width, height)
    renderList.add(
        RenderObject(depth = engine.playerY) { w, h ->
            drawPlayerCharacter(playerScreenPos, engine)
        }
    )

    // Sort render targets by depth before painting!
    renderList.sortBy { it.depth }

    // Execute drawing instructions sequentially
    for (obj in renderList) {
        obj.draw(width, height)
    }

    // 2. Overlay Particles (foreground)
    drawActiveParticles(engine, width, height)

    // 3. Environment Starlight Overlay
    if (starAlpha > 0.01f) {
        drawStarsOverlay(starAlpha, width, height)
    }

    // 4. Night Lantern Radial Flashlight Light from player
    // Draw night overlay shadow with transparent hole above campfire and player
    drawNightAmbientShadowOverlay(engine, ambientColor, ambientAlpha, width, height)

    // 5. Compass Arrow Target helper (Only if active animal distance > 200 units)
    drawActiveMissionCompass(engine, playerScreenPos, width, height)
}

// Draw the glassy river spanning across the map
private fun DrawScope.drawIsometricRiver(engine: GameEngine, width: Float, height: Float) {
    // Project Y river belt [Y=320 to Y=400]
    val riverStartPr = engine.mapToScreen(-MAP_LIMIT, 320f, 0f, width, height)
    val riverEndPr = engine.mapToScreen(MAP_LIMIT, 400f, 0f, width, height)

    // To make it simple and look isometric, draw a wide sheared band
    val path = Path().apply {
        val tl = engine.mapToScreen(-MAP_LIMIT, 320f, 0f, width, height)
        val tr = engine.mapToScreen(MAP_LIMIT, 320f, 0f, width, height)
        val br = engine.mapToScreen(MAP_LIMIT, 400f, 0f, width, height)
        val bl = engine.mapToScreen(-MAP_LIMIT, 400f, 0f, width, height)
        
        moveTo(tl.x, tl.y)
        lineTo(tr.x, tr.y)
        lineTo(br.x, br.y)
        lineTo(bl.x, bl.y)
        close()
    }
    
    // Draw water with high gradient blue
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF2E86C1), Color(0xFF5DADE2))
        )
    )

    // Draw wooden log bridge crossing over at X: [-45 to 45]
    val bridgePath = Path().apply {
        val tl = engine.mapToScreen(-35f, 310f, 0f, width, height)
        val tr = engine.mapToScreen(35f, 310f, 0f, width, height)
        val br = engine.mapToScreen(35f, 410f, 0f, width, height)
        val bl = engine.mapToScreen(-35f, 410f, 0f, width, height)
        
        moveTo(tl.x, tl.y)
        lineTo(tr.x, tr.y)
        lineTo(br.x, br.y)
        lineTo(bl.x, bl.y)
        close()
    }
    drawPath(
        path = bridgePath,
        color = Color(0xFF6E2C00) // Deep red brown wood
    )
    
    // Draw bridge planks lines
    for (yVal in 315..405 step 15) {
        val l = engine.mapToScreen(-35f, yVal.toFloat(), 4f, width, height)
        val r = engine.mapToScreen(35f, yVal.toFloat(), 4f, width, height)
        drawLine(
            color = Color(0xFFA04000),
            start = l,
            end = r,
            strokeWidth = 6f
        )
    }
}

// Rescue veterinary camp base layout structure
private fun DrawScope.drawRescueCampLayout(engine: GameEngine, campCenter: Offset) {
    // 1. Base dirt circle patch
    drawCircle(
        color = Color(0xFF937A62), // beautiful soil dust
        radius = 160f,
        center = campCenter
    )
    
    // Circle border stones
    drawCircle(
        color = Color(0xFF5D6D70),
        radius = 160f,
        style = Stroke(width = 8f),
        center = campCenter
    )

    // 2. Draw Veteran Tents (white and dynamic medical coral angled triangles)
    val tent1 = Offset(campCenter.x - 70f, campCenter.y - 40f)
    drawLowPolyTent(tent1, Color(0xFFECF0F1), Color(0xFFE74C3C))

    val tent2 = Offset(campCenter.x + 80f, campCenter.y - 70f)
    drawLowPolyTent(tent2, Color(0xFFFFEBEE), Color(0xFF42A5F5))

    // 3. Cozy Campfire centered at (0, -20)
    val fireCenter = Offset(campCenter.x, campCenter.y + 40f)
    // Dark ash logs base
    drawCircle(color = Color(0xFF2C3E50), radius = 22f, center = fireCenter)
    
    // Cross logs (brown blocks)
    drawRect(
        color = Color(0xFF5C3A21),
        topLeft = Offset(fireCenter.x - 18f, fireCenter.y - 4f),
        size = Size(36f, 8f),
    )
    drawRect(
        color = Color(0xFF5C3A21),
        topLeft = Offset(fireCenter.x - 4f, fireCenter.y - 18f),
        size = Size(8f, 36f),
    )

    // Animated Fire Flickering polygonal body
    val firePulse = 12f + sin((System.currentTimeMillis() / 150f)) * 5f
    val firePath = Path().apply {
        moveTo(fireCenter.x, fireCenter.y - firePulse * 1.5f)
        lineTo(fireCenter.x - firePulse, fireCenter.y + 5f)
        lineTo(fireCenter.x + firePulse, fireCenter.y + 5f)
        close()
    }
    // Radiant fire gradients
    drawPath(
        path = firePath,
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFF5722), Color(0xFFFFEB3B)),
            center = fireCenter
        )
    )

    // Tiny fire spark particle generator directly in render
    if (Random.nextInt(5) == 1) {
        val rx = fireCenter.x + (Random.nextFloat() - 0.5f) * 20f
        val ry = fireCenter.y - Random.nextFloat() * 40f
        engine.particlesList.add(
            com.example.game.engine.GameParticle(
                x = engine.playerX + (rx - campCenter.x), // approximate coords
                y = engine.playerY + (ry - campCenter.y),
                z = 10f,
                vx = (Random.nextFloat() - 0.5f) * 2f,
                vy = (Random.nextFloat() - 0.5f) * 2f,
                vz = 3f + Random.nextFloat() * 4f,
                color = Color(0xFFFF9800),
                radius = 3f + Random.nextFloat() * 3f,
                alpha = 1f,
                life = 15,
                maxLife = 20
            )
        )
    }
}

// Stylized polygonal Low-Poly tents
private fun DrawScope.drawLowPolyTent(pos: Offset, bodyColor: Color, stripeColor: Color) {
    // Left flap
    val leftPath = Path().apply {
        moveTo(pos.x, pos.y - 35f)
        lineTo(pos.x - 30f, pos.y + 10f)
        lineTo(pos.x, pos.y + 10f)
        close()
    }
    drawPath(path = leftPath, color = bodyColor)
    
    // Right flap (darker shade for 3D low poly depth)
    val rightPath = Path().apply {
        moveTo(pos.x, pos.y - 35f)
        lineTo(pos.x, pos.y + 10f)
        lineTo(pos.x + 35f, pos.y + 10f)
        close()
    }
    drawPath(path = rightPath, color = bodyColor.copy(alpha = 0.85f))

    // Accent Stripe
    val stripePath = Path().apply {
        moveTo(pos.x, pos.y - 35f)
        lineTo(pos.x - 8f, pos.y + 10f)
        lineTo(pos.x + 8f, pos.y + 10f)
        close()
    }
    drawPath(path = stripePath, color = stripeColor)

    // Tent entrance triangle opening
    val openingPath = Path().apply {
        moveTo(pos.x, pos.y - 5f)
        lineTo(pos.x - 12f, pos.y + 10f)
        lineTo(pos.x + 12f, pos.y + 10f)
        close()
    }
    drawPath(path = openingPath, color = Color(0xFF2C3E50))
}

// High visual quality polygonal stacked-cone tree
private fun DrawScope.drawLowPolyTree(pos: Offset) {
    // 1. Shadow underneath
    drawOval(
        color = Color(0x3B000000),
        topLeft = Offset(pos.x - 22f, pos.y + 5f),
        size = Size(44f, 15f)
    )

    // 2. Tree Bark trunk
    drawRect(
        color = Color(0xFF5D4037),
        topLeft = Offset(pos.x - 5f, pos.y - 15f),
        size = Size(10f, 25f)
    )

    // 3. Stacked triangular low poly cones (Pine Tree faceted logic)
    val darkGreen = Color(0xFF1B4D3E)
    val brightGreen = Color(0xFF2D7F60)

    // Layer 1 (Base cone)
    drawLowPolyCone(pos.x, pos.y - 12f, 40f, 25f, darkGreen, brightGreen)
    // Layer 2 (Middle cone)
    drawLowPolyCone(pos.x, pos.y - 28f, 32f, 22f, darkGreen, brightGreen)
    // Layer 3 (High cap)
    drawLowPolyCone(pos.x, pos.y - 44f, 22f, 18f, darkGreen, brightGreen)
}

// Draws a faceted triangle representing 3D low poly cone shading
private fun DrawScope.drawLowPolyCone(cx: Float, cy: Float, radius: Float, height: Float, dCol: Color, lCol: Color) {
    // Left shading facet
    val leftPath = Path().apply {
        moveTo(cx, cy - height)
        lineTo(cx - radius, cy)
        lineTo(cx, cy)
        close()
    }
    drawPath(path = leftPath, color = dCol)

    // Right shading facet (sunlight side)
    val rightPath = Path().apply {
        moveTo(cx, cy - height)
        lineTo(cx, cy)
        lineTo(cx + radius, cy)
        close()
    }
    drawPath(path = rightPath, color = lCol)
}

// Slate styled 3D rock structure
private fun DrawScope.drawLowPolyRock(pos: Offset) {
    // Ground Shadow
    drawOval(
        color = Color(0x42000000),
        topLeft = Offset(pos.x - 30f, pos.y + 3f),
        size = Size(60f, 18f)
    )

    // Sharp facets rock path
    val leftFacet = Path().apply {
        moveTo(pos.x - 28f, pos.y + 5f)
        lineTo(pos.x - 10f, pos.y - 25f)
        lineTo(pos.x, pos.y + 8f)
        close()
    }
    drawPath(path = leftFacet, color = Color(0xFF7F8C8D))

    val rightFacet = Path().apply {
        moveTo(pos.x - 10f, pos.y - 25f)
        lineTo(pos.x + 25f, pos.y + 5f)
        lineTo(pos.x, pos.y + 8f)
        close()
    }
    drawPath(path = rightFacet, color = Color(0xFFBDC3C7))

    val highlightF = Path().apply {
        moveTo(pos.x - 10f, pos.y - 25f)
        lineTo(pos.x, pos.y - 12f)
        lineTo(pos.x + 12f, pos.y - 5f)
        close()
    }
    drawPath(path = highlightF, color = Color(0xFFECF0F1).copy(alpha = 0.5f))
}

// Gorgeous spinning/pulse gold coin drawing
private fun DrawScope.drawCoinItem(pos: Offset) {
    // Simple Shadow
    drawOval(
        color = Color(0x2E000000),
        topLeft = Offset(pos.x - 10f, pos.y + 12f),
        size = Size(20f, 6f)
    )

    // Pulse animation sizes
    val pulseFreq = (System.currentTimeMillis() / 200f)
    val scaleAxis = abs(sin(pulseFreq))
    
    // Draw golden ring ellipse
    drawOval(
        color = Color(0xFFF1C40F),
        topLeft = Offset(pos.x - (12f * scaleAxis), pos.y - 12f),
        size = Size(24f * scaleAxis, 24f)
    )
    
    // Inner outline star detailing
    drawOval(
        color = Color(0xFFFFE066),
        topLeft = Offset(pos.x - (8f * scaleAxis), pos.y - 8f),
        size = Size(16f * scaleAxis, 16f)
    )
}

// Multi-faceted cute animal models drawing
private fun DrawScope.drawAnimalEntity(animal: AnimalEntity, pos: Offset, engine: GameEngine) {
    val tickPulse = (System.currentTimeMillis() / 200f)
    // Small hops when rescued and running following player
    val verticalHop = if (animal.isRescued) abs(sin(tickPulse)) * 12f else 0f
    val animalPos = Offset(pos.x, pos.y - verticalHop)

    // Base Shadow
    drawOval(
        color = Color(0x2C000000),
        topLeft = Offset(animalPos.x - 15f, pos.y + 6f),
        size = Size(30f, 10f)
    )

    // Draw the actual animal model based on its ID
    when (animal.id) {
        0 -> drawRabbitModel(animalPos)
        1 -> drawDogModel(animalPos)
        2 -> drawDeerModel(animalPos, tickPulse)
        3 -> drawFoxModel(animalPos)
        4 -> drawPandaModel(animalPos)
    }

    // Draw cage trap or nets if still trapped
    if (animal.isTrapped) {
        drawTrapStructure(animal.trapType, animalPos)
        
        // Active mission glow pointer!
        if (animal.id == engine.activeMissionId) {
            val distMap = sqrt((engine.playerX - animal.mapX) * (engine.playerX - animal.mapX) + (engine.playerY - animal.mapY) * (engine.playerY - animal.mapY))
            val isPromptInRange = distMap < 100f
            if (isPromptInRange) {
                // Glow text bubble bouncy above animal head
                val bounce = sin((System.currentTimeMillis() / 150f)) * 8f
                drawInteractPrompt(Offset(animalPos.x, animalPos.y - 50f + bounce))
            }
        }
    }
}

// Rescued animal in protective corral/paddock at camp
private fun DrawScope.drawAnimalInPaddock(animal: AnimalEntity, pos: Offset) {
    val tickPulse = (System.currentTimeMillis() / 400f) + animal.id
    val hop = (abs(sin(tickPulse.toDouble())) * 8.0).toFloat()
    val animalPos = Offset(pos.x, pos.y - hop)

    drawOval(
        color = Color(0x1B000000),
        topLeft = Offset(pos.x - 14f, pos.y + 6f),
        size = Size(28f, 8f)
    )

    when (animal.id) {
        0 -> drawRabbitModel(animalPos)
        1 -> drawDogModel(animalPos)
        2 -> drawDeerModel(animalPos, tickPulse)
        3 -> drawFoxModel(animalPos)
        4 -> drawPandaModel(animalPos)
    }
}

// Rabbit: White clean rounded body, long bouncy ears, pink pads
private fun DrawScope.drawRabbitModel(pos: Offset) {
    // Body oval
    drawCircle(color = Color(0xFFECF0F1), radius = 12f, center = pos)
    
    // Head oval offset
    drawCircle(color = Color(0xFFFFFFFF), radius = 10f, center = Offset(pos.x, pos.y - 12f))
    
    // Ears
    drawRoundRect(
        color = Color(0xFFFFFFFF),
        topLeft = Offset(pos.x - 7f, pos.y - 28f),
        size = Size(4f, 16f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = Color(0xFFFFCDD2), // pink pads
        topLeft = Offset(pos.x - 6f, pos.y - 24f),
        size = Size(2f, 10f),
        cornerRadius = CornerRadius(1f, 1f)
    )

    drawRoundRect(
        color = Color(0xFFFFFFFF),
        topLeft = Offset(pos.x + 3f, pos.y - 28f),
        size = Size(4f, 16f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = Color(0xFFFFCDD2),
        topLeft = Offset(pos.x + 4f, pos.y - 24f),
        size = Size(2f, 10f),
        cornerRadius = CornerRadius(1f, 1f)
    )

    // Eyes
    drawCircle(color = Color(0xFFFF4081), radius = 1.5f, center = Offset(pos.x - 3f, pos.y - 14f))
    drawCircle(color = Color(0xFFFF4081), radius = 1.5f, center = Offset(pos.x + 3f, pos.y - 14f))
}

// Dog Model: Golden-brown hound, floppy darker ears
private fun DrawScope.drawDogModel(pos: Offset) {
    // Main Body
    drawRoundRect(
        color = Color(0xFFD35400),
        topLeft = Offset(pos.x - 15f, pos.y - 8f),
        size = Size(26f, 15f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    // Head
    drawCircle(color = Color(0xFFE67E22), radius = 9f, center = Offset(pos.x + 10f, pos.y - 11f))
    // Muzzle snout
    drawRect(
        color = Color(0xFFCA6F1E),
        topLeft = Offset(pos.x + 14f, pos.y - 10f),
        size = Size(6f, 5f)
    )
    drawCircle(color = Color(0xFF2C3E50), radius = 1.5f, center = Offset(pos.x + 18f, pos.y - 10f)) // nose
    
    // Floppy ears
    drawRoundRect(
        color = Color(0xFF873A0C),
        topLeft = Offset(pos.x + 6f, pos.y - 14f),
        size = Size(4f, 10f),
        cornerRadius = CornerRadius(2f, 2f)
    )
}

// Deer Model: Tall elegant form, branch dynamic antlers
private fun DrawScope.drawDeerModel(pos: Offset, pulse: Float) {
    // Legs
    drawLine(color = Color(0xFF8E44AD), start = Offset(pos.x - 6f, pos.y + 4f), end = Offset(pos.x - 6f, pos.y + 12f), strokeWidth = 3f)
    drawLine(color = Color(0xFF8E44AD), start = Offset(pos.x + 6f, pos.y + 4f), end = Offset(pos.x + 6f, pos.y + 12f), strokeWidth = 3f)

    // Body
    drawRoundRect(
        color = Color(0xFFBA4A00),
        topLeft = Offset(pos.x - 16f, pos.y - 12f),
        size = Size(26f, 16f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    // Long Neck
    drawLine(
        color = Color(0xFFCA6F1E),
        start = Offset(pos.x + 8f, pos.y - 4f),
        end = Offset(pos.x + 14f, pos.y - 20f),
        strokeWidth = 6f
    )
    // Head
    drawCircle(color = Color(0xFFE67E22), radius = 6f, center = Offset(pos.x + 15f, pos.y - 22f))

    // Antlers (stately horns)
    drawLine(color = Color(0xFF5C3A21), start = Offset(pos.x + 14f, pos.y - 26f), end = Offset(pos.x + 10f, pos.y - 36f), strokeWidth = 2f)
    drawLine(color = Color(0xFF5C3A21), start = Offset(pos.x + 16f, pos.y - 26f), end = Offset(pos.x + 22f, pos.y - 36f), strokeWidth = 2f)
}

// Fox Model: Sleek fox red coat, pointy nose, white tail tip
private fun DrawScope.drawFoxModel(pos: Offset) {
    // Body
    drawRoundRect(
        color = Color(0xFFE65100),
        topLeft = Offset(pos.x - 14f, pos.y - 8f),
        size = Size(24f, 14f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    // White chest fluff panel
    drawCircle(color = Color(0xFFFFFFFF), radius = 5f, center = Offset(pos.x + 8f, pos.y - 2f))

    // Head
    drawCircle(color = Color(0xFFFF6D00), radius = 7.5f, center = Offset(pos.x + 8f, pos.y - 10f))
    // Pointy Ears
    val leftEar = Path().apply {
        moveTo(pos.x + 3f, pos.y - 16f)
        lineTo(pos.x + 6f, pos.y - 24f)
        lineTo(pos.x + 9f, pos.y - 16f)
    }
    drawPath(path = leftEar, color = Color(0xFFD84315))
    val rightEar = Path().apply {
        moveTo(pos.x + 8f, pos.y - 16f)
        lineTo(pos.x + 11f, pos.y - 24f)
        lineTo(pos.x + 14f, pos.y - 16f)
    }
    drawPath(path = rightEar, color = Color(0xFFD84315))

    // Bushy Tail
    drawOval(
        color = Color(0xFFE65100),
        topLeft = Offset(pos.x - 24f, pos.y - 11f),
        size = Size(12f, 8f)
    )
    drawCircle(color = Color.White, radius = 2.5f, center = Offset(pos.x - 23f, pos.y - 7f))
}

// Panda Model: Chunky rounded white body, black limbs, circular black eyes
private fun DrawScope.drawPandaModel(pos: Offset) {
    // Body
    drawCircle(color = Color(0xFFE0E0E0), radius = 17f, center = pos)
    
    // Black shoulder stripes band
    drawRoundRect(
        color = Color(0xFF212121),
        topLeft = Offset(pos.x - 14f, pos.y - 12f),
        size = Size(28f, 10f),
        cornerRadius = CornerRadius(3f, 3f)
    )

    // White Head
    drawCircle(color = Color(0xFFFFFFFF), radius = 12f, center = Offset(pos.x, pos.y - 16f))

    // Black ears
    drawCircle(color = Color(0xFF111111), radius = 4f, center = Offset(pos.x - 10f, pos.y - 26f))
    drawCircle(color = Color(0xFF111111), radius = 4f, center = Offset(pos.x + 10f, pos.y - 26f))

    // Eye sockets
    drawCircle(color = Color(0xFF111111), radius = 3f, center = Offset(pos.x - 4f, pos.y - 17f))
    drawCircle(color = Color(0xFF111111), radius = 3f, center = Offset(pos.x + 4f, pos.y - 17f))
    
    // white pupils
    drawCircle(color = Color.White, radius = 1f, center = Offset(pos.x - 3.5f, pos.y - 17f))
    drawCircle(color = Color.White, radius = 1f, center = Offset(pos.x + 3.5f, pos.y - 17f))
}

// Stylized Traps: Cage, net, log barrier overlays
private fun DrawScope.drawTrapStructure(type: String, pos: Offset) {
    when (type) {
        "CAGE" -> { // Wooden Cage cages
            // Corner posts
            drawRect(color = Color(0xFF7E5109), topLeft = Offset(pos.x - 24f, pos.y - 28f), size = Size(6f, 38f))
            drawRect(color = Color(0xFF7E5109), topLeft = Offset(pos.x + 18f, pos.y - 28f), size = Size(6f, 38f))
            // Horizontal bars
            drawRect(color = Color(0xFFA0522D), topLeft = Offset(pos.x - 24f, pos.y - 28f), size = Size(48f, 5f))
            drawRect(color = Color(0xFFA0522D), topLeft = Offset(pos.x - 24f, pos.y + 5f), size = Size(48f, 5f))
            // Grids lines map
            for (offset in -14..12 step 7) {
                drawRect(
                    color = Color(0xFFCD853F),
                    topLeft = Offset(pos.x + offset.toFloat(), pos.y - 24f),
                    size = Size(3f, 30f)
                )
            }
        }
        "NET" -> { // Hunting net cross line web overlay
            val netW = 46f
            val netH = 26f
            val netTL = Offset(pos.x - 23f, pos.y - 15f)
            
            // Draw transparent green netting block
            drawRect(
                color = Color(0x3B2ECC71),
                topLeft = netTL,
                size = Size(netW, netH)
            )
            // Border Net rope
            drawRect(
                color = Color(0xFF27AE60),
                topLeft = netTL,
                size = Size(netW, netH),
                style = Stroke(width = 3f)
            )
            // Net Grid wire lines
            for (step in 5 until netW.toInt() step 8) {
                drawLine(color = Color(0x8C27AE60), start = Offset(netTL.x + step, netTL.y), end = Offset(netTL.x + step, netTL.y + netH), strokeWidth = 2f)
            }
            for (step in 4 until netH.toInt() step 6) {
                drawLine(color = Color(0x8C27AE60), start = Offset(netTL.x, netTL.y + step), end = Offset(netTL.x + netW, netTL.y + step), strokeWidth = 2f)
            }
        }
        "LOGS" -> { // Fallen wood debris blockages
            // Logs crossing
            drawRoundRect(color = Color(0xFF4A2711), topLeft = Offset(pos.x - 24f, pos.y - 8f), size = Size(48f, 10f), cornerRadius = CornerRadius(2f, 2f))
            drawRoundRect(color = Color(0xFF5C3317), topLeft = Offset(pos.x - 20f, pos.y), size = Size(44f, 8f), cornerRadius = CornerRadius(2f, 2f))
            // Cross diagonal branch trap
            rotate(45f, pivot = pos) {
                drawRect(color = Color(0xFF3D1F0E), topLeft = Offset(pos.x - 25f, pos.y - 4f), size = Size(50f, 6f))
            }
        }
        "ROCKS" -> { // Pile of blockage boulders
            drawCircle(color = Color(0xFF5F6A6A), radius = 12f, center = Offset(pos.x - 18f, pos.y + 4f))
            drawCircle(color = Color(0xFF7F8C8D), radius = 15f, center = Offset(pos.x + 14f, pos.y + 2f))
            drawCircle(color = Color(0xFFBDC3C7), radius = 10f, center = Offset(pos.x + 2f, pos.y - 10f))
        }
        "GRID" -> { // Neon Grid barrier prison cage
            val gridRadius = 26f
            drawCircle(
                color = Color(0xFFFF1744).copy(alpha = 0.45f),
                radius = gridRadius,
                center = pos,
                style = Stroke(width = 4f)
            )
            // glowing forcefield lines
            for (a in 0..360 step 60) {
                val rad = (a * Math.PI / 180f).toFloat()
                drawLine(
                    color = Color(0xFFFF5252).copy(alpha = 0.6f),
                    start = pos,
                    end = Offset(pos.x + cos(rad) * gridRadius, pos.y + sin(rad) * gridRadius),
                    strokeWidth = 2f
                )
            }
        }
    }
}

// Interact bounce label button popup
private fun DrawScope.drawInteractPrompt(target: Offset) {
    // Bubble Capsule background
    val pW = 110f
    val pH = 34f
    val pBox = Rect(target.x - pW / 2f, target.y - pH / 2f, target.x + pW / 2f, target.y + pH / 2f)
    
    // Glassy cyan background card
    drawRoundRect(
        color = Color(0xFF00E676),
        topLeft = Offset(pBox.left, pBox.top),
        size = Size(pW, pH),
        cornerRadius = CornerRadius(12f, 12f)
    )

    drawRoundRect(
        color = Color.White,
        topLeft = Offset(pBox.left, pBox.top),
        size = Size(pW, pH),
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = 3f)
    )

    // Inner rescue indicator icon detail
    drawCircle(
        color = Color.White,
        radius = 5f,
        center = Offset(target.x - 28f, target.y)
    )
    
    // Draw cross cross lines for first aid
    drawLine(color = Color(0xFF00BAC4), start = Offset(target.x - 28f, target.y - 3f), end = Offset(target.x - 28f, target.y + 3f), strokeWidth = 2f)
    drawLine(color = Color(0xFF00BAC4), start = Offset(target.x - 31f, target.y), end = Offset(target.x - 25f, target.y), strokeWidth = 2f)
}

// Distinct, responsive Player character models with clothing details (supporting rescue vehicle ATV mode)
private fun DrawScope.drawPlayerCharacter(pos: Offset, engine: GameEngine) {
    val moving = engine.playerMovingTicks > 0
    val pulse = (System.currentTimeMillis() / 150f)
    
    // Body bobbing gait animation (bypassed when riding)
    val characterBob = if (moving && !engine.useVehicle) abs(sin(pulse)) * 6f else 0f
    val characterBaseZ = pos.y - characterBob

    if (engine.useVehicle) {
        // --- 1. JEEP / ATV RESCUE VEHICLE GRAPHICS STYLE ---
        // Enhanced large shadow
        drawOval(
            color = Color(0x3B000000),
            topLeft = Offset(pos.x - 22f, pos.y + 10f),
            size = Size(44f, 18f)
        )

        // Draw headlight illumination if night
        val cycleVal = engine.dayCycleProgress
        val isNight = cycleVal > 0.85f || cycleVal < 0.2f
        if (isNight) {
            val radAngle = (engine.playerAngle * Math.PI / 180f).toFloat()
            val coneD = 280f
            val spread = 35f * (Math.PI / 180f).toFloat()
            val beamPath = Path().apply {
                moveTo(pos.x, characterBaseZ)
                val pLeft = Offset(
                    pos.x + cos(radAngle - spread) * coneD,
                    characterBaseZ + sin(radAngle - spread) * coneD
                )
                val pRight = Offset(
                    pos.x + cos(radAngle + spread) * coneD,
                    characterBaseZ + sin(radAngle + spread) * coneD
                )
                lineTo(pLeft.x, pLeft.y)
                lineTo(pRight.x, pRight.y)
                close()
            }
            drawPath(
                path = beamPath,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF176).copy(alpha = 0.9f), Color(0x00FFF9C4)),
                    center = Offset(pos.x, characterBaseZ),
                    radius = coneD
                )
            )
        }

        // ATV Chassis (Main safety yellow shell)
        drawRoundRect(
            color = Color(0xFFFFD54F), // High-visibility yellow
            topLeft = Offset(pos.x - 20f, characterBaseZ - 5f),
            size = Size(40f, 15f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        // Red back lights / bumper decoration
        drawRect(
            color = Color(0xFFE53935),
            topLeft = Offset(pos.x - 18f, characterBaseZ - 4f),
            size = Size(6f, 3f)
        )
        // Bright front lights
        drawRect(
            color = Color(0xFFFFF59D),
            topLeft = Offset(pos.x + 12f * engine.playerScaleX - 3f, characterBaseZ + 2f),
            size = Size(6f, 4f)
        )

        // 4 chunky black wheels with orange alloy center cap detailing
        val wheelRadius = 7.5f
        // Front Left wheel
        drawCircle(color = Color(0xFF212121), radius = wheelRadius, center = Offset(pos.x - 12f, characterBaseZ + 12f))
        drawCircle(color = Color(0xFFFFB74D), radius = 2.5f, center = Offset(pos.x - 12f, characterBaseZ + 12f))
        
        // Front Right wheel
        drawCircle(color = Color(0xFF212121), radius = wheelRadius, center = Offset(pos.x + 12f, characterBaseZ + 12f))
        drawCircle(color = Color(0xFFFFB74D), radius = 2.5f, center = Offset(pos.x + 12f, characterBaseZ + 12f))

        // Back Left wheel
        drawCircle(color = Color(0xFF212121), radius = wheelRadius, center = Offset(pos.x - 12f, characterBaseZ - 2f))
        drawCircle(color = Color(0xFFFFB74D), radius = 2.5f, center = Offset(pos.x - 12f, characterBaseZ - 2f))

        // Back Right wheel
        drawCircle(color = Color(0xFF212121), radius = wheelRadius, center = Offset(pos.x + 12f, characterBaseZ - 2f))
        drawCircle(color = Color(0xFFFFB74D), radius = 2.5f, center = Offset(pos.x + 12f, characterBaseZ - 2f))

        // Metal bumpers / grill
        drawRect(
            color = Color(0xFF78909C),
            topLeft = Offset(pos.x - 10f, characterBaseZ + 6f),
            size = Size(20f, 4f)
        )

        // Steering handlebars
        drawLine(
            color = Color(0xFF37474F),
            start = Offset(pos.x + 8f * engine.playerScaleX, characterBaseZ - 4f),
            end = Offset(pos.x + 12f * engine.playerScaleX, characterBaseZ - 12f),
            strokeWidth = 3f
        )
        drawCircle(
            color = Color(0xFF263238),
            radius = 2.5f,
            center = Offset(pos.x + 12f * engine.playerScaleX, characterBaseZ - 12f)
        )

        // Ranger sitting passenger representation
        val sitY = characterBaseZ - 14f
        val bodyW = 14f
        val bodyH = 16f
        
        // Vest torso
        drawRoundRect(
            color = Color(0xFF1B5E20),
            topLeft = Offset(pos.x - bodyW / 2, sitY),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(3f, 3f)
        )
        // High vis safety stripe
        drawLine(
            color = Color(0xFFFF6D00),
            start = Offset(pos.x - 5f, sitY + 2f),
            end = Offset(pos.x + 5f, sitY + bodyH - 2f),
            strokeWidth = 2.5f
        )

        // Ranger Head (tan skin, orange ranger caps)
        val headR = 7.5f
        val headCenter = Offset(pos.x, sitY - headR + 2f)
        drawCircle(color = Color(0xFFFFCC80), radius = headR, center = headCenter)

        // Ranger field cap
        drawRect(
            color = Color(0xFFFF6D00),
            topLeft = Offset(headCenter.x - 8f, headCenter.y - 8f),
            size = Size(16f, 4.5f)
        )
        val capVisorOffset = 5.5f * engine.playerScaleX
        drawLine(
            color = Color(0xFFE65100),
            start = Offset(headCenter.x, headCenter.y - 6f),
            end = Offset(headCenter.x + capVisorOffset, headCenter.y - 4f),
            strokeWidth = 2.5f
        )

        // Backpack behind passenger
        drawRoundRect(
            color = Color(0xFF795548),
            topLeft = Offset(pos.x - 10f * engine.playerScaleX - 3f, sitY + 2f),
            size = Size(6f, 11f),
            cornerRadius = CornerRadius(2f, 2f)
        )
    } else {
        // --- 2. STANDARD WALKING RANGER GRAPHICS ---
        val shadowW = 28f - (engine.playerZ * 0.15f).coerceAtMost(16f)
        drawOval(
            color = Color(0x3B000000),
            topLeft = Offset(pos.x - shadowW / 2f, pos.y + 12f),
            size = Size(shadowW, 10f)
        )

        val cycleVal = engine.dayCycleProgress
        val isNight = cycleVal > 0.85f || cycleVal < 0.2f
        if (isNight) {
            val radAngle = (engine.playerAngle * Math.PI / 180f).toFloat()
            val coneD = 180f
            val spread = 40f * (Math.PI / 180f).toFloat()
            
            val beamPath = Path().apply {
                moveTo(pos.x, characterBaseZ)
                val pLeft = Offset(
                    pos.x + cos(radAngle - spread) * coneD,
                    characterBaseZ + sin(radAngle - spread) * coneD
                )
                val pRight = Offset(
                    pos.x + cos(radAngle + spread) * coneD,
                    characterBaseZ + sin(radAngle + spread) * coneD
                )
                lineTo(pLeft.x, pLeft.y)
                lineTo(pRight.x, pRight.y)
                close()
            }
            
            drawPath(
                path = beamPath,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xE6FFF59D), Color(0x00FFF59D)),
                    center = Offset(pos.x, characterBaseZ),
                    radius = coneD
                )
            )
        }

        val bodyW = 16f
        val bodyH = 22f
        
        drawRoundRect(
            color = Color(0xFF1B5E20),
            topLeft = Offset(pos.x - bodyW / 2, characterBaseZ - bodyH + 3f),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawLine(
            color = Color(0xFFFF6D00),
            start = Offset(pos.x - 7f, characterBaseZ - bodyH + 5f),
            end = Offset(pos.x + 7f, characterBaseZ - 2f),
            strokeWidth = 3f
        )

        drawRect(color = Color(0xFF3E2723), topLeft = Offset(pos.x - 7f, characterBaseZ + 4f), size = Size(5f, 9f))
        drawRect(color = Color(0xFF3E2723), topLeft = Offset(pos.x + 2f, characterBaseZ + 4f), size = Size(5f, 9f))

        val headR = 8f
        val headCenter = Offset(pos.x, characterBaseZ - bodyH - headR + 4f)
        drawCircle(color = Color(0xFFFFCC80), radius = headR, center = headCenter)

        drawRect(
            color = Color(0xFFFF6D00),
            topLeft = Offset(headCenter.x - 9f, headCenter.y - 9f),
            size = Size(18f, 5f)
        )
        val capVisorOffset = 6f * engine.playerScaleX
        drawLine(
            color = Color(0xFFE65100),
            start = Offset(headCenter.x, headCenter.y - 6f),
            end = Offset(headCenter.x + capVisorOffset, headCenter.y - 4f),
            strokeWidth = 3f
        )

        drawRoundRect(
            color = Color(0xFF795548),
            topLeft = Offset(pos.x - 11f * engine.playerScaleX - 4f, characterBaseZ - bodyH + 3f),
            size = Size(8f, 15f),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }
}

// Drawing stardust / active particles on UI
private fun DrawScope.drawActiveParticles(engine: GameEngine, width: Float, height: Float) {
    for (part in engine.particlesList) {
        val screenPos = engine.mapToScreen(part.x, part.y, part.z, width, height)
        drawCircle(
            color = part.color.copy(alpha = part.alpha),
            radius = part.radius,
            center = screenPos
        )
    }
}

// Night dark lighting shadow overlay system mapping radial holes above Campfire and player lantern
private fun DrawScope.drawNightAmbientShadowOverlay(
    engine: GameEngine,
    ambientColor: Color,
    ambientAlpha: Float,
    width: Float,
    height: Float
) {
    if (ambientAlpha < 0.05f) return
    
    // Draw flat translucent shadow color, using custom radial circles to isolate lighting
    val playerSc = engine.mapToScreen(engine.playerX, engine.playerY, engine.playerZ, width, height)
    val campSc = engine.mapToScreen(0f, 0f, 0f, width, height)

    // To make a lightweight high performance shadow hole overlay, paint concentric circles with negative/clear values or layered overlays
    // Since Compose canvas has BlendMode.DstOut, we can compile a layered mask!
    drawRect(
        color = ambientColor.copy(alpha = ambientAlpha)
    )

    // Campfire warm light core illumination overlay
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xBBFFCC00), Color(0x00FF8800)),
            center = Offset(campSc.x, campSc.y + 40f),
            radius = 110f
        ),
        radius = 110f,
        center = Offset(campSc.x, campSc.y + 40f),
        blendMode = BlendMode.Screen
    )
    
    // Player headlamp lantern overlay glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x99FFF9C4), Color(0x00FFF9C4)),
            center = playerSc,
            radius = 80f
        ),
        radius = 80f,
        center = playerSc,
        blendMode = BlendMode.Screen
    )
}

// Atmospheric night starlight layout
private fun DrawScope.drawStarsOverlay(alpha: Float, width: Float, height: Float) {
    // Generate simple deterministic star locations based on height constraints
    val random = Random(777)
    for (i in 0 until 35) {
        val sx = random.nextFloat() * width
        val sy = random.nextFloat() * (height * 0.45f) // top half of screen sky
        val size = 2f + random.nextFloat() * 4f
        // pulse alpha
        val pAlpha = (0.3f + 0.7f * abs(sin((System.currentTimeMillis() / 400f) + i))) * alpha
        drawCircle(
            color = Color.White.copy(alpha = pAlpha),
            radius = size,
            center = Offset(sx, sy)
        )
    }
}

// Draw a beautiful pointing navigation arrow around player's HUD telling the heading to target animal
private fun DrawScope.drawActiveMissionCompass(
    engine: GameEngine,
    playerScreen: Offset,
    width: Float,
    height: Float
) {
    val activeId = engine.activeMissionId
    val animal = engine.gameAnimals.find { it.id == activeId } ?: return

    val dxMap = engine.playerX - animal.mapX
    val dyMap = engine.playerY - animal.mapY
    val distToTarget = sqrt(dxMap * dxMap + dyMap * dyMap)

    // If completed all or target animal in safe Camp range, do not show compass
    if (distToTarget < 150f || distToTarget > MAP_LIMIT * 2.5f) return

    val animalScreen = engine.mapToScreen(animal.mapX, animal.mapY, 0f, width, height)
    val dx = animalScreen.x - playerScreen.x
    val dy = animalScreen.y - playerScreen.y
    val dist = sqrt(dx * dx + dy * dy)

    if (dist < 120f) return // do not draw if close enough

    // Direction angle heading
    val headingRad = atan2(dy, dx)
    val compassRadius = 60f
    val compassPoint = Offset(
        playerScreen.x + cos(headingRad) * compassRadius,
        playerScreen.y + sin(headingRad) * compassRadius
    )

    // Draw little arrow pointer
    rotate(degrees = (headingRad * 180 / Math.PI).toFloat(), pivot = compassPoint) {
        val arrowPath = Path().apply {
            moveTo(compassPoint.x + 12f, compassPoint.y)
            lineTo(compassPoint.x - 8f, compassPoint.y - 8f)
            lineTo(compassPoint.x - 4f, compassPoint.y)
            lineTo(compassPoint.x - 8f, compassPoint.y + 8f)
            close()
        }
        
        // draw glowing arrow
        drawPath(
            path = arrowPath,
            color = Color(0xFFFFD54F) // compass amber
        )
        // outline
        drawPath(
            path = arrowPath,
            color = Color.Black,
            style = Stroke(width = 2f)
        )
    }
}
