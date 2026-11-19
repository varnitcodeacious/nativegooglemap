package com.example.nativemap

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.nativemap.databinding.FragmentFirstBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlin.math.abs

class FirstFragment : Fragment(), GoogleMap.OnMarkerDragListener {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private var googleMap: GoogleMap? = null
    private var driverMarker: Marker? = null
    private var liveMode = false
    private var locationManager: LocationManager? = null
    private var positionAnimator: ValueAnimator? = null
    private lateinit var permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    private val pickup = LatLng(28.5705, 77.3920) // Noida Sector 101
    private val destination = LatLng(28.6675, 77.2285) // Kashmere Gate, Delhi
    private val driverStart = LatLng(28.5740, 77.3870)

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val next = LatLng(location.latitude, location.longitude)
            val bearing = location.bearing
            moveDriverSmoothly(next, bearing)
            _binding?.movementStatus?.text = "Live GPS · ${"%.5f".format(next.latitude)}, ${"%.5f".format(next.longitude)}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) startLiveLocation() else showMockMode("Location permission denied · Mock mode is ready")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val mapFragment = childFragmentManager.findFragmentByTag(MAP_TAG) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction().replace(R.id.map_container, it, MAP_TAG).commit()
            }
        mapFragment.getMapAsync { map -> configureMap(map) }

        binding.mockModeButton.setOnClickListener {
            stopLiveLocation()
            liveMode = false
            driverMarker?.isDraggable = true
            showMockMode("Mock mode · Drag the driver marker on the map")
        }
        binding.liveModeButton.setOnClickListener { enableLiveMode() }
    }

    private fun configureMap(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isMapToolbarEnabled = false
        map.addMarker(MarkerOptions().position(pickup).title("Pickup · Noida Sector 101").snippet("Point A"))
        map.addMarker(MarkerOptions().position(destination).title("Destination · Kashmere Gate").snippet("Point B"))
        val originalIcon = BitmapFactory.decodeResource(resources, R.drawable.driver_icon)
        val largerIcon = Bitmap.createScaledBitmap(
            originalIcon,
            (originalIcon.width * DRIVER_ICON_SCALE).toInt(),
            (originalIcon.height * DRIVER_ICON_SCALE).toInt(),
            true
        )
        if (largerIcon !== originalIcon) originalIcon.recycle()

        driverMarker = map.addMarker(
            MarkerOptions()
                .position(driverStart)
                .title("Driver")
                .snippet("Drag to mock driver movement")
                .draggable(true)
                .flat(true)
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.fromBitmap(largerIcon))
        )
        map.setOnMarkerDragListener(this)
        map.setOnMapLoadedCallback {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(driverMarker?.position ?: driverStart, 15f))
        }
        if (liveMode && hasLocationPermission()) startLiveLocation()
    }

    private fun enableLiveMode() {
        liveMode = true
        driverMarker?.isDraggable = false
        if (!hasLocationPermission()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            startLiveLocation()
        }
    }

    private fun startLiveLocation() {
        if (!liveMode || !hasLocationPermission()) return
        driverMarker?.isDraggable = false
        binding.movementStatus.text = "Live GPS · Waiting for location fix…"
        try {
            val manager = locationManager ?: return
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 700L, 5f, locationListener, Looper.getMainLooper())
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1_000L, 5f, locationListener, Looper.getMainLooper())
            }
            val lastKnown = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            lastKnown?.let { moveDriverSmoothly(LatLng(it.latitude, it.longitude), it.bearing) }
        } catch (_: SecurityException) {
            showMockMode("Location permission needed · Mock mode is ready")
        }
    }

    private fun stopLiveLocation() {
        runCatching { locationManager?.removeUpdates(locationListener) }
        positionAnimator?.cancel()
        positionAnimator = null
    }

    private fun moveDriverSmoothly(target: LatLng, bearing: Float) {
        val marker = driverMarker ?: return
        val from = marker.position
        googleMap?.animateCamera(CameraUpdateFactory.newLatLng(target), 650, null)
        positionAnimator?.cancel()
        //val startBearing = marker.rotation
        positionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650L
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                marker.position = LatLng(
                    from.latitude + (target.latitude - from.latitude) * t,
                    from.longitude + (target.longitude - from.longitude) * t
                )
                //val delta = ((bearing - startBearing + 540f) % 360f) - 180f
                //marker.rotation = (startBearing + delta * t + 360f) % 360f
            }
            start()
        }
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun showMockMode(message: String) {
        liveMode = false
        driverMarker?.isDraggable = true
        _binding?.movementStatus?.text = message
    }

    override fun onMarkerDragStart(marker: Marker) = Unit
    override fun onMarkerDrag(marker: Marker) {
        if (marker == driverMarker) _binding?.movementStatus?.text =
            "Mock mode · ${"%.5f".format(marker.position.latitude)}, ${"%.5f".format(marker.position.longitude)}"
    }
    override fun onMarkerDragEnd(marker: Marker) {
        if (marker == driverMarker) {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLng(marker.position))
        }
    }

    override fun onResume() {
        super.onResume()
        if (liveMode && hasLocationPermission()) startLiveLocation()
    }

    override fun onPause() {
        stopLiveLocation()
        super.onPause()
    }

    override fun onDestroyView() {
        stopLiveLocation()
        googleMap = null
        driverMarker = null
        super.onDestroyView()
        _binding = null
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAP_TAG = "google_map"
        private const val DRIVER_ICON_SCALE = 1.5f
    }
}
