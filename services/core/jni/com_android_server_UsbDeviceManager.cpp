/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "UsbDeviceManagerJNI"
#include <android-base/properties.h>
#include <android-base/unique_fd.h>
#include <core_jni_helpers.h>
#include <fcntl.h>
#include <linux/usb/f_accessory.h>
#include <nativehelper/JNIPlatformHelp.h>
#include <nativehelper/ScopedUtfChars.h>
#include <stdio.h>
#include <sys/epoll.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <thread>

#include "MtpDescriptors.h"
#include "android_runtime/AndroidRuntime.h"
#include "jni.h"
#include "utils/Log.h"

#define DRIVER_NAME "/dev/usb_accessory"
#define EPOLL_MAX_EVENTS 4
#define FFS_NUM_EVENTS 5
#define USB_STATE_MAX_LEN 20
#define FFS_VENDOR_CTRL_REQUEST_EP0 "/dev/usb-ffs/ctrl/ep0"

#define FFS_ACCESSORY_EP0 "/dev/usb-ffs/aoa/ep0"

namespace {
struct func_desc {
    struct usb_interface_descriptor intf;
    struct usb_endpoint_descriptor_no_audio source;
    struct usb_endpoint_descriptor_no_audio sink;
} __attribute__((packed));

struct func_desc_ss {
    struct usb_interface_descriptor intf;
    struct usb_endpoint_descriptor_no_audio source;
    struct usb_ss_ep_comp_descriptor source_comp;
    struct usb_endpoint_descriptor_no_audio sink;
    struct usb_ss_ep_comp_descriptor sink_comp;
} __attribute__((packed));

struct desc_v2 {
    struct usb_functionfs_descs_head_v2 header;
    // The rest of the structure depends on the flags in the header.
    __le32 fs_count;
    __le32 hs_count;
    __le32 ss_count;
    struct func_desc fs_descs, hs_descs;
    struct func_desc_ss ss_descs;
} __attribute__((packed));

const struct usb_interface_descriptor interface_desc = {
        .bLength = USB_DT_INTERFACE_SIZE,
        .bDescriptorType = USB_DT_INTERFACE,
        .bInterfaceNumber = 0,
        .bNumEndpoints = 2,
        .bInterfaceClass = USB_CLASS_VENDOR_SPEC,
        .bInterfaceSubClass = USB_SUBCLASS_VENDOR_SPEC,
        .bInterfaceProtocol = 0,
        .iInterface = 1,
};

const struct usb_endpoint_descriptor_no_audio fs_sink = {
        .bLength = USB_DT_ENDPOINT_SIZE,
        .bDescriptorType = USB_DT_ENDPOINT,
        .bEndpointAddress = 1 | USB_DIR_IN,
        .bmAttributes = USB_ENDPOINT_XFER_BULK,
        .wMaxPacketSize = htole16(64),
};

const struct usb_endpoint_descriptor_no_audio fs_source = {
        .bLength = USB_DT_ENDPOINT_SIZE,
        .bDescriptorType = USB_DT_ENDPOINT,
        .bEndpointAddress = 2 | USB_DIR_OUT,
        .bmAttributes = USB_ENDPOINT_XFER_BULK,
        .wMaxPacketSize = htole16(64),
};

const struct usb_endpoint_descriptor_no_audio hs_sink = {
        .bLength = USB_DT_ENDPOINT_SIZE,
        .bDescriptorType = USB_DT_ENDPOINT,
        .bEndpointAddress = 1 | USB_DIR_IN,
        .bmAttributes = USB_ENDPOINT_XFER_BULK,
        .wMaxPacketSize = htole16(512),
};

const struct usb_endpoint_descriptor_no_audio hs_source = {
        .bLength = USB_DT_ENDPOINT_SIZE,
        .bDescriptorType = USB_DT_ENDPOINT,
        .bEndpointAddress = 2 | USB_DIR_OUT,
        .bmAttributes = USB_ENDPOINT_XFER_BULK,
        .wMaxPacketSize = htole16(512),
};

const struct usb_endpoint_descriptor_no_audio ss_sink = {
        .bLength = USB_DT_ENDPOINT_SIZE,
        .bDescriptorType = USB_DT_ENDPOINT,
        .bEndpointAddress = 1 | USB_DIR_IN,
        .bmAttributes = USB_ENDPOINT_XFER_BULK,
        .wMaxPacketSize = htole16(1024),
};

const struct usb_endpoint_descriptor_no_audio ss_source = {
        .bLength = USB_DT_ENDPOINT_SIZE,
        .bDescriptorType = USB_DT_ENDPOINT,
        .bEndpointAddress = 2 | USB_DIR_OUT,
        .bmAttributes = USB_ENDPOINT_XFER_BULK,
        .wMaxPacketSize = htole16(1024),
};

const struct usb_ss_ep_comp_descriptor ss_sink_comp = {
        .bLength = sizeof(ss_sink_comp),
        .bDescriptorType = USB_DT_SS_ENDPOINT_COMP,
        .bMaxBurst = 6,
};

const struct usb_ss_ep_comp_descriptor ss_source_comp = {
        .bLength = sizeof(ss_source_comp),
        .bDescriptorType = USB_DT_SS_ENDPOINT_COMP,
        .bMaxBurst = 6,
};

const struct func_desc fs_descriptors = {
        .intf = interface_desc,
        .source = fs_source,
        .sink = fs_sink,
};

const struct func_desc hs_descriptors = {
        .intf = interface_desc,
        .source = hs_source,
        .sink = hs_sink,
};

const struct func_desc_ss ss_descriptors = {
        .intf = interface_desc,
        .source = ss_source,
        .source_comp = ss_source_comp,
        .sink = ss_sink,
        .sink_comp = ss_sink_comp,
};

const struct desc_v2 ctrl_desc = {
        .header =
                {
                        .magic = htole32(FUNCTIONFS_DESCRIPTORS_MAGIC_V2),
                        .length = htole32(sizeof(ctrl_desc)),
                        .flags = FUNCTIONFS_ALL_CTRL_RECIP | FUNCTIONFS_CONFIG0_SETUP |
                                FUNCTIONFS_HAS_FS_DESC | FUNCTIONFS_HAS_HS_DESC |
                                FUNCTIONFS_HAS_SS_DESC,
                },
        .fs_count = 3,
        .hs_count = 3,
        .ss_count = 5,
        .fs_descs = fs_descriptors,
        .hs_descs = hs_descriptors,
        .ss_descs = ss_descriptors,
};

#define CTRL_INTERFACE_STR "Android Control Interface"
struct ctrl_functionfs_lang {
    __le16 code;
    char str1[sizeof(CTRL_INTERFACE_STR)];
} __attribute__((packed));

struct ctrl_functionfs_strings {
    struct usb_functionfs_strings_head header;
    struct ctrl_functionfs_lang lang0;
} __attribute__((packed));

const struct ctrl_functionfs_strings ctrl_strings = {
        .header =
                {
                        .magic = htole32(FUNCTIONFS_STRINGS_MAGIC),
                        .length = htole32(sizeof(ctrl_strings)),
                        .str_count = htole32(1),
                        .lang_count = htole32(1),
                },
        .lang0 =
                {
                        .code = htole16(0x0409),
                        .str1 = CTRL_INTERFACE_STR,
                },
};

const struct desc_v2 acc_desc = {
    .header =
            {
                    .magic = htole32(FUNCTIONFS_DESCRIPTORS_MAGIC_V2),
                    .length = htole32(sizeof(acc_desc)),
                    .flags = FUNCTIONFS_HAS_FS_DESC | FUNCTIONFS_HAS_HS_DESC |
                            FUNCTIONFS_HAS_SS_DESC,
            },
    .fs_count = 3,
    .hs_count = 3,
    .ss_count = 5,
    .fs_descs = fs_descriptors,
    .hs_descs = hs_descriptors,
    .ss_descs = ss_descriptors,
};

#define ACC_INTERFACE_STR "Android Accessory Interface"
struct acc_functionfs_lang {
    __le16 code;
    char str1[sizeof(ACC_INTERFACE_STR)];
} __attribute__((packed));

struct acc_functionfs_strings {
    struct usb_functionfs_strings_head header;
    struct acc_functionfs_lang lang0;
} __attribute__((packed));

const struct acc_functionfs_strings acc_strings = {
        .header =
                {
                        .magic = htole32(FUNCTIONFS_STRINGS_MAGIC),
                        .length = htole32(sizeof(acc_strings)),
                        .str_count = htole32(1),
                        .lang_count = htole32(1),
                },
        .lang0 =
                {
                        .code = htole16(0x0409),
                        .str1 = ACC_INTERFACE_STR,
                },
};


} // namespace

