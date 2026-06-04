package com.example.game.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sqrt

// Centralized View router overlay
@Composable
fun GameScreensManager(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
    gameCanvasContent: @Composable (BoxScope) -> Unit
) {
    val currentScreen by viewModel.screenState.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF1B261D))) {
        // Always draw the raw interactive gameplay canvas behind overlays if active
        if (currentScreen != GameScreenState.MAIN_MENU) {
            gameCanvasContent(this)
        }

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                GameScreenState.MAIN_MENU -> MainMenuScreen(
                    viewModel = viewModel,
                    onStartAdventure = { viewModel.setScreenState(GameScreenState.PLAYING) },
                    onOpenUpgrades = { viewModel.setScreenState(GameScreenState.UPGRADES) },
                    onOpenSettings = { viewModel.setScreenState(GameScreenState.SETTINGS) }
                )
                GameScreenState.PLAYING -> GameHudOverlay(viewModel)
                GameScreenState.SETTINGS -> GameSettingsScreen(
                    viewModel = viewModel,
                    onBack = { if (viewModel.gameEngine.coins >= 0) viewModel.setScreenState(GameScreenState.PLAYING) else viewModel.setScreenState(GameScreenState.MAIN_MENU) }
                )
                GameScreenState.UPGRADES -> RangerShopScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.setScreenState(GameScreenState.PLAYING) }
                )
                GameScreenState.VICTORY -> VictoryCelebrationScreen(viewModel)
                else -> MainMenuScreen(viewModel, {}, {}, {})
            }
        }
    }
}

// 1. MAIN MENU SCREEN Layout
@Composable
fun MainMenuScreen(
    viewModel: GameViewModel,
    onStartAdventure: () -> Unit,
    onOpenUpgrades: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF87CEEB), Color(0xFF7BB661))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative Forest Background Shapes
        Box(
            modifier = Modifier
                .size(420.dp)
                .offset(y = (-160).dp)
                .background(Color.White.copy(alpha = 0.25f), shape = CircleShape)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Animal Icon headers grid
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🐰", fontSize = 36.sp)
                Text("🐶", fontSize = 42.sp)
                Text("🦌", fontSize = 48.sp)
                Text("🦊", fontSize = 42.sp)
                Text("🐼", fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Game Logo heading - modern retro styled card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .shadow(16.dp, shape = RoundedCornerShape(24.dp))
                    .border(3.dp, Color(0xFFFFC107), RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                Text(
                    text = "ANIMAL RESCUE\nADVENTURE",
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF1E3A1E), // Slate-style rich evergreen
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            Text(
                text = "3D Low-Poly Forest Explorer app",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B4314),
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.height(34.dp))

            // Character Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .shadow(10.dp, RoundedCornerShape(20.dp))
                    .border(2.dp, Color(0xFF22C55E), RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFF3F8EF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🤠", fontSize = 36.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Ranger Scout",
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E293B),
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Camp Level: ${viewModel.gameEngine.activeMissionsCompleted + 1} / 5",
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 3D Tactile START GAME Button
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(64.dp)
                    .clickable { onStartAdventure() }
                    .testTag("start_adventure_button")
            ) {
                // 3D Shadow block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .offset(y = 6.dp)
                        .background(Color(0xFF15803D), RoundedCornerShape(18.dp))
                )
                // Main Button body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .background(Color(0xFF22C55E), RoundedCornerShape(18.dp))
                        .border(3.dp, Color(0xFF86EFAC), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START ADVENTURE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Secondary Buttons Panel Row with 3D tactile theme
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Ranger Shop
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clickable { onOpenUpgrades() }
                        .testTag("upgrades_store_button")
                ) {
                    // 3D shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .offset(y = 5.dp)
                            .background(Color(0xFFD97706), RoundedCornerShape(14.dp))
                    )
                    // Main body
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFFF59E0B), RoundedCornerShape(14.dp)) // Amber-500
                            .border(2.dp, Color(0xFFFEF3C7), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Shop",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "UPGRADES",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Settings
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clickable { onOpenSettings() }
                        .testTag("settings_button")
                ) {
                    // 3D shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .offset(y = 5.dp)
                            .background(Color(0xFF475569), RoundedCornerShape(14.dp))
                    )
                    // Main body
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFF64748B), RoundedCornerShape(14.dp)) // slate-500
                            .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "SETTINGS",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper for short mission narratives defining the story objectives
