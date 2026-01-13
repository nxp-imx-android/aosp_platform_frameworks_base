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

#define LOG_TAG "HubEndpointJNI"

#include <android/looper.h>
#include <nativehelper/JNIHelp.h>
#include <stdio.h>

#include <cinttypes>

#include "android_os_MessageQueue.h"
#include "core_jni_helpers.h"
#include "data_flow/host/notification_manager.h"
#include "data_flow/host/region_manager.h"
#include "data_flow/untyped_queue.h"
#include "jni.h"
#include "utils/Log.h"

using namespace android;
using ::aidl::android::hardware::contexthub::DataFlowAlertFds;
using ::aidl::android::hardware::contexthub::DataFlowId;
using ::aidl::android::hardware::contexthub::DataFlowSinkContext;
using ::aidl::android::hardware::contexthub::EndpointId;
using ::aidl::android::hardware::contexthub::SharedDataRegion;
using android::contexthub::data_flow::AllocatorRegion;
using android::contexthub::data_flow::ConsumerPolicyBuilder;
using android::contexthub::data_flow::DataNotifier;
using android::contexthub::data_flow::NotificationManager;
using android::contexthub::data_flow::NotificationPolicy;
using android::contexthub::data_flow::queueLayout;
using android::contexthub::data_flow::RegionManager;
using android::contexthub::data_flow::RemoteNotifyArgs;
using android::contexthub::data_flow::UntypedConsumer;
using android::contexthub::data_flow::UntypedProducer;

static JavaVM* gVm = nullptr;

#define RETURN_ON_FALSE(condition, retval, errorString) \
    if (!(condition)) {                                 \
        ALOGE("%s", errorString);                       \
        return retval;                                  \
    }

class SourceWrapper {
public:
    SourceWrapper(UntypedProducer&& producer, AllocatorRegion allocator,
                  NotificationManager::NotificationDataHandle notificationDataHandle)
          : mProducer(std::move(producer)),
            mAllocator(allocator),
            mNotificationDataHandle(notificationDataHandle) {}

    SourceWrapper(SourceWrapper&& other)
          : mProducer(std::move(other.mProducer)),
            mAllocator(other.mAllocator),
            mNotificationDataHandle(other.mNotificationDataHandle) {}

    UntypedProducer& getProducer() {
        return mProducer;
    }

    AllocatorRegion* getAllocator() {
        return &mAllocator;
    }

    NotificationManager::NotificationDataHandle getNotificationDataHandle() {
        return mNotificationDataHandle;
    }

    std::optional<DataFlowId> getDataFlowId() {
        return mDataFlowId;
    }
    void setDataFlowId(DataFlowId dataFlowId) {
        mDataFlowId = dataFlowId;
    }

    std::set<EndpointId>& getOffloadSinks() {
        return mOffloadSinks;
    }

    void removeOffloadSink(EndpointId endpointId) {
        mOffloadSinks.erase(endpointId);
    }

private:
    UntypedProducer mProducer;
    AllocatorRegion mAllocator;
    NotificationManager::NotificationDataHandle mNotificationDataHandle;
    std::optional<DataFlowId> mDataFlowId;

    std::set<EndpointId> mOffloadSinks;
};

class SinkWrapper {
public:
    SinkWrapper(UntypedConsumer&& consumer, DataFlowId dataFlowId)
          : mConsumer(std::move(consumer)), mDataFlowId(dataFlowId) {}
    SinkWrapper(SinkWrapper&& other)
          : mConsumer(std::move(other.mConsumer)), mDataFlowId(other.mDataFlowId) {}

    UntypedConsumer& getConsumer() {
        return mConsumer;
    }
    DataFlowId getDataFlowId() {
        return mDataFlowId;
    }

    ~SinkWrapper() {
        mConsumer.disable();
    }

private:
    UntypedConsumer mConsumer;
    DataFlowId mDataFlowId;
};

