import "dart:convert";

import "package:camera/camera.dart";
import "package:flutter/material.dart";
import "package:flutter/services.dart";
import "package:wakelock_plus/wakelock_plus.dart";
import "package:web_socket_channel/io.dart";

import 'utils.dart';

class CameraScreen extends StatefulWidget {
  @override
  _CameraScreenState createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  CameraController? _cameraController;
  IOWebSocketChannel? _channel;

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
    _initializeCamera();
  }

  @override
  void dispose() {
    WakelockPlus.disable();
    _cameraController!.stopImageStream();
    _cameraController?.dispose();
    _channel?.sink.close();
    super.dispose();
  }

  Future<void> _initializeCamera() async {
    final cameras = await availableCameras();
    final backCamera = cameras.firstWhere(
      (camera) => camera.lensDirection == CameraLensDirection.back,
    );

    _cameraController = CameraController(
      backCamera,
      ResolutionPreset.medium,
      enableAudio: true,
    );

    await _cameraController!.initialize();

    setState(() {});

    //_channel = IOWebSocketChannel.connect("ws://10.0.2.2:5050/send");
    _channel = IOWebSocketChannel.connect("ws://192.168.8.29:5050/send");

    int lastFrameTime = 0;

    _channel!.sink.add(jsonEncode({"token": "token"}));

    _cameraController!.startImageStream((CameraImage image) {
      int now = DateTime.now().millisecondsSinceEpoch;

      if (now - lastFrameTime < 50) return;
      lastFrameTime = now;

      Uint8List imageBytes = convertImageToBytes(image);

      _channel!.sink.add(imageBytes);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child:
            _cameraController == null || !_cameraController!.value.isInitialized
                ? CircularProgressIndicator()
                : CameraPreview(_cameraController!),
      ),
    );
  }
}
