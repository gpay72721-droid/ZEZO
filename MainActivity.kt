package com.example.mobilefaultdetector

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.squareup.moshi.Moshi
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class TestResult(val name: String, val status: String, val details: String)

class MainActivity : ComponentActivity() {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppUI(this)
        }
    }

    @Composable
    fun AppUI(activity: Activity) {
        var running by remember { mutableStateOf(false) }
        var results by remember { mutableStateOf(listOf<TestResult>()) }
        val permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val requestPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            // no-op
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = "Mobile Fault Detector", style = androidx.compose.material.MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = {
                requestPermLauncher.launch(permissions.toTypedArray())
                running = true
                results = listOf()
                results = results + runBatteryCheck()
                results = results + runStorageCheck()
                results = results + runMemoryCheck()
                results = results + runSensorsCheck()
                results = results + runNetworkCheck()
                results = results + runVibrationTest(activity)
                results = results + runTouchscreenPrompt(activity)
                running = false
            }) { Text("Run Full Diagnostic") }

            Spacer(modifier = Modifier.height(8.dp))

            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Running checks...")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn {
                items(results.size) { idx ->
                    val r = results[idx]
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "${'$'}{r.name} — ${'$'}{r.status}")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = r.details)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(List::class.java)
                val fname = "mfd_report_${'$'}{SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
                val outFile = File(getExternalFilesDir(null), fname)
                outFile.writeText(adapter.toJson(results))
                Toast.makeText(this@MainActivity, "Report saved: ${'$'}{outFile.absolutePath}", Toast.LENGTH_LONG).show()
            }) { Text("Export Report (JSON)") }
        }
    }

    private fun runBatteryCheck(): TestResult {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = if (Build.VERSION.SDK_INT >= 21) {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else -1
        val health = getBatteryHealth()
        val status = if (level >= 0 && level < 10) "WARN" else "OK"
        val details = "level=${'$'}level% , health=${'$'}health"
        return TestResult("Battery", status, details)
    }

    private fun getBatteryHealth(): String {
        val intent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        return when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            else -> "UNKNOWN"
        }
    }

    private fun runStorageCheck(): TestResult {
        val stat = getExternalFilesDir(null)
        val free = stat?.freeSpace ?: -1
        val total = stat?.totalSpace ?: -1
        val percent = if (total > 0) (100 - (free * 100 / total)).toInt() else -1
        val status = if (percent >= 90) "WARN" else "OK"
        val details = "used=${'$'}percent% , free=${'$'}{free/1024/1024}MB"
        return TestResult("Storage", status, details)
    }

    private fun runMemoryCheck(): TestResult {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val max = runtime.maxMemory() / 1024 / 1024
        val status = if (used > max * 0.9) "WARN" else "OK"
        val details = "used=${'$'}usedMB / max=${'$'}maxMB"
        return TestResult("Memory (app-level)", status, details)
    }

    private fun runSensorsCheck(): TestResult {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensors = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL)
        val names = sensors.map { it.name.toLowerCase(Locale.ROOT) }
        val expected = listOf("accelerometer", "gyroscope", "proximity", "light")
        val missing = expected.filter { exp -> names.none { it.contains(exp) } }
        val status = if (missing.isEmpty()) "OK" else "WARN"
        return TestResult("Sensors", status, "missing: ${'$'}{missing.joinToString(", ")}")
    }

    private fun runNetworkCheck(): TestResult {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(net)
        val details = if (caps == null) "no network" else caps.transportTypes().joinToString(", ") { it.toString() }
        val status = if (caps == null) "WARN" else "OK"
        return TestResult("Network", status, details)
    }

    private fun runVibrationTest(activity: Activity): TestResult {
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        return if (vib.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(300)
            }
            TestResult("Vibrator", "OK", "Vibrated for 300ms")
        } else TestResult("Vibrator", "FAIL", "No vibrator present")
    }

    private fun runTouchscreenPrompt(activity: Activity): TestResult {
        return TestResult("Touchscreen", "MANUAL", "Please open Touch Test screen and follow instructions")
    }

    private fun NetworkCapabilities.transportTypes(): List<String> {
        val types = mutableListOf<String>()
        if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) types.add("WIFI")
        if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) types.add("CELLULAR")
        if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) types.add("ETHERNET")
        if (hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) types.add("BLUETOOTH")
        return types
    }
}
