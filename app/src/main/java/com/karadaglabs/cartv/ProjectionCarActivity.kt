package com.karadaglabs.cartv

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import com.google.android.apps.auto.sdk.CarActivity

class ProjectionCarActivity : CarActivity(), SurfaceHolder.Callback {

    private var surfaceView: SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = SurfaceView(this)

        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        view.holder.addCallback(this)

        surfaceView = view

        setContentView(view)
    }

    override fun onResume() {
        super.onResume()

        surfaceView?.holder?.let {
            if (it.surface.isValid) {
                ProjectionSurfaceHolder.setSurface(it.surface)
            }
        }
    }

    override fun onDestroy() {
        ProjectionSurfaceHolder.clear()
        surfaceView?.holder?.removeCallback(this)
        surfaceView = null

        super.onDestroy()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        ProjectionSurfaceHolder.setSurface(holder.surface)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        ProjectionSurfaceHolder.setSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        ProjectionSurfaceHolder.clear()
    }
}