namespace android
{

static JavaVM *gvm = nullptr;
static jmethodID gUpdateGadgetStateMethod;
static jmethodID gUpdateAccessoryStateMethod;

static struct parcel_file_descriptor_offsets_t
{
    jclass mClass;
    jmethodID mConstructor;
} gParcelFileDescriptorOffsets;

/*
 * NativeGadgetMonitorThread starts a new thread to monitor udc state by epoll,
 * convert and update the state to UsbDeviceManager.
 */
class NativeGadgetMonitorThread {
    android::base::unique_fd mMonitorFd;
    int mPipefd[2];
    std::thread mThread;
    jobject mCallbackObj;
    std::string mGadgetState;

    void handleStateUpdate(const char *state) {
        JNIEnv *env = AndroidRuntime::getJNIEnv();
        std::string gadgetState;

        if (!std::strcmp(state, "not attached\n")) {
            gadgetState = "DISCONNECTED";
        } else if (!std::strcmp(state, "attached\n") || !std::strcmp(state, "powered\n") ||
                   !std::strcmp(state, "default\n") || !std::strcmp(state, "addressed\n")) {
            gadgetState = "CONNECTED";
        } else if (!std::strcmp(state, "configured\n")) {
            gadgetState = "CONFIGURED";
        } else if (!std::strcmp(state, "suspended\n")) {
            return;
        } else {
            ALOGE("Unknown gadget state %s", state);
            return;
        }

        if (mGadgetState.compare(gadgetState)) {
            mGadgetState = gadgetState;
            jstring obj = env->NewStringUTF(gadgetState.c_str());
            env->CallVoidMethod(mCallbackObj, gUpdateGadgetStateMethod, obj);
        }
    }

