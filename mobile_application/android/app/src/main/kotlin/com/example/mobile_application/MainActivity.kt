package com.example.mobile_application

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.mobile_application/channel"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "extractFrames") {
                val videoPath = call.argument<String>("path")
                if (videoPath != null) {
                    val frames = extractFrames(videoPath)
                    result.success(frames)
                } else {
                    result.error("INVALID_ARGUMENT", "Video path is null", null)
                }
            } else {
                result.notImplemented()
            }
        }
    }

    private fun extractFrames(videoPath: String): List<ByteArray> {
        val retriever = MediaMetadataRetriever()
        val framesList = mutableListOf<ByteArray>()

        try {
            retriever.setDataSource(videoPath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0

            for (timeMs in 0 until duration step 250) {
                val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bitmap != null) {
                    framesList.add(convertBitmapToByteArray(bitmap))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }

        return framesList
    }

    private fun convertBitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
