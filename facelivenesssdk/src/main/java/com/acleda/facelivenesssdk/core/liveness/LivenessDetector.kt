package com.acleda.facelivenesssdk.core.liveness

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.acleda.facelivenesssdk.core.utils.LogUtils
import com.acleda.facelivenesssdk.core.utils.ModelUtils
import com.acleda.facelivenesssdk.exceptions.LivenessException
import com.acleda.facelivenesssdk.models.DetectionResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * Handles face liveness detection using ONNX runtime
 */
class LivenessDetector(context: Context) : AutoCloseable {
    private val TAG = "LivenessDetector"

    // Constants
    companion object {
        private const val MODEL_PATH = "Liveliness.onnx"
        private const val INPUT_SIZE = 224
        private const val BATCH_SIZE = 1
        private const val CHANNELS = 3
        private const val LIVE_THRESHOLD = 0.5f
    }

    // ImageNet normalization values
    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    // Initialize ONNX environment and session once
    private val ortEnv = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String

    // Pre-allocated buffers for better performance
    private val inputBuffer =
        ByteBuffer.allocateDirect(BATCH_SIZE * CHANNELS * INPUT_SIZE * INPUT_SIZE * 4).apply {
            order(ByteOrder.nativeOrder())
        }
    private val floatArray = FloatArray(BATCH_SIZE * CHANNELS * INPUT_SIZE * INPUT_SIZE)

    // Reusable bitmap for resizing
    private val resizedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(resizedBitmap)
    private val paint = Paint().apply { isFilterBitmap = true }

    init {
        try {
            val sessionOptions = OrtSession.SessionOptions()
            val modelBytes = ModelUtils.loadModelBytesFromAssets(context, MODEL_PATH)
            session = ortEnv.createSession(modelBytes, sessionOptions)
            inputName = session.inputNames.iterator().next()
            LogUtils.d(TAG, "Liveness model loaded successfully with input name: $inputName")
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error initializing liveness model", e)
            throw LivenessException("Failed to load liveness model: ${e.message}", e)
        }
    }

    /**
     * Run face liveness detection on the provided bitmap
     *
     * @param bitmap The bitmap to analyze (should be a cropped face)
     * @return DetectionResult containing the result label ("Live" or "Spoof") and confidence
     * @throws LivenessException if detection fails
     */
    fun runInference(bitmap: Bitmap): DetectionResult {
        if (bitmap.isRecycled) {
            LogUtils.e(TAG, "Cannot process recycled bitmap")
            throw LivenessException("Cannot process recycled bitmap")
        }

        LogUtils.d(
            TAG,
            "Starting liveness inference on face image: ${bitmap.width}x${bitmap.height}"
        )

        // Prepare input
        preprocessImage(bitmap)

        // Convert to proper format for OnnxTensor creation
        inputBuffer.asFloatBuffer().get(floatArray)
        val shape = longArrayOf(
            BATCH_SIZE.toLong(),
            CHANNELS.toLong(),
            INPUT_SIZE.toLong(),
            INPUT_SIZE.toLong()
        )

        var inputTensor: OnnxTensor? = null

        try {
            // Create input tensor
            inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floatArray), shape)

            // Run model inference
            LogUtils.d(TAG, "Running model inference")
            val output = session.run(mapOf(inputName to inputTensor))
            val outputTensor = output[0] as OnnxTensor
            val outputValue = outputTensor.value

            // Extract logit value
            val logit = when (outputValue) {
                is FloatArray -> outputValue[0]
                is Array<*> -> (outputValue[0] as FloatArray)[0]
                else -> throw LivenessException("Unexpected output type: ${outputValue?.javaClass?.name}")
            }

            LogUtils.d(TAG, "Raw model output (logit): $logit")

            // Apply sigmoid to get confidence score
            val conf = 1.0f / (1.0f + exp(-logit).toFloat())
            LogUtils.d(TAG, "Confidence after sigmoid: $conf")

            // Apply threshold for classification
            val label = if (conf > LIVE_THRESHOLD) "Live" else "Spoof"

            // Adjust confidence display (showing confidence in the prediction)
            val displayConf = if (label == "Live") conf else 1f - conf
            LogUtils.d(TAG, "Final prediction: $label with display confidence: $displayConf")

            return DetectionResult(label, displayConf)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error during inference", e)
            throw LivenessException("Error during liveness detection: ${e.message}", e)
        } finally {
            // Cleanup resources
            inputTensor?.close()

            // Reset buffer for next use
            inputBuffer.rewind()
        }
    }

    /**
     * Preprocess the bitmap for model input - matches Python's preprocessing pipeline
     * Optimized to reuse buffers and minimize allocations
     *
     * @param bitmap The input bitmap to preprocess
     */
    private fun preprocessImage(bitmap: Bitmap) {
        // Clear the buffer before reuse
        inputBuffer.rewind()

        // Resize the bitmap if needed
        val sourceBitmap = if (bitmap.width != INPUT_SIZE || bitmap.height != INPUT_SIZE) {
            LogUtils.d(
                TAG,
                "Resizing bitmap from ${bitmap.width}x${bitmap.height} to ${INPUT_SIZE}x${INPUT_SIZE}"
            )
            // Draw the source bitmap onto our reusable bitmap
            canvas.drawBitmap(
                bitmap,
                Rect(0, 0, bitmap.width, bitmap.height),
                Rect(0, 0, INPUT_SIZE, INPUT_SIZE),
                paint
            )
            resizedBitmap
        } else {
            bitmap
        }

        // Get pixel values from the bitmap
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        sourceBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Process in NCHW format (batch, channels, height, width)
        // All RED channel values, then all GREEN channel values, then all BLUE channel values

        // Process RED channel
        for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f  // Normalize to [0,1]
            inputBuffer.putFloat((r - mean[0]) / std[0])     // Apply ImageNet normalization
        }

        // Process GREEN channel
        for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
            val pixel = pixels[i]
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            inputBuffer.putFloat((g - mean[1]) / std[1])
        }

        // Process BLUE channel
        for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
            val pixel = pixels[i]
            val b = (pixel and 0xFF) / 255.0f
            inputBuffer.putFloat((b - mean[2]) / std[2])
        }

        inputBuffer.rewind()
    }

    /**
     * Close and clean up resources
     */
    override fun close() {
        try {
            session.close()
            LogUtils.d(TAG, "LivenessDetector resources released")
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error closing session", e)
        }
    }
}