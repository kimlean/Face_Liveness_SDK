/**
 * FaceLivenessSDK - Android face liveness detection library
 *
 * This SDK provides tools for verifying that a face captured in an image
 * belongs to a live person rather than a presentation attack (spoofing attempt).
 *
 * Key features:
 * - Face liveness detection using computer vision and deep learning
 * - Face occlusion detection (masks, hands over face)
 * - Image quality assessment
 * - Fully configurable settings and thresholds
 *
 * Basic usage:
 * ```
 * // Initialize SDK
 * val sdk = FaceLivenessSDK.create(context)
 *
 * // Analyze an image
 * lifecycleScope.launch {
 *   val result = sdk.detectLiveness(bitmap)
 *   if (result.prediction == "Live") {
 *     // Authentication successful
 *   }
 * }
 *
 * // Release resources when done
 * sdk.close()
 * ```
 *
 * @author ACLEDA
 * @version 1.0.0
 */
package com.acleda.facelivenesssdk

/**
 * Main entry point for face liveness detection functionality.
 *
 * This class provides methods for performing face liveness checks,
 * detecting face occlusions, and assessing image quality. It manages
 * all underlying resources and ML models.
 *
 * Instances should be created using the factory methods [create]
 * and closed with [close] when no longer needed.
 *
 * @property context Application context used for accessing resources
 * @property config Configuration settings for the SDK
 * @constructor Create private instance - use factory methods instead
 */
class FaceLivenessSDK private constructor(
    private val context: Context,
    private val config: Config
) : AutoCloseable {
    // Implementation details...

    /**
     * Configuration class for SDK settings.
     *
     * Controls behavior such as debug logging, quality checks,
     * and occlusion detection.
     *
     * @property enableDebugLogging Whether to output detailed logs
     * @property skipQualityCheck Whether to bypass image quality verification
     * @property skipOcclusionCheck Whether to bypass face occlusion detection
     */
    class Config private constructor(
        val enableDebugLogging: Boolean,
        val skipQualityCheck: Boolean,
        val skipOcclusionCheck: Boolean
    ) {
        /**
         * Builder for creating SDK configuration.
         *
         * Example:
         * ```
         * val config = FaceLivenessSDK.Config.Builder()
         *     .setDebugLoggingEnabled(true)
         *     .setSkipQualityCheck(false)
         *     .build()
         * ```
         */
        class Builder {
            // Implementation details...

            /**
             * Enable or disable detailed debug logging.
             *
             * @param enabled True to enable debug logs, false to disable
             * @return This builder for chaining
             */
            fun setDebugLoggingEnabled(enabled: Boolean): Builder = apply {
                // Implementation details...
            }

            /**
             * Skip image quality checks (not recommended for production).
             *
             * @param skip True to skip quality checks, false to perform them
             * @return This builder for chaining
             */
            fun setSkipQualityCheck(skip: Boolean): Builder = apply {
                // Implementation details...
            }

            /**
             * Skip face occlusion checks (not recommended for production).
             *
             * @param skip True to skip occlusion checks, false to perform them
             * @return This builder for chaining
             */
            fun setSkipOcclusionCheck(skip: Boolean): Builder = apply {
                // Implementation details...
            }

            /**
             * Build the configuration object.
             *
             * @return Configured [Config] instance
             */
            fun build(): Config {
                // Implementation details...
            }
        }
    }

    companion object {
        /**
         * Create a new SDK instance with default configuration.
         *
         * @param context Application context
         * @return Configured SDK instance
         */
        fun create(context: Context): FaceLivenessSDK {
            // Implementation details...
        }

        /**
         * Create a new SDK instance with custom configuration.
         *
         * @param context Application context
         * @param config Custom configuration
         * @return Configured SDK instance
         */
        fun create(context: Context, config: Config): FaceLivenessSDK {
            // Implementation details...
        }
    }

    /**
     * Perform face liveness detection on the provided image.
     *
     * This method runs a complete authentication pipeline including:
     * 1. Image validation
     * 2. Face occlusion detection (optional)
     * 3. Image quality assessment (optional)
     * 4. Liveness detection
     *
     * The operation may take several hundred milliseconds and should be
     * called from a background thread or coroutine.
     *
     * @param bitmap Image to analyze
     * @return Model containing detection results
     * @throws FaceLivenessException if detection fails
     * @throws InvalidImageException if the input image is invalid
     */
    suspend fun detectLiveness(bitmap: Bitmap): FaceLivenessModel {
        // Implementation details...
    }

    /**
     * Perform image quality check without liveness detection.
     *
     * Analyzes the image for properties like brightness, sharpness,
     * and face presence, without performing liveness checks.
     *
     * @param bitmap Image to check
     * @return Quality assessment results
     * @throws InvalidImageException if the input image is invalid
     */
    suspend fun checkImageQuality(bitmap: Bitmap): ImageQualityResult {
        // Implementation details...
    }

    /**
     * Get the SDK version.
     *
     * @return Version string in semantic versioning format (x.y.z)
     */
    fun getVersion(): String {
        // Implementation details...
    }

    /**
     * Release all resources used by the SDK.
     *
     * After calling this method, the SDK instance is no longer usable.
     * A new instance must be created if needed again.
     */
    override fun close() {
        // Implementation details...
    }
}