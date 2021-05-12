package space.wisnuwiry.camera_works

import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import io.flutter.BuildConfig

val Any.TAG: String
    get() = javaClass.simpleName

val Camera.torchable: Boolean
    get() = cameraInfo.hasFlashUnit()

val ImageProxy.yuv: ByteArray
    get() {
        val ySize = y.buffer.remaining()
        val uSize = u.buffer.remaining()
        val vSize = v.buffer.remaining()

        val size = ySize + uSize + vSize
        val data = ByteArray(size)

        var offset = 0
        y.buffer.get(data, offset, ySize)
        offset += ySize
        u.buffer.get(data, offset, uSize)
        offset += uSize
        v.buffer.get(data, offset, vSize)

        return data
    }

val ImageProxy.nv21: ByteArray
    get() {
        if (BuildConfig.DEBUG) {
            if (y.pixelStride != 1 || u.rowStride != v.rowStride || u.pixelStride != v.pixelStride) {
                error("Assertion failed")
            }
        }

        val ySize = width * height
        val uvSize = ySize / 2
        val size = ySize + uvSize
        val data = ByteArray(size)

        var offset = 0
        // Y Plane
        if (y.rowStride == width) {
            y.buffer.get(data, offset, ySize)
            offset += ySize
        } else {
            for (row in 0 until height) {
                y.buffer.get(data, offset, width)
                offset += width
            }

            if (BuildConfig.DEBUG && offset != ySize) {
                error("Assertion failed")
            }
        }
        // U,V Planes
        if (v.rowStride == width && v.pixelStride == 2) {
            if (BuildConfig.DEBUG && v.size != uvSize - 1) {
                error("Assertion failed")
            }

            v.buffer.get(data, offset, 1)
            offset += 1
            u.buffer.get(data, offset, u.size)
            if (BuildConfig.DEBUG) {
                val value = v.buffer.get()
                if (data[offset] != value) {
                    error("Assertion failed")
                }
            }
        } else {
            for (row in 0 until height / 2)
                for (col in 0 until width / 2) {
                    val index = row * v.rowStride + col * v.pixelStride
                    data[offset++] = v.buffer.get(index)
                    data[offset++] = u.buffer.get(index)
                }

            if (BuildConfig.DEBUG && offset != size) {
                error("Assertion failed")
            }
        }

        return data
    }

val ImageProxy.PlaneProxy.size
    get() = buffer.remaining()

val ImageProxy.y: ImageProxy.PlaneProxy
    get() = planes[0]

val ImageProxy.u: ImageProxy.PlaneProxy
    get() = planes[1]

val ImageProxy.v: ImageProxy.PlaneProxy
    get() = planes[2]
