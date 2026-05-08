#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "HARDWARE_LEVEL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapplication_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {

    LOGI("C++ Backend Initialized - Hardware Access Ready");

    std::string hello = "Native Hardware Bridge Active";
    return env->NewStringUTF(hello.c_str());
}
