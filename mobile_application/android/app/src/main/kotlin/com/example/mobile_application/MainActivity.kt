package com.example.mobile_application

import io.flutter.embedding.android.FlutterActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.Manifest

class MainActivity: FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            0
        )
    }
}
