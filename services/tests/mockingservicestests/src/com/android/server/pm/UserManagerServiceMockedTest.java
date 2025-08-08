/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.pm;

import static android.app.admin.flags.Flags.FLAG_APP_RESTRICTIONS_COEXISTENCE;
import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.content.pm.PackageManager.FEATURE_EMBEDDED;
import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static android.content.pm.UserInfo.FLAG_ADMIN;
import static android.content.pm.UserInfo.FLAG_FULL;
import static android.content.pm.UserInfo.flagsToString;
import static android.multiuser.Flags.FLAG_BLOCK_PRIVATE_SPACE_CREATION;
import static android.multiuser.Flags.FLAG_DEMOTE_MAIN_USER;
import static android.multiuser.Flags.FLAG_DISALLOW_REMOVING_LAST_ADMIN_USER;
import static android.multiuser.Flags.FLAG_ENABLE_PRIVATE_SPACE_FEATURES;
import static android.multiuser.Flags.FLAG_LOGOUT_USER_API;
import static android.multiuser.Flags.FLAG_SUPPORT_AUTOLOCK_FOR_PRIVATE_SPACE;
import static android.multiuser.Flags.FLAG_UNICORN_MODE_REFACTORING_FOR_HSUM_READ_ONLY;
import static android.os.Flags.FLAG_ALLOW_PRIVATE_PROFILE;
import static android.os.UserHandle.USER_SYSTEM;
import static android.os.UserManager.DISALLOW_OUTGOING_CALLS;
import static android.os.UserManager.DISALLOW_SMS;
import static android.os.UserManager.DISALLOW_USER_SWITCH;
import static android.os.UserManager.USER_TYPE_FULL_RESTRICTED;
import static android.os.UserManager.USER_TYPE_FULL_SECONDARY;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;
import static android.os.UserManager.USER_TYPE_PROFILE_PRIVATE;
import static android.os.UserManager.USER_TYPE_PROFILE_SUPERVISING;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.pm.UserJourneyLogger.USER_JOURNEY_DEMOTE_MAIN_USER;
import static com.android.server.pm.UserJourneyLogger.USER_JOURNEY_PROMOTE_MAIN_USER;
import static com.android.server.pm.UserManagerService.BOOT_TO_HSU_FOR_PROVISIONED_DEVICE;
import static com.android.server.pm.UserManagerService.BOOT_TO_PREVIOUS_OR_FIRST_SWITCHABLE_USER;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.app.PropertyInvalidatedCache;
import android.app.admin.DevicePolicyManagerInternal;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.ServiceSpecificException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.Xml;

import androidx.test.annotation.UiThreadTest;

import com.android.dx.mockito.inline.extended.MockedVoidMethod;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.LocalServices;
import com.android.server.StorageManagerInternal;
import com.android.server.am.UserState;
import com.android.server.locksettings.LockSettingsInternal;
import com.android.server.pm.UserJourneyLogger.UserJourney;
import com.android.server.pm.UserManagerService.BootStrategy;
import com.android.server.pm.UserManagerService.UserData;
import com.android.server.storage.DeviceStorageMonitorInternal;

import com.google.common.primitives.Ints;
import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Run as {@code atest
 * FrameworksMockingServicesTests:com.android.server.pm.UserManagerServiceMockedTest}
 */
public final class UserManagerServiceMockedTest {

    private static final String TAG = UserManagerServiceMockedTest.class.getSimpleName();

    /**
     * Id for a simple user (that doesn't have profiles).
     */
    private static final int USER_ID = 600;

    /**
     * Id for another simple user.
     */
    private static final int OTHER_USER_ID = 666;

    /**
     * Id for a third simple user.
     */
    private static final int THIRD_USER_ID = 667;

    /**
     * Id for a user that has one profile (whose id is {@link #PROFILE_USER_ID}.
     *
     * <p>You can use {@link #addDefaultProfileAndParent()} to add both of this user to the service.
     */
    private static final int PARENT_USER_ID = 642;

    /**
     * Id for a profile whose parent is {@link #PARENTUSER_ID}.
     *
     * <p>You can use {@link #addDefaultProfileAndParent()} to add both of this user to the service.
     */
    private static final int PROFILE_USER_ID = 643;

    private static final String USER_INFO_DIR = "system" + File.separator + "users";

    private static final String XML_SUFFIX = ".xml";

    private static final String TAG_RESTRICTIONS = "restrictions";

    private static final String PRIVATE_PROFILE_NAME = "TestPrivateProfile";

    @Rule public final Expect expect = Expect.create();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .spyStatic(UserManager.class)
            .spyStatic(LocalServices.class)
            .spyStatic(SystemProperties.class)
            .spyStatic(ActivityManager.class)
            .spyStatic(UserHandle.class)
            .mockStatic(Settings.Global.class)
            .mockStatic(Settings.Secure.class)
            .mockStatic(Resources.class)
            .build();

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(
            SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);

    private final Object mPackagesLock = new Object();
    private final Context mRealContext = androidx.test.InstrumentationRegistry.getInstrumentation()
            .getTargetContext();
    private final SparseArray<UserData> mUsers = new SparseArray<>();

    private File mTestDir;

    private Context mSpiedContext;
    private Resources mSpyResources;

    private @Mock PackageManagerService mMockPms;
    private @Mock UserDataPreparer mMockUserDataPreparer;
    private @Mock UserJourneyLogger mUserJourneyLogger;
    private @Mock ActivityManagerInternal mActivityManagerInternal;
    private @Mock DeviceStorageMonitorInternal mDeviceStorageMonitorInternal;
    private @Mock StorageManagerInternal mStorageManagerInternal;
    private @Mock LockSettingsInternal mLockSettingsInternal;
    private @Mock PackageManagerInternal mPackageManagerInternal;
    private @Mock DevicePolicyManagerInternal mDevicePolicyManagerInternal;
    private @Mock KeyguardManager mKeyguardManager;
    private @Mock PowerManager mPowerManager;
    private @Mock TelecomManager mTelecomManager;

    /**
     * Reference to the {@link UserManagerService} being tested.
     */
    private UserManagerService mUms;

    /**
     * Reference to the {@link UserManagerInternal} being tested.
     */
    private UserManagerInternal mUmi;

    @Before
    @UiThreadTest // Needed to initialize main handler
    public void setFixtures() {
        mSpiedContext = spy(mRealContext);

        // Disable binder caches in this process.
        PropertyInvalidatedCache.disableForTestMode();

        // Called when creating new users
        when(mDeviceStorageMonitorInternal.isMemoryLow()).thenReturn(false);
        mockGetLocalService(DeviceStorageMonitorInternal.class, mDeviceStorageMonitorInternal);
        mockGetLocalService(StorageManagerInternal.class, mStorageManagerInternal);
        doReturn(mKeyguardManager).when(mSpiedContext).getSystemService(KeyguardManager.class);
        when(mSpiedContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mSpiedContext.getSystemService(TelecomManager.class)).thenReturn(mTelecomManager);
        mockGetLocalService(LockSettingsInternal.class, mLockSettingsInternal);
        mockGetLocalService(PackageManagerInternal.class, mPackageManagerInternal);
        doNothing().when(mSpiedContext).sendBroadcastAsUser(any(), any(), any());
        mockIsLowRamDevice(false);

        mSpyResources = spy(mSpiedContext.getResources());
        when(mSpiedContext.getResources()).thenReturn(mSpyResources);
        mockHsumBootStrategy(BOOT_TO_PREVIOUS_OR_FIRST_SWITCHABLE_USER);
        mockDisallowRemovingLastAdminUser(false);

        doReturn(mSpyResources).when(Resources::getSystem);

        // Must construct UserManagerService in the UiThread
        mTestDir = new File(mRealContext.getDataDir(), "umstest");
        mTestDir.mkdirs();
        mUms = new UserManagerService(mSpiedContext, mMockPms, mMockUserDataPreparer,
                mUserJourneyLogger, mPackagesLock, mTestDir, mUsers);
        mUmi = LocalServices.getService(UserManagerInternal.class);
        assertWithMessage("LocalServices.getService(UserManagerInternal.class)").that(mUmi)
                .isNotNull();
    }

    @After
    public void tearDown() {
        // LocalServices follows the "Highlander rule" - There can be only one!
        LocalServices.removeServiceForTest(UserManagerInternal.class);

        // Clean up test dir to remove persisted user files.
        deleteRecursive(mTestDir);
        mUsers.clear();
    }

    @Test
    public void testGetCurrentUserId_amInternalNotReady() {
        mockGetLocalService(ActivityManagerInternal.class, null);

        assertWithMessage("getCurrentUserId()").that(mUms.getCurrentUserId())
                .isEqualTo(UserHandle.USER_NULL);
    }

    @Test
    public void testGetCurrentAndTargetUserIds() {
        mockCurrentAndTargetUser(USER_ID, OTHER_USER_ID);

        assertWithMessage("getCurrentAndTargetUserIds()")
                .that(mUms.getCurrentAndTargetUserIds())
                .isEqualTo(new Pair<>(USER_ID, OTHER_USER_ID));
    }

    @Test
    public void testGetCurrentUserId() {
        mockCurrentUser(USER_ID);

        assertWithMessage("getCurrentUserId()").that(mUms.getCurrentUserId())
                .isEqualTo(USER_ID);
    }

    @Test
    public void testIsCurrentUserOrRunningProfileOfCurrentUser_currentUser() {
        mockCurrentUser(USER_ID);

        assertWithMessage("isCurrentUserOrRunningProfileOfCurrentUser(%s)", USER_ID)
                .that(mUms.isCurrentUserOrRunningProfileOfCurrentUser(USER_ID)).isTrue();
    }

    @Test
    public void testIsCurrentUserOrRunningProfileOfCurrentUser_notCurrentUser() {
        mockCurrentUser(OTHER_USER_ID);

        assertWithMessage("isCurrentUserOrRunningProfileOfCurrentUser(%s)", USER_ID)
                .that(mUms.isCurrentUserOrRunningProfileOfCurrentUser(USER_ID)).isFalse();
    }

    @Test
    public void testIsCurrentUserOrRunningProfileOfCurrentUser_startedProfileOfCurrentUser() {
        addDefaultProfileAndParent();
        startDefaultProfile();
        mockCurrentUser(PARENT_USER_ID);

        assertWithMessage("isCurrentUserOrRunningProfileOfCurrentUser(%s)", PROFILE_USER_ID)
                .that(mUms.isCurrentUserOrRunningProfileOfCurrentUser(PROFILE_USER_ID)).isTrue();
    }

    @Test
    public void testIsCurrentUserOrRunningProfileOfCurrentUser_stoppedProfileOfCurrentUser() {
        addDefaultProfileAndParent();
        stopDefaultProfile();
        mockCurrentUser(PARENT_USER_ID);

        assertWithMessage("isCurrentUserOrRunningProfileOfCurrentUser(%s)", PROFILE_USER_ID)
                .that(mUms.isCurrentUserOrRunningProfileOfCurrentUser(PROFILE_USER_ID)).isFalse();
    }

    @Test
    public void testIsCurrentUserOrRunningProfileOfCurrentUser_profileOfNonCurrentUSer() {
        addDefaultProfileAndParent();
        mockCurrentUser(OTHER_USER_ID);

        assertWithMessage("isCurrentUserOrRunningProfileOfCurrentUser(%s)", PROFILE_USER_ID)
                .that(mUms.isCurrentUserOrRunningProfileOfCurrentUser(PROFILE_USER_ID)).isFalse();
    }

    @Test
    public void testIsUserRunning_StartedUserShouldReturnTrue() {
        addSecondaryUser(USER_ID);
        startUser(USER_ID);

        assertWithMessage("isUserRunning(%s)", USER_ID)
                .that(mUms.isUserRunning(USER_ID)).isTrue();
    }

    @Test
    public void testIsUserRunning_StoppedUserShouldReturnFalse() {
        addSecondaryUser(USER_ID);
        stopUser(USER_ID);

        assertWithMessage("isUserRunning(%s)", USER_ID)
                .that(mUms.isUserRunning(USER_ID)).isFalse();
    }

