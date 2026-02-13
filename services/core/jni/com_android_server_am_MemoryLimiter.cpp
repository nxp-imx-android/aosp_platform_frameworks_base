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

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>
#include <core_jni_helpers.h>
#include <cutils/misc.h>
#include <errno.h>
#include <inttypes.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>
#include <private/android_filesystem_config.h>
#include <processgroup/processgroup.h>
#include <pthread.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utils/Log.h>

#include <map>
#include <mutex>
#include <optional>
#include <queue>
#include <string>

namespace android {

namespace {

// Enable debug messages.  Do not commit a true value.
const bool DEBUG = false;

// The timeout for the process scrub poll.  It is elevated to this point in the file because we
// like to see constants near the top.  Units are milliseconds.  The value is 5 minutes.
const int POLL_PERIOD_MS = 5 * 60 * 1000;

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

// Enable the short form of StringPrintf.
using ::android::base::StringPrintf;

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
 * Statistics for the limiter.  All counts are int64_t to be compatible with Java long.
 */
struct Statistics {
    // The number of times start() was called.
    std::atomic<uint64_t> mStarted;

    // The number of times watch() was called and succeeded.
    std::atomic<uint64_t> mWatched;

    // The number of times watch() was called and failed.
    std::atomic<uint64_t> mWatchFailed;

    // The number of events that were generated.
    std::atomic<uint64_t> mEvents;

    // The current number of processes being watched.
    std::atomic<uint64_t> mProcesses;

    // The high watch mark for processes.  A process record exists only once watch() succeeds.
    std::atomic<uint64_t> mProcessesHwm;

    std::string toString() const {
        const char* fmt = "started=%" PRIu64 " watched=%" PRIu64 " watch-failed=%" PRIu64
                          " events=%" PRIu64 "\n"
                          "processes=%" PRIu64 " process-hwm=%" PRIu64 "\n";
        return StringPrintf(fmt, mStarted.load(), mWatched.load(), mWatchFailed.load(),
                            mEvents.load(), mProcesses.load(), mProcessesHwm.load());
    }
};

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
    Process(Process&& r) noexcept : mPid(r.mPid), mUid(r.mUid), mMemoryWd(r.mMemoryWd) {
        r.mMemoryWd = UNSET;
    }

    // File descriptors are closed in the destructor if they are open.
    ~Process() {}

