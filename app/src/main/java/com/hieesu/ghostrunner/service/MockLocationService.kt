package com.hieesu.ghostrunner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.hieesu.ghostrunner.MainActivity
import com.hieesu.ghostrunner.R
import com.hieesu.ghostrunner.data.location.MockLocationProvider
import com.hieesu.ghostrunner.domain.model.GpsPoint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground Service that dispatches mock GPS locations in real-time.
 *
 * Key design decisions to prevent real GPS from overriding mock:
 *
 * 1. **Continuous re-push**: Between waypoints, the service re-pushes the current
 *    interpolated position every 500ms. This prevents the real GPS from "winning"
 *    when the app is backgrounded.
 *
 * 2. **Triple provider**: MockLocationProvider pushes to GPS_PROVIDER,
 *    NETWORK_PROVIDER, and FusedLocationProviderClient simultaneously.
 *
 * 3. **WakeLock**: Keeps CPU awake even when screen is off.
 */
@AndroidEntryPoint
class MockLocationService : Service() {

    companion object {
        const val CHANNEL_ID = "ghost_runner_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"

        /**
         * How frequently to re-push the current mock location (ms).
         * Must be shorter than the interval at which the real GPS provider
         * reports updates (typically 1000ms). 500ms is a safe default.
         */
        const val REPUSH_INTERVAL_MS = 500L

        // Shared state for UI observation
        private val _mockingState = MutableStateFlow(MockingState())
        val mockingState: StateFlow<MockingState> = _mockingState.asStateFlow()

        private var routePoints: List<GpsPoint> = emptyList()

        fun setRoutePoints(points: List<GpsPoint>) {
            routePoints = points
        }
    }

    @Inject
    lateinit var mockLocationProvider: MockLocationProvider

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mockingJob: Job? = null
    private var repushJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var isPaused = false

    @Volatile
    private var currentIndex = 0

