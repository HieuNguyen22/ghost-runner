package com.hieesu.ghostrunner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hieesu.ghostrunner.data.local.RouteHistoryDao
import com.hieesu.ghostrunner.data.local.RouteHistoryEntity
import com.hieesu.ghostrunner.domain.engine.RouteGenerator
import com.hieesu.ghostrunner.domain.model.RouteConfig
import com.hieesu.ghostrunner.domain.model.RunSession
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val routeGenerator: RouteGenerator,
    private val routeHistoryDao: RouteHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _generatedSession = MutableStateFlow<RunSession?>(null)
    val generatedSession: StateFlow<RunSession?> = _generatedSession.asStateFlow()

    private val gson = Gson()

    fun updateText(text: String) {
        _uiState.value = _uiState.value.copy(text = text)
    }

    fun updateLatitude(lat: String) {
        _uiState.value = _uiState.value.copy(latitudeStr = lat)
    }

    fun updateLongitude(lng: String) {
        _uiState.value = _uiState.value.copy(longitudeStr = lng)
    }

    fun updateDistance(km: String) {
        _uiState.value = _uiState.value.copy(distanceKmStr = km)
    }

    fun updateDuration(minutes: String) {
        _uiState.value = _uiState.value.copy(durationMinutesStr = minutes)
    }

    fun updateParkMode(isParkMode: Boolean) {
        _uiState.value = _uiState.value.copy(isParkMode = isParkMode)
    }

    fun generateRoute() {
        val state = _uiState.value
        val errors = validateInput(state)

        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }

        _uiState.value = state.copy(errors = emptyList(), isGenerating = true)

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val config = RouteConfig(
                    text = state.text.trim(),
                    startLatitude = state.latitudeStr.toDoubleOrNull() ?: 0.0,
                    startLongitude = state.longitudeStr.toDoubleOrNull() ?: 0.0,
                    totalDistanceKm = state.distanceKmStr.toDouble(),
                    totalDurationMinutes = state.durationMinutesStr.toInt(),
                    isParkMode = state.isParkMode
                )

                val session = routeGenerator.generate(config)
                _generatedSession.value = session

                // Save to history
                val entity = RouteHistoryEntity(
                    text = if (state.isParkMode) "Nghia Do Park" else config.text,
                    startLat = config.startLatitude,
                    startLng = config.startLongitude,
                    distanceKm = config.totalDistanceKm,
                    durationMinutes = config.totalDurationMinutes,
                    routePointsJson = gson.toJson(session.gpsRoute),
                    status = "CREATED"
                )
                routeHistoryDao.insertRoute(entity)

                _uiState.value = _uiState.value.copy(isGenerating = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    errors = listOf("Lỗi tạo route: ${e.message}")
                )
            }
        }
    }

    fun clearSession() {
        _generatedSession.value = null
    }

    private fun validateInput(state: HomeUiState): List<String> {
        val errors = mutableListOf<String>()

        if (!state.isParkMode) {
            // Empty text is allowed — generates a closed-loop circle route
            if (state.text.isNotBlank()) {
                val validChars = ('A'..'Z') + ('0'..'9') + ' '
                val invalidChars = state.text.uppercase().filter { it !in validChars }
                if (invalidChars.isNotEmpty()) {
                    errors.add("Ký tự không hỗ trợ: $invalidChars")
                }
            }

            val lat = state.latitudeStr.toDoubleOrNull()
            if (lat == null || lat < -90 || lat > 90) {
                errors.add("Latitude không hợp lệ (−90 đến 90)")
            }

            val lng = state.longitudeStr.toDoubleOrNull()
            if (lng == null || lng < -180 || lng > 180) {
                errors.add("Longitude không hợp lệ (−180 đến 180)")
            }
        }

        val km = state.distanceKmStr.toDoubleOrNull()
        if (km == null || km <= 0 || km > 100) {
            errors.add("Quãng đường không hợp lệ (0.1 – 100 km)")
        }

        val minutes = state.durationMinutesStr.toIntOrNull()
        if (minutes == null || minutes <= 0 || minutes > 600) {
            errors.add("Thời gian không hợp lệ (1 – 600 phút)")
        }

        return errors
    }
}

data class HomeUiState(
    val text: String = "",
    val latitudeStr: String = "21.028173",
    val longitudeStr: String = "105.767888",
    val distanceKmStr: String = "5",
    val durationMinutesStr: String = "30",
    val isGenerating: Boolean = false,
    val isParkMode: Boolean = false,
    val errors: List<String> = emptyList()
)
