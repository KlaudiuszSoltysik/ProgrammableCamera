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
    _triggerNativeInit();
  }

  @override
  void dispose() {
    WakelockPlus.disable();
    super.dispose();
  }

  Future<void> _triggerNativeInit() async {
    try {
      final String result = await platform.invokeMethod('initScreen');
      print("Native response: $result");
    } on PlatformException catch (e) {
      print("Failed to call native method: '${e.message}'.");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(body: Placeholder());
  }
}