class HubEndpointResource {
public:
    class EpollWaiter : public NotificationManager::EpollWaiter {
    public:
        EpollWaiter(HubEndpointResource* resource) : mResource(resource) {}
        void addFd(int fd) override {
            ALOGI("EpollWaiter::addFd: fd=%d", fd);
            auto fn = [](int fd, int events, void* data) -> int {
                ALOGI("Looper cb called: fd=%d events=%d data=%p", fd, events, data);
                static_cast<EpollWaiter*>(data)->handleNotification(fd, /* error= */ false);
                return 1;
            };
            mResource->mMessageQueue->getLooper()->addFd(fd, fd, ALOOPER_EVENT_INPUT, fn,
                                                         /* data= */ this);
        }

        void removeFd(int fd) override {
            ALOGI("EpollWaiter::removeFd: fd=%d", fd);
            mResource->mMessageQueue->getLooper()->removeFd(fd);
        }

    private:
        HubEndpointResource* mResource;
    };

    HubEndpointResource(sp<MessageQueue> messageQueue, jobject callbackObject, jlong hubId)
          : mMessageQueue(messageQueue), mCallbackObject(callbackObject), mHubId(hubId) {
        auto waiter = std::make_unique<EpollWaiter>(this);
        auto notificationCb = [this](DataFlowId id, bool waking) {
            ALOGI("NotificationCallback: hub id=0x%" PRIx64 " id=%" PRIu32 " waking=%d", id.hubId,
                  id.id, waking);
            JNIEnv* env = GetOrAttachJNIEnvironment(gVm, JNI_VERSION_1_6);
            if (env == nullptr) {
                ALOGE("Failed to get JNIEnv");
                return;
            }

            // TODO(b/460528144): Cache the jclass/jmethodId
            jclass callbackClass = env->GetObjectClass(mCallbackObject);
            jmethodID methodId =
                    env->GetMethodID(callbackClass, "onNotificationCallback", "(JIZ)V");
            env->CallVoidMethod(mCallbackObject, methodId, id.hubId, id.id, waking);
            env->DeleteLocalRef(callbackClass);
        };
        mNotificationManager = NotificationManager::create(std::move(waiter), notificationCb);
    }

    ~HubEndpointResource() {
        for (auto& [id, sourceWrapper] : mSources) {
            removeSourceWrapper(id);
        }
        for (auto& [id, sinkWrapper] : mSinks) {
            removeSinkWrapper(id);
        }
    }

    sp<MessageQueue> getMessageQueue() {
        return mMessageQueue;
    }

    jobject getCallbackObject() {
        return mCallbackObject;
    }

    long getHubId() {
        return mHubId;
    }

    RegionManager* getRegionManager() {
        return &mRegionManager;
    }

    NotificationManager* getNotificationManager() {
        return mNotificationManager.get();
    }

    DataNotifier& getNotifier() {
        return mNotifier;
    }

    SourceWrapper* getSourceWrapper(int regionId) {
        auto it = mSources.find(regionId);
        if (it == mSources.end()) {
            return nullptr;
        }
        return &it->second;
    }

    void removeSourceWrapper(int regionId) {
        SourceWrapper* wrapper = getSourceWrapper(regionId);
        if (wrapper != nullptr) {
            auto dataFlowId = wrapper->getDataFlowId();
            if (dataFlowId.has_value()) {
                mNotificationManager->removeHostProducerDataFlow(dataFlowId.value().id);
            }
            mSources.erase(regionId);
            mRegionManager.unmapHostProducerRegion(regionId);
        }
    }

    void addSourceWrapper(int regionId, SourceWrapper&& sourceWrapper) {
        mSources.emplace(regionId, std::move(sourceWrapper));
    }

    SinkWrapper* getSinkWrapper(int dataFlowId) {
        auto it = mSinks.find(dataFlowId);
        if (it == mSinks.end()) {
            return nullptr;
        }
        return &it->second;
    }

