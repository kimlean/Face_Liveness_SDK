package com.acleda.facelivenesssdk.core.quality

import android.graphics.Bitmap
import android.graphics.Color
import com.acleda.facelivenesssdk.core.utils.LogUtils
import com.acleda.facelivenesssdk.exceptions.QualityCheckException
import com.acleda.facelivenesssdk.models.ImageQualityResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Checks the quality of an image for face authentication
 */
class ImageQualityChecker : AutoCloseable {
    private val TAG = "ImageQualityChecker"

    // Constants for brightness thresholds
    companion object {
        private const val BRIGHTNESS_TOO_DARK = 40f
        private const val BRIGHTNESS_SOMEWHAT_DARK = 80f
        private const val BRIGHTNESS_GOOD_UPPER = 180f
        private const val BRIGHTNESS_SOMEWHAT_BRIGHT = 220f

        private const val SHARPNESS_VERY_BLURRY = 5f
        private const val SHARPNESS_SOMEWHAT_BLURRY = 10f
        private const val SHARPNESS_GOOD_UPPER = 50f
        private const val SHARPNESS_TOO_DETAILED = 100f
    }

    // Initialize face detector from ML Kit
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )

    /**
     * Check image quality metrics and face presence
     *
     * @param bitmap The bitmap to analyze
     * @return ImageQualityResult containing quality metrics
     * @throws QualityCheckException if quality check fails
     */
    suspend fun checkImageQuality(bitmap: Bitmap): ImageQualityResult = withContext(Dispatchers.Default) {
        LogUtils.d(TAG, "Checking image quality for bitmap: ${bitmap.width}x${bitmap.height}")

        // Validate bitmap
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            LogUtils.e(TAG, "Invalid bitmap provided")
            throw QualityCheckException("Invalid bitmap provided")
        }

        val result = ImageQualityResult().apply {
            brightnessScore = checkBrightness(bitmap)
            sharpnessScore = checkSharpness(bitmap)
        }

        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            suspendCoroutine<Unit> { cont ->
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isEmpty()) {
                            result.hasFace = false
                            result.faceScore = 0.0f
                            LogUtils.d(TAG, "No faces detected in the image")
                        } else {
                            result.hasFace = true
                            result.faceScore = 1.0f
                            LogUtils.d(TAG, "Faces detected: ${faces.size}")
                        }
                        result.calculateOverallScore()
                        cont.resume(Unit)
                    }
                    .addOnFailureListener { e ->
                        LogUtils.e(TAG, "Face detection failed", e)
                        result.hasFace = false
                        result.faceScore = 0.0f
                        result.calculateOverallScore()
                        cont.resume(Unit)
                    }
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error in quality check: ${e.message}", e)
            throw QualityCheckException("Error in quality check: ${e.message}", e)
        }

        return@withContext result
    }

    /**
     * Check image brightness - optimized with adaptive sampling
     *
     * @param bitmap The bitmap to analyze
     * @return Brightness score between 0.0 and 1.0
     */
    private fun checkBrightness(bitmap: Bitmap): Float {
        var total = 0L
        val w = bitmap.width
        val h = bitmap.height

        // Adaptive sampling - more pixels for smaller images, fewer for larger ones
        val stepSize = maxOf(1, minOf(w, h) / 50)
        var count = 0

        for (y in 0 until h step stepSize) {
            for (x in 0 until w step stepSize) {
                val p = bitmap.getPixel(x, y)
                // Calculate perceived brightness using standard luminance formula
                val brightness = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
                total += brightness
                count++
            }
        }

        val avg = total.toFloat() / count

        // Convert brightness to a score using our defined constants
        return when {
            avg < BRIGHTNESS_TOO_DARK -> avg / BRIGHTNESS_TOO_DARK  // Too dark
            avg < BRIGHTNESS_SOMEWHAT_DARK -> 0.5f + (avg - BRIGHTNESS_TOO_DARK) / 80  // Somewhat dark
            avg < BRIGHTNESS_GOOD_UPPER -> 1.0f  // Good brightness
            avg < BRIGHTNESS_SOMEWHAT_BRIGHT -> 1.0f - (avg - BRIGHTNESS_GOOD_UPPER) / 80  // Somewhat bright
            else -> 0.5f - (avg - BRIGHTNESS_SOMEWHAT_BRIGHT) / 70  // Too bright
        }.coerceIn(0.0f, 1.0f)  // Ensure result is between 0 and 1
    }

    /**
     * Check image sharpness by calculating gradients - optimized with adaptive sampling
     *
     * @param bitmap The bitmap to analyze
     * @return Sharpness score between 0.0 and 1.0
     */
    private fun checkSharpness(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height

        // Skip processing for very small images
        if (w < 10 || h < 10) {
            LogUtils.w(TAG, "Image too small for reliable sharpness calculation")
            return 0.5f
        }

        // Adaptive sampling rate
        val stepSize = maxOf(1, minOf(w, h) / 40)

        var edgeSum = 0L
        var count = 0

        // Sample pixels at intervals to improve performance
        for (y in 2 until h - 2 step stepSize) {
            for (x in 2 until w - 2 step stepSize) {
                // Calculate horizontal and vertical gradients
                val gx = gradientX(bitmap, x, y)
                val gy = gradientY(bitmap, x, y)

                // Calculate magnitude of gradient
                val grad = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt()
                edgeSum += grad
                count++
            }
        }

        // Avoid division by zero
        if (count == 0) return 0.5f

        val avgGrad = edgeSum.toFloat() / count

        // Convert average gradient to a score using our constants
        return when {
            avgGrad < SHARPNESS_VERY_BLURRY -> avgGrad / SHARPNESS_VERY_BLURRY  // Very blurry
            avgGrad < SHARPNESS_SOMEWHAT_BLURRY -> 0.5f + (avgGrad - SHARPNESS_VERY_BLURRY) / 10  // Somewhat blurry
            avgGrad < SHARPNESS_GOOD_UPPER -> 1.0f  // Good sharpness
            avgGrad < SHARPNESS_TOO_DETAILED -> 1.0f - (avgGrad - SHARPNESS_GOOD_UPPER) / 100  // Too much detail/noise
            else -> 0.5f  // Extremely noisy or artificially sharpened
        }.coerceIn(0.0f, 1.0f)  // Ensure result is between 0 and 1
    }

    /**
     * Calculate horizontal gradient (Sobel operator X direction)
     */
    private fun gradientX(b: Bitmap, x: Int, y: Int): Int {
        val p1 = gray(b, x - 1, y - 1)
        val p2 = gray(b, x - 1, y)
        val p3 = gray(b, x - 1, y + 1)
        val p4 = gray(b, x + 1, y - 1)
        val p5 = gray(b, x + 1, y)
        val p6 = gray(b, x + 1, y + 1)
        return p4 + 2 * p5 + p6 - p1 - 2 * p2 - p3
    }

    /**
     * Calculate vertical gradient (Sobel operator Y direction)
     */
    private fun gradientY(b: Bitmap, x: Int, y: Int): Int {
        val p1 = gray(b, x - 1, y - 1)
        val p2 = gray(b, x, y - 1)
        val p3 = gray(b, x + 1, y - 1)
        val p4 = gray(b, x - 1, y + 1)
        val p5 = gray(b, x, y + 1)
        val p6 = gray(b, x + 1, y + 1)
        return p4 + 2 * p5 + p6 - p1 - 2 * p2 - p3
    }

    /**
     * Convert a pixel to grayscale
     */
    private fun gray(b: Bitmap, x: Int, y: Int): Int {
        // Ensure coordinates are within bitmap bounds
        val safeX = x.coerceIn(0, b.width - 1)
        val safeY = y.coerceIn(0, b.height - 1)

        val p = b.getPixel(safeX, safeY)
        // Convert RGB to grayscale using standard luminance formula
        return (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
    }

    /**
     * Properly close resources when done
     */
    override fun close() {
        try {
            detector.close()
            LogUtils.d(TAG, "ImageQualityChecker resources released")
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error closing detector", e)
        }
    }
}