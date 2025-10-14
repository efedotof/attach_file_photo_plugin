package com.example.device_media_library

import android.content.Context
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.Result

class MediaQueryManager(private val context: Context) {

    fun getMedia(call: MethodCall, result: Result, mediaType: MediaType) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0
        val albumId = call.argument<String>("albumId")

        val mediaList = mutableListOf<Map<String, Any?>>()
        val contentResolver = context.contentResolver

        val (uri, idColumn, dateColumn) = when (mediaType) {
            MediaType.IMAGE -> Triple(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            MediaType.VIDEO -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED
            )
        }

        // Убраны колонки DATA, используем только URI
        val projection = when (mediaType) {
            MediaType.IMAGE -> arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            MediaType.VIDEO -> arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
            )
        }

        val selection = if (albumId != null) {
            "${MediaStore.Images.Media.BUCKET_ID} = ?"
        } else null

        val selectionArgs = if (albumId != null) {
            arrayOf(albumId)
        } else null

        val sortOrder = "$dateColumn DESC"

        try {
            val cursor: Cursor? = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)

            cursor?.use { c ->
                var count = 0
                var currentPosition = 0

                while (c.moveToNext() && (limit == null || count < limit!!)) {
                    if (currentPosition >= offset) {
                        val id = c.getLong(c.getColumnIndexOrThrow(idColumn))
                        val name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))

                        // Создаем content URI
                        val contentUri = when (mediaType) {
                            MediaType.IMAGE -> ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            MediaType.VIDEO -> ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                        }

                        val dateAdded = c.getLong(c.getColumnIndexOrThrow(dateColumn))
                        val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                        val width = c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH))
                        val height = c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT))
                        val bucketId = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID))
                        val bucketName = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME))

                        val mediaData = mutableMapOf(
                            "id" to id.toString(),
                            "name" to name,
                            "uri" to contentUri.toString(),
                            "dateAdded" to dateAdded,
                            "size" to size,
                            "width" to width,
                            "height" to height,
                            "albumId" to bucketId,
                            "albumName" to bucketName,
                            "type" to mediaType.name.lowercase()
                        )

                        if (mediaType == MediaType.VIDEO) {
                            val duration = c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
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

    fun getAllMedia(call: MethodCall, result: Result) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0
        val albumId = call.argument<String>("albumId")

        val allMedia = mutableListOf<Map<String, Any?>>()

        val images = getMediaList(MediaType.IMAGE, limit, offset, albumId)
        val videos = getMediaList(MediaType.VIDEO, limit, offset, albumId)

        allMedia.addAll(images)
        allMedia.addAll(videos)

        allMedia.sortByDescending { it["dateAdded"] as Long }

        val finalList = if (limit != null) {
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

        val (uri, idColumn, dateColumn) = when (mediaType) {
            MediaType.IMAGE -> Triple(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            MediaType.VIDEO -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED
            )
        }

        // Убраны колонки DATA
        val projection = when (mediaType) {
            MediaType.IMAGE -> arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            MediaType.VIDEO -> arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
            )
        }

        val selection = if (albumId != null) {
            "${MediaStore.MediaColumns.BUCKET_ID} = ?"
        } else null

        val selectionArgs = if (albumId != null) {
            arrayOf(albumId)
        } else null

        val sortOrder = "$dateColumn DESC"

        val cursor: Cursor? = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)

        cursor?.use { c ->
            var count = 0
            var currentPosition = 0

            while (c.moveToNext() && (limit == null || count < (limit ?: Int.MAX_VALUE))) {
                if (currentPosition >= offset) {
                    val id = c.getLong(c.getColumnIndexOrThrow(idColumn))
                    val name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                    val dateAdded = c.getLong(c.getColumnIndexOrThrow(dateColumn))
                    val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                    val width = c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH))
                    val height = c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT))
                    val bucketId = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID))
                    val bucketName = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME))

                    // Создаем content URI вместо использования пути
                    val contentUri = when (mediaType) {
                        MediaType.IMAGE -> ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        MediaType.VIDEO -> ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                    }

                    val mediaData = mutableMapOf(
                        "id" to id.toString(),
                        "name" to name,
                        "uri" to contentUri.toString(),
                        "dateAdded" to dateAdded,
                        "size" to size,
                        "width" to width,
                        "height" to height,
                        "albumId" to bucketId,
                        "albumName" to bucketName,
                        "type" to mediaType.name.lowercase()
                    )

                    if (mediaType == MediaType.VIDEO) {
                        val duration = c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
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

    fun getAlbums(result: Result) {
        val albums = mutableMapOf<String, Map<String, Any?>>()
        val contentResolver = context.contentResolver

        // Получение альбомов с изображениями - используем URI вместо DATA
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val imageCursor: Cursor? = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null,
            null,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        imageCursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val bucketId = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID))
                val bucketName = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))

                // Создаем URI для обложки
                val coverUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                if (!albums.containsKey(bucketId)) {
                    albums[bucketId] = mapOf(
                        "id" to bucketId,
                        "name" to bucketName,
                        "coverPath" to coverUri.toString(),
                        "type" to "mixed"
                    )
                }
            }
        }

        // Получение альбомов с видео - используем URI вместо DATA
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val videoCursor: Cursor? = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        videoCursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                val bucketId = c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID))
                val bucketName = c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME))

                // Создаем URI для обложки
                val coverUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                if (albums.containsKey(bucketId)) {
                    val existing = albums[bucketId]!!.toMutableMap()
                    existing["type"] = "mixed"
                    albums[bucketId] = existing
                } else {
                    albums[bucketId] = mapOf(
                        "id" to bucketId,
                        "name" to bucketName,
                        "coverPath" to coverUri.toString(),
                        "type" to "video"
                    )
                }
            }
        }

        result.success(albums.values.toList())
    }

    fun getMediaFromAlbum(call: MethodCall, result: Result) {
        val albumId = call.argument<String>("albumId") ?: run {
            result.error("INVALID_ARGUMENT", "Album ID is required", null)
            return
        }
        getAllMedia(call, result)
    }
}