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

package com.android.server.locksettings;

import android.annotation.UserIdInt;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.server.utils.Slogf;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/**
 * This is the software rate-limiter used by LockSettingsService. It rate-limits guesses of LSKFs
 * (Lock Screen Knowledge Factors), complementing the "hardware" (TEE or Secure Element)
 * rate-limiter provided by the Gatekeeper or Weaver HAL.
 *
 * <p>This has several purposes:
 *
 * <ol>
 *   <li>Keep track of recent wrong guesses, and reject duplicate wrong guesses before they reach
 *       the hardware rate-limiter and actually count as wrong guesses. This is helpful for
 *       legitimate users who may mis-enter their LSKF in the same way multiple times. It does not
 *       help capable attackers, for whom duplicate wrong guesses provide no additional information.
 *       Overall, this logic makes it feasible to more strictly rate-limit (unique) wrong guesses,
 *       which increases security. It also eliminates the need for all hardware rate-limiter
 *       implementations to implement the same duplicate wrong detection logic.
 *   <li>Enable faster validation and deployment of rate-limiter improvements, before mirroring
 *       those same changes in the hardware rate-limiters which tends to take longer.
 *   <li>Serve as a fallback just in case the hardware rate-limiter (which is a vendor component)
 *       does not work properly. The software and hardware rate-limiters operate concurrently, so
 *       the stricter of the two is what is normally observed. Of course, a properly implemented
 *       hardware rate-limiter is more secure and is always supposed to be present too.
 *   <li>Reject guesses of too-short LSKFs before they count as real guesses.
 * </ol>
 */
class SoftwareRateLimiter {

    private static final String TAG = "SoftwareRateLimiter";

    /**
     * The maximum number of unique wrong guesses saved per LSKF.
     *
     * <p>5 should be more than enough, considering that the chance of matching on the n-th last
     * unique wrong guess should (in general) diminish as n increases, and the rate-limiter kicks in
     * after the first 5 unique wrong guesses anyway.
     */
    @VisibleForTesting static final int MAX_SAVED_WRONG_GUESSES = 5;

    /**
     * The duration between an LSKF's most recent wrong guess to when that LSKF's saved wrong
     * guesses are forgotten.
     *
     * <p>5 minutes provides a reasonable balance between user convenience and minimizing the small
     * security risk of wrong guesses being kept around in system_server memory. (Wrong guesses can
     * be somewhat sensitive information, since they may be similar to the correct LSKF or they may
     * be the correct LSKF for another device or user.)
     */
    @VisibleForTesting static final Duration SAVED_WRONG_GUESS_TIMEOUT = Duration.ofMinutes(5);

    /**
     * A table that maps the number of (real) wrong guesses to the delay that is enforced after that
     * number of (real) wrong guesses. Out-of-bounds indices default to the final delay.
     */
    private static final Duration[] DELAY_TABLE =
            new Duration[] {
                /* 0 */ Duration.ZERO,
                /* 1 */ Duration.ZERO,
                /* 2 */ Duration.ZERO,
                /* 3 */ Duration.ZERO,
                /* 4 */ Duration.ZERO,
                /* 5 */ Duration.ofMinutes(1),
                /* 6 */ Duration.ofMinutes(5),
                /* 7 */ Duration.ofMinutes(15),
                /* 8 */ Duration.ofMinutes(30),
                /* 9 */ Duration.ofMinutes(90),
                /* 10 */ Duration.ofMinutes(243), // This and the rest are 3^(n-5) minutes.
                /* 11 */ Duration.ofMinutes(729),
                /* 12 */ Duration.ofMinutes(2187),
                /* 13 */ Duration.ofMinutes(6561),
                /* 14 */ Duration.ofMinutes(19683),
                /* 15 */ Duration.ofMinutes(59049),
                /* 16 */ Duration.ofMinutes(177147),
                /* 17 */ Duration.ofMinutes(531441),
                /* 18 */ Duration.ofMinutes(1594323),
                /* 19 */ Duration.ofMinutes(4782969),
            };

    private final Injector mInjector;

    /**
     * Whether the software rate-limiter is actually in enforcing mode. In non-enforcing mode all
     * delays are considered to be zero, and "duplicate wrong guess" results are not returned.
     */
    private final boolean mEnforcing;

    /** The software rate-limiter state for each LSKF */
    @GuardedBy("this")
    private final ArrayMap<LskfIdentifier, RateLimiterState> mState = new ArrayMap<>();

