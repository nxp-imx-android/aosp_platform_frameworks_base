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

package com.android.server.am;

import static android.os.Process.myUid;
import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;

import static com.android.server.am.psc.OomAdjuster.ALL_CPU_TIME_CAPABILITIES;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.usage.UsageStatsManagerInternal;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.DropBoxManagerInternal;
import com.android.server.LocalServices;
import com.android.server.am.ActivityManagerService.Injector;
import com.android.server.am.ApplicationExitInfoTest.ServiceThreadRule;
import com.android.server.appop.AppOpsService;
import com.android.server.firewall.IntentFirewall;
import com.android.server.uri.UriGrantsManagerInternal;
import com.android.server.wm.ActivityTaskManagerService;
import com.android.server.wm.WindowProcessController;

import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public abstract class BaseServiceTest {
    private static final String TAG = BaseServiceTest.class.getSimpleName();

    protected static final String CALLING_PACKAGE_NAME = "com.example.foo";

    @Rule
    public final ServiceThreadRule mServiceThreadRule = new ServiceThreadRule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private HandlerThread mHandlerThread;

    @Mock
    private AppOpsService mAppOpsService;
    @Mock
    private DropBoxManagerInternal mDropBoxManagerInt;
    @Mock
    private PackageManagerInternal mPackageManagerInt;
    @Mock
    private UsageStatsManagerInternal mUsageStatsManagerInt;
    @Mock
    private UserController mUserControllerInt;
    @Mock
    private UriGrantsManagerInternal mUriGrantsManagerInternalInt;
    @Mock
    private AppErrors mAppErrors;
    @Mock
    private IntentFirewall mIntentFirewall;

    protected ActivityManagerService mAms;
    protected ProcessList mProcessList;
    private ActiveServices mActiveServices;

    protected int mCurrentCallingUid;
    protected int mCurrentCallingPid;

    /**
     * Setup system server objects with just enough mocking to test service related behavior.
     */
    @SuppressWarnings("GuardedBy")
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        final ProcessList realProcessList = new ProcessList();
        mProcessList = spy(realProcessList);

        LocalServices.removeServiceForTest(DropBoxManagerInternal.class);
        LocalServices.addService(DropBoxManagerInternal.class, mDropBoxManagerInt);
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInt);
        doReturn(new ComponentName("", "")).when(mPackageManagerInt).getSystemUiServiceComponent();

        final ActivityManagerService realAms = new ActivityManagerService(
                new TestInjector(mContext), mServiceThreadRule.getThread(), mUserControllerInt);
        final ActivityTaskManagerService realAtm = new ActivityTaskManagerService(mContext);
        realAtm.initialize(null, null, realAms.mProcessStateController, mContext.getMainLooper());
        realAms.mActivityTaskManager = spy(realAtm);
        realAms.mAtmInternal = spy(realAms.mActivityTaskManager.getAtmInternal());

        final CachedAppOptimizer cachedAppOptimizer = spy(realAms.getCachedAppOptimizer());
        doReturn(true).when(cachedAppOptimizer).useFreezer();
        doNothing().when(cachedAppOptimizer).freezeAppAsyncAtEarliestLSP(any());
        doNothing().when(cachedAppOptimizer).freezeAppAsyncLSP(any());
        realAms.setCachedAppOptimizer(cachedAppOptimizer);
        realAms.mProcessStateController = spy(realAms.mProcessStateController);
        realAms.mOomAdjuster = spy(realAms.mOomAdjuster);

        realAms.mPackageManagerInt = mPackageManagerInt;
        realAms.mUsageStatsService = mUsageStatsManagerInt;
        realAms.mUgmInternal = mUriGrantsManagerInternalInt;
        realAms.mAppProfiler = spy(realAms.mAppProfiler);
        realAms.mProcessesReady = true;
        mAms = spy(realAms);
        realProcessList.mService = mAms;

        doReturn(false).when(mPackageManagerInt).filterAppAccess(anyString(), anyInt(), anyInt());
        // Necessary for calling package to match caller uid
        doReturn(myUid()).when(mPackageManagerInt).getPackageUid(
                eq(CALLING_PACKAGE_NAME), anyLong(), anyInt());
        doReturn(true).when(mPackageManagerInt).isSameApp(
                eq(CALLING_PACKAGE_NAME), anyLong(), eq(myUid()), anyInt());
        doReturn(true).when(mIntentFirewall).checkService(any(), any(), anyInt(), anyInt(), any(),
                any());
        doReturn(false).when(mAms.mAtmInternal).hasSystemAlertWindowPermission(anyInt(), anyInt(),
                any());
        doNothing().when(mAms.mAppProfiler).updateLowMemStateLSP(anyInt(), anyInt(), anyLong());
        doReturn(true).when(mUserControllerInt).exists(anyInt());
        doReturn(true).when(mUserControllerInt).hasStartedUserState(anyInt());
    }

    /**  Clean up anything that can affect subsequent tests. */
    public void tearDown() throws Exception {
        LocalServices.removeServiceForTest(DropBoxManagerInternal.class);
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        mHandlerThread.quit();
    }

    protected Intent createServiceIntent(String packageName, String serviceName, int serviceUid) {
        final Intent serviceIntent = new Intent().setClassName(packageName, serviceName);
        final ResolveInfo rInfo = new ResolveInfo();
        rInfo.serviceInfo = makeServiceInfo(serviceName, packageName, serviceUid);
        doReturn(rInfo).when(mPackageManagerInt).resolveService(eq(serviceIntent), any(), anyLong(),
                anyInt(), anyInt(), anyInt());

        return serviceIntent;
    }

    private ServiceInfo makeServiceInfo(String serviceName, String packageName, int packageUid) {
        final ServiceInfo sInfo = new ServiceInfo();
        sInfo.name = serviceName;
        sInfo.processName = packageName;
        sInfo.packageName = packageName;
        sInfo.applicationInfo = new ApplicationInfo();
        sInfo.applicationInfo.uid = packageUid;
        sInfo.applicationInfo.packageName = packageName;
        sInfo.exported = true;
        return sInfo;
    }

    private class TestInjector extends Injector {
        TestInjector(Context context) {
            super(context);
        }

        @Override
        public AppOpsService getAppOpsService(Handler handler) {
            return mAppOpsService;
        }

        @Override
        public Handler getUiHandler(ActivityManagerService service) {
            return mHandlerThread.getThreadHandler();
        }

        @Override
        public ProcessList getProcessList(ActivityManagerService service) {
            return mProcessList;
        }

        @Override
        public ActiveServices getActiveServices(ActivityManagerService service) {
            if (mActiveServices == null) {
                mActiveServices = spy(new ActiveServices(service));
            }
            return mActiveServices;
        }

        @Override
        public int getCallingUid() {
            return mCurrentCallingUid;
        }

        @Override
        public int getCallingPid() {
            return mCurrentCallingPid;
        }

        @Override
        public AppErrors getAppErrors() {
            return mAppErrors;
        }

        @Override
        public IntentFirewall getIntentFirewall() {
            return mIntentFirewall;
        }
    }

    public static class TestProcessBuilder {
        ActivityManagerService mAms = mock(ActivityManagerService.class);
        String mPackageName = CALLING_PACKAGE_NAME;
        String mProcessName = null;
        ApplicationThreadDeferred mAppThread = mock(ApplicationThreadDeferred.class);
        int mUid = 10123;
        int mPid = 12345;
        boolean mIsNativeService = false;

        TestProcessBuilder setActivityManagerService(ActivityManagerService ams) {
            mAms = ams;
            return this;
        }

        TestProcessBuilder setPackageName(String packageName) {
            mPackageName = packageName;
            return this;
        }

        TestProcessBuilder setProcessName(String processName) {
            mProcessName = processName;
            return this;
        }

        TestProcessBuilder setAppThread(ApplicationThreadDeferred appThread) {
            mAppThread = appThread;
            return this;
        }

        TestProcessBuilder setPid(int pid) {
            mPid = pid;
            return this;
        }

        TestProcessBuilder setUid(int uid) {
            mUid = uid;
            return this;
        }

        ProcessRecord build() {
            if (mProcessName == null) {
                mProcessName = mPackageName;
            }

            // Create the ApplicationInfo
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = mPackageName;
            ai.uid = mUid;

            // Create and setup the Process
            final ProcessRecord proc = new ProcessRecord(mAms, ai, mProcessName, mUid);
            proc.setPid(mPid);
            proc.makeActive(mAppThread, mAms.mProcessStats);
            doReturn(mock(IBinder.class)).when(mAppThread).asBinder();

            // Make ActivityManagerService aware of the process.
            mAms.mProcessList.addProcessNameLocked(proc);
            mAms.mProcessList.updateLruProcessLocked(proc, false, null);

            setFieldValue(ProcessRecord.class, proc, "mWindowProcessController",
                    mock(WindowProcessController.class));

            return proc;
        }
    }

    // A data-only template used to generate a ProcessStateValidator with
    // ProcessStateValidator#create
    public static class ProcessStateValidatorTemplate {
        protected Integer mExpectedProcStateLowerBound = null;
        protected Integer mExpectedProcStateUpperBound = null;
        protected Integer mExpectedOomAdjScoreUpperBound = null;
        protected Integer mExpectedOomAdjScoreLowerBound = null;
        protected Boolean mExpectedFreezability = null;
    }

    public static class ProcessStateValidator extends ProcessStateValidatorTemplate {
        void validate(ProcessRecord proc) {
            final int actualProcState = proc.getCurProcState();
            final int actualOomAdjScore = proc.getSetAdj();
            final boolean actualFreezability =
                    (proc.getSetCapability() & ALL_CPU_TIME_CAPABILITIES) == 0;

            if (checkState(actualProcState, actualOomAdjScore, actualFreezability)) {
                // Passed
                return;
            }

            // Fail with a dump of the state.
            StringBuilder str = new StringBuilder();
            str.append("Process State Validation failed for ");
            str.append(proc);
            str.append("\n");
            str.append("ProcState - actual:");
            str.append(actualProcState);
            if (mExpectedProcStateUpperBound != null && mExpectedProcStateLowerBound != null) {
                str.append(" expected:");
                if (mExpectedProcStateUpperBound.equals(mExpectedProcStateLowerBound)) {
                    str.append(mExpectedProcStateUpperBound);
                } else {
                    str.append("[");
                    str.append(mExpectedProcStateLowerBound);
                    str.append(",");
                    str.append(mExpectedProcStateUpperBound);
                    str.append("]");
                }
            } else if (mExpectedProcStateUpperBound != null) {
                str.append(" expected:<=");
                str.append(mExpectedProcStateUpperBound);
            } else if (mExpectedProcStateLowerBound != null) {
                str.append(" expected:>=");
                str.append(mExpectedProcStateLowerBound);
            }
            str.append("\n");

            str.append("OomAdjScore - actual:");
            str.append(actualOomAdjScore);
            if (mExpectedOomAdjScoreUpperBound != null && mExpectedOomAdjScoreLowerBound != null) {
                str.append(" expected:");
                if (mExpectedOomAdjScoreUpperBound.equals(mExpectedOomAdjScoreLowerBound)) {
                    str.append(mExpectedOomAdjScoreUpperBound);
                } else {
                    str.append("[");
                    str.append(mExpectedOomAdjScoreLowerBound);
                    str.append(",");
                    str.append(mExpectedOomAdjScoreUpperBound);
                    str.append("]");
                }
            } else if (mExpectedOomAdjScoreUpperBound != null) {
                str.append(" expected:<=");
                str.append(mExpectedOomAdjScoreUpperBound);
            } else if (mExpectedOomAdjScoreLowerBound != null) {
                str.append(" expected:>=");
                str.append(mExpectedOomAdjScoreLowerBound);
            }
            str.append("\n");

            str.append("Freezability - actual:");
            str.append(actualFreezability);
            if (mExpectedFreezability != null) {
                str.append(" expected:");
                str.append(mExpectedFreezability);
            }
            str.append("\n");

            fail(str.toString());
        }

        private boolean checkState(int actualProcState, int actualOomAdjScore,
                boolean actualFreezability) {
            if (mExpectedProcStateUpperBound != null
                    && mExpectedProcStateUpperBound < actualProcState) {
                return false;
            }
            if (mExpectedProcStateLowerBound != null
                    && mExpectedProcStateLowerBound > actualProcState) {
                return false;
            }
            if (mExpectedOomAdjScoreUpperBound != null
                    && mExpectedOomAdjScoreUpperBound < actualOomAdjScore) {
                return false;
            }
            if (mExpectedOomAdjScoreLowerBound != null
                    && mExpectedOomAdjScoreLowerBound > actualOomAdjScore) {
                return false;
            }
            if (mExpectedFreezability != null && mExpectedFreezability != actualFreezability) {
                return false;
            }
            return true;
        }

        // Create a validator that satisfies all constraints from the provided validators.
        static ProcessStateValidator create(ProcessStateValidatorTemplate... validators) {
            final ProcessStateValidator merged = new ProcessStateValidator();
            for (ProcessStateValidatorTemplate validator : validators) {
                merged.maybeRaiseProcStateLowerBound(validator.mExpectedProcStateLowerBound);
                merged.maybeLowerProcStateUpperBound(validator.mExpectedProcStateUpperBound);
                merged.maybeRaiseOomAdjScoreLowerBound(validator.mExpectedOomAdjScoreLowerBound);
                merged.maybeLowerOomAdjScoreUpperBound(validator.mExpectedOomAdjScoreUpperBound);
                merged.mergeFreezeabilityExpectations(validator.mExpectedFreezability);
            }
            return merged;
        }

        // If any proc state of oom adj score bounds are unset, clamp them to the corresponding
        // bound.
        ProcessStateValidator clamp() {
            if (mExpectedProcStateLowerBound == null) {
                mExpectedProcStateLowerBound = mExpectedProcStateUpperBound;
            }
            if (mExpectedProcStateUpperBound == null) {
                mExpectedProcStateUpperBound = mExpectedProcStateLowerBound;
            }
            if (mExpectedOomAdjScoreLowerBound == null) {
                mExpectedOomAdjScoreLowerBound = mExpectedOomAdjScoreUpperBound;
            }
            if (mExpectedOomAdjScoreUpperBound == null) {
                mExpectedOomAdjScoreUpperBound = mExpectedOomAdjScoreLowerBound;
            }
            return this;
        }

        ProcessStateValidator expectedProcState(@ActivityManager.ProcessState int state) {
            expectedProcStateAtLeast(state);
            expectedProcStateAtMost(state);
            return this;
        }

        ProcessStateValidator expectedProcStateAtLeast(int value) {
            mExpectedProcStateLowerBound = value;
            return this;
        }

        private void maybeRaiseProcStateLowerBound(@Nullable Integer value) {
            if (value == null) return;
            if (mExpectedProcStateLowerBound == null) {
                mExpectedProcStateLowerBound = value;
                return;
            }
            if (mExpectedProcStateLowerBound < value) {
                mExpectedProcStateLowerBound = value;
            }
        }

        ProcessStateValidator expectedProcStateAtMost(int value) {
            mExpectedProcStateUpperBound = value;
            return this;
        }

        private void maybeLowerProcStateUpperBound(@Nullable Integer value) {
            if (value == null) return;
            if (mExpectedProcStateUpperBound == null) {
                mExpectedProcStateUpperBound = value;
                return;
            }
            if (mExpectedProcStateUpperBound > value) {
                mExpectedProcStateUpperBound = value;
            }
        }

        ProcessStateValidator expectedOomAdjScore(int value) {
            // Just set the upper and lower bounds to the value.
            expectedOomAdjScoreAtLeast(value);
            expectedOomAdjScoreAtMost(value);
            return this;
        }

        ProcessStateValidator expectedOomAdjScoreAtLeast(int value) {
            mExpectedOomAdjScoreLowerBound = value;
            return this;
        }

        private void maybeRaiseOomAdjScoreLowerBound(@Nullable Integer value) {
            if (value == null) return;
            if (mExpectedOomAdjScoreLowerBound == null) {
                mExpectedOomAdjScoreLowerBound = value;
                return;
            }
            if (mExpectedOomAdjScoreLowerBound < value) {
                mExpectedOomAdjScoreLowerBound = value;
            }
        }

        ProcessStateValidator expectedOomAdjScoreAtMost(int value) {
            mExpectedOomAdjScoreUpperBound = value;
            return this;
        }

        private void maybeLowerOomAdjScoreUpperBound(@Nullable Integer value) {
            if (value == null) return;
            if (mExpectedOomAdjScoreUpperBound == null) {
                mExpectedOomAdjScoreUpperBound = value;
                return;
            }
            if (mExpectedOomAdjScoreUpperBound > value) {
                mExpectedOomAdjScoreUpperBound = value;
            }
        }

        ProcessStateValidator expectedFreezability(boolean freezable) {
            mExpectedFreezability = freezable;
            return this;
        }

        private void mergeFreezeabilityExpectations(@Nullable Boolean freezable) {
            if (freezable == null) return;
            if (mExpectedFreezability == null) {
                mExpectedFreezability = freezable;
            }

            // A process should only be considered freezable if all expectations are considered
            // freezable.
            mExpectedFreezability = mExpectedFreezability && freezable;
        }
    }

    private static <T> void setFieldValue(Class clazz, Object obj, String fieldName, T val) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Field mfield = Field.class.getDeclaredField("accessFlags");
            mfield.setAccessible(true);
            mfield.setInt(field, mfield.getInt(field) & ~(Modifier.FINAL | Modifier.PRIVATE));
            field.set(obj, val);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to set field '" + fieldName + "': " + e.getMessage());
        }
    }
}
