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
using ::aidl::android::hardware::contexthub::DataFlowConsumerHandle;
using ::aidl::android::hardware::contexthub::DataFlowId;
using ::aidl::android::hardware::contexthub::DataFlowNotificationFds;
using ::aidl::android::hardware::contexthub::EndpointId;
using ::aidl::android::hardware::contexthub::SharedDataRegion;
using android::contexthub::data_flow::AllocatorRegion;
using android::contexthub::data_flow::ConsumerPolicyBuilder;
using android::contexthub::data_flow::createQueueUntyped;
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

class ProducerWrapper {
public:
    ProducerWrapper(UntypedProducer&& producer, void* queue, AllocatorRegion allocator,
                    NotificationManager::NotificationDataHandle notificationDataHandle)
          : mProducer(std::move(producer)),
            mQueue(queue),
            mAllocator(allocator),
            mNotificationDataHandle(notificationDataHandle) {}

    ProducerWrapper(ProducerWrapper&& other)
          : mProducer(std::move(other.mProducer)),
            mQueue(other.mQueue),
            mAllocator(other.mAllocator),
            mNotificationDataHandle(other.mNotificationDataHandle) {}

    UntypedProducer& getProducer() {
        return mProducer;
    }

    void* getQueue() {
        return mQueue;
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

    std::set<EndpointId>& getOffloadConsumers() {
        return mOffloadConsumers;
    }

    void removeOffloadConsumer(EndpointId endpointId) {
        mOffloadConsumers.erase(endpointId);
    }

private:
    UntypedProducer mProducer;
    void* mQueue;
    AllocatorRegion mAllocator;
    NotificationManager::NotificationDataHandle mNotificationDataHandle;
    std::optional<DataFlowId> mDataFlowId;

    std::set<EndpointId> mOffloadConsumers;
};

class ConsumerWrapper {
public:
    ConsumerWrapper(UntypedConsumer&& consumer, DataFlowId dataFlowId)
          : mConsumer(std::move(consumer)), mDataFlowId(dataFlowId) {}
    ConsumerWrapper(ConsumerWrapper&& other)
          : mConsumer(std::move(other.mConsumer)), mDataFlowId(other.mDataFlowId) {}

    UntypedConsumer& getConsumer() {
        return mConsumer;
    }
    DataFlowId getDataFlowId() {
        return mDataFlowId;
    }