    void removeSinkWrapper(int dataFlowId) {
        SinkWrapper* wrapper = getSinkWrapper(dataFlowId);
        if (wrapper != nullptr) {
            auto dataFlowId = wrapper->getDataFlowId();
            mNotificationManager->disableHostConsumer(dataFlowId);
            mSinks.erase(dataFlowId.id);
            mRegionManager.unlinkHostConsumerDataFlow(dataFlowId);
        }
    }

    void addSinkWrapper(int dataFlowId, SinkWrapper&& sinkWrapper) {
        mSinks.emplace(dataFlowId, std::move(sinkWrapper));
    }

private:
    sp<MessageQueue> mMessageQueue;
    jobject mCallbackObject;
    long mHubId;
    RegionManager mRegionManager;
    std::shared_ptr<NotificationManager> mNotificationManager;
    DataNotifier mNotifier;

    // Key is the shared data region ID.
    std::map<int, SourceWrapper> mSources;
    // Key is the data flow ID.
    std::map<int, SinkWrapper> mSinks;
};

static jlong android_hardware_HubEndpoint_init(JNIEnv* env, jobject /* thiz */, jobject queueObject,
                                               jobject callbackObject, jlong hubId) {
    HubEndpointResource* resource =
            new HubEndpointResource(android_os_MessageQueue_getMessageQueue(env, queueObject),
                                    env->NewGlobalRef(callbackObject), hubId);
    return reinterpret_cast<jlong>(resource);
}

static jintArray android_hardware_HubEndpoint_createDataFlowInfo(JNIEnv* env, jobject /* thiz */,
                                                                 jlong handle, jint regionId,
                                                                 jlong regionSize, jint regionFd,
                                                                 jint elementSize, jint alignment,
                                                                 jint minElementCount,
                                                                 jint maxElementCount) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, nullptr, "Invalid handle");

    SharedDataRegion region = {};
    region.id = regionId;
    region.sizeBytes = regionSize;
    region.sharedMemory = ndk::ScopedFileDescriptor(dup(regionFd));
    auto alloc = resource->getRegionManager()->mapHostProducerRegion(std::move(region));
    RETURN_ON_FALSE(alloc.ok(), nullptr,
                    (std::string("Failed to map producer region: ") + alloc.status().str())
                            .c_str());

    constexpr size_t kBlockCapacityInBytes = 1024;
    RemoteNotifyArgs args = {.fn = [&](pw::ConstByteSpan /*id*/) {}, .id = {}};

    size_t minElementSize = static_cast<size_t>(elementSize) * minElementCount;
    size_t maxElementSize = static_cast<size_t>(elementSize) * maxElementCount;
    // The min block count >= 1, and maxBlockCount >= minBlockCount
    size_t minBlockCount = 1 + (minElementSize - 1) / kBlockCapacityInBytes;
    size_t maxBlockCount = 1 + (maxElementSize - 1) / kBlockCapacityInBytes;
    auto producer =
            UntypedProducer::createRemote(alloc.value(),
                                          kBlockCapacityInBytes / static_cast<size_t>(elementSize),
                                          static_cast<size_t>(elementSize),
                                          static_cast<size_t>(alignment), maxBlockCount,
                                          minBlockCount, resource->getNotifier(), std::move(args));
    RETURN_ON_FALSE(producer.ok(), nullptr,
                    (std::string("Failed to create producer: ") + producer.status().str()).c_str());

    size_t queueOffset = producer->getQueueOffset();
    auto flow = resource->getNotificationManager()->prepareHostProducerDataFlowInfo();
    RETURN_ON_FALSE(flow.ok(), nullptr,
                    (std::string("Failed to prepare host producer data flow: ") +
                     flow.status().str())
                            .c_str());
    auto [dataFlowInfo, notificationDataHandle] = std::move(flow.value());
    dataFlowInfo.region.id = regionId;
    dataFlowInfo.metadataOffsetBytes = queueOffset;

    std::vector<jint> out;
    out.push_back(dataFlowInfo.metadataOffsetBytes);
    out.push_back(dup(dataFlowInfo.alertFds.waking.get()));
    out.push_back(dup(dataFlowInfo.alertFds.nonWaking.get()));
    out.push_back(dup(dataFlowInfo.alertFds.halAck.get()));
    jintArray javaArray = env->NewIntArray(out.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create int array");
    env->SetIntArrayRegion(javaArray, 0, out.size(), out.data());

    resource->addSourceWrapper(regionId,
                               SourceWrapper(std::move(*producer), alloc.value(),
                                             notificationDataHandle));

    return javaArray;
}

