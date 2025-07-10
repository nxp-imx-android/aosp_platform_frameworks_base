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

//! The crate providing the functionality to manage the native application process.

use log::{info, LevelFilter};

mod library_loader;
mod task;

/// Start NativeActivityThread to manage the process.
pub fn run_native_activity_thread(start_seq: i64) -> ! {
    logger::init(
        logger::Config::default()
            .with_tag_on_device("native_activity_thread")
            .with_max_level(LevelFilter::Trace),
    );
    info!("Hello from the native activity thread! start_seq={}", start_seq);

    // TODO(b/402614577): Implement the ActivityThread logic.

    panic!("Something wrong happened!");
}