    ~ConsumerWrapper() {
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
        for (auto& [id, producerWrapper] : mProducers) {
            removeProducerWrapper(id);
        }
        for (auto& [id, consumerWrapper] : mConsumers) {
            removeConsumerWrapper(id);
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

    ProducerWrapper* getProducerWrapper(int regionId) {
        auto it = mProducers.find(regionId);
        if (it == mProducers.end()) {
            return nullptr;
        }
        return &it->second;
    }

    void removeProducerWrapper(int regionId) {
        ProducerWrapper* wrapper = getProducerWrapper(regionId);
        if (wrapper != nullptr) {
            auto dataFlowId = wrapper->getDataFlowId();
            if (dataFlowId.has_value()) {
                mNotificationManager->removeHostProducerDataFlow(dataFlowId.value().id);
            }
            auto* allocator = wrapper->getAllocator()->allocator;
            mProducers.erase(regionId);
            allocator->Deallocate(wrapper->getQueue(), queueLayout());
            mRegionManager.unmapHostProducerRegion(regionId);
        }
    }

    void addProducerWrapper(int regionId, ProducerWrapper&& producerWrapper) {
        mProducers.emplace(regionId, std::move(producerWrapper));
    }

    ConsumerWrapper* getConsumerWrapper(int dataFlowId) {
        auto it = mConsumers.find(dataFlowId);
        if (it == mConsumers.end()) {
            return nullptr;
        }
        return &it->second;
    }

    void removeConsumerWrapper(int dataFlowId) {
        ConsumerWrapper* wrapper = getConsumerWrapper(dataFlowId);
        if (wrapper != nullptr) {
            auto dataFlowId = wrapper->getDataFlowId();
            mNotificationManager->disableHostConsumer(dataFlowId);
            mConsumers.erase(dataFlowId.id);
            mRegionManager.unlinkHostConsumerDataFlow(dataFlowId);
        }
    }

    void addConsumerWrapper(int dataFlowId, ConsumerWrapper&& consumerWrapper) {
        mConsumers.emplace(dataFlowId, std::move(consumerWrapper));
    }

private:
    sp<MessageQueue> mMessageQueue;
    jobject mCallbackObject;
    long mHubId;
    RegionManager mRegionManager;
    std::shared_ptr<NotificationManager> mNotificationManager;
    DataNotifier mNotifier;

    // Key is the shared data region ID.
    std::map<int, ProducerWrapper> mProducers;
    // Key is the data flow ID.
    std::map<int, ConsumerWrapper> mConsumers;
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

    constexpr size_t kBlockCapacity = 1024;
    auto queue = createQueueUntyped(*alloc.value().allocator, kBlockCapacity,
                                    static_cast<size_t>(elementSize),
                                    static_cast<size_t>(alignment), /* local = */ false);
    RETURN_ON_FALSE(queue.ok(), nullptr,
                    (std::string("Failed to create queue: ") + queue.status().str()).c_str());

    RemoteNotifyArgs args = {.fn = [&](pw::ConstByteSpan /*id*/) {}, .id = {}};

    size_t minElementSize = static_cast<size_t>(elementSize) * minElementCount;
    size_t maxElementSize = static_cast<size_t>(elementSize) * maxElementCount;
    // The min block count >= 1, and maxBlockCount >= minBlockCount
    size_t minBlockCount = 1 + (minElementSize - 1) / kBlockCapacity;
    size_t maxBlockCount = 1 + (maxElementSize - 1) / kBlockCapacity;
    auto producer =
            UntypedProducer::createRemote(alloc.value(), queue.value(), maxBlockCount,
                                          minBlockCount, resource->getNotifier(), std::move(args));
    RETURN_ON_FALSE(producer.ok(), nullptr,
                    (std::string("Failed to create producer: ") + producer.status().str()).c_str());

    size_t queueOffset = reinterpret_cast<uintptr_t>(queue.value()) - alloc.value().base;
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
    out.push_back(dup(dataFlowInfo.notificationFds.waking.get()));
    out.push_back(dup(dataFlowInfo.notificationFds.nonWaking.get()));
    out.push_back(dup(dataFlowInfo.notificationFds.halAck.get()));
    jintArray javaArray = env->NewIntArray(out.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create int array");
    env->SetIntArrayRegion(javaArray, 0, out.size(), out.data());

    resource->addProducerWrapper(regionId,
                                 ProducerWrapper(std::move(*producer), queue.value(), alloc.value(),
                                                 notificationDataHandle));

    return javaArray;
}

static jboolean android_hardware_HubEndpoint_activateDataFlow(JNIEnv* env, jobject thiz,
                                                              jlong handle, jint dataFlowId,
                                                              jint regionId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    ProducerWrapper* producerWrapper = resource->getProducerWrapper(regionId);
    RETURN_ON_FALSE(producerWrapper != nullptr, JNI_FALSE, "Producer not found for regionId");

    auto status =
            resource->getNotificationManager()
                    ->activateHostProducerDataFlow(dataFlowId,
                                                   producerWrapper->getNotificationDataHandle());
    RETURN_ON_FALSE(status.ok(), JNI_FALSE,
                    (std::string("Failed to activate host producer data flow: ") + status.str())
                            .c_str());
    status = resource->getRegionManager()->linkHostProducerDataFlowToRegion(regionId, dataFlowId);
    RETURN_ON_FALSE(status.ok(), JNI_FALSE,
                    (std::string("Failed to link host producer data flow to region: ") +
                     status.str())
                            .c_str());

    producerWrapper->setDataFlowId({
            .hubId = resource->getHubId(),
            .id = static_cast<int32_t>(dataFlowId),
    });

    return JNI_TRUE; // Success
}

static jintArray android_hardware_HubEndpoint_enableHostConsumer(
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
                    (std::string("Failed to map consumer region: ") + result.status().str())
                            .c_str());
    auto [region, _] = std::move(result.value());

    DataFlowNotificationFds notifyHostFds =
            {.waking = ndk::ScopedFileDescriptor(dup(notifyHostFdsWaking)),
             .nonWaking = ndk::ScopedFileDescriptor(dup(notifyHostFdsNonWaking)),
             .halAck = ndk::ScopedFileDescriptor(dup(notifyHostFdsHalAck))};
    DataFlowNotificationFds notifyOffloadFds = {.waking = ndk::ScopedFileDescriptor(
                                                        dup(notifyOffloadFdsWaking)),
                                                .nonWaking = ndk::ScopedFileDescriptor(
                                                        dup(notifyOffloadFdsNonWaking))};
    auto status = resource->getNotificationManager()
                          ->enableHostConsumerFromEventFds(id, std::move(notifyHostFds),
                                                           std::move(notifyOffloadFds));
    RETURN_ON_FALSE(status.ok(), nullptr,
                    (std::string("Failed to enable host consumer: ") + status.str()).c_str());

    RemoteNotifyArgs args = {.fn = [](pw::ConstByteSpan /*id*/) { return; }, .id = {}};
    auto consumer = UntypedConsumer::createRemote(region, std::nullopt, queueOffset, metadataOffset,
                                                  std::move(args));
    RETURN_ON_FALSE(consumer.ok(), nullptr,
                    (std::string("Failed to create consumer: ") + consumer.status().str()).c_str());

    std::vector<jint> out;
    out.push_back(consumer->getElementSize());
    out.push_back(consumer->getElementAlignment());
    jintArray javaArray = env->NewIntArray(out.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create int array");
    env->SetIntArrayRegion(javaArray, 0, out.size(), out.data());

    resource->addConsumerWrapper(id.id, ConsumerWrapper(std::move(*consumer), id));

    return javaArray;
}

static jint android_hardware_HubEndpoint_sourcePush(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                    jint regionId, jbyteArray data,
                                                    jboolean allOrNothing) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    ProducerWrapper* producerWrapper = resource->getProducerWrapper(regionId);
    RETURN_ON_FALSE(producerWrapper != nullptr, JNI_FALSE, "Producer not found for regionId");

    UntypedProducer& producer = producerWrapper->getProducer();

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
    for (auto& consumer : producerWrapper->getOffloadConsumers()) {
        ALOGI("Notifying offload consumer: endpoint id=0x%" PRIx64, consumer.id);
        auto status =
                resource->getNotificationManager()->notifyOffloadConsumer(consumer,
                                                                          /* waking = */ true);
        RETURN_ON_FALSE(status.ok(), 0,
                        (std::string("Failed to notify offload consumer: ") + status.str())
                                .c_str());
    }

    return static_cast<jint>(pushResult.value());
}

static jboolean android_hardware_HubEndpoint_sourceFull(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                        jint regionId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    ProducerWrapper* producerWrapper = resource->getProducerWrapper(regionId);
    RETURN_ON_FALSE(producerWrapper != nullptr, JNI_FALSE, "Producer not found for regionId");

    return static_cast<jboolean>(producerWrapper->getProducer().full());
}

static jint android_hardware_HubEndpoint_sourceSize(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                    jint regionId, jboolean includeReserved) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    ProducerWrapper* producerWrapper = resource->getProducerWrapper(regionId);
    RETURN_ON_FALSE(producerWrapper != nullptr, 0, "Producer not found for regionId");

    return static_cast<jint>(producerWrapper->getProducer().size(includeReserved));
}

static jbyteArray android_hardware_HubEndpoint_sinkRequestData(JNIEnv* env, jobject /*thiz*/,
                                                               jlong handle, jint dataFlowId,
                                                               jint elementCount,
                                                               jboolean allOrNothing) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, nullptr, "Invalid handle");

