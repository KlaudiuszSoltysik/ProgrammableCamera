import "dart:convert";

import "package:flutter/material.dart";
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:http/http.dart' as http;
import "package:wakelock_plus/wakelock_plus.dart";

class CameraScreen extends StatefulWidget {
  @override
  _CameraScreenState createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  RTCPeerConnection? _peerConnection;
  MediaStream? _localStream;
  final RTCVideoRenderer _localRenderer = RTCVideoRenderer();
  // final String serverUrl = "http://10.0.2.2:8000";
  final String serverUrl = "http://192.168.8.31:8000";

  String? token;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();

    if (token == null) {
      final receivedArgs = ModalRoute.of(context)?.settings.arguments;
      if (receivedArgs is String) {
        setState(() {
          token = receivedArgs;
        });
      }
    }
  }

  @override
  void initState() {
    super.initState();
    WakelockPlus.enable();
    _initializeWebRTC();
  }

  @override
  void dispose() {
    _localRenderer.dispose();
    _peerConnection?.close();
    _localStream?.dispose();
    WakelockPlus.disable();
    super.dispose();
  }

  Future<void> _initializeWebRTC() async {
    await _localRenderer.initialize();

    _localStream = await navigator.mediaDevices.getUserMedia({
      'video': {
        'facingMode': 'environment',
        'width': {'ideal': 1920, 'min': 1280},
        'height': {'ideal': 1080, 'min': 720},
        'frameRate': {'ideal': 30, 'max': 30},
        'mandatory': {'minWidth': 1280, 'minHeight': 720, 'minFrameRate': 30},
      },
      'audio': true,
    });

    _localRenderer.srcObject = _localStream;

    Map<String, dynamic> config = {
      'iceServers': [
        {'urls': 'stun:stun.l.google.com:19302'},
      ],
    };

    _peerConnection = await createPeerConnection(config);

    _localStream!.getTracks().forEach((track) {
      _peerConnection!.addTrack(track, _localStream!);
    });

    RTCSessionDescription offer = await _peerConnection!.createOffer();
    await _peerConnection!.setLocalDescription(offer);

    final response = await http.post(
      Uri.parse('$serverUrl/offer'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'sdp': offer.sdp, 'type': offer.type}),
    );

    if (response.statusCode == 200) {
      final answer = jsonDecode(response.body);
      await _peerConnection!.setRemoteDescription(
        RTCSessionDescription(answer['sdp'], answer['type']),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(body: Center(child: RTCVideoView(_localRenderer)));
  }
}
