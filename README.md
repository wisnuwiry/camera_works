# Camera Works

A camera plugin for flutter, which use CameraX on Android, native API on iOS, supports camera capture, flash, & switch camera.

*Note*: This plugin is inspired by the official [camera](https://pub.dev/packages/camera) 
## Features

- [x] Switch camera front & back
- [x] Take Picture
- [x] Handle Flash

## Getting Started

Add `camera_works` as a [dependency in your pubspec.yaml file](https://flutter.dev/using-packages/).

```
dependencies:
  camera_works: ^<latest-version>
```

### Android

Make sure you have a `miniSdkVersion` with 21 or higher in your `android/app/build.gradle` file, because the camera2 API which CameraX used only support Android 5.0 or above.

*Note*: You can run the example on a device emulator with Android 11 or higher and physical devices, CameraX doesn't work when running on emulators with Android 10 or lower. See https://developer.android.google.cn/codelabs/camerax-getting-started#5

## Issues

- Doesn't work with horizontal orientation.
- No Unit tests for now.