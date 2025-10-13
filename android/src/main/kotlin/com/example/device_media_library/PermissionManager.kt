package com.example.device_media_library

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodChannel.Result

class PermissionManager(private val context: Context) {
    private var activityBinding: ActivityPluginBinding? = null
    private var permissionResult: Result? = null
    private var isPermissionRequestInProgress = false

    fun setActivityBinding(binding: ActivityPluginBinding?) {
        activityBinding = binding
        binding?.addRequestPermissionsResultListener { requestCode, permissions, grantResults ->
            onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    fun checkPermission(result: Result) {
        result.success(hasPermission())
    }

    fun requestPermission(activity: Activity, result: Result) {
        if (isPermissionRequestInProgress) {
            return
        }

        isPermissionRequestInProgress = true
        permissionResult = result

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        ActivityCompat.requestPermissions(activity, permissions, DeviceMediaLibraryPlugin.PERMISSION_REQUEST_CODE)
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == DeviceMediaLibraryPlugin.PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            permissionResult?.success(granted)
            permissionResult = null
            isPermissionRequestInProgress = false
            return true
        }
        return false
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImagePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val hasVideoPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            hasImagePermission && hasVideoPermission
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }
}