    ConsumerWrapper* consumerWrapper = resource->getConsumerWrapper(dataFlowId);
    RETURN_ON_FALSE(consumerWrapper != nullptr, nullptr, "Consumer not found for dataFlowId");

    UntypedConsumer& consumer = consumerWrapper->getConsumer();
    if (allOrNothing) {
        auto sizeResult = consumer.size();
        RETURN_ON_FALSE(sizeResult.ok(), nullptr,
                        (std::string("Failed to get consumer size: ") + sizeResult.status().str())
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

    ConsumerWrapper* consumerWrapper = resource->getConsumerWrapper(dataFlowId);
    RETURN_ON_FALSE(consumerWrapper != nullptr, JNI_FALSE, "Consumer not found for dataFlowId");

    auto syncStatus = consumerWrapper->getConsumer().resync(offset);
    RETURN_ON_FALSE(syncStatus.ok(), JNI_FALSE,
                    (std::string("Failed to resync consumer: ") + syncStatus.str()).c_str());
    return JNI_TRUE;
}

static jboolean android_hardware_HubEndpoint_sinkSourceCanOverwriteReadPosition(JNIEnv* env,
                                                                                jobject /*thiz*/,
                                                                                jlong handle,
                                                                                jint dataFlowId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    ConsumerWrapper* consumerWrapper = resource->getConsumerWrapper(dataFlowId);
    RETURN_ON_FALSE(consumerWrapper != nullptr, JNI_FALSE, "Consumer not found for dataFlowId");

    // TODO(b/457452333): Implement
    (void)consumerWrapper;
    return JNI_FALSE;
}

static jint android_hardware_HubEndpoint_sinkSize(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                  jint dataFlowId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    ConsumerWrapper* consumerWrapper = resource->getConsumerWrapper(dataFlowId);
    RETURN_ON_FALSE(consumerWrapper != nullptr, 0, "Consumer not found for dataFlowId");

    auto sizeResult = consumerWrapper->getConsumer().size();
    RETURN_ON_FALSE(sizeResult.ok(), 0,
                    (std::string("Failed to get consumer size: ") + sizeResult.status().str())
                            .c_str());
    return static_cast<jint>(sizeResult.value());
}

static jintArray android_hardware_HubEndpoint_addOffloadConsumer(JNIEnv* env, jobject /*thiz*/,
                                                                 jlong handle, jint regionId,
                                                                 jlong hubId, jlong endpointId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, nullptr, "Invalid handle");

    ProducerWrapper* producerWrapper = resource->getProducerWrapper(regionId);
    RETURN_ON_FALSE(producerWrapper != nullptr, nullptr, "Producer not found for regionId");
    auto dataFlowId = producerWrapper->getDataFlowId();
    RETURN_ON_FALSE(dataFlowId.has_value(), nullptr, "Data flow ID not set for producer");

    EndpointId consumerEndpointId;
    consumerEndpointId.hubId = hubId;
    consumerEndpointId.id = endpointId;
    auto result =
            resource->getNotificationManager()
                    ->addOffloadConsumerAndGetEventFds(dataFlowId.value().id, consumerEndpointId);
    RETURN_ON_FALSE(result.ok(), nullptr,
                    (std::string("Failed to add offload consumer: ") + result.status().str())
                            .c_str());

    std::vector<jint> out;
    out.push_back(dup(result->waking.get()));
    out.push_back(dup(result->nonWaking.get()));
    jintArray javaArray = env->NewIntArray(out.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create int array");
    env->SetIntArrayRegion(javaArray, 0, out.size(), out.data());

    producerWrapper->getOffloadConsumers().insert(consumerEndpointId);

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

static std::array<std::byte, 16> getConsumerNameArray(jlong consumerHubId,
                                                      jlong consumerEndpointId) {
    // The consumer name is a combination of the hub ID and endpoint ID.
    // The size of the array is chosen to be large enough to hold both jlongs.
    // The static_assert ensures this assumption holds.
    std::array<std::byte, 16> consumerName;
    static_assert(sizeof(consumerHubId) + sizeof(consumerEndpointId) <= consumerName.size());

    memcpy(consumerName.data(), &consumerHubId, sizeof(consumerHubId));
    memcpy(consumerName.data() + sizeof(consumerHubId), &consumerEndpointId,
           sizeof(consumerEndpointId));

    return consumerName;
}

static jint android_hardware_HubEndpoint_mapOffloadConsumerRegion(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint producerRegionId, jint dataFlowId,
        jlong consumerHubId, jlong consumerEndpointId, jint regionId, jlong regionSize,
        jint regionFd, jint notificationPolicy, jint notificationPolicyData,
        jboolean canOverwrite) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    ProducerWrapper* producerWrapper = resource->getProducerWrapper(producerRegionId);
    RETURN_ON_FALSE(producerWrapper != nullptr, 0, "Producer not found for regionId");

    SharedDataRegion region = {.id = regionId,
                               .sharedMemory = ndk::ScopedFileDescriptor(dup(regionFd)),
                               .sizeBytes = regionSize};
    EndpointId id = {.id = consumerEndpointId, .hubId = consumerHubId};
    auto result = resource->getRegionManager()->mapOffloadConsumerRegion(std::move(region), id,
                                                                         dataFlowId);

    RETURN_ON_FALSE(result.ok(), 0,
                    (std::string("Failed to map offload consumer region: ") + result.status().str())
                            .c_str());

    std::array<std::byte, 16> consumerNameArray =
            getConsumerNameArray(consumerHubId, consumerEndpointId);
    pw::ConstByteSpan nameSpan(consumerNameArray.data(),
                               sizeof(consumerHubId) + sizeof(consumerEndpointId));
    ConsumerPolicyBuilder policy =
            createConsumerPolicyBuilder(notificationPolicy, notificationPolicyData, canOverwrite);
    auto consDescOffsetRes =
            producerWrapper->getProducer().getConsumerManager().addConsumer(nameSpan, policy,
                                                                            &result.value());
    RETURN_ON_FALSE(consDescOffsetRes.ok(), 0,
                    (std::string("Failed to add consumer: ") + consDescOffsetRes.status().str())
                            .c_str());

    return consDescOffsetRes.value();
}

static void android_hardware_HubEndpoint_updateSinkPolicy(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint regionId, jlong consumerHubId,
        jlong consumerEndpointId, jint notificationPolicy, jint notificationPolicyData,
        jboolean canOverwrite) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        ProducerWrapper* producerWrapper = resource->getProducerWrapper(regionId);
        if (producerWrapper != nullptr) {
            std::array<std::byte, 16> consumerNameArray =
                    getConsumerNameArray(consumerHubId, consumerEndpointId);
            pw::ConstByteSpan nameSpan(consumerNameArray.data(),
                                       sizeof(consumerHubId) + sizeof(consumerEndpointId));
            ConsumerPolicyBuilder policy =
                    createConsumerPolicyBuilder(notificationPolicy, notificationPolicyData,
                                                canOverwrite);
            producerWrapper->getProducer().getConsumerManager().updateConsumerPolicy(nameSpan,
                                                                                     policy);
        }
    }
}

