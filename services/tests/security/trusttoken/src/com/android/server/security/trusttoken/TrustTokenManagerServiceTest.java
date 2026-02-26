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

package com.android.server.security.trusttoken;

import static android.security.trusttoken.TrustTokenManager.VERIFICATION_FAILURE_CHALLENGE_INCORRECT;
import static android.security.trusttoken.TrustTokenManager.VERIFICATION_FAILURE_SIGNATURE_INVALID;
import static android.security.trusttoken.TrustTokenManager.VERIFICATION_SUCCESS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.StatsManager;
import android.security.trusttoken.ITrustTokenManager;
import android.security.trusttoken.TrustAnchorUnavailableException;
import android.security.trusttoken.TrustConfiguration;
import android.security.trusttoken.TrustToken;
import android.security.trusttoken.TrustTokenManager;
import android.security.trusttoken.TrustTokenUnavailableException;
import android.testing.TestableContext;
import android.util.Base64;
import android.util.StatsEvent;
import android.util.StatsEventTestUtils;
import android.util.StatsLog;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.R;
import com.android.internal.os.Clock;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.os.AtomsProto.Atom;
import com.android.os.framework.AcquireTrustTokenCalled;
import com.android.os.framework.TrustTokenExtensionAtomsProto;
import com.android.os.framework.TrustTokenState;
import com.android.os.framework.TrustTokenType;
import com.android.os.framework.VerifyTrustTokenCalled;

