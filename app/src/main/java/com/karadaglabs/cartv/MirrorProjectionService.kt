package com.karadaglabs.cartv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Surface

class MirrorProjectionService : Service() {

    companion object {
        private const val TAG = "KaradagMirror"

        const val ACTION_START =
            "com.karadaglabs.cartv.action.START_MIRROR"
        const val ACTION_STOP =
            "com.karadaglabs.cartv.action.STOP_MIRROR"
        const val ACTION_REFRESH =
            "com.karadaglabs.cartv.action.REFRESH_MIRROR"
        const val ACTION_SURFACE_LOST =
            "com.karadaglabs.cartv.action.SURFACE_LOST"

        const val EXTRA_RESULT_CODE =
            "projection_result_code"
        const val EXTRA_RESULT_DATA =
            "projection_result_data"

        private const val CHANNEL_ID =
            "karadag_car_tv_mirror"
        private const val NOTIFICATION_ID = 1001

        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
        private const val DEFAULT_DPI = 160
    }

    private var mediaProjection: MediaProjection? = null

    private var virtualDisplay:
            android.hardware.display.VirtualDisplay? = null

    private var projectionSurface: Surface? = null

    private var projectionWidth = 0
    private var projectionHeight = 0
    private var projectionDpi = 0

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        Log.d(TAG, "MirrorProjectionService created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                val resultCode = intent.getIntExtra(
                    EXTRA_RESULT_CODE,
                    -1
                )

                val resultData =
                    if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(
                            EXTRA_RESULT_DATA,
                            Intent::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(
                            EXTRA_RESULT_DATA
                        )
                    }

                if (
                    resultCode == -1 ||
                    resultData == null
                ) {
                    Log.e(
                        TAG,
                        "Invalid MediaProjection result"
                    )

                    MirrorProjectionController
                        .markProjectionInactive()

                    stopSelf()
                    return START_NOT_STICKY
                }

