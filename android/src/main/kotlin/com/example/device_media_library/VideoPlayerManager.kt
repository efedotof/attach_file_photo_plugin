package com.example.device_media_library

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class VideoPlayerManager(
    private val context: Context,
    private val videoPath: String?,
    private val videoUri: String?,
    private val callback: VideoEventCallback
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
    private val prepared = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    private val positionUpdater = object : Runnable {
        override fun run() {
            if (prepared.get() && !released.get()) {
                try {
                    val mp = mediaPlayer ?: return
                    val pos = mp.currentPosition / 1000.0
                    callback.onPlaybackUpdate(pos, mp.isPlaying)
                } catch (_: Exception) {
                }
                handler.postDelayed(this, 300)
            }
        }
    }

    init {
        initPlayer()
    }

    private fun initPlayer() {
        try {
            val uri = resolveUri()
            Log.d("VideoPlayerManager", "Using uri=$uri")

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)

                setOnPreparedListener {
                    prepared.set(true)
                    val durationSec = it.duration / 1000.0
                    Log.d("VideoPlayerManager", "Prepared, duration=$durationSec")
                    callback.onPrepared(durationSec)
                    callback.onBufferingUpdate(false)
                    handler.post(positionUpdater)
                }

                setOnVideoSizeChangedListener { _, w, h ->
                    callback.onVideoSizeChanged(w, h)
                }

                setOnCompletionListener {
                    callback.onCompleted()
                }

                setOnErrorListener { _, what, extra ->
                    val msg = "MediaPlayer error what=$what extra=$extra"
                    Log.e("VideoPlayerManager", msg)
                    callback.onError(msg)
                    true
                }

                prepareAsync()
                callback.onBufferingUpdate(true)
            }

        } catch (e: Exception) {
            Log.e("VideoPlayerManager", "Init failed", e)
            callback.onError(e.message ?: "Init failed")
        }
    }

    private fun resolveUri(): Uri {
        videoUri?.let {
            return Uri.parse(it)
        }
        videoPath?.let {
            return Uri.parse(it)
        }
        throw IllegalArgumentException("Video URI is null")
    }

    fun setDisplay(holder: android.view.SurfaceHolder?) {
        mediaPlayer?.setDisplay(holder)
    }

    fun play() {
        if (prepared.get()) mediaPlayer?.start()
    }

    fun pause() {
        if (prepared.get()) mediaPlayer?.pause()
    }

    fun seekTo(seconds: Float) {
        if (prepared.get()) mediaPlayer?.seekTo((seconds * 1000).toInt())
    }

    fun getDuration(): Double =
        if (prepared.get()) mediaPlayer?.duration?.div(1000.0) ?: 0.0 else 0.0

    fun getCurrentPosition(): Double =
        if (prepared.get()) mediaPlayer?.currentPosition?.div(1000.0) ?: 0.0 else 0.0

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun setVolume(v: Float) {
        mediaPlayer?.setVolume(v, v)
    }

    fun release() {
        if (released.getAndSet(true)) return
        handler.removeCallbacks(positionUpdater)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