    /** The most recently pushed GPS point — re-pushed periodically to maintain dominance */
    @Volatile
    private var lastPushedPoint: GpsPoint? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMocking()
            ACTION_PAUSE -> pauseMocking()
            ACTION_RESUME -> resumeMocking()
            ACTION_STOP -> stopMocking()
        }
        return START_STICKY
    }

    private fun startMocking() {
        startForeground(NOTIFICATION_ID, createNotification("Ghost Runner: Đang chạy..."))
        acquireWakeLock()

        try {
            mockLocationProvider.setupTestProvider()
        } catch (e: Exception) {
            _mockingState.value = MockingState(
                isRunning = false,
                error = e.message
            )
            stopSelf()
            return
        }

        currentIndex = 0
        isPaused = false
        startRepushLoop()
        startLocationLoop()
    }

    /**
     * Background coroutine that continuously re-pushes the last known mock location
     * every [REPUSH_INTERVAL_MS] milliseconds. This prevents the real GPS from
     * overriding the mock when the app is in background.
     *
     * This is the KEY fix for the background location reset issue.
     */
    private fun startRepushLoop() {
        repushJob?.cancel()
        repushJob = serviceScope.launch {
            while (isActive) {
                lastPushedPoint?.let { point ->
                    try {
                        mockLocationProvider.setMockLocation(point)
                    } catch (_: Exception) { }
                }
                delay(REPUSH_INTERVAL_MS)
            }
        }
    }

    private fun startLocationLoop() {
        mockingJob?.cancel()
        mockingJob = serviceScope.launch {
            val points = routePoints
            if (points.isEmpty()) {
                _mockingState.value = MockingState(isRunning = false, error = "No route points")
                stopSelf()
                return@launch
            }

            _mockingState.value = MockingState(
                isRunning = true,
                totalPoints = points.size,
                currentIndex = currentIndex
            )

            while (currentIndex < points.size && isActive) {
                if (isPaused) {
                    // Even while paused, keep re-pushing the last location (repushJob handles this)
                    delay(100)
                    continue
                }

                val point = points[currentIndex]

                // Push new waypoint location
                mockLocationProvider.setMockLocation(point)
                lastPushedPoint = point

                _mockingState.value = MockingState(
                    isRunning = true,
                    isPaused = false,
                    totalPoints = points.size,
                    currentIndex = currentIndex,
                    currentPoint = point,
                    progress = (currentIndex.toFloat() / points.size.toFloat()) * 100f
                )

                // Calculate delay until next point
                if (currentIndex < points.size - 1) {
                    val nextPoint = points[currentIndex + 1]
                    val timeDiff = nextPoint.timestamp - point.timestamp
                    val delayMs = timeDiff.coerceIn(100L, 5000L)

                    // During the wait, the repushJob keeps pushing the current
                    // location at REPUSH_INTERVAL_MS intervals. But we also
                    // interpolate between current and next point for smoother movement.
                    val steps = (delayMs / REPUSH_INTERVAL_MS).toInt().coerceAtLeast(1)
                    val stepDelay = delayMs / steps

                    for (step in 0 until steps) {
                        if (!isActive || isPaused) break

                        // Linear interpolation between current and next point
                        val t = (step + 1).toDouble() / steps
                        val interpLat = point.latitude + (nextPoint.latitude - point.latitude) * t
                        val interpLng = point.longitude + (nextPoint.longitude - point.longitude) * t
                        val interpSpeed = point.speed + (nextPoint.speed - point.speed) * t.toFloat()
                        val interpBearing = point.bearing + (nextPoint.bearing - point.bearing) * t.toFloat()

                        val interpPoint = point.copy(
                            latitude = interpLat,
                            longitude = interpLng,
                            speed = interpSpeed,
                            bearing = interpBearing,
                            timestamp = System.currentTimeMillis()
                        )

                        lastPushedPoint = interpPoint
                        mockLocationProvider.setMockLocation(interpPoint)

                        delay(stepDelay)
                    }
                }

                currentIndex++
            }

            // Completed — keep pushing last location for a few more seconds
            // so Strava doesn't snap back to real location immediately
            lastPushedPoint?.let { finalPoint ->
                repeat(10) {
                    mockLocationProvider.setMockLocation(finalPoint)
                    delay(REPUSH_INTERVAL_MS)
                }
            }

            _mockingState.value = MockingState(
                isRunning = false,
                isCompleted = true,
                totalPoints = points.size,
                currentIndex = points.size,
                progress = 100f
            )

            stopMocking()
        }
    }

    private fun pauseMocking() {
        isPaused = true
        _mockingState.value = _mockingState.value.copy(isPaused = true)
        updateNotification("Ghost Runner: Tạm dừng")
        // NOTE: repushJob continues running during pause — keeps pushing last location
    }

    private fun resumeMocking() {
        isPaused = false
        _mockingState.value = _mockingState.value.copy(isPaused = false)
        updateNotification("Ghost Runner: Đang chạy...")
    }

    private fun stopMocking() {
        mockingJob?.cancel()
        repushJob?.cancel()
        mockLocationProvider.removeTestProvider()
        releaseWakeLock()

        lastPushedPoint = null

        if (!_mockingState.value.isCompleted) {
            _mockingState.value = MockingState(isRunning = false)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ghost Runner",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo khi đang giả lập GPS"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ghost Runner")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GhostRunner::MockLocationWakeLock"
        ).apply {
            acquire(4 * 60 * 60 * 1000L) // Max 4 hours
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        mockingJob?.cancel()
        repushJob?.cancel()
        serviceScope.cancel()
        mockLocationProvider.removeTestProvider()
        releaseWakeLock()
        super.onDestroy()
    }
}

/**
 * Observable state of the mocking service.
 */
data class MockingState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val totalPoints: Int = 0,
    val currentIndex: Int = 0,
    val currentPoint: GpsPoint? = null,
    val progress: Float = 0f,
    val error: String? = null
)