    int setupEpoll(android::base::unique_fd &epollFd) {
        struct epoll_event ev;

        ev.data.fd = mMonitorFd.get();
        ev.events = EPOLLPRI;
        if (epoll_ctl(epollFd.get(), EPOLL_CTL_ADD, mMonitorFd.get(), &ev) != 0) {
            ALOGE("epoll_ctl failed for monitor fd; errno=%d", errno);
            return errno;
        }

        ev.data.fd = mPipefd[0];
        ev.events = EPOLLIN;
        if (epoll_ctl(epollFd.get(), EPOLL_CTL_ADD, mPipefd[0], &ev) != 0) {
            ALOGE("epoll_ctl failed for pipe fd; errno=%d", errno);
            return errno;
        }

        return 0;
    }

    void monitorLoop() {
        android::base::unique_fd epollFd(epoll_create(EPOLL_MAX_EVENTS));
        if (epollFd.get() == -1) {
            ALOGE("epoll_create failed; errno=%d", errno);
            return;
        }
        if (setupEpoll(epollFd) != 0) return;

        JNIEnv *env = nullptr;
        JavaVMAttachArgs aargs = {JNI_VERSION_1_4, "NativeGadgetMonitorThread", nullptr};
        if (gvm->AttachCurrentThread(&env, &aargs) != JNI_OK || env == nullptr) {
            ALOGE("Couldn't attach thread");
            return;
        }

        struct epoll_event events[EPOLL_MAX_EVENTS];
        int nevents = 0;
        while (true) {
            nevents = epoll_wait(epollFd.get(), events, EPOLL_MAX_EVENTS, -1);
            if (nevents < 0) {
                ALOGE("usb epoll_wait failed; errno=%d", errno);
                continue;
            }
            for (int i = 0; i < nevents; ++i) {
                int fd = events[i].data.fd;
                if (fd == mPipefd[0]) {
                    goto exit;
                } else if (fd == mMonitorFd.get()) {
                    char state[USB_STATE_MAX_LEN] = {0};
                    lseek(fd, 0, SEEK_SET);
                    read(fd, &state, USB_STATE_MAX_LEN);
                    handleStateUpdate(state);
                }
            }
        }

    exit:
        auto res = gvm->DetachCurrentThread();
        ALOGE_IF(res != JNI_OK, "Couldn't detach thread");
        return;
    }

    void stop() {
        if (mThread.joinable()) {
            int c = 'q';
            write(mPipefd[1], &c, 1);
            mThread.join();
        }
    }

    DISALLOW_COPY_AND_ASSIGN(NativeGadgetMonitorThread);

public:
    explicit NativeGadgetMonitorThread(jobject obj, android::base::unique_fd monitorFd)
          : mMonitorFd(std::move(monitorFd)), mGadgetState("") {
        mCallbackObj = AndroidRuntime::getJNIEnv()->NewGlobalRef(obj);
        pipe(mPipefd);
        mThread = std::thread(&NativeGadgetMonitorThread::monitorLoop, this);
    }

