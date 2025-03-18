package com.acleda.facelivenesssdk

import android.content.Context
import android.graphics.Bitmap
import com.acleda.facelivenesssdk.core.detection.FaceOcclusionDetector
import com.acleda.facelivenesssdk.core.liveness.LivenessDetector
import com.acleda.facelivenesssdk.core.quality.ImageQualityChecker
import com.acleda.facelivenesssdk.core.utils.BitmapUtils
import com.acleda.facelivenesssdk.core.utils.LogUtils
import com.acleda.facelivenesssdk.exceptions.FaceLivenessException
import com.acleda.facelivenesssdk.exceptions.InvalidImageException
import com.acleda.facelivenesssdk.models.FaceLivenessModel
import com.acleda.facelivenesssdk.models.ImageQualityResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Main SDK class for face liveness detection
 */
class FaceLivenessSDK private constructor(
    private val context: Context,
    private val config: Config
) : AutoCloseable {
    private val TAG = "FaceLivenessSDK"

    // Configuration class for SDK initialization
    class Config private constructor(
        val enableDebugLogging: Boolean,
        val skipQualityCheck: Boolean,
        val skipOcclusionCheck: Boolean
    ) {
        /**
         * Builder class for SDK configuration
         */
        class Builder {
            private var enableDebugLogging = false
            private var skipQualityCheck = false
            private var skipOcclusionCheck = false

            /**
             * Enable detailed debug logs
             */
            fun setDebugLoggingEnabled(enabled: Boolean) = apply {
                this.enableDebugLogging = enabled
            }

            /**
             * Skip image quality checks (not recommended for production)
             */
            fun setSkipQualityCheck(skip: Boolean) = apply {
                this.skipQualityCheck = skip
            }

            /**
             * Skip face occlusion checks (not recommended for production)
             */
            fun setSkipOcclusionCheck(skip: Boolean) = apply {
                this.skipOcclusionCheck = skip
            }

            /**
             * Build the configuration
             */
            fun build(): Config {
                return Config(
                    enableDebugLogging = enableDebugLogging,
                    skipQualityCheck = skipQualityCheck,
                    skipOcclusionCheck = skipOcclusionCheck
                )
            }
        }
    }

    // Lazy initialization of components to improve startup time
    private val imageQualityChecker by lazy { ImageQualityChecker() }
    private val livenessDetector by lazy { LivenessDetector(context) }
    private val occlusionDetector by lazy { FaceOcclusionDetector(context) }

    // Companion factory methods
    companion object {
        /**
         * Create a new SDK instance with default configuration
         *
         * @param context Application context
         * @return Configured SDK instance
         */
        fun create(context: Context): FaceLivenessSDK {
            return create(context, Config.Builder().build())
        }

        /**
         * Create a new SDK instance with custom configuration
         *
         * @param context Application context
         * @param config Custom configuration
         * @return Configured SDK instance
         */
        fun create(context: Context, config: Config): FaceLivenessSDK {
            return FaceLivenessSDK(context.applicationContext, config)
        }
    }

    init {
        // Configure logging based on config
        LogUtils.setDebugEnabled(config.enableDebugLogging)
        LogUtils.i(TAG, "FaceLivenessSDK initialized with config: debugLogging=${config.enableDebugLogging}, " +
                "skipQualityCheck=${config.skipQualityCheck}, skipOcclusionCheck=${config.skipOcclusionCheck}")
    }

    /**
     * Full liveness detection process with occlusion check and quality check
     *
     * @param bitmap Image to analyze
     * @return FaceLivenessModel containing detection results
     * @throws FaceLivenessException if detection fails
     * @throws InvalidImageException if the input image is invalid
     */
    suspend fun detectLiveness(bitmap: Bitmap): FaceLivenessModel = coroutineScope {
        LogUtils.d(TAG, "Starting face liveness detection process")

        // Validate input
        if (!BitmapUtils.validateBitmap(bitmap)) {
            throw InvalidImageException("Invalid input image")
        }

        try {
            // Step 1: Occlusion check (if enabled)
            if (!config.skipOcclusionCheck) {
                val occlusionDeferred = async { occlusionDetector.detectFaceMask(bitmap) }
                val occlusionResult = occlusionDeferred.await()
                LogUtils.d(TAG, "Face occlusion check result: ${occlusionResult.label} with confidence ${occlusionResult.confidence}")

                // If face is occluded (not normal), return as spoof
                if (occlusionResult.label != "normal") {
                    LogUtils.d(TAG, "Face is occluded: ${occlusionResult.label}, skipping further checks")
                    return@coroutineScope FaceLivenessModel(
                        prediction = "Spoof",
                        confidence = occlusionResult.confidence,
                        qualityResult = ImageQualityResult.createDefault(),
                        failureReason = "Face is occluded: ${occlusionResult.label}"
                    )
                }
            } else {
                LogUtils.d(TAG, "Face occlusion check skipped as per configuration")
            }

            // Step 2: Quality check (if enabled)
            val qualityResult = if (!config.skipQualityCheck) {
                val qualityDeferred = async { imageQualityChecker.checkImageQuality(bitmap) }
                qualityDeferred.await()
            } else {
                LogUtils.d(TAG, "Image quality check skipped as per configuration")
                // Create a default "passing" quality result when checks are skipped
                ImageQualityResult().apply {
                    brightnessScore = 1.0f
                    sharpnessScore = 1.0f
                    faceScore = 1.0f
                    hasFace = true
                    calculateOverallScore()
                }
            }

            LogUtils.d(TAG, "Quality check result: $qualityResult")

            if (!config.skipQualityCheck && !qualityResult.isAcceptable()) {
                // Return result with quality failure
                LogUtils.d(TAG, "Image quality not acceptable, skipping liveness detection")
                return@coroutineScope FaceLivenessModel(
                    prediction = "Spoof",
                    confidence = 0.9f,
                    qualityResult = qualityResult,
                    failureReason = "Image quality not sufficient (Score: ${qualityResult.overallScore})"
                )
            }

            // Step 3: Perform liveness detection
            LogUtils.d(TAG, "Image quality acceptable, performing liveness detection")
            val detectionResult = livenessDetector.runInference(bitmap)

            val result = FaceLivenessModel(
                prediction = detectionResult.label,
                confidence = detectionResult.confidence,
                qualityResult = qualityResult
            )

            LogUtils.d(TAG, "Detection complete: $result")
            return@coroutineScope result

        } catch (e: Exception) {
            // Convert to SDK exception for consistent error handling
            LogUtils.e(TAG, "Error in liveness detection pipeline: ${e.message}", e)
            throw when (e) {
                is FaceLivenessException -> e
                else -> FaceLivenessException("SDK error: ${e.message}", e)
            }
        }
    }

    /**
     * Just check image quality without liveness detection
     *
     * @param bitmap Image to check
     * @return ImageQualityResult containing quality metrics
     * @throws InvalidImageException if the input image is invalid
     */
    suspend fun checkImageQuality(bitmap: Bitmap): ImageQualityResult {
        LogUtils.d(TAG, "Performing standalone image quality check")

        // Validate input
        if (!BitmapUtils.validateBitmap(bitmap)) {
            throw InvalidImageException("Invalid input image")
        }

        return imageQualityChecker.checkImageQuality(bitmap)
    }

    /**
     * Check SDK version
     *
     * @return Version string
     */
    fun getVersion(): String {
        return "1.0.0"
    }

    /**
     * Cleanup resources when SDK is no longer needed
     */
    override fun close() {
        try {
            LogUtils.d(TAG, "Closing FaceLivenessSDK resources")

            // Simply close the components without checking if they've been initialized.
            // If they haven't been accessed yet, the lazy properties won't be initialized
            // and no resources will have been allocated, so there's nothing to close.
            try {
                // Access the property only if we're going to use it
                if (!config.skipOcclusionCheck) {
                    occlusionDetector.let {
                        // This block only executes if occlusionDetector is initialized
                        it.close()
                    }
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error closing occlusionDetector: ${e.message}")
            }

            try {
                livenessDetector.let { it.close() }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error closing livenessDetector: ${e.message}")
            }

            try {
                if (!config.skipQualityCheck) {
                    imageQualityChecker.let { it.close() }
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error closing imageQualityChecker: ${e.message}")
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error closing resources: ${e.message}", e)
        }
    }
}