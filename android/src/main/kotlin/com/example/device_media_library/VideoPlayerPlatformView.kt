package com.example.device_media_library

import android.content.Context
import android.graphics.Color
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import io.flutter.plugin.platform.PlatformView

class VideoPlayerPlatformView(
    private val context: Context,
    private val viewId: Int,
    private val args: Any?
) : PlatformView {
    private val surfaceView: SurfaceView
    private val container: FrameLayout

    init {
        container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        surfaceView = SurfaceView(context).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                 
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {

                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {

                }
            })
        }

        container.addView(surfaceView)
    }

    override fun getView(): android.view.View = container

    override fun dispose() {
        container.removeAllViews()
    }
}