import com.google.protobuf.ExtensionLite;
import com.google.protobuf.ExtensionRegistryLite;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class TrustTokenManagerServiceTest {
    static final byte[] ROOT_KEY_1 = testdata("root_1_public");
    static final byte[] ROOT_KEY_2 = testdata("root_2_public");
    static final TrustConfiguration TRUST_CONFIGURATION_1 =
            new TrustConfiguration.Builder()
                    .addRootKey(ROOT_KEY_1)
                    .addIntermediateCertificate(testdata("root_1_intermediate_1"))
                    .setUpdatedAt(Instant.now())
                    .build();
    static final TrustConfiguration TRUST_CONFIGURATION_2 =
            new TrustConfiguration.Builder()
                    .addRootKey(ROOT_KEY_2)
                    .addIntermediateCertificate(testdata("root_2_intermediate_1"))
                    .setUpdatedAt(Instant.now())
                    .build();
    static final TrustTokenKey TRUST_TOKEN_KEY_1 =
            new TrustTokenKey(
                    testdata("root_1_intermediate_1_token_1_key_public"), "not-needed".getBytes());
    static final byte[] TRUST_TOKEN_1 = testdata("root_1_intermediate_1_token_1");
    static final TrustTokenKey TRUST_TOKEN_KEY_2 =
            new TrustTokenKey(
                    testdata("root_1_intermediate_1_token_2_key_public"), "not-needed".getBytes());
    static final byte[] TRUST_TOKEN_2 = testdata("root_1_intermediate_1_token_2");
    static final byte[] CHALLENGE = testdata("challenge_1");
    static final byte[] CHALLENGE_RESPONSE =
            testdata("root_1_intermediate_1_token_1_challenge_1_response");
    private static final String DATABASE_NAME = "trust_token_test";
    private static final String MASTER_KEY_PREFIX = "trust_token_test_";
    static final long SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS =
            TrustTokenManagerService.SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS;

    @Rule
    public TestableContext mContext =
            new TestableContext(InstrumentationRegistry.getContext(), null);

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(StatsLog.class).build();

    @Captor ArgumentCaptor<StatsEvent> mStatsEventCaptor;

    ExtensionRegistryLite mLogRegistry = ExtensionRegistryLite.newInstance();

    TrustTokenManager mManager;
    TrustTokenSqliteDatabase mDatabase;
    TrustTokenMasterKey mMasterKey;
    TrustTokenManagerService mService;
    TrustTokenManagerInternal mInternal;

    Clock mClock = spy(Clock.SYSTEM_CLOCK);

    static byte[] testdata(String path) {
        try {
            return Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(path)
                    .readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    @Before
    public void setUp() {
        mLogRegistry.add(TrustTokenExtensionAtomsProto.acquireTrustTokenCalled);
        mLogRegistry.add(TrustTokenExtensionAtomsProto.verifyTrustTokenCalled);
        mLogRegistry.add(TrustTokenExtensionAtomsProto.trustTokenState);
        mDatabase =
                TrustTokenSqliteDatabase.create(
                        mContext, mContext.getDatabasePath(DATABASE_NAME), mClock);
        mMasterKey = spy(TrustTokenMasterKey.generateMasterKey(MASTER_KEY_PREFIX));
        doReturn(CHALLENGE_RESPONSE)
                .when(mMasterKey)
                .sign(any(TrustTokenKey.class), aryEq(CHALLENGE));
        mService = new TrustTokenManagerService(mContext, mMasterKey, mDatabase, mClock);
        mManager = new TrustTokenManager(ITrustTokenManager.Stub.asInterface(mService.getBinder()));
        mInternal = mService.getInternal();
    }

    @After
    public void tearDown() {
        mDatabase.close();
        mContext.deleteDatabase(DATABASE_NAME);
        TrustTokenMasterKey.removeFromKeyStore(MASTER_KEY_PREFIX);
    }

    <T> T getLogEvent(ExtensionLite<Atom, T> extension) throws Exception {
        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), atLeast(1));
        for (StatsEvent event : mStatsEventCaptor.getAllValues()) {
            Atom atom =
                    StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue(), mLogRegistry);
            assertThat(atom).isNotNull();
            if (atom.hasExtension(extension)) {
                return atom.getExtension(extension);
            }
        }
        throw new AssertionError(extension.toString() + " not found in the log events");
    }

    TrustTokenState pullState() throws Exception {
        var events = new ArrayList<StatsEvent>();
        assertThat(
                        mService.new PullTrustTokenState()
                                .onPullAtom(MetricsLogger.TrustTokenState.ATOM_TAG, events))
                .isEqualTo(StatsManager.PULL_SUCCESS);
        assertThat(events).hasSize(1);
        Atom atom = StatsEventTestUtils.convertToAtom(events.get(0), mLogRegistry);
        assertThat(atom).isNotNull();
        TrustTokenState state = atom.getExtension(TrustTokenExtensionAtomsProto.trustTokenState);
        assertThat(state).isNotNull();
        return state;
    }

    @Test
    public void acquireVerifiedDeviceToken_success() throws Exception {
        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_1);
        assertThrows(
                TrustTokenUnavailableException.class,
                () -> mManager.acquireVerifiedDeviceToken(CHALLENGE));
        AcquireTrustTokenCalled log =
                getLogEvent(TrustTokenExtensionAtomsProto.acquireTrustTokenCalled);
        assertThat(log.getTokenType()).isEqualTo(TrustTokenType.TRUST_TOKEN_TYPE_DEVICE);
        assertThat(log.getOutcome())
                .isEqualTo(AcquireTrustTokenCalled.Outcome.OUTCOME_TOKEN_EXHAUSTED);

        mInternal.addTrustTokens(List.of(TRUST_TOKEN_KEY_1), List.of(TRUST_TOKEN_1));
        var token = mManager.acquireVerifiedDeviceToken(CHALLENGE);
        log = getLogEvent(TrustTokenExtensionAtomsProto.acquireTrustTokenCalled);
        assertThat(log.getTokenType()).isEqualTo(TrustTokenType.TRUST_TOKEN_TYPE_DEVICE);
        assertThat(log.getOutcome()).isEqualTo(AcquireTrustTokenCalled.Outcome.OUTCOME_SUCCESS);

        assertThrows(
                TrustTokenUnavailableException.class,
                () -> mManager.acquireVerifiedDeviceToken(CHALLENGE));
    }

    @Test
    public void verifyTrustTokenAndChallenge_success() throws Exception {
        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_1);
        assertThat(
                        mManager.verifyTrustToken(
                                new TrustToken(TRUST_TOKEN_1), CHALLENGE_RESPONSE, CHALLENGE))
                .isEqualTo(VERIFICATION_SUCCESS);
        VerifyTrustTokenCalled log =
                getLogEvent(TrustTokenExtensionAtomsProto.verifyTrustTokenCalled);
        assertThat(log.getOutcome()).isEqualTo(VerifyTrustTokenCalled.Outcome.OUTCOME_SUCCESS);
        assertThat(log.getPolicy())
                .isEqualTo(VerifyTrustTokenCalled.Policy.POLICY_VERIFIED_DEVICE_STRONG);
    }

    @Test
    public void verifyTrustTokenAndChallenge_errors() throws Exception {
        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_1);
        assertThat(
                        mManager.verifyTrustToken(
                                new TrustToken(TRUST_TOKEN_1),
                                CHALLENGE_RESPONSE,
                                "some-other-challenge".getBytes()))
                .isEqualTo(VERIFICATION_FAILURE_CHALLENGE_INCORRECT);
        VerifyTrustTokenCalled log =
                getLogEvent(TrustTokenExtensionAtomsProto.verifyTrustTokenCalled);
        assertThat(log.getOutcome())
                .isEqualTo(VerifyTrustTokenCalled.Outcome.OUTCOME_CHALLENGE_INCORRECT);

        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_2);
        assertThat(
                        mManager.verifyTrustToken(
                                new TrustToken(TRUST_TOKEN_1), CHALLENGE_RESPONSE, CHALLENGE))
                .isEqualTo(VERIFICATION_FAILURE_SIGNATURE_INVALID);
        log = getLogEvent(TrustTokenExtensionAtomsProto.verifyTrustTokenCalled);
        assertThat(log.getOutcome())
                .isEqualTo(VerifyTrustTokenCalled.Outcome.OUTCOME_SIGNATURE_INVALID);
    }

    @Test
    public void verifyTrustTokenAndChallenge_trustConfigurationUnavailable() throws Exception {
        assertThrows(
                TrustAnchorUnavailableException.class,
                () ->
                        mManager.verifyTrustToken(
                                new TrustToken(TRUST_TOKEN_1), "".getBytes(), "".getBytes()));
        VerifyTrustTokenCalled log =
                getLogEvent(TrustTokenExtensionAtomsProto.verifyTrustTokenCalled);
        assertThat(log.getOutcome())
                .isEqualTo(VerifyTrustTokenCalled.Outcome.OUTCOME_ANCHOR_UNAVAILABLE);
    }

    @Test
    public void updateTrustConfiguration_success() {
        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_1);
        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_2);
        // This token is invalid under the new trust configuration.
        assertThrows(
                IllegalArgumentException.class,
                () -> mInternal.addTrustTokens(List.of(TRUST_TOKEN_KEY_1), List.of(TRUST_TOKEN_1)));
    }

    @Test
    public void updateTrustConfiguration_useDeviceKeyWhenBothTimestampAreRecent() throws Exception {
        when(mClock.currentTimeMillis()).thenReturn(200000L);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.vendor_required_trust_token_roots_timestamp, 1000);
        mContext.getOrCreateTestableResources()
                .addOverride(
                        R.array.vendor_required_trust_token_roots,
                        new String[] {Base64.encodeToString(ROOT_KEY_1, Base64.DEFAULT)});
        mInternal.updateTrustConfiguration(
                new TrustConfiguration.Builder()
                        .addRootKey(ROOT_KEY_1)
                        .addRootKey(ROOT_KEY_2)
                        .addIntermediateCertificate(testdata("root_1_intermediate_1"))
                        .setUpdatedAt(Instant.ofEpochMilli(300000))
                        .build());
        // No exception is good.
        mInternal.addTrustTokens(List.of(TRUST_TOKEN_KEY_1), List.of(TRUST_TOKEN_1));
        TrustTokenState state = pullState();
        assertFalse(state.getAuthorityFallback());
    }

    @Test
    public void updateTrustConfiguration_useDeviceKeyWhenOnlyDeviceClockIsRecent()
            throws Exception {
        when(mClock.currentTimeMillis()).thenReturn(200000L);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.vendor_required_trust_token_roots_timestamp, 1000);
        mContext.getOrCreateTestableResources()
                .addOverride(
                        R.array.vendor_required_trust_token_roots,
                        new String[] {Base64.encodeToString(ROOT_KEY_1, Base64.DEFAULT)});
        mInternal.updateTrustConfiguration(
                new TrustConfiguration.Builder()
                        .addRootKey(ROOT_KEY_1)
                        .addRootKey(ROOT_KEY_2)
                        .addIntermediateCertificate(testdata("root_1_intermediate_1"))
                        .setUpdatedAt(
                                Instant.ofEpochMilli(300000 + SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS))
                        .build());
        // No exception is good.
        mInternal.addTrustTokens(List.of(TRUST_TOKEN_KEY_1), List.of(TRUST_TOKEN_1));
        TrustTokenState state = pullState();
        assertFalse(state.getAuthorityFallback());
    }

    @Test
    public void updateTrustConfiguration_useDeviceKeyWhenServerTimeIsRecent() throws Exception {
        when(mClock.currentTimeMillis()).thenReturn(200000L + SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.vendor_required_trust_token_roots_timestamp, 1000);
        mContext.getOrCreateTestableResources()
                .addOverride(
                        R.array.vendor_required_trust_token_roots,
                        new String[] {Base64.encodeToString(ROOT_KEY_1, Base64.DEFAULT)});
        mInternal.updateTrustConfiguration(
                new TrustConfiguration.Builder()
                        .addRootKey(ROOT_KEY_1)
                        .addRootKey(ROOT_KEY_2)
                        .addIntermediateCertificate(testdata("root_1_intermediate_1"))
                        .setUpdatedAt(Instant.ofEpochMilli(300000))
                        .build());
        // No exception is good.
        mInternal.addTrustTokens(List.of(TRUST_TOKEN_KEY_1), List.of(TRUST_TOKEN_1));
        TrustTokenState state = pullState();
        assertFalse(state.getAuthorityFallback());
    }

    @Test
    public void updateTrustConfiguration_doNotUseDeviceKey() throws Exception {
        when(mClock.currentTimeMillis()).thenReturn(200000L + SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.vendor_required_trust_token_roots_timestamp, 100);
        mContext.getOrCreateTestableResources()
                .addOverride(
                        R.array.vendor_required_trust_token_roots,
                        new String[] {Base64.encodeToString(ROOT_KEY_2, Base64.DEFAULT)});
        mInternal.updateTrustConfiguration(
                new TrustConfiguration.Builder()
                        .addRootKey(ROOT_KEY_1)
                        .addIntermediateCertificate(testdata("root_1_intermediate_1"))
                        .setUpdatedAt(
                                Instant.ofEpochMilli(300000 + SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS))
                        .build());
        // No exception is good.
        mInternal.addTrustTokens(List.of(TRUST_TOKEN_KEY_1), List.of(TRUST_TOKEN_1));
        TrustTokenState state = pullState();
        assertTrue(state.getAuthorityFallback());
    }

    @Test
    public void addTrustTokens_keysAndTokensInOrder() {
        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_1);
        mInternal.addTrustTokens(
                List.of(TRUST_TOKEN_KEY_1, TRUST_TOKEN_KEY_2),
                List.of(TRUST_TOKEN_1, TRUST_TOKEN_2));
        var tokens =
                List.of(
                        mManager.acquireVerifiedDeviceToken(CHALLENGE).getToken(),
                        mManager.acquireVerifiedDeviceToken(CHALLENGE).getToken());
        assertThat(tokens)
                .containsExactly(new TrustToken(TRUST_TOKEN_1), new TrustToken(TRUST_TOKEN_2));
    }

    @Test
    public void addTrustTokens_keysAndTokensOutOfOrder() {
        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_1);
        mInternal.addTrustTokens(
                List.of(TRUST_TOKEN_KEY_1, TRUST_TOKEN_KEY_2),
                List.of(TRUST_TOKEN_2, TRUST_TOKEN_1));
        var tokens =
                List.of(
                        mManager.acquireVerifiedDeviceToken(CHALLENGE).getToken(),
                        mManager.acquireVerifiedDeviceToken(CHALLENGE).getToken());
        assertThat(tokens)
                .containsExactly(new TrustToken(TRUST_TOKEN_1), new TrustToken(TRUST_TOKEN_2));
    }

    @Test
    public void addTrustTokens_missingToken() {
        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mInternal.addTrustTokens(
                                List.of(TRUST_TOKEN_KEY_1, TRUST_TOKEN_KEY_2),
                                List.of(TRUST_TOKEN_1)));
    }

    @Test
    public void addTrustTokens_duplicateToken() {
        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mInternal.addTrustTokens(
                                List.of(TRUST_TOKEN_KEY_1, TRUST_TOKEN_KEY_2),
                                List.of(TRUST_TOKEN_1, TRUST_TOKEN_1)));
    }

    @Test
    public void addTrustTokens_duplicateKey() {
        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mInternal.addTrustTokens(
                                List.of(TRUST_TOKEN_KEY_1, TRUST_TOKEN_KEY_1),
                                List.of(TRUST_TOKEN_1, TRUST_TOKEN_2)));
    }

    @Test
    public void generateKeys_numMatches() {
        assertThat(mInternal.generateKeys(2)).hasSize(2);
        assertThat(mInternal.generateKeys(3)).hasSize(3);
    }

    @Test
    public void attestKeys_numMatches() {
        TrustTokenBatchAttestation attestation = mInternal.attestKeys(mInternal.generateKeys(2));
        assertThat(attestation.getBatchHash().length).isGreaterThan(0);
        assertThat(attestation.getSignature().length).isGreaterThan(0);
        // There should at least an attestation root, and the batch attestation key.
        assertThat(attestation.getCertificates().size()).isAtLeast(2);
    }

    @Test
    public void statsPullCallback() throws Exception {
        TrustTokenState state = pullState();
        assertFalse(state.getHasTrustAnchor());

        mInternal.updateTrustConfiguration(TRUST_CONFIGURATION_1);
        state = pullState();
        assertTrue(state.getHasTrustAnchor());
        assertThat(state.getRootKeyCount()).isEqualTo(1);

        mInternal.addTrustTokens(
                List.of(TRUST_TOKEN_KEY_1, TRUST_TOKEN_KEY_2),
                List.of(TRUST_TOKEN_1, TRUST_TOKEN_2));
        state = pullState();
        assertThat(state.getDeviceTokenCount()).isEqualTo(2);
    }
}
