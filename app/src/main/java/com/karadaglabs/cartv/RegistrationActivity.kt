package com.karadaglabs.cartv

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    private val deviceUuid by lazy {
        AppSession.deviceUuid(this)
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val savedEmail =
            AppSession.email(this)

        val savedLicense =
            AppSession.licenseKey(this)

        if (
            savedEmail.isNotBlank() &&
            savedLicense.isNotBlank()
        ) {

            /*
             * Lisans zaten aktif.
             * MediaProjection yalnızca Mirror kullanılınca
             * istenir; IPTV / YouTube / Radyo buna bağlı değil.
             */
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

        startActivity(
            Intent(
                this,
                MainActivity::class.java
            )
        )

        finish()
    }

    private fun request(
        path: String,
        body: JSONObject
    ): Pair<Int, JSONObject> {

        val connection =
            URL(
                "${AppSession.API_BASE_URL}$path"
            ).openConnection()
                    as HttpURLConnection

        return try {

            connection.requestMethod = "POST"

            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            connection.useCaches = false

            connection.doInput = true
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val payload =
                body.toString()
                    .toByteArray(
                        Charsets.UTF_8
                    )

            connection.setFixedLengthStreamingMode(
                payload.size
            )

            connection.outputStream.use { output ->

                output.write(payload)
                output.flush()
            }

            val code =
                connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val text =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    .orEmpty()

            val json =
                runCatching {

                    JSONObject(
                        text.ifBlank {
                            "{}"
                        }
                    )

                }.getOrElse {

                    JSONObject()
                        .put(
                            "message",
                            "Sunucudan geçersiz cevap alındı. HTTP $code"
                        )
                }

            code to json

        } finally {

            connection.disconnect()
        }
    }

    @Composable
    private fun RegistrationScreen() {

        var ui by remember {
            mutableStateOf(
                RegistrationUiState.initial()
            )
        }

        fun openLicensedSessionIfReady(
            next: RegistrationUiState
        ) {

            if (!next.shouldOpenLicensedSession) {
                return
            }

            AppSession.saveLicense(
                this@RegistrationActivity,
                next.email,
                next.licenseKey
            )

            openMain()
        }

        fun checkStatus() {

            val started =
                ui.beginStatusCheck()

            ui = started

            if (
                !started.mayCallStatusApi ||
                !started.loading
            ) {
                return
            }

            val cleanEmail =
                started.email
                    .trim()
                    .lowercase()

            Thread {

                try {

                    val (
                        httpCode,
                        response
                    ) = request(
                        "/api/customer/status",
                        JSONObject().apply {

                            put(
                                "email",
                                cleanEmail
                            )

                            put(
                                "device_uuid",
                                deviceUuid
                            )
                        }
                    )

                    val snapshot =
                        CustomerStatus.parse(
                            httpCode,
                            response
                        )

                    runOnUiThread {

                        val next =
                            ui.completeStatus(
                                snapshot
                            )

                        ui = next

                        openLicensedSessionIfReady(
                            next
                        )
                    }

                } catch (
                    exception: Exception
                ) {

                    runOnUiThread {

                        ui = ui.failStatus(
                            exception.message
                                ?: "Bilinmeyen bağlantı hatası."
                        )
                    }
                }

            }.start()
        }

        LaunchedEffect(
            ui.autoPolling
        ) {

            if (ui.autoPolling) {

                while (true) {

                    delay(15_000)

                    if (!ui.autoPolling) {
                        break
                    }

                    checkStatus()
                }
            }
        }

        Box(
            modifier =
                Modifier.fillMaxSize(),
            contentAlignment =
                Alignment.Center
        ) {

            Column(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            horizontal = 120.dp,
                            vertical = 48.dp
                        ),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Center
            ) {

                Text(
                    text = "KARADAG CAR TV",
                    style =
                        MaterialTheme
                            .typography
                            .headlineLarge
                )

                Button(
                    onClick = {
                        startActivity(
                            Intent(
                                this@RegistrationActivity,
                                DiscoveryDiagnosticActivity::class.java
                            )
                        )
                    }
                ) {
                    Text("AA Discovery")
                }

                Spacer(
                    Modifier.height(12.dp)
                )

                Text(
                    text =
                        if (ui.applicationSent) {
                            "Başvurunuz alındı. Yönetici onayı bekleniyor."
                        } else {
                            "Kullanıma başlamak için başvuru yapın."
                        },
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Spacer(
                    Modifier.height(28.dp)
                )

                Text(
                    text = "Cihaz: $deviceUuid",
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )

                Spacer(
                    Modifier.height(16.dp)
                )

                if (ui.showApplyButton) {

                    OutlinedTextField(

                        value = ui.name,

                        onValueChange = {
                            ui = ui.withName(it)
                        },

                        enabled =
                            ui.fieldsEditable,

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            Text("Ad Soyad")
                        },

                        singleLine = true
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    OutlinedTextField(

                        value = ui.email,

                        onValueChange = {
                            ui = ui.withEmail(it)
                        },

                        enabled =
                            ui.fieldsEditable,

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            Text("E-posta")
                        },

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Email
                            ),

                        singleLine = true
                    )

                    Spacer(
                        Modifier.height(20.dp)
                    )

                    Button(

                        enabled =
                            ui.applyButtonEnabled,

                        onClick = {

                            val started =
                                ui.beginApply()

                            ui = started

                            if (!started.loading) {
                                return@Button
                            }

                            val cleanName =
                                started.name.trim()

                            val cleanEmail =
                                started.email
                                    .trim()
                                    .lowercase()

                            Thread {

                                try {

                                    val (
                                        httpCode,
                                        response
                                    ) = request(

                                        "/api/customer/apply",

                                        JSONObject().apply {

                                            put(
                                                "name",
                                                cleanName
                                            )

                                            put(
                                                "email",
                                                cleanEmail
                                            )

                                            put(
                                                "device_uuid",
                                                deviceUuid
                                            )

                                            put(
                                                "device_name",
                                                Settings.Global.getString(
                                                    contentResolver,
                                                    "device_name"
                                                )
                                                    ?: android.os.Build.MODEL
                                            )

                                            put(
                                                "device_model",
                                                android.os.Build.MODEL
                                            )

                                            put(
                                                "android_version",
                                                android.os.Build.VERSION.RELEASE
                                            )

                                            put(
                                                "app_version",
                                                "1.0"
                                            )
                                        }
                                    )

                                    val snapshot =
                                        CustomerStatus.parse(
                                            httpCode,
                                            response
                                        )

                                    val accepted =
                                        CustomerStatus.applicationAccepted(
                                            httpCode,
                                            response
                                        )

                                    runOnUiThread {

                                        val next =
                                            ui.completeApply(
                                                httpCode,
                                                snapshot,
                                                accepted
                                            )

                                        ui = next

                                        openLicensedSessionIfReady(
                                            next
                                        )
                                    }

                                } catch (
                                    exception: Exception
                                ) {

                                    runOnUiThread {

                                        ui = ui.failApply(
                                            exception.message
                                                ?: "Başvuru gönderilemedi."
                                        )
                                    }
                                }

                            }.start()
                        }

                    ) {

                        if (ui.loading) {

                            CircularProgressIndicator(
                                modifier =
                                    Modifier.height(18.dp)
                            )

                        } else {

                            Text(
                                "Başvuru Yap"
                            )
                        }
                    }

                } else {

                    Text(

                        text =
                            when (ui.statusCode) {

                                "pending" ->
                                    "🟡 Onay bekleniyor"

                                "active" ->
                                    "🟢 Aktif"

                                "cancelled" ->
                                    "⛔ İptal edildi"

                                "rejected" ->
                                    "🔴 Reddedildi"

                                CustomerStatus.DEVICE_NOT_FOUND ->
                                    "⚪ Cihaz henüz kayıtlı değil"

                                else ->
                                    "Başvuru durumu kontrol ediliyor"
                            },

                        style =
                            MaterialTheme
                                .typography
                                .titleLarge
                    )

                    Spacer(
                        Modifier.height(18.dp)
                    )

                    if (ui.showRecheckButton) {

                        Button(

                            enabled = !ui.loading,

                            onClick = {
                                checkStatus()
                            }

                        ) {

                            Text(
                                "Durumu Kontrol Et"
                            )
                        }
                    }

                    if (ui.showRetryButton) {

                        Spacer(
                            Modifier.height(12.dp)
                        )

                        Button(

                            enabled = !ui.loading,

                            onClick = {
                                ui = ui.retryApply()
                            }

                        ) {

                            Text(
                                "Yeniden Başvur"
                            )
                        }
                    }

                    if (ui.loading) {

                        Spacer(
                            Modifier.height(16.dp)
                        )

                        CircularProgressIndicator()
                    }
                }

                Spacer(
                    Modifier.height(20.dp)
                )

                if (ui.status.isNotBlank()) {

                    Text(

                        text = ui.status,

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )
                }

                if (ui.lastError.isNotBlank()) {

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Text(

                        text = ui.lastError,

                        color =
                            MaterialTheme
                                .colorScheme
                                .error,

                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )
                }
            }
        }
    }
}