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

package com.android.server.am;

/**
 * A helper class that holds data that is useful for calculating system memory
 * usage.
 */
public final class MemoryUsageStats {
    long nativePss;
    long nativeSwapPss;
    long nativeRss;
    long nativePrivateDirty;
    long dalvikPss;
    long dalvikSwapPss;
    long dalvikRss;
    long dalvikPrivateDirty;
    long otherPss;
    long otherSwapPss;
    long otherRss;
    long otherPrivateDirty;
    long totalPss;
    long totalSwapPss;
    long totalRss;
    long totalPrivateDirty;
    long totalNativePss;
    long totalMemtrackGraphics;
    long totalMemtrackGl;
    long cachedPss;
    long cachedSwapPss;
}