package com.example.device_media_library

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.ProgressBar
import io.flutter.plugin.platform.PlatformView

class VideoPlayerPlatformView(
    private val context: Context,
    private val viewId: Int,
    private val args: Any?
) : PlatformView, SurfaceHolder.Callback {
    private val surfaceView: SurfaceView
    private val container: FrameLayout
    private var progressBar: ProgressBar
    private var isSurfaceReady = false

    init {
        container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        surfaceView = SurfaceView(context).apply {
            holder.addCallback(this@VideoPlayerPlatformView)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        progressBar = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.gravity = android.view.Gravity.CENTER
            }
            isIndeterminate = true
        }

        container.addView(surfaceView)
        container.addView(progressBar)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("VideoPlayerPlatformView", "Surface created for view $viewId")
        isSurfaceReady = true
        progressBar.visibility = android.view.View.GONE

        DeviceMediaLibraryPlugin.getInstance()
            ?.attachSurface(holder)
    }


    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("VideoPlayerPlatformView", "Surface changed: ${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("VideoPlayerPlatformView", "Surface destroyed for view $viewId")
        isSurfaceReady = false
        progressBar.visibility = android.view.View.VISIBLE

        DeviceMediaLibraryPlugin.getInstance()
            ?.attachSurface(null)
    }


    override fun getView(): android.view.View = container

    override fun dispose() {
        Log.d("VideoPlayerPlatformView", "Disposing view $viewId")
        surfaceView.holder.removeCallback(this)
        container.removeAllViews()
    }

    fun showLoading() {
        progressBar.visibility = android.view.View.VISIBLE
    }

    fun hideLoading() {
        progressBar.visibility = android.view.View.GONE
    }

    fun isSurfaceReady(): Boolean = isSurfaceReady
}