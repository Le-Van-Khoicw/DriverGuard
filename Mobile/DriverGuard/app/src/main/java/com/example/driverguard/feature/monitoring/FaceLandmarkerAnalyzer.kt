package com.example.driverguard.feature.auth.monitoring

import android.content.Context
import android.graphics.Matrix
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.driverguard.feature.monitoring.ai.EarCalculator
import com.example.driverguard.feature.monitoring.ai.Point2D
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker

class FaceLandmarkerAnalyzer(
    context: Context,
    private val onEar: (Float?) -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val landmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
    )

    override fun analyze(image: ImageProxy) {
        try {
            val width = image.width
            val height = image.height
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride

            val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            if (rowStride == width * 4) {
                buffer.rewind()
                source.copyPixelsFromBuffer(buffer)
            } else {
                // Buffer may contain extra padding bytes at the end of each row.
                // We create a clean direct buffer of size width * height * 4 and copy row by row.
                val cleanBuffer = java.nio.ByteBuffer.allocateDirect(width * height * 4)
                val rowBytes = width * 4
                val tempRow = ByteArray(rowBytes)
                for (y in 0 until height) {
                    buffer.position(y * rowStride)
                    val bytesToRead = minOf(rowBytes, buffer.remaining())
                    buffer.get(tempRow, 0, bytesToRead)
                    cleanBuffer.put(tempRow, 0, bytesToRead)
                }
                cleanBuffer.rewind()
                source.copyPixelsFromBuffer(cleanBuffer)
            }

            val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
            val rotated = android.graphics.Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            val result = landmarker.detect(BitmapImageBuilder(rotated).build())
            val face = result.faceLandmarks().firstOrNull()
            if (face == null) {
                onEar(null)
            } else {
                fun points(indices: IntArray) = indices.map { index ->
                    face[index].let { Point2D(it.x(), it.y()) }
                }
                onEar(EarCalculator.average(points(EarCalculator.LEFT_EYE), points(EarCalculator.RIGHT_EYE)))
            }
        } catch (error: Exception) {
            onError(error.message ?: "Không phân tích được khung hình")
        } finally {
            image.close()
        }
    }

    override fun close() = landmarker.close()
}
