//
// Copyright (C) 2025 The Android Open-Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use bitflags::bitflags;
use libc::mallopt;
use time_bindgen::tzset;

use std::ffi::c_int;
use std::io::Error;

/// A safe wrapper around tzset()
pub fn reset_time_zone() {
    // Refresh Bionic's timezone information.
    // SAFETY: Not passing any function parameter.
    unsafe { tzset() };
}

bitflags! {
    /// Runtime flag constants.
    /// Must match values in com.android.internal.os.Zygote.
    #[derive(Debug, Clone, Copy, PartialEq, Eq)]
    pub struct RuntimeFlags: u32 {
        const DEBUG_ENABLE_JDWP = 1;
        const PROFILE_SYSTEM_SERVER = 1 << 14;
        const PROFILE_FROM_SHELL = 1 << 15;
        const MEMORY_TAG_LEVEL_MASK = (1 << 19) | (1 << 20);
        const MEMORY_TAG_LEVEL_TBI = 1 << 19;
        const MEMORY_TAG_LEVEL_ASYNC = 2 << 19;
        const MEMORY_TAG_LEVEL_SYNC = 3 << 19;
        const GWP_ASAN_LEVEL_MASK = (1 << 21) | (1 << 22);
        const GWP_ASAN_LEVEL_NEVER = 0 << 21;
        const GWP_ASAN_LEVEL_LOTTERY = 1 << 21;
        const GWP_ASAN_LEVEL_ALWAYS = 2 << 21;
        const GWP_ASAN_LEVEL_DEFAULT = 3 << 21;
        const NATIVE_HEAP_ZERO_INIT_ENABLED = 1 << 23;
        const PROFILEABLE = 1 << 24;
        const DEBUG_ENABLE_PTRACE = 1 << 25;
        const ENABLE_PAGE_SIZE_APP_COMPAT = 1 << 26;
    }
}

impl RuntimeFlags {
    pub fn get_heap_tagging_level(&self) -> c_int {
        match self.intersection(Self::MEMORY_TAG_LEVEL_MASK) {
            Self::MEMORY_TAG_LEVEL_TBI => libc::M_HEAP_TAGGING_LEVEL_TBI,
            Self::MEMORY_TAG_LEVEL_ASYNC => libc::M_HEAP_TAGGING_LEVEL_ASYNC,
            Self::MEMORY_TAG_LEVEL_SYNC => libc::M_HEAP_TAGGING_LEVEL_SYNC,
            _ => libc::M_HEAP_TAGGING_LEVEL_NONE,
        }
    }

    pub fn is_native_heap_zero_init_enabled(&self) -> bool {
        self.contains(Self::NATIVE_HEAP_ZERO_INIT_ENABLED)
    }
}

pub fn apply_runtime_flags(runtime_flags: u32) {
    let flags = match RuntimeFlags::from_bits(runtime_flags) {
        Some(flags) => flags,
        None => {
            log::warn!("runtime_flags doesn't have a valid representation: {}", runtime_flags);
            return;
        }
    };

    if !flags.is_native_heap_zero_init_enabled() {
        // SAFETY: Setting configuration with valid argument (0 = disable).
        let ret = unsafe { mallopt(libc::M_BIONIC_ZERO_INIT, 0) };
        if ret == 0 {
            log::warn!("Failed to mallopt(M_BIONIC_ZERO_INIT): {}", Error::last_os_error());
        }
    }

    // SAFETY: Setting configuration with valid argument (as defined in get_heap_tagging_level).
    let ret =
        unsafe { mallopt(libc::M_BIONIC_SET_HEAP_TAGGING_LEVEL, flags.get_heap_tagging_level()) };
    if ret == 0 {
        log::warn!(
            "Failed to mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL): {}",
            Error::last_os_error()
        );
    }
}
