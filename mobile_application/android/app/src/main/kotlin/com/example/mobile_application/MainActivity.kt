package com.example.mobile_application

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.Context
import android.util.Log

import org.webrtc.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.mobile_application/native"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->
            if (call.method == "initScreen") {
                val message = NativeBridge().createStream(this)
                result.success(message)
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

    fun createStream(context: Context): String {
        executor.execute {
            initWebRTC(context)
        }
        return "Initializing WebRTC..."
    }

    private fun initWebRTC(context: Context) {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

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

        val eglBase = EglBase.create()
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)

        val videoCapturer = createCameraCapturer(context)
            ?: throw IllegalStateException("No camera found")

        val videoSource = peerConnectionFactory.createVideoSource(false)
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        videoCapturer.startCapture(1280, 720, 30)

        val videoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

        val streamId = "stream1"
        peerConnection.addTrack(videoTrack, listOf(streamId))

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
        val sanitizedSdp = offer.description.replace("\n", "\\n").replace("\r", "\\r")

        val requestBody = JSONObject().apply {
            put("sdp", offer.description)
            put("type", offer.type.canonicalForm())
        }
        val request = Request.Builder()
            .url("http://192.168.8.33:8000/offer") // `localhost` for emulator is 10.0.2.2
            .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    val json = JSONObject(body)
                    val sdp = json.getString("sdp")
                    val type = json.getString("type")
                    val answer = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
                    
                    peerConnection.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d("WebRTC", "Remote SDP set")
                        }

                        override fun onSetFailure(p0: String?) {}
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, answer)
                }
            }
        })
    }
}

