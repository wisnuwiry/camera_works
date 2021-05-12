package space.wisnuwiry.camera_works

import androidx.annotation.NonNull
import androidx.lifecycle.Lifecycle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel


/** CameraWorksPlugin */
class CameraWorksPlugin : FlutterPlugin, ActivityAware {
    private var flutter: FlutterPlugin.FlutterPluginBinding? = null
    private var binding: ActivityPluginBinding? = null
    private var handler: CameraWorksHandler? = null
    private var method: MethodChannel? = null
    private var event: EventChannel? = null

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        this.flutter = binding
        method = MethodChannel(binding.binaryMessenger, "space.wisnuwiry/camera_works/method")
        event = EventChannel(binding.binaryMessenger, "space.wisnuwiry/camera_works/event")
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        this.flutter = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.binding = binding
        handler = CameraWorksHandler(binding, flutter!!.textureRegistry)
        method!!.setMethodCallHandler(handler)
        event!!.setStreamHandler(handler)
        binding.addRequestPermissionsResultListener(handler!!)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        binding!!.removeRequestPermissionsResultListener(handler!!)
        event!!.setStreamHandler(null)
        method!!.setMethodCallHandler(null)
        event = null
        method = null
        handler = null
        binding = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }
}