    @Test
    public void testIsUserRunning_CurrentUserStartedWorkProfileShouldReturnTrue() {
        addDefaultProfileAndParent();
        startDefaultProfile();

        assertWithMessage("isUserRunning(%s)", PROFILE_USER_ID)
                .that(mUms.isUserRunning(PROFILE_USER_ID)).isTrue();
    }

    @Test
    public void testIsUserRunning_CurrentUserStoppedWorkProfileShouldReturnFalse() {
        addDefaultProfileAndParent();
        stopDefaultProfile();

        assertWithMessage("isUserRunning(%s)", PROFILE_USER_ID)
                .that(mUms.isUserRunning(PROFILE_USER_ID)).isFalse();
    }

    @Test
    public void testSetBootUser_SuppliedUserIsSwitchable() throws Exception {
        addSecondaryUser(USER_ID);
        addSecondaryUser(OTHER_USER_ID);

        mUms.setBootUser(OTHER_USER_ID);

        assertWithMessage("getBootUser")
                .that(mUmi.getBootUser(/* waitUntilSet= */ false)).isEqualTo(OTHER_USER_ID);
    }

    @Test
    public void testSetBootUser_NotHeadless_SuppliedUserIsNotSwitchable() throws Exception {
        setSystemUserHeadless(false);
        addSecondaryUser(USER_ID);
        addSecondaryUser(OTHER_USER_ID);
        addDefaultProfileAndParent();

        mUms.setBootUser(PROFILE_USER_ID);

        assertWithMessage("getBootUser")
                .that(mUmi.getBootUser(/* waitUntilSet= */ false))
                .isEqualTo(USER_SYSTEM);
    }

    @Test
    public void testSetBootUser_Headless_SuppliedUserIsNotSwitchable() throws Exception {
        setSystemUserHeadless(true);
        addSecondaryUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addSecondaryUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);
        addDefaultProfileAndParent();

