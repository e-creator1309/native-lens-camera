#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstdint>
#include <cmath>
#include <cstring>
#include <vector>
#include <algorithm>

#define LOG_TAG "NativeLensEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

inline uint8_t clamp8(float v) {
    if (v < 0.0f) return 0;
    if (v > 255.0f) return 255;
    return static_cast<uint8_t>(v);
}

// Mirrors FilterPreset.kt's `id` values.
enum FilterType {
    FILTER_NORMAL = 0,
    FILTER_VIVID = 1,
    FILTER_WARM = 2,
    FILTER_COOL = 3,
    FILTER_MONO = 4,
    FILTER_VINTAGE = 5
};

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_nativelens_camera_NativeImageProcessor_nativeApplyColorGrade(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap,
        jint filterType,
        jfloat brightness,
        jfloat contrast,
        jfloat saturation,
        jfloat warmth,
        jboolean vignette,
        jboolean sharpen) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to get bitmap info");
        return;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format, expected RGBA_8888");
        return;
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to lock bitmap pixels");
        return;
    }

    const int width = static_cast<int>(info.width);
    const int height = static_cast<int>(info.height);
    const uint32_t stride = info.stride;

    float presetSaturation = saturation;
    float presetWarmth = warmth;
    float presetContrast = contrast;
    bool presetSepia = false;
    bool presetVignette = vignette;

    switch (filterType) {
        case FILTER_VIVID:
            presetSaturation *= 1.5f;
            presetContrast *= 1.15f;
            break;
        case FILTER_WARM:
            presetWarmth += 25.0f;
            break;
        case FILTER_COOL:
            presetWarmth -= 25.0f;
            break;
        case FILTER_MONO:
            presetSaturation = 0.0f;
            break;
        case FILTER_VINTAGE:
            presetSepia = true;
            presetContrast *= 0.9f;
            presetVignette = true;
            break;
        default:
            break;
    }

    // Pre-copy the original buffer so the unsharp mask's low-pass blur reads unmodified
    // neighboring pixels even as the main loop writes graded pixels in place.
    std::vector<uint8_t> original;
    if (sharpen) {
        original.resize(static_cast<size_t>(stride) * height);
        std::memcpy(original.data(), pixels, original.size());
    }

    const float cx = width / 2.0f;
    const float cy = height / 2.0f;
    const float maxDist = std::sqrt(cx * cx + cy * cy);

    for (int y = 0; y < height; ++y) {
        uint8_t *row = static_cast<uint8_t *>(pixels) + y * stride;
        for (int x = 0; x < width; ++x) {
            uint8_t *px = row + x * 4;
            float r = px[0];
            float g = px[1];
            float b = px[2];
            const uint8_t a = px[3];

            if (sharpen && x > 0 && x < width - 1 && y > 0 && y < height - 1) {
                float sr = 0.0f, sg = 0.0f, sb = 0.0f;
                for (int ky = -1; ky <= 1; ++ky) {
                    const uint8_t *brow = original.data() + (y + ky) * stride;
                    for (int kx = -1; kx <= 1; ++kx) {
                        const uint8_t *bp = brow + (x + kx) * 4;
                        sr += bp[0];
                        sg += bp[1];
                        sb += bp[2];
                    }
                }
                sr /= 9.0f;
                sg /= 9.0f;
                sb /= 9.0f;
                const float amount = 0.6f;
                r = r + (r - sr) * amount;
                g = g + (g - sg) * amount;
                b = b + (b - sb) * amount;
            }

            r = (r - 128.0f) * presetContrast + 128.0f + brightness;
            g = (g - 128.0f) * presetContrast + 128.0f + brightness;
            b = (b - 128.0f) * presetContrast + 128.0f + brightness;

            const float gray = 0.299f * r + 0.587f * g + 0.114f * b;
            r = gray + (r - gray) * presetSaturation;
            g = gray + (g - gray) * presetSaturation;
            b = gray + (b - gray) * presetSaturation;

            r += presetWarmth;
            b -= presetWarmth;

            if (presetSepia) {
                const float sr = r * 0.393f + g * 0.769f + b * 0.189f;
                const float sg = r * 0.349f + g * 0.686f + b * 0.168f;
                const float sb = r * 0.272f + g * 0.534f + b * 0.131f;
                r = sr;
                g = sg;
                b = sb;
            }

            if (presetVignette && maxDist > 0.0f) {
                const float dx = x - cx;
                const float dy = y - cy;
                const float dist = std::sqrt(dx * dx + dy * dy) / maxDist;
                const float falloff = 1.0f - 0.55f * dist * dist;
                r *= falloff;
                g *= falloff;
                b *= falloff;
            }

            px[0] = clamp8(r);
            px[1] = clamp8(g);
            px[2] = clamp8(b);
            px[3] = a;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_nativelens_camera_NativeImageProcessor_nativeComputeLuminanceHistogram(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray yPlane,
        jint length) {
    jint bins[256];
    std::memset(bins, 0, sizeof(bins));

    jbyte *data = env->GetByteArrayElements(yPlane, nullptr);
    if (data != nullptr) {
        const jint arrayLen = env->GetArrayLength(yPlane);
        const int count = std::min<jint>(length, arrayLen);
        for (int i = 0; i < count; ++i) {
            const uint8_t luma = static_cast<uint8_t>(data[i]);
            bins[luma]++;
        }
        env->ReleaseByteArrayElements(yPlane, data, JNI_ABORT);
    }

    jintArray result = env->NewIntArray(256);
    env->SetIntArrayRegion(result, 0, 256, bins);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nativelens_camera_NativeImageProcessor_nativeGetEngineVersion(
        JNIEnv *env,
        jobject /* this */) {
#if defined(__aarch64__)
    const char *version = "NativeLens Engine v1.0.0 (arm64-v8a, C++17)";
#elif defined(__arm__)
    const char *version = "NativeLens Engine v1.0.0 (armeabi-v7a, C++17)";
#elif defined(__x86_64__)
    const char *version = "NativeLens Engine v1.0.0 (x86_64, C++17)";
#else
    const char *version = "NativeLens Engine v1.0.0 (unknown-abi, C++17)";
#endif
    return env->NewStringUTF(version);
}
