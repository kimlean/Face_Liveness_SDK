# Face Liveness SDK for Android

This SDK provides face liveness detection capabilities for Android applications, helping verify that a face in an image is from a live person rather than a spoofing attempt (printed photo, screen display, mask, etc.).

## Features

- Face liveness detection using deep learning models
- Face occlusion detection (masks, hands covering face)
- Image quality assessment (brightness, sharpness, face presence)
- Configurable checks and validation
- Optimized for mobile performance

## Requirements

- Android API level 21+ (Android 5.0 Lollipop or higher)
- Kotlin-enabled project
- ML Kit and ONNX Runtime dependencies

## Installation

### Gradle

Add the AAR file to your project's `libs` folder and update your app-level `build.gradle`:

```gradle
dependencies {
    implementation files('libs/facelivenesssdk-release.aar')
    
    // Required dependencies
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'com.google.mlkit:face-detection:16.1.5'
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.15.1'
}
```

## Usage

### Initialization

```kotlin
// Create with default configuration
val sdk = FaceLivenessSDK.create(context)

// Or with custom configuration
val config = FaceLivenessSDK.Config.Builder()
    .setDebugLoggingEnabled(true)
    .build()
val sdk = FaceLivenessSDK.create(context, config)
```

### Performing Liveness Detection

```kotlin
// Must be called from a coroutine
lifecycleScope.launch {
    try {
        // Get bitmap from camera/gallery
        val bitmap: Bitmap = ...
        
        // Run detection
        val result = sdk.detectLiveness(bitmap)
        
        if (result.prediction == "Live") {
            // Authenticated successfully
            // result.confidence contains the confidence level (0.0-1.0)
        } else {
            // Spoofing attempt detected
            // result.failureReason may contain details about the failure
        }
    } catch (e: Exception) {
        // Handle errors
    }
}
```

### Image Quality Check Only

```kotlin
lifecycleScope.launch {
    try {
        val bitmap: Bitmap = ...
        val qualityResult = sdk.checkImageQuality(bitmap)
        
        if (qualityResult.isAcceptable()) {
            // Image quality is good enough for liveness detection
        } else {
            // Image quality issues - provide feedback to user
            val report = qualityResult.getDetailedReport()
            // Check specific issues: brightness, sharpness, etc.
        }
    } catch (e: Exception) {
        // Handle errors
    }
}
```

### Resource Cleanup

Always close the SDK when you're done to release resources:

```kotlin
override fun onDestroy() {
    sdk.close()
    super.onDestroy()
}
```

## Configuration Options

The SDK can be configured with various options:

```kotlin
val config = FaceLivenessSDK.Config.Builder()
    .setDebugLoggingEnabled(true)  // Enable detailed logging
    .setSkipQualityCheck(false)    // Perform quality checks (recommended)
    .setSkipOcclusionCheck(false)  // Perform occlusion checks (recommended)
    .build()
```

## Error Handling

The SDK uses the following exception classes:

- `FaceLivenessException`: Base exception class
- `InvalidImageException`: When the input image is invalid
- `ModelLoadingException`: Issues with loading ML models
- `FaceDetectionException`: Errors in face detection
- `LivenessException`: Errors in liveness detection
- `OcclusionDetectionException`: Errors in occlusion detection
- `QualityCheckException`: Errors in quality assessment

## Performance Considerations

- Image processing is done on the calling thread - use coroutines with Dispatchers.Default for background processing
- First initialization of the SDK may take a moment as models are loaded
- For best performance, maintain a single instance of the SDK throughout your app's lifecycle