        mUms.setBootUser(PROFILE_USER_ID);
        // Boot user not switchable so return most recently in foreground.
        assertWithMessage("getBootUser")
                .that(mUmi.getBootUser(/* waitUntilSet= */ false)).isEqualTo(OTHER_USER_ID);
    }

    @Test
    public void testGetBootUser_NotHeadless_ReturnsSystemUser() throws Exception {
        setSystemUserHeadless(false);
        addSecondaryUser(USER_ID);
        addSecondaryUser(OTHER_USER_ID);

        assertWithMessage("getBootUser")
                .that(mUmi.getBootUser(/* waitUntilSet= */ false))
                .isEqualTo(USER_SYSTEM);
    }

    @Test
    public void testGetBootUser_Headless_ReturnsMostRecentlyInForeground() throws Exception {
        setSystemUserHeadless(true);
        addSecondaryUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);

        addSecondaryUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        assertWithMessage("getBootUser")
                .that(mUmi.getBootUser(/* waitUntilSet= */ false)).isEqualTo(OTHER_USER_ID);
    }

    @Test
    public void testGetBootUser_CannotSwitchToHeadlessSystemUser_ThrowsIfOnlySystemUserExists()
            throws Exception {
        setSystemUserHeadless(true);
        removeNonSystemUsers();
        mockCanSwitchToHeadlessSystemUser(false);

        assertThrows(UserManager.CheckedUserOperationException.class,
                () -> mUmi.getBootUser(/* waitUntilSet= */ false));
    }

    @Test
    public void testGetBootUser_CanSwitchToHeadlessSystemUser_NoThrowIfOnlySystemUserExists()
            throws Exception {
        setSystemUserHeadless(true);
        removeNonSystemUsers();
        mockCanSwitchToHeadlessSystemUser(true);

        assertThat(mUmi.getBootUser(/* waitUntilSet= */ false)).isEqualTo(USER_SYSTEM);
    }

    @Test
    public void testGetPreviousUserToEnterForeground() throws Exception {
        addSecondaryUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addSecondaryUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        assertWithMessage("getPreviousUserToEnterForeground")
                .that(mUms.getPreviousUserToEnterForeground())
                .isEqualTo(OTHER_USER_ID);
    }

    @Test
    public void testGetPreviousUserToEnterForeground_SkipsCurrentUser() throws Exception {
        addSecondaryUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addSecondaryUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        mockCurrentUser(OTHER_USER_ID);
        assertWithMessage("getPreviousUserToEnterForeground")
                .that(mUms.getPreviousUserToEnterForeground())
                .isEqualTo(USER_ID);
    }

    @Test
    public void testGetPreviousUserToEnterForeground_SkipsPartialUsers() throws Exception {
        addSecondaryUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addSecondaryUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        mUsers.get(OTHER_USER_ID).info.partial = true;
        assertWithMessage("getPreviousUserToEnterForeground")
                .that(mUms.getPreviousUserToEnterForeground())
                .isEqualTo(USER_ID);
    }

    @Test
    public void testGetPreviousUserToEnterForeground_SkipsDisabledUsers() throws Exception {
        addSecondaryUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addSecondaryUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        mUsers.get(OTHER_USER_ID).info.flags |= UserInfo.FLAG_DISABLED;
        assertWithMessage("getPreviousUserToEnterForeground")
                .that(mUms.getPreviousUserToEnterForeground())
                .isEqualTo(USER_ID);
    }

    @Test
    public void testGetPreviousUserToEnterForeground_SkipsRemovingUsers() throws Exception {
        addSecondaryUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addSecondaryUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        mUms.addRemovingUserId(OTHER_USER_ID);
        assertWithMessage("getPreviousUserToEnterForeground")
                .that(mUms.getPreviousUserToEnterForeground())
                .isEqualTo(USER_ID);
    }

    @Test
    public void testGetPreviousUserToEnterForeground_ReturnsHeadlessSystemUser() throws Exception {
        addSecondaryUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        setSystemUserHeadless(true);
        mockCanSwitchToHeadlessSystemUser(true);
        setLastForegroundTime(USER_SYSTEM, 2_000_000L);

        assertWithMessage("getPreviousUserToEnterForeground")
                .that(mUms.getPreviousUserToEnterForeground())
                .isEqualTo(USER_SYSTEM);
    }

    @Test
    public void assertIsUserSwitcherEnabledOnMultiUserSettings() throws Exception {
        resetUserSwitcherEnabled();

        mockUserSwitcherEnabled(false);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isFalse();

        mockUserSwitcherEnabled(true);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isTrue();
    }

    @Test
    public void assertIsUserSwitcherEnabledOnMaxSupportedUsers()  throws Exception {
        resetUserSwitcherEnabled();

        mockMaxSupportedUsers(/* maxUsers= */ 1);
        assertThat(UserManager.supportsMultipleUsers()).isFalse();
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isFalse();

        mockMaxSupportedUsers(/* maxUsers= */ 8);
        assertThat(UserManager.supportsMultipleUsers()).isTrue();
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isTrue();
    }

    @Test
    public void assertIsUserSwitcherEnabled()  throws Exception {
        resetUserSwitcherEnabled();

        mockMaxSupportedUsers(/* maxUsers= */ 8);
        assertThat(mUms.isUserSwitcherEnabled(true, USER_ID)).isTrue();

        mockUserSwitcherEnabled(false);
        assertThat(mUms.isUserSwitcherEnabled(true, USER_ID)).isFalse();

        mockUserSwitcherEnabled(true);
        assertThat(mUms.isUserSwitcherEnabled(false, USER_ID)).isTrue();

        mUms.setUserRestriction(DISALLOW_USER_SWITCH, true, USER_ID);
        assertThat(mUms.isUserSwitcherEnabled(false, USER_ID)).isFalse();

        mUms.setUserRestriction(DISALLOW_USER_SWITCH, false, USER_ID);
        mockMaxSupportedUsers(1);
        assertThat(mUms.isUserSwitcherEnabled(true, USER_ID)).isFalse();
    }

    @Test
    public void assertIsUserSwitcherEnabledOnShowMultiuserUI()  throws Exception {
        resetUserSwitcherEnabled();

        mockShowMultiuserUI(/* show= */ false);
        assertThat(UserManager.supportsMultipleUsers()).isFalse();
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isFalse();

        mockShowMultiuserUI(/* show= */ true);
        assertThat(UserManager.supportsMultipleUsers()).isTrue();
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isTrue();
    }

    @Test
    public void assertIsUserSwitcherEnabledOnUserRestrictions() throws Exception {
        resetUserSwitcherEnabled();

        mUms.setUserRestriction(DISALLOW_USER_SWITCH, true, USER_ID);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isFalse();

        mUms.setUserRestriction(DISALLOW_USER_SWITCH, false, USER_ID);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isTrue();
    }

    @Test
    public void assertIsUserSwitcherEnabledOnDemoMode() throws Exception {
        resetUserSwitcherEnabled();

        mockDeviceDemoMode(/* enabled= */ true);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isFalse();

        mockDeviceDemoMode(/* enabled= */ false);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isTrue();
    }

    @Test
    public void testMainUser_hasNoCallsOrSMSRestrictionsByDefault() {
        // Remove the main user so we can add another one
        for (int i = 0; i < mUsers.size(); i++) {
            UserData userData = mUsers.valueAt(i);
            if (userData.info.isMain()) {
                mUsers.delete(i);
                break;
            }
        }

        UserInfo mainUser = mUms.createUserWithThrow("main user", USER_TYPE_FULL_SECONDARY,
                UserInfo.FLAG_FULL | UserInfo.FLAG_MAIN);

        assertThat(mUms.hasUserRestriction(DISALLOW_OUTGOING_CALLS, mainUser.id))
                .isFalse();
        assertThat(mUms.hasUserRestriction(DISALLOW_SMS, mainUser.id))
                .isFalse();
    }

    @Test
    public void testCreateUserWithLongName_TruncatesName() {
        UserInfo user = mUms.createUserWithThrow(generateLongString(), USER_TYPE_FULL_SECONDARY, 0);
        assertThat(user.name.length()).isEqualTo(UserManager.MAX_USER_NAME_LENGTH);
        UserInfo user1 = mUms.createUserWithThrow("Test", USER_TYPE_FULL_SECONDARY, 0);
        assertThat(user1.name.length()).isEqualTo(4);
    }

    @Test
    public void testDefaultRestrictionsArePersistedAfterCreateUser()
            throws IOException, XmlPullParserException {
        UserInfo user = mUms.createUserWithThrow("Test", USER_TYPE_FULL_SECONDARY, 0);
        assertTrue(hasRestrictionsInUserXMLFile(user.id));
    }

    @Test
    @EnableFlags({FLAG_ALLOW_PRIVATE_PROFILE, FLAG_ENABLE_PRIVATE_SPACE_FEATURES})
    public void testAutoLockPrivateProfile() {
        int mainUser = mUms.getMainUserId();
        assumeTrue(mUms.canAddPrivateProfile(mainUser));
        UserManagerService mSpiedUms = spy(mUms);
        UserInfo privateProfileUser =
                mSpiedUms.createProfileForUserEvenWhenDisallowedWithThrow(PRIVATE_PROFILE_NAME,
                        USER_TYPE_PROFILE_PRIVATE, 0, mainUser, null);
        Mockito.doNothing().when(mSpiedUms).setQuietModeEnabledAsync(
                eq(privateProfileUser.id), eq(true), any(), any());

        mSpiedUms.autoLockPrivateSpace();

        Mockito.verify(mSpiedUms).setQuietModeEnabledAsync(
                eq(privateProfileUser.id), eq(true), any(), any());
    }

    @Test
    @EnableFlags({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES,
        FLAG_SUPPORT_AUTOLOCK_FOR_PRIVATE_SPACE
    })
    public void testAutoLockOnDeviceLockForPrivateProfile() {
        int mainUser = mUms.getMainUserId();
        assumeTrue(mUms.canAddPrivateProfile(mainUser));
        UserManagerService mSpiedUms = spy(mUms);
        UserInfo privateProfileUser =
                mSpiedUms.createProfileForUserEvenWhenDisallowedWithThrow(PRIVATE_PROFILE_NAME,
                USER_TYPE_PROFILE_PRIVATE, 0, mainUser, null);
        mockAutoLockForPrivateSpace(Settings.Secure.PRIVATE_SPACE_AUTO_LOCK_ON_DEVICE_LOCK);
        Mockito.doNothing().when(mSpiedUms).setQuietModeEnabledAsync(
                eq(privateProfileUser.id), eq(true), any(), any());

        mSpiedUms.tryAutoLockingPrivateSpaceOnKeyguardChanged(true);

        Mockito.verify(mSpiedUms).setQuietModeEnabledAsync(
                eq(privateProfileUser.id), eq(true), any(), any());
    }

    @Test
    @EnableFlags({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES,
        FLAG_SUPPORT_AUTOLOCK_FOR_PRIVATE_SPACE
    })
    public void testAutoLockOnDeviceLockForPrivateProfile_keyguardUnlocked() {
        assumeTrue(mUms.canAddPrivateProfile(0));
        UserManagerService mSpiedUms = spy(mUms);
        UserInfo privateProfileUser =
                mSpiedUms.createProfileForUserEvenWhenDisallowedWithThrow(PRIVATE_PROFILE_NAME,
                USER_TYPE_PROFILE_PRIVATE, 0, 0, null);
        mockAutoLockForPrivateSpace(Settings.Secure.PRIVATE_SPACE_AUTO_LOCK_ON_DEVICE_LOCK);

        mSpiedUms.tryAutoLockingPrivateSpaceOnKeyguardChanged(false);

        // Verify that no operation to disable quiet mode is not called
        Mockito.verify(mSpiedUms, never()).setQuietModeEnabledAsync(
                eq(privateProfileUser.id), eq(true), any(), any());
    }

    @Test
    @EnableFlags({FLAG_ALLOW_PRIVATE_PROFILE, FLAG_ENABLE_PRIVATE_SPACE_FEATURES})
    @DisableFlags(FLAG_SUPPORT_AUTOLOCK_FOR_PRIVATE_SPACE)
    public void testAutoLockOnDeviceLockForPrivateProfile_flagDisabled() {
        int mainUser = mUms.getMainUserId();
        assumeTrue(mUms.canAddPrivateProfile(mainUser));
        UserManagerService mSpiedUms = spy(mUms);
        UserInfo privateProfileUser =
                mSpiedUms.createProfileForUserEvenWhenDisallowedWithThrow(PRIVATE_PROFILE_NAME,
                USER_TYPE_PROFILE_PRIVATE, 0, mainUser, null);

        mSpiedUms.tryAutoLockingPrivateSpaceOnKeyguardChanged(true);

        // Verify that no auto-lock operations take place
        verify((MockedVoidMethod) () -> Settings.Secure.getInt(any(),
                eq(Settings.Secure.PRIVATE_SPACE_AUTO_LOCK), anyInt()), never());
        Mockito.verify(mSpiedUms, never()).setQuietModeEnabledAsync(
                eq(privateProfileUser.id), eq(true), any(), any());
    }

    @Test
    @EnableFlags({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES,
        FLAG_SUPPORT_AUTOLOCK_FOR_PRIVATE_SPACE
    })
    public void testAutoLockAfterInactityForPrivateProfile() {
        int mainUser = mUms.getMainUserId();
        assumeTrue(mUms.canAddPrivateProfile(mainUser));
        UserManagerService mSpiedUms = spy(mUms);
        mockAutoLockForPrivateSpace(Settings.Secure.PRIVATE_SPACE_AUTO_LOCK_AFTER_INACTIVITY);
        when(mPowerManager.isInteractive()).thenReturn(false);

        UserInfo privateProfileUser =
                mSpiedUms.createProfileForUserEvenWhenDisallowedWithThrow(PRIVATE_PROFILE_NAME,
                        USER_TYPE_PROFILE_PRIVATE, 0, mainUser, null);
        Mockito.doNothing().when(mSpiedUms).scheduleAlarmToAutoLockPrivateSpace(
                eq(privateProfileUser.id), anyLong());

        mSpiedUms.maybeScheduleAlarmToAutoLockPrivateSpace();

        Mockito.verify(mSpiedUms).scheduleAlarmToAutoLockPrivateSpace(
                eq(privateProfileUser.id), anyLong());
    }

    @Test
    @EnableFlags({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES,
        FLAG_SUPPORT_AUTOLOCK_FOR_PRIVATE_SPACE
    })
    public void testSetOrUpdateAutoLockPreference_noPrivateProfile() {
        mUms.setOrUpdateAutoLockPreferenceForPrivateProfile(
                Settings.Secure.PRIVATE_SPACE_AUTO_LOCK_AFTER_INACTIVITY);

        Mockito.verify(mSpiedContext, never()).registerReceiver(any(), any(), any(), any());
        Mockito.verify(mSpiedContext, never()).unregisterReceiver(any());
        Mockito.verify(mKeyguardManager, never()).removeKeyguardLockedStateListener((any()));
        Mockito.verify(mKeyguardManager, never()).addKeyguardLockedStateListener(any(), any());
    }

    @Test
    @EnableFlags({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES,
        FLAG_SUPPORT_AUTOLOCK_FOR_PRIVATE_SPACE
    })
    public void testSetOrUpdateAutoLockPreference() {
        int mainUser = mUms.getMainUserId();
        assumeTrue(mUms.canAddPrivateProfile(mainUser));
        mUms.createProfileForUserEvenWhenDisallowedWithThrow(PRIVATE_PROFILE_NAME,
                        USER_TYPE_PROFILE_PRIVATE, 0, mainUser, null);

        // Set the preference to auto lock on device lock
        mUms.setOrUpdateAutoLockPreferenceForPrivateProfile(
                Settings.Secure.PRIVATE_SPACE_AUTO_LOCK_ON_DEVICE_LOCK);

        // Verify that keyguard state listener was added
        Mockito.verify(mKeyguardManager).addKeyguardLockedStateListener(any(), any());
        //Verity that keyguard state listener was not removed
        Mockito.verify(mKeyguardManager, never()).removeKeyguardLockedStateListener(any());
        // Broadcasts are already unregistered when UserManagerService starts and the flag
        // isDeviceInactivityBroadcastReceiverRegistered is false
        Mockito.verify(mSpiedContext, never()).registerReceiver(any(), any(), any(), any());
        Mockito.verify(mSpiedContext, never()).unregisterReceiver(any());

        Mockito.clearInvocations(mKeyguardManager);
        Mockito.clearInvocations(mSpiedContext);

        // Now set the preference to auto-lock on inactivity
        mUms.setOrUpdateAutoLockPreferenceForPrivateProfile(
                Settings.Secure.PRIVATE_SPACE_AUTO_LOCK_AFTER_INACTIVITY);

        // Verify that inactivity broadcasts are registered
        Mockito.verify(mSpiedContext, times(2)).registerReceiver(any(), any(), any(), any());
        // Verify that keyguard state listener is removed
        Mockito.verify(mKeyguardManager).removeKeyguardLockedStateListener(any());
        // Verify that all other operations don't take place
        Mockito.verify(mSpiedContext, never()).unregisterReceiver(any());
        Mockito.verify(mKeyguardManager, never()).addKeyguardLockedStateListener(any(), any());

        Mockito.clearInvocations(mKeyguardManager);
        Mockito.clearInvocations(mSpiedContext);

        // Finally, set the preference to auto-lock only after device restart, which is the default
        // behaviour
        mUms.setOrUpdateAutoLockPreferenceForPrivateProfile(
                Settings.Secure.PRIVATE_SPACE_AUTO_LOCK_AFTER_DEVICE_RESTART);

        // Verify that inactivity broadcasts are unregistered and keyguard listener was removed
        Mockito.verify(mSpiedContext).unregisterReceiver(any());
        Mockito.verify(mKeyguardManager).removeKeyguardLockedStateListener(any());
        // Verify that no broadcasts were registered and no listeners were added
        Mockito.verify(mSpiedContext, never()).registerReceiver(any(), any(), any(), any());
        Mockito.verify(mKeyguardManager, never()).addKeyguardLockedStateListener(any(), any());
    }

    @Test
    @EnableFlags({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES,
    })
    public void testGetProfileIdsExcludingHidden() {
        assumeTrue(mUms.canAddPrivateProfile(0));
        UserInfo privateProfileUser =
                mUms.createProfileForUserEvenWhenDisallowedWithThrow("TestPrivateProfile",
                        USER_TYPE_PROFILE_PRIVATE, 0, 0, null);
        for (int id : mUms.getProfileIdsExcludingHidden(0, true)) {
            assertThat(id).isNotEqualTo(privateProfileUser.id);
        }
    }

    @Test
    @EnableFlags({android.multiuser.Flags.FLAG_ALLOW_SUPERVISING_PROFILE})
    public void testGetProfileIdsIncludingAlwaysVisible_supervisingProfile() {
        assumeTrue(mUms.canAddMoreUsersOfType(USER_TYPE_FULL_SECONDARY));
        UserInfo secondaryUser = mUms.createUserWithThrow("Secondary", USER_TYPE_FULL_SECONDARY, 0);
        UserInfo supervisingProfile = mUms.createUserWithThrow("Supervising",
                USER_TYPE_PROFILE_SUPERVISING, 0);
        assertThat(mUmi.getProfileIds(secondaryUser.id, /* enabledOnly */ true,
                /* includeAlwaysVisible */ true)).asList().containsExactly(
                        secondaryUser.id, supervisingProfile.id);
        assertThat(mUmi.getProfileIds(secondaryUser.id, /* enabledOnly */ true,
                /* includeAlwaysVisible */ false)).asList().containsExactly(secondaryUser.id);

        assertThat(mUmi.getProfileIds(
                supervisingProfile.id, /* enabledOnly */ true, /* includeAlwaysVisible */ true))
                .asList().containsExactlyElementsIn(Ints.asList(mUms.getUserIds()));
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_BLOCK_PRIVATE_SPACE_CREATION,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES
    })
    public void testCreatePrivateProfileOnHeadlessSystemUser_shouldAllowCreation() {
        UserManagerService mSpiedUms = spy(mUms);
        assumeTrue(mUms.isHeadlessSystemUserMode());
        int mainUser = mSpiedUms.getMainUserId();
        // Check whether private space creation is blocked on the device
        assumeTrue(mSpiedUms.canAddPrivateProfile(mainUser));
        assertThat(mSpiedUms.createProfileForUserEvenWhenDisallowedWithThrow(
                PRIVATE_PROFILE_NAME, USER_TYPE_PROFILE_PRIVATE, 0, mainUser, null)).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_BLOCK_PRIVATE_SPACE_CREATION,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES
    })
    public void testCreatePrivateProfileOnSecondaryUser_shouldNotAllowCreation() {
        assumeTrue(mUms.canAddMoreUsersOfType(USER_TYPE_FULL_SECONDARY));
        UserInfo user = mUms.createUserWithThrow(generateLongString(), USER_TYPE_FULL_SECONDARY, 0);
        assertThat(mUms.canAddPrivateProfile(user.id)).isFalse();
        assertThrows(ServiceSpecificException.class,
                () -> mUms.createProfileForUserWithThrow(PRIVATE_PROFILE_NAME,
                        USER_TYPE_PROFILE_PRIVATE, 0, user.id, null));
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_BLOCK_PRIVATE_SPACE_CREATION,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES
    })
    public void testCreatePrivateProfileOnAutoDevices_shouldNotAllowCreation() {
        doReturn(true).when(mMockPms).hasSystemFeature(eq(FEATURE_AUTOMOTIVE), anyInt());
        int mainUser = mUms.getMainUserId();
        assertThat(mUms.canAddPrivateProfile(mainUser)).isFalse();
        assertThrows(ServiceSpecificException.class,
                () -> mUms.createProfileForUserWithThrow(PRIVATE_PROFILE_NAME,
                        USER_TYPE_PROFILE_PRIVATE, 0, mainUser, null));
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_BLOCK_PRIVATE_SPACE_CREATION,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES
    })
    public void testCreatePrivateProfileOnTV_shouldNotAllowCreation() {
        doReturn(true).when(mMockPms).hasSystemFeature(eq(FEATURE_LEANBACK), anyInt());
        int mainUser = mUms.getMainUserId();
        assertThat(mUms.canAddPrivateProfile(mainUser)).isFalse();
        assertThrows(ServiceSpecificException.class,
                () -> mUms.createProfileForUserEvenWhenDisallowedWithThrow(PRIVATE_PROFILE_NAME,
                        USER_TYPE_PROFILE_PRIVATE, 0, mainUser, null));
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_BLOCK_PRIVATE_SPACE_CREATION,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES
    })
    public void testCreatePrivateProfileOnEmbedded_shouldNotAllowCreation() {
        doReturn(true).when(mMockPms).hasSystemFeature(eq(FEATURE_EMBEDDED), anyInt());
        int mainUser = mUms.getMainUserId();
        assertThat(mUms.canAddPrivateProfile(mainUser)).isFalse();
        assertThrows(ServiceSpecificException.class,
                () -> mUms.createProfileForUserEvenWhenDisallowedWithThrow(PRIVATE_PROFILE_NAME,
                        USER_TYPE_PROFILE_PRIVATE, 0, mainUser, null));
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_BLOCK_PRIVATE_SPACE_CREATION,
        FLAG_ENABLE_PRIVATE_SPACE_FEATURES
    })
    public void testCreatePrivateProfileOnWatch_shouldNotAllowCreation() {
        doReturn(true).when(mMockPms).hasSystemFeature(eq(FEATURE_WATCH), anyInt());
        int mainUser = mUms.getMainUserId();
        assertThat(mUms.canAddPrivateProfile(mainUser)).isFalse();
        assertThrows(ServiceSpecificException.class,
                () -> mUms.createProfileForUserEvenWhenDisallowedWithThrow(PRIVATE_PROFILE_NAME,
                        USER_TYPE_PROFILE_PRIVATE, 0, mainUser, null));
    }

    @Test
    public void testGetBootUser_Headless_BootToSystemUserWhenDeviceIsProvisioned() {
        setSystemUserHeadless(true);
        addSecondaryUser(USER_ID);
        addSecondaryUser(OTHER_USER_ID);
        mockProvisionedDevice(true);
        mockHsumBootStrategy(BOOT_TO_HSU_FOR_PROVISIONED_DEVICE);

        assertThat(mUms.getBootUser()).isEqualTo(USER_SYSTEM);
    }

    @Test
    public void testGetBootUser_Headless_BootToFirstSwitchableFullUserWhenDeviceNotProvisioned() {
        setSystemUserHeadless(true);
        addSecondaryUser(USER_ID);
        addSecondaryUser(OTHER_USER_ID);
        mockProvisionedDevice(false);
        mockHsumBootStrategy(BOOT_TO_HSU_FOR_PROVISIONED_DEVICE);
        // Even if the headless system user switchable flag is true, the boot user should be the
        // first switchable full user.
        mockCanSwitchToHeadlessSystemUser(true);

        assertThat(mUms.getBootUser()).isEqualTo(USER_ID);
    }

    @Test
    public void testGetBootUser_Headless_ThrowsIfBootFailsNoFullUserWhenDeviceNotProvisioned()
                throws Exception {
        setSystemUserHeadless(true);
        removeNonSystemUsers();
        mockProvisionedDevice(false);
        mockHsumBootStrategy(BOOT_TO_HSU_FOR_PROVISIONED_DEVICE);

        assertThrows(ServiceSpecificException.class,
                () -> mUms.getBootUser());
    }

    @Test
    @EnableFlags(FLAG_LOGOUT_USER_API)
    public void testGetUserLogoutability_HsumAndInteractiveHeadlessSystemUser_UserCanLogout()
            throws Exception {
        setSystemUserHeadless(true);
        addSecondaryUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        mockCurrentUser(USER_ID);

        mockCanSwitchToHeadlessSystemUser(true);
        mockUserIsInCall(false);

        assertThat(mUms.getUserLogoutability(USER_ID))
                .isEqualTo(UserManager.LOGOUTABILITY_STATUS_OK);
    }

    @Test
    @EnableFlags(FLAG_LOGOUT_USER_API)
    public void testGetUserLogoutability_HsumAndNonInteractiveHeadlessSystemUser_UserCannotLogout()
            throws Exception {
        setSystemUserHeadless(true);
        mockCanSwitchToHeadlessSystemUser(false);
        addSecondaryUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        mockCurrentUser(USER_ID);
        mockUserIsInCall(false);

        assertThat(mUms.getUserLogoutability(USER_ID))
                .isEqualTo(UserManager.LOGOUTABILITY_STATUS_DEVICE_NOT_SUPPORTED);
    }

    @Test
    @EnableFlags(FLAG_LOGOUT_USER_API)
    public void
            testGetUserLogoutability_HsumAndInteractiveHeadlessSystemUser_SystemUserCannotLogout()
                    throws Exception {
        setSystemUserHeadless(true);
        mockCanSwitchToHeadlessSystemUser(true);
        mockCurrentUser(USER_SYSTEM);
        assertThat(mUms.getUserLogoutability(USER_SYSTEM))
                .isEqualTo(UserManager.LOGOUTABILITY_STATUS_CANNOT_LOGOUT_SYSTEM_USER);
    }

    @Test
    @EnableFlags(FLAG_LOGOUT_USER_API)
    public void testGetUserLogoutability_NonHsum_SystemUserCannotLogout() throws Exception {
        setSystemUserHeadless(false);
        mockCurrentUser(USER_SYSTEM);
        assertThat(
                mUms.getUserLogoutability(USER_SYSTEM)).isEqualTo(
                UserManager.LOGOUTABILITY_STATUS_CANNOT_LOGOUT_SYSTEM_USER);
    }

    @Test
    @EnableFlags(FLAG_LOGOUT_USER_API)
    public void testGetUserLogoutability_CannotSwitch_CannotLogout() throws Exception {
        setSystemUserHeadless(true);
        mockCanSwitchToHeadlessSystemUser(true);
        addSecondaryUser(USER_ID);
        addSecondaryUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 1_000_000L);
        mockCurrentUser(USER_ID);
        mUms.setUserRestriction(DISALLOW_USER_SWITCH, true, USER_ID);
        assertThat(mUms.getUserLogoutability(USER_ID))
                .isEqualTo(UserManager.LOGOUTABILITY_STATUS_CANNOT_SWITCH);
    }

    @Test
    @DisableFlags(FLAG_LOGOUT_USER_API)
    public void testGetUserLogoutability_LogoutDisabled() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> mUms.getUserLogoutability(USER_ID));
    }

    @Test
    public void testGetOwnerName() {
        assertThat(mUms.getOwnerName()).isNotEmpty();
    }

    @Test
    public void testGetGuestName() {
        assertThat(mUms.getGuestName()).isNotEmpty();
    }

    @Test
    public void testUserWithName_null() {
        assertThat(mUms.userWithName(null)).isNull();
    }

    private int getCurrentNumberOfUser0Allocations() {
        return mUms.mUser0Allocations == null ? 0 : mUms.mUser0Allocations.get();
    }

    /**
     * Tests what happens when {@code userWithName} is called with a {@link UserInfo} that has a
     * explicit (non-{@code null}) name.
     */
    @Test
    public void testUserWithName_hasExplicitName() {
        int initialAllocations = getCurrentNumberOfUser0Allocations();

        var systemUser = new UserInfo(USER_SYSTEM, "James Bond", /* flags= */ 0);
        expect.withMessage("userWithName(%s)", systemUser).that(mUms.userWithName(systemUser))
                .isSameInstanceAs(systemUser);
        expect.withMessage("system.name").that(systemUser.name).isEqualTo("James Bond");
        expect.withMessage("number of system user allocations after systemUser call")
                .that(getCurrentNumberOfUser0Allocations()).isEqualTo(initialAllocations);

        var mainUser = new UserInfo(007, "James Bond", UserInfo.FLAG_MAIN);
        expect.withMessage("userWithName(%s)", mainUser).that(mUms.userWithName(mainUser))
                .isSameInstanceAs(mainUser);
        expect.withMessage("mainUser.name").that(mainUser.name).isEqualTo("James Bond");
        expect.withMessage("number of system user allocations after non-systemUser call")
                .that(getCurrentNumberOfUser0Allocations()).isEqualTo(initialAllocations);

        var guestUser = new UserInfo(007, "James Bond", UserInfo.FLAG_GUEST);
        expect.withMessage("userWithName(%s)", guestUser).that(mUms.userWithName(guestUser))
                .isSameInstanceAs(guestUser);
        expect.withMessage("guest.name").that(guestUser.name).isEqualTo("James Bond");
        expect.withMessage("number of system user allocations after non-systemUser call")
                .that(getCurrentNumberOfUser0Allocations()).isEqualTo(initialAllocations);

        var normalUser = new UserInfo(007, "James Bond", /* flags= */ 0);
        expect.withMessage("userWithName(%s)", systemUser).that(mUms.userWithName(normalUser))
                .isSameInstanceAs(normalUser);
        expect.withMessage("normalUser.name").that(normalUser.name).isEqualTo("James Bond");
        expect.withMessage("number of system user allocations after non-systemUser call")
                .that(getCurrentNumberOfUser0Allocations()).isEqualTo(initialAllocations);
    }

    /**
     * Tests what happens when {@code userWithName} is called with a {@link UserInfo} that has a
     * {@code null} name.
     */
    @Test
    public void testUserWithName_withDefaultName_nonHsum() {
        setSystemUserHeadless(false);
        int initialAllocations = getCurrentNumberOfUser0Allocations();

        var systemUser = new UserInfo(USER_SYSTEM, /* name= */ null, /* flags= */ 0);
        UserInfo systemUserWithName = mUms.userWithName(systemUser);
        assertWithMessage("userWithName(systemUser)").that(systemUserWithName).isNotNull();
        expect.withMessage("userWithName(systemUser)").that(systemUserWithName)
                .isNotSameInstanceAs(systemUser);
        expect.withMessage("systemUserWithName.name").that(systemUserWithName.name)
                .isEqualTo(mUms.getOwnerName());
        expect.withMessage("system.name").that(systemUser.name).isNull();

        // Allocation should only increase for USER_SYSTEM
        int expectedAllocations = mUms.mUser0Allocations == null ? 0 : initialAllocations + 1;
        expect.withMessage("number of system user allocations after systemUser call")
                .that(getCurrentNumberOfUser0Allocations()).isEqualTo(expectedAllocations);

        var mainUser = new UserInfo(42, /* name= */ null, UserInfo.FLAG_MAIN);
        UserInfo mainUserWithName = mUms.userWithName(mainUser);
        assertWithMessage("userWithName(mainUser)").that(mainUserWithName).isNotNull();
        expect.withMessage("userWithName(mainUser)").that(mainUserWithName)
                .isNotSameInstanceAs(mainUser);
        expect.withMessage("mainUserWithName.name").that(mainUserWithName.name)
                .isEqualTo(mUms.getOwnerName());
        expect.withMessage("mainUser.name").that(mainUser.name).isNull();
        expect.withMessage("number of system user allocations after non-systemUser call")
                .that(getCurrentNumberOfUser0Allocations()).isEqualTo(expectedAllocations);

        var guestUser = new UserInfo(42, /* name= */ null, UserInfo.FLAG_GUEST);
        UserInfo guestUserWithName = mUms.userWithName(guestUser);
        assertWithMessage("userWithName(guestUser)").that(guestUserWithName).isNotNull();
        expect.withMessage("userWithName(guestUser)").that(guestUserWithName)
                .isNotSameInstanceAs(guestUser);
        expect.withMessage("mainUserWithName.name").that(guestUserWithName.name)
                .isEqualTo(mUms.getGuestName());
        expect.withMessage("guestUser.name").that(guestUser.name).isNull();
        expect.withMessage("number of system user allocations after non-systemUser call")
                .that(getCurrentNumberOfUser0Allocations()).isEqualTo(expectedAllocations);

        var normalUser = new UserInfo(42, /* name= */ null, /* flags= */ 0);
        UserInfo normalUserWithName = mUms.userWithName(normalUser);
        assertWithMessage("userWithName(normalUser)").that(normalUserWithName).isNotNull();
        expect.withMessage("userWithName(normalUser)").that(normalUserWithName)
                .isNotSameInstanceAs(normalUser);
        expect.withMessage("normalUserWithName.name").that(normalUserWithName.name)
                .isEqualTo(mUms.getUnnamedUserName());
        expect.withMessage("normalUser.name").that(normalUser.name).isNull();
        expect.withMessage("number of system user allocations after non-systemUser call")
                .that(getCurrentNumberOfUser0Allocations()).isEqualTo(expectedAllocations);
    }

    @Test
    @EnableFlags(FLAG_LOGOUT_USER_API)
    public void testUserWithName_withDefaultName_hsum() {
        setSystemUserHeadless(true);

        var systemUser = new UserInfo(USER_SYSTEM, /* name= */ null, /* flags= */ 0);
        UserInfo systemUserWithName = mUms.userWithName(systemUser);
        assertWithMessage("userWithName(systemUser)").that(systemUserWithName).isNotNull();
        expect.withMessage("userWithName(systemUser)").that(systemUserWithName)
                .isNotSameInstanceAs(systemUser);
        expect.withMessage("systemUserWithName.name").that(systemUserWithName.name)
                .isEqualTo(mUms.getHeadlessSystemUserName());
        expect.withMessage("system.name").that(systemUser.name).isNull();
    }

    @Test
    public void testGetName_null() {
        assertThrows(NullPointerException.class, () -> mUms.getName(null));
    }

    /** Tests what happens when the {@link UserInfo} has a explicit (non-{@code null}) name. */
    @Test
    public void testGetName_withExplicitName() {
        String name = "Bond, James Bond!";

        var systemUser = new UserInfo(USER_SYSTEM, name, /* flags= */ 0);
        expect.withMessage("name of system user").that(mUms.getName(systemUser)).isEqualTo(name);

        var mainUser = new UserInfo(42, name, UserInfo.FLAG_MAIN);
        expect.withMessage("name of main user").that(mUms.getName(mainUser)).isEqualTo(name);

        var guestUser = new UserInfo(42, name, UserInfo.FLAG_GUEST);
        expect.withMessage("name of guest user").that(mUms.getName(guestUser)).isEqualTo(name);

        var normalUser = new UserInfo(42, name, /* flags=*/ 0);
        expect.withMessage("name of normal user").that(mUms.getName(normalUser)).isEqualTo(name);
    }

    /** Tests what happens when the {@link UserInfo} has a {@code null} name. */
    @Test
    public void testGetName_withDefaultNames_nonHsum() {
        setSystemUserHeadless(false);

        var systemUser = new UserInfo(USER_SYSTEM, /* name= */ null, /* flags= */ 0);
        expect.withMessage("name of system user").that(mUms.getName(systemUser))
                .isEqualTo(mUms.getOwnerName());

        var mainUser = new UserInfo(42, /* name= */ null, UserInfo.FLAG_MAIN);
        expect.withMessage("name of main user").that(mUms.getName(mainUser))
                .isEqualTo(mUms.getOwnerName());

        var guestUser = new UserInfo(42, /* name= */ null, UserInfo.FLAG_GUEST);
        expect.withMessage("name of guest user").that(mUms.getName(guestUser))
                .isEqualTo(mUms.getGuestName());

        var normalUser = new UserInfo(42, /* name= */ null, /* flags= */ 0);
        expect.withMessage("name of normal user").that(mUms.getName(normalUser))
                .isEqualTo(mUms.getUnnamedUserName());
    }

    @Test
    @EnableFlags(FLAG_LOGOUT_USER_API)
    public void testGetName_withDefaultNames_hsum() {
        setSystemUserHeadless(true);

        var systemUser = new UserInfo(USER_SYSTEM, /* name= */ null, /* flags= */ 0);
        expect.withMessage("name of system user").that(mUms.getName(systemUser))
                .isEqualTo(mUms.getHeadlessSystemUserName());

        var mainUser = new UserInfo(42, /* name= */ null, UserInfo.FLAG_MAIN);
        expect.withMessage("name of main user").that(mUms.getName(mainUser))
                .isEqualTo(mUms.getOwnerName());

        var guestUser = new UserInfo(42, /* name= */ null, UserInfo.FLAG_GUEST);
        expect.withMessage("name of guest user").that(mUms.getName(guestUser))
                .isEqualTo(mUms.getGuestName());

        var normalUser = new UserInfo(42, /* name= */ null, /* flags= */ 0);
        expect.withMessage("name of normal user").that(mUms.getName(normalUser))
                .isEqualTo(mUms.getUnnamedUserName());
    }

    @Test
    public void testCanSwitchToHeadlessSystemUser_true() {
        mockCanSwitchToHeadlessSystemUser(true);

        expect.withMessage("canSwitchToHeadlessSystemUser()")
                .that(mUms.canSwitchToHeadlessSystemUser()).isTrue();
    }

    @Test
    public void testCanSwitchToHeadlessSystemUser_false() {
        mockCanSwitchToHeadlessSystemUser(false);

        expect.withMessage("canSwitchToHeadlessSystemUser()")
                .that(mUms.canSwitchToHeadlessSystemUser()).isFalse();
    }

    @Test
    @EnableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testDemoteMainUser() {
        assumeMainUserIsNotTheSystemUser();
        UserInfo mainUser = createMainUser();
        int mainUserId = mainUser.id;
        int flagsBefore = mainUser.flags;

        boolean demoted = mUms.demoteMainUser();

        // assert call itself
        expect.withMessage("demoteMainUser()").that(demoted).isTrue();

        // assert getMainUserId()
        expect.withMessage("getMainUserId()").that(mUms.getMainUserId())
                .isEqualTo(UserHandle.USER_NULL);

        // assert flags changed
        UserInfo demotedMainUser = mUms.getUserInfo(mainUserId);
        assertWithMessage("getUserInfo(%s)", mainUserId).that(demotedMainUser).isNotNull();
        Log.d(TAG, "Demoted main user: " + demotedMainUser);
        int expectedFlags = flagsBefore ^ UserInfo.FLAG_MAIN;
        int actualFlags = demotedMainUser.flags;
        expect.withMessage("flags of user %s after demotion (where %s=%s and %s=%s)", mainUserId,
                expectedFlags, flagsToString(expectedFlags),
                actualFlags, flagsToString(actualFlags))
                .that(actualFlags).isEqualTo(expectedFlags);

        // assert journey logged
        expectUserJourneyLogged(mainUserId, USER_JOURNEY_DEMOTE_MAIN_USER);
    }

    @Test
    @EnableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testDemoteMainUser_whenItsSystemUser() {
        assumeMainUserIsTheSystemUser();
        int flagsBefore = getSystemUser().flags;

        boolean demoted = mUms.demoteMainUser();

        // assert call itself
        expect.withMessage("demoteMainUser()").that(demoted).isFalse();

        // assert getMainUserId()
        expect.withMessage("getMainUserId()").that(mUms.getMainUserId()).isEqualTo(USER_SYSTEM);

        // assert flags didn't change
        int flagsAfter = getSystemUser().flags;
        expect.withMessage("flags of system user after no-demotion (where %s=%s and %s=%s)",
                flagsBefore, flagsToString(flagsBefore),
                flagsAfter, flagsToString(flagsAfter))
                .that(flagsAfter).isEqualTo(flagsBefore);

        // assert journey not logged
        expectUserJourneyNotLogged(USER_SYSTEM, USER_JOURNEY_DEMOTE_MAIN_USER);
    }

    @Test
    @DisableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testDemoteMainUser_flagDisabled() {
        assumeMainUserIsNotTheSystemUser();
        UserInfo mainUser = createMainUser();
        int mainUserId = mainUser.id;
        int flagsBefore = mainUser.flags;

        boolean demoted = mUms.demoteMainUser();

        // assert call itself
        expect.withMessage("demoteMainUser()").that(demoted).isFalse();

        // assert getMainUserId()
        expect.withMessage("getMainUserId()").that(mUms.getMainUserId())
                .isEqualTo(mainUserId);

        // assert flags didn't change
        UserInfo unchangedMainUser = mUms.getUserInfo(mainUserId);
        assertWithMessage("getUserInfo(%s)", mainUserId).that(unchangedMainUser).isNotNull();
        Log.d(TAG, "Unchanged main user: " + unchangedMainUser);
        int flagsAfter = unchangedMainUser.flags;
        expect.withMessage("flags of user %s after no-demotion (where %s=%s and %s=%s)", mainUserId,
                flagsBefore, flagsToString(flagsBefore),
                flagsAfter, flagsToString(flagsAfter))
                .that(flagsAfter).isEqualTo(flagsBefore);

        // assert journey not logged
        expectUserJourneyNotLogged(mainUserId, USER_JOURNEY_DEMOTE_MAIN_USER);
    }

    @Test
    @DisableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testDemoteMainUser_whenItsSystemUser_flagDisabled() {
        // Should behave the same as when it's enabled (i.e. be a no-op)
        testDemoteMainUser_whenItsSystemUser();
    }

    // TODO(b/419086491): remove constants and helper below when deprecated methods that use them
    // (getUsersWithUnresolvedNames() and getUsersInternal()) are removed)

    private static final boolean EXCLUDE_PARTIAL = true;
    private static final boolean EXCLUDE_DYING = true;
    private static final boolean RESOLVE_NULL_NAMES = true;
    private static final boolean DONT_EXCLUDE_PARTIAL = false;
    private static final boolean DONT_EXCLUDE_DYING = false;
    private static final boolean DONT_RESOLVE_NULL_NAMES = false;

    private void assertDefaultSystemUserName(List<UserInfo> users) {
        var systemUser = getExistingUser(users, USER_SYSTEM);
        if (systemUser != null) {
            expect.withMessage("name on system user (%s)", systemUser.toFullString())
                    .that(systemUser.name)
                    .isEqualTo(mUms.getName(systemUser));
        }
    }

    private void assertDefaultNewUserName(List<UserInfo> users, int... userIds) {
        Set<Integer> userIdsSet = Arrays.stream(userIds).boxed().collect(Collectors.toSet());
        var newUserName = mUms.getUnnamedUserName();
        for (var user : users) {
            if (userIdsSet.contains(user.id)) {
                expect.withMessage("name on user (%s)", user.toFullString())
                        .that(user.name)
                        .isEqualTo(newUserName);
            }
        }
    }

    @Test
    public void testGetUsersWithUnresolvedNames() {
        var headlessSystemUser = addUser(new UserInfo(USER_SYSTEM, /* name= */ null, FLAG_ADMIN));
        var adminUser = addUser(new UserInfo(/* id= */ 4, /* name= */ null,
                FLAG_FULL | FLAG_ADMIN));
        var nonAdminUser = addUser(new UserInfo(/* id= */ 8, /* name= */ null, FLAG_FULL));
        var partialUser = addUser(new UserInfo(/* id= */ 15, /* name= */ null, FLAG_FULL));
        partialUser.partial = true;
        // NOTE: user pre-creation is not supported anymore, so it won't be returned
        var preCreatedUser = addUser(new UserInfo(/* id= */ 16, /* name= */ null, FLAG_FULL));
        preCreatedUser.preCreated = true;
        var dyingUser = addDyingUser(new UserInfo(/* id= */ 23, /* name= */ null, FLAG_FULL));
        var namedUser = addUser(new UserInfo(/* id= */ 42, "Bond, James Bond", FLAG_FULL));

        expect.withMessage("getUsersWithUnresolvedNames(%s, %s)", EXCLUDE_PARTIAL, EXCLUDE_DYING)
                .that(mUms.getUsersWithUnresolvedNames(EXCLUDE_PARTIAL, EXCLUDE_DYING))
                .containsExactly(headlessSystemUser, adminUser, nonAdminUser, namedUser);
        expect.withMessage("getUsersWithUnresolvedNames(%s, %s)", DONT_EXCLUDE_PARTIAL,
                EXCLUDE_DYING)
                .that(mUms.getUsersWithUnresolvedNames(DONT_EXCLUDE_PARTIAL, EXCLUDE_DYING))
                .containsExactly(headlessSystemUser, adminUser, nonAdminUser, namedUser,
                        partialUser);
        expect.withMessage("getUsersWithUnresolvedNames(%s, %s)", DONT_EXCLUDE_PARTIAL,
                DONT_EXCLUDE_DYING)
                .that(mUms.getUsersWithUnresolvedNames(DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING))
                .containsExactly(headlessSystemUser, adminUser, nonAdminUser, namedUser,
                        partialUser, dyingUser);
        expect.withMessage("getUsersWithUnresolvedNames(%s, %s)", DONT_EXCLUDE_PARTIAL,
                DONT_EXCLUDE_DYING)
                .that(mUms.getUsersWithUnresolvedNames(DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING))
                .containsExactly(headlessSystemUser, adminUser, nonAdminUser, namedUser,
                        partialUser, dyingUser);
    }

    @Test
    public void testGetUsersInternal_nonHsum() {
        var fullSystemUser =
                addUser(new UserInfo(USER_SYSTEM, /* name= */ null, FLAG_FULL | FLAG_ADMIN));
        testGetUsersInternal(fullSystemUser);
    }

    @Test
    public void testGetUsersInternal_hsum() {
        var headlessSystemUser = addUser(new UserInfo(USER_SYSTEM, /* name= */ null, FLAG_ADMIN));
        testGetUsersInternal(headlessSystemUser);
    }

    private void testGetUsersInternal(UserInfo systemUser) {
        var adminUser = addUser(new UserInfo(/* id= */ 4, /* name= */ null,
                FLAG_FULL | FLAG_ADMIN));
        var nonAdminUser = addUser(new UserInfo(/* id= */ 8, /* name= */ null, FLAG_FULL));
        var partialUser = addUser(new UserInfo(/* id= */ 15, /* name= */ null, FLAG_FULL));
        partialUser.partial = true;
        // NOTE: user pre-creation is not supported anymore, so it won't be returned
        var preCreatedUser = addUser(new UserInfo(/* id= */ 16, /* name= */ null, FLAG_FULL));
        preCreatedUser.preCreated = true;
        var dyingUser = addDyingUser(new UserInfo(/* id= */ 23, /* name= */ null, FLAG_FULL));
        var namedUser = addUser(new UserInfo(/* id= */ 42, "Bond, James Bond", FLAG_FULL));

        expect.withMessage("getUsersInternal(%s, %s, %s)", EXCLUDE_PARTIAL, EXCLUDE_DYING,
                DONT_RESOLVE_NULL_NAMES)
                .that(mUms.getUsersInternal(EXCLUDE_PARTIAL, EXCLUDE_DYING,
                        DONT_RESOLVE_NULL_NAMES))
                .containsExactly(systemUser, adminUser, nonAdminUser, namedUser);
        expect.withMessage("getUsersInternal(%s, %s, %s)", DONT_EXCLUDE_PARTIAL, EXCLUDE_DYING,
                DONT_RESOLVE_NULL_NAMES)
                .that(mUms.getUsersInternal(DONT_EXCLUDE_PARTIAL, EXCLUDE_DYING,
                        DONT_RESOLVE_NULL_NAMES))
                .containsExactly(systemUser, adminUser, nonAdminUser, namedUser,
                        partialUser);
        expect.withMessage("getUsersInternal(%s, %s, %s)", DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING,
                DONT_RESOLVE_NULL_NAMES)
                .that(mUms.getUsersInternal(DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING,
                        DONT_RESOLVE_NULL_NAMES))
                .containsExactly(systemUser, adminUser, nonAdminUser, namedUser,
                        partialUser, dyingUser);
        expect.withMessage("getUsersInternal(%s, %s, %s)", DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING,
                DONT_RESOLVE_NULL_NAMES)
                .that(mUms.getUsersInternal(DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING,
                        DONT_RESOLVE_NULL_NAMES))
                .containsExactly(systemUser, adminUser, nonAdminUser, namedUser,
                        partialUser, dyingUser);

        // NOTE: cannot check for users with resolved names on containsExactly() because
        // UserInfo doesn't implement equals, hence checks below need to explicitly check them
        List<UserInfo> resolvedNameUsers;

        resolvedNameUsers = mUms.getUsersInternal(EXCLUDE_PARTIAL, EXCLUDE_DYING,
                RESOLVE_NULL_NAMES);
        expect.withMessage("getUsersInternal(%s, %s, %s)", EXCLUDE_PARTIAL, EXCLUDE_DYING,
                RESOLVE_NULL_NAMES)
                .that(resolvedNameUsers)
                .hasSize(4);
        expect.withMessage("getUsersInternal(%s, %s, %s)", EXCLUDE_PARTIAL, EXCLUDE_DYING,
                RESOLVE_NULL_NAMES)
                .that(resolvedNameUsers)
                .contains(namedUser);
        assertDefaultSystemUserName(resolvedNameUsers);
        assertDefaultNewUserName(resolvedNameUsers, adminUser.id, nonAdminUser.id);

        resolvedNameUsers = mUms.getUsersInternal(DONT_EXCLUDE_PARTIAL, EXCLUDE_DYING,
                RESOLVE_NULL_NAMES);
        expect.withMessage("getUsersInternal(%s, %s, %s)", DONT_EXCLUDE_PARTIAL, EXCLUDE_DYING,
                RESOLVE_NULL_NAMES)
                .that(resolvedNameUsers)
                .hasSize(5);
        expect.withMessage("getUsersInternal(%s, %s, %s)", DONT_EXCLUDE_PARTIAL, EXCLUDE_DYING,
                RESOLVE_NULL_NAMES)
                .that(resolvedNameUsers)
                .contains(namedUser);
        assertDefaultSystemUserName(resolvedNameUsers);
        assertDefaultNewUserName(resolvedNameUsers, adminUser.id, nonAdminUser.id, partialUser.id);

        resolvedNameUsers = mUms.getUsersInternal(DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING,
                RESOLVE_NULL_NAMES);
        expect.withMessage("getUsersInternal(%s, %s, %s)", DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING,
                RESOLVE_NULL_NAMES)
                .that(resolvedNameUsers)
                .hasSize(6);
        expect.withMessage("getUsersInternal(%s, %s, %s)", DONT_EXCLUDE_PARTIAL,
                DONT_EXCLUDE_DYING, RESOLVE_NULL_NAMES)
                .that(resolvedNameUsers)
                .contains(namedUser);
        assertDefaultSystemUserName(resolvedNameUsers);
        assertDefaultNewUserName(resolvedNameUsers, adminUser.id, nonAdminUser.id, partialUser.id,
                dyingUser.id);

        resolvedNameUsers = mUms.getUsersInternal(DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING,
                RESOLVE_NULL_NAMES);
        expect.withMessage("getUsersInternal(%s, %s, %s)", DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING,
                RESOLVE_NULL_NAMES)
                .that(resolvedNameUsers)
                .hasSize(6);
        expect.withMessage("getUsersInternal(%s, %s, %s)", DONT_EXCLUDE_PARTIAL, DONT_EXCLUDE_DYING,
                RESOLVE_NULL_NAMES)
                .that(resolvedNameUsers)
                .contains(namedUser);
        assertDefaultSystemUserName(resolvedNameUsers);
        assertDefaultNewUserName(resolvedNameUsers, adminUser.id, nonAdminUser.id, partialUser.id,
                dyingUser.id);
    }

    @Test
    @EnableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testSetMainUser() {
        assumeDoesntHaveMainUser();
        var adminUser = createAdminUser();
        int userId = adminUser.id;
        // Make sure the new user is not the main user
        expect.withMessage("getMainUser() before").that(mUms.getMainUserId()).isNotEqualTo(userId);

        expect.withMessage("setMainUser(%s)", userId).that(mUms.setMainUser(userId)).isTrue();

        // Make sure it changed
        expect.withMessage("getMainUser() after").that(mUms.getMainUserId()).isEqualTo(userId);

        // assert journey logged
        expectUserJourneyLogged(userId, USER_JOURNEY_PROMOTE_MAIN_USER);
    }

    @Test
    @EnableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testSetMainUser_hasMainUser() {
        var mainUserId = assumeHasMainUser();
        var adminUser = createAdminUser();
        int userId = adminUser.id;

        expect.withMessage("setMainUser(%s)", userId).that(mUms.setMainUser(userId)).isFalse();

        // Make sure it didn't change
        expect.withMessage("getMainUser()").that(mUms.getMainUserId()).isEqualTo(mainUserId);

        // assert journey not logged
        expectUserJourneyNotLogged(userId, USER_JOURNEY_PROMOTE_MAIN_USER);
    }

    @Test
    @EnableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testSetMainUser_userNotFound() {
        assumeDoesntHaveMainUser();
        int userId = OTHER_USER_ID;

        expect.withMessage("setMainUser(%s)", userId).that(mUms.setMainUser(userId)).isFalse();

        // Make sure it didn't change
        expect.withMessage("getMainUser()").that(mUms.getMainUserId())
                .isEqualTo(UserHandle.USER_NULL);

        // assert journey not logged
        expectUserJourneyNotLogged(userId, USER_JOURNEY_PROMOTE_MAIN_USER);
    }

    @Test
    @EnableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testSetMainUser_userNotAdmin() {
        assumeDoesntHaveMainUser();
        var regularUser = createRegularUser();
        int userId = regularUser.id;

        expect.withMessage("setMainUser(%s)", userId).that(mUms.setMainUser(userId)).isFalse();

        // Make sure it didn't change
        expect.withMessage("getMainUser()").that(mUms.getMainUserId())
                .isEqualTo(UserHandle.USER_NULL);

        // assert journey not logged
        expectUserJourneyNotLogged(userId, USER_JOURNEY_PROMOTE_MAIN_USER);
    }

    @Test
    @DisableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testSetMainUser_flagDisabled() {
        assumeDoesntHaveMainUser();
        var adminUser = createAdminUser();
        int userId = adminUser.id;
        // Make sure the new user is not the main user
        expect.withMessage("getMainUser() before").that(mUms.getMainUserId()).isNotEqualTo(userId);

        expect.withMessage("setMainUser(%s)", userId).that(mUms.setMainUser(userId)).isFalse();

        // Make sure it didn't change
        expect.withMessage("getMainUser() after").that(mUms.getMainUserId())
                .isEqualTo(UserHandle.USER_NULL);

        // assert journey logged
        expectUserJourneyNotLogged(userId, USER_JOURNEY_PROMOTE_MAIN_USER);
    }

    @Test
    @DisableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testSetMainUser_hasMainUser_flagDisabled() {
        // Should behave the same as when it's enabled (i.e. be a no-op)
        testSetMainUser_hasMainUser();
    }

    @Test
    @DisableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testSetMainUser_userNotFound_flagDisabled() {
        // Should behave the same as when it's enabled (i.e. be a no-op)
        testSetMainUser_userNotFound();
    }

    @Test
    @DisableFlags(FLAG_DEMOTE_MAIN_USER)
    public void testSetMainUser_userNotAdmin_flagDisabled() {
        // Should behave the same as when it's enabled (i.e. be a no-op)
        testSetMainUser_userNotAdmin();
    }

    @Test
    public void testIsLastFullAdminUser_nonHsum_targetNotSystemUser_returnsFalse() {
        setSystemUserHeadless(false);
        addAdminUser(USER_ID);

        assertThat(mUms.isLastFullAdminUserLU(mUsers.get(USER_ID).info)).isFalse();
    }

    @Test
    public void testIsLastFullAdminUser_hsum_targetNotAdmin_returnsFalse() {
        setSystemUserHeadless(true);
        addSecondaryUser(USER_ID); // USER_ID is full, not admin
        addAdminUser(OTHER_USER_ID); // OTHER_USER_ID is full, admin

        assertThat(mUms.isLastFullAdminUserLU(mUsers.get(USER_ID).info)).isFalse();
    }

    @Test
    public void testIsLastFullAdminUser_hsum_targetAdmin_otherFullAdminExists_returnsFalse() {
        setSystemUserHeadless(true);
        addAdminUser(USER_ID); // USER_ID is full, admin (target)
        addAdminUser(OTHER_USER_ID); // OTHER_USER_ID is full, admin

        expect.withMessage("isLastFullAdminUserLU(%s)", USER_ID)
                .that(mUms.isLastFullAdminUserLU(mUsers.get(USER_ID).info)).isFalse();
        expect.withMessage("isLastFullAdminUserLU(%s)", OTHER_USER_ID)
                .that(mUms.isLastFullAdminUserLU(mUsers.get(OTHER_USER_ID).info)).isFalse();
    }

    @Test
    public void testIsLastFullAdminUser_hsum_targetAdmin_systemUserNotFull_returnsTrue() {
        // Ensure system user (0) is admin, but not full
        setSystemUserHeadless(true);
        addAdminUser(USER_ID); // USER_ID is full, admin (target)

        assertThat(mUms.isLastFullAdminUserLU(mUsers.get(USER_ID).info)).isTrue();
    }

    @Test
    public void testIsLastFullAdminUser_hsum_targetAdmin_otherFullNotAdmin_returnsTrue() {
        setSystemUserHeadless(true);
        addAdminUser(USER_ID); // USER_ID is full, admin (target)
        addSecondaryUser(OTHER_USER_ID); // OTHER_USER_ID is full, not admin

        assertThat(mUms.isLastFullAdminUserLU(mUsers.get(USER_ID).info)).isTrue();
    }

    @Test
    public void testIsLastFullAdminUser_hsum_targetAdmin_otherFullAdminIsRemoving_returnsTrue() {
        setSystemUserHeadless(true);
        addAdminUser(USER_ID); // USER_ID is full, admin (target)
        addAdminUser(OTHER_USER_ID); // OTHER_USER_ID is full, admin
        mUms.addRemovingUserId(OTHER_USER_ID); // Mark OTHER_USER_ID as dying

        // OTHER_USER_ID will be excluded by getUsersInternal
        assertThat(mUms.isLastFullAdminUserLU(mUsers.get(USER_ID).info)).isTrue();
    }

    @Test
    public void testIsLastFullAdminUser_hsum_targetAdmin_otherFullAdminIsPartial_returnsTrue() {
        setSystemUserHeadless(true);
        addAdminUser(USER_ID); // USER_ID is full, admin (target)
        addAdminUser(OTHER_USER_ID); // OTHER_USER_ID is full, admin
        mUsers.get(OTHER_USER_ID).info.partial = true; // Mark OTHER_USER_ID as partial

        // OTHER_USER_ID will be excluded by getUsersInternal
        assertThat(mUms.isLastFullAdminUserLU(mUsers.get(USER_ID).info)).isTrue();
    }

    @Test
    public void testIsLastFullAdminUser_targetAdmin_otherFullAdminIsPreCreated_returnsTrue() {
        // Ensure system user (0) is full admin
        setSystemUserHeadless(false);
        mUsers.get(UserHandle.USER_SYSTEM).info.preCreated = true; // Mark system user as preCreated
        addAdminUser(USER_ID); // USER_ID is full, admin (target)

        // OTHER_USER_ID will be excluded by getUsersInternal
        assertThat(mUms.isLastFullAdminUserLU(mUsers.get(USER_ID).info)).isTrue();
    }

    @Test
    public void
            testIsLastFullAdminUser_systemUserIsFullAdmin_targetIsOtherFullAdmin_returnsFalse() {
        // Ensure system user (0) is full admin
        setSystemUserHeadless(false);

        addAdminUser(USER_ID); // USER_ID is full, admin (target)

        assertThat(mUms.isLastFullAdminUserLU(mUsers.get(USER_ID).info)).isFalse();
    }

    @Test
    public void testIsLastFullAdminUser_systemUserIsFullAdmin_targetIsSystemUser_returnsTrue() {
        // Ensure system user (0) is full admin
        setSystemUserHeadless(false);

        // Add another non-admin full user to ensure system is not the *only* user
        addSecondaryUser(USER_ID);

        assertThat(mUms.isLastFullAdminUserLU(mUsers.get(UserHandle.USER_SYSTEM).info)).isTrue();
    }

    @Test
    public void testIsLastFullAdminUser_targetAdmin_otherFullAdminIsSystemUser_returnsFalse() {
        // Ensure system user (0) is full admin
        setSystemUserHeadless(false);

        addAdminUser(USER_ID); // USER_ID is full, admin (target)

        assertThat(mUms.isLastFullAdminUserLU(mUsers.get(USER_ID).info)).isFalse();
    }

    @Test
    public void testSetUserAdmin() {
        addSecondaryUser(USER_ID);

        mUms.setUserAdmin(USER_ID);

        assertThat(mUsers.get(USER_ID).info.isAdmin()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_UNICORN_MODE_REFACTORING_FOR_HSUM_READ_ONLY)
    public void testSetUserAdminThrowsSecurityException() {
        addSecondaryUser(USER_ID);
        addSecondaryUser(OTHER_USER_ID);
        mockCallingUserId(OTHER_USER_ID);

        // 1. Target User Restriction
        mUms.setUserRestriction(UserManager.DISALLOW_GRANT_ADMIN, true, USER_ID);
        assertThrows(SecurityException.class, () -> mUms.setUserAdmin(USER_ID));

        // 2. Current User Restriction
        mUms.setUserRestriction(UserManager.DISALLOW_GRANT_ADMIN, false, USER_ID);
        mUms.setUserRestriction(UserManager.DISALLOW_GRANT_ADMIN, true, OTHER_USER_ID);
        assertThrows(SecurityException.class, () -> mUms.setUserAdmin(USER_ID));
    }

    @Test
    public void testSetUserAdminFailsForGuest() {
        addGuestUser(USER_ID);

        mUms.setUserAdmin(USER_ID);

        assertThat(mUsers.get(USER_ID).info.isAdmin()).isFalse();
    }

    @Test
    public void testSetUserAdminFailsForProfile() {
        addSecondaryUser(PARENT_USER_ID);
        addProfile(PROFILE_USER_ID, PARENT_USER_ID, USER_TYPE_PROFILE_MANAGED);

        mUms.setUserAdmin(PROFILE_USER_ID);

        assertThat(mUsers.get(PROFILE_USER_ID).info.isAdmin()).isFalse();
    }

    @Test
    public void testSetUserAdminFailsForRestrictedProfile() {
        addRestrictedProfile(USER_ID);

        mUms.setUserAdmin(USER_ID);

        assertThat(mUsers.get(USER_ID).info.isAdmin()).isFalse();
    }

    @Test
    public void testRevokeUserAdmin() {
        addAdminUser(USER_ID);

        mUms.revokeUserAdmin(USER_ID);

        assertThat(mUsers.get(USER_ID).info.isAdmin()).isFalse();
    }

    @Test
    public void testRevokeUserAdminFromNonAdmin() {
        addSecondaryUser(USER_ID);

        mUms.revokeUserAdmin(USER_ID);

        assertThat(mUsers.get(USER_ID).info.isAdmin()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_UNICORN_MODE_REFACTORING_FOR_HSUM_READ_ONLY)
    public void testRevokeUserAdminThrowsSecurityException() {
        addAdminUser(USER_ID);
        addSecondaryUser(OTHER_USER_ID);
        mockCallingUserId(OTHER_USER_ID);

        // 1. Target User Restriction
        mUms.setUserRestriction(UserManager.DISALLOW_GRANT_ADMIN, true, USER_ID);
        assertThrows(SecurityException.class, () -> mUms.revokeUserAdmin(USER_ID));

        // 2. Current User Restriction
        mUms.setUserRestriction(UserManager.DISALLOW_GRANT_ADMIN, false, USER_ID);
        mUms.setUserRestriction(UserManager.DISALLOW_GRANT_ADMIN, true, OTHER_USER_ID);
        assertThrows(SecurityException.class, () -> mUms.revokeUserAdmin(USER_ID));
    }

    @Test
    public void testRevokeUserAdminFailsForSystemUser() {
        mUms.revokeUserAdmin(UserHandle.USER_SYSTEM);

        assertThat(mUsers.get(UserHandle.USER_SYSTEM).info.isAdmin()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_DISALLOW_REMOVING_LAST_ADMIN_USER)
    public void testRevokeUserAdminFailsForLastFullAdmin() {
        mockDisallowRemovingLastAdminUser(true);
        // Mark system user as headless so that it is not a full admin user.
        setSystemUserHeadless(true);
        addAdminUser(USER_ID);

        mUms.revokeUserAdmin(USER_ID);

        assertThat(mUsers.get(USER_ID).info.isAdmin()).isTrue();
    }

    // NOTE: tests for getApplicationRestrictionsForUser were added to check when DPMI is null, so
    // they don't encompass all scenarios (like when FLAG_APP_RESTRICTIONS_COEXISTENCE is not set)

    @Test
    public void testGetApplicationRestrictionsForUser_invalidUser() {
        mockCallingUserId(USER_ID);

        assertThrows(SecurityException.class, () -> mUms
                .getApplicationRestrictionsForUser(mRealContext.getPackageName(), OTHER_USER_ID));
    }

    @Test
    public void testGetApplicationRestrictionsForUser_differentPackage() {
        // Should throw because it's not the same as the calling uid package
        assertThrows(SecurityException.class,
                () -> mUms.getApplicationRestrictions("Age, the name is Pack Age"));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_RESTRICTIONS_COEXISTENCE)
    public void testGetApplicationRestrictionsForUser_flagEnabled_noDPMI() {
        mockGetLocalService(DevicePolicyManagerInternal.class, null);

        var result = mUms.getApplicationRestrictions(mRealContext.getPackageName());

        assertThat(result).isSameInstanceAs(Bundle.EMPTY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_RESTRICTIONS_COEXISTENCE)
    public void testGetApplicationRestrictionsForUser_flagEnabled_noRestrictions() {
        String pkg = mRealContext.getPackageName();
        mockCallingUserId(USER_ID);
        mockDpmiGetApplicationRestrictionsPerAdminForUser(pkg, USER_ID);
        mockGetLocalService(DevicePolicyManagerInternal.class, mDevicePolicyManagerInternal);

        var result = mUms.getApplicationRestrictions(pkg);

        assertThat(result).isSameInstanceAs(Bundle.EMPTY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_RESTRICTIONS_COEXISTENCE)
    public void testGetApplicationRestrictionsForUser_flagEnabled_multipleRestrictions() {
        String pkg = mRealContext.getPackageName();
        Bundle bundle1 = new Bundle();
        Bundle bundle2 = new Bundle();
        mockCallingUserId(USER_ID);
        mockDpmiGetApplicationRestrictionsPerAdminForUser(pkg, USER_ID, bundle1, bundle2);
        mockGetLocalService(DevicePolicyManagerInternal.class, mDevicePolicyManagerInternal);

        var result = mUms.getApplicationRestrictions(pkg);

        assertThat(result).isSameInstanceAs(bundle1);
    }

    /**
     * Returns true if the user's XML file has Default restrictions
     * @param userId Id of the user.
     */
    private boolean hasRestrictionsInUserXMLFile(int userId)
            throws IOException, XmlPullParserException {
        FileInputStream is = new FileInputStream(getUserXmlFile(userId));
        final TypedXmlPullParser parser = Xml.resolvePullParser(is);

        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            // Skip
        }

        if (type != XmlPullParser.START_TAG) {
            return false;
        }

        int outerDepth = parser.getDepth();
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (TAG_RESTRICTIONS.equals(parser.getName())) {
                return true;
            }
        }

        return false;
    }

    private File getUserXmlFile(int userId) {
        File file = new File(mTestDir, USER_INFO_DIR);
        return new File(file, userId + XML_SUFFIX);
    }

    private String generateLongString() {
        String partialString = "Test Name Test Name Test Name Test Name Test Name Test Name Test "
                + "Name Test Name Test Name Test Name "; //String of length 100
        StringBuilder resultString = new StringBuilder();
        for (int i = 0; i < 660; i++) {
            resultString.append(partialString);
        }
        return resultString.toString();
    }

    private void removeNonSystemUsers() {
        for (UserInfo user : mUms.getUsers(true)) {
            if (!user.getUserHandle().isSystem()) {
                mUms.removeUserInfo(user.id);
            }
        }
    }

    private void resetUserSwitcherEnabled() {
        mUms.putUserInfo(new UserInfo(USER_ID, "Test User", 0));
        mUms.setUserRestriction(DISALLOW_USER_SWITCH, false, USER_ID);
        mockUserSwitcherEnabled(/* enabled= */ true);
        mockDeviceDemoMode(/* enabled= */ false);
        mockMaxSupportedUsers(/* maxUsers= */ 8);
        mockShowMultiuserUI(/* show= */ true);
    }

    private void mockUserSwitcherEnabled(boolean enabled) {
        doReturn(enabled ? 1 : 0).when(() -> Settings.Global.getInt(
                any(), eq(android.provider.Settings.Global.USER_SWITCHER_ENABLED), anyInt()));
    }

    private void mockProvisionedDevice(boolean isProvisionedDevice) {
        doReturn(isProvisionedDevice ? 1 : 0).when(() -> Settings.Global.getInt(
                any(), eq(android.provider.Settings.Global.DEVICE_PROVISIONED), anyInt()));
    }

    private void mockIsLowRamDevice(boolean isLowRamDevice) {
        doReturn(isLowRamDevice).when(ActivityManager::isLowRamDeviceStatic);
    }

    private void mockDeviceDemoMode(boolean enabled) {
        doReturn(enabled ? 1 : 0).when(() -> Settings.Global.getInt(
                any(), eq(android.provider.Settings.Global.DEVICE_DEMO_MODE), anyInt()));
    }

    private void mockMaxSupportedUsers(int maxUsers) {
        doReturn(maxUsers).when(() ->
                SystemProperties.getInt(eq("fw.max_users"), anyInt()));
    }

    private void mockShowMultiuserUI(boolean show) {
        doReturn(show).when(() ->
                SystemProperties.getBoolean(eq("fw.show_multiuserui"), anyBoolean()));
    }

    private void mockAutoLockForPrivateSpace(int val) {
        doReturn(val).when(() ->
                Settings.Secure.getIntForUser(any(), eq(Settings.Secure.PRIVATE_SPACE_AUTO_LOCK),
                        anyInt(), anyInt()));
    }

    private void mockCurrentUser(@UserIdInt int userId) {
        mockGetLocalService(ActivityManagerInternal.class, mActivityManagerInternal);

        when(mActivityManagerInternal.getCurrentUserId()).thenReturn(userId);
    }

    private void mockCurrentAndTargetUser(@UserIdInt int currentUserId,
            @UserIdInt int targetUserId) {
        mockGetLocalService(ActivityManagerInternal.class, mActivityManagerInternal);

        when(mActivityManagerInternal.getCurrentAndTargetUserIds())
                .thenReturn(new Pair<>(currentUserId, targetUserId));
    }

    private <T> void mockGetLocalService(Class<T> serviceClass, T service) {
        doReturn(service).when(() -> LocalServices.getService(serviceClass));
    }

    private void mockCanSwitchToHeadlessSystemUser(boolean canSwitch) {
        boolean previousValue = mSpyResources
                .getBoolean(com.android.internal.R.bool.config_canSwitchToHeadlessSystemUser);

        Log.d(TAG, "mockCanSwitchToHeadlessSystemUser(): will return " + canSwitch + " instad of "
                + previousValue);
        doReturn(canSwitch)
                .when(mSpyResources)
                .getBoolean(com.android.internal.R.bool.config_canSwitchToHeadlessSystemUser);
    }

    private void mockHsumBootStrategy(@BootStrategy int strategy) {
        int previousValue = mSpyResources
                .getInteger(com.android.internal.R.integer.config_hsumBootStrategy);
        Log.d(TAG,
                "mockHsumBootStrategy(): will return " + strategy + " instead of " + previousValue);
        doReturn(strategy)
                .when(mSpyResources)
                .getInteger(com.android.internal.R.integer.config_hsumBootStrategy);
    }

    private void mockDisallowRemovingLastAdminUser(boolean disallow) {
        boolean previousValue = mSpyResources
                .getBoolean(com.android.internal.R.bool.config_disallowRemovingLastAdminUser);
        Log.d(TAG, "mockDisallowRemovingLastAdminUser(): will return " + disallow + " instead of "
                + previousValue);
        doReturn(disallow)
                .when(mSpyResources)
                .getBoolean(com.android.internal.R.bool.config_disallowRemovingLastAdminUser);
    }

    private void mockUserIsInCall(boolean isInCall) {
        when(mTelecomManager.isInCall()).thenReturn(isInCall);
    }

    private void mockCallingUserId(@UserIdInt int userId) {
        doReturn(userId).when(UserHandle::getCallingUserId);
    }

    private void mockDpmiGetApplicationRestrictionsPerAdminForUser(String pkgName,
            @UserIdInt int userId, Bundle...bundles) {
        List<Bundle> list = Arrays.asList(bundles);

        Log.d(TAG, "mockDpmiGetApplicationRestrictionsPerAdminForAnyUser(" + pkgName
                + ", " + userId + "): will return " + list);
        when(mDevicePolicyManagerInternal
                .getApplicationRestrictionsPerAdminForUser(pkgName, userId))
                        .thenReturn(list);
    }

    private void expectUserJourneyLogged(@UserIdInt int userId, @UserJourney int journey) {
        verify(mUserJourneyLogger).logUserJourneyBegin(userId, journey);
    }

    private void expectUserJourneyNotLogged(@UserIdInt int userId, @UserJourney int journey) {
        verify(mUserJourneyLogger, never()).logUserJourneyBegin(userId, journey);
    }

    private void addDefaultProfileAndParent() {
        addSecondaryUser(PARENT_USER_ID);
        addProfile(PROFILE_USER_ID, PARENT_USER_ID, /* userType= */ null);
    }

    private void addProfile(@UserIdInt int profileId, @UserIdInt int parentId, String userType) {
        TestUserData profileData = new TestUserData(profileId);
        profileData.info.flags = UserInfo.FLAG_PROFILE;
        profileData.info.profileGroupId = parentId;
        profileData.info.userType = userType;
        addUserData(profileData);

        assertWithMessage("user (id %s) retrieved after being added is not an admin", profileId)
            .that(mUsers.get(profileId).info.isAdmin()).isFalse();
    }

    private void addRestrictedProfile(@UserIdInt int profileId) {
        TestUserData profileData = new TestUserData(profileId);
        profileData.info.flags = UserInfo.FLAG_FULL;
        profileData.info.userType = USER_TYPE_FULL_RESTRICTED;
        addUserData(profileData);

        assertWithMessage("user (id %s) retrieved after being added is not an admin", profileId)
            .that(mUsers.get(profileId).info.isAdmin()).isFalse();
    }

    /** Adds a full secondary non-admin user. */
    private void addSecondaryUser(@UserIdInt int userId) {
        TestUserData userData = new TestUserData(userId);
        userData.info.flags = UserInfo.FLAG_FULL;
        userData.info.userType = USER_TYPE_FULL_SECONDARY;
        addUserData(userData);

        assertWithMessage("user (id %s) retrieved after being added is not an admin", userId)
            .that(mUsers.get(userId).info.isAdmin()).isFalse();
    }

    private void addGuestUser(@UserIdInt int userId) {
        TestUserData userData = new TestUserData(userId);
        userData.info.flags = UserInfo.FLAG_GUEST;
        userData.info.userType = UserManager.USER_TYPE_FULL_GUEST;
        addUserData(userData);

        assertWithMessage("user (id %s) retrieved after being added is not an admin", userId)
            .that(mUsers.get(userId).info.isAdmin()).isFalse();
    }

    private void assumeMainUserIsNotTheSystemUser() {
        var mainUserId = mUms.getMainUserId();
        assumeFalse("main user is the system user", mainUserId == USER_SYSTEM);
    }

    private void assumeMainUserIsTheSystemUser() {
        var mainUserId = mUms.getMainUserId();
        assumeTrue("main user (" + mainUserId + ") is not the system user",
                mainUserId == USER_SYSTEM);
    }

    @UserIdInt
    private int assumeHasMainUser() {
        var mainUserId = mUms.getMainUserId();
        assumeFalse("main user exists (id=" + mainUserId + ")", mainUserId == UserHandle.USER_NULL);
        return mainUserId;
    }

    private void assumeDoesntHaveMainUser() {
        var mainUserId = mUms.getMainUserId();
        assumeTrue("main user doesn't exsit", mainUserId == UserHandle.USER_NULL);
    }

    private UserInfo createMainUser() {
        UserInfo user = mUms.createUserWithThrow("The Name is User, Main User",
                USER_TYPE_FULL_SECONDARY,
                UserInfo.FLAG_ADMIN | UserInfo.FLAG_FULL | UserInfo.FLAG_MAIN);
        Log.d(TAG, "created main user: " + user);
        return user;
    }

    private UserInfo createAdminUser() {
        UserInfo user = mUms.createUserWithThrow("The Name is Admin, Admin User",
                USER_TYPE_FULL_SECONDARY,
                UserInfo.FLAG_ADMIN | UserInfo.FLAG_FULL);
        Log.d(TAG, "created admin user: " + user);
        return user;
    }

    private UserInfo createRegularUser() {
        UserInfo user = mUms.createUserWithThrow("The Name is Regular, Regular User",
                USER_TYPE_FULL_SECONDARY, UserInfo.FLAG_FULL);
        Log.d(TAG, "created regular user: " + user);
        return user;
    }

    private UserInfo getSystemUser() {
        // Primary user is deprecated, so in theory we should interact through all users and check
        // which has id 0. But pragramtically speaking, this is simpler...
        return mUms.getPrimaryUser();
    }

    private UserInfo addUser(UserInfo user) {
        TestUserData userData = new TestUserData(user);
        addUserData(userData);
        return user;
    }

    private void addAdminUser(@UserIdInt int userId) {
        TestUserData userData = new TestUserData(userId);
        userData.info.flags = UserInfo.FLAG_FULL | UserInfo.FLAG_ADMIN;
        userData.info.userType = USER_TYPE_FULL_SECONDARY;
        addUserData(userData);

        assertWithMessage("user (id %s) retrieved after being added is an admin", userId)
            .that(mUsers.get(userId).info.isAdmin()).isTrue();
    }

    private UserInfo addDyingUser(UserInfo user) {
        addUser(user);
        mUms.addRemovingUserId(user.id);
        return user;
    }

    /**
     * Gets the user with the given id.
     *
     * <p>If not found, adds a failure on {@link #expect} and returns {@code null}.
     */
    @Nullable
    private UserInfo getExistingUser(Collection<UserInfo> users, @UserIdInt int userId) {
        for (UserInfo user : users) {
            if (user.id == userId) {
                return user;
            }
        }
        expect.withMessage("Didn't find user with id %s on %s", userId, users).fail();
        return null;
    }

    private void startDefaultProfile() {
        startUser(PROFILE_USER_ID);
    }

    private void stopDefaultProfile() {
        stopUser(PROFILE_USER_ID);
    }

    private void startUser(@UserIdInt int userId) {
        setUserState(userId, UserState.STATE_RUNNING_UNLOCKED);
    }

    private void stopUser(@UserIdInt int userId) {
        setUserState(userId, UserState.STATE_STOPPING);
    }

    private void setUserState(@UserIdInt int userId, int userState) {
        mUmi.setUserState(userId, userState);
    }

    private void addUserData(TestUserData userData) {
        Log.d(TAG, "Adding " + userData);
        mUsers.put(userData.info.id, userData);
        mUms.putUserInfo(userData.info);
    }

    private void setSystemUserHeadless(boolean headless) {
        UserData systemUser = mUsers.get(USER_SYSTEM);
        if (headless) {
            systemUser.info.flags &= ~UserInfo.FLAG_FULL;
            systemUser.info.userType = UserManager.USER_TYPE_SYSTEM_HEADLESS;
        } else {
            systemUser.info.flags |= UserInfo.FLAG_FULL;
            systemUser.info.userType = UserManager.USER_TYPE_FULL_SYSTEM;
        }
        doReturn(headless).when(() -> UserManager.isHeadlessSystemUserMode());
    }

    private void setLastForegroundTime(@UserIdInt int userId, long timeMillis) {
        UserData userData = mUsers.get(userId);
        userData.mLastEnteredForegroundTimeMillis = timeMillis;
    }

    public boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File item : file.listFiles()) {
                boolean success = deleteRecursive(item);
                if (!success) {
                    return false;
                }
            }
        }
        return file.delete();
    }

    private static final class TestUserData extends UserData {

        @SuppressWarnings("deprecation")
        TestUserData(@UserIdInt int userId) {
            this(new UserInfo());
            info.id = userId;
        }

        TestUserData(UserInfo user) {
            info = user;
        }

        @Override
        public String toString() {
            return "TestUserData[" + info.toFullString() + "]";
        }
    }
}
