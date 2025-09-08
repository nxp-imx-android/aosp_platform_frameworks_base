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

#include "com_android_internal_os_JniStringCache.h"

#include <android-base/logging.h>
#include <android_runtime/AndroidRuntime.h>

#include <atomic>
#include <mutex>

namespace android {

namespace {

template <typename TChar>
uint32_t computeHash(const TChar* s, size_t len) {
    uint32_t h = 0;
    // Equivalent to java.lang.String.hashCode()
    for (size_t i = 0; i < len; i++) {
        h = 31 * h + static_cast<uint32_t>(s[i]);
    }
    return h;
}

// Compare jstring and chat16_t string.
bool StringsAreEqual(JNIEnv* env, jstring jstr, const char16_t* chars, size_t len) {
    if (static_cast<size_t>(env->GetStringLength(jstr)) != len) {
        return false;
    }
    const jchar* jchars = env->GetStringCritical(jstr, nullptr);
    if (jchars == nullptr) {
        return false;
    }
    bool result = memcmp(chars, jchars, len * sizeof(char16_t)) == 0;
    env->ReleaseStringCritical(jstr, jchars);
    return result;
}

// Compare jstring and char string.
bool StringsAreEqual(JNIEnv* env, jstring jstr, const char* chars, size_t len) {
    if (static_cast<size_t>(env->GetStringUTFLength(jstr)) != len) {
        return false;
    }
    // Note we can't use GetStringCritical because it returns jchar*.
    // We could compare jchar* to char*, iterating through the characters, but then we can't use
    // whatever fast path is in memcmp. It's probably not worth it.
    const char* jchars = env->GetStringUTFChars(jstr, nullptr);
    if (jchars == nullptr) {
        return false;
    }
    bool result = memcmp(chars, jchars, len) == 0;
    env->ReleaseStringUTFChars(jstr, jchars);
    return result;
}

JniStringCache* gInstance = nullptr;
std::once_flag gInstanceFlag;

} // namespace

JniStringCache& JniStringCache::getInstance() {
    std::call_once(gInstanceFlag, []() { gInstance = new JniStringCache(); });
    return *gInstance;
}

JniStringCache::JniStringCache() : mHits(0), mMisses(0), mEvictions(0), mSkips(0) {}

JniStringCache::~JniStringCache() {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (env == nullptr) {
        return;
    }
    // Relaxed memory order is sufficient here because this is the destructor, and we assume
    // no other threads are concurrently accessing the cache.
    for (size_t i = 0; i < kCacheSize; ++i) {
        CacheEntry entry = mCache[i].load(std::memory_order_relaxed);
        if (entry.str != nullptr) {
            env->DeleteGlobalRef(entry.str);
        }
        CacheEntry utf8Entry = mUtf8Cache[i].load(std::memory_order_relaxed);
        if (utf8Entry.str != nullptr) {
            env->DeleteGlobalRef(utf8Entry.str);
        }
    }
}

template <typename TChar>
jstring JniStringCache::newStringInternal(JNIEnv* env, const TChar* chars, size_t len,
                                          std::atomic<CacheEntry>* cache,
                                          std::function<jstring(const TChar*, size_t)> newJstring) {
    // Don't cache strings that are too long.
    if (len >= kMaxStringLength) {
        mSkips.fetch_add(1, std::memory_order_relaxed);
        return newJstring(chars, len);
    }
    // Project the input string into the hash space.
    const uint32_t hash = computeHash(chars, len);

    // Use the low 8 bits of the hash as the cache index.
    const size_t index = static_cast<uint16_t>(hash) % kCacheSize;
    std::atomic<CacheEntry>& slot = cache[index];

#ifdef __LP64__
    // On 64-bit, we have a 32-bit hash field, so we don't need to shift.
    const uint32_t entry_hash = hash;
#else
    // Store the next lowest 16 bits of the hash in the cache entry.
    // This allows us to quickly detect a hash collision (same cache entry, different string) using
    // 24 bits of entropy.
    const uint16_t entry_hash = static_cast<uint16_t>(hash >> 8);
#endif

    // Try to hit the cache.

    // Relaxed memory order is sufficient for this initial read. We are optimistically checking for
    // a cache hit. If we find a potential hit candidate, we will use stronger memory orders later.
    CacheEntry entry = slot.load(std::memory_order_relaxed);
    if (entry.hash == entry_hash && entry.str != nullptr) {
        // Found an entry at the respective cache slot with a matching hash.
        // Check for full string equality, in case of hash collision.

        // First we need to acquire a local reference to the entry.
        CacheEntry new_entry;
        do {
            if (entry.hash != entry_hash || entry.str == nullptr) {
                // The entry was changed from under us. Go to miss path.
                goto miss;
            }
            new_entry = entry;
            new_entry.refCount++;
        } while (!slot.compare_exchange_weak(entry, new_entry,
                                             // Success memory order is acquire because we require
                                             // visibility from prior writes in order to safely read
                                             // the global reference.
                                             std::memory_order_acquire,
                                             // Failure memory order is relaxed because we will just
                                             // retry the loop.
                                             std::memory_order_relaxed));

        // We acquired a reference.
        jstring localRef = static_cast<jstring>(env->NewLocalRef(new_entry.str));

        // Now we can decrement the refcount.
        do {
            new_entry = entry;
            new_entry.refCount--;
        } while (!slot.compare_exchange_weak(entry, new_entry,
                                             // Success memory order needs acquire semantics so that
                                             // we pick up writes that occurred prior to other
                                             // decrements that came before us. It also needs
                                             // release semantics so that any writes prior to this
                                             // decrement (including those we picked up via the
                                             // acquire) are visible to subsequent decrements and
                                             // eventual eviction, so that if the global reference
                                             // slot is reused those writes don't result in
                                             // use-after-free.
                                             std::memory_order_acq_rel,
                                             // Failure memory order is relaxed because we will just
                                             // retry the loop.
                                             std::memory_order_relaxed));

        if (localRef == nullptr) {
            // NewLocalRef failed and an exception is pending.
            return nullptr;
        }

        // We got the string, now we can check for full equality.
        if (StringsAreEqual(env, localRef, chars, len)) {
            // Cache hit!
            mHits.fetch_add(1, std::memory_order_relaxed);

            // Return the local reference.
            return localRef;
        }

        // Hash collision with a different string.
        // Don't need the local reference anymore.
        env->DeleteLocalRef(localRef);
    }

miss:
    // Cache miss!
    mMisses.fetch_add(1, std::memory_order_relaxed);

    jstring localRef = newJstring(chars, len);
    if (localRef == nullptr) return nullptr;
    jstring newGlobalRef = static_cast<jstring>(env->NewGlobalRef(localRef));
    if (newGlobalRef == nullptr) {
        // We failed to create a new global ref. Just return the local ref
        // and don't update the cache.
        return localRef;
    }

    CacheEntry new_entry{newGlobalRef, entry_hash, 0};

    // Try to swap in our new entry.
    if (entry.refCount == 0 &&
        slot.compare_exchange_strong(entry, new_entry,
                                     // Success memory order needs acquire semantics for visibility
                                     // into prior writes to the entry we are trying to replace, and
                                     // release semantics to ensure visibility of the new entry to
                                     // other threads that might be accessing the same cache entry.
                                     std::memory_order_acq_rel,
                                     // Failure memory order can be relaxed because we fall back to
                                     // just losing the race and returning the local reference that
                                     // we have without updating the cache.
                                     std::memory_order_relaxed)) {
        // We successfully swapped our entry in.
        if (entry.str != nullptr) {
            // Evict the old entry, now that it's definitely not in use.
            env->DeleteGlobalRef(entry.str);
            mEvictions.fetch_add(1, std::memory_order_relaxed);
        }
    } else {
        // The slot is busy or we lost the race.
        // We'll just drop ours and return what we have.
        env->DeleteGlobalRef(newGlobalRef);
    }
    return localRef;
}

jstring JniStringCache::NewString(JNIEnv* env, const char16_t* chars, size_t len) {
    return newStringInternal<char16_t>(env, chars, len, mCache, [&](const char16_t* c, size_t l) {
        // We assume that the string is null terminated because this method is used as a drop-in
        // replacement for env->NewString which does the same.
        return env->NewString(reinterpret_cast<const jchar*>(c), l);
    });
}

jstring JniStringCache::NewStringUTF(JNIEnv* env, const char* bytes, size_t len) {
    return newStringInternal<char>(env, bytes, len, mUtf8Cache, [&](const char* c, size_t l) {
        // We assume that the string is null terminated because this
        // method is used as a drop-in replacement for
        // env->NewStringUTF which does the same.
        return env->NewStringUTF(c);
    });
}

size_t JniStringCache::hits() const {
    return mHits.load(std::memory_order_relaxed);
}

size_t JniStringCache::misses() const {
    return mMisses.load(std::memory_order_relaxed);
}
size_t JniStringCache::evictions() const {
    return mEvictions.load(std::memory_order_relaxed);
}

size_t JniStringCache::skips() const {
    return mSkips.load(std::memory_order_relaxed);
}

void JniStringCache::clear() {
    clear(AndroidRuntime::getJNIEnv());
}

void JniStringCache::clear(JNIEnv* env) {
    if (env == nullptr) {
        DCHECK(false) << "JNIEnv is null, can't clear cache";
        return;
    }
    auto clear_cache = [&](std::atomic<CacheEntry>* cache) {
        for (size_t i = 0; i < kCacheSize; ++i) {
            std::atomic<CacheEntry>& slot = cache[i];
            // Relaxed memory order is sufficient for this initial read which just decides if we
            // have something to clear. We use stronger semantics later if attempting to delete an
            // unreferenced entry.
            CacheEntry entry = slot.load(std::memory_order_relaxed);

            if (entry.str != nullptr && entry.refCount == 0) {
                CacheEntry null_entry{nullptr, 0, 0};
                if (slot.compare_exchange_strong(entry, null_entry,
                                                 // Success memory order is acquire because we
                                                 // require visibility of prior writes from other
                                                 // threads that
                                                 // acquired/released a reference or replaced the
                                                 // entry.
                                                 // Release is not required because we are
                                                 // exchanging with a null entry, which no one will
                                                 // attempt to increment the refcount on in order to
                                                 // acquire.
                                                 std::memory_order_acquire,
                                                 // Failure memory order is relaxed because on
                                                 // failure we just admit having lost a race and
                                                 // move on to the next slot to clear.
                                                 std::memory_order_relaxed)) {
                    // We successfully swapped. `entry` now holds the old value.
                    env->DeleteGlobalRef(entry.str);
                    // We intentionally don't count this as an eviction.
                }
                // If CAS fails, that's ok. Someone else is using the slot - let them.
                // Clearing is a best effort anyway.
            }
        }
    };

    clear_cache(mCache);
    clear_cache(mUtf8Cache);
}

void JniStringCache::Unload(JavaVM* vm) {
    if (gInstance == nullptr) {
        return;
    }

    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        gInstance->clear(env);
    }

