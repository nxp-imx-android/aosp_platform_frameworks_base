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


#include <vector>

#include "dmabufinfo/dmabuf_sysfs_stats.h"
#include "dmabufinfo/dmabufinfo.h"
#include "jni.h"
#include "nativehelper/JNIHelp.h"
#include "log/log_main.h"

#define LOG_TAG "DMABuf"

namespace android {
    static jclass gDmabufinfo_clazz = NULL;
    static jmethodID gDmabufinfo_construtor = NULL;

    jobjectArray NativeToJavaDmaBuf(JNIEnv* env, std::vector<dmabufinfo::DmaBuffer>* dmabufs) {
        jobjectArray joa = env->NewObjectArray(dmabufs->size(), gDmabufinfo_clazz, NULL);
        int i = 0;
        for (dmabufinfo::DmaBuffer dmabuf : *dmabufs) {
            jintArray pids = env->NewIntArray(dmabuf.pids().size());
            std::vector<int> pids_vector(dmabuf.pids().begin(), dmabuf.pids().end());
            env->SetIntArrayRegion(pids, 0, dmabuf.pids().size(), pids_vector.data());
            env->SetObjectArrayElement(joa, i++,
                                    env->NewObject(gDmabufinfo_clazz, gDmabufinfo_construtor,
                                                    dmabuf.inode(), dmabuf.size(),
                                                    env->NewStringUTF(dmabuf.exporter().c_str()),
                                                    pids));
        }
        return joa;
    }

    static jobjectArray getDmaBufInfo(JNIEnv* env, jobject) {
        std::vector<dmabufinfo::DmaBuffer> dmabufs;
        dmabufinfo::ReadProcfsDmaBufs(&dmabufs);
        return NativeToJavaDmaBuf(env, &dmabufs);
    }

    static jobjectArray getDmaBufInfoForPid(JNIEnv* env, jobject , jint pid) {
        std::vector<dmabufinfo::DmaBuffer> dmabufs;
        dmabufinfo::ReadDmaBufInfo(pid, &dmabufs);
        return NativeToJavaDmaBuf(env, &dmabufs);
    }

    static jlong getTotalDmaBufExportedKb(JNIEnv*, jobject ) {
        uint64_t total;
        dmabufinfo::GetDmabufTotalExportedKb(&total);
        return total;
    }

    // ----------------------------------------------------------------------------
    // JNI Glue
    // ----------------------------------------------------------------------------

    const char* const kClassPathName = "com.android.server.ondeviceintelligence.OnDeviceIntelligenceManagerService";

    static const JNINativeMethod gMethods[] = {
            {"nativeGetDmaBufInfo", "()[Landroid/app/ondeviceintelligence/DmaBufEntry;",
            (void*)getDmaBufInfo},
            {"nativeGetDmaBufInfoForPid", "(I)[Landroid/app/ondeviceintelligence/DmaBufEntry;",
            (void*)getDmaBufInfoForPid},
            {"nativeGetTotalDmaBufExportedKb", "()J", (void*)getTotalDmaBufExportedKb},
    };
    int register_com_android_server_ondeviceintelligence_OnDeviceIntelligenceManagerService(JNIEnv* env) {
            jclass dmabufinfo_clazz = env->FindClass("android/app/ondeviceintelligence/DmaBufEntry");
        ALOG_ASSERT(dmabufinfo_clazz, "Couldn't find DmaBufEntry class.");
        gDmabufinfo_clazz = static_cast<jclass>(env->NewGlobalRef(dmabufinfo_clazz));
        ALOG_ASSERT(gDmabufinfo_clazz, "Couldn't get global ref for DmaBufEntry class.");
        gDmabufinfo_construtor =
                env->GetMethodID(gDmabufinfo_clazz, "<init>", "(JJLjava/lang/String;[I)V");
        ALOG_ASSERT(gDmabufinfo_construtor, "Couldn't get method id of DmaBufEntry constructor");

        return jniRegisterNativeMethods(env, kClassPathName, gMethods, NELEM(gMethods));
    }

} // namespace android
