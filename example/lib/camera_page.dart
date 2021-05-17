import 'dart:developer';

import 'package:camera_works/camera_works.dart';
import 'package:flutter/material.dart';

class CameraPage extends StatefulWidget {
  @override
  _CameraPageState createState() => _CameraPageState();
}

class _CameraPageState extends State<CameraPage> {
  late CameraController cameraController;
  var _lensType = CameraType.front;

  @override
  void initState() {
    super.initState();
    cameraController = CameraController(_lensType);
    start();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Camera Demo'),
      ),
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
            color: Colors.blue,
            padding: EdgeInsets.zero,
            onPressed: () {
              switchCamera(
                  _lensType.index == 0 ? CameraType.back : CameraType.front);
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
    } on CameraException catch (e) {
      _showErrorSnackBar(e.message ?? '');
    } catch (e) {
      log(e.toString());
    }
  }

  void stop() {
    try {
      cameraController.dispose();
    } on CameraException catch (e) {
      _showErrorSnackBar(e.message ?? '');
    } catch (e) {
      log(e.toString());
    }
  }

  void setFlash(FlashState type) async {
    try {
      await cameraController.setFlash(type);
    } on CameraException catch (e) {
      _showErrorSnackBar(e.message ?? '');
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
    } on CameraException catch (e) {
      _showErrorSnackBar(e.message ?? '');
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
      _showErrorSnackBar(e.message ?? '');
      log(e.toString());
    }
  }

  void _showErrorSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(message),
      backgroundColor: Colors.red,
    ));
  }
}
