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
import androidx.compose.ui.graphics.Color
import com.ff.strahlenschutz.radiocaching.ui.theme.RadioCachingTheme

import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

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

// Imports für Hive-MQTT Verbindung
import org.json.JSONObject
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.datatypes.MqttQos



class LocationViewModel : ViewModel() {
    private val _locationState = MutableStateFlow("Lat: -, Lon: -")
    val locationState: StateFlow<String> = _locationState

    // MQTT Client (Blocking client for simplicity)
    private val mqttClient = MqttClient.builder()
        .useMqttVersion3()
        .serverHost("broker.hivemq.com")
        .serverPort(8883) // TLS Port
        .sslWithDefaultConfig()
        .identifier("android-client-${System.currentTimeMillis()}")
        // Optional: .simpleAuth().username("<username>").password("<password>").applySimpleAuth()
        .buildBlocking()

    init {
        connectMqtt()
    }


    private fun connectMqtt() {
        viewModelScope.launch {
            try {
                if (mqttClient.state != MqttClientState.CONNECTED) {
                    mqttClient.connect()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun updateLocation(lat: Double, lon: Double, doseRate: Double) {
        viewModelScope.launch {
            val time = System.currentTimeMillis()
            _locationState.value = "Lat: %.5f, Lon: %.5f".format(lat, lon)

            // JSON-Daten aufbauen
            val data = JSONObject()
            data.put("latitude", lat)
            data.put("longitude", lon)
            data.put("timestamp", time)
            data.put("dose_rate", doseRate)

            // MQTT Topic mit Team Nr. 1 (kann parametrisiert werden)
            val topic = "radiocaching/ff/search_teams/2/coordinates"

            try {
                if (mqttClient.state != MqttClientState.CONNECTED) {
                    mqttClient.connect()
                }
                mqttClient.publishWith()
                    .topic(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .payload(data.toString().toByteArray())
                    .send()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        try {
            if (mqttClient.state == MqttClientState.CONNECTED) {
                mqttClient.disconnect()
            }
        } catch (e: Exception) {
            // ignore
        }
        super.onCleared()
    }

}


class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()

        val viewModel = LocationViewModel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkPermissions()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    viewModel.updateLocation(it.latitude, it.longitude, 89.6)
                }
            }
        }

        if (hasLocationPermissions()) {
            startLocationUpdates()
        }

        setContent {
            RadioCachingTheme {
                Scaffold(modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black // schwarzer Hintergrund
                ) { innerPadding ->

                    val coord by viewModel.locationState.collectAsState()
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    val currentTime = LocalDateTime.now().format(formatter)

                    PrintUserOutput(
                        teamNr = 1,
                        coord = coord,
                        ts = currentTime,
                        doseRate = "0.120 uSv/h",
                        conn = "Nein!",
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
fun PrintUserOutput(teamNr: Int, coord: String, doseRate: String, ts: String, conn: String,  modifier: Modifier = Modifier) {
    var userText = "\nStrahlen-Spürtrupp: $teamNr\n\n"
    userText += "Dosisleistung: $doseRate\n"
    userText += "GPS Koordinaten: $coord\n\n"
    userText += "Letzter Zeitpunkt: $ts\n"
    userText += "Verbunden mit Server: $conn\n"

    Text(
        text = userText,
        color = Color.White,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RadioCachingTheme {
        PrintUserOutput(teamNr = 1, coord = "-", doseRate = "-", ts = "-", conn = "-")
    }
}