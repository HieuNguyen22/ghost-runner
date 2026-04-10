package com.hieesu.ghostrunner.ui.components

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.hieesu.ghostrunner.domain.model.GpsPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Compose wrapper for osmdroid MapView displaying a GPS route with polyline overlay.
 *
 * Uses OpenStreetMap tiles — free, no API key required.
 */
@Composable
fun OsmMapView(
    routePoints: List<GpsPoint>,
    currentPoint: GpsPoint? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Configure osmdroid
    DisposableEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
            osmdroidTileCache = context.cacheDir.resolve("tiles")
        }
        onDispose { }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()

            if (routePoints.isNotEmpty()) {
                // Draw route polyline
                val polyline = Polyline().apply {
                    outlinePaint.apply {
                        color = Color.parseColor("#FF6200EE")
                        strokeWidth = 8f
                        isAntiAlias = true
                    }
                    setPoints(routePoints.map { GeoPoint(it.latitude, it.longitude) })
                }
                view.overlays.add(polyline)

                // Start marker
                val startPoint = routePoints.first()
                val startMarker = Marker(view).apply {
                    position = GeoPoint(startPoint.latitude, startPoint.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Bắt đầu"
                }
                view.overlays.add(startMarker)

                // End marker
                val endPoint = routePoints.last()
                val endMarker = Marker(view).apply {
                    position = GeoPoint(endPoint.latitude, endPoint.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Kết thúc"
                }
                view.overlays.add(endMarker)

                // Current position marker (if running)
                currentPoint?.let { point ->
                    val currentMarker = Marker(view).apply {
                        position = GeoPoint(point.latitude, point.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Vị trí hiện tại"
                    }
                    view.overlays.add(currentMarker)
                }

                // Fit map to route bounds
                try {
                    val lats = routePoints.map { it.latitude }
                    val lngs = routePoints.map { it.longitude }
                    val boundingBox = BoundingBox(
                        lats.max(), lngs.max(),
                        lats.min(), lngs.min()
                    )
                    view.zoomToBoundingBox(boundingBox.increaseByScale(1.3f), true)
                } catch (_: Exception) {
                    // Fallback: center on first point
                    view.controller.setCenter(
                        GeoPoint(startPoint.latitude, startPoint.longitude)
                    )
                }
            }

            view.invalidate()
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }
}
