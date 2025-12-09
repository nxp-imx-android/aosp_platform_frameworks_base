/**
 * Copyright 2025, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define ATRACE_TAG ATRACE_TAG_ACTIVITY_MANAGER
#define LOG_TAG "MemoryLimiter"

// LINT.IfChange(traceTrack)
#define TRACE_TRACK "MemoryLimiter"
// LINT.ThenChange(/services/core/java/com/android/server/am/MemoryLimiter.java:traceTrack)

#include <android-base/file.h>
#include <core_jni_helpers.h>
#include <cutils/misc.h>
#include <errno.h>
#include <inttypes.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>
#include <processgroup/processgroup.h>
#include <pthread.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysinfo.h>
#include <unistd.h>
#include <utils/Log.h>
#include <utils/Trace.h>

#include <map>
#include <string>

namespace android {

namespace {

/**
 * The different limits that this process monitors.
 */
enum class MonitoredLimit {
    // LINT.IfChange(limitTypes)
    kUnknown,
    kMemory,
    // LINT.ThenChange(/services/core/java/com/android/server/am/MemoryLimiter.java:limitTypes)
};

// The JVM information that supports callback notifications.
class Callback {
    JavaVM* mVm;
    jclass mClass;
    jmethodID mFunc;

public:
    bool init(JNIEnv* env, char const* class_name) {
        if (env->GetJavaVM(&mVm) != 0) {
            ALOGE("GetJavaVM failed");
            return false;
        }
        jclass service = env->FindClass(class_name);
        if (service == nullptr) {
            ALOGE("Failed to find class %s", class_name);
            return false;
        }
        mClass = static_cast<jclass>(env->NewGlobalRef(service));
        if (mClass == nullptr) {
            ALOGE("Failed to create class reference");
            return false;
        }
        mFunc = env->GetStaticMethodID(mClass, "onLimitExceeded", "(III)V");
        if (mFunc == nullptr) {
            ALOGE("Failed to find static method onLimitExceeded");
            return false;
        }
        return true;
    }

    void operator()(int pid, int uid, MonitoredLimit type) {
        JNIEnv* env;
        if (mVm->AttachCurrentThread(&env, 0) == JNI_OK) {
            env->CallStaticVoidMethod(mClass, mFunc, pid, uid, type);
            mVm->DetachCurrentThread();
        } else {
            ALOGE("failed to attach thread to JavaVM");
        }
    }
};
Callback sOnLimitExceeded;

/**
 * A monitored process.
 */
class Process {
public:
    // The pid and uid being monitored.  These are const, and therefore may be public.
    const pid_t mPid;
    const uid_t mUid;

    Process(pid_t pid, uid_t uid, int inotify_fd) : mPid(pid), mUid(uid), mInotifyFd(inotify_fd) {}

    // There is no copy constructor.
    Process(Process const&) = delete;

    // The move constructor takes possession of the file descriptors for this process.  The
    // inotify file descriptor is not owned by this object so it is copied into the destination
    // but not modified in the source.
    Process(Process&& r) noexcept
          : mPid(r.mPid),
            mUid(r.mUid),
            mInotifyFd(r.mInotifyFd),
            mPidFd(r.mPidFd),
            mMemoryWd(r.mMemoryWd) {
        r.mPidFd = UNSET;
        r.mMemoryWd = UNSET;
    }

    // File descriptors are closed in the destructor if they are open.  Note that mInotifyFd is
    // not owned by this object and is therefore not touched.
    ~Process() {
        if (mPidFd >= 0) {
            ::close(mPidFd);
        }
        if (mMemoryWd >= 0) {
            inotify_rm_watch(mInotifyFd, mMemoryWd);
            sDescriptorMap.erase(mMemoryWd);
        }
    }

