/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.accessibility;

import static android.accessibilityservice.AccessibilityTrace.FLAGS_INPUT_FILTER;
import static android.util.MathUtils.sqrt;

import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.hardware.input.InputManager;
import android.hardware.input.VirtualMouse;
import android.hardware.input.VirtualMouseButtonEvent;
import android.hardware.input.VirtualMouseConfig;
import android.hardware.input.VirtualMouseRelativeEvent;
import android.hardware.input.VirtualMouseScrollEvent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.annotation.VisibleForTesting;

import com.android.server.LocalServices;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;

/**
 * Implements the "mouse keys" accessibility feature for physical keyboards.
 *
 * If enabled, mouse keys will allow users to use a physical keyboard to
 * control the mouse on the display.
 * The following mouse functionality is supported by the mouse keys:
 * <ul>
 *   <li> Move the mouse pointer in different directions (up, down, left, right and diagonally).
 *   <li> Click the mouse button (left, right and middle click).
 *   <li> Press and hold the mouse button.
 *   <li> Release the mouse button.
 *   <li> Scroll (up and down).
 * </ul>
 *
 * The keys that are mapped to mouse keys are consumed by {@link AccessibilityInputFilter}.
 * Non-mouse key {@link KeyEvent} will be passed to the parent handler to be handled as usual.
 * A new {@link VirtualMouse} is created whenever the mouse keys feature is turned on in Settings.
 * In case multiple physical keyboard are connected to a device,
 * mouse keys of each physical keyboard will control a single (global) mouse pointer.
 */
