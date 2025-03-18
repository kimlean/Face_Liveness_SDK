package com.acleda.facelivenesssdk.models

/**
 * Represents the result of the face liveness detection process
 */
data class FaceLivenessModel(
    /**
     * The prediction result: "Live" or "Spoof"
     */
    val prediction: String,

    /**
     * Confidence level in the prediction (0.0 to 1.0)
     */
    val confidence: Float,

    /**
     * The quality assessment results for the image
     */
    val qualityResult: ImageQualityResult,

    /**
     * Reason for failure if authentication failed
     */
    val failureReason: String? = null
)