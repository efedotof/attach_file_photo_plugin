package com.example.device_media_library

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class DeviceMediaLibraryPlugin : FlutterPlugin, ActivityAware {

    private lateinit var context: Context

    private lateinit var mediaChannel: MethodChannel
    private lateinit var videoMethodChannel: MethodChannel
    private lateinit var videoEventChannel: EventChannel

    private var activityBinding: ActivityPluginBinding? = null
    private var videoEventSink: EventChannel.EventSink? = null

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var mediaQueryManager: MediaQueryManager
    private lateinit var thumbnailManager: ThumbnailManager
    private lateinit var fileReader: FileReader

    private var permissionManager: PermissionManager? = null
    private var videoPlayerManager: VideoPlayerManager? = null

    companion object {
        @Volatile
        private var instance: DeviceMediaLibraryPlugin? = null
        fun getInstance(): DeviceMediaLibraryPlugin? = instance
    }


    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        instance = this
        context = binding.applicationContext

        permissionManager = PermissionManager(context)

        mediaQueryManager = MediaQueryManager(context, executor, mainHandler)
        thumbnailManager = ThumbnailManager(context, executor, mainHandler)
        fileReader = FileReader(context, executor, mainHandler)

        mediaChannel = MethodChannel(binding.binaryMessenger, "device_media_library")
        videoMethodChannel = MethodChannel(binding.binaryMessenger, "device_media_library/video")
        videoEventChannel = EventChannel(binding.binaryMessenger, "device_media_library/video/events")

        mediaChannel.setMethodCallHandler(MediaMethodCallHandler())
        videoMethodChannel.setMethodCallHandler(VideoMethodCallHandler())

        videoEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                videoEventSink = events
            }

            override fun onCancel(arguments: Any?) {
                videoEventSink = null
            }
        })

        binding.platformViewRegistry.registerViewFactory(
            "device_media_library/video/view",
            VideoPlayerViewFactory()
        )
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        instance = null
        executor.shutdownNow()

        videoPlayerManager?.release()
        videoPlayerManager = null
        permissionManager = null
    }


    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        permissionManager?.setActivityBinding(binding)
    }

    override fun onDetachedFromActivity() {
        activityBinding = null
        permissionManager?.setActivityBinding(null)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
        permissionManager?.setActivityBinding(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding = null
        permissionManager?.setActivityBinding(null)
    }


    private fun sendVideoEvent(event: Map<String, Any>) {
        mainHandler.post { videoEventSink?.success(event) }
    }

    inner class MediaMethodCallHandler : MethodChannel.MethodCallHandler {
        override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
            when (call.method) {

                "checkPermission" ->
                    permissionManager?.checkPermission(result)
                        ?: result.error("NO_ACTIVITY", "Plugin not attached", null)

                "requestPermission" -> {
                    val activity = activityBinding?.activity
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity is null", null)
                        return
                    }
                    permissionManager?.requestPermission(activity, result)
                }

                "getImages" ->
                    mediaQueryManager.getMedia(call, result, MediaType.IMAGE)

                "getVideos" ->
                    mediaQueryManager.getMedia(call, result, MediaType.VIDEO)

                "getAllMedia" ->
                    mediaQueryManager.getAllMedia(call, result)

                "getAlbums" ->
                    mediaQueryManager.getAlbums(result)

                "getMediaFromAlbum" ->
                    mediaQueryManager.getMediaFromAlbum(call, result)

                "getThumbnail" ->
                    thumbnailManager.getThumbnail(call, result)

                "readFileByUri" ->
                    fileReader.readFileByUri(call, result)

                else -> result.notImplemented()
            }
        }
    }

    inner class VideoMethodCallHandler : MethodChannel.MethodCallHandler {
        override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
            when (call.method) {

                "initialize" -> {
                    val path = call.argument<String>("videoPath")
                    val uri = call.argument<String>("videoUri")
                    val volume = call.argument<Double>("volume")?.toFloat() ?: 1f

                    videoPlayerManager?.release()
                    videoPlayerManager = VideoPlayerManager(
                        context,
                        path,
                        uri,
                        object : VideoPlayerManager.VideoEventCallback {

                            override fun onPrepared(duration: Double) {
                                sendVideoEvent(mapOf("event" to "onPrepared", "duration" to duration))
                            }

                            override fun onPlaybackUpdate(position: Double, isPlaying: Boolean) {
                                sendVideoEvent(
                                    mapOf(
                                        "event" to "onPlaybackUpdate",
                                        "position" to position,
                                        "isPlaying" to isPlaying
                                    )
                                )
                            }

                            override fun onBufferingUpdate(isBuffering: Boolean) {
                                sendVideoEvent(mapOf("event" to "onBufferingUpdate", "isBuffering" to isBuffering))
                            }

                            override fun onCompleted() {
                                sendVideoEvent(mapOf("event" to "onCompleted"))
                            }

                            override fun onError(message: String) {
                                sendVideoEvent(mapOf("event" to "onError", "message" to message))
                            }

                            override fun onVideoSizeChanged(width: Int, height: Int) {
                                sendVideoEvent(
                                    mapOf(
                                        "event" to "onVideoSizeChanged",
                                        "width" to width,
                                        "height" to height
                                    )
                                )
                            }
                        }
                    )

                    videoPlayerManager?.setVolume(volume)
                    result.success(true)
                }

                "play" -> {
                    videoPlayerManager?.play()
                    result.success(null)
                }

                "pause" -> {
                    videoPlayerManager?.pause()
                    result.success(null)
                }

                "seekTo" -> {
                    call.argument<Double>("position")?.toFloat()?.let {
                        videoPlayerManager?.seekTo(it)
                    }
                    result.success(null)
                }

                "dispose" -> {
                    videoPlayerManager?.release()
                    videoPlayerManager = null
                    result.success(null)
                }

                else -> result.notImplemented()
            }
        }
    }

    fun attachSurface(holder: android.view.SurfaceHolder?) {
        videoPlayerManager?.setDisplay(holder)
    }


}
