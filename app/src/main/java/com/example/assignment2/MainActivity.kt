package com.example.assignment2

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

//Publisher Application
class MainActivity : AppCompatActivity() {
    private var client: Mqtt5BlockingClient? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isPublishing = false
    private lateinit var studentIdEditText: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private val locationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100).setMaxUpdateDelayMillis(120)
            .setMinUpdateDistanceMeters(0f).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        studentIdEditText = findViewById(R.id.studentIdEditText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        client = Mqtt5Client.builder().identifier(UUID.randomUUID().toString())
            .serverHost("broker-816037392.sundaebytestt.com").serverPort(1883).build().toBlocking()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startButton.setOnClickListener {
            val studentId = studentIdEditText.text.toString()
            if (studentId.length == 9) {
                startPublishing()
                Toast.makeText(
                    this, "Publishing location for student ID: $studentId", Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this, "Please enter a valid 9-digit student ID", Toast.LENGTH_SHORT
                ).show()
            }
        }

        stopButton.setOnClickListener {
            stopPublishing()
            Toast.makeText(this, "Stopped publishing location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPublishing() {
        if (isPublishing) {
            Log.d("MainActivity", "Already publishing")
            return
        }
        isPublishing = true
        connect()
        startLocationUpdates()
    }

    private fun stopPublishing() {
        if (!isPublishing) {
            Log.d("MainActivity", "Not currently publishing")
            return
        }
        isPublishing = false
        stopLocationUpdates()
        disconnect()
    }

    private fun connect() {
        try {
            client?.connect()
            Log.d("MainActivity", "Successfully connected to MQTT broker")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error connecting to broker: ${e.message}")
            Toast.makeText(
                this, "Failed to connect to broker: ${e.message}", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun disconnect() {
        try {
            client?.disconnect()
            Log.d("MainActivity", "Successfully disconnected from MQTT broker")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error disconnecting from broker: ${e.message}")
            Toast.makeText(
                this, "Failed to disconnect from broker: ${e.message}", Toast.LENGTH_SHORT
            ).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper()
        )
        Log.d("MainActivity", "Started location updates")
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("MainActivity", "Stopped location updates")
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location: Location = locationResult.lastLocation!!
            if (isPublishing) publishLocation(location)
        }
    }

    private fun publishLocation(location: Location) {
        val studentId = studentIdEditText.text.toString()
        val locationdata = LocationData(
            studentId.toInt(),
            location.latitude,
            location.longitude,
            location.speed,
            DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        )
        Log.d("MainActivity", "Publishing location: $locationdata")
        val jsonifiedLocationData = Gson().toJson(locationdata)
        try {
            client?.publishWith()?.topic("assignment/location")
                ?.payload(jsonifiedLocationData.toByteArray())?.send()
            Log.d(
                "MainActivity",
                "Published: ${Gson().fromJson(jsonifiedLocationData, LocationData::class.java)}"
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Error publishing message: ${e.message}")
            Toast.makeText(
                this, "Failed to publish message: ${e.message}", Toast.LENGTH_SHORT
            ).show()
        }
    }
}