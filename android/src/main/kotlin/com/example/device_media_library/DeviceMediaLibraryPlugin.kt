package com.example.device_media_library

import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodCall

class DeviceMediaLibraryPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activityBinding: ActivityPluginBinding? = null
    private lateinit var permissionManager: PermissionManager
    private lateinit var mediaQueryManager: MediaQueryManager
    private lateinit var thumbnailManager: ThumbnailManager
    private lateinit var fileReader: FileReader

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "device_media_library")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        
        permissionManager = PermissionManager(context)
        mediaQueryManager = MediaQueryManager(context)
        thumbnailManager = ThumbnailManager(context)
        fileReader = FileReader(context)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "checkPermission" -> {
                permissionManager.checkPermission(result)
            }
            "requestPermission" -> {
                val activity = activityBinding?.activity
                if (activity != null) {
                    permissionManager.requestPermission(activity, result)
                } else {
                    result.error("NO_ACTIVITY", "No activity available", null)
                }
            }
            "getImages" -> {
                if (permissionManager.hasPermission()) {
                    mediaQueryManager.getMedia(call, result, MediaType.IMAGE)
                } else {
                    result.error("PERMISSION_DENIED", "Read external storage permission not granted", null)
                }
            }
            "readFileByUri" -> {
                if (permissionManager.hasPermission()) {
                    fileReader.readFileByUri(call, result)
                } else {
                    result.error("PERMISSION_DENIED", "Permission not granted", null)
                }
            }
            "getVideos" -> {
                if (permissionManager.hasPermission()) {
                    mediaQueryManager.getMedia(call, result, MediaType.VIDEO)
                } else {
                    result.error("PERMISSION_DENIED", "Read external storage permission not granted", null)
                }
            }
            "getAllMedia" -> {
                if (permissionManager.hasPermission()) {
                    mediaQueryManager.getAllMedia(call, result)
                } else {
                    result.error("PERMISSION_DENIED", "Read external storage permission not granted", null)
                }
            }
            "getAlbums" -> {
                if (permissionManager.hasPermission()) {
                    mediaQueryManager.getAlbums(result)
                } else {
                    result.error("PERMISSION_DENIED", "Read external storage permission not granted", null)
                }
            }
            "getMediaFromAlbum" -> {
                if (permissionManager.hasPermission()) {
                    mediaQueryManager.getMediaFromAlbum(call, result)
                } else {
                    result.error("PERMISSION_DENIED", "Read external storage permission not granted", null)
                }
            }
            "getThumbnail" -> {
                if (permissionManager.hasPermission()) {
                    thumbnailManager.getThumbnail(call, result)
                } else {
                    result.error("PERMISSION_DENIED", "Read external storage permission not granted", null)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
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
        channel.setMethodCallHandler(null)
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 123
    }
}

enum class MediaType {
    IMAGE, VIDEO
}