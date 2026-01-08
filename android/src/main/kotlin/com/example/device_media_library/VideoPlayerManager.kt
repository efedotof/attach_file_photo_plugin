package com.example.device_media_library

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
                            eventCallback.onPlaybackUpdate(currentPos / 1000.0, true)
                        }
                    } catch (e: IllegalStateException) {
                        // Игнорируем ошибки при обновлении позиции
                    }
                }
            }
            if (!isReleased.get()) {
                handler.postDelayed(this, 500)
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
                // Устанавливаем источник видео
                when {
                    !videoPath.isNullOrEmpty() -> {
                        val file = File(videoPath)
                        if (file.exists()) {
                            setDataSource(file.absolutePath)
                        } else {
                            throw IllegalArgumentException("File does not exist: $videoPath")
                        }
                    }
                    !videoUri.isNullOrEmpty() -> {
                        val uri = Uri.parse(videoUri)
                        setDataSource(context, uri)
                    }
                    else -> throw IllegalArgumentException("No video source provided")
                }

                // Настройка слушателей
                setOnPreparedListener {
                    isPrepared.set(true)
                    eventCallback.onPrepared(it.duration / 1000.0)
                    eventCallback.onBufferingUpdate(false)
                    
                    // Запускаем обновление позиции
                    handler.post(positionUpdateRunnable)
                }

                setOnBufferingUpdateListener { _, percent ->
                    eventCallback.onBufferingUpdate(percent < 100)
                }

                setOnCompletionListener {
                    eventCallback.onCompleted()
                }

                setOnErrorListener { _, what, extra ->
                    val errorMsg = "MediaPlayer error: $what, $extra"
                    eventCallback.onError(errorMsg)
                    true
                }

                // Подготавливаем асинхронно
                prepareAsync()
                eventCallback.onBufferingUpdate(true)
            }
        } catch (e: Exception) {
            eventCallback.onError("Initialization error: ${e.message}")
            release()
        }
    }

    fun setDisplay(holder: android.view.SurfaceHolder?) {
        if (!isReleased.get()) {
            mediaPlayer?.setDisplay(holder)
        }
    }

    fun play() {
        if (isPrepared.get() && !isReleased.get()) {
            try {
                mediaPlayer?.start()
                eventCallback.onBufferingUpdate(false)
            } catch (e: IllegalStateException) {
                eventCallback.onError("Cannot play: ${e.message}")
            }
        }
    }

    fun pause() {
        if (isPrepared.get() && !isReleased.get()) {
            try {
                mediaPlayer?.pause()
            } catch (e: IllegalStateException) {
                // Игнорируем
            }
        }
    }

    fun seekTo(position: Float) {
        if (isPrepared.get() && !isReleased.get()) {
            try {
                val posMillis = (position * 1000).toInt()
                mediaPlayer?.seekTo(posMillis)
            } catch (e: IllegalStateException) {
                eventCallback.onError("Cannot seek: ${e.message}")
            }
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
            } catch (e: IllegalStateException) {
                // Игнорируем
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
                } catch (e: IllegalStateException) {
                    // Игнорируем
                }
            }
        }
    }

    fun release() {
        if (isReleased.getAndSet(true)) return
        
        handler.removeCallbacks(positionUpdateRunnable)
        isPrepared.set(false)
        
        mediaPlayer?.let { player ->
            try {
                player.stop()
                player.release()
            } catch (e: IllegalStateException) {
                // Игнорируем
            }
        }
        mediaPlayer = null
    }
}