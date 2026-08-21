package com.karadaglabs.cartv

import android.content.Intent
import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate

class CarMirrorService : CarAppService() {

    override fun createHostValidator():
            androidx.car.app.validation.HostValidator {

        return androidx.car.app.validation
            .HostValidator
            .ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return CarMirrorSession()
    }

    override fun onCreateSession(
        sessionInfo: SessionInfo
    ): Session {
        return CarMirrorSession()
    }
}

private class CarMirrorSession : Session() {

    override fun onCreateScreen(
        intent: Intent
    ): Screen {

        return CarMirrorScreen(
            carContext
        )
    }
}

private class CarMirrorScreen(
    carContext: CarContext
) : Screen(carContext), SurfaceCallback {

    companion object {
        private const val TAG =
            "KaradagCarMirror"
    }

    private val appManager: AppManager
        get() =
            carContext.getCarService(
                AppManager::class.java
            )

    init {
        appManager.setSurfaceCallback(this)
    }

    override fun onGetTemplate(): Template {

        return NavigationTemplate.Builder()

            .setActionStrip(
                ActionStrip.Builder()

                    .addAction(
                        Action.Builder()
                            .setTitle(
                                "Karadag Car TV"
                            )
                            .setOnClickListener {

                                Log.d(
                                    TAG,
                                    "Karadag Car TV clicked"
                                )
                            }
                            .build()
                    )

                    .build()
            )

            .setMapActionStrip(
                ActionStrip.Builder()

                    .addAction(
                        Action.Builder(
                            Action.PAN
                        ).build()
                    )

                    .build()
            )

            .build()
    }

    override fun onSurfaceAvailable(
        surfaceContainer: SurfaceContainer
    ) {

        Log.d(
            TAG,
            "======================================"
        )

        Log.d(
            TAG,
            "ANDROID AUTO SURFACE AVAILABLE"
        )

        Log.d(
            TAG,
            "width=${surfaceContainer.width}"
        )

        Log.d(
            TAG,
            "height=${surfaceContainer.height}"
        )

        Log.d(
            TAG,
            "dpi=${surfaceContainer.dpi}"
        )

        Log.d(
            TAG,
            "surface=${surfaceContainer.surface}"
        )

        Log.d(
            TAG,
            "======================================"
        )

        MirrorSurfaceHolder.setSurface(
            surfaceContainer
        )

        MirrorProjectionController.ensureProjection(
            carContext
        )
    }

    override fun onSurfaceDestroyed(
        surfaceContainer: SurfaceContainer
    ) {

        Log.d(
            TAG,
            "ANDROID AUTO SURFACE DESTROYED"
        )

        MirrorSurfaceHolder.clearSurface()

        MirrorProjectionController.surfaceLost(
            carContext
        )
    }

    override fun onVisibleAreaChanged(
        visibleArea: Rect
    ) {

        Log.d(
            TAG,
            "Visible area: $visibleArea"
        )
    }

    override fun onStableAreaChanged(
        stableArea: Rect
    ) {

        Log.d(
            TAG,
            "Stable area: $stableArea"
        )
    }

    override fun onClick(
        x: Float,
        y: Float
    ) {

        Log.d(
            TAG,
            "Surface click x=$x y=$y"
        )
    }

    override fun onScroll(
        distanceX: Float,
        distanceY: Float
    ) {

        Log.d(
            TAG,
            "Surface scroll dx=$distanceX dy=$distanceY"
        )
    }

    override fun onFling(
        velocityX: Float,
        velocityY: Float
    ) {

        Log.d(
            TAG,
            "Surface fling vx=$velocityX vy=$velocityY"
        )
    }

    override fun onScale(
        focusX: Float,
        focusY: Float,
        scale: Float
    ) {

        Log.d(
            TAG,
            "Surface scale=$scale"
        )
    }
}

object MirrorSurfaceHolder {

    @Volatile
    private var currentSurfaceContainer:
            SurfaceContainer? = null

    @Synchronized
    fun setSurface(
        surfaceContainer: SurfaceContainer
    ) {

        currentSurfaceContainer =
            surfaceContainer

        Log.d(
            "MirrorSurfaceHolder",
            "Surface stored: ${surfaceContainer.width}x${surfaceContainer.height}"
        )
    }

    @Synchronized
    fun clearSurface() {
        currentSurfaceContainer = null

        Log.d(
            "MirrorSurfaceHolder",
            "Surface cleared"
        )
    }

    fun getSurfaceContainer():
            SurfaceContainer? = currentSurfaceContainer

    fun getSurface():
            android.view.Surface? =
        currentSurfaceContainer?.surface

    fun getWidth(): Int =
        currentSurfaceContainer?.width ?: 0

    fun getHeight(): Int =
        currentSurfaceContainer?.height ?: 0

    fun getDpi(): Int =
        currentSurfaceContainer?.dpi ?: 0
}

object MirrorProjectionController {

    private const val TAG =
        "MirrorProjectionController"

    @Volatile
    private var projectionActive = false

    @Volatile
    private var projectionRequestInFlight = false

    fun ensureProjection(
        context: CarContext
    ) {

        if (projectionActive) {
            refresh(context)
            return
        }

        if (projectionRequestInFlight) {
            return
        }

        projectionRequestInFlight = true

        Log.d(
            TAG,
            "Requesting MediaProjection consent"
        )

        val intent = Intent(
            context,
            MediaProjectionPermissionActivity::class.java
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (exception: Exception) {
            projectionRequestInFlight = false

            Log.e(
                TAG,
                "Unable to open MediaProjection consent activity",
                exception
            )
        }
    }

    fun markProjectionActive() {
        projectionRequestInFlight = false
        projectionActive = true
    }

    fun markProjectionInactive() {
        projectionRequestInFlight = false
        projectionActive = false
    }

    fun refresh(
        context: CarContext
    ) {

        Log.d(
            TAG,
            "Surface ready - requesting projection refresh"
        )

        val intent =
            Intent(
                context,
                MirrorProjectionService::class.java
            ).apply {
                action =
                    MirrorProjectionService.ACTION_REFRESH
            }

        try {
            context.startService(intent)
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to refresh projection service",
                exception
            )
        }
    }

    fun surfaceLost(
        context: CarContext
    ) {

        Log.d(
            TAG,
            "Android Auto surface lost"
        )

        val intent =
            Intent(
                context,
                MirrorProjectionService::class.java
            ).apply {
                action =
                    MirrorProjectionService.ACTION_SURFACE_LOST
            }

        try {
            context.startService(intent)
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to notify projection service",
                exception
            )
        }
    }
}