    /** The software rate-limiter state for a particular LSKF */
    private static class RateLimiterState {

        /**
         * The number of (real) wrong guesses since the last correct guess. This includes only wrong
         * guesses that reached the hardware rate-limiter and were reported to be wrong guesses. It
         * excludes guesses that never reached the real credential check for a reason such as
         * detection of a duplicate wrong guess, too short, delay still remaining, etc. It also
         * excludes any times that the real credential check failed due to a transient error (e.g.
         * failure to communicate with the Secure Element) rather than due to the guess being wrong.
         */
        public int numWrongGuesses;

        /**
         * The time since boot at which the number of wrong guesses was last incremented, or zero if
         * the number of wrong guesses was last incremented before the current boot.
         */
        public Duration timeSinceBootOfLastWrongGuess = Duration.ZERO;

        /**
         * The list of wrong guesses that were recently tried already in the current boot, ordered
         * from newest to oldest. The used portion is followed by nulls in any unused space.
         */
        public final LockscreenCredential[] savedWrongGuesses =
                new LockscreenCredential[MAX_SAVED_WRONG_GUESSES];

        RateLimiterState(int numWrongGuesses) {
            this.numWrongGuesses = numWrongGuesses;
        }
    }

    SoftwareRateLimiter(Injector injector) {
        this(injector, /* enforcing= */ true);
    }

    SoftwareRateLimiter(Injector injector, boolean enforcing) {
        mInjector = injector;
        mEnforcing = enforcing;
    }

    private Duration getCurrentDelay(RateLimiterState state) {
        if (!mEnforcing) {
            return Duration.ZERO;
        } else if (state.numWrongGuesses >= 0 && state.numWrongGuesses < DELAY_TABLE.length) {
            return DELAY_TABLE[state.numWrongGuesses];
        } else {
            return DELAY_TABLE[DELAY_TABLE.length - 1];
        }
    }

    /**
     * Applies the software rate-limiter to the given LSKF guess.
     *
     * @param id the ID of the protector or special credential
     * @param guess the LSKF being checked
     * @return a {@link SoftwareRateLimiterResult}
     */
    synchronized SoftwareRateLimiterResult apply(LskfIdentifier id, LockscreenCredential guess) {

        // Check for too-short credential that cannot possibly be correct.
        // There's no need to waste any real guesses for such credentials.
        // Should be handled by the UI already, but check here too just in case.
        final int minLength = switch (guess.getType()) {
            case LockPatternUtils.CREDENTIAL_TYPE_PATTERN ->
                    LockPatternUtils.MIN_LOCK_PATTERN_SIZE;
            case LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PIN ->
                    LockPatternUtils.MIN_LOCK_PASSWORD_SIZE;
            default -> 0;
        };
        if (guess.size() < minLength) {
            Slogf.e(TAG, "Credential is too short; size=%d", guess.size());
            return SoftwareRateLimiterResult.credentialTooShort();
        }

        final RateLimiterState state =
                mState.computeIfAbsent(
                        id,
                        key -> {
                            // The state isn't cached yet. Create it.
                            //
                            // For LSKF-based synthetic password protectors the only persistent
                            // software rate-limiter state is the number of wrong guesses, which is
                            // loaded from a counter file on-disk. timeSinceBootOfLastWrongGuess is
                            // just set to zero, so effectively the delay resets to its original
                            // value (for the current number of wrong guesses) upon reboot. That
                            // matches what typical hardware rate-limiter implementations do; they
                            // typically do not have access to a trusted real-time clock that runs
                            // without the device being powered on.
                            //
                            // Likewise, rebooting causes any saved wrong guesses to be forgotten.
                            return new RateLimiterState(readWrongGuessCounter(id));
                        });

        // Check for remaining delay. Note that the case of a positive remaining delay normally
        // won't be reached, since reportWrongGuess() will have returned the delay when the last
        // guess was made, causing the lock screen to block inputs for that amount of time. But
        // checking for it is still needed to cover any cases where a guess gets made anyway, for
        // example following a reboot which causes the lock screen to "forget" the delay.
        final Duration delay = getCurrentDelay(state);
        final Duration now = mInjector.getTimeSinceBoot();
        final Duration remainingDelay = state.timeSinceBootOfLastWrongGuess.plus(delay).minus(now);
        if (remainingDelay.isPositive()) {
            Slogf.e(
                    TAG,
                    "Rate-limited; numWrongGuesses=%d, remainingDelay=%s",
                    state.numWrongGuesses,
                    remainingDelay);
            return SoftwareRateLimiterResult.rateLimited(remainingDelay);
        }

        // Check for duplicate wrong guess.
        for (int i = 0; i < MAX_SAVED_WRONG_GUESSES; i++) {
            LockscreenCredential wrongGuess = state.savedWrongGuesses[i];
            if (wrongGuess != null && wrongGuess.equals(guess)) {
                Slog.i(TAG, "Duplicate wrong guess");
                // The guess is now the most recent wrong guess, so move it to the front of the
                // list.
                for (int j = i; j >= 1; j--) {
                    state.savedWrongGuesses[j] = state.savedWrongGuesses[j - 1];
                }
                state.savedWrongGuesses[0] = wrongGuess;
                return mEnforcing
                        ? SoftwareRateLimiterResult.duplicateWrongGuess()
                        : SoftwareRateLimiterResult.continueToHardware();
            }
        }

        // Ready to make a real guess. Continue on to the real credential check.
        return SoftwareRateLimiterResult.continueToHardware();
    }

