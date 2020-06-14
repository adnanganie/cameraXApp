package com.adnan.cardscanner.ui.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar


class PermissionFragment : Fragment() {

    private val rootView by lazy { FrameLayout(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isCameraPermissionGranted()) {
            //todo start camera
        } else {
            requestCameraPermission()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE) {
            when {
                //grantResults.contains(PackageManager.PERMISSION_GRANTED) -> //todo start camera
                //    shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                //-> requestCameraPermission()
                else -> displayErrorMessage()
            }
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        val permission =
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
        return permission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_REQUEST_CODE
        )
    }

    private fun displayErrorMessage() {
        Snackbar.make(
            rootView,
            "The camera permission must be granted in order to use this app",
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Retry") { requestCameraPermission() }
            .show()
    }

    companion object {
        const val CAMERA_REQUEST_CODE = 20
    }
}