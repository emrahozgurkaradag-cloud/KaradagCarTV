package com.karadaglabs.cartv

import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.Surface

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
        return CarMirrorScreen(carContext)
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
        get() = carContext.getCarService(
            AppManager::class.java
        )

    init {

        Log.d(
            TAG,
            "CarMirrorScreen created"
        )

        appManager.setSurfaceCallback(this)
    }

    override fun onGetTemplate(): Template {

        Log.d(
            TAG,
            "onGetTemplate"
        )

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
                                    "Karadag Car TV selected"
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

        /*
         * Android Auto tarafından verilen
         * Surface'i ortak holder'a kaydet.
         */
        MirrorSurfaceHolder.setSurface(
            surfaceContainer
        )

        /*
         * Projection servisine yeni Surface'in
         * hazır olduğunu bildir.
         */
        val refreshIntent =
            Intent(
                carContext,
                MirrorProjectionService::class.java
            ).apply {

                action =
                    MirrorProjectionService.ACTION_REFRESH
            }

        try {

            carContext.startService(
                refreshIntent
            )

            Log.d(
                TAG,
                "Projection refresh sent"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to refresh projection",
                exception
            )
        }
    }

    override fun onSurfaceDestroyed(
        surfaceContainer: SurfaceContainer
    ) {

        Log.d(
            TAG,
            "======================================"
        )

        Log.d(
            TAG,
            "ANDROID AUTO SURFACE DESTROYED"
        )

        Log.d(
            TAG,
            "======================================"
        )

        MirrorSurfaceHolder.clearSurface()

        val stopIntent =
            Intent(
                carContext,
                MirrorProjectionService::class.java
            ).apply {

                action =
                    MirrorProjectionService.ACTION_SURFACE_LOST
            }

        try {

            carContext.startService(
                stopIntent
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to notify surface lost",
                exception
            )
        }
    }

    override fun onVisibleAreaChanged(
        visibleArea: Rect
    ) {

        Log.d(
            TAG,
            "Visible area=$visibleArea"
        )
    }

    override fun onStableAreaChanged(
        stableArea: Rect
    ) {

        Log.d(
            TAG,
            "Stable area=$stableArea"
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


/**
 * Android Auto tarafından sağlanan SurfaceContainer'ı
 * uygulamanın diğer bileşenlerine aktarmak için ortak holder.
 */
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
            "Surface stored: " +
                    "${surfaceContainer.width}x" +
                    "${surfaceContainer.height}"
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
            SurfaceContainer? {

        return currentSurfaceContainer
    }

    fun getSurface(): Surface? {

        return currentSurfaceContainer?.surface
    }

    fun getWidth(): Int {

        return currentSurfaceContainer?.width ?: 0
    }

    fun getHeight(): Int {

        return currentSurfaceContainer?.height ?: 0
    }

    fun getDpi(): Int {

        return currentSurfaceContainer?.dpi ?: 0
    }
}