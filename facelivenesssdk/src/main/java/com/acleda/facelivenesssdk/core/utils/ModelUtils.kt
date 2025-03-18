package com.acleda.facelivenesssdk.core.utils

import android.content.Context
import android.util.Log
import com.acleda.facelivenesssdk.exceptions.ModelLoadingException
import java.io.File
import java.io.FileOutputStream

/**
 * Utility functions for ML model handling
 */
object ModelUtils {
    private const val TAG = "ModelUtils"

    /**
     * Load model from assets to cache directory for ONNX runtime
     *
     * @param context Application context
     * @param modelName Name of the model file in assets
     * @return File object pointing to the cached model
     * @throws ModelLoadingException if model loading fails
     */
    fun loadModelFromAssets(context: Context, modelName: String): File {
        try {
            val modelFile = File(context.cacheDir, modelName).apply {
                if (!exists()) {
                    Log.d(TAG, "Copying model $modelName from assets to cache")
                    context.assets.open(modelName).use { input ->
                        FileOutputStream(this).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            if (!modelFile.exists() || modelFile.length() == 0L) {
                throw ModelLoadingException("Failed to load model $modelName: File does not exist or is empty")
            }

            Log.d(TAG, "Model $modelName loaded successfully from: ${modelFile.absolutePath}")
            return modelFile
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model $modelName: ${e.message}", e)

            // Try to delete the cached model file as it might be corrupted
            try {
                File(context.cacheDir, modelName).delete()
                Log.d(TAG, "Deleted potentially corrupted model file")
            } catch (deleteEx: Exception) {
                Log.e(TAG, "Failed to delete model file", deleteEx)
            }

            throw ModelLoadingException("Failed to load model $modelName: ${e.message}", e)
        }
    }

    /**
     * Load model directly from assets as a byte array for ONNX runtime
     *
     * @param context Application context
     * @param modelName Name of the model file in assets
     * @return ByteArray containing the model data
     * @throws ModelLoadingException if model loading fails
     */
    fun loadModelBytesFromAssets(context: Context, modelName: String): ByteArray {
        try {
            return context.assets.open(modelName).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model bytes for $modelName: ${e.message}", e)
            throw ModelLoadingException("Failed to load model bytes for $modelName: ${e.message}", e)
        }
    }
}