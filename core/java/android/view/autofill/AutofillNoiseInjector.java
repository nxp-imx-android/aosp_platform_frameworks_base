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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.assist.AssistStructure.ViewNode;
import android.content.ComponentName;
import android.text.TextUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

/**
 * The class containing the implementation of the noise injection algorithm.
 *
 * @hide
 */
public final class AutofillNoiseInjector {
    private static final int FIXED_LENGTH_BYTES = 32;
    private static final int BITS_TO_RETAIN = 2;

    private final String mMasterSeed;
    private final ComponentName mActivityComponent;
    private final int mRetainedBitMask;

    /**
     * Constructs a new AutofillNoiseInjector.
     *
     * @param masterSeed The master seed for the noise injection algorithm. It's expected to be
     *     unique per device.
     * @param activityComponent the componentName of the current assistStructure, which contains the
     *     package name and activity name. Needed for calculating seed.
     * @hide
     */
    public AutofillNoiseInjector(
            @NonNull String masterSeed, @NonNull ComponentName activityComponent) {
        this.mMasterSeed = masterSeed;
        this.mActivityComponent = activityComponent;
        // Determine mRetainedBitMask based on the masterSeed hash
        Random random;
        try {
            random = new Random(hashString(masterSeed));
        } catch (NoSuchAlgorithmException e) {
            // If we can't get a legitimate hash, then we should not retain any bit.
            mRetainedBitMask = 0;
            return;
        }

        // Randomly pick BITS_TO_RETAIN bits out of a byte to retain.
        int retainedBitMask = 0;
        for (int i = 0; i < BITS_TO_RETAIN; i++) {
            // Generate a random index for which *available* bit to set.
            // There are (8 - i) bits not yet set.
            int nthAvailableBit = random.nextInt(8 - i);

            int availableBitCount = 0;
            for (int j = 0; j < 8; j++) {
                // Check if the j-th bit is NOT already set in retainedBitMask
                if ((retainedBitMask & (1 << j)) == 0) {
                    if (availableBitCount == nthAvailableBit) {
                        // This is the nth available bit we want to set.
                        retainedBitMask |= (1 << j);
                        break; // Move to the next iteration of the outer loop
                    }
                    availableBitCount++;
                }
            }
        }
        mRetainedBitMask = retainedBitMask;
    }

    // Helper hash function for a single string
    private long hashString(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        long hashLong = 0;
        for (int i = 0; i < 8; i++) {
            hashLong = (hashLong << 8) | (hashBytes[i] & 0xFF);
        }
        return hashLong;
    }

    // Hash function for combining multiple inputs for the Random seed
    private long hashInputs(
            String masterSeed, String packageName, String className, AutofillId autofillId)
            throws NoSuchAlgorithmException {
        // The virtual Id is also needed here because different virtual views may have the same view
        // id.
        String combined =
                masterSeed
                        + "|"
                        + packageName
                        + "|"
                        + className
                        + "|"
                        + autofillId.getViewId()
                        + "|"
                        + autofillId.getAutofillVirtualId();
        return hashString(combined);
    }

    /**
     * Injects noise into the given view node's text.
     *
     * @param viewNode the node to inject noise into.
     * @return returns the payload and metadata of the view node's text after noise-injection.
     * @hide
     */
    @Nullable
    public AutofillNoiseInjectedData injectNoise(@NonNull ViewNode viewNode) {
        CharSequence textChars = viewNode.getText();
        if (TextUtils.isEmpty(textChars)) {
            return null;
        }
        String originalText = textChars.toString();
        byte[] originalBytes = originalText.getBytes(StandardCharsets.UTF_16BE);

        // Adjust the payload to fixed length
        byte[] adjustedBytes = new byte[FIXED_LENGTH_BYTES];
        int lengthToCopy = Math.min(originalBytes.length, FIXED_LENGTH_BYTES);
        // Padding with 0x00 is implicit as byte arrays are initialized to 0.
        System.arraycopy(originalBytes, 0, adjustedBytes, 0, lengthToCopy);

        // Generate seed for randomization
        if (viewNode.getAutofillId() == null) {
            return null;
        }
        long seed;
        try {
            seed =
                    hashInputs(
                            mMasterSeed,
                            mActivityComponent.getPackageName(),
                            mActivityComponent.getClassName(),
                            viewNode.getAutofillId());
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        Random random = new Random(seed);

        byte[] noisedBytes = Arrays.copyOf(adjustedBytes, adjustedBytes.length);

        // Inject noise at bit level
        for (int byteIndex = 0; byteIndex < FIXED_LENGTH_BYTES; byteIndex++) {
            byte currentByte = noisedBytes[byteIndex];
            byte modifiedByte = 0;
            for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
                int finalBit = (currentByte >> bitIndex) & 1;

                // 50% chance to resample
                if (random.nextInt(100) < 50) {
                    // Flip a coin for the new value
                    finalBit = random.nextInt(100) < 50 ? 0 : 1;
                }

                if (finalBit == 1) {
                    modifiedByte = (byte) (modifiedByte | (1 << bitIndex));
                }
            }
            noisedBytes[byteIndex] = modifiedByte;
        }

        // Dropping bits that should not be retained.
        byte[] resultBytes = new byte[FIXED_LENGTH_BYTES];
        for (int byteIndex = 0; byteIndex < FIXED_LENGTH_BYTES; byteIndex++) {
            resultBytes[byteIndex] = (byte) (noisedBytes[byteIndex] & mRetainedBitMask);
        }

        return new AutofillNoiseInjectedData(mRetainedBitMask, resultBytes);
    }
}