public class MouseKeysInterceptor extends BaseEventStreamTransformation
        implements Handler.Callback, InputManager.InputDeviceListener {
    private static final String LOG_TAG = "MouseKeysInterceptor";

    // To enable these logs, run: 'adb shell setprop log.tag.MouseKeysInterceptor DEBUG'
    // (requires restart)
    private static final boolean DEBUG = Log.isLoggable(LOG_TAG, Log.DEBUG);

    private static final int MESSAGE_MOVE_MOUSE_POINTER = 1;
    private static final int MESSAGE_SCROLL_MOUSE_POINTER = 2;
    private static final int KEY_NOT_SET = -1;

    /**
     * The base time interval, in milliseconds, after which a mouse action (like movement or scroll)
     * will be repeated. This is used as the default interval when FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT
     * is not enabled, or for scroll actions even when FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT is enabled.
     */
    private static final int INTERVAL_MILLIS = 10;

    /**
     * The specific time interval, in milliseconds, after which mouse pointer movement actions
     * are repeated when FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT is enabled. This value is longer than
     * {@link #INTERVAL_MILLIS} to allow for a perceptible acceleration curve.
     */
    private static final int INTERVAL_MILLIS_MOUSE_POINTER = 25;

    /**
     * The initial movement step, in pixels per interval, for the mouse pointer.
     * This value is used as the starting point for acceleration, and also as the
     * reset value for the current movement step when a key is released and
     * FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT is enabled.
     */
    private static final float INITIAL_MOUSE_POINTER_MOVEMENT_STEP = 1.0f;

    @VisibleForTesting
    public static final float MOUSE_POINTER_MOVEMENT_STEP = 1.8f;
    @VisibleForTesting
    public static final float MOUSE_SCROLL_STEP = 0.2f;

    private final AccessibilityManagerService mAms;
    private final Handler mHandler;
    private final InputManager mInputManager;

    /** Thread to wait for virtual mouse creation to complete */
    private final Thread mCreateVirtualMouseThread;

    /**
     * Map of device IDs to a map of key codes to their corresponding {@link MouseKeyEvent} values.
     * To ensure thread safety for the map, all access and modification of the map
     * should happen on the same thread, i.e., on the handler thread.
     */
    private final SparseArray<SparseArray<MouseKeyEvent>> mDeviceKeyCodeMap =
            new SparseArray<>();

    VirtualDeviceManager.VirtualDevice mVirtualDevice = null;

    private VirtualMouse mVirtualMouse = null;

    /**
     * State of the active directional mouse key.
     * Multiple mouse keys will not be allowed to be used simultaneously i.e.,
     * once a mouse key is pressed, other mouse key presses will be disregarded
     * (except for when the "HOLD" key is pressed).
     */
    private int mActiveMoveKey = KEY_NOT_SET;

    /** State of the active scroll mouse key. */
    private int mActiveScrollKey = KEY_NOT_SET;

    /** Last time the key action was performed */
    private long mLastTimeKeyActionPerformed = 0;

    /** Whether scroll toggle is on */
    private boolean mScrollToggleOn = false;

    /** The ID of the input device that is currently active */
    private int mActiveInputDeviceId = 0;

    /** The maximum movement step the mouse pointer can reach when accelerating. */
    private float mMaxMovementStep = 10.0f;

    /** The acceleration factor applied to the mouse pointer's speed per interval. */
    private float mAcceleration = 0.1f;

    /** The current movement step of the mouse pointer, which increases with acceleration. */
    private float mCurrentMovementStep = INITIAL_MOUSE_POINTER_MOVEMENT_STEP;

    /** Provides a source for obtaining uptime, used for precise timing calculations. */
    private final TimeSource mTimeSource;

    /**
     * Enum representing different types of mouse key events, each associated with a specific
     * key code.
     *
     * <p> These events correspond to various mouse actions such as directional movements,
     * clicks, and scrolls, mapped to specific keys on the keyboard.
     * The key codes here are the QWERTY key codes, and should be accessed via
     * {@link MouseKeyEvent#getKeyCode(InputDevice)}
     * so that it is mapped to the equivalent key on the keyboard layout of the keyboard device
     * that is actually in use.
     * </p>
     */
    public enum MouseKeyEvent {
        DIAGONAL_UP_LEFT_MOVE(KeyEvent.KEYCODE_7),
        UP_MOVE_OR_SCROLL(KeyEvent.KEYCODE_8),
        DIAGONAL_UP_RIGHT_MOVE(KeyEvent.KEYCODE_9),
        LEFT_MOVE_OR_SCROLL(KeyEvent.KEYCODE_U),
        RIGHT_MOVE_OR_SCROLL(KeyEvent.KEYCODE_O),
        DIAGONAL_DOWN_LEFT_MOVE(KeyEvent.KEYCODE_J),
        DOWN_MOVE_OR_SCROLL(KeyEvent.KEYCODE_K),
        DIAGONAL_DOWN_RIGHT_MOVE(KeyEvent.KEYCODE_L),
        LEFT_CLICK(KeyEvent.KEYCODE_I),
        RIGHT_CLICK(KeyEvent.KEYCODE_SLASH),
        HOLD(KeyEvent.KEYCODE_M),
        RELEASE(KeyEvent.KEYCODE_COMMA),
        SCROLL_TOGGLE(KeyEvent.KEYCODE_PERIOD);

        private final int mLocationKeyCode;
        MouseKeyEvent(int enumValue) {
            mLocationKeyCode = enumValue;
        }

        @VisibleForTesting
        public final int getKeyCodeValue() {
            return mLocationKeyCode;
        }

        /**
         * Get the key code associated with the given MouseKeyEvent for the given keyboard
         * input device, taking into account its layout.
         * The default is to return the keycode for the default layout (QWERTY).
         * We check if the input device has been generated using {@link InputDevice#getGeneration()}
         * to test with the default {@link MouseKeyEvent} values in the unit tests.
         */
        public int getKeyCode(InputDevice inputDevice) {
            if (inputDevice.getGeneration() == -1) {
                return mLocationKeyCode;
            }
            return inputDevice.getKeyCodeForKeyLocation(mLocationKeyCode);
        }

        /**
         * Convert int value of the key code to corresponding {@link MouseKeyEvent}
         * enum for a particular device ID.
         * If no matching value is found, this will return {@code null}.
         */
        @Nullable
        public static MouseKeyEvent from(int keyCode, int deviceId,
                SparseArray<SparseArray<MouseKeyEvent>> deviceKeyCodeMap) {
            SparseArray<MouseKeyEvent> keyCodeToEnumMap = deviceKeyCodeMap.get(deviceId);
            if (keyCodeToEnumMap != null) {
                return keyCodeToEnumMap.get(keyCode);
            }
            return null;
        }
    }

    /**
     * Create a map of key codes to their corresponding {@link MouseKeyEvent} values
     * for a specific input device.
     * The key for {@code mDeviceKeyCodeMap} is the deviceId.
     * The key for {@code keyCodeToEnumMap} is the keycode for each
     * {@link MouseKeyEvent} according to the keyboard layout of the input device.
     */
    public void initializeDeviceToEnumMap(InputDevice inputDevice) {
        int deviceId = inputDevice.getId();
        SparseArray<MouseKeyEvent> keyCodeToEnumMap = new SparseArray<>();
        for (MouseKeyEvent mouseKeyEventType : MouseKeyEvent.values()) {
            int keyCode = mouseKeyEventType.getKeyCode(inputDevice);
            keyCodeToEnumMap.put(keyCode, mouseKeyEventType);
        }
        mDeviceKeyCodeMap.put(deviceId, keyCodeToEnumMap);
    }

    /**
     * Construct a new MouseKeysInterceptor.
     *
     * @param service The service to notify of key events
     * @param looper Looper to use for callbacks and messages
     * @param displayId Display ID to send mouse events to
     */
    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    public MouseKeysInterceptor(AccessibilityManagerService service,
            InputManager inputManager, Looper looper, int displayId, TimeSource timeSource) {
        mAms = service;
        mInputManager = inputManager;
        mHandler = new Handler(looper, this);
        mTimeSource = timeSource;
        // Create the virtual mouse on a separate thread since virtual device creation
        // should happen on an auxiliary thread, and not from the handler's thread.
        // This is because the handler thread is the same as the main thread,
        // and the main thread will be blocked waiting for the virtual device to be created.
        mCreateVirtualMouseThread = new Thread(() -> {
            mVirtualMouse = createVirtualMouse(displayId);
        });
        mCreateVirtualMouseThread.start();
        // Register an input device listener to watch when input devices are
        // added, removed or reconfigured.
        mInputManager.registerInputDeviceListener(this, mHandler);
    }

    /**
     * Wait for {@code mVirtualMouse} to be created.
     * This will ensure that {@code mVirtualMouse} is always created before
     * trying to send mouse events.
     **/
    private void waitForVirtualMouseCreation() {
        try {
            // Block the current thread until the virtual mouse creation thread completes.
            mCreateVirtualMouseThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    private void sendVirtualMouseRelativeEvent(float x, float y) {
        waitForVirtualMouseCreation();
        mVirtualMouse.sendRelativeEvent(new VirtualMouseRelativeEvent.Builder()
                .setRelativeX(x)
                .setRelativeY(y)
                .build()
        );
    }

    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    private void sendVirtualMouseButtonEvent(int buttonCode, int actionCode) {
        waitForVirtualMouseCreation();
        mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(actionCode)
                .setButtonCode(buttonCode)
                .build()
        );
    }

    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    private void sendVirtualMouseScrollEvent(float x, float y) {
        waitForVirtualMouseCreation();
        mVirtualMouse.sendScrollEvent(new VirtualMouseScrollEvent.Builder()
                .setXAxisMovement(x)
                .setYAxisMovement(y)
                .build()
        );
    }

    /**
     * Performs a mouse scroll action based on the provided key code.
     * The scroll action will only be performed if the scroll toggle is on.
     * This method interprets the key code as a mouse scroll and sends
     * the corresponding {@code VirtualMouseScrollEvent#mYAxisMovement}.

     * @param keyCode The key code representing the mouse scroll action.
     *                Supported keys are:
     *                <ul>
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#UP_MOVE_OR_SCROLL}
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#DOWN_MOVE_OR_SCROLL}
     *                </ul>
     */
    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    private void performMouseScrollAction(int keyCode) {
        MouseKeyEvent mouseKeyEvent = MouseKeyEvent.from(
                keyCode, mActiveInputDeviceId, mDeviceKeyCodeMap);
        float x = 0f;
        float y = 0f;

        switch (mouseKeyEvent) {
            case UP_MOVE_OR_SCROLL -> {
                y = MOUSE_SCROLL_STEP;
            }
            case DOWN_MOVE_OR_SCROLL -> {
                y = -MOUSE_SCROLL_STEP;
            }
            case LEFT_MOVE_OR_SCROLL -> {
                x = MOUSE_SCROLL_STEP;
            }
            case RIGHT_MOVE_OR_SCROLL -> {
                x = -MOUSE_SCROLL_STEP;
            }
            default -> {
                x = 0.0f;
                y = 0.0f;
            }
        }
        sendVirtualMouseScrollEvent(x, y);
        if (DEBUG) {
            Slog.d(LOG_TAG, "Performed mouse key event: " + mouseKeyEvent.name()
                    + " for scroll action with axis movement (x=" + x + ", y=" + y + ")");
        }
    }

    /**
     * Performs a mouse button action based on the provided key code.
     * This method interprets the key code as a mouse button press and sends
     * the corresponding press and release events to the virtual mouse.

     * @param keyCode The key code representing the mouse button action.
     *                Supported keys are:
     *                <ul>
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#LEFT_CLICK} (Primary Button)
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#RIGHT_CLICK} (Secondary
     *                  Button)
     *                </ul>
     */
    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    private void performMouseButtonAction(int keyCode) {
        MouseKeyEvent mouseKeyEvent = MouseKeyEvent.from(
                keyCode, mActiveInputDeviceId, mDeviceKeyCodeMap);
        int buttonCode = switch (mouseKeyEvent) {
            case LEFT_CLICK -> VirtualMouseButtonEvent.BUTTON_PRIMARY;
            case RIGHT_CLICK -> VirtualMouseButtonEvent.BUTTON_SECONDARY;
            default -> VirtualMouseButtonEvent.BUTTON_UNKNOWN;
        };
        if (buttonCode != VirtualMouseButtonEvent.BUTTON_UNKNOWN) {
            sendVirtualMouseButtonEvent(buttonCode,
                    VirtualMouseButtonEvent.ACTION_BUTTON_PRESS);
            sendVirtualMouseButtonEvent(buttonCode,
                    VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE);
        }
        if (DEBUG) {
            if (buttonCode == VirtualMouseButtonEvent.BUTTON_UNKNOWN) {
                Slog.d(LOG_TAG, "Button code is unknown for mouse key event: "
                        + mouseKeyEvent.name());
            } else {
                Slog.d(LOG_TAG, "Performed mouse key event: " + mouseKeyEvent.name()
                        + " for button action");
            }
        }
    }

    /**
     * Performs a mouse pointer action based on the provided key code.
     * The method calculates the relative movement of the mouse pointer
     * and sends the corresponding event to the virtual mouse.
     *
     * The UP, DOWN, LEFT, RIGHT  pointer actions will only take place for their
     * respective keys if the scroll toggle is off.
     *
     * @param keyCode The key code representing the direction or button press.
     *                Supported keys are:
     *                <ul>
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#DIAGONAL_DOWN_LEFT_MOVE}
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#DOWN_MOVE_OR_SCROLL}
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#DIAGONAL_DOWN_RIGHT_MOVE}
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#LEFT_MOVE_OR_SCROLL}
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#RIGHT_MOVE_OR_SCROLL}
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#DIAGONAL_UP_LEFT_MOVE}
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#UP_MOVE_OR_SCROLL}
     *                  <li>{@link MouseKeysInterceptor.MouseKeyEvent#DIAGONAL_UP_RIGHT_MOVE}
     *                </ul>
     */
    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    private void performMousePointerAction(int keyCode) {
        float x = 0f;
        float y = 0f;

        if (Flags.enableMouseKeyEnhancement()) {
            // If there is no acceleration, start at the max movement step
            if (mAcceleration == 0.0f) {
                mCurrentMovementStep = mMaxMovementStep;
            } else {
                mCurrentMovementStep = Math.min(
                        mCurrentMovementStep * (1 + mAcceleration), mMaxMovementStep);
            }
        } else {
            mCurrentMovementStep = MOUSE_POINTER_MOVEMENT_STEP;
        }
        MouseKeyEvent mouseKeyEvent = MouseKeyEvent.from(
                keyCode, mActiveInputDeviceId, mDeviceKeyCodeMap);

        switch (mouseKeyEvent) {
            case DIAGONAL_DOWN_LEFT_MOVE -> {
                x = -mCurrentMovementStep / sqrt(2);
                y = mCurrentMovementStep / sqrt(2);
            }
            case DOWN_MOVE_OR_SCROLL -> {
                if (!mScrollToggleOn) {
                    y = mCurrentMovementStep;
                }
            }
            case DIAGONAL_DOWN_RIGHT_MOVE -> {
                x = mCurrentMovementStep / sqrt(2);
                y = mCurrentMovementStep / sqrt(2);
            }
            case LEFT_MOVE_OR_SCROLL -> {
                x = -mCurrentMovementStep;
            }
            case RIGHT_MOVE_OR_SCROLL -> {
                x = mCurrentMovementStep;
            }
            case DIAGONAL_UP_LEFT_MOVE -> {
                x = -mCurrentMovementStep / sqrt(2);
                y = -mCurrentMovementStep / sqrt(2);
            }
            case UP_MOVE_OR_SCROLL -> {
                if (!mScrollToggleOn) {
                    y = -mCurrentMovementStep;
                }
            }
            case DIAGONAL_UP_RIGHT_MOVE -> {
                x = mCurrentMovementStep / sqrt(2);
                y = -mCurrentMovementStep / sqrt(2);
            }
            default -> {
                x = 0.0f;
                y = 0.0f;
            }
        }
        sendVirtualMouseRelativeEvent(x, y);
        if (DEBUG) {
            Slog.d(LOG_TAG, "Performed mouse key event: " + mouseKeyEvent.name()
                    + " for relative pointer movement (x=" + x + ", y=" + y + ")");
        }
    }

    private boolean isMouseKey(int keyCode, int deviceId) {
        SparseArray<MouseKeyEvent> keyCodeToEnumMap = mDeviceKeyCodeMap.get(deviceId);
        return keyCodeToEnumMap.contains(keyCode);
    }

    private boolean isMouseButtonKey(int keyCode, InputDevice inputDevice) {
        return keyCode == MouseKeyEvent.LEFT_CLICK.getKeyCode(inputDevice)
                || keyCode == MouseKeyEvent.RIGHT_CLICK.getKeyCode(inputDevice);
    }

    private boolean isMouseScrollKey(int keyCode, InputDevice inputDevice) {
        return keyCode == MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCode(inputDevice)
                || keyCode == MouseKeyEvent.DOWN_MOVE_OR_SCROLL.getKeyCode(inputDevice)
                || keyCode == MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCode(inputDevice)
                || keyCode == MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCode(inputDevice);
    }

    /**
     * Create a virtual mouse using the VirtualDeviceManagerInternal.
     *
     * @return The created VirtualMouse.
     */
    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    private VirtualMouse createVirtualMouse(int displayId) {
        final VirtualDeviceManagerInternal localVdm =
                LocalServices.getService(VirtualDeviceManagerInternal.class);
        mVirtualDevice = localVdm.createVirtualDevice(
                new VirtualDeviceParams.Builder().setName("Mouse Keys Virtual Device").build());
        VirtualMouse virtualMouse = mVirtualDevice.createVirtualMouse(
                new VirtualMouseConfig.Builder()
                .setInputDeviceName("Mouse Keys Virtual Mouse")
                .setAssociatedDisplayId(displayId)
                .build());
        return virtualMouse;
    }

    /**
     * Handles key events and forwards mouse key events to the virtual mouse on the handler thread.
     *
     * @param event The key event to handle.
     * @param policyFlags The policy flags associated with the key event.
     */
    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    @Override
    public void onKeyEvent(KeyEvent event, int policyFlags) {
        if (mAms.getTraceManager().isA11yTracingEnabledForTypes(FLAGS_INPUT_FILTER)) {
            mAms.getTraceManager().logTrace(LOG_TAG + ".onKeyEvent",
                    FLAGS_INPUT_FILTER, "event=" + event + ";policyFlags=" + policyFlags);
        }

        mHandler.post(() -> {
            onKeyEventInternal(event, policyFlags);
        });
    }

    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    private void onKeyEventInternal(KeyEvent event, int policyFlags) {
        boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;
        int keyCode = event.getKeyCode();
        mActiveInputDeviceId = event.getDeviceId();
        InputDevice inputDevice = mInputManager.getInputDevice(mActiveInputDeviceId);

        if (!mDeviceKeyCodeMap.contains(mActiveInputDeviceId)) {
            initializeDeviceToEnumMap(inputDevice);
        }

        if (!isMouseKey(keyCode, mActiveInputDeviceId)) {
            // Pass non-mouse key events to the next handler
            super.onKeyEvent(event, policyFlags);
        } else if (isDown) {
            if (keyCode == MouseKeyEvent.SCROLL_TOGGLE.getKeyCode(inputDevice)) {
                mScrollToggleOn = !mScrollToggleOn;
                if (DEBUG) {
                    Slog.d(LOG_TAG, "Scroll toggle " + (mScrollToggleOn ? "ON" : "OFF"));
                }
            } else if (keyCode == MouseKeyEvent.HOLD.getKeyCode(inputDevice)) {
                sendVirtualMouseButtonEvent(
                        VirtualMouseButtonEvent.BUTTON_PRIMARY,
                        VirtualMouseButtonEvent.ACTION_BUTTON_PRESS
                );
            } else if (keyCode == MouseKeyEvent.RELEASE.getKeyCode(inputDevice)) {
                sendVirtualMouseButtonEvent(
                        VirtualMouseButtonEvent.BUTTON_PRIMARY,
                        VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE
                );
            } else if (isMouseButtonKey(keyCode, inputDevice)) {
                performMouseButtonAction(keyCode);
            } else if (mScrollToggleOn && isMouseScrollKey(keyCode, inputDevice)) {
                // If the scroll key is pressed down and no other key is active,
                // set it as the active key and send a message to scroll the pointer
                if (mActiveScrollKey == KEY_NOT_SET) {
                    mActiveScrollKey = keyCode;
                    mLastTimeKeyActionPerformed = event.getDownTime();
                    mHandler.sendEmptyMessage(MESSAGE_SCROLL_MOUSE_POINTER);
                }
            } else {
                // This is a directional key.
                // If the key is pressed down and no other key is active,
                // set it as the active key and send a message to move the pointer
                if (mActiveMoveKey == KEY_NOT_SET) {
                    mActiveMoveKey = keyCode;
                    mLastTimeKeyActionPerformed = event.getDownTime();
                    mHandler.sendEmptyMessage(MESSAGE_MOVE_MOUSE_POINTER);
                }
            }
        } else {
            // Up event received
            if (mActiveMoveKey == keyCode) {
                // If the key is released, and it is the active key, stop moving the pointer
                mActiveMoveKey = KEY_NOT_SET;
                mCurrentMovementStep = Flags.enableMouseKeyEnhancement()
                        ? INITIAL_MOUSE_POINTER_MOVEMENT_STEP : MOUSE_POINTER_MOVEMENT_STEP;
                mHandler.removeMessages(MESSAGE_MOVE_MOUSE_POINTER);
            } else if (mActiveScrollKey == keyCode) {
                // If the key is released, and it is the active key, stop scrolling the pointer
                mActiveScrollKey = KEY_NOT_SET;
                mHandler.removeMessages(MESSAGE_SCROLL_MOUSE_POINTER);
            } else {
                Slog.i(LOG_TAG, "Dropping event with key code: '" + keyCode
                        + "', with no matching down event from deviceId = "
                        + event.getDeviceId());
            }
        }
    }

    /**
     * Handle messages for moving or scrolling the mouse pointer.
     *
     * @param msg The message to handle.
     * @return True if the message was handled, false otherwise.
     */
    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    @Override
    public boolean handleMessage(Message msg) {
        long currentProcessingTime = msg.getWhen();
        if (Flags.enableMouseKeyEnhancement()) {
            currentProcessingTime = this.mTimeSource.uptimeMillis();
        }
        switch (msg.what) {
            case MESSAGE_MOVE_MOUSE_POINTER ->
                    handleMouseMessage(currentProcessingTime, mActiveMoveKey,
                            MESSAGE_MOVE_MOUSE_POINTER);
            case MESSAGE_SCROLL_MOUSE_POINTER ->
                    handleMouseMessage(msg.getWhen(), mActiveScrollKey,
                            MESSAGE_SCROLL_MOUSE_POINTER);
            default -> {
                Slog.e(LOG_TAG, "Unexpected message type");
                return false;
            }
        }
        return true;
    }

    /**
     * Handles mouse-related messages for moving or scrolling the mouse pointer.
     *
     * This method checks if the specified time interval (either {@code INTERVAL_MILLIS} or
     * {@code INTERVAL_MILLIS_MOUSE_POINTER} if mouse keys enhancement is enabled for move messages)
     * has passed since the last action was performed. If it has, the corresponding mouse
     * action (move or scroll) is executed based on the {@code activeKey} and {@code messageType}.
     * The time of this action is then recorded.
     *
     * If there is an {@code activeKey} (i.e., a key is still considered held down):
     * <ul>
     *   <li>If {@code Flags.enableMouseKeyEnhancement()} is true, the message is precisely
     *       rescheduled to be handled at a target uptime derived from the controlled
     *       {@code mTimeSource} plus the relevant delay ({@code INTERVAL_MILLIS} or
     *       {@code INTERVAL_MILLIS_MOUSE_POINTER}). This ensures consistent timing
     *       irrespective of message handling latencies.</li>
     *   <li>If {@code Flags.enableMouseKeyEnhancement()} is false, the message is rescheduled
     *       to be handled again after a fixed delay of {@code INTERVAL_MILLIS} using
     *       {@code sendEmptyMessageDelayed}.</li>
     * </ul>
     *
     * @param currentTime The current time (typically from the event or looper) when the message
     *                    is being initially processed.
     * @param activeKey The key code representing the active key. This determines the
     *                  direction or type of action to be performed. Should be
     *                  {@code KEY_NOT_SET} if no key is active.
     * @param messageType The type of message to be handled. It can be one of the
     *                    following:
     *                    <ul>
     *                      <li>{@link #MESSAGE_MOVE_MOUSE_POINTER} - for moving the mouse pointer.
     *                      <li>{@link #MESSAGE_SCROLL_MOUSE_POINTER} - for scrolling mouse pointer.
     *                    </ul>
     */
    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    public void handleMouseMessage(long currentTime, int activeKey, int messageType) {
        int delayMillis = INTERVAL_MILLIS;

        if (Flags.enableMouseKeyEnhancement() && messageType == MESSAGE_MOVE_MOUSE_POINTER) {
            delayMillis = INTERVAL_MILLIS_MOUSE_POINTER;
        }

        if (currentTime - mLastTimeKeyActionPerformed >= delayMillis) {
            if (messageType == MESSAGE_MOVE_MOUSE_POINTER) {
                performMousePointerAction(activeKey);
            } else if (messageType == MESSAGE_SCROLL_MOUSE_POINTER) {
                performMouseScrollAction(activeKey);
            }
            mLastTimeKeyActionPerformed = currentTime;
        }
        if (activeKey != KEY_NOT_SET) {
            if (Flags.enableMouseKeyEnhancement() && messageType == MESSAGE_MOVE_MOUSE_POINTER) {
                // Schedule next message using a target time based on the controlled clock
                long targetTime = this.mTimeSource.uptimeMillis() + delayMillis;
                Message nextMessage = Message.obtain(mHandler, messageType);
                mHandler.sendMessageAtTime(nextMessage, targetTime);
            } else {
                // Reschedule the message if the key is still active
                mHandler.sendEmptyMessageDelayed(messageType, INTERVAL_MILLIS);
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.CREATE_VIRTUAL_DEVICE)
    @Override
    public void onDestroy() {
        mHandler.post(() -> {
            // Clear mouse state
            mActiveMoveKey = KEY_NOT_SET;
            mActiveScrollKey = KEY_NOT_SET;
            mLastTimeKeyActionPerformed = 0;
            mDeviceKeyCodeMap.clear();
        });

        mHandler.removeCallbacksAndMessages(null);
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        mDeviceKeyCodeMap.remove(deviceId);
    }

    /**
     * The user can change the keyboard layout from settings at anytime, which would change
     * key character map for that device. Hence, we should use this callback to
     * update the key code to enum mapping if there is a change in the physical keyboard detected.
     *
     * @param deviceId The id of the input device that changed.
     */
    @Override
    public void onInputDeviceChanged(int deviceId) {
        InputDevice inputDevice = mInputManager.getInputDevice(deviceId);
        // Update the enum mapping only if input device that changed is a keyboard
        if (inputDevice.isFullKeyboard() && !mDeviceKeyCodeMap.contains(deviceId)) {
            initializeDeviceToEnumMap(inputDevice);
        }
    }
}
