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
#include <android-base/unique_fd.h>
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

// A convenience type declaration.
using wdmap_t = std::unordered_map<int, pid_t>;

/**
 * A wrapper that throws a message constructed from a printf string.
 */
void throwRuntime(JNIEnv* env, char const* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char msg[1024];
    vsnprintf(msg, sizeof(msg), fmt, args);
    va_end(args);
    jniThrowRuntimeException(env, msg);
}

/**
 * A monitored process.
 */
class Process {
public:
    // The pid and uid being monitored.  These are const, and therefore may be public.
    const pid_t mPid;
    const uid_t mUid;

    Process(pid_t pid, uid_t uid) : mPid(pid), mUid(uid) {}

    // There is no copy constructor.
    Process(Process const&) = delete;

    // The move constructor takes ownership of the pid fd for this process.  The watch
    // descriptor is not owned by the Process, but it is copied over and then reset on the
    // right-hand side.
    Process(Process&& r) noexcept
          : mPid(r.mPid), mUid(r.mUid), mPidFd(std::move(r.mPidFd)), mMemoryWd(r.mMemoryWd) {
        r.mMemoryWd = UNSET;
    }

    // File descriptors are closed in the destructor if they are open.
    ~Process() {}

    // Connect the pidfd file descriptor.  Return false on failure.  This does not create the
    // watch descriptors because there seems to be a delay between process creation and cgroup
    // file creation.
    bool init() {
        int pfd = syscall(SYS_pidfd_open, mPid, 0);
        if (pfd < 0) {
            ALOGE("pidfd_open(%d) failed: %s", mPid, strerror(errno));
            return false;
        }
        mPidFd.reset(pfd);
        return true;
    }

    // Watch the events files.  This cannot be called immediately after a process starts because
    // the threads that move the process into its cgroup may take tens of microseconds to
    // complete.
    bool watch(int inotify_fd, wdmap_t& wdmap) {
        // If the process has been initialized, do nothing.
        if (mInitialized) return true;

        constexpr char const* fmt = "/sys/fs/cgroup/%s/uid_%d/pid_%d/%s";
        char const* category = (mUid >= FIRST_APPLICATION_UID) ? "apps" : "system";

        char path[PATH_MAX];
        struct stat sbuff;
        snprintf(path, sizeof(path), fmt, category, mUid, mPid, "memory.events");
        if (stat(path, &sbuff) != 0) {
            ALOGE("path %s not found: %s", path, strerror(errno));
            return false;
        }
        mMemoryWd = inotify_add_watch(inotify_fd, path, IN_MODIFY);
        if (mMemoryWd < 0) {
            ALOGE("add_watch(%s) failed: %s", path, strerror(errno));
            return false;
        }
        wdmap[mMemoryWd] = mPid;

        mInitialized = true;
        return true;
    }

    // Stop watching.  This does not change the initialized flag but it does remove the watch
    // descriptors from the inotify.  To resume watching, clear the initialized flag.
    void unwatch(int inotify_fd, wdmap_t& wdmap) {
        if (mMemoryWd >= 0) {
            inotify_rm_watch(inotify_fd, mMemoryWd);
            wdmap.erase(mMemoryWd);
        }
        mMemoryWd = UNSET;
    }

