package com.example.device_media_library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.ByteArrayOutputStream

class DeviceMediaLibraryPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activityBinding: ActivityPluginBinding? = null
    private var permissionResult: Result? = null
    private var isPermissionRequestInProgress = false

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "device_media_library")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "checkPermission" -> {
                checkPermission(result)
            }
            "requestPermission" -> {
                requestPermission(result)
            }
            "getImages" -> {
                if (hasPermission()) {
                    getMedia(call, result, MediaType.IMAGE)
                } else {
                    result.error(
                            "PERMISSION_DENIED",
                            "Read external storage permission not granted",
                            null
                    )
                }
            }
            "readFileByUri" -> {
                if (hasPermission()) {
                    readFileByUri(call, result)
                } else {
                    result.error("PERMISSION_DENIED", "Permission not granted", null)
                }
            }
            "getVideos" -> {
                if (hasPermission()) {
                    getMedia(call, result, MediaType.VIDEO)
                } else {
                    result.error(
                            "PERMISSION_DENIED",
                            "Read external storage permission not granted",
                            null
                    )
                }
            }
            "getAllMedia" -> {
                if (hasPermission()) {
                    getAllMedia(call, result)
                } else {
                    result.error(
                            "PERMISSION_DENIED",
                            "Read external storage permission not granted",
                            null
                    )
                }
            }
            "getAlbums" -> {
                if (hasPermission()) {
                    getAlbums(result)
                } else {
                    result.error(
                            "PERMISSION_DENIED",
                            "Read external storage permission not granted",
                            null
                    )
                }
            }
            "getMediaFromAlbum" -> {
                if (hasPermission()) {
                    getMediaFromAlbum(call, result)
                } else {
                    result.error(
                            "PERMISSION_DENIED",
                            "Read external storage permission not granted",
                            null
                    )
                }
            }
            "getThumbnail" -> {
                if (hasPermission()) {
                    getThumbnail(call, result)
                } else {
                    result.error(
                            "PERMISSION_DENIED",
                            "Read external storage permission not granted",
                            null
                    )
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun checkPermission(result: Result) {
        result.success(hasPermission())
    }

    private fun requestPermission(result: Result) {
        if (isPermissionRequestInProgress) {
            return
        }

        val activity =
                activityBinding?.activity
                        ?: run {
                            result.error("NO_ACTIVITY", "No activity available", null)
                            return
                        }

        isPermissionRequestInProgress = true
        permissionResult = result

        val permissions =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                    )
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

        ActivityCompat.requestPermissions(activity, permissions, PERMISSION_REQUEST_CODE)
    }

    fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ): Boolean {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val granted =
                    grantResults.isNotEmpty() &&
                            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            permissionResult?.success(granted)
            permissionResult = null
            isPermissionRequestInProgress = false
            return true
        }
        return false
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImagePermission =
                    ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED
            val hasVideoPermission =
                    ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED
            hasImagePermission && hasVideoPermission
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getMedia(call: MethodCall, result: Result, mediaType: MediaType) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0
        val albumId = call.argument<String>("albumId")

        val mediaList = mutableListOf<Map<String, Any?>>()
        val contentResolver = context.contentResolver

        val (uri, idColumn, dateColumn) =
                when (mediaType) {
                    MediaType.IMAGE ->
                            Triple(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    MediaStore.Images.Media._ID,
                                    MediaStore.Images.Media.DATE_ADDED
                            )
                    MediaType.VIDEO ->
                            Triple(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    MediaStore.Video.Media._ID,
                                    MediaStore.Video.Media.DATE_ADDED
                            )
                }

        val projection =
                when (mediaType) {
                    MediaType.IMAGE ->
                            arrayOf(
                                    MediaStore.Images.Media._ID,
                                    MediaStore.Images.Media.DISPLAY_NAME,
                                    MediaStore.Images.Media.DATA,
                                    MediaStore.Images.Media.DATE_ADDED,
                                    MediaStore.Images.Media.SIZE,
                                    MediaStore.Images.Media.WIDTH,
                                    MediaStore.Images.Media.HEIGHT,
                                    MediaStore.Images.Media.BUCKET_ID,
                                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                            )
                    MediaType.VIDEO ->
                            arrayOf(
                                    MediaStore.Video.Media._ID,
                                    MediaStore.Video.Media.DISPLAY_NAME,
                                    MediaStore.Video.Media.DATA,
                                    MediaStore.Video.Media.DATE_ADDED,
                                    MediaStore.Video.Media.SIZE,
                                    MediaStore.Video.Media.WIDTH,
                                    MediaStore.Video.Media.HEIGHT,
                                    MediaStore.Video.Media.BUCKET_ID,
                                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                                    MediaStore.Video.Media.DURATION
                            )
                }

        val selection =
                if (albumId != null) {
                    "${MediaStore.Images.Media.BUCKET_ID} = ?"
                } else null

        val selectionArgs =
                if (albumId != null) {
                    arrayOf(albumId)
                } else null

        val sortOrder = "$dateColumn DESC"

        try {
            val cursor: Cursor? =
                    contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)

            cursor?.use { c ->
                var count = 0
                var currentPosition = 0

                while (c.moveToNext() && (limit == null || count < limit)) {
                    if (currentPosition >= offset) {
                        val id = c.getLong(c.getColumnIndexOrThrow(idColumn))
                        val name =
                                c.getString(
                                        c.getColumnIndexOrThrow(
                                                MediaStore.MediaColumns.DISPLAY_NAME
                                        )
                                )

                        // Создаем content URI
                        val contentUri =
                                when (mediaType) {
                                    MediaType.IMAGE ->
                                            ContentUris.withAppendedId(
                                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                    id
                                            )
                                    MediaType.VIDEO ->
                                            ContentUris.withAppendedId(
                                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                                    id
                                            )
                                }

                        val dateAdded = c.getLong(c.getColumnIndexOrThrow(dateColumn))
                        val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                        val width = c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH))
                        val height =
                                c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT))
                        val bucketId =
                                c.getString(
                                        c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                                )
                        val bucketName =
                                c.getString(
                                        c.getColumnIndexOrThrow(
                                                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
                                        )
                                )

                        val mediaData =
                                mutableMapOf(
                                        "id" to id.toString(),
                                        "name" to name,
                                        "uri" to contentUri.toString(), // Основное поле - URI
                                        "path" to
                                                contentUri.toString(), // Для обратной совместимости
                                        "dateAdded" to dateAdded,
                                        "size" to size,
                                        "width" to width,
                                        "height" to height,
                                        "albumId" to bucketId,
                                        "albumName" to bucketName,
                                        "type" to mediaType.name.lowercase()
                                )

                        if (mediaType == MediaType.VIDEO) {
                            val duration =
                                    c.getLong(
                                            c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                                    )
                            mediaData["duration"] = duration
                        }

                        mediaList.add(mediaData)
                        count++
                    }
                    currentPosition++
                }
            }

            result.success(mediaList)
        } catch (e: SecurityException) {
            result.error("PERMISSION_DENIED", "Permission denied: ${e.message}", null)
        } catch (e: Exception) {
            result.error("QUERY_ERROR", "Error querying media: ${e.message}", null)
        }
    }
    private fun readFileByUri(call: MethodCall, result: Result) {
        try {
            val uriString =
                    call.argument<String>("uri")
                            ?: run {
                                result.error("INVALID_ARGUMENT", "URI is required", null)
                                return
                            }

            val uri = Uri.parse(uriString)
            val contentResolver = context.contentResolver

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                result.success(bytes)
            }
                    ?: run { result.error("FILE_ERROR", "Cannot open file stream", null) }
        } catch (e: SecurityException) {
            result.error("PERMISSION_DENIED", "Permission denied: ${e.message}", null)
        } catch (e: Exception) {
            result.error("FILE_ERROR", "Error reading file: ${e.message}", null)
        }
    }

    private fun getAllMedia(call: MethodCall, result: Result) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0
        val albumId = call.argument<String>("albumId")

        val allMedia = mutableListOf<Map<String, Any?>>()

        val images = getMediaList(MediaType.IMAGE, limit, offset, albumId)
        val videos = getMediaList(MediaType.VIDEO, limit, offset, albumId)

        allMedia.addAll(images)
        allMedia.addAll(videos)

        allMedia.sortByDescending { it["dateAdded"] as Long }

        val finalList =
                if (limit != null) {
                    allMedia.take(limit)
                } else {
                    allMedia
                }

        result.success(finalList)
    }

    private fun getMediaList(
            mediaType: MediaType,
            limit: Int?,
            offset: Int,
            albumId: String?
    ): List<Map<String, Any?>> {
        val mediaList = mutableListOf<Map<String, Any?>>()
        val contentResolver = context.contentResolver

        val (uri, idColumn, dateColumn) =
                when (mediaType) {
                    MediaType.IMAGE ->
                            Triple(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    MediaStore.Images.Media._ID,
                                    MediaStore.Images.Media.DATE_ADDED
                            )
                    MediaType.VIDEO ->
                            Triple(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    MediaStore.Video.Media._ID,
                                    MediaStore.Video.Media.DATE_ADDED
                            )
                }

        val projection =
                when (mediaType) {
                    MediaType.IMAGE ->
                            arrayOf(
                                    MediaStore.Images.Media._ID,
                                    MediaStore.Images.Media.DISPLAY_NAME,
                                    MediaStore.Images.Media.DATA,
                                    MediaStore.Images.Media.DATE_ADDED,
                                    MediaStore.Images.Media.SIZE,
                                    MediaStore.Images.Media.WIDTH,
                                    MediaStore.Images.Media.HEIGHT,
                                    MediaStore.Images.Media.BUCKET_ID,
                                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                            )
                    MediaType.VIDEO ->
                            arrayOf(
                                    MediaStore.Video.Media._ID,
                                    MediaStore.Video.Media.DISPLAY_NAME,
                                    MediaStore.Video.Media.DATA,
                                    MediaStore.Video.Media.DATE_ADDED,
                                    MediaStore.Video.Media.SIZE,
                                    MediaStore.Video.Media.WIDTH,
                                    MediaStore.Video.Media.HEIGHT,
                                    MediaStore.Video.Media.BUCKET_ID,
                                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                                    MediaStore.Video.Media.DURATION
                            )
                }

        val selection =
                if (albumId != null) {
                    "${MediaStore.MediaColumns.BUCKET_ID} = ?"
                } else null

        val selectionArgs =
                if (albumId != null) {
                    arrayOf(albumId)
                } else null

        val sortOrder = "$dateColumn DESC"

        val cursor: Cursor? =
                contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)

        cursor?.use { c ->
            var count = 0
            var currentPosition = 0

            while (c.moveToNext() && (limit == null || count < (limit ?: Int.MAX_VALUE))) {
                if (currentPosition >= offset) {
                    val id = c.getLong(c.getColumnIndexOrThrow(idColumn))
                    val name =
                            c.getString(
                                    c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                            )
                    val path = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                    val dateAdded = c.getLong(c.getColumnIndexOrThrow(dateColumn))
                    val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                    val width = c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH))
                    val height = c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT))
                    val bucketId =
                            c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID))
                    val bucketName =
                            c.getString(
                                    c.getColumnIndexOrThrow(
                                            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
                                    )
                            )

                    val mediaData =
                            mutableMapOf(
                                    "id" to id.toString(),
                                    "name" to name,
                                    "path" to path,
                                    "dateAdded" to dateAdded,
                                    "size" to size,
                                    "width" to width,
                                    "height" to height,
                                    "albumId" to bucketId,
                                    "albumName" to bucketName,
                                    "type" to mediaType.name.lowercase()
                            )

                    if (mediaType == MediaType.VIDEO) {
                        val duration =
                                c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                        mediaData["duration"] = duration
                    }

                    mediaList.add(mediaData)
                    count++
                }
                currentPosition++
            }
        }

        return mediaList
    }

    private fun getAlbums(result: Result) {
        val albums = mutableMapOf<String, Map<String, Any?>>()
        val contentResolver = context.contentResolver

        val imageProjection =
                arrayOf(
                        MediaStore.Images.Media.BUCKET_ID,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                        MediaStore.Images.Media.DATA
                )

        val imageCursor: Cursor? =
                contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        imageProjection,
                        null,
                        null,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )

        imageCursor?.use { c ->
            while (c.moveToNext()) {
                val bucketId =
                        c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID))
                val bucketName =
                        c.getString(
                                c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                        )
                val data = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))

                if (!albums.containsKey(bucketId)) {
                    albums[bucketId] =
                            mapOf(
                                    "id" to bucketId,
                                    "name" to bucketName,
                                    "coverPath" to data,
                                    "type" to "mixed"
                            )
                }
            }
        }

        val videoProjection =
                arrayOf(
                        MediaStore.Video.Media.BUCKET_ID,
                        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                        MediaStore.Video.Media.DATA
                )

        val videoCursor: Cursor? =
                contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        videoProjection,
                        null,
                        null,
                        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
                )

        videoCursor?.use { c ->
            while (c.moveToNext()) {
                val bucketId =
                        c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID))
                val bucketName =
                        c.getString(
                                c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                        )
                val data = c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))

                if (albums.containsKey(bucketId)) {
                    val existing = albums[bucketId]!!.toMutableMap()
                    existing["type"] = "mixed"
                    albums[bucketId] = existing
                } else {
                    albums[bucketId] =
                            mapOf(
                                    "id" to bucketId,
                                    "name" to bucketName,
                                    "coverPath" to data,
                                    "type" to "video"
                            )
                }
            }
        }

        result.success(albums.values.toList())
    }

    private fun getMediaFromAlbum(call: MethodCall, result: Result) {
        val albumId =
                call.argument<String>("albumId")
                        ?: run {
                            result.error("INVALID_ARGUMENT", "Album ID is required", null)
                            return
                        }

        getAllMedia(call, result)
    }

    private fun getThumbnail(call: MethodCall, result: Result) {
        val mediaId = call.argument<String>("mediaId") ?: ""
        val mediaType = call.argument<String>("mediaType") ?: "image"
        val width = call.argument<Int>("width") ?: 200
        val height = call.argument<Int>("height") ?: 200

        try {
            val contentUri =
                    if (mediaType == "video") {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }

            val mediaUri = Uri.withAppendedPath(contentUri, mediaId)
            val bitmap: Bitmap?

            if (mediaType == "video") {
                bitmap = createVideoThumbnail(mediaUri, width, height)
            } else {
                bitmap = createImageThumbnail(mediaUri, width, height)
            }

            if (bitmap != null) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val byteArray = stream.toByteArray()
                stream.close()
                result.success(byteArray)
            } else {
                result.error("THUMBNAIL_ERROR", "Error generating thumbnail", null)
            }
        } catch (e: Exception) {
            result.error(
                    "THUMBNAIL_ERROR",
                    "Error generating thumbnail: ${e.message}",
                    e.toString()
            )
        }
    }

    private fun createImageThumbnail(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            // Метод 1: Попробовать использовать ContentResolver для безопасного доступа
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(inputStream, null, options)

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565

                // Нужно заново открыть поток
                context.contentResolver.openInputStream(uri)?.use { newInputStream ->
                    val scaledBitmap = BitmapFactory.decodeStream(newInputStream, null, options)
                    if (scaledBitmap != null) {
                        Bitmap.createScaledBitmap(scaledBitmap, reqWidth, reqHeight, true)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            // Метод 2: Fallback - попробовать через file path если URI метод не сработал
            try {
                createImageThumbnailFallback(uri, reqWidth, reqHeight)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun createImageThumbnailFallback(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = context.contentResolver.query(uri, filePathColumn, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val columnIndex = c.getColumnIndex(filePathColumn[0])
                    if (columnIndex != -1) {
                        val filePath = c.getString(columnIndex)

                        val options = BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        BitmapFactory.decodeFile(filePath, options)

                        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                        options.inJustDecodeBounds = false
                        options.inPreferredConfig = Bitmap.Config.RGB_565

                        val scaledBitmap = BitmapFactory.decodeFile(filePath, options)
                        if (scaledBitmap != null) {
                            Bitmap.createScaledBitmap(scaledBitmap, reqWidth, reqHeight, true)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createVideoThumbnail(uri: Uri, width: Int, height: Int): Bitmap? {
        return try {
            // Метод 1: Попробовать MediaMetadataRetriever с URI
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.frameAtTime
            retriever.release()

            if (frame != null) {
                Bitmap.createScaledBitmap(frame, width, height, true)
            } else {
                // Метод 2: Fallback - попробовать через file path
                createVideoThumbnailFallback(uri, width, height)
            }
        } catch (e: Exception) {
            // Метод 3: Final fallback
            createVideoThumbnailFallback(uri, width, height)
        }
    }

    private fun createVideoThumbnailFallback(uri: Uri, width: Int, height: Int): Bitmap? {
        return try {
            val filePathColumn = arrayOf(MediaStore.Video.Media.DATA)
            val cursor = context.contentResolver.query(uri, filePathColumn, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val columnIndex = c.getColumnIndex(filePathColumn[0])
                    if (columnIndex != -1) {
                        val filePath = c.getString(columnIndex)

                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(filePath)
                            val frame = retriever.frameAtTime
                            retriever.release()

                            if (frame != null) {
                                return Bitmap.createScaledBitmap(frame, width, height, true)
                            }
                        } catch (e: Exception) {
                            // Ignore and try next method
                        }

                        ThumbnailUtils.createVideoThumbnail(
                                        filePath,
                                        MediaStore.Images.Thumbnails.MINI_KIND
                                )
                                ?.let { Bitmap.createScaledBitmap(it, width, height, true) }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addRequestPermissionsResultListener { requestCode, permissions, grantResults ->
            onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDetachedFromActivity() {
        activityBinding = null
        isPermissionRequestInProgress = false
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addRequestPermissionsResultListener { requestCode, permissions, grantResults ->
            onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding = null
        isPermissionRequestInProgress = false
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    private enum class MediaType {
        IMAGE,
        VIDEO
    }
}