static jboolean android_hardware_HubEndpoint_activateDataFlow(JNIEnv* env, jobject thiz,
                                                              jlong handle, jint dataFlowId,
                                                              jint regionId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, JNI_FALSE, "Source not found for regionId");

    auto status =
            resource->getNotificationManager()
                    ->activateHostProducerDataFlow(dataFlowId,
                                                   sourceWrapper->getNotificationDataHandle());
    RETURN_ON_FALSE(status.ok(), JNI_FALSE,
                    (std::string("Failed to activate host producer data flow: ") + status.str())
                            .c_str());
    status = resource->getRegionManager()->linkHostProducerDataFlowToRegion(regionId, dataFlowId);
    RETURN_ON_FALSE(status.ok(), JNI_FALSE,
                    (std::string("Failed to link host producer data flow to region: ") +
                     status.str())
                            .c_str());

    sourceWrapper->setDataFlowId({
            .hubId = resource->getHubId(),
            .id = static_cast<int32_t>(dataFlowId),
    });

    return JNI_TRUE; // Success
}

static jintArray android_hardware_HubEndpoint_enableHostSink(
        JNIEnv* env, jobject /* thiz */, jlong handle, jint regionId, jlong regionSize,
        jint regionFd, jlong dataFlowHubId, jint dataFlowId, jint notifyHostFdsWaking,
        jint notifyHostFdsNonWaking, jint notifyHostFdsHalAck, jint notifyOffloadFdsWaking,
        jint notifyOffloadFdsNonWaking, jlong queueOffset, jlong metadataOffset) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, nullptr, "Invalid handle");
    RegionManager::RegionToMap regionToMap = {
            .id = regionId,
            .fd = ndk::ScopedFileDescriptor(dup(regionFd)),
            .size = static_cast<size_t>(regionSize),
    };

    DataFlowId id = {};
    id.hubId = dataFlowHubId;
    id.id = dataFlowId;
    auto result = resource->getRegionManager()->mapHostConsumerRegions(std::move(regionToMap),
                                                                       std::nullopt, id);
    RETURN_ON_FALSE(result.ok(), nullptr,
                    (std::string("Failed to map sink region: ") + result.status().str()).c_str());
    auto [region, _] = std::move(result.value());

    DataFlowAlertFds notifyHostFds = {.waking = ndk::ScopedFileDescriptor(dup(notifyHostFdsWaking)),
                                      .nonWaking = ndk::ScopedFileDescriptor(
                                              dup(notifyHostFdsNonWaking)),
                                      .halAck =
                                              ndk::ScopedFileDescriptor(dup(notifyHostFdsHalAck))};
    DataFlowAlertFds notifyOffloadFds = {.waking = ndk::ScopedFileDescriptor(
                                                 dup(notifyOffloadFdsWaking)),
                                         .nonWaking = ndk::ScopedFileDescriptor(
                                                 dup(notifyOffloadFdsNonWaking))};
    auto status = resource->getNotificationManager()
                          ->enableHostConsumerFromEventFds(id, std::move(notifyHostFds),
                                                           std::move(notifyOffloadFds));
    RETURN_ON_FALSE(status.ok(), nullptr,
                    (std::string("Failed to enable host sink: ") + status.str()).c_str());

    RemoteNotifyArgs args = {.fn = [](pw::ConstByteSpan /*id*/) { return; }, .id = {}};
    auto consumer = UntypedConsumer::createRemote(region, std::nullopt, queueOffset, metadataOffset,
                                                  std::move(args));
    RETURN_ON_FALSE(consumer.ok(), nullptr,
                    (std::string("Failed to create sink: ") + consumer.status().str()).c_str());

    std::vector<jint> out;
    out.push_back(consumer->getElementSize());
    out.push_back(consumer->getElementAlignment());
    jintArray javaArray = env->NewIntArray(out.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create int array");
    env->SetIntArrayRegion(javaArray, 0, out.size(), out.data());

    resource->addSinkWrapper(id.id, SinkWrapper(std::move(*consumer), id));

    return javaArray;
}

