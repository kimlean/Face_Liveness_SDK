package com.acleda.facelivenesssdk.core.utils

import android.util.Log

/**
 * Utility class for consistent logging throughout the SDK
 */
object LogUtils {
    private const val SDK_TAG_PREFIX = "FaceSDK-"
    private var isDebugEnabled = false

    /**
     * Enable or disable debug logging
     *
     * @param enabled True to enable debug logs, false to disable
     */
    fun setDebugEnabled(enabled: Boolean) {
        isDebugEnabled = enabled
    }

    /**
     * Log a debug message
     *
     * @param tag Component tag
     * @param message Log message
     */
    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.d("$SDK_TAG_PREFIX$tag", message)
        }
    }

    /**
     * Log an info message
     *
     * @param tag Component tag
     * @param message Log message
     */
    fun i(tag: String, message: String) {
        Log.i("$SDK_TAG_PREFIX$tag", message)
    }

    /**
     * Log a warning message
     *
     * @param tag Component tag
     * @param message Log message
     */
    fun w(tag: String, message: String) {
        Log.w("$SDK_TAG_PREFIX$tag", message)
    }

    /**
     * Log an error message
     *
     * @param tag Component tag
     * @param message Log message
     */
    fun e(tag: String, message: String) {
        Log.e("$SDK_TAG_PREFIX$tag", message)
    }

    /**
     * Log an error message with exception
     *
     * @param tag Component tag
     * @param message Log message
     * @param throwable Exception
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e("$SDK_TAG_PREFIX$tag", message, throwable)
    }
}