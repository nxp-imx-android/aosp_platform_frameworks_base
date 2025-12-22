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

package android.security.keystore2;

import android.annotation.NonNull;
import android.hardware.security.keymint.KeyParameter;
import android.hardware.security.keymint.KeyParameterValue;
import android.hardware.security.keymint.Tag;

/**
 * This class is necessary to allow the version of the KeyMint AIDL interface used in <code>
 * KeyStore2ParameterUtils.java</code> to differ by build flag <code>RELEASE_AIDL_USE_UNFROZEN
 * </code>. When <code>RELEASE_AIDL_USE_UNFROZEN</code> is not set, this file is included and the
 * KeyMint v4 AIDL interface is used, which means <code>KeyStore2ParameterUtils</code> does not
 * support <code>Tag::ML_DSA_VARIANT</code>.
 *
 * @hide
 */
// TODO(b/462036047): Remove this class once KeyMint v5 is frozen.
class KeyStore2MakeEnumTag {
    /**
     * This function constructs a {@link KeyParameter} expressing an enum value.
     * @param tag Must be KeyMint tag with the associated type ENUM or ENUM_REP.
     * @param v A 32bit integer.
     * @return An instance of {@link KeyParameter}.
     * @hide
     */
    static @NonNull KeyParameter makeEnum(int tag, int v) {
        KeyParameter kp = new KeyParameter();
        kp.tag = tag;
        switch (tag) {
            case Tag.PURPOSE:
                kp.value = KeyParameterValue.keyPurpose(v);
                break;
            case Tag.ALGORITHM:
                kp.value = KeyParameterValue.algorithm(v);
                break;
            case Tag.BLOCK_MODE:
                kp.value = KeyParameterValue.blockMode(v);
                break;
            case Tag.DIGEST:
            case Tag.RSA_OAEP_MGF_DIGEST:
                kp.value = KeyParameterValue.digest(v);
                break;
            case Tag.EC_CURVE:
                kp.value = KeyParameterValue.ecCurve(v);
                break;
            case Tag.ORIGIN:
                kp.value = KeyParameterValue.origin(v);
                break;
            case Tag.PADDING:
                kp.value = KeyParameterValue.paddingMode(v);
                break;
            case Tag.USER_AUTH_TYPE:
                kp.value = KeyParameterValue.hardwareAuthenticatorType(v);
                break;
            case Tag.HARDWARE_TYPE:
                kp.value = KeyParameterValue.securityLevel(v);
                break;
            default:
                throw new IllegalArgumentException("Not an enum or repeatable enum tag: " + tag);
        }
        return kp;
    }
}
