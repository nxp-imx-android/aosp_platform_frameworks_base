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

use anyhow::{Context, Result};
use bitflags::bitflags;
use libc::mallopt;
use nix::{errno::Errno, unistd::getuid};
use time_bindgen::tzset;

use std::ffi::c_int;
use std::io::Error;

const AID_APP_START: u32 = 10000;

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

// Enum corresponding to SUID_DUMP_* defined in linux/sched/coredump.h.
#[derive(PartialEq)]
enum Dumpable {
    Disable,
    ByUser,
    ByRoot,
}

impl TryFrom<i32> for Dumpable {
    type Error = ();

    fn try_from(value: i32) -> Result<Self, Self::Error> {
        let dumpable = match value {
            0 => Dumpable::Disable,
            1 => Dumpable::ByUser,
            2 => Dumpable::ByRoot,
            _ => return Err(()),
        };
        Ok(dumpable)
    }
}

impl From<Dumpable> for i32 {
    fn from(value: Dumpable) -> Self {
        match value {
            Dumpable::Disable => 0,
            Dumpable::ByUser => 1,
            Dumpable::ByRoot => 2,
        }
    }
}

// TODO(b/450462991): Use nix::sys::prctl::get_dumpable once `prctl` module is available to android
// and this issue is fixed: https://github.com/nix-rust/nix/issues/2684.
fn prctl_get_dumpable() -> nix::Result<Dumpable> {
    // SAFETY: Trivially safe. See `man PR_GET_DUMPABLE`.
    let res = unsafe { libc::prctl(libc::PR_GET_DUMPABLE) };
    if res == -1 {
        return Err(Errno::last());
    }
    Ok(Dumpable::try_from(res).expect("prctl(PR_GET_DUMPABLE) returned an unexpected value: {res}"))
}

// TODO(b/450462991): Use nix::sys::prctl::set_dumpable once `prctl` module is available to
// android.
fn prctl_set_dumpable(dumpable: Dumpable) -> nix::Result<()> {
    let val: i32 = dumpable.into();
    // SAFETY: Trivially safe. See `man PR_SET_DUMPABLE`.
    let res = unsafe { libc::prctl(libc::PR_SET_DUMPABLE, val) };
    if res == -1 {
        return Err(Errno::last());
    }
    Ok(())
}

pub fn setup_process_dumpability() -> Result<()> {
    if prctl_get_dumpable().context("prctl(PR_GET_DUMPABLE) failed")? == Dumpable::ByRoot
        && getuid().as_raw() >= AID_APP_START
    {
        prctl_set_dumpable(Dumpable::Disable).context("prctl(PR_SET_DUMPABLE) failed")?;
    }
    Ok(())
}