    base::borrowed_fd getPidFd() const {
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

private:
    // A constant that identifies a file descriptor or watch descriptor that is unset.  Posix
    // never creates a negative descriptor.
    static const int UNSET = -1;

    // True if this process has been configured.  This is used to short-circuit subsequent
    // attempts to configure the process.
    bool mInitialized = false;

    // The pidfd used to detect process exits.
    base::unique_fd mPidFd;

    // The memory event watch descriptor.  This is remembered by Process but is not managed
    // autonmously by the Process.  It is created inside the watch() method and is destroyed
    // inside the unwatch() method.
    int mMemoryWd = UNSET;
};

// The JVM information that supports callback notifications.  This class is valid in a single
// thread that remains attached for its lifetime.
class Callback {
    JavaVM* mVm = nullptr;
    jmethodID mFunc = nullptr;
    jobject mLimiter = nullptr;

public:
    // The constructor throws a Java exception on error.  If an exception is thrown, some
    // attributes will be null.
    Callback(JNIEnv* env, jobject jlimiter) {
        if (env->GetJavaVM(&mVm) != 0) {
            throwRuntime(env, "Callback: GetJavaVM() failed");
            return;
        }
        char const* class_name = "com/android/server/am/MemoryLimiter$Controller";
        jclass service = env->FindClass(class_name);
        if (service == nullptr) {
            throwRuntime(env, "failed to find Controller class");
            return;
        }
        mFunc = env->GetMethodID(service, "onLimitExceeded", "(III)V");
        if (mFunc == nullptr) {
            throwRuntime(env, "failed to find limiter callback");
            return;
        }
        mLimiter = env->NewGlobalRef(jlimiter);
        if (mLimiter == nullptr) {
            throwRuntime(env, "failed to create global ref");
            return;
        }
    }

    // The copy constructor is disallowed.
    Callback(Callback const&) = delete;

    ~Callback() {
        // These pointers will be null only if the constructor encountered a failure and exited
        // early.
        if (mVm != nullptr && mLimiter != nullptr) {
            JNIEnv* env;
            if (mVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
                env->DeleteGlobalRef(mLimiter);
            } else {
                ALOGE("~Callback() failed in GetEnv()");
            }
        }
    }

    void operator()(int pid, int uid, MonitoredLimit type) {
        JNIEnv* env;
        if (mVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->CallVoidMethod(mLimiter, mFunc, pid, uid, type);
        } else {
            ALOGE("GetEnv() failed");
        }
    }
};

class Monitor {
    // A mutex to guard access to targets.
    mutable Mutex mLock;

    // The callback
    Callback mCallback;

    // The Java VM, used by the monitor thread.
    JavaVM* mVm;

    // The inotify object that watches cgroup event files.
    base::unique_fd mInotifyFd;

    // The inter-thread communication pipe.
    base::unique_fd mEventFd;

    // The epoll object.
    base::unique_fd mEpollFd;

    // The polling thread.
    pthread_t mPoller;

    // The list of monitored processes, indexed by pid.
    std::unordered_map<pid_t, Process> mTargets;

    // The map from watch descriptors to pids.
    wdmap_t mWdMap;

    // Event FD commands.
    enum class Cmd {
        Stop = 1,
    };

public:
    Monitor(JNIEnv* env, jobject jlimiter) : mLock(0), mCallback(env, jlimiter) {
        if (env->ExceptionCheck()) {
            // The callback has probably failed in its constructor.  Exit immediately.
            return;
        }

        if (env->GetJavaVM(&mVm) != 0) {
            throwRuntime(env, "Monitor: GetJavaVM() failed");
            return;
        }

        int ifd = inotify_init();
        if (ifd < 0) {
            throwRuntime(env, "inotify_init() failed: %s", strerror(errno));
            return;
        }
        mInotifyFd.reset(ifd);

        int efd = eventfd(0, EFD_CLOEXEC | EFD_SEMAPHORE);
        if (efd < 0) {
            throwRuntime(env, "eventfd() failed: %s", strerror(errno));
            return;
        }
        mEventFd.reset(efd);

        // See the man page for epoll_create(): the size argument is ignored except that it must
        // be non-zero.
        int pfd = epoll_create(1);
        if (pfd < 0) {
            throwRuntime(env, "epoll_create() failed: %s", strerror(errno));
            return;
        }
        mEpollFd.reset(pfd);

        // Add the two, permanent descriptors to epoll.
        struct epoll_event e_event = {.events = EPOLLIN, .data.u64 = 0};
        if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mEventFd, &e_event) < 0) {
            throwRuntime(env, "epoll add(event) failed: %s", strerror(errno));
            return;
        }

