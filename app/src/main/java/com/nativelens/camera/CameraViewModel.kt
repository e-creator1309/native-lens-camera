package com.nativelens.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CameraUiState(
    val selectedFilter: FilterPreset = FilterPreset.NORMAL,
    val vignetteEnabled: Boolean = false,
    val sharpenEnabled: Boolean = true,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val isCapturing: Boolean = false,
    val lastSavedMessage: String? = null,
    val engineVersion: String = ""
)

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        CameraUiState(engineVersion = NativeImageProcessor.nativeGetEngineVersion())
    )
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _histogram = MutableStateFlow(IntArray(256))
    val histogram: StateFlow<IntArray> = _histogram.asStateFlow()

    fun selectFilter(preset: FilterPreset) {
        _uiState.value = _uiState.value.copy(selectedFilter = preset)
    }

    fun toggleVignette() {
        _uiState.value = _uiState.value.copy(vignetteEnabled = !_uiState.value.vignetteEnabled)
    }

    fun toggleSharpen() {
        _uiState.value = _uiState.value.copy(sharpenEnabled = !_uiState.value.sharpenEnabled)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(lastSavedMessage = null)
    }

    /** Feeds each preview frame's Y-plane into the native histogram engine. */
    val histogramAnalyzer = ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
        val yPlane = imageProxy.planes[0]
        val buffer = yPlane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        _histogram.value = NativeImageProcessor.nativeComputeLuminanceHistogram(bytes, bytes.size)
        imageProxy.close()
    }

    fun capturePhoto(context: Context, imageCapture: ImageCapture) {
        _uiState.value = _uiState.value.copy(isCapturing = true)
        val name = "NativeLens_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cacheFile = File(context.cacheDir, "$name.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(cacheFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processAndSave(context, cacheFile, name)
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        lastSavedMessage = "Capture failed: ${exception.message}"
                    )
                }
            }
        )
    }

    private fun processAndSave(context: Context, sourceFile: File, name: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath)
            val bitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
            decoded.recycle()

            val state = _uiState.value
            NativeImageProcessor.nativeApplyColorGrade(
                bitmap,
                state.selectedFilter.id,
                state.brightness,
                state.contrast,
                state.saturation,
                state.warmth,
                state.vignetteEnabled,
                state.sharpenEnabled
            )

            val savedUri = saveToGallery(context, bitmap, name)
            bitmap.recycle()
            sourceFile.delete()

            _uiState.value = _uiState.value.copy(
                isCapturing = false,
                lastSavedMessage = if (savedUri != null) "Saved to Pictures/NativeLens" else "Save failed"
            )
        }
    }

    private fun saveToGallery(context: Context, bitmap: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NativeLens")
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        return uri
    }
}
