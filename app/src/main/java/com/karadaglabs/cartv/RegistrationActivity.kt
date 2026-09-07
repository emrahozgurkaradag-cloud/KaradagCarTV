package com.karadaglabs.cartv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

    /*
     * Android ekran yakalama izin sonucu.
     */
    private val mediaProjectionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode == Activity.RESULT_OK &&
                result.data != null
            ) {

                startMirrorProjection(
                    result.resultCode,
                    result.data!!
                )

                /*
                 * Projection izni alındı.
                 * Artık normal uygulamayı açabiliriz.
                 */
                openMain()

            } else {

                Toast.makeText(
                    this,
                    "Araç ekranı yansıtması için ekran paylaşım izni gerekiyor.",
                    Toast.LENGTH_LONG
                ).show()

                /*
                 * Kullanıcı izin vermediyse yine uygulamayı
                 * kullanmasına izin veriyoruz.
                 *
                 * Sadece mirror özelliği aktif olmayacak.
                 */
                openMain()
            }
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
             *
             * Önce MediaProjection izni iste.
             * İzin verilince MainActivity açılacak.
             */
            requestMediaProjection()

            return
        }

        setContent {

            KaradagCarTVTheme {

                RegistrationScreen()
            }
        }
    }

    /**
     * Android sisteminin MediaProjection izin ekranını açar.
     */
    private fun requestMediaProjection() {

        try {

            val projectionManager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as android.media.projection.MediaProjectionManager

            val captureIntent =
                projectionManager.createScreenCaptureIntent()

            mediaProjectionLauncher.launch(
                captureIntent
            )

        } catch (exception: Exception) {

            Toast.makeText(
                this,
                "Ekran yansıtma başlatılamadı: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()

            openMain()
        }
    }

    /**
     * MediaProjection sonucu alındıktan sonra
     * gerçek mirror servisini başlatır.
     */
    private fun startMirrorProjection(
        resultCode: Int,
        resultData: Intent
    ) {

        val serviceIntent =
            Intent(
                this,
                MirrorProjectionService::class.java
            ).apply {

                action =
                    MirrorProjectionService.ACTION_START

                putExtra(
                    MirrorProjectionService.EXTRA_RESULT_CODE,
                    resultCode
                )

                putExtra(
                    MirrorProjectionService.EXTRA_RESULT_DATA,
                    resultData
                )
            }

        try {

            androidx.core.content.ContextCompat
                .startForegroundService(
                    this,
                    serviceIntent
                )

        } catch (exception: Exception) {

            Toast.makeText(
                this,
                "Mirror servisi başlatılamadı: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
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

        var name by remember {
            mutableStateOf("")
        }

        var email by remember {
            mutableStateOf("")
        }

        var status by remember {
            mutableStateOf("")
        }

        var statusCode by remember {
            mutableStateOf("")
        }

        var loading by remember {
            mutableStateOf(false)
        }

        var applicationSent by remember {
            mutableStateOf(false)
        }

        var lastError by remember {
            mutableStateOf("")
        }

        var autoPolling by remember {
            mutableStateOf(false)
        }

        fun checkStatus() {

            val cleanEmail =
                email
                    .trim()
                    .lowercase()

            if (cleanEmail.isBlank()) {

                status =
                    "E-posta adresinizi girin."

                return
            }

            loading = true
            lastError = ""

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

                        statusCode =
                            snapshot.displayStatus

                        status =
                            snapshot.message

                        loading = false

                        if (snapshot.isActive) {

                            autoPolling = false

                            AppSession.saveLicense(
                                this@RegistrationActivity,
                                cleanEmail,
                                snapshot.licenseKey
                            )

                            requestMediaProjection()
                        } else if (snapshot.isTerminalFailure) {

                            autoPolling = false
                        } else if (
                            snapshot.isDeviceNotFound
                        ) {

                            autoPolling = false

                            lastError =
                                "Cihaz UUID'si henüz bulunamadı. " +
                                    "Durumu tekrar kontrol edin veya " +
                                    "yeniden başvuru yapın."
                        }
                    }

                } catch (
                    exception: Exception
                ) {

                    runOnUiThread {

                        loading = false

                        lastError =
                            exception.message
                                ?: "Bilinmeyen bağlantı hatası."

                        status =
                            "Başvuru durumu kontrol edilemedi."
                    }
                }

            }.start()
        }

        LaunchedEffect(
            autoPolling
        ) {

            if (autoPolling) {

                while (true) {

                    delay(15_000)

                    if (!autoPolling) {
                        break
                    }

                    checkStatus()
                }
            }
        }

        Column(

            modifier =
                Modifier
                    .fillMaxSize()
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

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                text =
                    if (applicationSent) {
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

            if (!applicationSent) {

                OutlinedTextField(

                    value = name,

                    onValueChange = {
                        name = it
                    },

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

                    value = email,

                    onValueChange = {
                        email = it
                    },

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
                        !loading &&
                                name.trim().length >= 2 &&
                                email.contains("@"),

                    onClick = {

                        loading = true
                        lastError = ""

                        Thread {

                            try {

                                val cleanName =
                                    name.trim()

                                val cleanEmail =
                                    email
                                        .trim()
                                        .lowercase()

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

                                val message =
                                    snapshot.message.ifBlank {
                                        "Başvurunuz alındı. Yönetici onayı bekleniyor."
                                    }

                                runOnUiThread {

                                    email =
                                        cleanEmail

                                    statusCode =
                                        snapshot.displayStatus.ifBlank {
                                            "pending"
                                        }

                                    status =
                                        message

                                    loading = false

                                    if (
                                        CustomerStatus.applicationAccepted(
                                            httpCode,
                                            response
                                        )
                                    ) {

                                        applicationSent =
                                            true

                                        autoPolling =
                                            true

                                        if (snapshot.isActive) {

                                            AppSession.saveLicense(
                                                this@RegistrationActivity,
                                                cleanEmail,
                                                snapshot.licenseKey
                                            )

                                            requestMediaProjection()
                                        }

                                    } else {

                                        lastError =
                                            "Sunucu HTTP $httpCode döndürdü."
                                    }
                                }

                            } catch (
                                exception: Exception
                            ) {

                                runOnUiThread {

                                    loading = false

                                    lastError =
                                        exception.message
                                            ?: "Başvuru gönderilemedi."

                                    status =
                                        "Başvuru gönderilemedi. İnternet bağlantınızı kontrol edin."
                                }
                            }

                        }.start()
                    }

                ) {

                    if (loading) {

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
                        when (statusCode) {

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

                Button(

                    enabled = !loading,

                    onClick = {
                        checkStatus()
                    }

                ) {

                    Text(
                        "Durumu Kontrol Et"
                    )
                }

                if (
                    statusCode == CustomerStatus.DEVICE_NOT_FOUND ||
                    statusCode == CustomerStatus.REJECTED ||
                    statusCode == CustomerStatus.CANCELLED
                ) {

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    Button(

                        enabled = !loading,

                        onClick = {
                            autoPolling = false
                            applicationSent = false
                            statusCode = ""
                            lastError = ""
                            status = ""
                        }

                    ) {

                        Text(
                            "Yeniden Başvur"
                        )
                    }
                }

                if (loading) {

                    Spacer(
                        Modifier.height(16.dp)
                    )

                    CircularProgressIndicator()
                }
            }

            Spacer(
                Modifier.height(20.dp)
            )

            if (status.isNotBlank()) {

                Text(

                    text = status,

                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )
            }

            if (lastError.isNotBlank()) {

                Spacer(
                    Modifier.height(10.dp)
                )

                Text(

                    text = lastError,

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