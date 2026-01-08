package com.example.device_media_library

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService

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
                val contentUri = if (mediaType == "video") {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
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
                    Log.w("ThumbnailManager", "Failed to create thumbnail for $mediaId")
                    mainHandler.post { result.error("THUMBNAIL_ERROR", "Failed to create thumbnail", null) }
                    return@execute
                }

                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val bytes = stream.toByteArray()
                stream.close()

                mainHandler.post { result.success(bytes) }
            } catch (e: SecurityException) {
                Log.e("ThumbnailManager", "Permission denied: ${e.message}")
                mainHandler.post { result.error("PERMISSION_DENIED", e.message ?: "Permission denied", null) }
            } catch (e: Exception) {
                Log.e("ThumbnailManager", "Error creating thumbnail: ${e.message}")
                mainHandler.post { result.error("THUMBNAIL_ERROR", e.message ?: "Error creating thumbnail", null) }
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
                Log.w("ThumbnailManager", "loadThumbnail failed: ${e.message}")
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
            Log.w("ThumbnailManager", "createImageThumbnail failed: ${e.message}")
            null
        }
    }

    private fun createVideoThumbnail(uri: Uri, width: Int, height: Int): Bitmap? {
        // Для видео используем MediaMetadataRetriever с улучшенной обработкой ошибок
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, uri)
            
            // Пробуем получить ключевой фрейм с таймкодом 0
            var frame = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            
            // Если не получилось, пробуем другие опции
            if (frame == null) {
                frame = mmr.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            
            // Если всё равно не получилось, пробуем без специфических опций
            if (frame == null) {
                frame = mmr.frameAtTime
            }
            
            mmr.release()
            
            frame?.let { 
                if (it.width > 0 && it.height > 0) {
                    Bitmap.createScaledBitmap(it, width, height, true)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w("ThumbnailManager", "createVideoThumbnail failed for $uri: ${e.message}")
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