package com.karadaglabs.cartv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Requests the user's MediaProjection consent and then starts
 * MirrorProjectionService with the returned projection token.
 */
class MediaProjectionPermissionActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val data = result.data

            if (
                result.resultCode == Activity.RESULT_OK &&
                data != null
            ) {
                val serviceIntent = Intent(
                    this,
                    MirrorProjectionService::class.java
                ).apply {
                    action = MirrorProjectionService.ACTION_START
                    putExtra(
                        MirrorProjectionService.EXTRA_RESULT_CODE,
                        result.resultCode
                    )
                    putExtra(
                        MirrorProjectionService.EXTRA_RESULT_DATA,
                        data
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                MirrorProjectionController.markProjectionActive()
            } else {
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

        projectionLauncher.launch(
            manager.createScreenCaptureIntent()
        )
    }
}
