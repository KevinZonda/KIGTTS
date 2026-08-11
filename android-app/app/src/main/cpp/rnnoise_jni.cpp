#include <jni.h>

#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

#include "rnnoise.h"

namespace {

constexpr int kRnNoiseFrameSize = 480;

// This RNNoise snapshot keeps its FFT plan in process-global state. Serialize native access and
// clean that plan only after the final denoiser is destroyed.
std::mutex gRnNoiseMutex;
size_t gRnNoiseHandleCount = 0;

struct RnNoiseHandle {
    DenoiseState* state = nullptr;
};

RnNoiseHandle* fromHandle(jlong handle) {
    return reinterpret_cast<RnNoiseHandle*>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_lhtstudio_kigtts_app_audio_RnNoiseNative_nativeCreate(JNIEnv*, jclass) {
    const std::lock_guard<std::mutex> guard(gRnNoiseMutex);
    auto* handle = new (std::nothrow) RnNoiseHandle();
    if (handle == nullptr) {
        return 0;
    }
    handle->state = rnnoise_create(nullptr);
    if (handle->state == nullptr) {
        delete handle;
        return 0;
    }
    ++gRnNoiseHandleCount;
    return static_cast<jlong>(reinterpret_cast<intptr_t>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_lhtstudio_kigtts_app_audio_RnNoiseNative_nativeDestroy(JNIEnv*, jclass, jlong handlePtr) {
    const std::lock_guard<std::mutex> guard(gRnNoiseMutex);
    auto* handle = fromHandle(handlePtr);
    if (handle == nullptr) {
        return;
    }
    if (handle->state != nullptr) {
        rnnoise_destroy(handle->state);
        handle->state = nullptr;
        if (gRnNoiseHandleCount > 0) {
            --gRnNoiseHandleCount;
        }
        if (gRnNoiseHandleCount == 0) {
            rnnoise_global_cleanup();
        }
    }
    delete handle;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_lhtstudio_kigtts_app_audio_RnNoiseNative_nativeFrameSize(JNIEnv*, jclass) {
    return kRnNoiseFrameSize;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_lhtstudio_kigtts_app_audio_RnNoiseNative_nativeProcessFrame(
    JNIEnv* env,
    jclass,
    jlong handlePtr,
    jfloatArray inputArray,
    jfloatArray outputArray) {
    const std::lock_guard<std::mutex> guard(gRnNoiseMutex);
    auto* handle = fromHandle(handlePtr);
    if (handle == nullptr || handle->state == nullptr || inputArray == nullptr || outputArray == nullptr) {
        return 0.0f;
    }
    const int frameSize = kRnNoiseFrameSize;
    if (env->GetArrayLength(inputArray) < frameSize || env->GetArrayLength(outputArray) < frameSize) {
        return 0.0f;
    }
    std::vector<float> input(static_cast<size_t>(frameSize));
    std::vector<float> output(static_cast<size_t>(frameSize));
    env->GetFloatArrayRegion(inputArray, 0, frameSize, input.data());
    const float vad = rnnoise_process_frame(handle->state, output.data(), input.data());
    env->SetFloatArrayRegion(outputArray, 0, frameSize, output.data());
    return vad;
}
