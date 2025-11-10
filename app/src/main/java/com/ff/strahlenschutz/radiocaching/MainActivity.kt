package com.ff.strahlenschutz.radiocaching

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ff.strahlenschutz.radiocaching.ui.theme.RadioCachingTheme

// Imports für Zeitsteuerung
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

// Imports für GPS-Koordinaten
import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.annotation.SuppressLint


class LocationViewModel : ViewModel() {
    private val _locationState = MutableStateFlow("Lat: -, Lon: -")
    val locationState: StateFlow<String> = _locationState

    fun updateLocation(lat: Double, lon: Double) {
        viewModelScope.launch {
            _locationState.value = "Lat: %.5f, Lon: %.5f".format(lat, lon)
        }
    }
}


class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = LocationViewModel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkPermissions()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    viewModel.updateLocation(it.latitude, it.longitude)
                }
            }
        }

        if (hasLocationPermissions()) {
            startLocationUpdates()
        }

        setContent {
            RadioCachingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    val coord by viewModel.locationState.collectAsState()

                    PrintUserOutput(
                        teamNr = 1,
                        coord = coord,
                        doseRate = "0.120 uSv/h",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun hasLocationPermissions(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun checkPermissions() {
        if (!hasLocationPermissions()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }


    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(10000L).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }
}

@Composable
fun PrintUserOutput(teamNr: Int, coord: String, doseRate: String, modifier: Modifier = Modifier) {
    var userText = "Strahlen-Spürtrupp: $teamNr\n\n"
    userText += "GPS Koordinaten: $coord\n"
    userText += "Dosisleistung: $doseRate"
    Text(
        text = userText,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RadioCachingTheme {
        PrintUserOutput(teamNr = 1, coord = "-", doseRate = "-")
    }
}