fun getMissionStory(animalId: Int): String {
    return when (animalId) {
        0 -> "🐰 Flop-Ear is caught in an illegal cage in the northwest meadow pastures."
        1 -> "🐶 Buddy the sheepdog is wedged under heavy timber logs near the river logs bridge."
        2 -> "🦌 Bambi was chased by wolves and got tangled in poacher steel netting in the East Grove."
        3 -> "🦊 Todd sought shelter but ended up barricaded in rocky caves by fallen boulders."
        4 -> "🐼 Ling-Ling wandered off into a forbidden high-tech research laser forcefield grid."
        else -> "🌟 All forest creatures are back home in the paddock paddock corral! Perfect job, Ranger!"
    }
}

// 2. HEAD-UP DISPLAY OVERLAY (HUD) panels during Active gameplay
@Composable
fun GameHudOverlay(viewModel: GameViewModel) {
    val progressFlowState by viewModel.progressFlow.collectAsState(initial = null)
    var isPaused by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // TOP HUD BAR: Stats trackers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // TOP-LEFT: Active Mission Card (Vibrant Left Green Border)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(IntrinsicSize.Min)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    // Left border green strip
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF22C55E))
                    )

                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "CURRENT MISSION",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D), // green-700
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        if (viewModel.gameEngine.activeMissionsCompleted >= 5) {
                            Text(
                                text = "ALL SECURED! 🎉",
                                color = Color(0xFF1E293B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            val distanceText = if (viewModel.activeMissionDistance > 0) {
                                "${viewModel.activeMissionDistance.toInt()}m"
                            } else {
                                "Locating..."
                            }

                            Text(
                                text = "Rescue the ${viewModel.activeMissionName}",
                                color = Color(0xFF1E293B), // slate-800
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(3.dp))
                            
                            // Short story mission narrative detail
                            Text(
                                text = getMissionStory(viewModel.gameEngine.activeMissionId),
                                color = Color(0xFF475569),
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(5.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = distanceText,
                                    color = Color(0xFF475569), // slate-600
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Progress tracker line for the mission
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { 0f },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFF22C55E),
                                    trackColor = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "0/1",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                            }

                            // Unlock check warnings
                            if (!viewModel.activeMissionUnlocked) {
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked Zone",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "UPGRADE TO UNLOCK AREA",
                                        color = Color(0xFFEF4444),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // TOP-RIGHT: Coins & Pause Menu Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Vibrant Coin Capsule
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFC107)),
                    shape = CircleShape,
                    modifier = Modifier
                        .shadow(8.dp, CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "●",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = "${viewModel.playerCoins}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = (-0.5).sp,
                            modifier = Modifier.testTag("coins_counter")
                        )
                    }
                }

                // Pause Button Trigger (With White Border / Backdrop Overlay)
                IconButton(
                    onClick = {
                        viewModel.audioSynth.playClickSfx()
                        isPaused = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .testTag("pause_game_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Pause Menu",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // CENTERED INTERACTIVE APPROACH PROMPT (Vibrant overlay pill)
        val trappedAnimalInRange = viewModel.activeMissionDistance in 1f..82f && viewModel.activeMissionUnlocked
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
        ) {
            AnimatedVisibility(
                visible = trappedAnimalInRange,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Approach trapped animal to rescue 🐾",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // FLOATING ACTION OVERLAY WIDGETS COLUMN (Middle-Right of gameplay)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera Angle Toggle Button (Flips 🎥 -> 🗺️ -> 🔍)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clickable { viewModel.cycleCameraAngle() }
                    .testTag("camera_cycle_button")
            ) {
                // 3D Visual Shadow Layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = 4.dp)
                        .background(Color(0xFF0F172A), CircleShape)
                )
                // Main visual plate
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E293B), CircleShape)
                        .border(2.dp, Color(0xFF64748B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val angleIcon = when (viewModel.gameEngine.cameraAngleMode) {
                        1 -> "🗺️" // Top Down Ortho
                        2 -> "🔍" // Close Up
                        else -> "🎥" // Isometric
                    }
                    Text(text = angleIcon, fontSize = 20.sp)
                }
            }

            // Mount / Dismount ATV vehicle button (Visible only if purchased)
            if (viewModel.gameEngine.hasVehicle) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { viewModel.toggleVehicleRiding() }
                        .testTag("vehicle_toggle_button")
                ) {
                    val shadowColor = if (viewModel.gameEngine.useVehicle) Color(0xFFB45309) else Color(0xFF1E293B)
                    val plateColor = if (viewModel.gameEngine.useVehicle) Color(0xFFF59E0B) else Color(0xFF475569)
                    val edgeColor = if (viewModel.gameEngine.useVehicle) Color(0xFFFDE68A) else Color(0xFF94A3B8)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = 4.dp)
                            .background(shadowColor, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(plateColor, CircleShape)
                            .border(2.dp, edgeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🚜", fontSize = 20.sp)
                    }
                }
            }
        }

        // BOTTOM HUD: Double capsule progress + Joystick & Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 20.dp, vertical = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contextual Rescue Interact Button (3D tactile emerald green)
            AnimatedVisibility(
                visible = trappedAnimalInRange,
                enter = scaleIn(animationSpec = spring()) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clickable { viewModel.interactAction() }
                        .testTag("interact_rescue_button")
                ) {
                    // Shadow layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = 6.dp)
                            .background(Color(0xFF228B22), CircleShape)
                    )
                    // Core body
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF22C55E), CircleShape)
                            .border(4.dp, Color(0xFF86EFAC), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "RESCUE",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                letterSpacing = (-0.5).sp
                            )
                            Text("🐾", fontSize = 24.sp)
                        }
                    }
                }
            }

            // Standard Jump Button (3D tactile sky blue)
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clickable { viewModel.jumpAction() }
                    .testTag("jump_action_button")
            ) {
                // Shadow layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = 5.dp)
                        .background(Color(0xFF3B82F6), CircleShape)
                )
                // Core body
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF60A5FA), CircleShape)
                        .border(3.dp, Color(0xFFBFDBFE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▲", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // BOTTOM-LEFT: Health & Stamina capsule layout
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 34.dp)
                .width(130.dp)
                .shadow(12.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.22f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Red Health Bar (85% filled for simulation visual richness)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(Color(0xE01E293B), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.85f)
                            .background(Color(0xFFEF4444), CircleShape)
                    )
                }

                // Blue Stamina Bar (Dynamic playerStamina tracker)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(Color(0xE01E293B), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(viewModel.playerStamina / 100f)
                            .background(Color(0xFF3B82F6), CircleShape)
                    )
                }
            }
        }

        // PAUSE SCREEN MODAL OVERLAY
        AnimatedVisibility(
            visible = isPaused,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222C24)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "GAME PAUSED",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFF9C4),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Text(
                            text = "Animal Rescue Camp is waiting for you ranger!",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // Sound controls inside pauses
                        val soundIcon = if (viewModel.audioSynth.soundEnabled) Icons.Default.Check else Icons.Default.Close
                        val soundLabel = if (viewModel.audioSynth.soundEnabled) "SOUND FX: ENABLED" else "SOUND FX: MUTED"
                        
                        Button(
                            onClick = { viewModel.toggleAudioState() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF335C3B)),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = soundIcon, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(soundLabel, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Upgrades shop
                        Button(
                            onClick = {
                                isPaused = false
                                viewModel.setScreenState(GameScreenState.UPGRADES)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("UPGRADES SHOP (COINS)", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Resume button
                        Button(
                            onClick = { isPaused = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RESUME ADVENTURE", color = Color.Black, fontWeight = FontWeight.Black)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Main Menu Quit option
                        TextButton(
                            onClick = {
                                isPaused = false
                                viewModel.setScreenState(GameScreenState.MAIN_MENU)
                            }
                        ) {
                            Text("Quit to Main Menu", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 3. RANGER UPGRADES & SHOP OVERLAY
@Composable
fun RangerShopScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val progressFlowState by viewModel.progressFlow.collectAsState(initial = null)
    
    val speedLvl = progressFlowState?.upgradeSpeed ?: 1
    val staminaLvl = progressFlowState?.upgradeStamina ?: 1
    
    val speedCost = speedLvl * 80
    val staminaCost = staminaLvl * 80

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF87CEEB), Color(0xFF7BB661))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                }

                Text(
                    text = "RANGER UPGRADE STATION",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E293B)
                )

                Box(modifier = Modifier.size(40.dp)) // spacer balance
            }

            // Coin Ledger status card - vibrant gold styling
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFC107)),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ranger Wallet Balance:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("🪙 ${viewModel.playerCoins} Coins", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // UPGRADES CARDS CONTAINER
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Upgrade Card #1: Sprint Speed (Ranger Boots)
                UpgradeItemCard(
                    title = "Ranger Sprint Boots",
                    description = "Increases horizontal movement speed. Unlocks secondary river pastures.",
                    level = speedLvl,
                    cost = speedCost,
                    iconLabel = "🥾",
                    canPurchase = viewModel.playerCoins >= speedCost && speedLvl < 5,
                    onPurchase = { viewModel.upgradeSpeedAttribute() }
                )

                // Upgrade Card #2: Max Stamina (Camp Hydrator Flask)
                UpgradeItemCard(
                    title = "Hydration Flask",
                    description = "Gives you maximum stamina. Delays sprint fatigue, unlocks valley grids.",
                    level = staminaLvl,
                    cost = staminaCost,
                    iconLabel = "🧪",
                    canPurchase = viewModel.playerCoins >= staminaCost && staminaLvl < 5,
                    onPurchase = { viewModel.upgradeStaminaAttribute() }
                )

                // Upgrade Card #3: Heavy-Duty Rescue ATV
                UpgradeItemCard(
                    title = "Heavy-Duty Rescue ATV",
                    description = "Traverse the massive expanded wilderness with an offroad quad! Regenerate stamina while riding.",
                    level = if (viewModel.gameEngine.hasVehicle) 5 else 0,
                    cost = 250,
                    iconLabel = "🚜",
                    canPurchase = viewModel.playerCoins >= 250 && !viewModel.gameEngine.hasVehicle,
                    onPurchase = { viewModel.unlockVehicle() }
                )

                // Area Unlocking helper ledger
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("📍 AREA UNLOCK REFERENCE CHART", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color(0xFF15803D))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Rabbit, Dog (Start Base): Unlocked initially.", fontSize = 10.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                        Text("• Deer (East Net): Needs Speed or Stamina Level 2+", fontSize = 10.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                        Text("• Fox (West Rocks): Needs Speed or Stamina Level 3+", fontSize = 10.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                        Text("• Panda (Bamboo Valley): Needs Speed & Stamina BOTH Level 4+", fontSize = 10.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// Single Shop Upgrade Card UI
@Composable
fun UpgradeItemCard(
    title: String,
    description: String,
    level: Int,
    cost: Int,
    iconLabel: String,
    canPurchase: Boolean,
    onPurchase: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left icon block
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(Color(0xFFF1F5F9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(iconLabel, fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Body
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, color = Color(0xFF1E293B), fontSize = 15.sp)
                Text(description, color = Color(0xFF475569), fontSize = 11.sp, lineHeight = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                
                // Progress blocks (1 to 5)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..5) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (i <= level) Color(0xFF22C55E) else Color(0xFFE2E8F0))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right purchase side
            if (level >= 5) {
                Text(
                    text = "MAXED",
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            } else {
                Button(
                    onClick = onPurchase,
                    enabled = canPurchase,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC107),
                        disabledContainerColor = Color(0xFFE2E8F0)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.width(82.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BUY", color = if (canPurchase) Color.White else Color(0xFF94A3B8), fontWeight = FontWeight.Black, fontSize = 9.sp)
                        Text("🪙$cost", color = if (canPurchase) Color.White else Color(0xFF94A3B8), fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// 4. GAME SETTINGS CONFIG PANEL
@Composable
fun GameSettingsScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val progressFlowState by viewModel.progressFlow.collectAsState(initial = null)
    var showResetDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF87CEEB), Color(0xFF7BB661))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                }

                Text(
                    text = "RANGER SETTINGS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E293B)
                )

                Box(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Config card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("CONTROLS & AUDIO", fontWeight = FontWeight.Black, color = Color(0xFF15803D), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(18.dp))

                    // Audio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Core Game Chimes", fontWeight = FontWeight.Black, color = Color(0xFF1E293B), fontSize = 14.sp)
                            Text("Enables coin plings & rescue fanfares.", color = Color(0xFF475569), fontSize = 11.sp)
                        }

                        Switch(
                            checked = viewModel.audioSynth.soundEnabled,
                            onCheckedChange = { viewModel.toggleAudioState() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF22C55E)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(18.dp))

                    // Dynamic cycle speed modifier
                    Text("Day-Night Cycle Rate", fontWeight = FontWeight.Black, color = Color(0xFF1E293B), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("1 Min", "2 Min", "Fast").forEachIndexed { index, label ->
                            val activeSelection = when (index) {
                                0 -> viewModel.gameEngine.cycleSpeed == 0.0006f
                                1 -> viewModel.gameEngine.cycleSpeed == 0.0003f
                                else -> viewModel.gameEngine.cycleSpeed == 0.001f
                            }

                            Button(
                                onClick = {
                                    viewModel.audioSynth.playClickSfx()
                                    viewModel.gameEngine.cycleSpeed = when (index) {
                                        0 -> 0.0006f
                                        1 -> 0.0003f
                                        else -> 0.001f
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeSelection) Color(0xFF22C55E) else Color(0xFFE2E8F0),
                                    contentColor = if (activeSelection) Color.White else Color(0xFF475569)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Delete database progress
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(50.dp)
                    .shadow(4.dp, RoundedCornerShape(14.dp))
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("RESET GAME PROGRESS", color = Color.White, fontWeight = FontWeight.Black)
            }
        }

        // Reset persistent DB popups
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Progress?") },
                text = { Text("Are you absolutely sure? This will wipe your coins, upgrades, and return all animals back to their cages.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        onClick = {
                            viewModel.restartWholeProgress()
                            showResetDialog = false
                        }
                    ) {
                        Text("RESET EVERYTHING")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}

// 5. VICTORY CELEBRATION & CREDITS
@Composable
fun VictoryCelebrationScreen(viewModel: GameViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF87CEEB), Color(0xFF7BB661))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text("🎉🏆🦁🏆🎉", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "CAMPAIGN COMPLETE!",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Congratulations Ranger! You have successfully searched the 3D-Isometric forest and brought the Rabbit, Dog, Deer, Fox, and Panda safely back into the Vet Refuge!",
                color = Color(0xFF334155),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            Spacer(modifier = Modifier.height(34.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFF22C55E), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("TACTICAL DEBRIEF", fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("🐰 Rabbit: SECURED", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("🐶 Dog: SECURED", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("🦌 Deer: SECURED", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("🦊 Fox: SECURED", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("🐼 Panda: SECURED", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Total Coins Accumulated: 🪙 ${viewModel.playerCoins}", color = Color(0xFFD97706), fontWeight = FontWeight.Black)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Play again 3D tactile button
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(58.dp)
                    .clickable { viewModel.restartWholeProgress() }
            ) {
                // 3D Shadow layer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .offset(y = 5.dp)
                        .background(Color(0xFF15803D), RoundedCornerShape(14.dp))
                )
                // Core body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color(0xFF22C55E), RoundedCornerShape(14.dp))
                        .border(3.dp, Color(0xFF86EFAC), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("PLAY AGAIN", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
