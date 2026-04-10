package com.hieesu.ghostrunner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hieesu.ghostrunner.domain.model.RunSession
import com.hieesu.ghostrunner.ui.screen.HomeScreen
import com.hieesu.ghostrunner.ui.screen.PreviewScreen
import com.hieesu.ghostrunner.ui.screen.RunningScreen
import com.hieesu.ghostrunner.ui.theme.GhostRunnerTheme
import com.hieesu.ghostrunner.ui.viewmodel.HomeViewModel
import com.hieesu.ghostrunner.ui.viewmodel.RunViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()
        enableEdgeToEdge()

        setContent {
            GhostRunnerTheme {
                GhostRunnerNavHost()
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun GhostRunnerNavHost() {
    val navController = rememberNavController()

    // Shared state for the generated session between screens
    var currentSession by remember { mutableStateOf<RunSession?>(null) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                val viewModel: HomeViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                val session by viewModel.generatedSession.collectAsState()

                // Navigate to preview when a session is generated
                LaunchedEffect(session) {
                    session?.let {
                        currentSession = it
                        viewModel.clearSession()
                        navController.navigate("preview")
                    }
                }

                HomeScreen(
                    uiState = uiState,
                    onTextChanged = viewModel::updateText,
                    onLatChanged = viewModel::updateLatitude,
                    onLngChanged = viewModel::updateLongitude,
                    onDistanceChanged = viewModel::updateDistance,
                    onDurationChanged = viewModel::updateDuration,
                    onGenerateRoute = viewModel::generateRoute
                )
            }

            composable("preview") {
                val session = currentSession
                if (session != null) {
                    PreviewScreen(
                        session = session,
                        onStartRun = {
                            navController.navigate("running") {
                                popUpTo("preview") { inclusive = true }
                            }
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }

            composable("running") {
                val runViewModel: RunViewModel = hiltViewModel()
                val mockingState by runViewModel.mockingState.collectAsState()
                val session = currentSession

                if (session != null) {
                    // Start the run when entering this screen
                    LaunchedEffect(Unit) {
                        runViewModel.setSession(session)
                        runViewModel.startRun()
                    }

                    RunningScreen(
                        session = session,
                        mockingState = mockingState,
                        onPause = runViewModel::pauseRun,
                        onResume = runViewModel::resumeRun,
                        onStop = {
                            runViewModel.stopRun()
                            navController.popBackStack("home", false)
                        },
                        onFinished = {
                            navController.popBackStack("home", false)
                        }
                    )
                }
            }
        }
    }
}