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

    /*
     * Stale HTTP cevaplarının idle formu tekrar
     * loading'e çekmesini engeller.
     */
    private var registrationRequestSeq = 0

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

        var state by remember {
            mutableStateOf(
                RegistrationFormState()
            )
        }

        fun activateLicense(
            email: String,
            licenseKey: String
        ) {

            AppSession.saveLicense(
                this@RegistrationActivity,
                email,
                licenseKey
            )

            requestMediaProjection()
        }

        fun applyOutcome(
            outcome: RegistrationStatusOutcome
        ) {

            state = outcome.state

            if (outcome.shouldActivateLicense) {

                activateLicense(
                    outcome.email.orEmpty(),
                    outcome.licenseKey.orEmpty()
                )
            }
        }

        fun checkStatus() {

            val attempt =
                state.beginStatusCheck()

            state = attempt.state

            val ready =
                attempt.start as? RegistrationStatusStart.Ready
                    ?: return

            val requestId =
                ++registrationRequestSeq

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
                                ready.email
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

                        if (requestId != registrationRequestSeq) {
                            return@runOnUiThread
                        }

                        applyOutcome(
                            state.onStatusSnapshot(
                                snapshot
                            )
                        )
                    }

                } catch (
                    exception: Exception
                ) {

                    runOnUiThread {

                        if (requestId != registrationRequestSeq) {
                            return@runOnUiThread
                        }

                        state =
                            state.onStatusError(
                                exception.message
                                    ?: "Bilinmeyen bağlantı hatası."
                            )
                    }
                }

            }.start()
        }

        fun submitApplication() {

            val attempt =
                state.beginApply()

            state = attempt.state

            val ready =
                attempt.start as? RegistrationApplyStart.Ready
                    ?: return

            val requestId =
                ++registrationRequestSeq

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
                                ready.name
                            )

                            put(
                                "email",
                                ready.email
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

                    runOnUiThread {

                        if (requestId != registrationRequestSeq) {
                            return@runOnUiThread
                        }

                        if (
                            CustomerStatus.applicationAccepted(
                                httpCode,
                                response
                            )
                        ) {

                            applyOutcome(
                                state.onApplyAccepted(
                                    snapshot
                                )
                            )

                        } else {

                            state =
                                state.onApplyRejected(
                                    httpCode,
                                    snapshot
                                )
                        }
                    }

                } catch (
                    exception: Exception
                ) {

                    runOnUiThread {

                        if (requestId != registrationRequestSeq) {
                            return@runOnUiThread
                        }

                        state =
                            state.onApplyError(
                                exception.message
                                    ?: "Başvuru gönderilemedi."
                            )
                    }
                }

            }.start()
        }

        LaunchedEffect(
            state.autoPolling,
            state.applicationSent
        ) {

            if (
                !state.autoPolling ||
                !state.applicationSent
            ) {
                return@LaunchedEffect
            }

            while (true) {

                delay(15_000)

                if (
                    !state.autoPolling ||
                    !state.applicationSent
                ) {
                    break
                }

                checkStatus()
            }
        }

        Column(

            modifier =
                Modifier
                    .fillMaxSize()
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

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                text =
                    if (state.applicationSent) {
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

            if (state.applyButtonVisible) {

                OutlinedTextField(

                    value = state.name,

                    onValueChange = {
                        state = state.updateName(it)
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    enabled = state.fieldsEnabled,

                    readOnly = state.fieldsReadOnly,

                    label = {
                        Text("Ad Soyad")
                    },

                    singleLine = true
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                OutlinedTextField(

                    value = state.email,

                    onValueChange = {
                        state = state.updateEmail(it)
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    enabled = state.fieldsEnabled,

                    readOnly = state.fieldsReadOnly,

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

                    enabled = state.applyButtonEnabled,

                    onClick = {
                        submitApplication()
                    }

                ) {

                    Text(
                        "Başvuru Yap"
                    )
                }

            } else {

                Text(

                    text =
                        when (state.statusCode) {

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

                    enabled = !state.loading,

                    onClick = {
                        checkStatus()
                    }

                ) {

                    Text(
                        "Durumu Kontrol Et"
                    )
                }

                if (state.showRetryApplyButton) {

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    Button(

                        enabled = !state.loading,

                        onClick = {
                            state = state.retryApply()
                        }

                    ) {

                        Text(
                            "Yeniden Başvur"
                        )
                    }
                }
            }

            if (
                state.showProgressIndicator &&
                state.applicationSent
            ) {

                Spacer(
                    Modifier.height(16.dp)
                )

                CircularProgressIndicator()
            }

            Spacer(
                Modifier.height(20.dp)
            )

            if (state.status.isNotBlank()) {

                Text(

                    text = state.status,

                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )
            }

            if (state.lastError.isNotBlank()) {

                Spacer(
                    Modifier.height(10.dp)
                )

                Text(

                    text = state.lastError,

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