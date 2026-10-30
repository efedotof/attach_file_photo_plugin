package com.example.device_media_library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
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
    private lateinit var safManager: SafManager

    private var permissionManager: PermissionManager? = null
    private var videoPlayerManager: VideoPlayerManager? = null

    private var pendingDownloadsSafResult: MethodChannel.Result? = null
    private var pendingDocumentsSafResult: MethodChannel.Result? = null
    private var pendingPickFilesResult: MethodChannel.Result? = null

    companion object {
        private const val PICK_FILES_REQUEST_CODE = 20003

        @Volatile
        private var instance: DeviceMediaLibraryPlugin? = null
        fun getInstance(): DeviceMediaLibraryPlugin? = instance
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        instance = this
        context = binding.applicationContext

        permissionManager = PermissionManager(context)
        safManager = SafManager(context)

        mediaQueryManager = MediaQueryManager(context, executor, mainHandler, safManager)
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
        binding.addActivityResultListener { requestCode, resultCode, data ->
            handleActivityResult(requestCode, resultCode, data)
        }
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

    private fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        val uri: Uri? = data?.data
        when (requestCode) {
            SafManager.REQUEST_CODE_DOWNLOADS_SAF -> {
                val pending = pendingDownloadsSafResult
                pendingDownloadsSafResult = null
                if (resultCode == Activity.RESULT_OK && uri != null) {
                    safManager.saveDownloadsUri(uri)
                    pending?.success(true)
                } else {
                    pending?.success(false)
                }
                return true
            }
            SafManager.REQUEST_CODE_DOCUMENTS_SAF -> {
                val pending = pendingDocumentsSafResult
                pendingDocumentsSafResult = null
                if (resultCode == Activity.RESULT_OK && uri != null) {
                    safManager.saveDocumentsUri(uri)
                    pending?.success(true)
                } else {
                    pending?.success(false)
                }
                return true
            }
            
            PICK_FILES_REQUEST_CODE -> {
                val pending = pendingPickFilesResult
                pendingPickFilesResult = null
                if (resultCode == Activity.RESULT_OK) {
                    val uris = mutableListOf<Uri>()
                    val clipData = data?.clipData
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i).uri?.let { uris.add(it) }
                        }
                    } else {
                        data?.data?.let { uris.add(it) }
                    }

                    val results = uris.map { fileUri ->
                        var name = fileUri.lastPathSegment ?: "Unknown"
                        var size = 0L
                        var mime = context.contentResolver.getType(fileUri) ?: "application/octet-stream"

                        context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1) name = cursor.getString(nameIndex)
                                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                            }
                        }

                        val type = when {
                            mime.startsWith("image/") -> "image"
                            mime.startsWith("video/") -> "video"
                            mime.startsWith("audio/") -> "audio"
                            else -> "file"
                        }

                        mapOf(
                            "id" to fileUri.toString(),
                            "name" to name,
                            "uri" to fileUri.toString(),
                            "path" to fileUri.toString(),
                            "dateAdded" to System.currentTimeMillis() / 1000L,
                            "size" to size,
                            "mimeType" to mime,
                            "type" to type,
                            "category" to "picked",
                            "width" to 0,
                            "height" to 0,
                            "albumId" to "",
                            "albumName" to "Picked Files"
                        )
                    }
                    pending?.success(results)
                } else {
                    pending?.success(emptyList<Map<String, Any?>>())
                }
                return true
            }
        }
        return false
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

                "checkAllFilesPermission" ->
                    permissionManager?.checkAllFilesPermission(result)
                        ?: result.error("NO_ACTIVITY", "Plugin not attached", null)

                "requestAllFilesPermission" -> {
                    val activity = activityBinding?.activity
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity is null", null)
                        return
                    }
                    permissionManager?.requestAllFilesPermission(activity, result)
                }

                "checkStorageAccess" -> {
                    val folder = call.argument<String>("folder") ?: "download"
                    result.success(mediaQueryManager.checkStorageAccess(folder))
                }

                "hasDownloadsFolderAccess" -> {
                    result.success(safManager.hasDownloadsUri())
                }

                "requestDownloadsFolderAccess" -> {
                    val activity = activityBinding?.activity
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity is null", null)
                        return
                    }
                    if (safManager.hasDownloadsUri()) {
                        result.success(true)
                        return
                    }
                    if (pendingDownloadsSafResult != null) {
                        result.error("REQUEST_IN_PROGRESS", "SAF request already in progress", null)
                        return
                    }
                    pendingDownloadsSafResult = result
                    try {
                        activity.startActivityForResult(
                            safManager.buildPickerIntent(),
                            SafManager.REQUEST_CODE_DOWNLOADS_SAF
                        )
                    } catch (e: Exception) {
                        pendingDownloadsSafResult = null
                        result.error("SAF_ERROR", "Cannot open SAF picker: ${e.message}", null)
                    }
                }

                "revokeDownloadsFolderAccess" -> {
                    safManager.clearDownloadsUri()
                    result.success(true)
                }

                "hasDocumentsFolderAccess" -> {
                    result.success(safManager.hasDocumentsUri())
                }

                "requestDocumentsFolderAccess" -> {
                    val activity = activityBinding?.activity
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity is null", null)
                        return
                    }
                    if (safManager.hasDocumentsUri()) {
                        result.success(true)
                        return
                    }
                    if (pendingDocumentsSafResult != null) {
                        result.error("REQUEST_IN_PROGRESS", "SAF request already in progress", null)
                        return
                    }
                    pendingDocumentsSafResult = result
                    try {
                        activity.startActivityForResult(
                            safManager.buildPickerIntent(),
                            SafManager.REQUEST_CODE_DOCUMENTS_SAF
                        )
                    } catch (e: Exception) {
                        pendingDocumentsSafResult = null
                        result.error("SAF_ERROR", "Cannot open SAF picker: ${e.message}", null)
                    }
                }

                "revokeDocumentsFolderAccess" -> {
                    safManager.clearDocumentsUri()
                    result.success(true)
                }

                "pickFiles" -> {
                    val activity = activityBinding?.activity
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity is null", null)
                        return
                    }
                    if (pendingPickFilesResult != null) {
                        result.error("REQUEST_IN_PROGRESS", "File pick request already in progress", null)
                        return
                    }

                    val multiple = call.argument<Boolean>("multiple") ?: true
                    pendingPickFilesResult = result

                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*" 
                        if (multiple) {
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    }
                    try {
                        activity.startActivityForResult(intent, PICK_FILES_REQUEST_CODE)
                    } catch (e: Exception) {
                        pendingPickFilesResult = null
                        result.error("PICK_ERROR", "Cannot open file picker: ${e.message}", null)
                    }
                }

                "getImages" -> mediaQueryManager.getMedia(call, result, MediaType.IMAGE)
                "getVideos" -> mediaQueryManager.getMedia(call, result, MediaType.VIDEO)
                "getAllMedia" -> mediaQueryManager.getAllMedia(call, result)
                "getAlbums" -> mediaQueryManager.getAlbums(result)
                "getMediaFromAlbum" -> mediaQueryManager.getMediaFromAlbum(call, result)
                "getAudio" -> mediaQueryManager.getAudio(call, result)
                "getDownloads" -> mediaQueryManager.getDownloads(call, result)
                "getDocuments" -> mediaQueryManager.getDocuments(call, result)
                "getThumbnail" -> thumbnailManager.getThumbnail(call, result)
                "readFileByUri" -> fileReader.readFileByUri(call, result)

                "getFilePath" -> {
                    val uriString = call.argument<String>("uri")
                    if (uriString.isNullOrEmpty()) {
                        result.error("INVALID_ARGUMENT", "URI is required", null)
                        return
                    }
                    val filePath = getFilePath(uriString)
                    if (filePath != null) result.success(filePath)
                    else result.error("FILE_ERROR", "Could not resolve file path", null)
                }

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
                    val volume = call.argument<Double>("volume") ?: 1.0

                    videoPlayerManager?.release()
                    videoPlayerManager = VideoPlayerManager(
                        context,
                        path,
                        uri,
                        object : VideoPlayerManager.VideoEventCallback {

                            override fun onPrepared(duration: Double) {
                                sendVideoEvent(
                                    mapOf("event" to "onPrepared", "duration" to duration)
                                )
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
                                sendVideoEvent(
                                    mapOf(
                                        "event" to "onBufferingUpdate",
                                        "isBuffering" to isBuffering
                                    )
                                )
                            }

                            override fun onCompleted() {
                                sendVideoEvent(mapOf("event" to "onCompleted"))
                            }

                            override fun onError(message: String) {
                                sendVideoEvent(
                                    mapOf("event" to "onError", "message" to message)
                                )
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

                    videoPlayerManager?.setVolume(volume.toFloat())
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
                    call.argument<Double>("position")?.let { pos ->
                        videoPlayerManager?.seekTo(pos)
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

    private fun getFilePath(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            return uri.path
        }
        if (uri.scheme == "content") {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                if (it.moveToFirst()) {
                    return it.getString(columnIndex)
                }
            }
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { }
            } catch (e: Exception) {
            }
        }
        return null
    }
}