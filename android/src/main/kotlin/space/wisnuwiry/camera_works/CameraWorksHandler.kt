package space.wisnuwiry.camera_works

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.annotation.NonNull
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.Metadata
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.TextureRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

class CameraWorksHandler(private val binding: ActivityPluginBinding, private val textureRegistry: TextureRegistry)
    : MethodChannel.MethodCallHandler, EventChannel.StreamHandler, PluginRegistry.RequestPermissionsResultListener {

    private var sink: EventChannel.EventSink? = null
    private var listener: PluginRegistry.RequestPermissionsResultListener? = null
    private lateinit var outputDirectory: File
    private lateinit var executor: Executor

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var preview: Preview? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var imageCapture: ImageCapture? = null

    companion object {
        private const val ERROR_CODE = "CAMERA_ERROR"
        private const val REQUEST_CODE = 19930430

        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
                File(baseFolder, SimpleDateFormat(format, Locale.US)
                        .format(System.currentTimeMillis()) + extension)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        when (call.method) {
            "state" -> getState(result)
            "requestPermission" -> requestPermission(result)
            "start" -> start(call, result)
            "setFlash" -> setFlash(call, result)
            "hasBackCamera" -> result.success(hasBackCamera())
            "hasFrontCamera" -> result.success(hasFrontCamera())
            "switchCamera" -> switchCamera(call, result)
            "stop" -> stop(result)
            "takePicture" -> takePicture(result)
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        this.sink = events
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?): Boolean {
        return listener?.onRequestPermissionsResult(requestCode, permissions, grantResults) ?: false
    }

    private fun getState(result: MethodChannel.Result) {
        // Can't get exact denied or not_determined state without request. Just return not_determined when state isn't authorized
        val state =
                if (ContextCompat.checkSelfPermission(binding.activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) 1
                else 0
        result.success(state)
    }

    private fun requestPermission(result: MethodChannel.Result) {
        listener = PluginRegistry.RequestPermissionsResultListener { requestCode, _, grantResults ->
            if (requestCode != REQUEST_CODE) {
                false
            } else {
                val authorized = grantResults[0] == PackageManager.PERMISSION_GRANTED
                result.success(authorized)
                listener = null
                true
            }
        }
        val permissions = arrayOf(Manifest.permission.CAMERA)
        ActivityCompat.requestPermissions(binding.activity, permissions, REQUEST_CODE)
    }

    private fun start(call: MethodCall, result: MethodChannel.Result) {
        val future = ProcessCameraProvider.getInstance(binding.activity)
        executor = ContextCompat.getMainExecutor(binding.activity)
        outputDirectory = getOutputDirectory(binding.activity.applicationContext)

        future.addListener(Runnable {
            try {
                cameraProvider = future.get()
                textureEntry = textureRegistry.createSurfaceTexture()
                val textureId = textureEntry!!.id()

                if (call.arguments is Int) {
                    selectLens(call.arguments as Int, true)
                }

                bindCameraUseCases()

                // TODO: seems there's not a better way to get the final resolution
                @SuppressLint("RestrictedApi")
                val resolution = preview!!.attachedSurfaceResolution!!
                val portrait = camera!!.cameraInfo.sensorRotationDegrees % 180 == 0
                val width = resolution.width.toDouble()
                val height = resolution.height.toDouble()
                val size = if (portrait) mapOf("width" to width, "height" to height) else mapOf("width" to height, "height" to width)
                val answer = mapOf("textureId" to textureId, "size" to size, "hasFlash" to camera!!.torchable)
                result.success(answer)

            } catch (e: IllegalStateException) {
                Log.e(TAG, e.message ?: "")
                result.error(ERROR_CODE, e.message, e.stackTrace)
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "")
                result.error(ERROR_CODE, e.message, e.stackTrace)
            }
        }, executor)
    }


    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {
        val resultListener: Consumer<SurfaceRequest.Result> = Consumer<SurfaceRequest.Result> { }

        // Preview
        val surfaceProvider = Preview.SurfaceProvider { request ->
            val resolution = request.resolution
            val texture = textureEntry!!.surfaceTexture()
            texture.setDefaultBufferSize(resolution.width, resolution.height)
            val surface = Surface(texture)
            request.provideSurface(surface, executor, resultListener)
        }

        preview = Preview.Builder().build().apply { setSurfaceProvider(surfaceProvider) }

        // ImageCapture
        imageCapture = ImageCapture.Builder()
                // .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                // We request aspect ratio but no resolution to match preview config, but letting
                // CameraX optimize for whatever specific resolution best fits our use cases
                // .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                // .setTargetRotation(rotation)
                .build()

        // Analyzer
        val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

        // Bind to lifecycle.
        val owner: LifecycleOwner = binding.activity as LifecycleOwner

        if (!hasBackCamera() && !hasFrontCamera()) {
            throw IllegalStateException("Back and front camera are unavailable")
        }

        val selector =
                CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Must unbind the use-cases before rebinding them
        cameraProvider!!.unbindAll()

        camera = cameraProvider!!.bindToLifecycle(owner, selector, preview, imageCapture, analysis)

        val observer: Observer<Int> = Observer<Int> { state -> // TorchState.OFF = 0; TorchState.ON = 1
            val event = mapOf("name" to "flashState", "data" to state)
            sink?.success(event)

        }
        camera!!.cameraInfo.torchState.observe(owner, observer)
    }

    private fun setFlash(call: MethodCall, result: MethodChannel.Result) {
        val state = call.arguments == 1
        camera!!.cameraControl.enableTorch(state)
        result.success(true)
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun switchCamera(call: MethodCall, result: MethodChannel.Result) {
        if (call.arguments is Int) {
            selectLens(call.arguments as Int, true)
            bindCameraUseCases()
            result.success(true)
        } else {
            Log.e(TAG, "Params Camera Id Is a Null")
            result.error(ERROR_CODE, "Params Camera Id Is a Null", "")
        }
    }

    private fun stop(result: MethodChannel.Result) {
        val owner = binding.activity as LifecycleOwner
        camera!!.cameraInfo.torchState.removeObservers(owner)
        cameraProvider!!.unbindAll()
        textureEntry!!.release()

        camera = null
        textureEntry = null
        cameraProvider = null
        imageCapture = null

        result.success(true)
    }

    private fun takePicture(result: MethodChannel.Result) {
        // Create output file to hold the image
        val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

        // Setup image capture metadata
        val metadata = Metadata().apply {
            // Mirror image when using the front camera
            isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(metadata)
                .build()

        imageCapture?.takePicture(
                outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                result.error(ERROR_CODE, "Photo capture failed: ${exc.message}", "")
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                Log.d(TAG, "Photo capture succeeded: $savedUri")

                result.success(savedUri.path)
            }
        })
    }

    /** Use app's file directory */
    private fun getOutputDirectory(context: Context): File {
        val appContext = context.applicationContext

        return appContext.filesDir
    }

    private fun selectLens(id: Int, withCheck: Boolean) {
        if (id in 0..1) {
            if (withCheck) {
                if (id == 0 && hasFrontCamera()) {
                    lensFacing = id
                } else if (id == 1 && hasBackCamera()) {
                    lensFacing = id
                }
                return
            }
            lensFacing = id
        }
    }
}