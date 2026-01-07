package com.example.device_media_library

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import java.io.File

class VideoPlayerManager(
    private val context: Context,
    private val videoPath: String?,
    private val videoUri: String?,
    private val sendEvent: (Map<String, Any>) -> Unit
) {
    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    sendEvent(
                        mapOf(
                            "event" to "onPlaybackUpdate",
                            "position" to player.currentPosition / 1000.0,
                            "duration" to player.duration / 1000.0,
                            "isPlaying" to true
                        )
                    )
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    init {
        initializeMediaPlayer()
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            try {
                if (videoPath != null) {
                    val file = File(videoPath)
                    if (file.exists()) {
                        setDataSource(file.absolutePath)
                    } else {
                        throw IllegalArgumentException("File does not exist: $videoPath")
                    }
                } else if (videoUri != null) {
                    setDataSource(context, Uri.parse(videoUri))
                }

                setOnPreparedListener {
                    isPrepared = true
                    sendEvent(
                        mapOf(
                            "event" to "onBufferingUpdate",
                            "isBuffering" to false
                        )
                    )
                    handler.post(updateRunnable)
                }

                setOnBufferingUpdateListener { _, percent ->
                    sendEvent(
                        mapOf(
                            "event" to "onBufferingUpdate",
                            "isBuffering" to percent < 100
                        )
                    )
                }

                setOnCompletionListener {
                    sendEvent(mapOf("event" to "onCompleted"))
                    handler.removeCallbacks(updateRunnable)
                }

                setOnErrorListener { _, what, extra ->
                    sendEvent(
                        mapOf(
                            "event" to "onError",
                            "message" to "MediaPlayer error: $what, $extra"
                        )
                    )
                    true
                }

                prepareAsync()
                sendEvent(
                    mapOf(
                        "event" to "onBufferingUpdate",
                        "isBuffering" to true
                    )
                )
            } catch (e: Exception) {
                sendEvent(
                    mapOf(
                        "event" to "onError",
                        "message" to e.message ?: "Unknown error"
                    )
                )
            }
        }
    }

    fun setDisplay(holder: SurfaceHolder?) {
        mediaPlayer?.setDisplay(holder)
    }

    fun play() {
        if (isPrepared) {
            mediaPlayer?.start()
            handler.post(updateRunnable)
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        handler.removeCallbacks(updateRunnable)
    }

    fun seekTo(position: Float) {
        mediaPlayer?.seekTo((position * 1000).toInt())
    }

    fun setVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { player ->
                val params = player.playbackParams ?: PlaybackParams()
                player.playbackParams = params.setSpeed(speed)
            }
        }
    }

    fun getDuration(): Float {
        return mediaPlayer?.duration?.toFloat()?.div(1000) ?: 0f
    }

    fun release() {
        handler.removeCallbacks(updateRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        isPrepared = false
    }
}