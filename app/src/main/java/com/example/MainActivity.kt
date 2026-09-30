package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SolutionScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.SolverViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val solverViewModel: SolverViewModel = viewModel()
                    GyanLensApp(viewModel = solverViewModel)
                }
            }
        }
    }
}

@Composable
fun GyanLensApp(viewModel: SolverViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

    when (val screen = currentScreen) {
        is Screen.Home -> {
            HomeScreen(viewModel = viewModel)
        }
        is Screen.Solution -> {
            SolutionScreen(
                question = screen.question,
                viewModel = viewModel
            )
        }
        is Screen.History -> {
            HistoryScreen(viewModel = viewModel)
        }
        is Screen.Settings -> {
            SettingsScreen(viewModel = viewModel)
        }
    }
}
