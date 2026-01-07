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
import io.flutter.plugin.common.MethodChannel.Result
import java.util.concurrent.Executors

class DeviceMediaLibraryPlugin : FlutterPlugin, ActivityAware {
    private lateinit var mediaChannel: MethodChannel
    private lateinit var videoMethodChannel: MethodChannel
    private lateinit var videoEventChannel: EventChannel
    private lateinit var context: Context
    private var activityBinding: ActivityPluginBinding? = null
    
    private lateinit var permissionManager: PermissionManager
    private lateinit var mediaQueryManager: MediaQueryManager
    private lateinit var thumbnailManager: ThumbnailManager
    private lateinit var fileReader: FileReader
    private var videoPlayerManager: VideoPlayerManager? = null
    
    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var videoEventSink: EventChannel.EventSink? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        
        mediaChannel = MethodChannel(binding.binaryMessenger, "device_media_library")
        mediaChannel.setMethodCallHandler(MediaMethodCallHandler())
        
        videoMethodChannel = MethodChannel(binding.binaryMessenger, "device_media_library/video")
        videoMethodChannel.setMethodCallHandler(VideoMethodCallHandler())
        
        videoEventChannel = EventChannel(binding.binaryMessenger, "device_media_library/video/events")
        videoEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                videoEventSink = events
            }
            
            override fun onCancel(arguments: Any?) {
                videoEventSink = null
            }
        })
        
        permissionManager = PermissionManager(context)
        mediaQueryManager = MediaQueryManager(context, executor, mainHandler)
        thumbnailManager = ThumbnailManager(context, executor, mainHandler)
        fileReader = FileReader(context, executor, mainHandler)
        
        binding.platformViewRegistry.registerViewFactory(
            "device_media_library/video/view",
            VideoPlayerViewFactory()
        )
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        permissionManager.setActivityBinding(binding)
    }

    override fun onDetachedFromActivity() {
        activityBinding = null
        permissionManager.setActivityBinding(null)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
        permissionManager.setActivityBinding(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding = null
        permissionManager.setActivityBinding(null)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        mediaChannel.setMethodCallHandler(null)
        videoMethodChannel.setMethodCallHandler(null)
        videoEventChannel.setStreamHandler(null)
        executor.shutdownNow()
        videoPlayerManager?.release()
        videoPlayerManager = null
    }
    
    private fun sendVideoEvent(event: Map<String, Any>) {
        mainHandler.post {
            videoEventSink?.success(event)
        }
    }

    inner class MediaMethodCallHandler : MethodChannel.MethodCallHandler {
        override fun onMethodCall(call: MethodCall, result: Result) {
            when (call.method) {
                "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")
                "checkPermission" -> permissionManager.checkPermission(result)
                "requestPermission" -> {
                    val activity = activityBinding?.activity
                    if (activity != null) permissionManager.requestPermission(activity, result)
                    else result.error("NO_ACTIVITY", "No activity available", null)
                }
                "getImages" -> {
                    if (permissionManager.hasPermission()) {
                        mediaQueryManager.getMedia(call, result, MediaType.IMAGE)
                    } else result.error("PERMISSION_DENIED", "Read permission not granted", null)
                }
                "getVideos" -> {
                    if (permissionManager.hasPermission()) {
                        mediaQueryManager.getMedia(call, result, MediaType.VIDEO)
                    } else result.error("PERMISSION_DENIED", "Read permission not granted", null)
                }
                "getAllMedia" -> {
                    if (permissionManager.hasPermission()) {
                        mediaQueryManager.getAllMedia(call, result)
                    } else result.error("PERMISSION_DENIED", "Read permission not granted", null)
                }
                "getAlbums" -> {
                    if (permissionManager.hasPermission()) {
                        mediaQueryManager.getAlbums(result)
                    } else result.error("PERMISSION_DENIED", "Read permission not granted", null)
                }
                "getMediaFromAlbum" -> {
                    if (permissionManager.hasPermission()) {
                        mediaQueryManager.getMediaFromAlbum(call, result)
                    } else result.error("PERMISSION_DENIED", "Read permission not granted", null)
                }
                "getThumbnail" -> {
                    if (permissionManager.hasPermission()) {
                        thumbnailManager.getThumbnail(call, result)
                    } else result.error("PERMISSION_DENIED", "Read permission not granted", null)
                }
                "readFileByUri" -> {
                    if (permissionManager.hasPermission()) {
                        fileReader.readFileByUri(call, result)
                    } else result.error("PERMISSION_DENIED", "Read permission not granted", null)
                }
                else -> result.notImplemented()
            }
        }
    }
    
    inner class VideoMethodCallHandler : MethodChannel.MethodCallHandler {
        override fun onMethodCall(call: MethodCall, result: Result) {
            when (call.method) {
                "initialize" -> {
                    val videoPath = call.argument<String>("videoPath")
                    val videoUri = call.argument<String>("videoUri")
                    val autoPlay = call.argument<Boolean>("autoPlay") ?: false
                    val volume = call.argument<Double>("volume")?.toFloat() ?: 1.0f

                    if (videoPath != null || videoUri != null) {
                        videoPlayerManager?.release()
                        videoPlayerManager = VideoPlayerManager(
                            context,
                            videoPath,
                            videoUri,
                            ::sendVideoEvent
                        ).apply {
                            setVolume(volume)
                            if (autoPlay) {
                                play()
                            }
                        }
                        result.success(true)
                    } else {
                        result.error("INVALID_ARGUMENT", "Video path or URI required", null)
                    }
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
                    val position = call.argument<Double>("position")?.toFloat()
                    if (position != null) {
                        videoPlayerManager?.seekTo(position)
                    }
                    result.success(null)
                }
                "setVolume" -> {
                    val volume = call.argument<Double>("volume")?.toFloat()
                    if (volume != null) {
                        videoPlayerManager?.setVolume(volume)
                    }
                    result.success(null)
                }
                "setPlaybackSpeed" -> {
                    val speed = call.argument<Double>("speed")?.toFloat()
                    if (speed != null) {
                        videoPlayerManager?.setPlaybackSpeed(speed)
                    }
                    result.success(null)
                }
                "getDuration" -> {
                    val duration = videoPlayerManager?.getDuration()?.toDouble()
                    result.success(duration)
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

    companion object {
        const val PERMISSION_REQUEST_CODE = 123
    }
}