    /**
     * Reports a successful guess to the software rate-limiter. This causes the wrong guess counter
     * and saved wrong guesses to be cleared.
     */
    synchronized void reportSuccess(LskfIdentifier id) {
        RateLimiterState state = getExistingState(id);
        // If the wrong guess counter is still 0, then there is no need to write it. Nor can there
        // be any saved wrong guesses, so there is no need to forget them. This optimizes for the
        // common case where the first guess is correct.
        if (state.numWrongGuesses != 0) {
            state.numWrongGuesses = 0;
            writeWrongGuessCounter(id, state);
            forgetSavedWrongGuesses(state);
        }
    }

    // Inserts a new wrong guess into the given list of saved wrong guesses.
    private void insertNewWrongGuess(RateLimiterState state, LockscreenCredential newWrongGuess) {
        // Shift the saved wrong guesses over by one to make room for the new one. If the list is
        // full, zeroize and evict the oldest entry.
        if (state.savedWrongGuesses[MAX_SAVED_WRONG_GUESSES - 1] != null) {
            state.savedWrongGuesses[MAX_SAVED_WRONG_GUESSES - 1].zeroize();
        }
        for (int i = MAX_SAVED_WRONG_GUESSES - 1; i >= 1; i--) {
            state.savedWrongGuesses[i] = state.savedWrongGuesses[i - 1];
        }

        // Store the new wrong guess. Duplicate it, since it may be held onto for some time. This
        // class is responsible for zeroizing the duplicated credential once its lifetime expires.
        state.savedWrongGuesses[0] = newWrongGuess.duplicate();
    }

    /**
     * Reports a new wrong guess to the software rate-limiter.
     *
     * <p>This must be called immediately after the hardware rate-limiter reported that the given
     * guess is incorrect, before the credential check failure is made visible in the UI. It is
     * assumed that {@link #apply(LskfIdentifier, LockscreenCredential)} was previously called with
     * the same parameters and returned a {@code CONTINUE_TO_HARDWARE} result.
     *
     * @param id the ID of the protector or special credential
     * @param newWrongGuess a new wrong guess for the LSKF
     * @return the delay until when the next guess will be allowed
     */
    synchronized Duration reportWrongGuess(LskfIdentifier id, LockscreenCredential newWrongGuess) {
        RateLimiterState state = getExistingState(id);

        // In non-enforcing mode, ignore duplicate guesses here since they were already counted.
        // In enforcing mode, this method isn't passed duplicate guesses.
        if (!mEnforcing && ArrayUtils.contains(state.savedWrongGuesses, newWrongGuess)) {
            return Duration.ZERO;
        }

        state.numWrongGuesses++;
        state.timeSinceBootOfLastWrongGuess = mInjector.getTimeSinceBoot();

        // Update the counter on-disk. It is important that this be done before the failure is
        // reported to the UI, and that it be done synchronously e.g. by fsync()-ing the file and
        // its containing directory. This minimizes the risk of the counter being rolled back.
        writeWrongGuessCounter(id, state);

        insertNewWrongGuess(state, newWrongGuess);

        // Schedule the saved wrong guesses to be forgotten after a few minutes, extending the
        // existing timeout if one was already running.
        mInjector.removeCallbacksAndMessages(/* token= */ state);
        mInjector.postDelayed(
                () -> {
                    Slogf.i(
                            TAG,
                            "Forgetting wrong LSKF guesses for user %d, protector %016x",
                            id.userId,
                            id.protectorId);
                    synchronized (this) {
                        forgetSavedWrongGuessesNoCancel(state);
                    }
                },
                /* token= */ state,
                SAVED_WRONG_GUESS_TIMEOUT.toMillis());

        return getCurrentDelay(state);
    }