    ~NativeGadgetMonitorThread() {
        stop();
        close(mPipefd[0]);
        close(mPipefd[1]);
        AndroidRuntime::getJNIEnv()->DeleteGlobalRef(mCallbackObj);
    }
};
static std::unique_ptr<NativeGadgetMonitorThread> sGadgetMonitorThread;

/*
 * NativeVendorControlRequestMonitorThread starts a new thread to monitor vendor
 * control requests. It issues state changes for accessory mode as required.
 */
class NativeVendorControlRequestMonitorThread {
    // Constants for accessory mode.
    static constexpr int ACCESSORY_VERSION = 2;
    static constexpr int ACCESSORY_NUM_STRINGS = 6;
    static constexpr int ACCESSORY_STRING_LENGTH = 256;

    android::base::unique_fd mMonitorFd;
    int mShutdownPipefd[2];
    std::thread mThread;
    jobject mCallbackObj;

    // Variables for accessory mode.
    std::mutex mAccessoryFieldsMutex;
    struct accessory_fields {
        std::string controlState;
        std::string strings[ACCESSORY_NUM_STRINGS];
        int maxPacketSize;
    } mAccessoryFields;

    bool handleAccessoryGetProtocol(int fd, uint16_t value, uint16_t index, uint16_t length,
                                    std::vector<char> &buf) {
        if (value != 0 || index != 0 || length != 2) {
            ALOGE("Malformed Get protocol");
            return false;
        }
        uint16_t *protocolVersion = reinterpret_cast<uint16_t *>(buf.data());
        protocolVersion[0] = htole16(ACCESSORY_VERSION);
        return true;
    }

    bool handleAccessorySendString(int fd, uint16_t index, uint16_t length,
                                   std::vector<char> &buf) {
        if (index >= ACCESSORY_NUM_STRINGS || length > ACCESSORY_STRING_LENGTH || length == 0) {
            ALOGE("Malformed send string");
            return false;
        }
        if (::read(fd, buf.data(), length) != length) {
            ALOGE("Usb error ctrlreq read string %d", length);
            return false;
        }
        buf[length] = '\0';
        std::string str(buf.data());
        std::lock_guard<std::mutex> lock(mAccessoryFieldsMutex);
        mAccessoryFields.strings[index] = str;
        return true;
    }

    bool handleAccessoryStart(int fd, uint16_t value, uint16_t index, uint16_t length,
                              std::vector<char> &buf) {
        if (value != 0 || index != 0 || length != 0) {
            ALOGE("Malformed start accessory");
            return false;
        }
        if (::read(fd, buf.data(), 0) != 0) {
            ALOGE("Usb error ctrlreq read data");
            return false;
        }
        return true;
    }

    void handleControlRequest(int fd, const struct usb_ctrlrequest *setup) {
        JNIEnv *env = AndroidRuntime::getJNIEnv();

        uint8_t type = setup->bRequestType;
        uint8_t code = setup->bRequest;
        uint16_t length = setup->wLength;
        uint16_t index = setup->wIndex;
        uint16_t value = setup->wValue;
        std::vector<char> buf;
        buf.resize(length + 1);
        std::string accessoryControlState;

        if ((type & USB_TYPE_MASK) == USB_TYPE_VENDOR) {
            switch (code) {
                case ACCESSORY_GET_PROTOCOL: {
                    if (!handleAccessoryGetProtocol(fd, value, index, length, buf) ||
                        !(type & USB_DIR_IN)) {
                        goto fail;
                    }
                    accessoryControlState = "GETPROTOCOL";
                    break;
                }
                case ACCESSORY_SEND_STRING: {
                    if (!handleAccessorySendString(fd, index, length, buf) || (type & USB_DIR_IN)) {
                        goto fail;
                    }
                    accessoryControlState = "SENDSTRING";
                    break;
                }
                case ACCESSORY_START: {
                    if (!handleAccessoryStart(fd, value, index, length, buf) ||
                        (type & USB_DIR_IN)) {
                        goto fail;
                    }
                    accessoryControlState = "START";
                    break;
                }

                // TODO(b/421807206): - Add support for accessory HID requests.

                default:
                    ALOGE("Unrecognized USB vendor request! %d", (int)code);
                    goto fail;
            }
        } else {
            ALOGE("Unrecognized USB request type %d", (int)type);
            goto fail;
        }

        if (type & USB_DIR_IN) {
            if (::write(fd, buf.data(), length) != length) {
                ALOGE("Usb error ctrlreq write data");
                goto fail;
            }
        }

        {
            std::lock_guard<std::mutex> lock(mAccessoryFieldsMutex);
            if (mAccessoryFields.controlState.compare(accessoryControlState) ||
                !accessoryControlState.compare("SENDSTRING")) {
                mAccessoryFields.controlState = accessoryControlState;
            }
        }
        return;
    fail:
        // stall control endpoint by applying opposite i/o
        if (type & USB_DIR_IN) {
            if (::read(fd, buf.data(), 0) != -1 || errno != EL2HLT) {
                ALOGE("Couldn't halt ep0 on in request");
            }
        } else {
            if (::write(fd, buf.data(), 0) != -1 || errno != EL2HLT) {
                ALOGE("Couldn't halt ep0 on out request");
            }
        }
    }