    delete gInstance;
    gInstance = nullptr;
}

static jlong com_android_internal_os_JniStringCache_nativeHits() {
    return static_cast<jlong>(JniStringCache::getInstance().hits());
}

static jlong com_android_internal_os_JniStringCache_nativeMisses() {
    return static_cast<jlong>(JniStringCache::getInstance().misses());
}

static jlong com_android_internal_os_JniStringCache_nativeEvictions() {
    return static_cast<jlong>(JniStringCache::getInstance().evictions());
}

static jlong com_android_internal_os_JniStringCache_nativeSkips() {
    return static_cast<jlong>(JniStringCache::getInstance().skips());
}

static void com_android_internal_os_JniStringCache_nativeClear() {
    JniStringCache::getInstance().clear();
}

static const JNINativeMethod gMethods[] = {
        {"nativeHits", "()J", (void*)com_android_internal_os_JniStringCache_nativeHits},
        {"nativeMisses", "()J", (void*)com_android_internal_os_JniStringCache_nativeMisses},
        {"nativeEvictions", "()J", (void*)com_android_internal_os_JniStringCache_nativeEvictions},
        {"nativeSkips", "()J", (void*)com_android_internal_os_JniStringCache_nativeSkips},
        {"nativeClear", "()V", (void*)com_android_internal_os_JniStringCache_nativeClear},
};

int register_com_android_internal_os_JniStringCache(JNIEnv* env) {
    return AndroidRuntime::registerNativeMethods(env, "com/android/internal/os/JniStringCache",
                                                 gMethods, NELEM(gMethods));
}

} // namespace android
