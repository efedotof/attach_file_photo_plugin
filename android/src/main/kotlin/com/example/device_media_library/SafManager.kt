package com.example.device_media_library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class SafManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "device_media_library_prefs"
        private const val KEY_DOWNLOADS_URI = "downloads_tree_uri"
        private const val KEY_DOCUMENTS_URI = "documents_tree_uri"
        const val REQUEST_CODE_DOWNLOADS_SAF = 20001
        const val REQUEST_CODE_DOCUMENTS_SAF = 20002
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasDownloadsUri(): Boolean = getDownloadsUri() != null
    fun hasDocumentsUri(): Boolean = getDocumentsUri() != null

    fun getDownloadsUri(): Uri? = getPersistedUri(KEY_DOWNLOADS_URI)
    fun getDocumentsUri(): Uri? = getPersistedUri(KEY_DOCUMENTS_URI)

    fun saveDownloadsUri(uri: Uri) = saveUri(KEY_DOWNLOADS_URI, uri)
    fun saveDocumentsUri(uri: Uri) = saveUri(KEY_DOCUMENTS_URI, uri)

    fun clearDownloadsUri() = clearUri(KEY_DOWNLOADS_URI)
    fun clearDocumentsUri() = clearUri(KEY_DOCUMENTS_URI)

    private fun getPersistedUri(key: String): Uri? {
        val raw = prefs.getString(key, null) ?: return null
        val uri = Uri.parse(raw)
        return try {
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (hasPermission) uri else null
        } catch (e: Exception) {
            null
        }
    }

    private fun saveUri(key: String, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            prefs.edit().putString(key, uri.toString()).apply()
        } catch (e: SecurityException) {
         
        }
    }

    private fun clearUri(key: String) {
        getPersistedUri(key)?.let { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        prefs.edit().remove(key).apply()
    }

    fun buildPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }

    fun listFiles(
        treeUri: Uri,
        limit: Int?,
        offset: Int,
        recursive: Boolean,
        category: String
    ): List<Map<String, Any?>> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()

        val all = mutableListOf<DocumentFile>()
        fun walk(dir: DocumentFile, depth: Int) {
            if (depth > 3) return
            dir.listFiles().forEach { f ->
                if (f.isFile) {
                    all.add(f)
                } else if (f.isDirectory && recursive) {
                    walk(f, depth + 1)
                }
            }
        }
        walk(root, 0)

        val sorted = all.sortedByDescending { it.lastModified() }
        val page = sorted.drop(offset).let {
            if (limit != null && limit > 0) it.take(limit) else it
        }

        return page.map { f ->
            val name = f.name ?: ""
            val mime = f.type ?: guessMimeType(name)
            mapOf(
                "id" to f.uri.toString(),
                "name" to name,
                "uri" to f.uri.toString(),
                "path" to f.uri.toString(),
                "dateAdded" to f.lastModified() / 1000L,
                "size" to f.length(),
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
            "epub" -> "application/epub+zip"
            "fb2" -> "application/octet-stream"
            "mobi" -> "application/octet-stream"
            "html", "htm" -> "text/html"
            "rtf" -> "text/rtf"
            "pem" -> "application/x-pem-file"
            else -> "application/octet-stream"
        }
    }

    private fun mediaTypeFromMime(mime: String): String = when {
        mime.startsWith("image/") -> "image"
        mime.startsWith("video/") -> "video"
        mime.startsWith("audio/") -> "audio"
        else -> "file"
    }
}