    // Connect the pidfd file descriptor.  Return false on failure.  This does not create the
    // watch descriptors because there seems to be a delay between process creation and cgroup
    // file creation.
    bool init() {
        mPidFd = syscall(SYS_pidfd_open, mPid, 0);
        if (mPidFd < 0) {
            ALOGE("pidfd_open(%d) failed: %s", mPid, strerror(errno));
            return false;
        }
        return true;
    }

    // Watch the events files.  This cannot be called immediately after a process starts because
    // the threads that move the process into its cgroup may take tens of microseconds to
    // complete.
    bool watch() {
        constexpr char const* fmt = "/sys/fs/cgroup/%s/uid_%d/pid_%d/%s";
        char const* category = (mUid >= FIRST_APPLICATION_UID) ? "apps" : "system";

        char path[PATH_MAX];
        struct stat sbuff;
        if (mMemoryWd < 0) {
            snprintf(path, sizeof(path), fmt, category, mUid, mPid, "memory.events");
            if (stat(path, &sbuff) != 0) {
                ALOGE("path %s not found: %s", path, strerror(errno));
                return false;
            }
            mMemoryWd = inotify_add_watch(mInotifyFd, path, IN_MODIFY);
            if (mMemoryWd < 0) {
                ALOGE("add_watch(%s) failed: %s", path, strerror(errno));
                return false;
            }
            sDescriptorMap[mMemoryWd] = mPid;
            // Note: it is not necessary to clean up the descriptor map if a subsequent call
            // fails.  The map is properly cleaned up in the Process destructor.
        }

        // This method is called whenever the limits are changed.  Clear the violation flag,
        // inasmuch as it applies to the old limits.
        mViolation = false;
        return true;
    }

    int getPidFd() const {
        return mPidFd;
    }

    // Return the limit type that corresponds to the watch descriptor.
    MonitoredLimit getLimitType(int wd) const {
        if (wd == mMemoryWd) {
            return MonitoredLimit::kMemory;
        } else {
            return MonitoredLimit::kUnknown;
        }
    }

    // Get the violation flag.
    bool getViolation() const {
        return mViolation;
    }

    // Set the violation flag.
    void setViolation(bool flag) {
        mViolation = flag;
    }

    // Return the pid associated with the descriptor.  A zero pid means "not found".
    static pid_t lookup(int descriptor) {
        if (sDescriptorMap.contains(descriptor)) {
            return sDescriptorMap[descriptor];
        } else {
            return 0;
        }
    }

private:
    // A constant that identifies a file descriptor or watch descriptor that is unset.  Posix
    // never creates a negative descriptor.
    static const int UNSET = -1;

    // The inotify fd that holds the watch descriptors.  This is not owned by the Process.  It
    // is only ever used to add and remove watched paths.
    const int mInotifyFd;

    // The pidfd used to detect process exits.
    int mPidFd = UNSET;

    // The memory event watch descriptor.
    int mMemoryWd = UNSET;

    // True if the process has reported a limit violation.  This flag can toggle throughout the
    // lifetime of the object.
    bool mViolation = false;

    // Map watch descriptors to a pid.  The descriptor (which libc types as 'int') is used to
    // find the associated pid.
    static std::unordered_map<int, pid_t> sDescriptorMap;
};

std::unordered_map<int, pid_t> Process::sDescriptorMap;

class Monitor {
    static const int UNSET = -1;

    // True if init() has ever been called on this object.
    bool mInitialized = false;

    // The inotify object that watches cgroup event files.
    int mInotifyFd = UNSET;

    // The inter-thread communication pipe.
    int mEventFd = UNSET;

    // The epoll object.
    int mEpollFd = UNSET;

    // The polling thread.
    pthread_t mPoller;

    // A mutex to guard access to targets.
    mutable Mutex mLock;

    // The list of monitored processes, indexed by pid.
    std::unordered_map<pid_t, Process> mTargets;

public:
    Monitor() : mLock(0) {}

