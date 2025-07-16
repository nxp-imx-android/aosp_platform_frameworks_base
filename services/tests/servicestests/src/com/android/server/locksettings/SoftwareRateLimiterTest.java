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

import static com.android.server.locksettings.SoftwareRateLimiterResult.CONTINUE_TO_HARDWARE;
import static com.android.server.locksettings.SoftwareRateLimiterResult.CREDENTIAL_TOO_SHORT;
import static com.android.server.locksettings.SoftwareRateLimiterResult.DUPLICATE_WRONG_GUESS;
import static com.android.server.locksettings.SoftwareRateLimiterResult.NO_MORE_GUESSES;
import static com.android.server.locksettings.SoftwareRateLimiterResult.RATE_LIMITED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** atest FrameworksServicesTests:SoftwareRateLimiterTest */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class SoftwareRateLimiterTest {

    private TestInjector mInjector;
    private SoftwareRateLimiter mRateLimiter;

    @Before
    public void setup() {
        mInjector = new TestInjector();
        mRateLimiter = new SoftwareRateLimiter(mInjector);
    }

    @Test
    public void testRateLimitSchedule() {
        mRateLimiter = new SoftwareRateLimiter(mInjector);
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final Duration[] delayTable = mRateLimiter.getDelayTable();

        for (int i = 0; i < delayTable.length; i++) {
            final LockscreenCredential guess = newPassword("password" + i);
            Duration expectedDelay = delayTable[i];
            if (!expectedDelay.isZero()) {
                verifyRateLimited(id, guess, expectedDelay);
                mInjector.advanceTime(Duration.ofSeconds(1));
                verifyRateLimited(id, guess, expectedDelay.minus(Duration.ofSeconds(1)));
                mInjector.advanceTime(expectedDelay.minus(Duration.ofMillis(1001)));
                verifyRateLimited(id, guess, Duration.ofMillis(1));
                mInjector.advanceTime(Duration.ofMillis(1));
            }
            verifyUniqueWrongGuess(id, guess, delayTable[Math.min(i + 1, delayTable.length - 1)]);
            verifyFailureCounter(id, i + 1);
        }
        verifyNoMoreGuesses(id, newPassword("password"));
    }

    // This test re-instantiates the SoftwareRateLimiter, like what happens after a reboot, after
    // there have already been a certain number of wrong guesses. It verifies that the delay for the
    // current number of wrong guesses is reset to its original value and starts counting down from
    // the new instantiation time.
    @Test
    public void testExistingDelayResetsOnReinstantiation() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential guess = newPassword("password");
        final Duration[] delayTable = mRateLimiter.getDelayTable();
        final int numWrongGuesses = delayTable.length - 1;
        final Duration expectedDelay = delayTable[numWrongGuesses];

        mInjector.setTime(Duration.ofSeconds(10));
        mInjector.writeFailureCounter(id, numWrongGuesses);
        mRateLimiter = new SoftwareRateLimiter(mInjector);

        mInjector.setTime(Duration.ofSeconds(1));
        verifyRateLimited(id, guess, expectedDelay.minus(Duration.ofSeconds(1)));
    }

    @Test
    public void testSuccessResetsFailureCounter() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        makeGuessesUntilRateLimited(id);
        mRateLimiter.reportSuccess(id);
        verifyFailureCounter(id, 0);
        verifyUniqueWrongGuess(id, newPassword("password"));
        verifyFailureCounter(id, 1);
    }

    @Test
    public void testRateLimitingIsPerProtector() {
        final LskfIdentifier id1 = new LskfIdentifier(10, 1000);
        final LskfIdentifier id2 = new LskfIdentifier(10, 2000);
        // This must be distinct from the guesses used by makeGuessesUntilRateLimited().
        final LockscreenCredential guess = newPassword("password");

        makeGuessesUntilRateLimited(id1);

        verifyRateLimited(id1, guess);
        verifyUniqueWrongGuess(id2, guess);

        makeGuessesUntilRateLimited(id2);

        verifyRateLimited(id1, guess);
        verifyRateLimited(id2, guess);

        mRateLimiter.reportSuccess(id1);

        verifyUniqueWrongGuess(id1, guess);
        verifyRateLimited(id2, guess);

        mRateLimiter.reportSuccess(id2);

        verifyDuplicateWrongGuess(id1, guess);
        verifyUniqueWrongGuess(id2, guess);
    }

    // When a protector is rate-limited, the returned status should always be RATE_LIMITED, even if
    // the given guess is one of the saved wrong guesses. I.e., the rate-limit check should occur
    // *before* the check for a duplicate wrong guess.
    @Test
    public void testDuplicateWrongGuessesNotDetectedWhenRateLimited() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        LockscreenCredential prevGuess = null, nextGuess;
        for (int i = 0; i < 100; i++, prevGuess = nextGuess) {
            nextGuess = newPassword("rate_limit_me" + i);
            SoftwareRateLimiterResult result = mRateLimiter.apply(id, nextGuess);
            if (result.code != CONTINUE_TO_HARDWARE) {
                assertThat(result.code).isEqualTo(RATE_LIMITED);
                assertThat(prevGuess).isNotNull();
                verifyRateLimited(id, prevGuess);
                return;
            }
            mRateLimiter.reportFailure(id, nextGuess, /* isCertainlyWrongGuess= */ true);
        }
        fail("Rate-limiter never kicked in");
    }

    @Test
    public void testDuplicateWrongGuessIsDetected() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential guess = newPassword("password");

        verifyUniqueWrongGuess(id, guess);
        verifyFailureCounter(id, 1);
        for (int i = 0; i < 100; i++) {
            verifyDuplicateWrongGuess(id, guess);
            verifyFailureCounter(id, 1);
        }
    }

    @Test
    public void testOldestSavedWrongGuess() {
        // Test a wrong guess that is just recent enough to still be saved.
        testNthLastWrongGuess(SoftwareRateLimiter.MAX_SAVED_WRONG_GUESSES, DUPLICATE_WRONG_GUESS);
    }

    @Test
    public void testNewestNotSavedWrongGuess() {
        // Test a wrong guess that is just old enough to have been forgotten.
        testNthLastWrongGuess(
                SoftwareRateLimiter.MAX_SAVED_WRONG_GUESSES + 1, CONTINUE_TO_HARDWARE);
    }

    private void testNthLastWrongGuess(
            int n, @SoftwareRateLimiterResult.Code int expectedResultCode) {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);

        for (int i = 0; i < n; i++) {
            verifyUniqueWrongGuess(id, newPassword("password" + i));
            mInjector.advanceTime(Duration.ofDays(1)); // Advance past any delay
        }
        SoftwareRateLimiterResult result = mRateLimiter.apply(id, newPassword("password0"));
        assertThat(result.code).isEqualTo(expectedResultCode);
    }

    @Test
    public void testSavedWrongGuessEvictionPolicyIsLru() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);

        for (int i = 0; i < SoftwareRateLimiter.MAX_SAVED_WRONG_GUESSES; i++) {
            verifyUniqueWrongGuess(id, newPassword("password" + i));
            mInjector.advanceTime(Duration.ofDays(1)); // Advance past any delay
        }
        // The list of saved guesses should now be full. Try the oldest one, which should cause it
        // to be moved to the front of the list.
        verifyDuplicateWrongGuess(id, newPassword("password0"));

        // Try a new guess. Then verify that it caused the eviction of password1, not password0.
        verifyUniqueWrongGuess(id, newPassword("passwordN"));
        mInjector.advanceTime(Duration.ofDays(1)); // Advance past any delay
        verifyDuplicateWrongGuess(id, newPassword("password0"));
        verifyUniqueWrongGuess(id, newPassword("password1"));
    }

    @Test
    public void testSavedWrongGuessesArePerProtector() {
        final LskfIdentifier id1 = new LskfIdentifier(10, 1000);
        final LskfIdentifier id2 = new LskfIdentifier(10, 2000);
        final LockscreenCredential guess = newPassword("password");

        verifyUniqueWrongGuess(id1, guess);
        verifyDuplicateWrongGuess(id1, guess);
        verifyUniqueWrongGuess(id2, guess);
        verifyDuplicateWrongGuess(id2, guess);
    }

    @Test
    public void testSavedWrongGuessesAreForgottenOnSuccessReported() {
        final LskfIdentifier id1 = new LskfIdentifier(10, 1000);
        final LskfIdentifier id2 = new LskfIdentifier(20, 1000);
        final LockscreenCredential guess = newPassword("password");

        verifyUniqueWrongGuess(id1, guess);
        verifyDuplicateWrongGuess(id1, guess);

        verifyUniqueWrongGuess(id2, guess);
        verifyDuplicateWrongGuess(id2, guess);

        mRateLimiter.reportSuccess(id1);

        verifyDuplicateWrongGuess(id2, guess);
        verifyUniqueWrongGuess(id1, guess);
    }

    @Test
    public void testSavedWrongGuessesAreForgottenOnLskfStateCleared() {
        final LskfIdentifier id1 = new LskfIdentifier(10, 1000);
        final LskfIdentifier id2 = new LskfIdentifier(20, 1000);
        final LockscreenCredential guess = newPassword("password");

        verifyUniqueWrongGuess(id1, guess);
        verifyDuplicateWrongGuess(id1, guess);

        verifyUniqueWrongGuess(id2, guess);
        verifyDuplicateWrongGuess(id2, guess);

        mRateLimiter.clearLskfState(id1);

        verifyDuplicateWrongGuess(id2, guess);
        verifyUniqueWrongGuess(id1, guess);
    }

    @Test
    public void testSavedWrongGuessesAreForgottenOnUserStateCleared() {
        final LskfIdentifier id1 = new LskfIdentifier(10, 1000);
        final LskfIdentifier id2 = new LskfIdentifier(20, 1000);
        final LockscreenCredential guess = newPassword("password");

        verifyUniqueWrongGuess(id1, guess);
        verifyDuplicateWrongGuess(id1, guess);

        verifyUniqueWrongGuess(id2, guess);
        verifyDuplicateWrongGuess(id2, guess);

        mRateLimiter.clearUserState(id1.userId);

        verifyDuplicateWrongGuess(id2, guess);
        verifyUniqueWrongGuess(id1, guess);
    }

    @Test
    public void testSavedWrongGuessesAreForgottenOnTimeout() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential guess = newPassword("password");

        verifyUniqueWrongGuess(id, guess);

        assertThat(mInjector.getWorkList()).hasSize(1);
        WorkItem work = mInjector.getWorkList().get(0);
        assertThat(work.delayMillis)
                .isEqualTo(SoftwareRateLimiter.SAVED_WRONG_GUESS_TIMEOUT.toMillis());

        verifyDuplicateWrongGuess(id, guess);
        work.runnable.run();
        verifyUniqueWrongGuess(id, guess);
    }

    @Test
    public void testSuccessCancelsEvictionWork() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential guess = newPassword("password");

        verifyUniqueWrongGuess(id, guess);
        assertThat(mInjector.getWorkList()).hasSize(1);

        mRateLimiter.reportSuccess(id);
        assertThat(mInjector.getWorkList()).isEmpty();
    }

    @Test
    public void testRateLimiterKeepsOwnCopyOfGuess() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential guess = newPassword("password");
        final LockscreenCredential guessCopy = guess.duplicate();

        verifyUniqueWrongGuess(id, guess);
        verifyDuplicateWrongGuess(id, guess);

        // The SoftwareRateLimiter should have created a copy of guess, so calling zeroize() here
        // should not affect the SoftwareRateLimiter's state.
        guess.zeroize();

        verifyDuplicateWrongGuess(id, guessCopy);
    }

    @Test
    public void testPin() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential guess = newPin("1234");

        verifyUniqueWrongGuess(id, guess);
        verifyDuplicateWrongGuess(id, guess);

        mRateLimiter.reportSuccess(id);

        verifyUniqueWrongGuess(id, guess);
    }

    @Test
    public void testPattern() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential guess = newPattern("1234");

        verifyUniqueWrongGuess(id, guess);
        verifyDuplicateWrongGuess(id, guess);

        mRateLimiter.reportSuccess(id);

        verifyUniqueWrongGuess(id, guess);
    }

    @Test
    public void testDifferentCredentialTypesAreConsideredDifferent() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential pin = newPin("1234");
        final LockscreenCredential pattern = newPattern("1234");
        final LockscreenCredential password = newPassword("1234");

        verifyUniqueWrongGuess(id, pin);
        verifyUniqueWrongGuess(id, pattern);
        verifyUniqueWrongGuess(id, password);
    }

    // For special credentials (e.g. FRP credential), the failure counter is not currently being
    // stored persistently. But all the other logic should still work using the in-memory state.
    @Test
    public void testSpecialCredential() {
        LskfIdentifier id = new LskfIdentifier(-9999, SyntheticPasswordManager.NULL_PROTECTOR_ID);
        LockscreenCredential guess = newPassword("password");
        verifyFailureCounter(id, 0);
        verifyUniqueWrongGuess(id, guess);
        verifyFailureCounter(id, 0);
        verifyDuplicateWrongGuess(id, guess);
        verifyFailureCounter(id, 0);
        verifyCredentialTooShort(id, newPassword("abc"));
        verifyFailureCounter(id, 0);
        makeGuessesUntilRateLimited(id);
    }

    @Test
    public void testTooShortCredentialIsRejected() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);

        // All credential types currently have a minimum length of 4.
        verifyCredentialTooShort(id, newPassword("123"));
        verifyCredentialTooShort(id, newPin("123"));
        verifyCredentialTooShort(id, newPattern("123"));
        verifyUniqueWrongGuess(id, newPassword("1234"));
        verifyUniqueWrongGuess(id, newPin("1234"));
        verifyUniqueWrongGuess(id, newPattern("1234"));
    }

    @Test
    public void testNonEnforcingModeDoesNotRateLimitGuesses() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);

        mRateLimiter = new SoftwareRateLimiter(mInjector, /* enforcing= */ false);
        for (int i = 0; i < 100; i++) {
            final LockscreenCredential guess = newPassword("password" + i);
            verifyUniqueWrongGuess(id, guess, Duration.ZERO);
        }
    }

    @Test
    public void testNonEnforcingModeDoesNotDetectDuplicateWrongGuesses() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential guess = newPassword("password");

        mRateLimiter = new SoftwareRateLimiter(mInjector, /* enforcing= */ false);
        for (int i = 0; i < 100; i++) {
            verifyUniqueWrongGuess(id, guess, Duration.ZERO);
        }
    }

    // Tests that if a generic failure (isCertainlyWrongGuess=false) is reported to the
    // SoftwareRateLimiter, then the SoftwareRateLimiter does not match against that guess when
    // detecting duplicate wrong guesses.
    @Test
    public void testReportGenericFailure_doesNotSaveWrongGuess() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential guess = newPassword("password");

        for (int i = 0; i < 2; i++) {
            SoftwareRateLimiterResult result = mRateLimiter.apply(id, guess);
            assertThat(result.code).isEqualTo(CONTINUE_TO_HARDWARE);
            mRateLimiter.reportFailure(id, guess, /* isCertainlyWrongGuess= */ false);
        }
    }

    // Tests that if a generic failure (isCertainlyWrongGuess=false) is reported to the
    // SoftwareRateLimiter, then the failure counter is still incremented and the rate-limiter
    // eventually kicks in.
    @Test
    public void testReportGenericFailure_incrementsFailureCounter() {
        final LskfIdentifier id = new LskfIdentifier(10, 1000);
        final LockscreenCredential guess = newPassword("password");

        for (int i = 0; i < 100; i++) {
            SoftwareRateLimiterResult result = mRateLimiter.apply(id, guess);
            if (result.code == RATE_LIMITED) {
                return;
            }
            assertThat(result.code).isEqualTo(CONTINUE_TO_HARDWARE);
            mRateLimiter.reportFailure(id, guess, /* isCertainlyWrongGuess= */ false);
            verifyFailureCounter(id, i + 1);
        }
        fail("Rate-limiter never kicked in");
    }

    private void makeGuessesUntilRateLimited(LskfIdentifier id) {
        for (int i = 0; i < 100; i++) {
            final LockscreenCredential guess = newPassword("rate_limit_me" + i);
            SoftwareRateLimiterResult result = mRateLimiter.apply(id, guess);
            if (result.code != CONTINUE_TO_HARDWARE) {
                assertThat(result.code).isEqualTo(RATE_LIMITED);
                return;
            }
            mRateLimiter.reportFailure(id, guess, /* isCertainlyWrongGuess= */ true);
        }
        fail("Rate-limiter never kicked in");
    }

    private LockscreenCredential newPin(String pin) {
        return LockscreenCredential.createPinOrNone(pin);
    }

    private LockscreenCredential newPattern(String pattern) {
        return LockscreenCredential.createPattern(
                LockPatternUtils.byteArrayToPattern(pattern.getBytes()));
    }

    private LockscreenCredential newPassword(String password) {
        return LockscreenCredential.createPasswordOrNone(password);
    }

    private void verifyCredentialTooShort(LskfIdentifier id, LockscreenCredential guess) {
        SoftwareRateLimiterResult result = mRateLimiter.apply(id, guess);
        assertThat(result.code).isEqualTo(CREDENTIAL_TOO_SHORT);
    }

    private void verifyNoMoreGuesses(LskfIdentifier id, LockscreenCredential guess) {
        SoftwareRateLimiterResult result = mRateLimiter.apply(id, guess);
        assertThat(result.code).isEqualTo(NO_MORE_GUESSES);
    }

    private void verifyRateLimited(LskfIdentifier id, LockscreenCredential guess) {
        SoftwareRateLimiterResult result = mRateLimiter.apply(id, guess);
        assertThat(result.code).isEqualTo(RATE_LIMITED);
    }

    private void verifyRateLimited(
            LskfIdentifier id, LockscreenCredential guess, Duration expectedRemainingDelay) {
        SoftwareRateLimiterResult result = mRateLimiter.apply(id, guess);
        assertThat(result.code).isEqualTo(RATE_LIMITED);
        assertThat(result.remainingDelay).isEqualTo(expectedRemainingDelay);
    }

    // Verifies that the rate-limiter returns a status of DUPLICATE_WRONG_GUESS for the given guess.
    private void verifyDuplicateWrongGuess(LskfIdentifier id, LockscreenCredential guess) {
        SoftwareRateLimiterResult result = mRateLimiter.apply(id, guess);
        assertThat(result.code).isEqualTo(DUPLICATE_WRONG_GUESS);
    }

    // Verifies that the rate-limiter returns a status of CONTINUE_TO_HARDWARE for the given guess.
    // Then reports a wrong guess to the rate-limiter.
    private void verifyUniqueWrongGuess(LskfIdentifier id, LockscreenCredential guess) {
        SoftwareRateLimiterResult result = mRateLimiter.apply(id, guess);
        assertThat(result.code).isEqualTo(CONTINUE_TO_HARDWARE);
        mRateLimiter.reportFailure(id, guess, /* isCertainlyWrongGuess= */ true);
    }

    // Same as above but also verifies the next delay reported by reportFailure().
    private void verifyUniqueWrongGuess(
            LskfIdentifier id, LockscreenCredential guess, Duration expectedNextDelay) {
        SoftwareRateLimiterResult result = mRateLimiter.apply(id, guess);
        assertThat(result.code).isEqualTo(CONTINUE_TO_HARDWARE);
        Duration nextDelay =
                mRateLimiter.reportFailure(id, guess, /* isCertainlyWrongGuess= */ true);
        assertThat(nextDelay).isEqualTo(expectedNextDelay);
    }

    private void verifyFailureCounter(LskfIdentifier id, int expectedValue) {
        assertThat(mInjector.readFailureCounter(id)).isEqualTo(expectedValue);
    }

    private static class TestInjector implements SoftwareRateLimiter.Injector {

        private final Map<LskfIdentifier, Integer> mFailureCounters = new HashMap<>();
        private final List<WorkItem> mWorkList = new ArrayList<>();
        private Duration mTimeSinceBoot = Duration.ZERO;

        List<WorkItem> getWorkList() {
            return mWorkList;
        }

        void advanceTime(Duration duration) {
            mTimeSinceBoot = mTimeSinceBoot.plus(duration);
        }

        void setTime(Duration timeSinceBoot) {
            mTimeSinceBoot = timeSinceBoot;
        }

        @Override
        public int readFailureCounter(LskfIdentifier id) {
            return mFailureCounters.getOrDefault(id, 0);
        }

        @Override
        public void writeFailureCounter(LskfIdentifier id, int counter) {
            mFailureCounters.put(id, counter);
        }

        @Override
        public Duration getTimeSinceBoot() {
            return mTimeSinceBoot;
        }

        @Override
        public void removeCallbacksAndMessages(Object token) {
            mWorkList.removeIf(work -> work.token == token);
        }

        @Override
        public void postDelayed(Runnable runnable, Object token, long delayMillis) {
            mWorkList.add(new WorkItem(runnable, token, delayMillis));
        }

        @Override
        public int getHardwareRateLimiter(LskfIdentifier id) {
            return FrameworkStatsLog.LSKF_AUTHENTICATION_ATTEMPTED__HARDWARE_RATE_LIMITER__WEAVER;
        }
    }

    private static class WorkItem {
        public final Runnable runnable;
        public final Object token;
        public final long delayMillis;

        WorkItem(Runnable runnable, Object token, long delayMillis) {
            this.runnable = runnable;
            this.token = token;
            this.delayMillis = delayMillis;
        }
    }
}