        struct epoll_event i_event = {.events = EPOLLIN, .data.u64 = 1};
        if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mInotifyFd, &i_event) != 0) {
            throwRuntime(env, "epoll add(inotify) failed: %s", strerror(errno));
            return;
        }

        if (pthread_create(&mPoller, 0, &Monitor::startMonitoring, this) != 0) {
            throwRuntime(env, "pthread_create() failed: %s", strerror(errno));
            return;
        }
        pthread_setname_np(mPoller, "memory.limiter");
    }

    // No copy constructors or move constructors.
    Monitor(Monitor const&) = delete;

    ~Monitor() {
        if (sendCommand(Cmd::Stop)) {
            pthread_join(mPoller, nullptr);
        } else {
            ALOGE("failed to send stop command: %s", strerror(errno));
        }
    }

    // Return the pid associated with the descriptor.  A zero pid means "not found".
    pid_t lookup(int descriptor) {
        if (mWdMap.contains(descriptor)) {
            return mWdMap[descriptor];
        } else {
            return 0;
        }
    }

    // Add a process to the list of monitored processes.  The process and its file descriptors
    // is owned by the monitor.
    bool start(int pid, int uid) {
        Process p(pid, uid);
        if (!p.init()) return false;
        base::borrowed_fd pfd = p.getPidFd();

        AutoMutex _l(mLock);
        mTargets.emplace(pid, std::move(p));
        struct epoll_event p_event = {.events = EPOLLIN, .data.u64 = static_cast<uint64_t>(pid)};
        if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, pfd.get(), &p_event) != 0) {
            ALOGE("epoll add(pid) failed pid=%d uid=%d: %s", pid, uid, strerror(errno));
        }
        return true;
    }

    // Ensure the process is being watched.
    bool watch(int pid, int uid) {
        AutoMutex _l(mLock);
        auto i = mTargets.find(pid);
        if (i != mTargets.end()) {
            return i->second.watch(mInotifyFd, mWdMap);
        }
        return false;
    }

    // Forget about a monitored process.
    void forget(pid_t pid) {
        AutoMutex _l(mLock);
        auto i = mTargets.find(pid);
        if (i != mTargets.end()) {
            base::borrowed_fd pfd = i->second.getPidFd();
            if (epoll_ctl(mEpollFd, EPOLL_CTL_DEL, pfd.get(), nullptr) < 0) {
                ALOGE("epoll del(pid) failed: %s", strerror(errno));
            }
            i->second.unwatch(mInotifyFd, mWdMap);
            mTargets.erase(i);
        }
    }

