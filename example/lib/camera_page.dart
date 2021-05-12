import 'dart:developer';

import 'package:camera_works/camera_works.dart';
import 'package:flutter/material.dart';

class CameraPage extends StatefulWidget {
  @override
  _CameraPageState createState() => _CameraPageState();
}

class _CameraPageState extends State<CameraPage> {
  late CameraController cameraController;
  var _lensType = CameraType.back;

  @override
  void initState() {
    super.initState();
    cameraController = CameraController();
    start();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(),
      body: Stack(
        children: [
          CameraView(cameraController),
          Container(
            alignment: Alignment.bottomCenter,
            margin: EdgeInsets.only(bottom: 32.0),
            child: _buildControls(),
          ),
        ],
      ),
    );
  }

  Widget _buildControls() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceAround,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        IconButton(
            iconSize: 16,
            icon: Icon(Icons.flip_camera_android, size: 32),
            color: Colors.white,
            padding: EdgeInsets.zero,
            onPressed: () {
              if (_lensType == CameraType.back) {
                switchCamera(CameraType.front);
              } else {
                switchCamera(CameraType.back);
              }
            }),
        IconButton(
          iconSize: 70,
          icon: Icon(Icons.circle, size: 70),
          color: Colors.white,
          padding: EdgeInsets.zero,
          onPressed: takePicture,
        ),
        ValueListenableBuilder(
          valueListenable: cameraController.torchState,
          builder: (context, state, child) {
            return IconButton(
              iconSize: 16,
              icon: Icon(Icons.bolt, size: 32),
              padding: EdgeInsets.zero,
              color: state == FlashState.off ? Colors.grey : Colors.white,
              onPressed: () {
                if (state == FlashState.on) {
                  setFlash(FlashState.off);
                } else {
                  setFlash(FlashState.on);
                }
              },
            );
          },
        ),
      ],
    );
  }

  @override
  void dispose() {
    stop();
    super.dispose();
  }

  void start() async {
    try {
      await cameraController.startAsync();
    } catch (e) {
      log(e.toString());
    }
  }

  void stop() {
    try {
      cameraController.dispose();
    } catch (e) {
      log(e.toString());
    }
  }

  void setFlash(FlashState type) async {
    try {
      await cameraController.setFlash(type);
    } catch (e) {
      log(e.toString());
    }
  }

  void switchCamera(CameraType type) async {
    try {
      await cameraController.switchCameraLens(type);
      setState(() {
        _lensType = type;
      });
    } catch (e) {
      log(e.toString());
    }
  }

  void takePicture() async {
    try {
      final image = await cameraController.takePicture();

      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Success Take Picture: ${image.path}'),
        backgroundColor: Colors.green,
      ));
    } on CameraException catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(e.message ?? ''),
        backgroundColor: Colors.red,
      ));

      log(e.toString());
    }
  }
}

class OpacityCurve extends Curve {
  @override
  double transform(double t) {
    if (t < 0.1) {
      return t * 10;
    } else if (t <= 0.9) {
      return 1.0;
    } else {
      return (1.0 - t) * 10;
    }
  }
}

class AnimatedLine extends AnimatedWidget {
  final Animation offsetAnimation;
  final Animation opacityAnimation;

  AnimatedLine(
      {Key? key, required this.offsetAnimation, required this.opacityAnimation})
      : super(key: key, listenable: offsetAnimation);

  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: opacityAnimation.value,
      child: CustomPaint(
        size: MediaQuery.of(context).size,
        painter: LinePainter(offsetAnimation.value),
      ),
    );
  }
}

class LinePainter extends CustomPainter {
  final double offset;

  LinePainter(this.offset);

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    final radius = size.width * 0.45;
    final dx = size.width / 2.0;
    final center = Offset(dx, radius);
    final rect = Rect.fromCircle(center: center, radius: radius);
    final paint = Paint()
      ..isAntiAlias = true
      ..shader = RadialGradient(
        colors: [Colors.green, Colors.green.withOpacity(0.0)],
        radius: 0.5,
      ).createShader(rect);
    canvas.translate(0.0, size.height * offset);
    canvas.scale(1.0, 0.1);
    final top = Rect.fromLTRB(0, 0, size.width, radius);
    canvas.clipRect(top);
    canvas.drawCircle(center, radius, paint);
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
