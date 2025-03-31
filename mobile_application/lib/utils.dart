import "package:camera/camera.dart";
import "package:flutter/services.dart";
import "package:image/image.dart" as img;

Uint8List convertImageToBytes(CameraImage image) {
  try {
    img.Image convertedImage = convertYUV420ToImage(image);

    return Uint8List.fromList(img.encodeJpg(convertedImage, quality: 25));
  } catch (e) {
    return Uint8List(0);
  }
}

img.Image convertYUV420ToImage(CameraImage image) {
  final int width = image.width;
  final int height = image.height;
  final int uvRowStride = image.planes[1].bytesPerRow;
  final int uvPixelStride = image.planes[1].bytesPerPixel ?? 1;

  img.Image imgBuffer = img.Image(width: width, height: height);

  final Uint8List yBuffer = image.planes[0].bytes;
  final Uint8List uBuffer = image.planes[1].bytes;
  final Uint8List vBuffer = image.planes[2].bytes;

  for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
      final int uvIndex = uvPixelStride * (x ~/ 2) + uvRowStride * (y ~/ 2);
      final int yp = y * width + x;

      final int yValue = yBuffer[yp];
      final int uValue = uBuffer[uvIndex];
      final int vValue = vBuffer[uvIndex];

      int r = (yValue + 1.370705 * (vValue - 128)).toInt();
      int g =
          (yValue - 0.698001 * (vValue - 128) - 0.337633 * (uValue - 128))
              .toInt();
      int b = (yValue + 1.732446 * (uValue - 128)).toInt();

      imgBuffer.setPixelRgb(
        x,
        y,
        r.clamp(0, 255),
        g.clamp(0, 255),
        b.clamp(0, 255),
      );
    }
  }
  return imgBuffer;
}
