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

package com.android.internal.os;

import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.NeverCompile;
import java.io.PrintWriter;

/** Java companion to native JNIStringCache class. */
public final class JniStringCache {

    /** Dumps cache statistics to the given writer. */
    @NeverCompile
    public static void dump(PrintWriter pw) {
        if (!android.os.Flags.parcelStringCacheEnabled()) {
            return;
        }

        pw.println("JniStringCache");
        pw.format(
                "                Hits: %,10d    Misses: %,10d\n",
                nativeHits(), nativeMisses());
        pw.format(
                "           Evictions: %,10d     Skips: %,10d\n",
                nativeEvictions(), nativeSkips());
        pw.flush();
        pw.println(" ");
    }

    /** Clears the cache. */
    public static void clear() {
        if (!android.os.Flags.parcelStringCacheEnabled()) {
            return;
        }

        nativeClear();
    }

    @CriticalNative
    private static native long nativeHits();

    @CriticalNative
    private static native long nativeMisses();

    @CriticalNative
    private static native long nativeEvictions();

    @CriticalNative
    private static native long nativeSkips();

    @CriticalNative
    private static native void nativeClear();
}
