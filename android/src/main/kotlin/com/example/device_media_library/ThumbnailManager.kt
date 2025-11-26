package com.example.device_media_library

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService

/**
 * Генерация миниатюр (thumbnail). Оптимизирована:
 * - Для API >= 29 используем ContentResolver.loadThumbnail (если доступно)
 * - Для видео используем MediaMetadataRetriever
 * - Вся работа в фоне; результат возвращаем в UI-поток
 */
class ThumbnailManager(
    private val context: Context,
    private val executor: ExecutorService,
    private val mainHandler: Handler
) {
    fun getThumbnail(call: MethodCall, result: MethodChannel.Result) {
        val mediaId = call.argument<String>("mediaId") ?: run {
            result.error("INVALID_ARG", "mediaId required", null)
            return
        }
        val mediaType = call.argument<String>("mediaType") ?: "image"
        val width = call.argument<Int>("width") ?: 200
        val height = call.argument<Int>("height") ?: 200

        executor.execute {
            try {
                val contentUri = if (mediaType == "video") MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val idLong = mediaId.toLongOrNull()
                if (idLong == null) {
                    mainHandler.post { result.error("INVALID_ARG", "mediaId must be numeric", null) }
                    return@execute
                }
                val mediaUri = ContentUris.withAppendedId(contentUri, idLong)
                val bitmap = if (mediaType == "video") {
                    createVideoThumbnail(mediaUri, width, height)
                } else {
                    createImageThumbnail(mediaUri, width, height)
                }

                if (bitmap == null) {
                    mainHandler.post { result.error("THUMBNAIL_ERROR", "Failed to create thumbnail", null) }
                    return@execute
                }

                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val bytes = stream.toByteArray()
                stream.close()

                mainHandler.post { result.success(bytes) }
            } catch (e: SecurityException) {
                mainHandler.post { result.error("PERMISSION_DENIED", e.message ?: "Permission denied", null) }
            } catch (e: Exception) {
                mainHandler.post { result.error("THUMBNAIL_ERROR", e.message ?: "Error", null) }
            }
        }
    }

    private fun createImageThumbnail(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        // API >= 29: ContentResolver.loadThumbnail (эффективно и быстро)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val size = android.util.Size(reqWidth, reqHeight)
                context.contentResolver.loadThumbnail(uri, size, null)
            } catch (e: Exception) {
                // fallback ниже
                null
            }
        }

        // fallback: decodeStream с inSampleSize
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                // повторно открыть поток
                context.contentResolver.openInputStream(uri)?.use { input2 ->
                    BitmapFactory.decodeStream(input2, null, options)?.let { b ->
                        Bitmap.createScaledBitmap(b, reqWidth, reqHeight, true)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createVideoThumbnail(uri: Uri, width: Int, height: Int): Bitmap? {
        // Для видео используем MediaMetadataRetriever (универсально)
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, uri)
            val frame = mmr.frameAtTime // ключевой фрейм
            mmr.release()
            frame?.let { Bitmap.createScaledBitmap(it, width, height, true) }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