    @GuardedBy("this")
    private RateLimiterState getExistingState(LskfIdentifier id) {
        RateLimiterState state = mState.get(id);
        if (state == null) {
            // This should never happen, since reportSuccess() and reportWrongGuess() are always
            // supposed to be paired with a call to apply() that created the state if it did not
            // exist. Nor is it supported to call clearLskfState() or clearUserState() in between;
            // higher-level locking in LockSettingsService guarantees that never happens.
            throw new IllegalStateException("Could not find RateLimiterState");
        }
        return state;
    }

    @GuardedBy("this")
    private void forgetSavedWrongGuesses(RateLimiterState state) {
        mInjector.removeCallbacksAndMessages(/* token= */ state);
        forgetSavedWrongGuessesNoCancel(state);
    }

    @GuardedBy("this")
    private void forgetSavedWrongGuessesNoCancel(RateLimiterState state) {
        for (int i = 0; i < MAX_SAVED_WRONG_GUESSES; i++) {
            if (state.savedWrongGuesses[i] != null) {
                state.savedWrongGuesses[i].zeroize();
                state.savedWrongGuesses[i] = null;
            }
        }
    }

    /**
     * Clears the in-memory software rate-limiter state for a protector that is being removed.
     *
     * @param id the ID of the protector or special credential
     */
    synchronized void clearLskfState(LskfIdentifier id) {
        int index = mState.indexOfKey(id);
        if (index >= 0) {
            forgetSavedWrongGuesses(mState.valueAt(index));
            mState.removeAt(index);
        }
    }

    /**
     * Clears the in-memory software rate-limiter state for a user that is being removed.
     *
     * @param userId the ID of the user being removed
     */
    synchronized void clearUserState(@UserIdInt int userId) {
        for (int index = mState.size() - 1; index >= 0; index--) {
            LskfIdentifier id = mState.keyAt(index);
            if (id.userId == userId) {
                forgetSavedWrongGuesses(mState.valueAt(index));
                mState.removeAt(index);
            }
        }
    }

    private int readWrongGuessCounter(LskfIdentifier id) {
        if (id.isSpecialCredential()) {
            // Special credentials (e.g. FRP credential and repair mode exit credential) do not yet
            // store a persistent wrong guess counter.
            return 0;
        }
        return mInjector.readWrongGuessCounter(id);
    }

    private void writeWrongGuessCounter(LskfIdentifier id, RateLimiterState state) {
        if (id.isSpecialCredential()) {
            // Special credentials (e.g. FRP credential and repair mode exit credential) do not yet
            // store a persistent wrong guess counter.
            return;
        }
        mInjector.writeWrongGuessCounter(id, state.numWrongGuesses);
    }

    // Only for unit tests.
    @VisibleForTesting
    Duration[] getDelayTable() {
        return DELAY_TABLE;
    }

    synchronized void dump(IndentingPrintWriter pw) {
        pw.println("Enforcing: " + mEnforcing);
        for (int index = 0; index < mState.size(); index++) {
            final LskfIdentifier lskfId = mState.keyAt(index);
            pw.println(
                    TextUtils.formatSimple(
                            "userId=%d, protectorId=%016x", lskfId.userId, lskfId.protectorId));
            final RateLimiterState state = mState.valueAt(index);
            pw.increaseIndent();
            pw.println("numWrongGuesses=" + state.numWrongGuesses);
            pw.println("timeSinceBootOfLastWrongGuess=" + state.timeSinceBootOfLastWrongGuess);
            pw.println(
                    "numSavedWrongGuesses="
                            + Arrays.stream(state.savedWrongGuesses)
                                    .filter(Objects::nonNull)
                                    .count());
            pw.decreaseIndent();
        }
    }

    interface Injector {
        int readWrongGuessCounter(LskfIdentifier id);

        void writeWrongGuessCounter(LskfIdentifier id, int count);

        Duration getTimeSinceBoot();

        void removeCallbacksAndMessages(Object token);

        void postDelayed(Runnable runnable, Object token, long delayMillis);
    }
}
