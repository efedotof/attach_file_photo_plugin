package com.example.device_media_library

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.IOException
import java.util.concurrent.ExecutorService

/**
 * Чтение произвольного URI (openInputStream) в фоновой задаче.
 * Результат возвращается в UI-поток.
 */
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
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    mainHandler.post { result.success(bytes) }
                } ?: run {
                    mainHandler.post { result.error("FILE_ERROR", "Cannot open stream for $uriString", null) }
                }
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
