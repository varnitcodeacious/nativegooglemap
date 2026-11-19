package com.example.nativemap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, null)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F7F8)) {
                    RideTrackingScreen()
                }
            }
        }
    }
}

@Composable
private fun RideTrackingScreen() {
    val context = LocalContext.current
    val pickup = remember { LatLng(28.5705, 77.3920) }
    val destination = remember { LatLng(28.6675, 77.2285) }
    val driverStart = remember { LatLng(28.5740, 77.3870) }

    var driverPosition by remember { mutableStateOf(driverStart) }
    var gpsTarget by remember { mutableStateOf<LatLng?>(null) }
    var isLiveMode by rememberSaveable { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(context.hasLocationPermission())
    }
    var status by remember { mutableStateOf("Mock mode · Drag the car on the map") }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(driverStart, 15f)
    }
    val driverMarkerState = rememberUpdatedMarkerState(position = driverPosition)
    val driverIcon = rememberDriverIcon(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            isLiveMode = true
            status = "Live GPS · Waiting for location fix…"
        } else {
            isLiveMode = false
            status = "Location permission denied · Mock mode is ready"
        }
    }

    // Animate the same marker between GPS fixes so location callbacks do not make it jump.
    LaunchedEffect(gpsTarget) {
        val target = gpsTarget ?: return@LaunchedEffect
        val start = driverPosition
        val progress = Animatable(0f)
        progress.animateTo(1f, tween(durationMillis = 650, easing = LinearEasing)) {
            driverPosition = LatLng(
                start.latitude + (target.latitude - start.latitude) * value,
                start.longitude + (target.longitude - start.longitude) * value
            )
        }
    }

    // Keep the camera centered on the driver while live updates arrive.
    LaunchedEffect(gpsTarget) {
        gpsTarget?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLng(it), 650) }
    }

    // Follow the car after a mock drag, while leaving the map free to pan during the drag.
    LaunchedEffect(driverMarkerState, isLiveMode) {
        var wasDragging = false
        snapshotFlow { driverMarkerState.position to driverMarkerState.isDragging }
            .collect { (position, isDragging) ->
                if (!isLiveMode) {
                    driverPosition = position
                    status = "Mock mode · ${position.latitude.formatCoordinate()}, ${position.longitude.formatCoordinate()}"
                    if (wasDragging && !isDragging) {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLng(position))
                    }
                }
                wasDragging = isDragging
            }
    }

    // Subscribe only while live mode is selected; the map and marker stay mounted.
    DisposableEffect(isLiveMode, hasLocationPermission) {
        if (!isLiveMode || !hasLocationPermission) {
            onDispose { }
        } else {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val position = LatLng(location.latitude, location.longitude)
                    gpsTarget = position
                    status = "Live GPS · ${position.latitude.formatCoordinate()}, ${position.longitude.formatCoordinate()}"
                }
            }
            try {
                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 700L, 0f, listener, Looper.getMainLooper())
                }
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
                }
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                    ?.let { location ->
                        gpsTarget = LatLng(location.latitude, location.longitude)
                    }
            } catch (_: SecurityException) {
                isLiveMode = false
                status = "Location permission needed · Mock mode is ready"
            }
            onDispose { runCatching { manager.removeUpdates(listener) } }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Noida Sector 101  →  Kashmere Gate",
            modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 14.dp),
            color = Color(0xFF14252D),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = remember {
                    MapUiSettings(compassEnabled = true, mapToolbarEnabled = false)
                }
            ) {
                Marker(
                    state = rememberUpdatedMarkerState(position = pickup),
                    title = "Pickup · Noida Sector 101",
                    snippet = "Point A"
                )
                Marker(
                    state = rememberUpdatedMarkerState(position = destination),
                    title = "Destination · Kashmere Gate",
                    snippet = "Point B"
                )
                Marker(
                    state = driverMarkerState,
                    title = "Driver",
                    snippet = if (isLiveMode) "Live location" else "Drag to mock driver movement",
                    draggable = !isLiveMode,
                    flat = true,
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                    icon = driverIcon
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 14.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = status,
                    color = Color(0xFF263A40),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            isLiveMode = false
                            gpsTarget = null
                            status = "Mock mode · Drag the car on the map"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F7568))
                    ) {
                        Text("Mock · drag")
                    }
                    OutlinedButton(
                        onClick = {
                            if (context.hasLocationPermission()) {
                                hasLocationPermission = true
                                isLiveMode = true
                                status = "Live GPS · Waiting for location fix…"
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Use my location")
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberDriverIcon(context: Context): BitmapDescriptor? = remember(context) {
    MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST, null)
    val source = BitmapFactory.decodeResource(context.resources, R.drawable.driver_icon) ?: return@remember null
    val enlarged = Bitmap.createScaledBitmap(
        source,
        (source.width * 1.5f).toInt(),
        (source.height * 1.5f).toInt(),
        true
    )
    val descriptor = BitmapDescriptorFactory.fromBitmap(enlarged)
    if (enlarged !== source) source.recycle()
    descriptor
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Double.formatCoordinate(): String = String.format(Locale.US, "%.5f", this)
