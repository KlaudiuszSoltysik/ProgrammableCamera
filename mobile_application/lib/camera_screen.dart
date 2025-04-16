import 'package:camera/camera.dart';
import "package:flutter/material.dart";
import 'package:flutter/services.dart';
import "package:wakelock_plus/wakelock_plus.dart";

class CameraScreen extends StatefulWidget {
  @override
  _CameraScreenState createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  static const platform = MethodChannel(
    'com.example.mobile_application/native',
  );
  String? token;
  CameraController? _cameraController;
  bool _isStreaming = false;

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
    _triggerNativeStreaming();
    _initCamera();
  }

  @override
  void dispose() {
    WakelockPlus.disable();
    _cameraController?.dispose();
    super.dispose();
  }

  Future<void> _triggerNativeStreaming() async {
    _isStreaming = await platform.invokeMethod('initScreen');
  }

  Future<void> _initCamera() async {
    final cameras = await availableCameras();
    final rearCamera = cameras.firstWhere(
      (camera) => camera.lensDirection == CameraLensDirection.back,
    );

    _cameraController = CameraController(
      rearCamera,
      ResolutionPreset.medium,
      enableAudio: false,
    );

    await _cameraController!.initialize();
    if (mounted) {
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body:
          _cameraController == null || !_cameraController!.value.isInitialized
              ? Center(child: CircularProgressIndicator())
              : Column(
                children: [
                  AspectRatio(
                    aspectRatio: _cameraController!.value.aspectRatio,
                    child: CameraPreview(_cameraController!),
                  ),
                  SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Container(
                        width: 12,
                        height: 12,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: _isStreaming ? Colors.green : Colors.red,
                        ),
                      ),
                      SizedBox(width: 8),
                      Text('streaming', style: TextStyle(fontSize: 16)),
                    ],
                  ),
                ],
              ),
    );
  }
}
