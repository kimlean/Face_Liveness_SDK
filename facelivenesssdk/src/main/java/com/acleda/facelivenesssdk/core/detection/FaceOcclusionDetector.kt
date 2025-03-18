package com.acleda.facelivenesssdk.core.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.acleda.facelivenesssdk.core.utils.LogUtils
import com.acleda.facelivenesssdk.core.utils.ModelUtils
import com.acleda.facelivenesssdk.exceptions.OcclusionDetectionException
import com.acleda.facelivenesssdk.models.DetectionResult
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Detects face occlusions such as masks or hands covering the face
 */
class FaceOcclusionDetector(private val context: Context) : AutoCloseable {
    // Constants
    companion object {
        private const val TAG = "FaceOcclusionDetector"
        private const val MODEL_NAME = "FaceOcclusion.onnx"
        private const val IMAGE_SIZE = 224
        private const val HAND_OVER_FACE_INDEX = 0
        private const val NORMAL_INDEX = 1
        private const val WITH_MASK_INDEX = 2
        private const val NORMAL_CONFIDENCE_THRESHOLD = 0.7f
    }

    // Class mapping
    private val classNames = mapOf(
        HAND_OVER_FACE_INDEX to "hand_over_face",
        NORMAL_INDEX to "normal",
        WITH_MASK_INDEX to "with_mask"
    )

    private var ortSession: OrtSession? = null
    private val ortEnvironment = OrtEnvironment.getEnvironment()

    // Flag to track if model loaded successfully
    private val isModelLoaded = AtomicBoolean(false)

    // Reusable bitmap for resizing
    private val resizedBitmap = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(resizedBitmap)
    private val paint = Paint().apply { isFilterBitmap = true }

    init {
        loadModel()
    }

    /**
     * Load the ONNX model from assets
     */
    private fun loadModel() {
        try {
            val modelFile = ModelUtils.loadModelFromAssets(context, MODEL_NAME)

            ortSession = ortEnvironment.createSession(
                modelFile.absolutePath,
                OrtSession.SessionOptions()
            )

            isModelLoaded.set(true)
            LogUtils.d(TAG, "Model loaded successfully from: ${modelFile.absolutePath}")
        } catch (e: Exception) {
            isModelLoaded.set(false)
            LogUtils.e(TAG, "Error loading model: ${e.message}", e)
        }
    }