    // Watch the events files.  This cannot be called immediately after a process starts because
    // the threads that move the process into its cgroup may take tens of microseconds to
    // complete.  Once the call succeeds, further calls quietly do nothing.
    void watch(int inotify_fd, wdmap_t& wdmap, Statistics& stats) {
        // If the process has been initialized, do nothing.
        if (mInitialized) return;

        char path[PATH_MAX];
        watchPath(path, sizeof(path));
        mMemoryWd = inotify_add_watch(inotify_fd, path, IN_MODIFY);
        if (mMemoryWd < 0) {
            // Only report the failure if the error is not path-not-found.  The path will
            // not be found if the process has already exited or if the process has not been
            // moved into its cgroup yet.
            ALOGE_IF(errno != ENOENT, "add_watch(%s) failed: %s", path, strerror(errno));
            stats.mWatchFailed++;
            return;
        }
        wdmap[mMemoryWd] = mPid;
        stats.mWatched++;
        mInitialized = true;
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

    // Return a string that identifies this process.
    std::string toString() const {
        return android::base::StringPrintf("pid=%d uid=%d wd=%d", mPid, mUid, mMemoryWd);
    }

    // Return true if the process is alive.  If the process is privileged (meaning, it's
    // system_server, use the kill() strategy because it is very fast.  Otherwise, test the
    // cgroup path. This is (relatively) expensive but it only occurs in test code.  It is also
    // definitive, since if the cgroup path cannot be seen then watching cannot occur.
    bool isAlive(bool privileged) const {
        if (privileged) {
            return kill(mPid, 0) == 0;
        } else {
            char path[PATH_MAX];
            struct stat sbuff;
            return stat(watchPath(path, sizeof(path)), &sbuff) == 0;
        }
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

    // The memory event watch descriptor.  This is remembered by Process but is not managed
    // autonmously by the Process.  It is created inside the watch() method and is destroyed
    // inside the unwatch() method.
    int mMemoryWd = UNSET;

    // Construct the watch path.  Return a pointer to the path.
    char* watchPath(char* buffer, size_t length) const {
        constexpr char const* fmt = "/sys/fs/cgroup/%s/uid_%d/pid_%d/%s";
        char const* category = (mUid >= FIRST_APPLICATION_UID) ? "apps" : "system";
        snprintf(buffer, length, fmt, category, mUid, mPid, "memory.events");
        return buffer;
    }
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
        char const* class_name = "com/android/server/am/MemoryLimiter$ControllerEnabled";
        jclass service = env->FindClass(class_name);
        if (service == nullptr) {
            // Throws ClassFormatError, ClassCircularityError, NoClassDefFoundError,
            // OutOfMemoryError
            return;
        }
        mFunc = env->GetMethodID(service, "onLimitExceeded", "(IIIJ)V");
        if (mFunc == nullptr) {
            // Throws NoSuchMethodError, ExceptionInInitializerError, or OutOfMemoryError
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

    void operator()(int pid, int uid, MonitoredLimit type, int64_t limit) {
        JNIEnv* env;
        if (mVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->CallVoidMethod(mLimiter, mFunc, pid, uid, type, limit);
        } else {
            ALOGE("GetEnv() failed");
        }
    }
};

class Monitor {
    // A mutex to guard access to targets.
    mutable std::mutex mLock;

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

    // Performance statistics.
    Statistics mStatistics;

    // Event FD commands.
    enum class Cmd {
        Error = 0,
        Stop = 1,
    };

public:
    Monitor(JNIEnv* env, jobject jlimiter, bool privileged, bool monitor, bool testMode)
          : mCallback(env, jlimiter), mSystem(privileged), mMonitor(monitor), mTestMode(testMode) {
        if (env->ExceptionCheck()) {
            // The callback has probably failed in its constructor.  Exit immediately.
            return;
        }

        if (!monitor) {
            // This is not an error.  The monitoring feature has been disabled, so do not create
            // the inotify object, epoll object, or monitoring thread.
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
        if (mMonitor) {
            if (sendCommand(Cmd::Stop)) {
                pthread_join(mPoller, nullptr);
            } else {
                ALOGE("failed to send stop command: %s", strerror(errno));
            }
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

    // Prepare to watch a process.
    void start(int pid) {
        if (!mMonitor) return;
        forget(pid);
        mStatistics.mStarted++;
    }

    // Ensure the process is being watched.
    void watch(int pid, int uid) {
        if (!mMonitor) return;
        std::lock_guard _l(mLock);
        auto i = mTargets.find(pid);
        if (i == mTargets.end()) {
            Process p(pid, uid);
            if (auto [j, inserted] = mTargets.emplace(pid, std::move(p)); !inserted) {
                return;
            } else {
                i = j;
                mStatistics.mProcesses = mTargets.size();
                mStatistics.mProcessesHwm.store(
                        std::max(mStatistics.mProcessesHwm, mStatistics.mProcesses));
            }
        }
        i->second.watch(mInotifyFd, mWdMap, mStatistics);
    }

    // Forget about a monitored process, if it exists.
    void forget(pid_t pid) {
        if (!mMonitor) return;
        std::lock_guard _l(mLock);
        auto i = mTargets.find(pid);
        if (i != mTargets.end()) {
            forgetLocked(i);
        }
    }

    // Return the statistics as a string.
    std::string getStatistics() const {
        std::lock_guard _l(mLock);
        return mStatistics.toString();
    }

private:
    std::unordered_map<pid_t, Process>::iterator forgetLocked(
            std::unordered_map<pid_t, Process>::iterator p) {
        p->second.unwatch(mInotifyFd, mWdMap);
        p = mTargets.erase(p);
        mStatistics.mProcesses = mTargets.size();
        return p;
    }

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

        // The timeout governs how frequently the thread will scrub non-existent processes.  The
        // units are milliseconds.  The scrub occurs every minute in normal operation.  If
        // running in test mode, the timeout occurs every second.
        static const int timeout = (mTestMode) ? 1000 : POLL_PERIOD_MS;

        int ready;
        while ((ready = epoll_wait(mEpollFd, events, event_size, timeout)) >= 0) {
            if (ready == 0) {
                handle_timeout();
            } else if (!handle_poll(events, ready)) {
                break;
            }
        }
        mVm->DetachCurrentThread();
        ALOGI("end monitoring");
        return nullptr;
    }

    // Handle a poll timeout.  This scrubs non-existent processes from the target list.
    void handle_timeout() {
        const std::lock_guard _l(mLock);
        int count = 0;
        for (auto i = mTargets.begin(); i != mTargets.end();) {
            Process& p = i->second;
            if (!p.isAlive(mSystem)) {
                ALOGI_IF(DEBUG, "scrubbing %s", p.toString().c_str());
                i = forgetLocked(i);
                count++;
            } else {
                ++i;
            }
        }
        ALOGI_IF(DEBUG && count > 0, "scrubbed %d processes", count);
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
                    ALOGE("unexpected poll datum: %" PRIu64, datum);
                    break;
            }
        }
        return true;
    }

    // A thread-safe FIFO queue of commands.
    class CmdQueue {
        mutable std::mutex mLock;
        std::queue<Cmd> mCmd;

    public:
        void push(Cmd cmd) {
            std::lock_guard _l(mLock);
            mCmd.push(cmd);
        }
        Cmd pop() {
            std::lock_guard _l(mLock);
            if (mCmd.empty()) return Cmd::Error;
            Cmd cmd = mCmd.front();
            mCmd.pop();
            return cmd;
        }
    };
    CmdQueue mCmd;

    // Push the command on the queue and notify the poller that a command is ready.  The
    // operations must proceed in that order, to ensure that the poller does not try to read
    // the queue before a command has been placed in it.
    bool sendCommand(Cmd cmd) {
        mCmd.push(cmd);
        uint64_t data = 1;
        return ::write(mEventFd, &data, sizeof(data)) == sizeof(data);
    }

    // Handle an event that is sent to the loop from the upper layers.  The function returns
    // false if the thread should terminate.
    bool handle_event() {
        uint64_t data;
        if (read(mEventFd, &data, sizeof(data)) == 8) {
            while (data-- > 0) {
                Cmd cmd = mCmd.pop();
                switch (cmd) {
                    case Cmd::Stop:
                        return false;
                    default:
                        ALOGE("read(event) unknown cmd %d", cmd);
                        break;
                }
            }
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
            uint32_t mask = data.event.mask;
            pid_t pid = 0;
            uid_t uid = 0;
            MonitoredLimit type = MonitoredLimit::kUnknown;
            if ((mask & IN_MODIFY) != 0) {
                std::lock_guard _l(mLock);
                pid = lookup(wd);
                auto i = mTargets.find(pid);
                if (i != mTargets.end()) {
                    Process& p = i->second;
                    uid = p.mUid;
                    type = p.getLimitType(wd);
                    p.unwatch(mInotifyFd, mWdMap);
                }
            }
            if (pid != 0) {
                // Fetch the configure memcg memory.high limit.  A value of 0 means the limit
                // could not be read or the value was read but is suspicious.
                int64_t limit = 0;
                std::string path;
                if (CgroupGetAttributePathForProcess("MemHigh", uid, pid, path)) {
                    std::string value;
                    if (android::base::ReadFileToString(path, &value, false)) {
                        // This will fail if the limit is "max".  If so, the limit value above
                        // will remain zero, which indicates that the limit could not be read or
                        // is suspicious.  A value of "max" at this point suggests that there
                        // may be another control process that is trying to configure limits.
                        sscanf(value.c_str(), "%" SCNd64, &limit);
                    }
                }
                mCallback(pid, uid, type, limit);
                mStatistics.mEvents++;
            }
        } else {
            ALOGE("read(inotify) failed: %s", strerror(errno));
        }
    }

    // True if this is system_server, which is a privileged process.  If this is not
    // system_server then it is most likely a test process.
    const bool mSystem;

    // True if monitoring is enabled.
    const bool mMonitor;

    // True if running in test mode.  This primarily affects the poller, which is busier (less
    // efficient) in test mode.
    const bool mTestMode;
};

// Convert a long from the java layer into a Monitor*.
Monitor* getMonitor(jlong service) {
    return reinterpret_cast<Monitor*>(service);
}

// Create a new Monitor object and returns its address.
jlong initLimiter(JNIEnv* env, jclass, jobject jlimiter, jboolean monitor, jboolean testMode) {
    const bool system = (getuid() == AID_SYSTEM);
    std::unique_ptr<Monitor> m(new Monitor(env, jlimiter, system, monitor, testMode));
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

// A process has started.  This call just ensures that the process list does not contain a
// stale references to the pid.
void startProcess(JNIEnv* env, jclass, jlong service, jint pid, jint /* uid */) {
    Monitor* m = getMonitor(service);
    m->start(pid);
}

// A small wrapper to make lines shorter.  The compiler will inline this.
bool writeString(std::string text, std::string& path) {
    return android::base::WriteStringToFile(text, path);
}

// A process is being configured with a memory.high limit.  A negative limit means "max".
void configureLimit(JNIEnv*, jclass, jlong service, jint pid, jint uid, jlong limit) {
    Monitor* m = getMonitor(service);

    // Start watching for over-limit events, if possible.  The call is idempotent.  Once it
    // succeeds further invocations do nothing.
    m->watch(pid, uid);

    std::string name = "MemHigh";
    std::string path;
    if (!CgroupGetAttributePathForProcess(name, uid, pid, path)) {
        return;
    }
    if (!writeString((limit < 0) ? "max" : std::to_string(limit), path)) {
        // Only report the failure if the error is not path-not-found.  The path will
        // not be found if the process has already exited or if the process has not been
        // moved into its cgroup yet.
        ALOGE_IF(errno != ENOENT, "failed to write memory.high (%s): %s", path.c_str(),
                 strerror(errno));
    }
}

// Return the statistics for the memory limiter.  If the limiter is invalid, null is returned.
jstring getStatistics(JNIEnv* env, jclass, jlong service) {
    Monitor* m = getMonitor(service);
    std::string stats = m->getStatistics();
    return env->NewStringUTF(stats.c_str());
}

const JNINativeMethod sMethods[] = {
        {"initLimiter", "(Lcom/android/server/am/MemoryLimiter$Controller;ZZ)J",
         (void*)initLimiter},
        {"closeLimiter", "(J)V", (void*)closeLimiter},
        {"onProcessStarted", "(JII)V", (void*)startProcess},
        {"configureLimit", "(JIIJ)V", (void*)configureLimit},
        {"getStatistics", "(J)Ljava/lang/String;", (void*)getStatistics},
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
