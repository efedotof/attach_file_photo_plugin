package com.example.device_media_library

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.ExecutorService
import android.os.Handler

/**
 * Класс для всех запросов к MediaStore.
 *
 * Особенности оптимизации:
 * - Если доступна функциональность ContentResolver query с аргументами (API >= 29 / 26),
 *   используем QUERY_ARG_LIMIT и QUERY_ARG_OFFSET (снижаем сканирование курсора).
 * - Для getAllMedia используем единый запрос к MediaStore.Files (один проход),
 *   чтобы избежать двух отдельных запросов и излишней сортировки в памяти.
 * - Все запросы выполняются в executor (фон) и результат возвращается через mainHandler.
 */
class MediaQueryManager(
    private val context: Context,
    private val executor: ExecutorService,
    private val mainHandler: Handler
) {
    private val contentResolver: ContentResolver = context.contentResolver

    // Общая структура для проекций
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

    /**
     * Получение изображений или видео (пагинация limit/offset поддерживается).
     * Запрос делается в фоне; result возвращается в UI-потоке.
     */
    fun getMedia(call: MethodCall, result: MethodChannel.Result, mediaType: MediaType) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0
        val albumId = call.argument<String>("albumId")

        executor.execute {
            try {
                val (uri, projection, idColumn, dateColumn) = when (mediaType) {
                    MediaType.IMAGE -> Quad(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageProjection, MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
                    MediaType.VIDEO -> Quad(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoProjection, MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED)
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

    /**
     * Получение всех медиа (смешанных) в одном запросе через MediaStore.Files.
     * Поддерживает limit/offset и фильтр по albumId.
     */
    fun getAllMedia(call: MethodCall, result: MethodChannel.Result) {
        val limit = call.argument<Int>("limit")
        val offset = call.argument<Int>("offset") ?: 0
        val albumId = call.argument<String>("albumId")

        executor.execute {
            try {
                // Используем MediaStore.Files чтобы получить изображения и видео за один запрос.
                val uri = MediaStore.Files.getContentUri("external")

                // Проекция: выбираем только колонки, которые нам нужны
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
                    MediaStore.Video.Media.DURATION // будет null для изображений
                )

                // selection — только изображения и видео
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

                val items = queryWithLimitOffset(uri, projection, selectionBuilder.toString(), if (selectionArgsList.isEmpty()) null else selectionArgsList.toTypedArray(), sortOrder, limit, offset) { cursor ->
                    // Обрабатываем смешанный тип: определяем media_type и формируем нужные поля
                    val mediaTypeInt = safeGetInt(cursor, MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val id = safeGetLong(cursor, MediaStore.Files.FileColumns._ID)
                    val dateAdded = safeGetLong(cursor, MediaStore.MediaColumns.DATE_ADDED)
                    val size = safeGetLong(cursor, MediaStore.MediaColumns.SIZE)
                    val width = safeGetInt(cursor, MediaStore.MediaColumns.WIDTH)
                    val height = safeGetInt(cursor, MediaStore.MediaColumns.HEIGHT)
                    val bucketId = safeGetString(cursor, MediaStore.MediaColumns.BUCKET_ID)
                    val bucketName = safeGetString(cursor, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val name = safeGetString(cursor, MediaStore.MediaColumns.DISPLAY_NAME)
                    val duration = safeGetLong(cursor, MediaStore.Video.Media.DURATION)

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
                        "name" to name,
                        "uri" to contentUri?.toString(),
                        "dateAdded" to dateAdded,
                        "size" to size,
                        "width" to width,
                        "height" to height,
                        "albumId" to bucketId,
                        "albumName" to bucketName,
                        "type" to type
                    )

                    if (type == "video") map["duration"] = duration

                    map
                }

                mainHandler.post { result.success(items) }
            } catch (e: Exception) {
                mainHandler.post { result.error("QUERY_ERROR", "Error querying all media: ${e.message}", null) }
            }
        }
    }

    /**
     * Возвращает список альбомов (bucket) — комбинирует image + video.
     */
    fun getAlbums(result: MethodChannel.Result) {
        executor.execute {
            try {
                val albums = mutableMapOf<String, MutableMap<String, Any?>>()

                // Сначала изображения — используем минимальную проекцию
                val imgProj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                querySimple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgProj, null, null, MediaStore.Images.Media.BUCKET_DISPLAY_NAME) { cursor ->
                    val id = safeGetLong(cursor, MediaStore.Images.Media._ID)
                    val bucketId = safeGetString(cursor, MediaStore.Images.Media.BUCKET_ID) ?: return@querySimple
                    val bucketName = safeGetString(cursor, MediaStore.Images.Media.BUCKET_DISPLAY_NAME) ?: "Unknown"
                    val coverUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()

                    val existing = albums[bucketId]
                    if (existing == null) {
                        albums[bucketId] = mutableMapOf("id" to bucketId, "name" to bucketName, "coverPath" to coverUri, "type" to "image")
                    }
                }

                // Затем видео — если альбом существует — пометить как mixed, иначе добавить
                val vidProj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.BUCKET_ID, MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                querySimple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vidProj, null, null, MediaStore.Video.Media.BUCKET_DISPLAY_NAME) { cursor ->
                    val id = safeGetLong(cursor, MediaStore.Video.Media._ID)
                    val bucketId = safeGetString(cursor, MediaStore.Video.Media.BUCKET_ID) ?: return@querySimple
                    val bucketName = safeGetString(cursor, MediaStore.Video.Media.BUCKET_DISPLAY_NAME) ?: "Unknown"
                    val coverUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()

                    val existing = albums[bucketId]
                    if (existing != null) {
                        existing["type"] = "mixed"
                        // не перезаписываем coverPath, оставляем изображение
                    } else {
                        albums[bucketId] = mutableMapOf("id" to bucketId, "name" to bucketName, "coverPath" to coverUri, "type" to "video")
                    }
                }

                val list = albums.values.toList()
                mainHandler.post { result.success(list) }
            } catch (e: Exception) {
                mainHandler.post { result.error("ALBUM_ERROR", "Error building albums: ${e.message}", null) }
            }
        }
    }

    /**
     * Получение медиа из конкретного альбома.
     * Просто делегируем на getAllMedia/getMedia с фильтром albumId.
     */
    fun getMediaFromAlbum(call: MethodCall, result: MethodChannel.Result) {
        // Перенаправляем вызов в getAllMedia с albumId — getAllMedia поддерживает albumId.
        getAllMedia(call, result)
    }

    // ============================================================
    // === Вспомогательные общие функции для запросов к ContentResolver
    // ============================================================

    /**
     * Универсальная обёртка: выполняет query с поддержкой limit/offset через Bundle (если доступно).
     * mapper - преобразовывает курсор в Map<String, Any?> для каждой строки.
     */
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

        // Если API поддерживает аргументы запроса (QUERY_ARG_LIMIT / QUERY_ARG_OFFSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                if (selectionArgs != null) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                if (limit != null && limit > 0) putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                if (offset > 0) putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }

            contentResolver.query(uri, projection, queryArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(mapper(cursor))
                }
            }
        } else {
            // fallback: старые устройства — делаем обычный query и применяем offset/limit вручную (эффективно только при небольших offset)
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

    /**
     * Простой query без limit/offset, используется для обхода альбомов.
     */
    private fun querySimple(uri: Uri, projection: Array<String>, selection: String?, selectionArgs: Array<String>?, sortOrder: String?, action: (Cursor) -> Unit) {
        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) action(cursor)
        }
    }

    // =======================================
    // === Помощники для чтения колонок из курсора с безопасными падениями
    // =======================================
    private fun safeGetLong(cursor: Cursor, columnName: String): Long {
        return try {
            val idx = cursor.getColumnIndexOrThrow(columnName)
            cursor.getLong(idx)
        } catch (e: Exception) {
            0L
        }
    }

    private fun safeGetInt(cursor: Cursor, columnName: String): Int {
        return try {
            val idx = cursor.getColumnIndexOrThrow(columnName)
            cursor.getInt(idx)
        } catch (e: Exception) {
            0
        }
    }

    private fun safeGetString(cursor: Cursor, columnName: String): String? {
        return try {
            val idx = cursor.getColumnIndexOrThrow(columnName)
            cursor.getString(idx)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Преобразует курсор строки в Map (для IMAGE/VIDEO)
     */
    private fun cursorToMap(cursor: Cursor, mediaType: MediaType): Map<String, Any?> {
        val id = safeGetLong(cursor, if (mediaType == MediaType.IMAGE) MediaStore.Images.Media._ID else MediaStore.Video.Media._ID)
        val name = safeGetString(cursor, MediaStore.MediaColumns.DISPLAY_NAME)
        val dateAdded = safeGetLong(cursor, if (mediaType == MediaType.IMAGE) MediaStore.Images.Media.DATE_ADDED else MediaStore.Video.Media.DATE_ADDED)
        val size = safeGetLong(cursor, MediaStore.MediaColumns.SIZE)
        val width = safeGetInt(cursor, MediaStore.MediaColumns.WIDTH)
        val height = safeGetInt(cursor, MediaStore.MediaColumns.HEIGHT)
        val bucketId = safeGetString(cursor, MediaStore.MediaColumns.BUCKET_ID)
        val bucketName = safeGetString(cursor, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)

        val uri = when (mediaType) {
            MediaType.IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            MediaType.VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        }

        val map = mutableMapOf<String, Any?>(
            "id" to id.toString(),
            "name" to name,
            "uri" to uri.toString(),
            "dateAdded" to dateAdded,
            "size" to size,
            "width" to width,
            "height" to height,
            "albumId" to bucketId,
            "albumName" to bucketName,
            "type" to mediaType.name.lowercase()
        )

        if (mediaType == MediaType.VIDEO) {
            val duration = safeGetLong(cursor, MediaStore.Video.Media.DURATION)
            map["duration"] = duration
        }

        return map
    }

    // Маленькая контейнерная структура (т.к. Kotlin не имеет native Quad)
    private data class Quad<A,B,C,D>(val a: A, val b: B, val c: C, val d: D)
}
