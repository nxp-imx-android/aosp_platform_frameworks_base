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

#ifndef ANDROID_OS_INTERNAL_JNI_STRING_CACHE_H
#define ANDROID_OS_INTERNAL_JNI_STRING_CACHE_H

#include <nativehelper/JNIHelp.h>

#include <atomic>
#include <functional>

namespace android {

// A concurrent, fixed-size hash table for caching JNI strings.
//
// This class is intended to reuse jstring instances for same-value native strings, and reduce
// the number of java.lang.String instances in memory that have the same underlying characters.
// To achieve this, clients call NewString or NewStringUTF on an instance of this cache to receive a
// jstring, instead of calling the similar methods on their JNIEnv. The returned jstring may be a
// new instance or a reused instance, and either way will contain the desired string characters.
//
// Eviction happens upon hash collision, i.e. when an element is inserted to the cache but the
// respective cache table entry is already occupied by a jstring with a different character value.
//
// This class is thread-safe.
class JniStringCache {
public:
    // Global instance to use in order to maximize cache hits.
    static JniStringCache& getInstance();

    JniStringCache();
    ~JniStringCache();

    jstring NewString(JNIEnv* env, const char16_t* chars, size_t len);
    jstring NewStringUTF(JNIEnv* env, const char* bytes, size_t len);

    // Returns the number of cache hits.
    // This is a measure of the count of allocations that were saved due to caching.
    size_t hits() const;

    // Returns the number of cache misses.
    // This is a measure of the count of allocations that needed to be performed.
    size_t misses() const;

    // Returns the number of cache evictions.
    // A high number of evictions indicates many cache collisions, and may indicate that the cache
    // size should be increased.
    size_t evictions() const;

    // Returns the number of times the cache was skipped for long strings.
    size_t skips() const;

    // Attempts to clear cache entries.
    // Under concurrent usage, some entries may not be cleared.
    // Use this for instance to trim memory usage if needed.
    void clear();

private:
    struct CacheEntry {
        // Global reference to the cached jstring.
        // We store this field at the start of the struct since this field has the highest alignment
        // requirements.
        jstring str;

#ifdef __LP64__
        // On 64-bit, we can fit a pointer and two 32-bit values in a single double-wide CAS.

        // Hash of the string characters.
        uint32_t hash;

        // Reference count used to control ownership of the global reference.
        // When refCount > 0, it's safe to acquire a local reference.
        // When refCount == 0 and it's guaranteed that no other thread is trying to acquire local
        // references anymore, it's safe to delete the global reference.
        uint32_t refCount;
#else
        // On 32-bit, we have less space, so we use smaller fields.
        // We store the middle 16 bits of the 32 bit hash in a cache entry.
        // Combined with the 8 bits used to select the cache index, this gives us 24 bits of entropy
        // to detect a hash collision.
        uint16_t hash;

        // A uint16_t refCount should be enough for anybody.
        uint16_t refCount;
#endif
    };

    // Ensure the struct can fit in lock-free atomic operations width.
    static_assert(std::atomic<CacheEntry>::is_always_lock_free);

    // Ensure no padding is added to the struct.
    // Uninitialized padding may cause spurious CAS failures.
    static_assert(sizeof(CacheEntry) ==
                  sizeof(CacheEntry::str) + sizeof(CacheEntry::hash) +
                          sizeof(CacheEntry::refCount));

    // Ensure the struct fits exactly in a double-wide CAS.
    static_assert(sizeof(CacheEntry) == sizeof(void*) * 2);

    // A larger cache size would increase the memory footprint, but would increase the likelihood
    // of a cache hit. 256 is a conservative value that seems to achieve good results in anecdotal
    // testing.
    static constexpr size_t kCacheSize = 256;

    // The maximum length of a string that we will attempt to cache.
    // Since we keep strong references to the cached strings, we don't want to cache very long
    // strings.
    // This also establishes a ceiling for the maximum amount of string characters that can be
    // retained by the cache (kCacheSize * kMaxStringLength).
    static constexpr size_t kMaxStringLength = 1024;

    template <typename TChar>
    jstring newStringInternal(JNIEnv* env, const TChar* chars, size_t len,
                              std::atomic<CacheEntry>* cache,
                              std::function<jstring(const TChar*, size_t)> newJstring);

    std::atomic<CacheEntry> mCache[kCacheSize];
    std::atomic<CacheEntry> mUtf8Cache[kCacheSize];

    // Statistics counters.
    // Always accessed with std::memory_order_relaxed, because these values are not used to
    // synchronize any other memory accesses.
    std::atomic<size_t> mHits;
    std::atomic<size_t> mMisses;
    std::atomic<size_t> mEvictions;
    std::atomic<size_t> mSkips;
};

} // namespace android

#endif // ANDROID_OS_INTERNAL_JNI_STRING_CACHE_H