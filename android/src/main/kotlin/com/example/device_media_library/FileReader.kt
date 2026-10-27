package com.example.device_media_library

import android.content.Context
import android.net.Uri
import android.os.Handler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService

class FileReader(
    private val context: Context,
    private val executor: ExecutorService,
    private val mainHandler: Handler
) {
    fun readFileByUri(call: MethodCall, result: MethodChannel.Result) {
        val uriString = call.argument<String>("uri")
        if (uriString.isNullOrEmpty()) {
            result.error("INVALID_ARGUMENT", "URI is required", null)
            return
        }
        val uri = Uri.parse(uriString)

        executor.execute {
            try {
                val bytes: ByteArray = when (uri.scheme) {
                    "file" -> {
                        val path = uri.path ?: throw IOException("Invalid file URI")
                        File(path).readBytes()
                    }
                    else -> {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IOException("Cannot open stream for $uriString")
                    }
                }
                mainHandler.post { result.success(bytes) }
            } catch (e: SecurityException) {
                mainHandler.post { result.error("PERMISSION_DENIED", "Permission denied: ${e.message}", null) }
            } catch (e: IOException) {
                mainHandler.post { result.error("FILE_ERROR", "IO error: ${e.message}", null) }
            } catch (e: Exception) {
                mainHandler.post { result.error("FILE_ERROR", "Unknown error: ${e.message}", null) }
            }
        }
    }
}