static jint android_hardware_HubEndpoint_sourcePush(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                    jint regionId, jbyteArray data,
                                                    jboolean allOrNothing) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, JNI_FALSE, "Source not found for regionId");

    UntypedProducer& producer = sourceWrapper->getProducer();

    jbyte* dataPtr = env->GetByteArrayElements(data, nullptr);
    RETURN_ON_FALSE(dataPtr != nullptr, 0, "Failed to get byte array elements");

    pw::ConstByteSpan byteSpan(reinterpret_cast<const std::byte*>(dataPtr),
                               env->GetArrayLength(data));
    pw::Result<size_t> pushResult = producer.push(byteSpan, allOrNothing);

    env->ReleaseByteArrayElements(data, dataPtr, JNI_ABORT);

    RETURN_ON_FALSE(pushResult.ok(), 0,
                    (std::string("Failed to push data to queue: ") + pushResult.status().str())
                            .c_str());

    // TODO(b/460528144): Wrap this using the default notifier
    for (auto& sink : sourceWrapper->getOffloadSinks()) {
        ALOGI("Notifying offload sink: endpoint id=0x%" PRIx64, sink.id);
        auto status =
                resource->getNotificationManager()->notifyOffloadConsumer(sink,
                                                                          /* waking = */ true);
        RETURN_ON_FALSE(status.ok(), 0,
                        (std::string("Failed to notify offload sink: ") + status.str()).c_str());
    }

    return static_cast<jint>(pushResult.value());
}

static jboolean android_hardware_HubEndpoint_sourceFull(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                        jint regionId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, JNI_FALSE, "Source not found for regionId");

    return static_cast<jboolean>(sourceWrapper->getProducer().full());
}

static jint android_hardware_HubEndpoint_sourceSize(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                    jint regionId, jboolean includeReserved) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, 0, "Source not found for regionId");

    return static_cast<jint>(sourceWrapper->getProducer().size(includeReserved));
}

static jbyteArray android_hardware_HubEndpoint_sinkRequestData(JNIEnv* env, jobject /*thiz*/,
                                                               jlong handle, jint dataFlowId,
                                                               jint elementCount,
                                                               jboolean allOrNothing) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, nullptr, "Invalid handle");

    SinkWrapper* sinkWrapper = resource->getSinkWrapper(dataFlowId);
    RETURN_ON_FALSE(sinkWrapper != nullptr, nullptr, "Sink not found for dataFlowId");

    UntypedConsumer& consumer = sinkWrapper->getConsumer();
    if (allOrNothing) {
        auto sizeResult = consumer.size();
        RETURN_ON_FALSE(sizeResult.ok(), nullptr,
                        (std::string("Failed to get sink size: ") + sizeResult.status().str())
                                .c_str());
        if (elementCount > static_cast<jint>(sizeResult.value())) {
            return nullptr;
        }
    }
    std::vector<std::byte> output(elementCount * consumer.getElementSize());
    auto popStatus = consumer.pop(output);
    RETURN_ON_FALSE(popStatus.ok(), nullptr,
                    (std::string("Failed to pop data from queue: ") + popStatus.str()).c_str());

    jbyteArray javaArray = env->NewByteArray(output.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create byte array");

    env->SetByteArrayRegion(javaArray, 0, output.size(),
                            reinterpret_cast<const jbyte*>(output.data()));
    return javaArray;
}

