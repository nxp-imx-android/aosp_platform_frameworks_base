/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_TAG "VrManagerService"

#include <android_runtime/AndroidRuntime.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <utils/Errors.h>
#include <utils/Log.h>

namespace android {

static void init_native(JNIEnv* /* env */, jclass /* clazz */) {
    ALOGW("%s: Could not open IVr interface as it no longer supported", __FUNCTION__);
    return;
}

static void setVrMode_native(JNIEnv* /* env */, jclass /* clazz */, jboolean enabled) {
    ALOGW("%s: Could not use IVr interface as it no longer supported", __FUNCTION__);
    return;
}

static const JNINativeMethod method_table[] = {
    { "initializeNative", "()V", (void*)init_native },
    { "setVrModeNative", "(Z)V", (void*)setVrMode_native },
};

int register_android_server_vr_VrManagerService(JNIEnv *env)
{
    return jniRegisterNativeMethods(env, "com/android/server/vr/VrManagerService",
            method_table, NELEM(method_table));
}

}; // namespace android
