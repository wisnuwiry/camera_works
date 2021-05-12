import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'exceptions/exceptions.dart';

Size toSize(Map<dynamic, dynamic> data) {
  final width = data['width'];
  final height = data['height'];
  return Size(width, height);
}

extension CameraExceptionX on PlatformException {
  CameraException toCameraException() {
    return CameraException(
      code: code,
      message: message ?? '',
      details: details,
      stacktrace: stacktrace,
    );
  }
}