static jboolean android_hardware_HubEndpoint_sinkSyncToSource(JNIEnv* env, jobject /*thiz*/,
                                                              jlong handle, jint dataFlowId,
                                                              jint offset) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    SinkWrapper* sinkWrapper = resource->getSinkWrapper(dataFlowId);
    RETURN_ON_FALSE(sinkWrapper != nullptr, JNI_FALSE, "Sink not found for dataFlowId");

    auto syncStatus = sinkWrapper->getConsumer().resync(offset);
    RETURN_ON_FALSE(syncStatus.ok(), JNI_FALSE,
                    (std::string("Failed to resync sink: ") + syncStatus.str()).c_str());
    return JNI_TRUE;
}

static jboolean android_hardware_HubEndpoint_sinkSourceCanOverwriteReadPosition(JNIEnv* env,
                                                                                jobject /*thiz*/,
                                                                                jlong handle,
                                                                                jint dataFlowId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    SinkWrapper* sinkWrapper = resource->getSinkWrapper(dataFlowId);
    RETURN_ON_FALSE(sinkWrapper != nullptr, JNI_FALSE, "Sink not found for dataFlowId");

    auto overwritableResult = sinkWrapper->getConsumer().isOverwritable();
    RETURN_ON_FALSE(overwritableResult.ok(), JNI_FALSE,
                    (std::string("Failed to get sink overwritable: ") +
                     overwritableResult.status().str())
                            .c_str());
    return static_cast<jboolean>(overwritableResult.value());
}

static jint android_hardware_HubEndpoint_sinkSize(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                  jint dataFlowId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    SinkWrapper* sinkWrapper = resource->getSinkWrapper(dataFlowId);
    RETURN_ON_FALSE(sinkWrapper != nullptr, 0, "Sink not found for dataFlowId");

    auto sizeResult = sinkWrapper->getConsumer().size();
    RETURN_ON_FALSE(sizeResult.ok(), 0,
                    (std::string("Failed to get sink size: ") + sizeResult.status().str()).c_str());
    return static_cast<jint>(sizeResult.value());
}

static jintArray android_hardware_HubEndpoint_addOffloadSink(JNIEnv* env, jobject /*thiz*/,
                                                             jlong handle, jint regionId,
                                                             jlong hubId, jlong endpointId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, nullptr, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, nullptr, "Source not found for regionId");
    auto dataFlowId = sourceWrapper->getDataFlowId();
    RETURN_ON_FALSE(dataFlowId.has_value(), nullptr, "Data flow ID not set for source");

    EndpointId sinkEndpointId;
    sinkEndpointId.hubId = hubId;
    sinkEndpointId.id = endpointId;
    auto result = resource->getNotificationManager()
                          ->addOffloadConsumerAndGetEventFds(dataFlowId.value().id, sinkEndpointId);
    RETURN_ON_FALSE(result.ok(), nullptr,
                    (std::string("Failed to add offload sink: ") + result.status().str()).c_str());

    std::vector<jint> out;
    out.push_back(dup(result->waking.get()));
    out.push_back(dup(result->nonWaking.get()));
    jintArray javaArray = env->NewIntArray(out.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create int array");
    env->SetIntArrayRegion(javaArray, 0, out.size(), out.data());

    sourceWrapper->getOffloadSinks().insert(sinkEndpointId);

    return javaArray;
}

