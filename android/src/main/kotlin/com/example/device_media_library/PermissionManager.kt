package com.example.device_media_library

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodChannel


class PermissionManager(private val context: Context) {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 10001
    }

    private var activityBinding: ActivityPluginBinding? = null
    private var pendingResult: MethodChannel.Result? = null
    private var requestInProgress = false

    fun setActivityBinding(binding: ActivityPluginBinding?) {
        activityBinding = binding
        binding?.addRequestPermissionsResultListener { requestCode, _, grantResults ->
            onRequestPermissionsResult(requestCode, grantResults)
        }
    }

    fun checkPermission(result: MethodChannel.Result) {
        result.success(hasPermission())
    }

    fun requestPermission(activity: Activity, result: MethodChannel.Result) {
        if (requestInProgress) {
            result.error(
                "REQUEST_IN_PROGRESS",
                "Permission request already in progress",
                null
            )
            return
        }

        requestInProgress = true
        pendingResult = result

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        ActivityCompat.requestPermissions(
            activity,
            permissions,
            PERMISSION_REQUEST_CODE
        )
    }

    private fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray
    ): Boolean {

        if (requestCode != PERMISSION_REQUEST_CODE) return false

        val granted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        pendingResult?.success(granted)
        pendingResult = null
        requestInProgress = false

        return true
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
