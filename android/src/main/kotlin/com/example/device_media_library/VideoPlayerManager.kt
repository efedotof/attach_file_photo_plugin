package com.example.device_media_library

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class VideoPlayerManager(
    private val context: Context,
    private val videoPath: String?,
    private val videoUri: String?,
    private val eventCallback: VideoEventCallback
) {
    interface VideoEventCallback {
        fun onPrepared(duration: Double)
        fun onPlaybackUpdate(position: Double, isPlaying: Boolean)
        fun onBufferingUpdate(isBuffering: Boolean)
        fun onCompleted()
        fun onError(message: String)
        fun onVideoSizeChanged(width: Int, height: Int)
    }

    private var mediaPlayer: MediaPlayer? = null
    private val isPrepared = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    
    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            if (isPrepared.get() && !isReleased.get()) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            val currentPos = player.currentPosition
                            eventCallback.onPlaybackUpdate(
                                currentPos / 1000.0, 
                                true
                            )
                        }
                    } catch (e: IllegalStateException) {
                        // Игнорируем
                    }
                }
            }
            if (!isReleased.get()) {
                handler.postDelayed(this, 200)
            }
        }
    }

    init {
        initializeMediaPlayer()
    }

    private fun initializeMediaPlayer() {
        if (isReleased.get()) return
        
        try {
            mediaPlayer = MediaPlayer().apply {
                val uri = getVideoUri()
                Log.d("VideoPlayerManager", "Setting data source from URI: $uri")
                setDataSource(context, uri)

                setOnPreparedListener {
                    Log.d("VideoPlayerManager", "MediaPlayer prepared, duration: ${it.duration}")
                    isPrepared.set(true)
                    eventCallback.onPrepared(it.duration / 1000.0)
                    eventCallback.onBufferingUpdate(false)
                    
                    // Запускаем обновление позиции
                    handler.post(positionUpdateRunnable)
                }

                setOnBufferingUpdateListener { _, percent ->
                    Log.d("VideoPlayerManager", "Buffering: $percent%")
                    eventCallback.onBufferingUpdate(percent < 100)
                }

                setOnCompletionListener {
                    Log.d("VideoPlayerManager", "Playback completed")
                    eventCallback.onCompleted()
                }

                setOnErrorListener { _, what, extra ->
                    val errorMsg = "MediaPlayer error: what=$what, extra=$extra"
                    Log.e("VideoPlayerManager", errorMsg)
                    eventCallback.onError(errorMsg)
                    true
                }

                setOnInfoListener { _, what, extra ->
                    Log.d("VideoPlayerManager", "MediaPlayer info: what=$what, extra=$extra")
                    true
                }

                setOnVideoSizeChangedListener { _, width, height ->
                    Log.d("VideoPlayerManager", "Video size changed: ${width}x$height")
                    eventCallback.onVideoSizeChanged(width, height)
                }

                // Включаем экран во время воспроизведения
                setScreenOnWhilePlaying(true)
                setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)

                // Начинаем асинхронную подготовку
                Log.d("VideoPlayerManager", "Starting async preparation")
                prepareAsync()
                eventCallback.onBufferingUpdate(true)
            }
        } catch (e: Exception) {
            Log.e("VideoPlayerManager", "Initialization error: ${e.message}", e)
            eventCallback.onError("Initialization error: ${e.message}")
            release()
        }
    }

    private fun getVideoUri(): Uri {
        // Проверяем videoUri как content URI
        if (!videoUri.isNullOrEmpty() && videoUri!!.startsWith("content://")) {
            return Uri.parse(videoUri)
        }
        
        // Проверяем videoPath как content URI
        if (!videoPath.isNullOrEmpty() && videoPath!!.startsWith("content://")) {
            return Uri.parse(videoPath)
        }
        
        // Проверяем videoPath как файловый путь
        if (!videoPath.isNullOrEmpty() && File(videoPath).exists()) {
            return Uri.fromFile(File(videoPath))
        }
        
        // Проверяем videoUri как файловый путь
        if (!videoUri.isNullOrEmpty() && File(videoUri).exists()) {
            return Uri.fromFile(File(videoUri))
        }
        
        throw IllegalArgumentException("No valid video source provided. Path: $videoPath, URI: $videoUri")
    }

    fun setDisplay(holder: android.view.SurfaceHolder?) {
        if (!isReleased.get()) {
            try {
                mediaPlayer?.setDisplay(holder)
                if (holder != null) {
                    Log.d("VideoPlayerManager", "SurfaceHolder set successfully")
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerManager", "Error setting display: ${e.message}")
            }
        }
    }

    fun play() {
        if (isPrepared.get() && !isReleased.get()) {
            try {
                mediaPlayer?.start()
                Log.d("VideoPlayerManager", "Play called")
                eventCallback.onBufferingUpdate(false)
            } catch (e: IllegalStateException) {
                Log.e("VideoPlayerManager", "Cannot play: ${e.message}")
                eventCallback.onError("Cannot play: ${e.message}")
            }
        } else {
            Log.w("VideoPlayerManager", "Cannot play: not prepared or released")
        }
    }

    fun pause() {
        if (isPrepared.get() && !isReleased.get()) {
            try {
                mediaPlayer?.pause()
                Log.d("VideoPlayerManager", "Pause called")
            } catch (e: IllegalStateException) {
                Log.e("VideoPlayerManager", "Cannot pause: ${e.message}")
            }
        }
    }

    fun seekTo(position: Float) {
        if (isPrepared.get() && !isReleased.get()) {
            try {
                val posMillis = (position * 1000).toInt()
                mediaPlayer?.seekTo(posMillis)
                Log.d("VideoPlayerManager", "Seek to: $posMillis ms")
            } catch (e: IllegalStateException) {
                Log.e("VideoPlayerManager", "Cannot seek: ${e.message}")
                eventCallback.onError("Cannot seek: ${e.message}")
            }
        } else {
            Log.w("VideoPlayerManager", "Cannot seek: not prepared or released")
        }
    }

    fun getCurrentPosition(): Double {
        return if (isPrepared.get() && !isReleased.get()) {
            try {
                mediaPlayer?.currentPosition?.div(1000.0) ?: 0.0
            } catch (e: IllegalStateException) {
                0.0
            }
        } else {
            0.0
        }
    }

    fun getDuration(): Double {
        return if (isPrepared.get() && !isReleased.get()) {
            try {
                mediaPlayer?.duration?.div(1000.0) ?: 0.0
            } catch (e: IllegalStateException) {
                0.0
            }
        } else {
            0.0
        }
    }

    fun isPlaying(): Boolean {
        return if (isPrepared.get() && !isReleased.get()) {
            try {
                mediaPlayer?.isPlaying ?: false
            } catch (e: IllegalStateException) {
                false
            }
        } else {
            false
        }
    }

    fun setVolume(volume: Float) {
        if (!isReleased.get()) {
            try {
                mediaPlayer?.setVolume(volume, volume)
                Log.d("VideoPlayerManager", "Volume set to: $volume")
            } catch (e: IllegalStateException) {
                Log.e("VideoPlayerManager", "Cannot set volume: ${e.message}")
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isReleased.get()) {
                try {
                    val params = mediaPlayer?.playbackParams ?: PlaybackParams()
                    params.speed = speed
                    mediaPlayer?.playbackParams = params
                    Log.d("VideoPlayerManager", "Playback speed set to: $speed")
                } catch (e: IllegalStateException) {
                    Log.e("VideoPlayerManager", "Cannot set playback speed: ${e.message}")
                }
            }
        }
    }

    fun release() {
        if (isReleased.getAndSet(true)) return
        
        Log.d("VideoPlayerManager", "Releasing MediaPlayer")
        
        handler.removeCallbacks(positionUpdateRunnable)
        isPrepared.set(false)
        
        mediaPlayer?.let { player ->
            try {
                player.stop()
                player.release()
            } catch (e: IllegalStateException) {
                Log.e("VideoPlayerManager", "Error releasing MediaPlayer: ${e.message}")
            }
        }
        mediaPlayer = null
    }

    fun reset() {
        if (isReleased.get()) return
        
        Log.d("VideoPlayerManager", "Resetting MediaPlayer")
        
        handler.removeCallbacks(positionUpdateRunnable)
        isPrepared.set(false)
        
        mediaPlayer?.let { player ->
            try {
                player.reset()
                // Переинициализируем
                initializeMediaPlayer()
            } catch (e: IllegalStateException) {
                Log.e("VideoPlayerManager", "Error resetting MediaPlayer: ${e.message}")
                release()
            }
        }
    }
}