static ConsumerPolicyBuilder createConsumerPolicyBuilder(jint notificationPolicy,
                                                         jint notificationPolicyData,
                                                         jboolean canOverwrite) {
    ConsumerPolicyBuilder policy;
    switch (static_cast<NotificationPolicy>(notificationPolicy)) {
        case NotificationPolicy::kNever:
            policy.setNeverNotify();
            break;
        case NotificationPolicy::kOpportunistic:
            policy.setOpportunistic(notificationPolicyData);
            break;
        case NotificationPolicy::kHighWaterMark:
            policy.setHighWaterMark(notificationPolicyData);
            break;
        case NotificationPolicy::kPeriodic:
            policy.setPeriodic(notificationPolicyData);
            break;
        case NotificationPolicy::kStreaming:
            policy.setStreaming();
            break;
        default:
            ALOGE("Invalid notification policy: %d", notificationPolicy);
            break;
    }
    if (canOverwrite) {
        policy.setOverwritable();
    }
    return policy;
}

static std::array<std::byte, 16> getSinkNameArray(jlong sinkHubId, jlong sinkEndpointId) {
    // The sink name is a combination of the hub ID and endpoint ID.
    // The size of the array is chosen to be large enough to hold both jlongs.
    // The static_assert ensures this assumption holds.
    std::array<std::byte, 16> sinkName;
    static_assert(sizeof(sinkHubId) + sizeof(sinkEndpointId) <= sinkName.size());

    memcpy(sinkName.data(), &sinkHubId, sizeof(sinkHubId));
    memcpy(sinkName.data() + sizeof(sinkHubId), &sinkEndpointId, sizeof(sinkEndpointId));

    return sinkName;
}

static jint android_hardware_HubEndpoint_mapOffloadSinkRegion(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint sourceRegionId, jint dataFlowId,
        jlong sinkHubId, jlong sinkEndpointId, jint regionId, jlong regionSize, jint regionFd,
        jint notificationPolicy, jint notificationPolicyData, jboolean canOverwrite) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(sourceRegionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, 0, "Source not found for regionId");

    SharedDataRegion region = {.id = regionId,
                               .sharedMemory = ndk::ScopedFileDescriptor(dup(regionFd)),
                               .sizeBytes = regionSize};
    EndpointId id = {.id = sinkEndpointId, .hubId = sinkHubId};
    auto result = resource->getRegionManager()->mapOffloadConsumerRegion(std::move(region), id,
                                                                         dataFlowId);

    RETURN_ON_FALSE(result.ok(), 0,
                    (std::string("Failed to map offload sink region: ") + result.status().str())
                            .c_str());

    std::array<std::byte, 16> sinkNameArray = getSinkNameArray(sinkHubId, sinkEndpointId);
    pw::ConstByteSpan nameSpan(sinkNameArray.data(), sizeof(sinkHubId) + sizeof(sinkEndpointId));
    ConsumerPolicyBuilder policy =
            createConsumerPolicyBuilder(notificationPolicy, notificationPolicyData, canOverwrite);
    auto consDescOffsetRes =
            sourceWrapper->getProducer().getConsumerManager().addConsumer(nameSpan, policy,
                                                                          &result.value());
    RETURN_ON_FALSE(consDescOffsetRes.ok(), 0,
                    (std::string("Failed to add sink: ") + consDescOffsetRes.status().str())
                            .c_str());

    return consDescOffsetRes.value();
}

static void android_hardware_HubEndpoint_updateSinkPolicy(JNIEnv* env, jobject /*thiz*/,
                                                          jlong handle, jint regionId,
                                                          jlong sinkHubId, jlong sinkEndpointId,
                                                          jint notificationPolicy,
                                                          jint notificationPolicyData,
                                                          jboolean canOverwrite) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
        if (sourceWrapper != nullptr) {
            std::array<std::byte, 16> sinkNameArray = getSinkNameArray(sinkHubId, sinkEndpointId);
            pw::ConstByteSpan nameSpan(sinkNameArray.data(),
                                       sizeof(sinkHubId) + sizeof(sinkEndpointId));
            ConsumerPolicyBuilder policy =
                    createConsumerPolicyBuilder(notificationPolicy, notificationPolicyData,
                                                canOverwrite);
            sourceWrapper->getProducer().getConsumerManager().updateConsumerPolicy(nameSpan,
                                                                                   policy);
        }
    }
}

