package com.karadaglabs.cartv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Owns the Android system MediaProjection consent flow.
 *
 * The result Intent is kept in-process and handed to
 * MirrorProjectionService after the foreground service has started.
 * Keeping the original Intent object avoids losing the projection token
 * while it is passed through another Intent on newer Android versions.
 */
class MediaProjectionPermissionActivity : ComponentActivity() {

    companion object {
        private const val TAG = "KaradagMirror"
    }

    private val projectionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val data = result.data

            Log.d(
                TAG,
                "MediaProjection consent result=${result.resultCode}, data=${data != null}"
            )

            if (
                result.resultCode != Activity.RESULT_OK ||
                data == null
            ) {
                Log.e(
                    TAG,
                    "MediaProjection consent was not granted"
                )

                MirrorProjectionController.markProjectionInactive()
                finish()
                return@registerForActivityResult
            }

            MirrorProjectionController.setProjectionConsent(
                result.resultCode,
                data
            )

            val serviceIntent = Intent(
                this,
                MirrorProjectionService::class.java
            ).apply {
                action = MirrorProjectionService.ACTION_START
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                Log.d(
                    TAG,
                    "MediaProjection service start requested"
                )

            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Unable to start MediaProjection service",
                    exception
                )

                MirrorProjectionController.clearProjectionConsent()
                MirrorProjectionController.markProjectionInactive()
            }

            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager =
            getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        Log.d(
            TAG,
            "Opening MediaProjection consent dialog"
        )

        projectionLauncher.launch(
            manager.createScreenCaptureIntent()
        )
    }
}
/**
 * MediaProjectionPermissionActivity ile
 * MirrorProjectionService arasındaki bağlantı.
 *
 * Video artık doğrudan Android Auto Surface'ine
 * ExoPlayer ile gönderiliyor olsa da eski
 * MediaProjection bileşenleri hâlâ projede bulunduğu
 * için bu controller gerekli.
 */
object MirrorProjectionController {

    private const val TAG =
        "MirrorProjectionController"

    private var projectionResultCode: Int? = null

    private var projectionResultData: Intent? = null

    private var projectionActive =
        false

    @Synchronized
    fun setProjectionConsent(
        resultCode: Int,
        data: Intent
    ) {

        projectionResultCode =
            resultCode

        projectionResultData =
            data

        Log.d(
            TAG,
            "Projection consent stored"
        )
    }

    @Synchronized
    fun takeProjectionConsent():
            Pair<Int, Intent>? {

        val resultCode =
            projectionResultCode

        val resultData =
            projectionResultData

        projectionResultCode = null
        projectionResultData = null

        if (
            resultCode == null ||
            resultData == null
        ) {
            return null
        }

        return Pair(
            resultCode,
            resultData
        )
    }

    @Synchronized
    fun clearProjectionConsent() {

        projectionResultCode = null
        projectionResultData = null

        Log.d(
            TAG,
            "Projection consent cleared"
        )
    }

    @Synchronized
    fun markProjectionActive() {

        projectionActive = true

        Log.d(
            TAG,
            "Projection marked ACTIVE"
        )
    }

    @Synchronized
    fun markProjectionInactive() {

        projectionActive = false

        Log.d(
            TAG,
            "Projection marked INACTIVE"
        )
    }

    fun isProjectionActive(): Boolean {

        return projectionActive
    }

    /**
     * Mirror yalnızca Android Auto template yüzeyi hazır
     * olduğunda izin ister. Telefon uygulamasının normal
     * açılışı bu diyaloga bağlı değildir.
     */
    @Synchronized
    fun requestConsentOnPhone(
        context: Context
    ) {

        if (projectionActive) {

            Log.d(
                TAG,
                "Projection already active; consent not requested"
            )

            return
        }

        val appContext =
            context.applicationContext

        val intent =
            Intent(
                appContext,
                MediaProjectionPermissionActivity::class.java
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

        try {

            appContext.startActivity(
                intent
            )

            Log.d(
                TAG,
                "MediaProjection consent requested for Mirror"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to request MediaProjection consent",
                exception
            )
        }
    }
}