private:
    static void* startMonitoring(void* arg) {
        return reinterpret_cast<Monitor*>(arg)->run();
    }

    // The main monitoring loop.
    void* run() {
        ALOGI("begin monitoring");

        // Attach the thread to the VM.  It is known that the thread is not currently attached.
        JNIEnv* env;
        if (mVm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            ALOGE("failed to attach monitor thread");
            return nullptr;
        }

        static const int event_size = 32;
        struct epoll_event events[event_size];
        memset(events, 0, sizeof(events));

        // For testing, set this to a positive value.  Setting it to -1 means "wait forever".
        static const int timeout = -1;

        int ready;
        while ((ready = epoll_wait(mEpollFd, events, event_size, timeout)) >= 0) {
            if (!handle_poll(events, ready)) {
                break;
            }
        }
        mVm->DetachCurrentThread();
        ALOGI("end monitoring");
        return nullptr;
    }

    // Handle a single poll event.  Return true if the enclosing loop should continue and false
    // if it should exit.
    bool handle_poll(struct epoll_event const* events, int size) {
        for (int i = 0; i < size; i++) {
            uint64_t datum = events[i].data.u64;
            switch (datum) {
                case 0:
                    if (!handle_event()) {
                        // The event has requested that the poller stop.
                        return false;
                    }
                    break;
                case 1:
                    handle_inotify();
                    break;
                default:
                    handle_pidfd(static_cast<pid_t>(datum));
                    break;
            }
        }
        return true;
    }

    // This class defines the data sent and received through the event fd.  Event fd takes 8
    // bytes (uint64_t).  Thus, the size of EventData must be 8.  The u64 field is just for
    // logging.
    union EventData {
        char raw[8];
        uint64_t u64;
        struct {
            Cmd cmd;
        };
    };
    static_assert(sizeof(EventData) == 8, "EventData must be 8 bytes");

    // Send a command the thread via eventfd.
    bool sendCommand(Cmd cmd) {
        EventData data = {.cmd = cmd};
        return ::write(mEventFd, &data, sizeof(data)) == sizeof(data);
    }

    // Handle an event that is sent to the loop from the upper layers.  The function returns
    // false if the thread should terminate.
    bool handle_event() {
        EventData data;
        if (read(mEventFd, &data, sizeof(data)) == 8) {
            if (data.cmd == Cmd::Stop) {
                return false;
            }
            ALOGI("read(event) returns 0x%" PRIx64, data.u64);
        } else {
            ALOGE("read(event) failed: %s", strerror(errno));
        }
        return true;
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
            pid_t pid = 0;
            uid_t uid = 0;
            MonitoredLimit limit = MonitoredLimit::kUnknown;
            {
                AutoMutex _l(mLock);
                pid = lookup(wd);
                auto i = mTargets.find(pid);
                if (i != mTargets.end()) {
                    Process* p = &i->second;
                    uid = p->mUid;
                    limit = p->getLimitType(wd);
                    p->unwatch(mInotifyFd, mWdMap);
                }
            }
            if (pid != 0) {
                mCallback(pid, uid, limit);
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

// Convert a long from the java layer into a Monitor*.
Monitor* getMonitor(jlong service) {
    return reinterpret_cast<Monitor*>(service);
}

// Create a new Monitor object and returns its address.
jlong initLimiter(JNIEnv* env, jclass, jobject jlimiter) {
    std::unique_ptr<Monitor> m(new Monitor(env, jlimiter));
    if (env->ExceptionCheck()) {
        return 0;
    }
    return reinterpret_cast<jlong>(m.release());
}

// Close and release a limiter.
void closeLimiter(JNIEnv* env, jclass, jlong service) {
    Monitor* m = getMonitor(service);
    delete m;
}

// A process has started.
jboolean startProcess(JNIEnv*, jclass, jlong service, jint pid, jint uid) {
    Monitor* m = getMonitor(service);
    ATRACE_CALL();
    return m->start(pid, uid);
}

// A tiny class that emits a trace in its destructor.
class EndTracer {
    char const* track;
    const int tag;

public:
    EndTracer(char const* track, int tag) : track(track), tag(tag) {}
    ~EndTracer() {
        ATRACE_ASYNC_FOR_TRACK_END(track, tag);
    }
};

// A small wrapper to make lines shorter.  The compiler will inline this.
bool writeString(std::string text, std::string& path) {
    return android::base::WriteStringToFile(text, path);
}

// A process is being configured with a memory.high limit.  A negative limit means "max".
jboolean configureLimit(JNIEnv*, jclass, jlong service, jint pid, jint uid, jlong limit) {
    Monitor* m = getMonitor(service);

    // The Java layer started a slice.  Ensure it is terminated, regardless of how this method
    // exits.
    EndTracer _tracer(TRACE_TRACK, pid);

    if (!m->watch(pid, uid)) return false;
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
        {"closeLimiter", "(J)V", (void*)closeLimiter},
        {"onProcessStarted", "(JII)Z", (void*)startProcess},
        {"configureLimit", "(JIIJ)Z", (void*)configureLimit},
        {"initLimiter", "(Lcom/android/server/am/MemoryLimiter$Controller;)J", (void*)initLimiter},
};

} // namespace

// Register the JNI methods and perform some common initialization.  This returns int but no one
// looks at the return value.  The method is expected to crash the process if anything goes
// wrong.
int register_android_server_am_MemoryLimiter(JNIEnv* env) {
    static const char* class_name = "com/android/server/am/MemoryLimiter";
    return jniRegisterNativeMethods(env, class_name, sMethods, NELEM(sMethods));
}

} // namespace android
