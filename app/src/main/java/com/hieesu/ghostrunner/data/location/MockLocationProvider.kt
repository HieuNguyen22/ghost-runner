package com.hieesu.ghostrunner.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.hieesu.ghostrunner.domain.model.GpsPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides mock GPS locations via BOTH Android LocationManager test provider API
 * AND Google's FusedLocationProviderClient.
 *
 * This dual approach ensures mock locations are respected by ALL apps:
 * - LocationManager: covers apps using raw GPS/NETWORK providers
 * - FusedLocationProviderClient: covers apps like Strava that use Google Play Services
 *
 * Requirements:
 * - Device must have Developer Options enabled
 * - This app must be selected as "Mock Location App" in Developer Options
 */
@Singleton
class MockLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var isGpsProviderAdded = false
    private var isNetworkProviderAdded = false
    private var isFusedMockMode = false

    /**
     * Set up test providers for BOTH GPS and Network, plus FusedLocation mock mode.
     * Must be called before [setMockLocation].
     */
    @SuppressLint("MissingPermission")
    fun setupTestProvider() {
        setupProvider(LocationManager.GPS_PROVIDER)
        setupProvider(LocationManager.NETWORK_PROVIDER)
        setupFusedMockMode()
    }

    private fun setupProvider(providerName: String) {
        val isAlreadyAdded = when (providerName) {
            LocationManager.GPS_PROVIDER -> isGpsProviderAdded
            LocationManager.NETWORK_PROVIDER -> isNetworkProviderAdded
            else -> false
        }
        if (isAlreadyAdded) return

        try {
            // Remove existing test provider if any
            try {
                locationManager.removeTestProvider(providerName)
            } catch (_: Exception) { }

            val accuracy = when (providerName) {
                LocationManager.GPS_PROVIDER -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        ProviderProperties.ACCURACY_FINE
                    else android.location.Criteria.ACCURACY_FINE
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        ProviderProperties.ACCURACY_FINE
                    else android.location.Criteria.ACCURACY_FINE
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(
                    providerName,
                    false,  // requiresNetwork
                    false,  // requiresSatellite
                    false,  // requiresCell
                    false,  // hasMonetaryCost
                    true,   // supportsAltitude
                    true,   // supportsSpeed
                    true,   // supportsBearing
                    ProviderProperties.POWER_USAGE_LOW,
                    accuracy
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    providerName,
                    false, false, false, false,
                    true, true, true,
                    android.location.Criteria.POWER_LOW,
                    accuracy
                )
            }

            locationManager.setTestProviderEnabled(providerName, true)

            when (providerName) {
                LocationManager.GPS_PROVIDER -> isGpsProviderAdded = true
                LocationManager.NETWORK_PROVIDER -> isNetworkProviderAdded = true
            }
        } catch (e: SecurityException) {
            throw MockLocationNotAllowedException(
                "Mock location not allowed. Please enable Developer Options and select this app as Mock Location App.",
                e
            )
        } catch (e: IllegalArgumentException) {
            // Provider may not exist on this device (e.g. network provider)
            // Continue silently — GPS provider is the critical one
        }
    }

    /**
     * Enable mock mode on FusedLocationProviderClient.
     * This is CRITICAL for apps like Strava that use Google Play Services location.
     */
    @SuppressLint("MissingPermission")
    private fun setupFusedMockMode() {
        if (isFusedMockMode) return
        try {
            fusedLocationClient.setMockMode(true)
            isFusedMockMode = true
        } catch (e: SecurityException) {
            // If this fails, LocationManager mocking may still work
        }
    }

    /**
     * Push a mock GPS location to ALL providers (GPS, Network, Fused).
     *
     * @param point The GPS point to broadcast as the device's current location
     */
    @SuppressLint("MissingPermission")
    fun setMockLocation(point: GpsPoint) {
        if (!isGpsProviderAdded) {
            setupTestProvider()
        }

        val elapsedNanos = SystemClock.elapsedRealtimeNanos()
        val currentTimeMs = System.currentTimeMillis()

        // Push to GPS_PROVIDER
        val gpsLocation = createLocation(LocationManager.GPS_PROVIDER, point, currentTimeMs, elapsedNanos)
        try {
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsLocation)
        } catch (_: Exception) { }

        // Push to NETWORK_PROVIDER
        if (isNetworkProviderAdded) {
            val networkLocation = createLocation(LocationManager.NETWORK_PROVIDER, point, currentTimeMs, elapsedNanos)
            try {
                locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, networkLocation)
            } catch (_: Exception) { }
        }

        // Push to FusedLocationProviderClient
        if (isFusedMockMode) {
            val fusedLocation = createLocation("fused", point, currentTimeMs, elapsedNanos)
            try {
                fusedLocationClient.setMockLocation(fusedLocation)
            } catch (_: Exception) { }
        }
    }

    /**
     * Create a Location object with all fields properly set.
     */
    private fun createLocation(
        provider: String,
        point: GpsPoint,
        timeMs: Long,
        elapsedNanos: Long
    ): Location {
        return Location(provider).apply {
            latitude = point.latitude
            longitude = point.longitude
            altitude = point.altitude
            speed = point.speed
            bearing = point.bearing
            accuracy = point.accuracy
            time = timeMs
            elapsedRealtimeNanos = elapsedNanos

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 5.0f
                verticalAccuracyMeters = 3.0f
                speedAccuracyMetersPerSecond = 0.5f
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                elapsedRealtimeUncertaintyNanos = 0.0
            }
        }
    }

    /**
     * Clean up all test providers and disable mock mode.
     * Call when mocking is complete.
     */
    @SuppressLint("MissingPermission")
    fun removeTestProvider() {
        // Disable fused mock mode FIRST
        if (isFusedMockMode) {
            try {
                fusedLocationClient.setMockMode(false)
            } catch (_: Exception) { }
            isFusedMockMode = false
        }

        // Remove GPS test provider
        if (isGpsProviderAdded) {
            try {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            } catch (_: Exception) { }
            isGpsProviderAdded = false
        }

        // Remove Network test provider
        if (isNetworkProviderAdded) {
            try {
                locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
                locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) { }
            isNetworkProviderAdded = false
        }
    }
}

class MockLocationNotAllowedException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
