package com.example.device_media_library

import android.net.Uri
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.Result

class FileReader(private val context: Context) {

    fun readFileByUri(call: MethodCall, result: Result) {
        try {
            val uriString = call.argument<String>("uri") ?: run {
                result.error("INVALID_ARGUMENT", "URI is required", null)
                return
            }

            val uri = Uri.parse(uriString)
            val contentResolver = context.contentResolver

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                result.success(bytes)
            } ?: run { result.error("FILE_ERROR", "Cannot open file stream", null) }
        } catch (e: SecurityException) {
            result.error("PERMISSION_DENIED", "Permission denied: ${e.message}", null)
        } catch (e: Exception) {
            result.error("FILE_ERROR", "Error reading file: ${e.message}", null)
        }
    }
}