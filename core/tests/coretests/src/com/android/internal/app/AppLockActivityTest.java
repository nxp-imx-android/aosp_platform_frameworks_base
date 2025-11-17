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

package com.android.internal.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ApplicationInfoFlags;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.Flags;
import android.widget.Toast;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.android.internal.R;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link AppLockActivity}.
 *
 * <p>We can't test {@link AppLockActivity} directly since it launches in a separate process and
 * {@link ActivityScenario} only supports testing in the main process, so we're using
 * {@link TestAppLockActivity} that extends {@link AppLockActivity} for testing.
 */
@RunWith(TestParameterInjector.class)
public class AppLockActivityTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final String SYSTEM_PACKAGE_NAME = "android";
    private static final String TEST_PACKAGE_NAME = "com.android.test";
    private static final String TEST_APP_LABEL = "Test App";
    private static final int EXPECTED_NEGATIVE_BUTTON_RES = android.R.string.cancel;
    private static final int EXPECTED_RESULT_TOAST_DURATION = Toast.LENGTH_SHORT;
    private static final int INVALID_TOAST_DURATION = -1;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private ApplicationInfo mApplicationInfo;
    @Mock
    private PackageManager mPackageManager;

    private AutoCloseable mMockCloseable;

    @Before
    public void setUp() throws Exception {
        mMockCloseable = MockitoAnnotations.openMocks(this);

        mApplicationInfo = spy(new ApplicationInfo());

        when(mPackageManager.getApplicationInfo(eq(TEST_PACKAGE_NAME),
                argThat(flags -> flags.getValue() == PackageManager.GET_APP_LOCK_INFO)))
                .thenReturn(mApplicationInfo);
        when(mApplicationInfo.loadLabel(mPackageManager)).thenReturn(TEST_APP_LABEL);

        TestAppLockActivity.sPackageManager = mPackageManager;
        TestAppLockActivity.sShownToastText = null;
        TestAppLockActivity.sShownToastDuration = INVALID_TOAST_DURATION;
    }

    @After
    public void tearDown() throws Exception {
        mMockCloseable.close();
    }

    @Test
    public void createIntent_withNullPackageName_throws(@TestParameter boolean newAppLockEnabled) {
        assertThrows(NullPointerException.class,
                () -> AppLockActivity.createAppLockActivityIntent(null, newAppLockEnabled));
    }

    @Test
    public void createIntent_withAppLockState_returnsCorrectlyConfiguredAppLockActivityIntent(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = AppLockActivity.createAppLockActivityIntent(TEST_PACKAGE_NAME,
                newAppLockEnabled);
        ComponentName expectedComponentName = new ComponentName(SYSTEM_PACKAGE_NAME,
                AppLockActivity.class.getName());

        assertThat(intent).isNotNull();
        assertThat(intent.getComponent()).isEqualTo(expectedComponentName);
        assertThat(intent.getAction()).isEqualTo(PackageManager.ACTION_SET_APP_LOCK);
        assertThat(intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(intent.hasExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE)).isTrue();
        assertThat(intent.getBooleanExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE, false))
                .isEqualTo(newAppLockEnabled);
        assertThat(intent.getFlags()).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withoutIntentExtraPackageName_finishes() {
        Intent intent = createTestAppLockActivityIntentWithoutExtras();
        intent.putExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE, true);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withoutIntentExtraAppLockNewState_finishes() {
        Intent intent = createTestAppLockActivityIntentWithoutExtras();
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_PACKAGE_NAME);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_whenPackageManagerThrowsNameNotFound_finishes(
            @TestParameter boolean newAppLockEnabled) throws PackageManager.NameNotFoundException {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        doThrow(new PackageManager.NameNotFoundException()).when(mPackageManager)
                .getApplicationInfo(anyString(), any(ApplicationInfoFlags.class));

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_whenAppLockIsNotSupported_finishes(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = false;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_whenAppLockStateIsUnchanged_finishes(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = newAppLockEnabled;

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_whenPackageLabelIsPackageName_showsDialogWithPackageName(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;
        when(mApplicationInfo.loadLabel(mPackageManager)).thenReturn(TEST_PACKAGE_NAME);
        final String expectedDialogTitle = getExpectedDialogTitle(newAppLockEnabled,
                TEST_PACKAGE_NAME);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withText(expectedDialogTitle)).check(matches(isDisplayed()));
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withValidIntent_showsCorrectDialogContent(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;
        final String expectedDialogTitle = getExpectedDialogTitle(newAppLockEnabled,
                TEST_APP_LABEL);
        final int expectedPositiveButtonRes = getExpectedPositiveButtonRes(newAppLockEnabled);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withText(expectedDialogTitle)).check(matches(isDisplayed()));
            onView(withText(expectedPositiveButtonRes)).check(matches(isDisplayed()));
            onView(withText(EXPECTED_NEGATIVE_BUTTON_RES)).check(matches(isDisplayed()));
        }
    }

    @DisableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withValidIntent_appLockFlagsDisabled_finishes(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void clickPositiveButtonOnDialog_setAppLockSuccess_showsSuccessToastAndFinishes(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;
        when(mPackageManager.setPackageAppLockEnabled(TEST_PACKAGE_NAME, newAppLockEnabled))
                .thenReturn(true);
        final int expectedPositiveButtonRes = getExpectedPositiveButtonRes(newAppLockEnabled);
        final int expectedSuccessToastMessageRes = newAppLockEnabled
                ? R.string.enable_app_lock_success_toast_message
                : R.string.disable_app_lock_success_toast_message;
        final String expectedSuccessToastText = mContext.getString(
                expectedSuccessToastMessageRes, TEST_APP_LABEL);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withText(expectedPositiveButtonRes)).perform(click());

            verify(mPackageManager).setPackageAppLockEnabled(TEST_PACKAGE_NAME, newAppLockEnabled);
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
            assertThat(TestAppLockActivity.sShownToastText.toString()).isEqualTo(
                    expectedSuccessToastText);
            assertThat(TestAppLockActivity.sShownToastDuration).isEqualTo(
                    EXPECTED_RESULT_TOAST_DURATION);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void clickPositiveButtonOnDialog_enableAppLockFails_showsFailureToastAndFinishes() {
        final boolean newAppLockEnabled = true;
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;
        when(mPackageManager.setPackageAppLockEnabled(TEST_PACKAGE_NAME, newAppLockEnabled))
                .thenReturn(false);
        final int expectedPositiveButtonRes = getExpectedPositiveButtonRes(newAppLockEnabled);
        final int expectedFailureToastMessageRes =
                R.string.enable_app_lock_failure_toast_message;
        final String expectedFailureToastText = mContext.getString(
                expectedFailureToastMessageRes, TEST_APP_LABEL);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withText(expectedPositiveButtonRes)).perform(click());

            verify(mPackageManager).setPackageAppLockEnabled(TEST_PACKAGE_NAME, newAppLockEnabled);
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
            assertThat(TestAppLockActivity.sShownToastText.toString()).isEqualTo(
                    expectedFailureToastText);
            assertThat(TestAppLockActivity.sShownToastDuration).isEqualTo(
                    EXPECTED_RESULT_TOAST_DURATION);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void clickPositiveButtonOnDialog_disableAppLockFails_doesNotShowToastAndFinishes() {
        final boolean newAppLockEnabled = false;
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;
        when(mPackageManager.setPackageAppLockEnabled(TEST_PACKAGE_NAME, newAppLockEnabled))
                .thenReturn(false);
        final int expectedPositiveButtonRes = getExpectedPositiveButtonRes(newAppLockEnabled);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withText(expectedPositiveButtonRes)).perform(click());

            verify(mPackageManager).setPackageAppLockEnabled(TEST_PACKAGE_NAME, newAppLockEnabled);
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
            assertThat(TestAppLockActivity.sShownToastText).isNull();
            assertThat(TestAppLockActivity.sShownToastDuration).isEqualTo(INVALID_TOAST_DURATION);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void clickNegativeButtonOnDialog_doesNotSetAppLockAndFinishes(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withText(EXPECTED_NEGATIVE_BUTTON_RES)).perform(click());

            verify(mPackageManager, never()).setPackageAppLockEnabled(anyString(), anyBoolean());
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    private Intent createTestAppLockActivityIntentWithoutExtras() {
        return new Intent(mContext, TestAppLockActivity.class).setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    private Intent createTestAppLockActivityIntent(boolean newAppLockEnabled) {
        return createTestAppLockActivityIntentWithoutExtras()
                .putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE, newAppLockEnabled);
    }

    private String getExpectedDialogTitle(boolean newAppLockEnabled, String packageLabel) {
        final int titleRes = newAppLockEnabled ? R.string.enable_app_lock_dialog_title
                : R.string.disable_app_lock_dialog_title;
        return mContext.getString(titleRes, packageLabel);
    }

    private int getExpectedPositiveButtonRes(boolean newAppLockEnabled) {
        return newAppLockEnabled ? R.string.enable_app_lock_dialog_enable_button_text
                : R.string.disable_app_lock_dialog_disable_button_text;
    }

    /**
     * A test-specific subclass of {@link AppLockActivity}.
     *
     * <p><b>Visibility:</b> This class must be declared {@code public} so that the Android testing
     * framework can instantiate it.
     */
    public static class TestAppLockActivity extends AppLockActivity {
        // These variables are defined in the setUp() method.
        static PackageManager sPackageManager;
        static CharSequence sShownToastText;
        static int sShownToastDuration;

        @Override
        public PackageManager getPackageManager() {
            return sPackageManager;
        }

        @Override
        protected void showToast(CharSequence toastText, int duration) {
            sShownToastText = toastText;
            sShownToastDuration = duration;
        }
    }
}
