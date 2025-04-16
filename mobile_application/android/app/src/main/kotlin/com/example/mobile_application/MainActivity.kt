package com.example.mobile_application

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.Context

import org.webrtc.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors

interface InitCallback {
    fun onResult(success: Boolean)
}

class MainActivity: FlutterActivity() {
    private val _channel = "com.example.mobile_application/native"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            _channel
        ).setMethodCallHandler { call, result ->
            if (call.method == "initScreen") {
                NativeBridge().createStream(this, object : InitCallback {
                    override fun onResult(success: Boolean) {
                        result.success(success)
                    }
                })
            } else {
                result.notImplemented()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            0
        )
    }
}

class NativeBridge {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection

    fun createStream(context: Context, callback: InitCallback) {
        executor.execute {
            val success = initWebRTC(context)
            callback.onResult(success)
        }
    }

    private fun initWebRTC(context: Context): Boolean {
        return try {
            val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initializationOptions)

            val eglBase = EglBase.create()

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                .createPeerConnectionFactory()

            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)

            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
                override fun onTrack(transceiver: RtpTransceiver?) {}
                override fun onDataChannel(dc: DataChannel?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onRenegotiationNeeded() {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            })!!

            val mediaConstraints = MediaConstraints()

            val videoCapturer = createCameraCapturer(context)
                ?: throw IllegalStateException("No camera found")

            val streamId = "stream1"

            val videoSource = peerConnectionFactory.createVideoSource(false)
            videoCapturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            videoCapturer.startCapture(1280, 720, 30)
            val videoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
            videoTrack.setEnabled(true)
            peerConnection.addTrack(videoTrack, listOf(streamId))

            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            }
            val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            val audioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
            audioTrack.setEnabled(true)
            peerConnection.addTrack(audioTrack, listOf(streamId))

            peerConnection.createOffer(object : SdpObserver {
                override fun onCreateSuccess(offer: SessionDescription) {
                    peerConnection.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            sendOfferToServer(offer)
                        }

                        override fun onSetFailure(p0: String?) {}
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, offer)
                }

                override fun onCreateFailure(p0: String?) {}
                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, mediaConstraints)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun createCameraCapturer(context: Context): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    return capturer
                }
            }
        }

        return null
    }

    private fun sendOfferToServer(offer: SessionDescription) {
        val client = OkHttpClient()

        val requestBody = JSONObject().apply {
            put("sdp", offer.description)
            put("type", offer.type.canonicalForm())
        }
        val request = Request.Builder()
            .url("http://192.168.0.19:8000/offer") // `localhost` for emulator is 10.0.2.2
            .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                response.body.string().let { body ->
                    val json = JSONObject(body)
                    val sdp = json.getString("sdp")
                    val type = json.getString("type")
                    val answer = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)

                    peerConnection.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {}
                        override fun onSetFailure(p0: String?) {}
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, answer)
                }
            }
        })
    }
}

