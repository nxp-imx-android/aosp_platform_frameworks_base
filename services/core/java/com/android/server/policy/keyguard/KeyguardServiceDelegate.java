package com.android.server.policy.keyguard;

import static android.internal.perfetto.protos.Windowmanagerservice.KeyguardServiceDelegateProto.INTERACTIVE_STATE;
import static android.internal.perfetto.protos.Windowmanagerservice.KeyguardServiceDelegateProto.OCCLUDED;
import static android.internal.perfetto.protos.Windowmanagerservice.KeyguardServiceDelegateProto.SCREEN_STATE;
import static android.internal.perfetto.protos.Windowmanagerservice.KeyguardServiceDelegateProto.SHOWING;

import static com.android.internal.policy.IKeyguardService.SCREEN_TURNING_ON_REASON_UNKNOWN;
import static com.android.server.flags.Flags.resetKeyguardFirstStateDispatchOnServiceConnected;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManagerInternal;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.dreams.DreamManagerInternal;
import android.util.Log;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;

import com.android.internal.policy.IKeyguardDismissCallback;
import com.android.internal.policy.IKeyguardDrawnCallback;
import com.android.internal.policy.IKeyguardExitCallback;
import com.android.internal.policy.IKeyguardService;
import com.android.internal.policy.IKeyguardStateCallback;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.LocalServices;
import com.android.server.wm.EventLogTags;

import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * A local class that keeps a cache of keyguard state that can be restored in the event
 * keyguard crashes.
 */
public class KeyguardServiceDelegate {
    private static final String TAG = "KeyguardServiceDelegate";
    private static final boolean DEBUG = false;

    /**
     * Callback interface for observers in the rest of the system server to be notified when
     * either the {@code showing} or {@code trusted} properties reported by the KeyguardService
     * have changed.
     */
    public interface StateCallback {
        /** Indicates that the value of {@link #isTrusted()} has changed recently. */
        void onTrustedChanged();

        /** Indicates that the value of {@link #isShowing()} has changed recently. */
        void onShowingChanged();

        /** Indicates that the KeyguardService has drawn after the screen turned on. */
        void onDrawn();

        /** Indicates that the KeyguardService has connected successfully. */
        void onKeyguardServiceConnected();
    }

    private static final int SCREEN_STATE_OFF = 0;
    private static final int SCREEN_STATE_TURNING_ON = 1;
    private static final int SCREEN_STATE_ON = 2;
    private static final int SCREEN_STATE_TURNING_OFF = 3;

    private static final int INTERACTIVE_STATE_SLEEP = 0;
    private static final int INTERACTIVE_STATE_WAKING = 1;
    private static final int INTERACTIVE_STATE_AWAKE = 2;
    private static final int INTERACTIVE_STATE_GOING_TO_SLEEP = 3;

    /**
     * Binder connection to the KeyguardService.
     *
     * <p>This is null on devices that do not have a KeyguardService, or during periods where
     * the connection is not established yet (eg. lost due to a crash).
     */
    @Nullable
    private IKeyguardService mKeyguardService;

    @NonNull
    private final LockPatternUtils mLockPatternUtils;

    @NonNull
    private final ActivityManagerInternal mActivityManagerInternal;

    /** Last system-determined state sent to KeyguardService. */
    @NonNull
    private final KeyguardState mKeyguardState = new KeyguardState();
    /** Last keyguard-determined state reported back from KeyguardService. */
    @NonNull
    private final KeyguardReportedState mKeyguardReportedState = new KeyguardReportedState();

    /** Event listener for PhoneWindowManager */
    @NonNull
    private final StateCallback mCallback;

    /** Options for a deferred call to doKeyguardTimeout. */
    private boolean doKeyguardTimeoutRequested;
    @Nullable
    private Bundle doKeyguardTimeoutRequestedOptions;

    private final DreamManagerInternal.DreamManagerStateListener mDreamManagerStateListener =
            new DreamManagerInternal.DreamManagerStateListener() {
                @Override
                public void onDreamingStarted() {
                    KeyguardServiceDelegate.this.onDreamingStarted();
                }

                @Override
                public void onDreamingStopped() {
                    KeyguardServiceDelegate.this.onDreamingStopped();
                }
            };

