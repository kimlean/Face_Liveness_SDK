package com.acleda.facelivenesssdk.exceptions

/**
 * Base exception class for all SDK exceptions
 */
open class FaceLivenessException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when there are issues loading ML models
 */
class ModelLoadingException(message: String, cause: Throwable? = null) :
    FaceLivenessException(message, cause)

/**
 * Exception thrown when the input image is invalid
 */
class InvalidImageException(message: String) : FaceLivenessException(message)

/**
 * Exception thrown when face detection fails
 */
class FaceDetectionException(message: String, cause: Throwable? = null) :
    FaceLivenessException(message, cause)

/**
 * Exception thrown when liveness detection fails
 */
class LivenessException(message: String, cause: Throwable? = null) :
    FaceLivenessException(message, cause)

/**
 * Exception thrown when occlusion detection fails
 */
class OcclusionDetectionException(message: String, cause: Throwable? = null) :
    FaceLivenessException(message, cause)

/**
 * Exception thrown when quality check fails
 */
class QualityCheckException(message: String, cause: Throwable? = null) :
    FaceLivenessException(message, cause)