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
) : PlatformView, SurfaceHolder.Callback {
    private val surfaceView: SurfaceView
    private val container: FrameLayout
    private var videoPlayerManager: VideoPlayerManager? = null

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
        }

        container.addView(surfaceView)
        
      
        initializeVideoPlayer()
    }

    private fun initializeVideoPlayer() {
        val argsMap = args as? Map<*, *> ?: return
        
        val videoPath = argsMap["videoPath"] as? String
        val videoUri = argsMap["videoUri"] as? String
        val autoPlay = argsMap["autoPlay"] as? Boolean ?: false
        val volume = (argsMap["volume"] as? Double)?.toFloat() ?: 1.0f

        videoPlayerManager = VideoPlayerManager(
            context,
            videoPath,
            videoUri,
            object : VideoPlayerManager.VideoEventCallback {
                override fun onPlaybackUpdate(position: Double, duration: Double, isPlaying: Boolean) {
                   
                }
                
                override fun onBufferingUpdate(isBuffering: Boolean) {
                   
                }
                
                override fun onCompleted() {
                 
                }
                
                override fun onError(message: String) {
                    
                }
            }
        )
        
       
        videoPlayerManager?.setDisplay(surfaceView.holder)
        
        if (autoPlay) {
            videoPlayerManager?.play()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        videoPlayerManager?.setDisplay(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        videoPlayerManager?.setDisplay(null)
    }

    override fun getView(): android.view.View = container

    override fun dispose() {
        videoPlayerManager?.release()
        container.removeAllViews()
    }
}