    void teardown() {
        // Add teardown for vendor control requests being handled.
        ALOGI("Vendor control request monitor teardown");

          // Teardown for accessory mode.
          std::lock_guard<std::mutex> lock(mAccessoryFieldsMutex);
          mAccessoryFields.controlState = "";
          for (int i = 0; i < ACCESSORY_NUM_STRINGS; i++) {
              mAccessoryFields.strings[i] = "";
          }
          mAccessoryFields.maxPacketSize = -1;
    }

    int setupEpoll(android::base::unique_fd &epollFd) {
        struct epoll_event ev;

        ev.data.fd = mMonitorFd.get();
        ev.events = EPOLLIN;
        if (epoll_ctl(epollFd.get(), EPOLL_CTL_ADD, mMonitorFd.get(), &ev) != 0) {
            ALOGE("epoll_ctl failed for ctrl request monitor fd; %s", strerror(errno));
            return errno;
        }

        ev.data.fd = mShutdownPipefd[0];
        ev.events = EPOLLIN;
        if (epoll_ctl(epollFd.get(), EPOLL_CTL_ADD, mShutdownPipefd[0], &ev) != 0) {
            ALOGE("epoll_ctl failed for ctrl request pipe fd; %s", strerror(errno));
            return errno;
        }

        return 0;
    }

    void monitorLoop() {
        android::base::unique_fd epollFd(epoll_create(EPOLL_MAX_EVENTS));
        std::vector<struct usb_functionfs_event> ffs_events(FFS_NUM_EVENTS);

        ALOGI("Monitoring vendor control requests...");

        if (epollFd.get() == -1) {
            ALOGE("Vendor control request monitor epoll_create failed; %s", strerror(errno));
            return;
        }

        if (setupEpoll(epollFd) != 0) {
            ALOGE("Vendor control request monitor setupEpoll failed!");
            return;
        }

        JNIEnv *env = nullptr;
        JavaVMAttachArgs aargs = {JNI_VERSION_1_4, "NativeVendorControlRequestMonitorThread",
                                  nullptr};
        if (gvm->AttachCurrentThread(&env, &aargs) != JNI_OK || env == nullptr) {
            ALOGE("Couldn't attach thread");
            return;
        }

        struct epoll_event events[EPOLL_MAX_EVENTS];
        int nevents = 0;
        while (true) {
            nevents = epoll_wait(epollFd.get(), events, EPOLL_MAX_EVENTS, -1);

            if (nevents < 0) {
                if (errno != EINTR)
                    ALOGE("Vendor control request monitor epoll_wait failed; %s", strerror(errno));
                continue;
            }

            for (int i = 0; i < nevents; ++i) {
                int fd = events[i].data.fd;
                if (fd == mShutdownPipefd[0]) {
                    ALOGE("Vendor control request monitor loop exiting...");
                    goto exit;
                } else if (fd == mMonitorFd.get()) {
                    if (events[i].events & EPOLLIN) {
                        struct usb_functionfs_event *event = ffs_events.data();
                        int nbytes = TEMP_FAILURE_RETRY(
                                ::read(fd, event,
                                       ffs_events.size() * sizeof(usb_functionfs_event)));
                        if (nbytes == -1) {
                            ALOGE("error reading Usb control events");
                            continue;
                        }
                        for (size_t n = nbytes / sizeof(*event); n; --n, ++event) {
                            switch (event->type) {
                                case FUNCTIONFS_SETUP:
                                    handleControlRequest(fd, &event->u.setup);
                                    break;
                                case FUNCTIONFS_UNBIND:
                                    teardown();
                                    break;
                                default:
                                    continue;
                            }
                        }
                    }
                }
            }
        }

    exit:
        auto res = gvm->DetachCurrentThread();
        ALOGE("Detaching thread");
        ALOGE_IF(res != JNI_OK, "Couldn't detach thread");
        return;
    }

