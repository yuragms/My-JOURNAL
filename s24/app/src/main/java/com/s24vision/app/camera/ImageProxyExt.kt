package com.s24vision.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/** Конвертация кадра CameraX (YUV_420_888) в Bitmap с учётом поворота. */
fun ImageProxy.toBitmapCompat(): Bitmap {
    val nv21 = yuv420ToNv21(this)
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), 90, out)
    val bytes = out.toByteArray()
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bmp
    val m = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

private fun yuv420ToNv21(image: ImageProxy): ByteArray {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    return nv21
}
