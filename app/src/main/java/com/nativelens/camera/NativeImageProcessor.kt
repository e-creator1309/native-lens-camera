package com.nativelens.camera

import android.graphics.Bitmap

/**
 * Thin JNI bridge into the native "nativelens" image-processing engine
 * (see app/src/main/cpp/native_lib.cpp). All heavy per-pixel work runs in C++;
 * this object only marshals data across the JNI boundary.
 */
object NativeImageProcessor {

    init {
        System.loadLibrary("nativelens")
    }

    /**
     * Applies color grading, an optional vignette, and an optional unsharp-mask sharpen
     * directly on [bitmap]'s pixels in native code. [bitmap] must be [Bitmap.Config.ARGB_8888]
     * and mutable.
     */
    external fun nativeApplyColorGrade(
        bitmap: Bitmap,
        filterType: Int,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float,
        vignette: Boolean,
        sharpen: Boolean
    )

    /** Computes a 256-bin luminance histogram from a raw YUV Y-plane byte array. */
    external fun nativeComputeLuminanceHistogram(yPlane: ByteArray, length: Int): IntArray

    /** Returns a human-readable native engine build string, shown in the UI. */
    external fun nativeGetEngineVersion(): String
}