    /** Callbacks for KeyguardService to report state changes. */
    private final IKeyguardStateCallback mKeyguardStateCallback =
            new IKeyguardStateCallback.Stub() {
        @Override
        public void onShowingStateChanged(boolean showing, @UserIdInt int userId) {
            if (userId != mKeyguardState.currentUser) return;
            KeyguardServiceDelegate.this.onShowingStateChanged(showing);
        }

        @Override
        public void onSimSecureStateChanged(boolean simSecure) {
            mKeyguardReportedState.simSecure = simSecure;
        }

        @Override
        public void onInputRestrictedStateChanged(boolean inputRestricted) {
            mKeyguardReportedState.inputRestricted = inputRestricted;
        }

        @Override
        public void onTrustedChanged(boolean trusted) {
            mKeyguardReportedState.trusted = trusted;
            mCallback.onTrustedChanged();
        }
    };

    // A delegate class to map a particular invocation with a ShowListener object.
    private final IKeyguardDrawnCallback mKeyguardShowDelegate = new IKeyguardDrawnCallback.Stub() {
        @Override
        public void onDrawn() throws RemoteException {
            if (DEBUG) Log.v(TAG, "**** SHOWN CALLED ****");
            KeyguardServiceDelegate.this.mCallback.onDrawn();
        }
    };

    /**
     * Data that has a source of truth in the system server and is pushed to KeyguardService.
     *
     * <p>When KeyguardService dies, this data is preserved and pushed to the new
     * KeyguardService instance, in order to keep the keyguard state consistent.
     */
    private static final class KeyguardState {
        public volatile boolean occluded;
        public boolean dreaming;
        public boolean systemIsReady;
        public boolean enabled = true;
        public volatile @UserIdInt int currentUser = UserHandle.USER_NULL;
        public boolean bootCompleted;
        public int screenState;
        public int interactiveState;

        KeyguardState() {
            reset();
        }

        private void reset() {
            occluded = false;
        }
    }

    /**
     * Data that has a source of truth in the KeyguardService and is reported over Binder via
     * {@link IKeyguardStateCallback} for the system server's decision-making.
     */
    private static final class KeyguardReportedState {
        volatile boolean showing;
        volatile boolean inputRestricted;
        volatile boolean deviceHasKeyguard;
        volatile boolean simSecure;
        volatile boolean trusted;

        KeyguardReportedState() {
            reset();
        }

        private void reset() {
            // Assume keyguard is showing and secure until we know for sure. This is here in
            // the event something checks before the service is actually started.
            // KeyguardService itself should default to this state until the real state is known.
            showing = true;
            inputRestricted = true;
            deviceHasKeyguard = true;
            simSecure = true;
            trusted = false;
        }

        private void disable() {
            showing = false;
            inputRestricted = false;
            deviceHasKeyguard = false;
            simSecure = false;
            trusted = false;
        }
    }

    public KeyguardServiceDelegate(@NonNull Context context, @NonNull StateCallback callback) {
        mCallback = callback;
        mLockPatternUtils = new LockPatternUtils(context);
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
    }

    public void onShowingStateChanged(boolean showing) {
        mKeyguardReportedState.showing = showing;
        mCallback.onShowingChanged();
    }

    /**
     * Creates a binding to the {@link IKeyguardService}.
     *
     * @param context the context to bind on.
     * @param handler the handler to run the ServiceConnection callbacks on.
     */
    public void bindService(@NonNull Context context, @NonNull Handler handler) {
        Intent intent = new Intent();
        final Resources resources = context.getApplicationContext().getResources();

        final ComponentName keyguardComponent = ComponentName.unflattenFromString(
                resources.getString(com.android.internal.R.string.config_keyguardComponent));
        intent.addFlags(Intent.FLAG_DEBUG_TRIAGED_MISSING);
        intent.setComponent(keyguardComponent);

        if (!context.bindServiceAsUser(intent, mKeyguardConnection, Context.BIND_AUTO_CREATE,
                handler, UserHandle.SYSTEM)) {
            Log.v(TAG, "*** Keyguard: can't bind to " + keyguardComponent);
            mKeyguardReportedState.disable();
        } else {
            if (DEBUG) Log.v(TAG, "*** Keyguard started");
        }

        final DreamManagerInternal dreamManager =
                LocalServices.getService(DreamManagerInternal.class);
        if (dreamManager != null) {
            dreamManager.registerDreamManagerStateListener(mDreamManagerStateListener);
        }
    }

