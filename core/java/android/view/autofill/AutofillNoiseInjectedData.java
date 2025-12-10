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

package android.view.autofill;

import static android.service.autofill.Flags.FLAG_STRING_REBUILD_API;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents the noise-injected version of a text field's content, generated using a differential
 * privacy algorithm. While the Autofill providers won't be able to recover the actual text from the
 * noise-injected data from one device, with uploads from sufficient number of devices, the Autofill
 * providers are able to rebuild the constant, non-private parts of the text to help them improve
 * Autofill detection's quality.
 *
 * <p>The noise injection process involves the following steps: 1. The original string is converted
 * to a byte array using UTF-16BE Encoding. The actual length of the string is hidden by padding
 * with 0x0000 or truncating as needed. 2. Then for each bit in the byte array, random noise is
 * introduced by "coin flipping": first we flip a coin to decide whether to change this bit. The
 * probability is 50%. If we decide to change this bit, then we flip another coin to decide its new
 * value. The probability is: 50% to be 1, 50% to be 0. 3. To further enhance privacy, each device
 * may drop some of the bits after noise injection. {@link #getRetainedBitMask()} tells you which
 * bits are retained for the current payload.
 *
 * <p>With the coin flipping mechanism described above, each bit has 75% chance to report the real
 * value, and 25% chance to report the opposite value(e.g. real value is 1, reported value is 0).
 * This means if a bit remains the same across all devices(i.e. it's a part of a non-personalized
 * string such as "Country"), then with a large enough amount of reports collected from different
 * devices, then the ratio of real value among all reports should be close to 75%. In another word,
 * if we see close to 75% of the reports show 1 then the real value of this bit should be a constant
 * 1; if we see close to 75% of the reports show 0 then the real value of this bit should be a
 * constant 0; all other distributions indicate this bit is not same across different devices(hence
 * very likely to be personalized/private information), which should be discarded as noise.
 */
@FlaggedApi(FLAG_STRING_REBUILD_API)
public final class AutofillNoiseInjectedData implements Parcelable {

    /** @hide */
    @IntDef(
            prefix = {"RETAIN_BIT_"},
            value = {
                RETAIN_BIT_0,
                RETAIN_BIT_1,
                RETAIN_BIT_2,
                RETAIN_BIT_3,
                RETAIN_BIT_4,
                RETAIN_BIT_5,
                RETAIN_BIT_6,
                RETAIN_BIT_7
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RetainBitType {}

    /**
     * Flag to retain the Least Significant Bit (bit 0) within each byte. Mask value is {@code 1 <<
     * 0} (0x01).
     */
    public static final @RetainBitType int RETAIN_BIT_0 = 1 << 0;

    /** Flag to retain bit 1 within each byte. Mask value is {@code 1 << 1} (0x02). */
    public static final @RetainBitType int RETAIN_BIT_1 = 1 << 1;

    /** Flag to retain bit 2 within each byte. Mask value is {@code 1 << 2} (0x04). */
    public static final @RetainBitType int RETAIN_BIT_2 = 1 << 2;

    /** Flag to retain bit 3 within each byte. Mask value is {@code 1 << 3} (0x08). */
    public static final @RetainBitType int RETAIN_BIT_3 = 1 << 3;

    /** Flag to retain bit 4 within each byte. Mask value is {@code 1 << 4} (0x10). */
    public static final @RetainBitType int RETAIN_BIT_4 = 1 << 4;

    /** Flag to retain bit 5 within each byte. Mask value is {@code 1 << 5} (0x20). */
    public static final @RetainBitType int RETAIN_BIT_5 = 1 << 5;

    /** Flag to retain bit 6 within each byte. Mask value is {@code 1 << 6} (0x40). */
    public static final @RetainBitType int RETAIN_BIT_6 = 1 << 6;

    /**
     * Flag to retain the Most Significant Bit (bit 7) within each byte. Mask value is {@code 1 <<
     * 7} (0x80).
     */
    public static final @RetainBitType int RETAIN_BIT_7 = 1 << 7;

    private final int mRetainedBitMask;
    private final @NonNull byte[] mNoiseInjectedPayload;

    /** @hide */
    public AutofillNoiseInjectedData(int retainedBitMask, @NonNull byte[] noiseInjectedPayload) {
        mRetainedBitMask = retainedBitMask;
        mNoiseInjectedPayload = noiseInjectedPayload;
    }

    private AutofillNoiseInjectedData(Parcel in) {
        mRetainedBitMask = in.readInt();
        mNoiseInjectedPayload = in.createByteArray();
    }

    /**
     * Returns a bit mask indicating which bits were retained for *each byte* in the payload
     * returned by {@link #getNoiseInjectedPayload()}. Bits not retained are marked as 0 in the
     * returned payload. The same bit mask is applied to every byte in the payload.
     *
     * @return Bitwise-or of zero or more of flags {@link #RETAIN_BIT_0}, {@link #RETAIN_BIT_1},
     *     {@link #RETAIN_BIT_2}, {@link #RETAIN_BIT_3}, {@link #RETAIN_BIT_4}, {@link
     *     #RETAIN_BIT_5}, {@link #RETAIN_BIT_6}, {@link #RETAIN_BIT_7}.
     */
    @FlaggedApi(FLAG_STRING_REBUILD_API)
    public int getRetainedBitMask() {
        return mRetainedBitMask;
    }

    /**
     * Gets the noise-injected and bit-reduced data. The original text has been transformed by a
     * differential privacy algorithm, including random bit flips and removing some of the bits in
     * every byte({@link #getRetainedBitMask()} tells you which bits are retained).
     *
     * @return A byte array representing the obfuscated text.
     */
    @FlaggedApi(FLAG_STRING_REBUILD_API)
    @NonNull
    public byte[] getNoiseInjectedPayload() {
        return mNoiseInjectedPayload;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mRetainedBitMask);
        dest.writeByteArray(mNoiseInjectedPayload);
    }

    @NonNull
    public static final Creator<AutofillNoiseInjectedData> CREATOR =
            new Creator<AutofillNoiseInjectedData>() {
                @Override
                public AutofillNoiseInjectedData createFromParcel(Parcel in) {
                    return new AutofillNoiseInjectedData(in);
                }

                @Override
                public AutofillNoiseInjectedData[] newArray(int size) {
                    return new AutofillNoiseInjectedData[size];
                }
            };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AutofillNoiseInjectedData that = (AutofillNoiseInjectedData) o;
        return mRetainedBitMask == that.mRetainedBitMask
                && Arrays.equals(mNoiseInjectedPayload, that.mNoiseInjectedPayload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mRetainedBitMask);
        result = 31 * result + Arrays.hashCode(mNoiseInjectedPayload);
        return result;
    }
}
