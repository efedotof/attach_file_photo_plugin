package com.example.device_media_library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.Result
import java.io.ByteArrayOutputStream

class ThumbnailManager(private val context: Context) {

    fun getThumbnail(call: MethodCall, result: Result) {
        val mediaId = call.argument<String>("mediaId") ?: ""
        val mediaType = call.argument<String>("mediaType") ?: "image"
        val width = call.argument<Int>("width") ?: 200
        val height = call.argument<Int>("height") ?: 200

        try {
            val contentUri = if (mediaType == "video") {
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
            result.error("THUMBNAIL_ERROR", "Error generating thumbnail: ${e.message}", e.toString())
        }
    }

    private fun createImageThumbnail(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(inputStream, null, options)

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565

                context.contentResolver.openInputStream(uri)?.use { newInputStream ->
                    BitmapFactory.decodeStream(newInputStream, null, options)?.let { scaledBitmap ->
                        Bitmap.createScaledBitmap(scaledBitmap, reqWidth, reqHeight, true)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createVideoThumbnail(uri: Uri, width: Int, height: Int): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.frameAtTime
            retriever.release()

            frame?.let { 
                Bitmap.createScaledBitmap(it, width, height, true)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
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
}