    void stop() {
        if (mThread.joinable()) {
            int c = 'q';
            write(mShutdownPipefd[1], &c, 1);
            mThread.join();
        }
    }

    DISALLOW_COPY_AND_ASSIGN(NativeVendorControlRequestMonitorThread);

public:
    explicit NativeVendorControlRequestMonitorThread(jobject obj,
                                                     android::base::unique_fd monitorFd)
          : mMonitorFd(std::move(monitorFd)),
            mAccessoryFields({.controlState = "",
                              .strings = {"", "", "", "", "", ""},
                              .maxPacketSize = -1}) {
        mCallbackObj = AndroidRuntime::getJNIEnv()->NewGlobalRef(obj);
        pipe(mShutdownPipefd);
        mThread = std::thread(&NativeVendorControlRequestMonitorThread::monitorLoop, this);
    }

    std::string getAccessoryString(int index) {
        if (index < 0 || index >= ACCESSORY_NUM_STRINGS) {
            ALOGE("Invalid accessory string index %d", index);
            return "";
        }
        std::lock_guard<std::mutex> lock(mAccessoryFieldsMutex);
        return mAccessoryFields.strings[index];
    }

    int getMaxPacketSize() {
        std::lock_guard<std::mutex> lock(mAccessoryFieldsMutex);
        return mAccessoryFields.maxPacketSize;
    }

    ~NativeVendorControlRequestMonitorThread() {
        stop();
        close(mShutdownPipefd[0]);
        close(mShutdownPipefd[1]);
        AndroidRuntime::getJNIEnv()->DeleteGlobalRef(mCallbackObj);
    }
};
static std::unique_ptr<NativeVendorControlRequestMonitorThread> sVendorControlRequestMonitorThread;

static void set_accessory_string(JNIEnv *env, int fd, int cmd, jobjectArray strArray, int index)
{
    char buffer[256];

    buffer[0] = 0;
    ioctl(fd, cmd, buffer);
    if (buffer[0]) {
        jstring obj = env->NewStringUTF(buffer);
        env->SetObjectArrayElement(strArray, index, obj);
        env->DeleteLocalRef(obj);
    }
}

static void set_accessory_string_from_ffs(JNIEnv *env, jobjectArray strArray, int index) {
    if (!sVendorControlRequestMonitorThread) {
        ALOGE("Vendor control request monitor thread is not running");
        return;
    }

    std::string str = sVendorControlRequestMonitorThread->getAccessoryString(index);
    if (!str.empty()) {
        jstring obj = env->NewStringUTF(str.data());
        env->SetObjectArrayElement(strArray, index, obj);
        env->DeleteLocalRef(obj);
    }
}

static jobjectArray android_server_UsbDeviceManager_getAccessoryStrings(JNIEnv *env,
                                                                        jobject /* thiz */)
{
    int fd = open(DRIVER_NAME, O_RDWR);
    if (fd < 0) {
        ALOGE("could not open %s", DRIVER_NAME);
        return NULL;
    }
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray strArray = env->NewObjectArray(6, stringClass, NULL);
    if (!strArray) goto out;
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_MANUFACTURER, strArray, 0);
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_MODEL, strArray, 1);
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_DESCRIPTION, strArray, 2);
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_VERSION, strArray, 3);
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_URI, strArray, 4);
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_SERIAL, strArray, 5);

out:
    close(fd);
    return strArray;
}

static jobjectArray android_server_UsbDeviceManager_getAccessoryStringsFromFfs(JNIEnv *env,
                                                                        jobject /* thiz */)
{
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray strArray = env->NewObjectArray(6, stringClass, NULL);
    if (!strArray) return nullptr;
    set_accessory_string_from_ffs(env, strArray, 0);
    set_accessory_string_from_ffs(env, strArray, 1);
    set_accessory_string_from_ffs(env, strArray, 2);
    set_accessory_string_from_ffs(env, strArray, 3);
    set_accessory_string_from_ffs(env, strArray, 4);
    set_accessory_string_from_ffs(env, strArray, 5);
    return strArray;
}

