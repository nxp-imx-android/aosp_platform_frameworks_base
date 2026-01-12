/*
 * Copyright (C) 2026 The Android Open Source Project
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

#pragma once

#include "common.h"

namespace android::adpf::api {

struct SupportInfoWrapper : public hal::SupportInfo {
    bool isSessionModeSupported(hal::SessionMode mode) {
        return getEnumSupportFromBitfield(mode, sessionModes);
    }

    bool isSessionHintSupported(hal::SessionHint hint) {
        return getEnumSupportFromBitfield(hint, sessionHints);
    }

private:
    template <class T>
    bool getEnumSupportFromBitfield(T& enumValue, int64_t& supportBitfield) {
        // extract the bit corresponding to the enum by shifting the bitfield
        // over that much and cutting off any extra values
        return (supportBitfield >> static_cast<int>(enumValue)) % 2;
    }
};

} // namespace android::adpf::api