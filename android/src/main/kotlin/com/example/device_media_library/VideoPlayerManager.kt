package com.example.device_media_library

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File

class VideoPlayerManager(
    private val context: Context,
    private val videoPath: String?,
    private val videoUri: String?,
    private val eventCallback: VideoEventCallback
) {
    interface VideoEventCallback {
        fun onPlaybackUpdate(position: Double, duration: Double, isPlaying: Boolean)
        fun onBufferingUpdate(isBuffering: Boolean)
        fun onCompleted()
        fun onError(message: String)
    }

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var isReleased = false
    private val handler = Handler(Looper.getMainLooper())
    
    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            if (mediaPlayer != null && isPrepared && mediaPlayer!!.isPlaying) {
                try {
                    val currentPos = mediaPlayer!!.currentPosition
                    val duration = mediaPlayer!!.duration
                    
                    eventCallback.onPlaybackUpdate(
                        currentPos / 1000.0,
                        duration / 1000.0,
                        true
                    )
                } catch (e: IllegalStateException) {
                   
                }
            }
            if (!isReleased) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    init {
        initializeMediaPlayer()
    }

    private fun initializeMediaPlayer() {
        if (isReleased) return
        
        mediaPlayer = MediaPlayer()
        
        try {
            when {
                !videoPath.isNullOrEmpty() -> {
                    val file = File(videoPath)
                    if (file.exists()) {
                        mediaPlayer!!.setDataSource(file.absolutePath)
                    } else {
                        throw IllegalArgumentException("File does not exist: $videoPath")
                    }
                }
                !videoUri.isNullOrEmpty() -> {
                    mediaPlayer!!.setDataSource(context, Uri.parse(videoUri))
                }
                else -> throw IllegalArgumentException("No video source provided")
            }

          
            mediaPlayer!!.setOnPreparedListener {
                isPrepared = true
                eventCallback.onBufferingUpdate(false)
                
           
                handler.post(positionUpdateRunnable)
                
               
                val duration = it.duration
                eventCallback.onPlaybackUpdate(0.0, duration / 1000.0, false)
            }

            mediaPlayer!!.setOnBufferingUpdateListener { _, percent ->
                eventCallback.onBufferingUpdate(percent < 100)
            }

            mediaPlayer!!.setOnCompletionListener {
                eventCallback.onCompleted()
                handler.removeCallbacks(positionUpdateRunnable)
            }

            mediaPlayer!!.setOnErrorListener { _, what, extra ->
                val errorMsg = "MediaPlayer error: $what, $extra"
                eventCallback.onError(errorMsg)
                handler.removeCallbacks(positionUpdateRunnable)
                true
            }

           
            mediaPlayer!!.prepareAsync()
            eventCallback.onBufferingUpdate(true)
            
        } catch (e: Exception) {
            eventCallback.onError("Initialization error: ${e.message}")
            release()
        }
    }

    fun setDisplay(holder: android.view.SurfaceHolder?) {
        if (mediaPlayer != null && !isReleased) {
            mediaPlayer!!.setDisplay(holder)
        }
    }

    fun play() {
        if (mediaPlayer != null && isPrepared && !isReleased) {
            try {
                mediaPlayer!!.start()
            } catch (e: IllegalStateException) {
                eventCallback.onError("Cannot play: ${e.message}")
            }
        }
    }

    fun pause() {
        if (mediaPlayer != null && isPrepared && !isReleased) {
            try {
                mediaPlayer!!.pause()
            } catch (e: IllegalStateException) {
              
            }
        }
    }

    fun seekTo(position: Float) {
        if (mediaPlayer != null && isPrepared && !isReleased) {
            try {
                val posMillis = (position * 1000).toInt()
                mediaPlayer!!.seekTo(posMillis)
            } catch (e: IllegalStateException) {
                eventCallback.onError("Cannot seek: ${e.message}")
            }
        }
    }

    fun setVolume(volume: Float) {
        if (mediaPlayer != null && !isReleased) {
            try {
                mediaPlayer!!.setVolume(volume, volume)
            } catch (e: IllegalStateException) {
               
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mediaPlayer != null && !isReleased) {
                try {
                    val params = mediaPlayer!!.playbackParams ?: PlaybackParams()
                    params.speed = speed
                    mediaPlayer!!.playbackParams = params
                } catch (e: IllegalStateException) {
                    
                }
            }
        }
    }

    fun getDuration(): Float {
        if (mediaPlayer != null && isPrepared && !isReleased) {
            try {
                return mediaPlayer!!.duration / 1000.0f
            } catch (e: IllegalStateException) {
                return 0f
            }
        }
        return 0f
    }

    fun release() {
        isReleased = true
        isPrepared = false
        
        handler.removeCallbacks(positionUpdateRunnable)
        
        mediaPlayer?.let { player ->
            try {
                player.stop()
                player.release()
            } catch (e: IllegalStateException) {
                
            }
        }
        mediaPlayer = null
    }
}