static jobject android_server_UsbDeviceManager_openAccessory(JNIEnv *env, jobject /* thiz */)
{
    int fd = open(DRIVER_NAME, O_RDWR);
    if (fd < 0) {
        ALOGE("could not open %s", DRIVER_NAME);
        return NULL;
    }
    jobject fileDescriptor = jniCreateFileDescriptor(env, fd);
    if (fileDescriptor == NULL) {
        close(fd);
        return NULL;
    }
    return env->NewObject(gParcelFileDescriptorOffsets.mClass,
        gParcelFileDescriptorOffsets.mConstructor, fileDescriptor);
}

static jboolean android_server_UsbDeviceManager_isStartRequested(JNIEnv* /* env */,
                                                                 jobject /* thiz */)
{
    int fd = open(DRIVER_NAME, O_RDWR);
    if (fd < 0) {
        ALOGE("could not open %s", DRIVER_NAME);
        return false;
    }
    int result = ioctl(fd, ACCESSORY_IS_START_REQUESTED);
    close(fd);
    return (result == 1);
}

static jobject android_server_UsbDeviceManager_openControl(JNIEnv *env, jobject /* thiz */, jstring jFunction) {
    ScopedUtfChars function(env, jFunction);
    bool ptp = false;
    int fd = -1;
    if (!strcmp(function.c_str(), "ptp")) {
        ptp = true;
    }
    if (!strcmp(function.c_str(), "mtp") || ptp) {
        fd = TEMP_FAILURE_RETRY(open(ptp ? FFS_PTP_EP0 : FFS_MTP_EP0, O_RDWR));
        if (fd < 0) {
            ALOGE("could not open control for %s %s", function.c_str(), strerror(errno));
            return NULL;
        }
        if (!writeDescriptors(fd, ptp)) {
            close(fd);
            return NULL;
        }
    }

    jobject jifd = jniCreateFileDescriptor(env, fd);
    if (jifd == NULL) {
        // OutOfMemoryError will be pending.
        close(fd);
    }
    return jifd;
}

static jboolean android_server_UsbDeviceManager_startGadgetMonitor(JNIEnv *env, jobject thiz,
                                                                   jstring jUdcName) {
    std::string filePath;
    ScopedUtfChars udcName(env, jUdcName);

    filePath = "/sys/class/udc/" + std::string(udcName.c_str()) + "/state";
    android::base::unique_fd fd(open(filePath.c_str(), O_RDONLY));

    if (fd.get() == -1) {
        ALOGE("Cannot open %s", filePath.c_str());
        return JNI_FALSE;
    }

    ALOGI("Start monitoring %s", filePath.c_str());
    sGadgetMonitorThread.reset(new NativeGadgetMonitorThread(thiz, std::move(fd)));

    return JNI_TRUE;
}

static void android_server_UsbDeviceManager_stopGadgetMonitor(JNIEnv *env, jobject /* thiz */) {
    sGadgetMonitorThread.reset();
    return;
}

static jboolean android_server_UsbDeviceManager_startVendorControlRequestMonitor(JNIEnv * /* env */,
                                                                                 jobject thiz) {
    android::base::unique_fd ufd(open(FFS_VENDOR_CTRL_REQUEST_EP0, O_RDWR));

    int fd = ufd.get();

    if (fd < 0) {
        ALOGE("Cannot open %s: %s", FFS_VENDOR_CTRL_REQUEST_EP0, strerror(errno));
        return JNI_FALSE;
    }

    ssize_t ret = TEMP_FAILURE_RETRY(write(fd, &ctrl_desc, sizeof(ctrl_desc)));
    if (ret != sizeof(ctrl_desc)) {
        ALOGE("Writing ctrl desc failed: %zd - %s", ret, strerror(errno));
        ufd.reset(-1);
        return JNI_FALSE;
    }

    ret = TEMP_FAILURE_RETRY(write(fd, &ctrl_strings, sizeof(ctrl_strings)));
    if (ret != sizeof(ctrl_strings)) {
        ALOGE("Writing ctrl strings failed: %zd - %s", ret, strerror(errno));
        ufd.reset(-1);
        return JNI_FALSE;
    }

    ALOGI("Start monitoring %s...", FFS_VENDOR_CTRL_REQUEST_EP0);
    sVendorControlRequestMonitorThread.reset(
            new NativeVendorControlRequestMonitorThread(thiz, std::move(ufd)));

    return JNI_TRUE;
}

