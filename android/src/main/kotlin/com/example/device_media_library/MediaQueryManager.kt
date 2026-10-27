package com.example.device_media_library

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.concurrent.ExecutorService

class MediaQueryManager(
    private val context: Context,
    private val executor: ExecutorService,
    private val mainHandler: Handler
) {
    private val contentResolver: ContentResolver = context.contentResolver

    private val imageProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )

    private val videoProjection = arrayOf(
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


    fun getMedia(call: MethodCall, result: MethodChannel.Result, mediaType: MediaType) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0
        val albumId = call.argument<String>("albumId")

        executor.execute {
            try {
                val (uri, projection, idColumn, dateColumn) = when (mediaType) {
                    MediaType.IMAGE -> Quad(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageProjection,
                        MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED
                    )
                    MediaType.VIDEO -> Quad(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoProjection,
                        MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED
                    )
                }

                val selection: String? = if (albumId != null) "${MediaStore.MediaColumns.BUCKET_ID} = ?" else null
                val selectionArgs: Array<String>? = if (albumId != null) arrayOf(albumId) else null
                val sortOrder = "$dateColumn DESC"

                val items = queryWithLimitOffset(uri, projection, selection, selectionArgs, sortOrder, limit, offset) { c ->
                    cursorToMap(c, mediaType)
                }

                mainHandler.post { result.success(items) }
            } catch (e: SecurityException) {
                mainHandler.post { result.error("PERMISSION_DENIED", "Permission denied: ${e.message}", null) }
            } catch (e: Exception) {
                mainHandler.post { result.error("QUERY_ERROR", "Error querying media: ${e.message}", null) }
            }
        }
    }

    fun getAllMedia(call: MethodCall, result: MethodChannel.Result) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0
        val albumId = call.argument<String>("albumId")

        executor.execute {
            try {
                val uri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT,
                    MediaStore.MediaColumns.BUCKET_ID,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION
                )

                val selectionBuilder = StringBuilder()
                selectionBuilder.append("(")
                selectionBuilder.append("${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}")
                selectionBuilder.append(" OR ")
                selectionBuilder.append("${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}")
                selectionBuilder.append(")")

                val selectionArgsList = mutableListOf<String>()
                if (albumId != null) {
                    selectionBuilder.append(" AND ${MediaStore.MediaColumns.BUCKET_ID} = ?")
                    selectionArgsList.add(albumId)
                }

                val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

                val items = queryWithLimitOffset(
                    uri, projection, selectionBuilder.toString(),
                    if (selectionArgsList.isEmpty()) null else selectionArgsList.toTypedArray(),
                    sortOrder, limit, offset
                ) { cursor ->
                    val mediaTypeInt = safeGetInt(cursor, MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val id = safeGetLong(cursor, MediaStore.Files.FileColumns._ID)
                    val type = when (mediaTypeInt) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> "image"
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> "video"
                        else -> "unknown"
                    }
                    val contentUri = when (type) {
                        "image" -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        "video" -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        else -> null
                    }
                    val map = mutableMapOf<String, Any?>(
                        "id" to id.toString(),
                        "name" to safeGetString(cursor, MediaStore.MediaColumns.DISPLAY_NAME),
                        "uri" to contentUri?.toString(),
                        "dateAdded" to safeGetLong(cursor, MediaStore.MediaColumns.DATE_ADDED),
                        "size" to safeGetLong(cursor, MediaStore.MediaColumns.SIZE),
                        "width" to safeGetInt(cursor, MediaStore.MediaColumns.WIDTH),
                        "height" to safeGetInt(cursor, MediaStore.MediaColumns.HEIGHT),
                        "albumId" to safeGetString(cursor, MediaStore.MediaColumns.BUCKET_ID),
                        "albumName" to safeGetString(cursor, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME),
                        "type" to type
                    )
                    if (type == "video") map["duration"] = safeGetLong(cursor, MediaStore.Video.Media.DURATION)
                    map
                }

                mainHandler.post { result.success(items) }
            } catch (e: Exception) {
                mainHandler.post { result.error("QUERY_ERROR", "Error querying all media: ${e.message}", null) }
            }
        }
    }

    fun getAlbums(result: MethodChannel.Result) {
        executor.execute {
            try {
                val albums = mutableMapOf<String, MutableMap<String, Any?>>()

                val imgProj = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )
                querySimple(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgProj, null, null,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                ) { cursor ->
                    val id = safeGetLong(cursor, MediaStore.Images.Media._ID)
                    val bucketId = safeGetString(cursor, MediaStore.Images.Media.BUCKET_ID) ?: return@querySimple
                    val bucketName = safeGetString(cursor, MediaStore.Images.Media.BUCKET_DISPLAY_NAME) ?: "Unknown"
                    val coverUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()
                    albums.getOrPut(bucketId) {
                        mutableMapOf("id" to bucketId, "name" to bucketName, "coverPath" to coverUri, "type" to "image")
                    }
                }

                val vidProj = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.BUCKET_ID,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME
                )
                querySimple(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vidProj, null, null,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME
                ) { cursor ->
                    val id = safeGetLong(cursor, MediaStore.Video.Media._ID)
                    val bucketId = safeGetString(cursor, MediaStore.Video.Media.BUCKET_ID) ?: return@querySimple
                    val bucketName = safeGetString(cursor, MediaStore.Video.Media.BUCKET_DISPLAY_NAME) ?: "Unknown"
                    val coverUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val existing = albums[bucketId]
                    if (existing != null) {
                        existing["type"] = "mixed"
                    } else {
                        albums[bucketId] = mutableMapOf(
                            "id" to bucketId, "name" to bucketName,
                            "coverPath" to coverUri, "type" to "video"
                        )
                    }
                }

                mainHandler.post { result.success(albums.values.toList()) }
            } catch (e: Exception) {
                mainHandler.post { result.error("ALBUM_ERROR", "Error building albums: ${e.message}", null) }
            }
        }
    }

    fun getMediaFromAlbum(call: MethodCall, result: MethodChannel.Result) {
        getAllMedia(call, result)
    }

    fun getAudio(call: MethodCall, result: MethodChannel.Result) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0

        executor.execute {
            try {
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

                val items = queryWithLimitOffset(uri, projection, selection, null, sortOrder, limit, offset) { c ->
                    val id = safeGetLong(c, MediaStore.Audio.Media._ID)
                    val albumName = safeGetString(c, MediaStore.Audio.Media.ALBUM)
                    mapOf(
                        "id" to id.toString(),
                        "name" to safeGetString(c, MediaStore.Audio.Media.DISPLAY_NAME),
                        "uri" to ContentUris.withAppendedId(uri, id).toString(),
                        "dateAdded" to safeGetLong(c, MediaStore.Audio.Media.DATE_ADDED),
                        "size" to safeGetLong(c, MediaStore.Audio.Media.SIZE),
                        "duration" to safeGetLong(c, MediaStore.Audio.Media.DURATION),
                        "mimeType" to safeGetString(c, MediaStore.Audio.Media.MIME_TYPE),
                        "artist" to safeGetString(c, MediaStore.Audio.Media.ARTIST),
                        "album" to albumName,
                        "albumId" to safeGetString(c, MediaStore.Audio.Media.ALBUM_ID),
                        "albumName" to albumName,
                        "type" to "audio",
                        "width" to 0,
                        "height" to 0
                    )
                }
                mainHandler.post { result.success(items) }
            } catch (e: SecurityException) {
                mainHandler.post { result.error("PERMISSION_DENIED", e.message ?: "Permission denied", null) }
            } catch (e: Exception) {
                mainHandler.post { result.error("QUERY_ERROR", "Error querying audio: ${e.message}", null) }
            }
        }
    }


    fun getDownloads(call: MethodCall, result: MethodChannel.Result) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0
        val recursive = call.argument<Boolean>("recursive") ?: true

        executor.execute {
            try {
                val hasAllAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                        Environment.isExternalStorageManager()

                if (hasAllAccess) {
                 
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val items = filePage(dir, recursive, limit, offset, "download")
                    mainHandler.post { result.success(items) }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                   
                    val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val projection = arrayOf(
                        MediaStore.Downloads._ID,
                        MediaStore.Downloads.DISPLAY_NAME,
                        MediaStore.Downloads.DATE_ADDED,
                        MediaStore.Downloads.SIZE,
                        MediaStore.Downloads.MIME_TYPE,
                        MediaStore.Downloads.RELATIVE_PATH
                    )
                    val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC"
                    val items = queryWithLimitOffset(uri, projection, null, null, sortOrder, limit, offset) { c ->
                        val id = safeGetLong(c, MediaStore.Downloads._ID)
                        val mime = safeGetString(c, MediaStore.Downloads.MIME_TYPE) ?: "application/octet-stream"
                        mapOf(
                            "id" to id.toString(),
                            "name" to safeGetString(c, MediaStore.Downloads.DISPLAY_NAME),
                            "uri" to ContentUris.withAppendedId(uri, id).toString(),
                            "dateAdded" to safeGetLong(c, MediaStore.Downloads.DATE_ADDED),
                            "size" to safeGetLong(c, MediaStore.Downloads.SIZE),
                            "mimeType" to mime,
                            "type" to mediaTypeFromMime(mime),
                            "category" to "download",
                            "path" to safeGetString(c, MediaStore.Downloads.RELATIVE_PATH),
                            "width" to 0,
                            "height" to 0,
                            "albumId" to "",
                            "albumName" to "Downloads"
                        )
                    }
                    mainHandler.post { result.success(items) }
                } else {
                    mainHandler.post {
                        result.error(
                            "PERMISSION_DENIED",
                            "READ_EXTERNAL_STORAGE required",
                            null
                        )
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { result.error("QUERY_ERROR", "Error querying downloads: ${e.message}", null) }
            }
        }
    }


    fun getDocuments(call: MethodCall, result: MethodChannel.Result) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0
        val recursive = call.argument<Boolean>("recursive") ?: true

        executor.execute {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !Environment.isExternalStorageManager()
                ) {
                    mainHandler.post {
                        result.error(
                            "PERMISSION_DENIED",
                            "MANAGE_EXTERNAL_STORAGE required to read Documents",
                            null
                        )
                    }
                    return@execute
                }

                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val items = filePage(dir, recursive, limit, offset, "document")
                mainHandler.post { result.success(items) }
            } catch (e: Exception) {
                mainHandler.post { result.error("QUERY_ERROR", "Error querying documents: ${e.message}", null) }
            }
        }
    }


    private fun queryWithLimitOffset(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String,
        limit: Int?,
        offset: Int,
        mapper: (Cursor) -> Map<String, Any?>
    ): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                if (selectionArgs != null) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                if (limit != null && limit > 0) putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                if (offset > 0) putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            contentResolver.query(uri, projection, queryArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) results.add(mapper(cursor))
            }
        } else {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                var current = 0
                var count = 0
                while (cursor.moveToNext()) {
                    if (current >= offset) {
                        if (limit != null && limit > 0 && count >= limit) break
                        results.add(mapper(cursor))
                        count++
                    }
                    current++
                }
            }
        }
        return results
    }

    private fun querySimple(
        uri: Uri, projection: Array<String>, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?, action: (Cursor) -> Unit
    ) {
        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) action(cursor)
        }
    }

    private fun safeGetLong(cursor: Cursor, columnName: String): Long =
        try { cursor.getLong(cursor.getColumnIndexOrThrow(columnName)) } catch (e: Exception) { 0L }

    private fun safeGetInt(cursor: Cursor, columnName: String): Int =
        try { cursor.getInt(cursor.getColumnIndexOrThrow(columnName)) } catch (e: Exception) { 0 }

    private fun safeGetString(cursor: Cursor, columnName: String): String? =
        try { cursor.getString(cursor.getColumnIndexOrThrow(columnName)) } catch (e: Exception) { null }

    private fun cursorToMap(cursor: Cursor, mediaType: MediaType): Map<String, Any?> {
        val id = safeGetLong(cursor, if (mediaType == MediaType.IMAGE) MediaStore.Images.Media._ID else MediaStore.Video.Media._ID)
        val uri = when (mediaType) {
            MediaType.IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            MediaType.VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        }
        val map = mutableMapOf<String, Any?>(
            "id" to id.toString(),
            "name" to safeGetString(cursor, MediaStore.MediaColumns.DISPLAY_NAME),
            "uri" to uri.toString(),
            "dateAdded" to safeGetLong(cursor, MediaStore.MediaColumns.DATE_ADDED),
            "size" to safeGetLong(cursor, MediaStore.MediaColumns.SIZE),
            "width" to safeGetInt(cursor, MediaStore.MediaColumns.WIDTH),
            "height" to safeGetInt(cursor, MediaStore.MediaColumns.HEIGHT),
            "albumId" to safeGetString(cursor, MediaStore.MediaColumns.BUCKET_ID),
            "albumName" to safeGetString(cursor, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME),
            "type" to mediaType.name.lowercase()
        )
        if (mediaType == MediaType.VIDEO) {
            map["duration"] = safeGetLong(cursor, MediaStore.Video.Media.DURATION)
        }
        return map
    }


    private fun filePage(
        root: File, recursive: Boolean, limit: Int?, offset: Int, category: String
    ): List<Map<String, Any?>> {
        val maxDepth = if (recursive) 3 else 0
        val files = walkDirectory(root, maxDepth)
        val sorted = files.sortedByDescending { it.lastModified() }
        val page = sorted.drop(offset).let { if (limit != null && limit > 0) it.take(limit) else it }
        return page.map { fileToMap(it, category) }
    }

    private fun walkDirectory(root: File, maxDepth: Int, maxFiles: Int = 5000): List<File> {
        val result = mutableListOf<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > maxDepth || result.size >= maxFiles) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (result.size >= maxFiles) return
                if (f.isFile && f.canRead()) {
                    result.add(f)
                } else if (f.isDirectory && !f.isHidden) {
                    walk(f, depth + 1)
                }
            }
        }
        if (root.exists()) walk(root, 0)
        return result
    }

    private fun fileToMap(file: File, category: String): Map<String, Any?> {
        val mime = guessMimeType(file.name)
        return mapOf(
            "id" to file.absolutePath,
            "name" to file.name,
            "uri" to "file://${file.absolutePath}",
            "path" to file.absolutePath,
            "dateAdded" to file.lastModified() / 1000L,
            "size" to file.length(),
            "mimeType" to mime,
            "type" to mediaTypeFromMime(mime),
            "category" to category,
            "width" to 0,
            "height" to 0,
            "albumId" to "",
            "albumName" to when (category) {
                "download" -> "Downloads"
                "document" -> "Documents"
                else -> category
            }
        )
    }

    private fun guessMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    private fun mediaTypeFromMime(mime: String): String = when {
        mime.startsWith("image/") -> "image"
        mime.startsWith("video/") -> "video"
        mime.startsWith("audio/") -> "audio"
        else -> "file"
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}