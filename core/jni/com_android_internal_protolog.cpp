/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "ProtoLogNative"

#include <ProtoLog.h>
#include <nativehelper/JNIHelp.h>
#include <utils/Log.h>

#include <cstring>
#include <string>
#include <vector>

#include "core_jni_helpers.h"
#include "jni.h"

// Structure to cache JNI classes and method IDs
struct CachedJniIds {
    jclass illegalArgumentExceptionClass = nullptr;

    bool initialize(JNIEnv* env) {
        // Find and create global references for classes
        illegalArgumentExceptionClass =
                findClassAsGlobalRef(env, "java/lang/IllegalArgumentException");

        if (!illegalArgumentExceptionClass) {
            return false; // Initialization failed
        }
        return true;
    }

    void cleanup(JNIEnv* env) {
        if (illegalArgumentExceptionClass) env->DeleteGlobalRef(illegalArgumentExceptionClass);
    }

private:
    jclass findClassAsGlobalRef(JNIEnv* env, const char* className) {
        jclass localClass = env->FindClass(className);
        if (!localClass) {
            ALOGE("Failed to find class %s", className);
            return nullptr;
        }
        jclass globalClass = (jclass)env->NewGlobalRef(localClass);
        env->DeleteLocalRef(localClass);
        if (!globalClass) {
            ALOGE("Failed to create global ref for class %s", className);
        }
        return globalClass;
    }
};

// Global instance - initialize this in JNI_OnLoad
CachedJniIds gCachedIds;

namespace android::protolog {

static void com_android_internal_protolog_init() {
    Initialize();
}

class JniArgumentProvider : public IArgumentProvider {
public:
    JniArgumentProvider(JNIEnv* env, int paramsMask, int argCount, jlongArray primitiveArgs,
                        jobjectArray stringArgs)
          : mEnv(env),
            mParamsMask(paramsMask),
            mArgCount(argCount),
            mPrimitiveArgsPtr(primitiveArgs ? env->GetLongArrayElements(primitiveArgs, nullptr)
                                            : nullptr),
            mStringArgs(stringArgs),
            mPrimitiveArgs(primitiveArgs),
            mArgIndex(0) {}

    ~JniArgumentProvider() {
        if (mPrimitiveArgs && mPrimitiveArgsPtr) {
            mEnv->ReleaseLongArrayElements(mPrimitiveArgs, mPrimitiveArgsPtr, JNI_ABORT);
        }
    }

    void newPass() override {
        mArgIndex = 0;
        mLastJString = nullptr;
        mLastStr = nullptr;
    }

    void endPass() override {
        if (mArgIndex < mArgCount) {
            char errorMsg[256];
            snprintf(errorMsg, sizeof(errorMsg),
                     "Too many arguments provided for format string, provided %d, expected %d",
                     mArgCount, mArgIndex);
            reportArgumentError(errorMsg);
        }
    }

    long long nextInt() override {
        if (checkType(0b01)) {
            return mPrimitiveArgsPtr[mArgIndex++];
        }
        return 0;
    }

    double nextDouble() override {
        if (checkType(0b10)) {
            jlong bits = mPrimitiveArgsPtr[mArgIndex++];
            double res;
            std::memcpy(&res, &bits, sizeof(double));
            return res;
        }
        return 0.0;
    }

    const char* nextString() override {
        if (checkType(0b00)) {
            jobject arg = mEnv->GetObjectArrayElement(mStringArgs, mArgIndex++);
            if (!arg) {
                return "null";
            }
            mLastJString = (jstring)arg;
            mLastStr = mEnv->GetStringUTFChars(mLastJString, nullptr);
            return mLastStr;
        }
        return "null";
    }

    void releaseString() override {
        if (mLastJString && mLastStr) {
            mEnv->ReleaseStringUTFChars(mLastJString, mLastStr);
            mEnv->DeleteLocalRef(mLastJString);
            mLastJString = nullptr;
            mLastStr = nullptr;
        }
    }