    /**
     * Initialize the file descriptors in the monitor.  Calling this more than once is harmless.
     * That would be unusual in production code but is common in test code.
     */
    bool init() {
        AutoMutex _l(mLock);

        if (mInitialized) {
            return true;
        }

        mInotifyFd = inotify_init();
        if (mInotifyFd < 0) {
            ALOGE("inotify_init() failed: %s", strerror(errno));
            return false;
        }

        mEventFd = eventfd(0, EFD_CLOEXEC | EFD_SEMAPHORE);
        if (mEventFd < 0) {
            ALOGE("eventfd() failed: %s", strerror(errno));
            return false;
        }

        // See the man page for epoll_create(): the size argument is ignored except that it must
        // be non-zero.
        mEpollFd = epoll_create(1);
        if (mEpollFd < 0) {
            ALOGE("epoll_create() failed: %s", strerror(errno));
            return false;
        }

        // Add the two, permanent descriptors to epoll.
        struct epoll_event e_event = {.events = EPOLLIN, .data.u64 = 0};
        if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mEventFd, &e_event) < 0) {
            ALOGE("epoll add(event) failed: %s", strerror(errno));
            return false;
        }

        struct epoll_event i_event = {.events = EPOLLIN, .data.u64 = 1};
        if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mInotifyFd, &i_event) != 0) {
            ALOGE("epoll add(inotify) failed: %s", strerror(errno));
            return false;
        }

        if (pthread_create(&mPoller, 0, &Monitor::startMonitoring, this) != 0) {
            ALOGE("pthread_create() failed: %s", strerror(errno));
            return false;
        }
        pthread_setname_np(mPoller, "memory.limiter");

        mInitialized = true;
        return true;
    }

    // Add a process to the list of monitored processes.  The process and its file descriptors
    // is owned by the monitor.
    bool start(int pid, int uid) {
        if (!mInitialized) {
            ALOGE("monitor not started in start()");
            return false;
        }
        Process p(pid, uid, mInotifyFd);
        if (!p.init()) return false;
        int efd = p.getPidFd();

        AutoMutex _l(mLock);
        mTargets.emplace(pid, std::move(p));
        struct epoll_event p_event = {.events = EPOLLIN, .data.u64 = static_cast<uint64_t>(pid)};
        if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, efd, &p_event) != 0) {
            ALOGE("epoll add(pid) failed pid=%d uid=%d: %s", pid, uid, strerror(errno));
        }
        return true;
    }

    // Ensure the process is being watched.
    bool watch(int pid, int uid) {
        if (!mInitialized) {
            ALOGE("monitor not started in watch()");
            return false;
        }

        AutoMutex _l(mLock);
        auto i = mTargets.find(pid);
        if (i != mTargets.end()) {
            return i->second.watch();
        }
        return false;
    }

    // Forget about a monitored process.
    void forget(pid_t pid) {
        AutoMutex _l(mLock);
        auto i = mTargets.find(pid);
        if (i != mTargets.end()) {
            int pfd = i->second.getPidFd();
            if (epoll_ctl(mEpollFd, EPOLL_CTL_DEL, pfd, nullptr) < 0) {
                ALOGE("epoll del(pid) failed: %s", strerror(errno));
            }
            mTargets.erase(i);
        }
    }

