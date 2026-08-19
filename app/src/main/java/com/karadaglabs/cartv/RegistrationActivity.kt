package com.karadaglabs.cartv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.karadaglabs.cartv.ui.theme.KaradagCarTVTheme
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RegistrationActivity : ComponentActivity() {

    private val deviceUuid by lazy { AppSession.deviceUuid(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedEmail = AppSession.email(this)
        val savedLicense = AppSession.licenseKey(this)

        if (savedEmail.isNotBlank() && savedLicense.isNotBlank()) {
            openMain()
            return
        }

        setContent {
            KaradagCarTVTheme {
                RegistrationScreen()
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun request(
        path: String,
        body: JSONObject
    ): Pair<Int, JSONObject> {
        val connection = URL("${AppSession.API_BASE_URL}$path")
            .openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val text = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            code to JSONObject(text.ifBlank { "{}" })
        } finally {
            connection.disconnect()
        }
    }

    @androidx.compose.runtime.Composable
    private fun RegistrationScreen() {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("") }
        var statusCode by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var applicationSent by remember { mutableStateOf(false) }

        fun checkStatus() {
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank()) {
                status = "Önce e-posta adresinizi girin."
                return
            }

            loading = true
            Thread {
                try {
                    val (_, response) = request(
                        "/api/customer/status",
                        JSONObject().apply {
                            put("email", cleanEmail)
                            put("device_uuid", deviceUuid)
                        }
                    )

                    val currentStatus = response.optString("status")
                    val message = response.optString(
                        "message",
                        "Başvuru durumu alınamadı."
                    )
                    val license = response.optJSONObject("license")
                        ?.optString("key")
                        .orEmpty()

                    runOnUiThread {
                        statusCode = currentStatus
                        status = message
                        loading = false

                        if (currentStatus == "active" && license.isNotBlank()) {
                            AppSession.saveLicense(this, cleanEmail, license)
                            openMain()
                        }
                    }
                } catch (exception: Exception) {
                    runOnUiThread {
                        status = exception.message ?: "Sunucuya bağlanılamadı."
                        loading = false
                    }
                }
            }.start()
        }

        LaunchedEffect(applicationSent) {
            if (applicationSent) {
                while (true) {
                    delay(10_000)
                    checkStatus()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "KARADAG CAR TV",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Kullanıma başlamak için başvuru yapın.",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ad Soyad") },
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("E-posta") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )

            Spacer(Modifier.height(20.dp))

            Button(
                enabled = !loading && name.trim().length >= 2 && email.contains("@"),
                onClick = {
                    loading = true
                    Thread {
                        try {
                            val cleanEmail = email.trim().lowercase()
                            val (_, response) = request(
                                "/api/customer/apply",
                                JSONObject().apply {
                                    put("name", name.trim())
                                    put("email", cleanEmail)
                                    put("device_uuid", deviceUuid)
                                    put("device_name", android.os.Build.MODEL)
                                    put("device_model", android.os.Build.MODEL)
                                    put("android_version", android.os.Build.VERSION.RELEASE)
                                    put("app_version", "1.0")
                                }
                            )

                            runOnUiThread {
                                statusCode = response.optString("status")
                                status = response.optString(
                                    "message",
                                    "Başvuru durumu alınamadı."
                                )
                                loading = false
                                applicationSent = true
                            }
                        } catch (exception: Exception) {
                            runOnUiThread {
                                status = exception.message ?: "Başvuru gönderilemedi."
                                loading = false
                            }
                        }
                    }.start()
                }
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp))
                } else {
                    Text("Başvuru Yap")
                }
            }

            Spacer(Modifier.height(20.dp))

            if (loading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
            }

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (statusCode.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (statusCode) {
                        "pending" -> "Durum: Onay bekleniyor"
                        "active" -> "Durum: Aktif"
                        "cancelled" -> "Durum: İptal edildi"
                        "rejected" -> "Durum: Reddedildi"
                        else -> ""
                    }
                )
            }

            if (applicationSent) {
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = !loading,
                    onClick = { checkStatus() }
                ) {
                    Text("Durumu Kontrol Et")
                }
            }
        }
    }
}