    bool nextBool() override {
        if (checkType(0b11)) {
            return mPrimitiveArgsPtr[mArgIndex++] != 0;
        }
        return false;
    }

private:
    bool checkType(int requestedType) {
        if (mArgIndex >= mArgCount) {
            reportArgumentError("Too few arguments");
            return false;
        }

        int actualType = (mParamsMask >> (mArgIndex * 2)) & 0b11;
        if (requestedType != actualType) {
            // We might have a param maks with the argument as string which is null, in which case
            // it can be parsed and interpreted as any type.
            bool nullValue = actualType == 0 &&
                    mEnv->GetObjectArrayElement(mStringArgs, mArgIndex) == nullptr;

            if (nullValue) {
                return true;
            }

            char errorMsg[256];
            snprintf(errorMsg, sizeof(errorMsg),
                     "Cannot apply argument at index %d to ProtoLog format string", mArgIndex);
            reportArgumentError(errorMsg);
            return false;
        }

        return true;
    }

    void reportArgumentError(const char* errorMsg) {
        if (gCachedIds.illegalArgumentExceptionClass) {
            mEnv->ThrowNew(gCachedIds.illegalArgumentExceptionClass, errorMsg);
        }
    }

    JNIEnv* mEnv;
    int mParamsMask;
    int mArgCount;
    jlong* mPrimitiveArgsPtr;
    jobjectArray mStringArgs;
    jlongArray mPrimitiveArgs;

    int mArgIndex;

    jstring mLastJString = nullptr;
    const char* mLastStr = nullptr;
};

static void com_android_internal_protolog_log_string(JNIEnv* env, jclass clazz, jint level,
                                                     jstring group, jstring message,
                                                     jint paramsMask, jint argCount,
                                                     jlongArray primitiveArgs,
                                                     jobjectArray stringArgs) {
    if (group == nullptr || message == nullptr) {
        return;
    }

    const char* groupStr = env->GetStringUTFChars(group, nullptr);
    const char* messageStr = env->GetStringUTFChars(message, nullptr);

    JniArgumentProvider argumentProvider(env, paramsMask, argCount, primitiveArgs, stringArgs);
    Log(static_cast<ProtoLogLevel>(level), groupStr, messageStr, argumentProvider);

    env->ReleaseStringUTFChars(group, groupStr);
    env->ReleaseStringUTFChars(message, messageStr);
}

static void com_android_internal_protolog_log_hash(JNIEnv* env, jclass clazz, jint level,
                                                   jstring group, jlong messageHash,
                                                   jint paramsMask, jint argCount,
                                                   jlongArray primitiveArgs,
                                                   jobjectArray stringArgs) {
    if (group == nullptr) {
        return;
    }

    const char* groupStr = env->GetStringUTFChars(group, nullptr);

    JniArgumentProvider argumentProvider(env, paramsMask, argCount, primitiveArgs, stringArgs);

    Log(static_cast<ProtoLogLevel>(level), groupStr, (uint64_t)messageHash, paramsMask, argCount,
        argumentProvider);

    env->ReleaseStringUTFChars(group, groupStr);
}

static const JNINativeMethod gMethods[] = {
        {"init", "()V", (void*)com_android_internal_protolog_init},
        {"log", "(ILjava/lang/String;Ljava/lang/String;II[J[Ljava/lang/Object;)V",
         (void*)com_android_internal_protolog_log_string},
        {"log", "(ILjava/lang/String;JII[J[Ljava/lang/Object;)V",
         (void*)com_android_internal_protolog_log_hash},
};

} // namespace android::protolog

jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    if (jniRegisterNativeMethods(env, "com/android/internal/protolog/ProtoLogNative",
                                 android::protolog::gMethods,
                                 NELEM(android::protolog::gMethods)) < 0) {
        return JNI_ERR;
    }

    if (!gCachedIds.initialize(env)) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* /* reserved */) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return;
    }
    gCachedIds.cleanup(env);
}