private:
    static void* startMonitoring(void* arg) {
        return reinterpret_cast<Monitor*>(arg)->run();
    }

    void* run() {
        ALOGI("begin monitoring");
        static const int event_size = 32;
        struct epoll_event events[event_size];
        memset(events, 0, sizeof(events));

        // 2, just for testing.
        static const int timeout = 2 * 1000;

        int ready;
        while ((ready = epoll_wait(mEpollFd, events, event_size, timeout)) >= 0) {
            // ALOGI("poll ready %d", ready);
            for (int i = 0; i < ready; i++) {
                uint64_t datum = events[i].data.u64;
                switch (datum) {
                    case 0:
                        handle_event();
                        break;
                    case 1:
                        handle_inotify();
                        break;
                    default:
                        handle_pidfd(static_cast<pid_t>(datum));
                        break;
                }
            }
        }
        ALOGI("end monitoring");
        return nullptr;
    }

    // Handle an event that is sent to the loop from the upper layers.
    void handle_event() {
        union {
            char data[8];
            uint64_t u64;
        } data;
        if (read(mEventFd, data.data, 8) == 8) {
            ALOGI("read(event) returns 0x%" PRIx64, data.u64);
        } else {
            ALOGE("read(event) failed: %s", strerror(errno));
        }
    }

    // Handle an inotify event.  This should be a memory limit event.
    void handle_inotify() {
        union {
            struct inotify_event event;
            char raw[sizeof(inotify_event) + NAME_MAX + 1];
        } data;
        if (read(mInotifyFd, &data, sizeof(data)) >= sizeof(struct inotify_event)) {
            // inotify events often arrive when a process is deleted and its cgroup files go
            // away.  In that case, lookup() will return null, and the event should be ignored.
            int wd = data.event.wd;
            AutoMutex _l(mLock);
            auto i = mTargets.find(Process::lookup(wd));
            if (i != mTargets.end()) {
                Process* p = &i->second;
                if (!p->getViolation()) {
                    p->setViolation(true);
                    sOnLimitExceeded(p->mPid, p->mUid, p->getLimitType(wd));
                }
            }
        } else {
            ALOGE("read(inotify) failed: %s", strerror(errno));
        }
    }

    // A pidfd event means a process has exited.
    void handle_pidfd(pid_t pid) {
        // Find the process and delete it from the targets
        forget(pid);
    }
};

// The singleton monitor.  It is configured in the registration function of this module.
Monitor sMonitor;

// Initialize the MemoryLimiter thread and return the total memory available to processes.  This
// total excludes kernel carve-outs.
jlong init(JNIEnv* env, jclass) {
    if (!sMonitor.init()) {
        jniThrowRuntimeException(env, "monitor initialization failed");
        return 0;
    }
    struct sysinfo si;
    if (sysinfo(&si) == 0) {
        return si.totalram;
    } else {
        ALOGE("sysinfo fails: %s", strerror(errno));
        return 0;
    }
}

// A process has started.
jboolean startProcess(JNIEnv*, jclass, jint pid, jint uid) {
    ATRACE_CALL();
    return sMonitor.start(pid, uid);
}

// A small wrapper to make lines shorter.  The compiler will inline this.
bool writeString(std::string text, std::string& path) {
    return android::base::WriteStringToFile(text, path);
}

// A process is being configured with a memory.high limit.  A negative limit means "max".
jboolean configureLimit(JNIEnv*, jclass, jint pid, jint uid, jlong limit) {
    if (!sMonitor.watch(pid, uid)) return false;
    std::string name = "MemHigh";
    std::string path;
    if (!CgroupGetAttributePathForProcess(name, uid, pid, path)) {
        ALOGE("failed to get memory.high path for pid=%d uid=%d", pid, uid);
        return false;
    }
    if (!writeString((limit < 0) ? "max" : std::to_string(limit), path)) {
        ALOGE("failed to write memory.high (pid=%d uid=%d): %s", pid, uid, strerror(errno));
        return false;
    }
    return true;
}

const JNINativeMethod sMethods[] = {
        {"initLimiter", "()J", (void*)init},
        {"onProcessStarted", "(II)Z", (void*)startProcess},
        {"configureLimit", "(IIJ)Z", (void*)configureLimit},
};

} // namespace

// Register the JNI methods and perform some common initialization.  This returns int but no one
// looks at the return value.  The method is expected to crash the process if anything goes
// wrong.
int register_android_server_am_MemoryLimiter(JNIEnv* env) {
    static const char* class_name = "com/android/server/am/MemoryLimiter";
    if (!sOnLimitExceeded.init(env, class_name)) {
        LOG_ALWAYS_FATAL("Failed to initialize callback");
    }
    return jniRegisterNativeMethods(env, class_name, sMethods, NELEM(sMethods));
}

} // namespace android
