# NativeLens Camera

A camera app built with Jetpack Compose + CameraX for the UI/capture pipeline, backed by a
real native C++ image-processing engine (`libnativelens.so`) compiled via the Android NDK/CMake.

## What's actually native here

- `app/src/main/cpp/native_lib.cpp` — the C++ engine, built as a shared library (`nativelens`).
- Per-pixel color grading (brightness/contrast/saturation/warmth), a vignette, a sepia tone map,
  and a 3x3 unsharp-mask sharpen all run in C++ directly on the captured bitmap's native pixel
  buffer via the Android Bitmap NDK API (`android/bitmap.h`) — no per-pixel work happens in Kotlin.
- A live 256-bin luminance histogram is computed in C++ every camera frame straight from the
  YUV Y-plane (`nativeComputeLuminanceHistogram`), and rendered as an overlay in Compose.
- `NativeImageProcessor.kt` is a thin JNI bridge; it does no image math itself.

## Filters

Normal, Vivid, Warm, Cool, Mono, and Vintage — each maps to a set of native color-grade
parameters in `native_lib.cpp`. Vignette and a detail-enhance (sharpen) toggle can be combined
with any filter.

## Building

Requires JDK 17 and the Android NDK (version pinned in `app/build.gradle.kts`, currently
`26.1.10909125`) plus CMake `3.22.1`, both installable via `sdkmanager`.

```
./gradlew assembleDebug
```

CI (`.github/workflows/build.yml`) builds a debug APK on every push to `main` and uploads it
as a workflow artifact.

## Architecture

```
CameraScreen (Compose)  --preview/frames-->  CameraViewModel
                                                  |
                                                  v
                                   NativeImageProcessor (JNI bridge)
                                                  |
                                                  v
                                   native_lib.cpp  (libnativelens.so)
```

Photo capture: CameraX saves a JPEG -> decoded to a mutable `ARGB_8888` bitmap -> the native
engine grades/vignettes/sharpens the bitmap in place -> re-encoded JPEG saved to
`Pictures/NativeLens` via `MediaStore`.
