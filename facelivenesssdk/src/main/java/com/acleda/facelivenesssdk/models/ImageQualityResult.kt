package com.acleda.facelivenesssdk.models

/**
 * Represents the result of image quality check
 */
class ImageQualityResult {
    var brightnessScore = 0.0f
    var sharpnessScore = 0.0f
    var faceScore = 0.0f
    var hasFace = false
    var overallScore = 0.0f

    // Weights for each component - made as constants for better maintainability
    companion object {
        const val BRIGHTNESS_WEIGHT = 0.3f
        const val SHARPNESS_WEIGHT = 0.3f
        const val FACE_WEIGHT = 0.4f

        // Minimum acceptable overall score
        const val ACCEPTABLE_SCORE_THRESHOLD = 0.5f

        /**
         * Create a default instance for cases where quality check is skipped
         */
        fun createDefault(): ImageQualityResult {
            return ImageQualityResult().apply {
                brightnessScore = 0.0f
                sharpnessScore = 0.0f
                faceScore = 0.0f
                hasFace = false  // Important: this will cause isAcceptable() to return false
                overallScore = 0.0f
            }
        }
    }

    /**
     * Calculates the overall score based on weighted components
     */
    fun calculateOverallScore() {
        overallScore = if (!hasFace) {
            0.0f
        } else {
            (brightnessScore * BRIGHTNESS_WEIGHT +
                    sharpnessScore * SHARPNESS_WEIGHT +
                    faceScore * FACE_WEIGHT)
        }.coerceIn(0.0f, 1.0f) // Ensure score is between 0 and 1
    }

    /**
     * Determines if the image quality is acceptable for further processing
     */
    fun isAcceptable(): Boolean = hasFace && overallScore >= ACCEPTABLE_SCORE_THRESHOLD

    /**
     * Get detailed breakdown of all component scores
     */
    fun getDetailedReport(): Map<String, Any> {
        return mapOf(
            "overallScore" to overallScore,
            "brightnessScore" to brightnessScore,
            "sharpnessScore" to sharpnessScore,
            "faceScore" to faceScore,
            "hasFace" to hasFace,
            "isAcceptable" to isAcceptable()
        )
    }

    override fun toString(): String {
        return "Quality: %.2f (Brightness: %.2f, Sharpness: %.2f, Face: %.2f)".format(
            overallScore, brightnessScore, sharpnessScore, faceScore
        )
    }
}