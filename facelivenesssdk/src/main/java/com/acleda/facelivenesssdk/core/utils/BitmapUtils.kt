package com.acleda.facelivenesssdk.core.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log

/**
 * Utility functions for bitmap processing and manipulation
 */
object BitmapUtils {
    private const val TAG = "BitmapUtils"

    /**
     * Constants for validation
     */
    const val MIN_BITMAP_SIZE = 64 // Minimum size in pixels
    const val MAX_BITMAP_SIZE = 4096 // Maximum size in pixels

    /**
     * Validates the input bitmap
     *
     * @param bitmap Image to validate
     * @return true if valid, false otherwise
     */
    fun validateBitmap(bitmap: Bitmap?): Boolean {
        if (bitmap == null) {
            Log.e(TAG, "Input bitmap is null")
            return false
        }

        if (bitmap.width <= MIN_BITMAP_SIZE || bitmap.height <= MIN_BITMAP_SIZE) {
            Log.e(TAG, "Bitmap too small: ${bitmap.width}x${bitmap.height}")
            return false
        }

        if (bitmap.width >= MAX_BITMAP_SIZE || bitmap.height >= MAX_BITMAP_SIZE) {
            Log.e(TAG, "Bitmap too large: ${bitmap.width}x${bitmap.height}")
            return false
        }

        if (bitmap.isRecycled) {
            Log.e(TAG, "Bitmap is recycled")
            return false
        }

        return true
    }

    /**
     * Resizes a bitmap to the specified dimensions
     *
     * @param bitmap Source bitmap
     * @param width Target width
     * @param height Target height
     * @return Resized bitmap
     */
    fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) {
            return bitmap
        }

        val resizedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resizedBitmap)
        val paint = Paint().apply { isFilterBitmap = true }

        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            Rect(0, 0, width, height),
            paint
        )

        return resizedBitmap
    }

    /**
     * Calculate average brightness of an image
     *
     * @param bitmap Image to analyze
     * @return Average brightness value (0-255)
     */
    fun calculateAverageBrightness(bitmap: Bitmap): Float {
        var total = 0L
        val w = bitmap.width
        val h = bitmap.height

        // Adaptive sampling for better performance
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

        return total.toFloat() / count
    }
}