    private final ServiceConnection mKeyguardConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.v(TAG, "*** Keyguard connected (yay!)");

            mKeyguardState.currentUser = mActivityManagerInternal.getCurrentUserId();

            if (resetKeyguardFirstStateDispatchOnServiceConnected()) {
                mCallback.onKeyguardServiceConnected();
            }

            // Replay the previous KeyguardState for the new KeyguardService.
            mKeyguardService = IKeyguardService.Stub.asInterface(service);
            try {
                mKeyguardService.addStateMonitorCallback(mKeyguardStateCallback);

                if (mKeyguardState.systemIsReady) {
                    // If the system is ready, it means keyguard crashed and restarted.
                    mKeyguardService.onSystemReady();
                    mKeyguardService.setCurrentUser(mKeyguardState.currentUser);
                    // This is used to hide the scrim once keyguard displays.
                    if (mKeyguardState.interactiveState == INTERACTIVE_STATE_AWAKE
                            || mKeyguardState.interactiveState == INTERACTIVE_STATE_WAKING) {
                        mKeyguardService.onStartedWakingUp(PowerManager.WAKE_REASON_UNKNOWN,
                                false /* powerButtonLaunchGestureTriggered */);
                    }
                    if (mKeyguardState.interactiveState == INTERACTIVE_STATE_AWAKE) {
                        mKeyguardService.onFinishedWakingUp();
                    }
                    if (mKeyguardState.screenState == SCREEN_STATE_ON
                            || mKeyguardState.screenState == SCREEN_STATE_TURNING_ON) {
                        mKeyguardService.onScreenTurningOn(SCREEN_TURNING_ON_REASON_UNKNOWN,
                                mKeyguardShowDelegate);
                    }
                    if (mKeyguardState.screenState == SCREEN_STATE_ON) {
                        mKeyguardService.onScreenTurnedOn();
                    }
                }
                if (mKeyguardState.bootCompleted) {
                    mKeyguardService.onBootCompleted();
                }
                if (mKeyguardState.occluded) {
                    mKeyguardService.setOccluded(mKeyguardState.occluded);
                }
                if (!mKeyguardState.enabled) {
                    mKeyguardService.setKeyguardEnabled(mKeyguardState.enabled);
                }
                if (mKeyguardState.dreaming) {
                    mKeyguardService.onDreamingStarted();
                }
                if (doKeyguardTimeoutRequested) {
                    if (isSecure(mKeyguardState.currentUser)) {
                        onShowingStateChanged(true);
                    }
                    mKeyguardService.doKeyguardTimeout(doKeyguardTimeoutRequestedOptions);
                    doKeyguardTimeoutRequested = false;
                    doKeyguardTimeoutRequestedOptions = null;
                }
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.v(TAG, "*** Keyguard disconnected (boo!)");
            mKeyguardService = null;
            mKeyguardState.reset();
            mKeyguardReportedState.reset();
            try {
                ActivityTaskManager.getService().setLockScreenShown(true /* showingKeyguard */,
                        false /* showingAod */);
            } catch (RemoteException e) {
                // Local call.
            }
        }
    };

    public boolean isShowing() {
        return mKeyguardReportedState.showing;
    }

    public boolean isTrusted() {
        return mKeyguardReportedState.trusted;
    }

    public boolean hasKeyguard() {
        return mKeyguardReportedState.deviceHasKeyguard;
    }

    public boolean isInputRestricted() {
        return mKeyguardReportedState.inputRestricted;
    }

    public void verifyUnlock(@NonNull final Consumer<Boolean> onKeyguardExitResult) {
        final var keyguardService = mKeyguardService;
        if (keyguardService == null) {
            return;
        }
        try {
            keyguardService.verifyUnlock(new IKeyguardExitCallback.Stub() {
                @Override
                public void onKeyguardExitResult(boolean success) throws RemoteException {
                    if (DEBUG) Log.v(TAG, "**** onKeyguardExitResult(" + success + ") CALLED ****");
                    onKeyguardExitResult.accept(success);
                }
            });
        } catch (RemoteException e) {
            Slog.w(TAG, "Remote Exception", e);
        }
    }

    public void setOccluded(boolean isOccluded) {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "setOccluded(" + isOccluded + ")");
            EventLogTags.writeWmSetKeyguardOccluded(
                    isOccluded ? 1 : 0,
                    0 /* animate */,
                    0 /* transit */,
                    "setOccluded");
            try {
                mKeyguardService.setOccluded(isOccluded);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.occluded = isOccluded;
    }

    public boolean isOccluded() {
        return mKeyguardState.occluded;
    }

    public void dismiss(IKeyguardDismissCallback callback, CharSequence message) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.dismiss(callback, message);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    public boolean isSecure(@UserIdInt int userId) {
        if (mKeyguardReportedState.simSecure) {
            return true;
        }
        if (userId == UserHandle.USER_NULL) {
            return true;
        }
        return mLockPatternUtils.isSecure(userId);
    }

    public void onDreamingStarted() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onDreamingStarted();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.dreaming = true;
    }

    public void onDreamingStopped() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onDreamingStopped();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.dreaming = false;
    }

    public void onStartedWakingUp(
            @PowerManager.WakeReason int pmWakeReason, boolean powerButtonLaunchGestureTriggered) {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onStartedWakingUp()");
            try {
                mKeyguardService.onStartedWakingUp(pmWakeReason, powerButtonLaunchGestureTriggered);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.interactiveState = INTERACTIVE_STATE_WAKING;
    }

    public void onFinishedWakingUp() {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onFinishedWakingUp()");
            try {
                mKeyguardService.onFinishedWakingUp();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.interactiveState = INTERACTIVE_STATE_AWAKE;
    }

    public void onScreenTurningOff() {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onScreenTurningOff()");
            try {
                mKeyguardService.onScreenTurningOff();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.screenState = SCREEN_STATE_TURNING_OFF;
    }

    public void onScreenTurnedOff() {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onScreenTurnedOff()");
            try {
                mKeyguardService.onScreenTurnedOff();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.screenState = SCREEN_STATE_OFF;
    }

    /**
     * Notify Keyguard that the screen started turning on.
     *
     * @param reason one of the SCREEN_TURNING_ON_REASON constants in IKeyguardService.aidl.
     */
    public void onScreenTurningOn(int reason) {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onScreenTurnedOn(reason = " + reason + ")");
            try {
                mKeyguardService.onScreenTurningOn(reason, mKeyguardShowDelegate);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        } else {
            // try again when we establish a connection
            Slog.w(TAG, "onScreenTurningOn(): no keyguard service!");
        }
        mKeyguardState.screenState = SCREEN_STATE_TURNING_ON;
    }

    public void onScreenTurnedOn() {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onScreenTurnedOn()");
            try {
                mKeyguardService.onScreenTurnedOn();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.screenState = SCREEN_STATE_ON;
    }

    public void onStartedGoingToSleep(@PowerManager.GoToSleepReason int pmSleepReason) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onStartedGoingToSleep(pmSleepReason);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.interactiveState = INTERACTIVE_STATE_GOING_TO_SLEEP;
    }

    public void onFinishedGoingToSleep(
            @PowerManager.GoToSleepReason int pmSleepReason,
            boolean powerButtonLaunchGestureTriggered) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onFinishedGoingToSleep(pmSleepReason,
                        powerButtonLaunchGestureTriggered);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.interactiveState = INTERACTIVE_STATE_SLEEP;
    }

    public void setKeyguardEnabled(boolean enabled) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.setKeyguardEnabled(enabled);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.enabled = enabled;
    }

    public void onSystemReady() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onSystemReady();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        } else {
            mKeyguardState.systemIsReady = true;
        }
    }

    public void doKeyguardTimeout(Bundle options) {
        if (mKeyguardService != null) {
            if (isSecure(mKeyguardState.currentUser)) {
                // Preemptively inform the cache that the keyguard will soon be showing, as calls to
                // doKeyguardTimeout are a signal to lock the device as soon as possible.
                onShowingStateChanged(true);
            }
            try {
                mKeyguardService.doKeyguardTimeout(options);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        } else {
            doKeyguardTimeoutRequested = true;
            if (options != null) {
                doKeyguardTimeoutRequestedOptions = options;
            }
        }
    }

    /**
     * Request to show the keyguard immediately without immediately locking the device.
     */
    public void showDismissibleKeyguard() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.showDismissibleKeyguard();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    public void setCurrentUser(int newUserId) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.setCurrentUser(newUserId);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.currentUser = newUserId;
    }

    public void setSwitchingUser(boolean switching) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.setSwitchingUser(switching);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    public void startKeyguardExitAnimation(long startTime) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.startKeyguardExitAnimation(startTime, 0);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    public void onBootCompleted() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onBootCompleted();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.bootCompleted = true;
    }

    public void onShortPowerPressedGoHome() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onShortPowerPressedGoHome();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    public void dismissKeyguardToLaunch(Intent intentToLaunch) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.dismissKeyguardToLaunch(intentToLaunch);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    public void onSystemKeyPressed(int keycode) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onSystemKeyPressed(keycode);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    public void dumpDebug(ProtoOutputStream proto, long fieldId) {
        final long token = proto.start(fieldId);
        proto.write(SHOWING, mKeyguardReportedState.showing);
        proto.write(OCCLUDED, mKeyguardState.occluded);
        proto.write(SCREEN_STATE, mKeyguardState.screenState);
        proto.write(INTERACTIVE_STATE, mKeyguardState.interactiveState);
        proto.end(token);
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + TAG);
        prefix += "  ";
        pw.println(prefix + "showing=" + mKeyguardReportedState.showing);
        pw.println(prefix + "inputRestricted=" + mKeyguardReportedState.inputRestricted);
        pw.println(prefix + "occluded=" + mKeyguardState.occluded);
        pw.println(prefix + "trusted=" + mKeyguardReportedState.trusted);
        pw.println(prefix + "simSecure=" + mKeyguardReportedState.simSecure);
        pw.println(prefix + "dreaming=" + mKeyguardState.dreaming);
        pw.println(prefix + "systemIsReady=" + mKeyguardState.systemIsReady);
        pw.println(prefix + "deviceHasKeyguard=" + mKeyguardReportedState.deviceHasKeyguard);
        pw.println(prefix + "enabled=" + mKeyguardState.enabled);
        pw.println(prefix + "currentUser=" + mKeyguardState.currentUser);
        pw.println(prefix + "bootCompleted=" + mKeyguardState.bootCompleted);
        pw.println(prefix + "screenState=" + screenStateToString(mKeyguardState.screenState));
        pw.println(prefix + "interactiveState=" +
                interactiveStateToString(mKeyguardState.interactiveState));
    }

    private static String screenStateToString(int screen) {
        switch (screen) {
            case SCREEN_STATE_OFF:
                return "SCREEN_STATE_OFF";
            case SCREEN_STATE_TURNING_ON:
                return "SCREEN_STATE_TURNING_ON";
            case SCREEN_STATE_ON:
                return "SCREEN_STATE_ON";
            case SCREEN_STATE_TURNING_OFF:
                return "SCREEN_STATE_TURNING_OFF";
            default:
                return Integer.toString(screen);
        }
    }

    private static String interactiveStateToString(int interactive) {
        switch (interactive) {
            case INTERACTIVE_STATE_SLEEP:
                return "INTERACTIVE_STATE_SLEEP";
            case INTERACTIVE_STATE_WAKING:
                return "INTERACTIVE_STATE_WAKING";
            case INTERACTIVE_STATE_AWAKE:
                return "INTERACTIVE_STATE_AWAKE";
            case INTERACTIVE_STATE_GOING_TO_SLEEP:
                return "INTERACTIVE_STATE_GOING_TO_SLEEP";
            default:
                return Integer.toString(interactive);
        }
    }
}