                startMirror(
                    resultCode,
                    resultData
                )
            }

            ACTION_REFRESH -> {
                Log.d(
                    TAG,
                    "Surface refresh requested"
                )

                createVirtualDisplayIfPossible()
            }

            ACTION_SURFACE_LOST -> {
                Log.d(
                    TAG,
                    "Android Auto surface lost"
                )

                /*
                 * Android 14+ does not allow creating another
                 * VirtualDisplay from the same MediaProjection.
                 * Detach the current surface instead and keep the
                 * projection session alive.
                 */
                virtualDisplay?.setSurface(null)
                projectionSurface = null
            }

            ACTION_STOP -> {
                stopMirror()
                MirrorProjectionController
                    .markProjectionInactive()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startMirror(
        resultCode: Int,
        resultData: Intent
    ) {

        /*
         * ACTION_START represents a new user-approved capture session.
         * The previous MediaProjection must be fully stopped first.
         */
        stopMirror()

        val manager =
            getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        try {
            val projection = manager.getMediaProjection(
                resultCode,
                resultData
            )

            if (projection == null) {
                Log.e(
                    TAG,
                    "MediaProjection is null"
                )

                MirrorProjectionController
                    .markProjectionInactive()

                stopSelf()
                return
            }

            mediaProjection = projection

            projection.registerCallback(
                projectionCallback,
                null
            )

            Log.d(
                TAG,
                "MediaProjection created"
            )

            createVirtualDisplayIfPossible()

        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to start MediaProjection",
                exception
            )

            MirrorProjectionController
                .markProjectionInactive()

            stopMirror()
            stopSelf()
        }
    }

    private fun createVirtualDisplayIfPossible() {

        val projection = mediaProjection

        if (projection == null) {
            Log.d(
                TAG,
                "Projection is not ready"
            )
            return
        }

        val surface = MirrorSurfaceHolder.getSurface()

        if (surface == null) {
            Log.d(
                TAG,
                "Android Auto Surface is not available yet"
            )
            return
        }

        val width =
            MirrorSurfaceHolder.getWidth()
                .takeIf { it > 0 }
                ?: DEFAULT_WIDTH

        val height =
            MirrorSurfaceHolder.getHeight()
                .takeIf { it > 0 }
                ?: DEFAULT_HEIGHT

        val dpi =
            MirrorSurfaceHolder.getDpi()
                .takeIf { it > 0 }
                ?: DEFAULT_DPI

        Log.d(TAG, "====================================")
        Log.d(TAG, "UPDATING ANDROID AUTO SURFACE")
        Log.d(TAG, "width=$width")
        Log.d(TAG, "height=$height")
        Log.d(TAG, "dpi=$dpi")
        Log.d(TAG, "surface=$surface")
        Log.d(TAG, "====================================")

        try {
            val existingDisplay = virtualDisplay

            if (existingDisplay != null) {
                if (
                    projectionWidth != width ||
                    projectionHeight != height ||
                    projectionDpi != dpi
                ) {
                    existingDisplay.resize(
                        width,
                        height,
                        dpi
                    )

                    projectionWidth = width
                    projectionHeight = height
                    projectionDpi = dpi
                }

                existingDisplay.setSurface(surface)
                projectionSurface = surface

                Log.d(
                    TAG,
                    "EXISTING VIRTUAL DISPLAY REUSED"
                )
                return
            }

            projectionSurface = surface

            virtualDisplay =
                projection.createVirtualDisplay(
                    "KaradagCarTV",
                    width,
                    height,
                    dpi,
                    0,
                    surface,
                    null,
                    null
                )

            if (virtualDisplay == null) {
                Log.e(
                    TAG,
                    "VirtualDisplay creation failed"
                )

                projectionSurface = null
                return
            }

            projectionWidth = width
            projectionHeight = height
            projectionDpi = dpi

            Log.d(TAG, "====================================")
            Log.d(TAG, "VIRTUAL DISPLAY CREATED")
            Log.d(TAG, "Karadag Car TV mirror is active")
            Log.d(TAG, "====================================")

        } catch (exception: Exception) {
            Log.e(
                TAG,
                "VirtualDisplay update/creation error",
                exception
            )

            releaseVirtualDisplay()
        }
    }

    private val projectionCallback =
        object : MediaProjection.Callback() {

            override fun onStop() {
                Log.d(
                    TAG,
                    "MediaProjection stopped by system"
                )

                releaseVirtualDisplay()
                mediaProjection = null

                MirrorProjectionController
                    .markProjectionInactive()
            }
        }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.let {
            try {
                it.release()
            } catch (_: Exception) {
            }
        }

        virtualDisplay = null
        projectionSurface = null
        projectionWidth = 0
        projectionHeight = 0
        projectionDpi = 0
    }

    private fun stopMirror() {
        Log.d(
            TAG,
            "Stopping mirror"
        )

        releaseVirtualDisplay()

        mediaProjection?.let {
            try {
                it.unregisterCallback(
                    projectionCallback
                )
            } catch (_: Exception) {
            }

            try {
                it.stop()
            } catch (_: Exception) {
            }
        }

        mediaProjection = null
    }

    private fun createNotificationChannel() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Karadag Car TV",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                "Karadag Car TV araç ekranı yansıtması"
            setShowBadge(false)
        }

        val manager = getSystemService(
            NotificationManager::class.java
        )

        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle("Karadag Car TV")
                .setContentText(
                    "Araç ekranı yansıtması aktif"
                )
                .setSmallIcon(
                    android.R.drawable.ic_media_play
                )
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Karadag Car TV")
                .setContentText(
                    "Araç ekranı yansıtması aktif"
                )
                .setSmallIcon(
                    android.R.drawable.ic_media_play
                )
                .setOngoing(true)
                .build()
        }
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    override fun onDestroy() {
        Log.d(
            TAG,
            "MirrorProjectionService destroyed"
        )

        stopMirror()
        MirrorProjectionController
            .markProjectionInactive()

        super.onDestroy()
    }
}