#include <jni.h>
#include <android/log.h>
#include <chrono>
#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "ListenWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/** 同步转写期间用于回调 Kotlin 进度方法的 JNI 上下文。 */
struct progress_callback_data {
    JNIEnv *env;
    jclass bridge_class;
    jmethodID callback;
};

/** 把 Whisper 的百分比进度交给 Kotlin，并记录回调异常。 */
static void report_progress(
        whisper_context * /* context */,
        whisper_state * /* state */,
        int progress,
        void *user_data) {
    auto *data = static_cast<progress_callback_data *>(user_data);
    data->env->CallStaticVoidMethod(data->bridge_class, data->callback, progress);
    if (data->env->ExceptionCheck()) {
        LOGE("Kotlin progress callback raised an exception at %d%%", progress);
        data->env->ExceptionDescribe();
        data->env->ExceptionClear();
    }
}

/**
 * 载入指定的英文模型，对 16 kHz 单声道 PCM 执行识别。
 * 每个返回元素以毫秒时间和子词偏移作为头部，换行后接原始字幕文本。
 */
extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_example_listenenglish_transcription_WhisperNativeBridge_nativeTranscribe(
        JNIEnv *env,
        jobject /* thiz */,
        jstring model_path,
        jfloatArray pcm) {
    const auto total_started_at = std::chrono::steady_clock::now();
    const char *model_chars = env->GetStringUTFChars(model_path, nullptr);
    if (model_chars == nullptr) return nullptr;

    const jsize sample_count = env->GetArrayLength(pcm);
    std::vector<float> samples(static_cast<size_t>(sample_count));
    env->GetFloatArrayRegion(pcm, 0, sample_count, samples.data());

    whisper_context_params context_params = whisper_context_default_params();
    LOGI("Native transcription started: %.1f seconds, model=%s", sample_count / 16000.0, model_chars);
    whisper_context *context = whisper_init_from_file_with_params(model_chars, context_params);
    env->ReleaseStringUTFChars(model_path, model_chars);
    if (context == nullptr) {
        LOGE("Cannot load Whisper model");
        return nullptr;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.token_timestamps = true;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.n_threads = 4;
    params.language = "en";
    LOGI("Whisper configured: threads=%d, %s", params.n_threads, whisper_print_system_info());
    jclass bridge = static_cast<jclass>(env->NewGlobalRef(env->FindClass(
            "com/example/listenenglish/transcription/WhisperNativeBridge")));
    jmethodID progress_method = env->GetStaticMethodID(bridge, "onNativeProgress", "(I)V");
    progress_callback_data progress_data = { env, bridge, progress_method };
    params.progress_callback = report_progress;
    params.progress_callback_user_data = &progress_data;

    const int result = whisper_full(context, params, samples.data(), samples.size());
    env->DeleteGlobalRef(bridge);
    if (result != 0) {
        const auto elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now() - total_started_at).count();
        LOGE("whisper_full failed: code=%d elapsed=%.1fs", result, elapsed);
        whisper_free(context);
        return nullptr;
    }

    const int count = whisper_full_n_segments(context);
    const auto elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now() - total_started_at).count();
    LOGI("Native transcription completed: segments=%d elapsed=%.1fs", count, elapsed);
    jclass string_class = env->FindClass("java/lang/String");
    jmethodID utf8_constructor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
    jstring utf8 = env->NewStringUTF("UTF-8");
    jobjectArray output = env->NewObjectArray(count, string_class, nullptr);
    for (int index = 0; index < count; ++index) {
        const int64_t start_ms = whisper_full_get_segment_t0(context, index) * 10;
        const int64_t end_ms = whisper_full_get_segment_t1(context, index) * 10;
        const std::string text = whisper_full_get_segment_text(context, index);
        std::string token_text;
        std::string timings;
        const int token_count = whisper_full_n_tokens(context, index);
        for (int token_index = 0; token_index < token_count; ++token_index) {
            const auto token = whisper_full_get_token_data(context, index, token_index);
            if (token.id >= whisper_token_eot(context)) continue;
            token_text += whisper_full_get_token_text(context, index, token_index);
            // Offsets refer to the original UTF-8 bytes, before trimming or Java decoding.
            timings += std::to_string(token_text.size()) + "," + std::to_string(token.t0 * 10)
                       + "," + std::to_string(token.t1 * 10) + ";";
        }
        if (token_text != text) timings.clear();
        const std::string packed = std::to_string(start_ms) + "\t" + std::to_string(end_ms)
                                   + "\t" + timings + "\n" + text;
        const auto size = static_cast<jsize>(packed.size());
        jbyteArray bytes = env->NewByteArray(size);
        if (bytes == nullptr) break;
        env->SetByteArrayRegion(bytes, 0, size, reinterpret_cast<const jbyte *>(packed.data()));
        jobject value = env->NewObject(string_class, utf8_constructor, bytes, utf8);
        env->DeleteLocalRef(bytes);
        if (value == nullptr) break;
        env->SetObjectArrayElement(output, index, value);
        env->DeleteLocalRef(value);
    }
    whisper_free(context);
    return output;
}