static void android_hardware_HubEndpoint_removeOffloadSink(JNIEnv* env, jobject /* thiz */,
                                                           jlong handle, jint regionId,
                                                           jlong sinkHubId, jlong sinkEndpointId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
        if (sourceWrapper != nullptr) {
            EndpointId id = {.id = sinkEndpointId, .hubId = sinkHubId};
            sourceWrapper->removeOffloadSink(id);
        }
    }
}

static void android_hardware_HubEndpoint_removeHostSource(JNIEnv* env, jobject /* thiz */,
                                                          jlong handle, jint regionId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        resource->removeSourceWrapper(regionId);
    }
}

static void android_hardware_HubEndpoint_removeHostSink(JNIEnv* env, jobject /* thiz */,
                                                        jlong handle, jint dataFlowId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        resource->removeSinkWrapper(dataFlowId);
    }
}

static void android_hardware_HubEndpoint_deinit(JNIEnv* env, jobject thiz, jlong handle) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        env->DeleteGlobalRef(resource->getCallbackObject());
        delete resource;
    }
}

static const JNINativeMethod method_table[] = {
        {"native_init",
         "(Landroid/os/MessageQueue;Landroid/hardware/contexthub/"
         "HubEndpoint$DataFlowJniCallback;J)J",
         (void*)android_hardware_HubEndpoint_init},
        {"native_createDataFlowInfo", "(JIJIIIII)[I",
         (void*)android_hardware_HubEndpoint_createDataFlowInfo},
        {"native_activateDataFlow", "(JII)Z", (void*)android_hardware_HubEndpoint_activateDataFlow},
        {"native_enableHostSink", "(JIJIJIIIIIIJJ)[I",
         (void*)android_hardware_HubEndpoint_enableHostSink},
        {"native_sourcePush", "(JI[BZ)I", (void*)android_hardware_HubEndpoint_sourcePush},
        {"native_sourceFull", "(JI)Z", (void*)android_hardware_HubEndpoint_sourceFull},
        {"native_sourceSize", "(JIZ)I", (void*)android_hardware_HubEndpoint_sourceSize},
        {"native_sinkRequestData", "(JIIZ)[B", (void*)android_hardware_HubEndpoint_sinkRequestData},
        {"native_sinkSyncToSource", "(JII)Z", (void*)android_hardware_HubEndpoint_sinkSyncToSource},
        {"native_sinkSourceCanOverwriteReadPosition", "(JI)Z",
         (void*)android_hardware_HubEndpoint_sinkSourceCanOverwriteReadPosition},
        {"native_sinkSize", "(JI)I", (void*)android_hardware_HubEndpoint_sinkSize},
        {"native_addOffloadSink", "(JIJJ)[I", (void*)android_hardware_HubEndpoint_addOffloadSink},
        {"native_mapOffloadSinkRegion", "(JIIJJIJIIIZ)I",
         (void*)android_hardware_HubEndpoint_mapOffloadSinkRegion},
        {"native_updateSinkPolicy", "(JIJJIIZ)V",
         (void*)android_hardware_HubEndpoint_updateSinkPolicy},
        {"native_removeOffloadSink", "(JIJJ)V",
         (void*)android_hardware_HubEndpoint_removeOffloadSink},
        {"native_removeHostSource", "(JI)V", (void*)android_hardware_HubEndpoint_removeHostSource},
        {"native_removeHostSink", "(JI)V", (void*)android_hardware_HubEndpoint_removeHostSink},
        {"native_deinit", "(J)V", (void*)android_hardware_HubEndpoint_deinit},
};

int register_android_hardware_HubEndpoint(JNIEnv* env) {
    if (env->GetJavaVM(&gVm) != JNI_OK) {
        ALOGE("Failed to get JavaVM from JNIEnv: %p", env);
    }
    return RegisterMethodsOrDie(env, "android/hardware/contexthub/HubEndpoint", method_table,
                                NELEM(method_table));
}
