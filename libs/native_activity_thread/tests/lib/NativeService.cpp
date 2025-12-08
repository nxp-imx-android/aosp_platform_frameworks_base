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

#define LOG_TAG " *** *** *** *** NATIVE SERVICE"

#include "NativeService.h"

#include <android/binder_ibinder.h>
#include <dlfcn.h>
#include <log/log.h>

static std::map<ANativeService*, std::shared_ptr<NativeServiceWrapper>> gServiceMap;
static std::map<ANativeService*, std::set<uint64_t>> gBindingMap;

ndk::ScopedAStatus NativeServiceWrapper::registerListener(
        const std::shared_ptr<INativeServiceListener>& listener) {
    listener_ = listener;
    listener_->onRegister();
    return ndk::ScopedAStatus::ok();
}

void NativeServiceWrapper::doUnbind() {
    listener_->onUnbind();
}

void NativeServiceWrapper::doRebind() {
    listener_->onRebind();
}

extern "C" void native_service_onDestroy(ANativeService* _Nonnull service) {
    ALOGI("native_service_onDestroy – service %p", service);
}

extern "C" AIBinder* native_service_onBind(ANativeService* _Nonnull service,
                                           uint64_t bindToken, char const* _Nonnull action,
                                           char const* _Nonnull data) {
    ALOGI("native_service_onBind – service %p, bindToken %lu, action: %s, data %s", service,
          bindToken, action, data);
    gBindingMap[service].insert(bindToken);
    if (strncmp(action, "TEST_ACTION", 12) != 0 ||
        strncmp(data, "content://com.example/people", 29) != 0) {
        ALOGE("Invalid action or data: %s %s", action, data);
        return nullptr;
    }
    return gServiceMap.at(service)->asBinder().get();
}

extern "C" void native_service_onRebind(ANativeService* _Nonnull service, uint64_t bindToken) {
    ALOGI("native_service_onRebind – service %p, bindToken %lu", service, bindToken);
    gBindingMap[service].insert(bindToken);
    gServiceMap.at(service)->doRebind();
}

extern "C" bool native_service_onUnbind(ANativeService* _Nonnull service, uint64_t bindToken) {
    ALOGI("native_service_onUnbind – service %p, bindToken %lu", service, bindToken);
    std::shared_ptr<NativeServiceWrapper> wrapper = gServiceMap.at(service);
    if (gBindingMap.at(service).contains(bindToken)) {
        wrapper->doUnbind();
        gBindingMap.at(service).erase(bindToken);
        gServiceMap.erase(service);
        AIBinder_decStrong(wrapper->asBinder().get());
    }
    return false;
}

extern "C" void native_service_onTrimMemory(ANativeService* _Nonnull service,
                                            ANativeServiceTrimMemoryLevel level) {
    ALOGI("native_service_onTrimMemory – service %p, level %d", service, level);
}

extern "C" void native_service_createService(ANativeService* service) {
    ALOGI("native_service_createService – service %p", service);
    // TODO: We are accessing the native APIs through dlopen and dlsym only because they are not
    // officially supported yet.  Use `ANativeService_*` functions linked to libandroid.so instead
    // once they are officially introduced. cf) go/unfinalized-native-api-questions
    void* lib = dlopen("libandroid.so", RTLD_LOCAL);
    if (!lib) {
        ALOGE("Failed to open libandroid.so: %s", dlerror());
        return;
    }
    auto setOnBindCallback = reinterpret_cast<decltype(&ANativeService_setOnBindCallback)>(
            dlsym(lib, "ANativeService_setOnBindCallback"));
    if (setOnBindCallback == nullptr) {
        ALOGE("Failed to find ANativeService_setOnBindCallback: %s", dlerror());
        return;
    }
    auto setOnUnbindCallback = reinterpret_cast<decltype(&ANativeService_setOnUnbindCallback)>(
            dlsym(lib, "ANativeService_setOnUnbindCallback"));
    if (setOnUnbindCallback == nullptr) {
        ALOGE("Failed to find ANativeService_setOnUnbindCallback: %s", dlerror());
        return;
    }
    auto setOnRebindCallback = reinterpret_cast<decltype(&ANativeService_setOnRebindCallback)>(
            dlsym(lib, "ANativeService_setOnRebindCallback"));
    if (setOnRebindCallback == nullptr) {
        ALOGE("Failed to find ANativeService_setOnRebindCallback: %s", dlerror());
        return;
    }
    auto setOnDestroyCallback = reinterpret_cast<decltype(&ANativeService_setOnDestroyCallback)>(
            dlsym(lib, "ANativeService_setOnDestroyCallback"));
    if (setOnDestroyCallback == nullptr) {
        ALOGE("Failed to find ANativeService_setOnDestroyCallback: %s", dlerror());
        return;
    }
    auto setOnTrimMemoryCallback =
            reinterpret_cast<decltype(&ANativeService_setOnTrimMemoryCallback)>(
                    dlsym(lib, "ANativeService_setOnTrimMemoryCallback"));
    if (setOnTrimMemoryCallback == nullptr) {
        ALOGE("Failed to find ANativeService_setOnTrimMemoryCallback: %s", dlerror());
        return;
    }

    setOnBindCallback(service, native_service_onBind);
    setOnUnbindCallback(service, native_service_onUnbind);
    setOnRebindCallback(service, native_service_onRebind);
    setOnDestroyCallback(service, native_service_onDestroy);
    setOnTrimMemoryCallback(service, native_service_onTrimMemory);

    std::shared_ptr<NativeServiceWrapper> wrapper =
            ndk::SharedRefBase::make<NativeServiceWrapper>();
    gServiceMap.insert({service, wrapper});
    AIBinder_incStrong(wrapper->asBinder().get());
}
