package com.hieesu.ghostrunner.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hieesu.ghostrunner.domain.model.GpsPoint
import com.hieesu.ghostrunner.domain.model.RunSession
import com.hieesu.ghostrunner.service.MockLocationService
import com.hieesu.ghostrunner.service.MockingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RunViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    val mockingState: StateFlow<MockingState> = MockLocationService.mockingState

    private val _session = MutableStateFlow<RunSession?>(null)
    val session: StateFlow<RunSession?> = _session.asStateFlow()

    fun setSession(session: RunSession) {
        _session.value = session
    }

    fun startRun() {
        val currentSession = _session.value ?: return

        // Set route points on the service companion
        MockLocationService.setRoutePoints(currentSession.simulatedRoute)

        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pauseRun() {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeRun() {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopRun() {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        context.startService(intent)
    }
}