static void android_hardware_HubEndpoint_removeOffloadSink(JNIEnv* env, jobject /* thiz */,
                                                           jlong handle, jint regionId,
                                                           jlong consumerHubId,
                                                           jlong consumerEndpointId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        ProducerWrapper* producerWrapper = resource->getProducerWrapper(regionId);
        if (producerWrapper != nullptr) {
            EndpointId id = {.id = consumerEndpointId, .hubId = consumerHubId};
            producerWrapper->removeOffloadConsumer(id);
        }
    }
}

static void android_hardware_HubEndpoint_removeHostSource(JNIEnv* env, jobject /* thiz */,
                                                          jlong handle, jint regionId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        resource->removeProducerWrapper(regionId);
    }
}

static void android_hardware_HubEndpoint_removeHostSink(JNIEnv* env, jobject /* thiz */,
                                                        jlong handle, jint dataFlowId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        resource->removeConsumerWrapper(dataFlowId);
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
        {"native_enableHostConsumer", "(JIJIJIIIIIIJJ)[I",
         (void*)android_hardware_HubEndpoint_enableHostConsumer},
        {"native_sourcePush", "(JI[BZ)I", (void*)android_hardware_HubEndpoint_sourcePush},
        {"native_sourceFull", "(JI)Z", (void*)android_hardware_HubEndpoint_sourceFull},
        {"native_sourceSize", "(JIZ)I", (void*)android_hardware_HubEndpoint_sourceSize},
        {"native_sinkRequestData", "(JIIZ)[B", (void*)android_hardware_HubEndpoint_sinkRequestData},
        {"native_sinkSyncToSource", "(JII)Z", (void*)android_hardware_HubEndpoint_sinkSyncToSource},
        {"native_sinkSourceCanOverwriteReadPosition", "(JI)Z",
         (void*)android_hardware_HubEndpoint_sinkSourceCanOverwriteReadPosition},
        {"native_sinkSize", "(JI)I", (void*)android_hardware_HubEndpoint_sinkSize},
        {"native_addOffloadConsumer", "(JIJJ)[I",
         (void*)android_hardware_HubEndpoint_addOffloadConsumer},
        {"native_mapOffloadConsumerRegion", "(JIIJJIJIIIZ)I",
         (void*)android_hardware_HubEndpoint_mapOffloadConsumerRegion},
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