static jboolean android_server_UsbDeviceManager_openAccessoryControl(JNIEnv * /* env */,
                                                                     jobject /* thiz */) {
    ALOGI("Writing descriptors to USB Accessory...");

    int fd = TEMP_FAILURE_RETRY(open(FFS_ACCESSORY_EP0, O_RDWR));
    if (fd < 0) {
        ALOGE("Opening accessory ep0 failed: %d - %s", fd, strerror(errno));
        return JNI_FALSE;
    }
    ssize_t ret = TEMP_FAILURE_RETRY(write(fd, &acc_desc, sizeof(acc_desc)));
    if (ret < 0) {
        ALOGE("Writing accessory desc failed: %d - %s", fd, strerror(errno));
        close(fd);
        return JNI_FALSE;
    }
    ret = TEMP_FAILURE_RETRY(write(fd, &acc_strings, sizeof(acc_strings)));
    if (ret < 0) {
        ALOGE("Writing accessory strings failed: %d - %s", fd, strerror(errno));
        close(fd);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static jstring android_server_UsbDeviceManager_waitAndGetProperty(JNIEnv *env, jobject thiz,
                                                                  jstring jPropName) {
    ScopedUtfChars propName(env, jPropName);
    std::string propValue;

    while (!android::base::WaitForPropertyCreation(propName.c_str()));
    propValue = android::base::GetProperty(propName.c_str(), "" /* default */);

    return env->NewStringUTF(propValue.c_str());
}

static const JNINativeMethod method_table[] = {
        {"nativeGetAccessoryStrings", "()[Ljava/lang/String;",
         (void *)android_server_UsbDeviceManager_getAccessoryStrings},
        {"nativeGetAccessoryStringsFromFfs", "()[Ljava/lang/String;",
         (void *)android_server_UsbDeviceManager_getAccessoryStringsFromFfs},
        {"nativeOpenAccessory", "()Landroid/os/ParcelFileDescriptor;",
         (void *)android_server_UsbDeviceManager_openAccessory},
        {"nativeIsStartRequested", "()Z", (void *)android_server_UsbDeviceManager_isStartRequested},
        {"nativeOpenControl", "(Ljava/lang/String;)Ljava/io/FileDescriptor;",
         (void *)android_server_UsbDeviceManager_openControl},
        {"nativeStartGadgetMonitor", "(Ljava/lang/String;)Z",
         (void *)android_server_UsbDeviceManager_startGadgetMonitor},
        {"nativeStopGadgetMonitor", "()V",
         (void *)android_server_UsbDeviceManager_stopGadgetMonitor},
        {"nativeStartVendorControlRequestMonitor", "()Z",
         (void *)android_server_UsbDeviceManager_startVendorControlRequestMonitor},
        {"nativeOpenAccessoryControl", "()Z",
         (void *)android_server_UsbDeviceManager_openAccessoryControl},
        {"nativeWaitAndGetProperty", "(Ljava/lang/String;)Ljava/lang/String;",
         (void *)android_server_UsbDeviceManager_waitAndGetProperty},
};

int register_android_server_UsbDeviceManager(JavaVM *vm, JNIEnv *env) {
    gvm = vm;

    jclass clazz = env->FindClass("com/android/server/usb/UsbDeviceManager");
    if (clazz == NULL) {
        ALOGE("Can't find com/android/server/usb/UsbDeviceManager");
        return -1;
    }

    gUpdateGadgetStateMethod =
            GetMethodIDOrDie(env, clazz, "updateGadgetState", "(Ljava/lang/String;)V");

    gUpdateAccessoryStateMethod =
            GetMethodIDOrDie(env, clazz, "updateAccessoryState", "(Ljava/lang/String;)V");

    clazz = env->FindClass("android/os/ParcelFileDescriptor");
    LOG_FATAL_IF(clazz == NULL, "Unable to find class android.os.ParcelFileDescriptor");
    gParcelFileDescriptorOffsets.mClass = (jclass) env->NewGlobalRef(clazz);
    gParcelFileDescriptorOffsets.mConstructor = env->GetMethodID(clazz, "<init>", "(Ljava/io/FileDescriptor;)V");
    LOG_FATAL_IF(gParcelFileDescriptorOffsets.mConstructor == NULL,
                 "Unable to find constructor for android.os.ParcelFileDescriptor");

    return jniRegisterNativeMethods(env, "com/android/server/usb/UsbDeviceManager",
            method_table, NELEM(method_table));
}
};