    /**
     * Detect if face is occluded by mask or hand
     *
     * @param bitmap Image to analyze
     * @return DetectionResult containing class name and confidence
     * @throws OcclusionDetectionException if detection fails
     */
    fun detectFaceMask(bitmap: Bitmap): DetectionResult {
        // Validate input
        if (bitmap.isRecycled) {
            LogUtils.e(TAG, "Cannot process recycled bitmap")
            throw OcclusionDetectionException("Cannot process recycled bitmap")
        }

        // If model failed to load, return normal with low confidence
        // This allows the pipeline to continue instead of failing
        if (!isModelLoaded.get() || ortSession == null) {
            LogUtils.w(TAG, "Model not loaded, assuming normal face with low confidence")
            return DetectionResult("normal", 0.7f)
        }

        var inputTensor: OnnxTensor? = null

        try {
            // Resize the bitmap if needed
            if (bitmap.width != IMAGE_SIZE || bitmap.height != IMAGE_SIZE) {
                canvas.drawBitmap(
                    bitmap,
                    Rect(0, 0, bitmap.width, bitmap.height),
                    Rect(0, 0, IMAGE_SIZE, IMAGE_SIZE),
                    paint
                )
            } else {
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            }

            // Create input tensor
            inputTensor = preprocessImage(resizedBitmap)

            val session = ortSession ?: throw OcclusionDetectionException("Session is null")
            val inputName = session.inputNames.iterator().next()
            val result = session.run(mapOf(inputName to inputTensor))
            val output = result[0].value

            LogUtils.d(TAG, "Model output type: ${output.javaClass.name}")

            val probabilities = output as? Array<*>
                ?: throw OcclusionDetectionException("Invalid output format")

            val floatArray = probabilities[0] as? FloatArray
                ?: throw OcclusionDetectionException("Invalid array format")

            // Log all probabilities for debugging
            floatArray.forEachIndexed { index, prob ->
                LogUtils.d(TAG, "Class ${classNames[index] ?: "Unknown"}: $prob")
            }

            // Get highest probability class and confidence
            val maxEntry = floatArray.withIndex().maxByOrNull { it.value }
                ?: throw OcclusionDetectionException("No max probability found")

            val (maxIndex, maxProb) = maxEntry

            // Apply the custom condition:
            // If predicted class is "normal" but confidence < threshold,
            // choose either "with_mask" or "hand_over_face" based on highest probability
            if (maxIndex == NORMAL_INDEX && maxProb < NORMAL_CONFIDENCE_THRESHOLD) {
                LogUtils.d(TAG, "Normal class detected with low confidence: $maxProb, reassigning...")

                // Get the probabilities of the other two classes
                val maskProb = floatArray[WITH_MASK_INDEX]
                val handOverFaceProb = floatArray[HAND_OVER_FACE_INDEX]

                // Choose the class with higher probability between mask and hand over face
                return if (maskProb > handOverFaceProb) {
                    LogUtils.d(TAG, "Reassigned to with_mask with probability: $maskProb")
                    DetectionResult("with_mask", maskProb)
                } else {
                    LogUtils.d(TAG, "Reassigned to hand_over_face with probability: $handOverFaceProb")
                    DetectionResult("hand_over_face", handOverFaceProb)
                }
            }

            // Standard case - return the highest probability class
            return DetectionResult(classNames[maxIndex] ?: "Unknown", maxProb)

        } catch (e: Exception) {
            LogUtils.e(TAG, "Error during inference: ${e.message}", e)
            throw OcclusionDetectionException("Error during occlusion detection: ${e.message}", e)
        } finally {
            inputTensor?.close()
        }
    }

    /**
     * Preprocess image for model input
     *
     * @param bitmap Image to preprocess
     * @return OnnxTensor ready for inference
     */
    private fun preprocessImage(bitmap: Bitmap): OnnxTensor {
        val floatBuffer = FloatBuffer.allocate(1 * 3 * IMAGE_SIZE * IMAGE_SIZE)
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE).also {
            bitmap.getPixels(it, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
        }

        // Process in NCHW format (batch, channels, height, width)
        for (c in 0 until 3) {
            for (h in 0 until IMAGE_SIZE) {
                for (w in 0 until IMAGE_SIZE) {
                    val pixel = pixels[h * IMAGE_SIZE + w]
                    val value = when (c) {
                        0 -> ((pixel shr 16) and 0xFF) / 255.0f // R
                        1 -> ((pixel shr 8) and 0xFF) / 255.0f  // G
                        2 -> (pixel and 0xFF) / 255.0f          // B
                        else -> 0.0f
                    }
                    floatBuffer.put(value)
                }
            }
        }

        floatBuffer.rewind()
        return OnnxTensor.createTensor(
            ortEnvironment,
            floatBuffer,
            longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
        )
    }

    /**
     * Try to reload model if it failed to load initially
     *
     * @return true if model loaded successfully
     */
    fun reloadModel(): Boolean {
        if (isModelLoaded.get()) return true

        try {
            // Close any existing session
            ortSession?.close()

            // Reload the model
            loadModel()
            return isModelLoaded.get()
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to reload model: ${e.message}", e)
            return false
        }
    }

    /**
     * Close and release resources
     */
    override fun close() {
        try {
            ortSession?.close()
            ortSession = null
            LogUtils.d(TAG, "FaceOcclusionDetector resources released")
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error closing resources: ${e.message}", e)
        }
    }
}