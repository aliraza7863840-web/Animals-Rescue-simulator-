package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.game.ui.GameCanvas
import com.example.game.ui.GameScreensManager
import com.example.game.ui.GameViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge support for stylized immersive full bleed gaming viewports
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // High-frequency tick parameters capturing analog stick forces
                    var joystickDx by remember { mutableStateOf(0f) }
                    var joystickDy by remember { mutableStateOf(0f) }

                    // Continuous hardware-synchronized VSync game loop
                    LaunchedEffect(Unit) {
                        while (true) {
                            withFrameMillis { frameTime ->
                                viewModel.onGameTick(joystickDx, joystickDy)
                            }
                        }
                    }

                    GameScreensManager(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    ) { boxScope ->
                        // Background game world draws under UI overlays
                        GameCanvas(
                            gameEngine = viewModel.gameEngine,
                            modifier = Modifier.fillMaxSize()
                        ) { dx, dy ->
                            // Update core input state parameters in real time
                            joystickDx = dx
                            joystickDy = dy
                        }
                    }
                }
            }
        }
    }
}
