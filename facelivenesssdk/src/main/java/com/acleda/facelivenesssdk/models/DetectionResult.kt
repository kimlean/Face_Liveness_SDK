package com.acleda.facelivenesssdk.models

/**
 * Represents the result of a detection operation
 */
data class DetectionResult(
    /**
     * The prediction result label
     */
    val label: String,

    /**
     * Confidence level in the prediction (0.0 to 1.0)
     */
    val confidence: Float
)