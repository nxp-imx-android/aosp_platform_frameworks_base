/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.InsetsSource.ID_IME;
import static android.view.InsetsState.ITYPE_CLIMATE_BAR;
import static android.view.InsetsState.ITYPE_EXTRA_NAVIGATION_BAR;
import static android.view.InsetsState.ITYPE_NAVIGATION_BAR;
import static android.view.InsetsState.ITYPE_STATUS_BAR;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.WindowContainer.POSITION_TOP;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.util.SparseArray;
import android.view.InsetsSourceControl;
import android.view.InsetsState;

import androidx.test.filters.SmallTest;

import com.android.internal.util.function.TriConsumer;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class InsetsStateControllerTest extends WindowTestsBase {

    @Test
    public void testStripForDispatch_navBar() {
        final WindowState navBar = createWindow(null, TYPE_APPLICATION, "navBar");
        final WindowState statusBar = createWindow(null, TYPE_APPLICATION, "statusBar");
        final WindowState ime = createWindow(null, TYPE_APPLICATION, "ime");

        // IME cannot be the IME target.
        ime.mAttrs.flags |= FLAG_NOT_FOCUSABLE;

        getController().getSourceProvider(ITYPE_STATUS_BAR).setWindowContainer(statusBar, null,
                null);
        getController().getSourceProvider(ITYPE_NAVIGATION_BAR).setWindowContainer(navBar, null,
                null);
        getController().getSourceProvider(ID_IME).setWindowContainer(ime, null, null);

        assertNull(navBar.getInsetsState().peekSource(ID_IME));
        assertNull(navBar.getInsetsState().peekSource(ITYPE_STATUS_BAR));
    }

    @Test
    public void testStripForDispatch_pip() {
        final WindowState statusBar = createWindow(null, TYPE_APPLICATION, "statusBar");
        final WindowState navBar = createWindow(null, TYPE_APPLICATION, "navBar");
        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");

        getController().getSourceProvider(ITYPE_STATUS_BAR).setWindowContainer(statusBar, null,
                null);
        getController().getSourceProvider(ITYPE_NAVIGATION_BAR).setWindowContainer(navBar, null,
                null);
        app.setWindowingMode(WINDOWING_MODE_PINNED);

        assertNull(app.getInsetsState().peekSource(ITYPE_STATUS_BAR));
        assertNull(app.getInsetsState().peekSource(ITYPE_NAVIGATION_BAR));
        assertNull(app.getInsetsState().peekSource(ID_IME));
    }

    @Test
    public void testStripForDispatch_freeform() {
        final WindowState statusBar = createWindow(null, TYPE_APPLICATION, "statusBar");
        final WindowState navBar = createWindow(null, TYPE_APPLICATION, "navBar");
        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");

        getController().getSourceProvider(ITYPE_STATUS_BAR).setWindowContainer(statusBar, null,
                null);
        getController().getSourceProvider(ITYPE_NAVIGATION_BAR).setWindowContainer(navBar, null,
                null);
        app.setWindowingMode(WINDOWING_MODE_FREEFORM);

        assertNull(app.getInsetsState().peekSource(ITYPE_STATUS_BAR));
        assertNull(app.getInsetsState().peekSource(ITYPE_NAVIGATION_BAR));
    }

    @Test
    public void testStripForDispatch_multiwindow_alwaysOnTop() {
        final WindowState statusBar = createWindow(null, TYPE_APPLICATION, "statusBar");
        final WindowState navBar = createWindow(null, TYPE_APPLICATION, "navBar");
        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");

        getController().getSourceProvider(ITYPE_STATUS_BAR).setWindowContainer(statusBar, null,
                null);
        getController().getSourceProvider(ITYPE_NAVIGATION_BAR).setWindowContainer(navBar, null,
                null);
        app.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        app.setAlwaysOnTop(true);

        assertNull(app.getInsetsState().peekSource(ITYPE_STATUS_BAR));
        assertNull(app.getInsetsState().peekSource(ITYPE_NAVIGATION_BAR));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testStripForDispatch_independentSources() {
        getController().getSourceProvider(ID_IME).setWindowContainer(mImeWindow, null, null);

        final WindowState app1 = createWindow(null, TYPE_APPLICATION, "app1");
        final WindowState app2 = createWindow(null, TYPE_APPLICATION, "app2");

        app1.mAboveInsetsState.addSource(getController().getRawInsetsState().peekSource(ID_IME));

        getController().getRawInsetsState().setSourceVisible(ID_IME, true);
        assertFalse(app2.getInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));
        assertTrue(app1.getInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testStripForDispatch_belowIme() {
        getController().getSourceProvider(ID_IME).setWindowContainer(mImeWindow, null, null);

        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");
        app.mAboveInsetsState.getOrCreateSource(ID_IME, ime())
                .setVisible(true)
                .setFrame(mImeWindow.getFrame());

        getController().getRawInsetsState().setSourceVisible(ID_IME, true);
        assertTrue(app.getInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testStripForDispatch_aboveIme() {
        getController().getSourceProvider(ID_IME).setWindowContainer(mImeWindow, null, null);

        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");

        getController().getRawInsetsState().setSourceVisible(ID_IME, true);
        assertFalse(app.getInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testStripForDispatch_imeOrderChanged() {
        // This can be the IME z-order target while app cannot be the IME z-order target.
        // This is also the only IME control target in this test, so IME won't be invisible caused
        // by the control-target change.
        mDisplayContent.updateImeInputAndControlTarget(
                createWindow(null, TYPE_APPLICATION, "base"));

        // Make IME and stay visible during the test.
        mImeWindow.setHasSurface(true);
        getController().getSourceProvider(ID_IME).setWindowContainer(mImeWindow, null, null);
        getController().onImeControlTargetChanged(
                mDisplayContent.getImeInputTarget().getWindowState());
        mDisplayContent.getImeInputTarget().getWindowState().setRequestedVisibleTypes(ime(), ime());
        getController().onInsetsModified(mDisplayContent.getImeInputTarget().getWindowState());

        // Send our spy window (app) into the system so that we can detect the invocation.
        final WindowState win = createWindow(null, TYPE_APPLICATION, "app");
        win.setHasSurface(true);
        final WindowToken parent = win.mToken;
        parent.removeChild(win);
        final WindowState app = spy(win);
        parent.addWindow(app);

        // Adding FLAG_NOT_FOCUSABLE makes app above IME.
        app.mAttrs.flags |= FLAG_NOT_FOCUSABLE;
        mDisplayContent.computeImeTarget(true);
        mDisplayContent.applySurfaceChangesTransaction();

        // app won't get visible IME insets while above IME even when IME is visible.
        assertTrue(getController().getRawInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));
        assertFalse(app.getInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));

        // Reset invocation counter.
        clearInvocations(app);

        // Removing FLAG_NOT_FOCUSABLE makes app below IME.
        app.mAttrs.flags &= ~FLAG_NOT_FOCUSABLE;
        mDisplayContent.computeImeTarget(true);
        mDisplayContent.applySurfaceChangesTransaction();
        app.mAboveInsetsState.getOrCreateSource(ID_IME, ime())
                .setVisible(true)
                .setFrame(mImeWindow.getFrame());

        // Make sure app got notified.
        verify(app, atLeastOnce()).notifyInsetsChanged();

        // app will get visible IME insets while below IME.
        assertTrue(app.getInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testStripForDispatch_childWindow_altFocusable() {
        getController().getSourceProvider(ID_IME).setWindowContainer(mImeWindow, null, null);

        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");
        final WindowState child = createWindow(app, TYPE_APPLICATION, "child");
        app.mAboveInsetsState.set(getController().getRawInsetsState());
        child.mAboveInsetsState.set(getController().getRawInsetsState());
        child.mAttrs.flags |= FLAG_ALT_FOCUSABLE_IM;

        mDisplayContent.computeImeTarget(true);
        mDisplayContent.setLayoutNeeded();
        mDisplayContent.applySurfaceChangesTransaction();

        getController().getRawInsetsState().setSourceVisible(ID_IME, true);
        assertTrue(app.getInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));
        assertFalse(child.getInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testStripForDispatch_childWindow_splitScreen() {
        getController().getSourceProvider(ID_IME).setWindowContainer(mImeWindow, null, null);

        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");
        final WindowState child = createWindow(app, TYPE_APPLICATION, "child");
        app.mAboveInsetsState.addSource(getController().getRawInsetsState().peekSource(ID_IME));
        child.mAttrs.flags |= FLAG_NOT_FOCUSABLE;
        child.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);

        mDisplayContent.computeImeTarget(true);
        mDisplayContent.setLayoutNeeded();
        mDisplayContent.applySurfaceChangesTransaction();

        getController().getRawInsetsState().setSourceVisible(ID_IME, true);
        assertTrue(app.getInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));
        assertFalse(child.getInsetsState().isSourceOrDefaultVisible(ID_IME, ime()));
    }

    @Test
    public void testImeForDispatch() {
        final WindowState statusBar = createWindow(null, TYPE_APPLICATION, "statusBar");
        final WindowState ime = createWindow(null, TYPE_INPUT_METHOD, "ime");

        // IME cannot be the IME target.
        ime.mAttrs.flags |= FLAG_NOT_FOCUSABLE;

        WindowContainerInsetsSourceProvider statusBarProvider =
                getController().getSourceProvider(ITYPE_STATUS_BAR);
        final SparseArray<TriConsumer<DisplayFrames, WindowContainer, Rect>> imeOverrideProviders =
                new SparseArray<>();
        imeOverrideProviders.put(TYPE_INPUT_METHOD, ((displayFrames, windowState, rect) ->
                rect.set(0, 1, 2, 3)));
        statusBarProvider.setWindowContainer(statusBar, null, imeOverrideProviders);
        getController().getSourceProvider(ID_IME).setWindowContainer(ime, null, null);
        statusBar.setControllableInsetProvider(statusBarProvider);
        statusBar.updateSourceFrame(statusBar.getFrame());

        statusBarProvider.onPostLayout();

        final InsetsState state = ime.getInsetsState();
        assertEquals(new Rect(0, 1, 2, 3), state.peekSource(ITYPE_STATUS_BAR).getFrame());
    }

    @Test
    public void testBarControllingWinChanged() {
        final WindowState navBar = createWindow(null, TYPE_APPLICATION, "navBar");
        final WindowState statusBar = createWindow(null, TYPE_APPLICATION, "statusBar");
        final WindowState climateBar = createWindow(null, TYPE_APPLICATION, "climateBar");
        final WindowState extraNavBar = createWindow(null, TYPE_APPLICATION, "extraNavBar");
        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");
        getController().getSourceProvider(ITYPE_STATUS_BAR).setWindowContainer(statusBar, null,
                null);
        getController().getSourceProvider(ITYPE_NAVIGATION_BAR).setWindowContainer(navBar, null,
                null);
        getController().getSourceProvider(ITYPE_CLIMATE_BAR).setWindowContainer(climateBar, null,
                null);
        getController().getSourceProvider(ITYPE_EXTRA_NAVIGATION_BAR).setWindowContainer(
                extraNavBar, null,
                null);
        getController().onBarControlTargetChanged(app, null, app, null);
        InsetsSourceControl[] controls = getController().getControlsForDispatch(app);
        assertEquals(4, controls.length);
    }

    @Test
    public void testControlRevoked() {
        final WindowState statusBar = createWindow(null, TYPE_APPLICATION, "statusBar");
        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");
        getController().getSourceProvider(ITYPE_STATUS_BAR).setWindowContainer(statusBar, null,
                null);
        getController().onBarControlTargetChanged(app, null, null, null);
        assertNotNull(getController().getControlsForDispatch(app));
        getController().onBarControlTargetChanged(null, null, null, null);
        assertNull(getController().getControlsForDispatch(app));
    }

    @Test
    public void testControlRevoked_animation() {
        final WindowState statusBar = createWindow(null, TYPE_APPLICATION, "statusBar");
        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");
        getController().getSourceProvider(ITYPE_STATUS_BAR).setWindowContainer(statusBar, null,
                null);
        getController().onBarControlTargetChanged(app, null, null, null);
        assertNotNull(getController().getControlsForDispatch(app));
        statusBar.cancelAnimation();
        assertNull(getController().getControlsForDispatch(app));
    }

    @Test
    public void testTransientVisibilityOfFixedRotationState() {
        final WindowState statusBar = createWindow(null, TYPE_APPLICATION, "statusBar");
        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");
        final WindowContainerInsetsSourceProvider provider = getController()
                .getSourceProvider(ITYPE_STATUS_BAR);
        provider.setWindowContainer(statusBar, null, null);

        final InsetsState rotatedState = new InsetsState(app.getInsetsState(),
                true /* copySources */);
        rotatedState.getOrCreateSource(ITYPE_STATUS_BAR, statusBars());
        spyOn(app.mToken);
        doReturn(rotatedState).when(app.mToken).getFixedRotationTransformInsetsState();
        assertTrue(rotatedState.isSourceOrDefaultVisible(ITYPE_STATUS_BAR, statusBars()));

        provider.getSource().setVisible(false);
        mDisplayContent.getInsetsPolicy().showTransient(statusBars(),
                true /* isGestureOnSystemBar */);

        assertTrue(mDisplayContent.getInsetsPolicy().isTransient(statusBars()));
        assertFalse(app.getInsetsState().isSourceOrDefaultVisible(ITYPE_STATUS_BAR, statusBars()));
    }

    @Test
    public void testUpdateAboveInsetsState_provideInsets() {
        final WindowState app = createTestWindow("app");
        final WindowState statusBar = createTestWindow("statusBar");
        final WindowState navBar = createTestWindow("navBar");

        getController().getSourceProvider(ITYPE_STATUS_BAR).setWindowContainer(statusBar, null,
                null);

        assertNull(app.mAboveInsetsState.peekSource(ITYPE_STATUS_BAR));
        assertNull(statusBar.mAboveInsetsState.peekSource(ITYPE_STATUS_BAR));
        assertNull(navBar.mAboveInsetsState.peekSource(ITYPE_STATUS_BAR));

        getController().updateAboveInsetsState(true /* notifyInsetsChange */);

        assertNotNull(app.mAboveInsetsState.peekSource(ITYPE_STATUS_BAR));
        assertNull(statusBar.mAboveInsetsState.peekSource(ITYPE_STATUS_BAR));
        assertNull(navBar.mAboveInsetsState.peekSource(ITYPE_STATUS_BAR));

        verify(app, atLeastOnce()).notifyInsetsChanged();
    }

    @Test
    public void testUpdateAboveInsetsState_receiveInsets() {
        final WindowState app = createTestWindow("app");
        final WindowState statusBar = createTestWindow("statusBar");
        final WindowState navBar = createTestWindow("navBar");

        getController().getSourceProvider(ITYPE_STATUS_BAR).setWindowContainer(statusBar, null,
                null);
        getController().getSourceProvider(ITYPE_NAVIGATION_BAR).setWindowContainer(navBar, null,
                null);

        assertNull(app.mAboveInsetsState.peekSource(ITYPE_STATUS_BAR));
        assertNull(app.mAboveInsetsState.peekSource(ITYPE_NAVIGATION_BAR));

        getController().updateAboveInsetsState(true /* notifyInsetsChange */);

        assertNotNull(app.mAboveInsetsState.peekSource(ITYPE_STATUS_BAR));
        assertNotNull(app.mAboveInsetsState.peekSource(ITYPE_NAVIGATION_BAR));

        verify(app, atLeastOnce()).notifyInsetsChanged();
    }

    @Test
    public void testUpdateAboveInsetsState_zOrderChanged() {
        final WindowState ime = createTestWindow("ime");
        final WindowState app = createTestWindow("app");
        final WindowState statusBar = createTestWindow("statusBar");
        final WindowState navBar = createTestWindow("navBar");

        getController().getSourceProvider(ID_IME).setWindowContainer(ime, null, null);

        waitUntilHandlersIdle();
        clearInvocations(mDisplayContent);
        getController().getSourceProvider(ID_IME).setClientVisible(true);
        waitUntilHandlersIdle();
        // The visibility change should trigger a traversal to notify the change.
        verify(mDisplayContent).notifyInsetsChanged(any());

        getController().getSourceProvider(ITYPE_STATUS_BAR).setWindowContainer(statusBar, null,
                null);
        getController().getSourceProvider(ITYPE_NAVIGATION_BAR).setWindowContainer(navBar, null,
                null);

        getController().updateAboveInsetsState(false /* notifyInsetsChange */);

        // ime is below others.
        assertNull(app.mAboveInsetsState.peekSource(ID_IME));
        assertNull(statusBar.mAboveInsetsState.peekSource(ID_IME));
        assertNull(navBar.mAboveInsetsState.peekSource(ID_IME));
        assertNotNull(ime.mAboveInsetsState.peekSource(ITYPE_STATUS_BAR));
        assertNotNull(ime.mAboveInsetsState.peekSource(ITYPE_NAVIGATION_BAR));

        ime.getParent().positionChildAt(POSITION_TOP, ime, true /* includingParents */);
        getController().updateAboveInsetsState(true /* notifyInsetsChange */);

        // ime is above others.
        assertNotNull(app.mAboveInsetsState.peekSource(ID_IME));
        assertNotNull(statusBar.mAboveInsetsState.peekSource(ID_IME));
        assertNotNull(navBar.mAboveInsetsState.peekSource(ID_IME));
        assertNull(ime.mAboveInsetsState.peekSource(ITYPE_STATUS_BAR));
        assertNull(ime.mAboveInsetsState.peekSource(ITYPE_NAVIGATION_BAR));

        verify(ime, atLeastOnce()).notifyInsetsChanged();
        verify(app, atLeastOnce()).notifyInsetsChanged();
        verify(statusBar, atLeastOnce()).notifyInsetsChanged();
        verify(navBar, atLeastOnce()).notifyInsetsChanged();
    }

    @Test
    public void testUpdateAboveInsetsState_imeTargetOnScreenBehavior() {
        final WindowToken imeToken = createTestWindowToken(TYPE_INPUT_METHOD, mDisplayContent);
        final WindowState ime = createWindow(null,  TYPE_INPUT_METHOD, imeToken, "ime");
        final WindowState app = createTestWindow("app");

        getController().getSourceProvider(ID_IME).setWindowContainer(ime, null, null);
        ime.getControllableInsetProvider().setServerVisible(true);

        app.mActivityRecord.setVisibility(true);
        mDisplayContent.setImeLayeringTarget(app);
        mDisplayContent.updateImeInputAndControlTarget(app);

        app.setRequestedVisibleTypes(ime(), ime());
        getController().onInsetsModified(app);
        assertTrue(ime.getControllableInsetProvider().getSource().isVisible());

        getController().updateAboveInsetsState(true /* notifyInsetsChange */);
        assertNotNull(app.getInsetsState().peekSource(ID_IME));
        verify(app, atLeastOnce()).notifyInsetsChanged();

        // Expect the app will still get IME insets even when the app was invisible.
        // (i.e. app invisible after locking the device)
        app.mActivityRecord.setVisible(false);
        app.setHasSurface(false);
        getController().updateAboveInsetsState(true /* notifyInsetsChange */);
        assertNotNull(app.getInsetsState().peekSource(ID_IME));
        verify(app, atLeastOnce()).notifyInsetsChanged();

        // Expect the app will get IME insets when the app is requesting visible.
        // (i.e. app is going to visible when unlocking the device)
        app.mActivityRecord.setVisibility(true);
        assertTrue(app.isVisibleRequested());
        getController().updateAboveInsetsState(true /* notifyInsetsChange */);
        assertNotNull(app.getInsetsState().peekSource(ID_IME));
        verify(app, atLeastOnce()).notifyInsetsChanged();
    }

    @Test
    public void testDispatchGlobalInsets() {
        final WindowState navBar = createWindow(null, TYPE_APPLICATION, "navBar");
        getController().getSourceProvider(ITYPE_NAVIGATION_BAR).setWindowContainer(navBar, null,
                null);
        final WindowState app = createWindow(null, TYPE_APPLICATION, "app");
        assertNull(app.getInsetsState().peekSource(ITYPE_NAVIGATION_BAR));
        app.mAttrs.receiveInsetsIgnoringZOrder = true;
        assertNotNull(app.getInsetsState().peekSource(ITYPE_NAVIGATION_BAR));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testGetInsetsHintForNewControl() {
        final WindowState app1 = createTestWindow("app1");
        final WindowState app2 = createTestWindow("app2");

        makeWindowVisible(mImeWindow);
        final InsetsSourceProvider imeInsetsProvider = getController().getSourceProvider(ID_IME);
        imeInsetsProvider.setWindowContainer(mImeWindow, null, null);
        imeInsetsProvider.updateSourceFrame(mImeWindow.getFrame());

        imeInsetsProvider.updateControlForTarget(app1, false);
        imeInsetsProvider.onPostLayout();
        final InsetsSourceControl control1 = imeInsetsProvider.getControl(app1);
        assertNotNull(control1);
        assertEquals(imeInsetsProvider.getSource().getFrame().height(),
                control1.getInsetsHint().bottom);

        // Simulate the IME control target updated from app1 to app2 when IME insets was invisible.
        imeInsetsProvider.setServerVisible(false);
        imeInsetsProvider.updateControlForTarget(app2, false);

        // Verify insetsHint of the new control is same as last IME source frame after the layout.
        imeInsetsProvider.onPostLayout();
        final InsetsSourceControl control2 = imeInsetsProvider.getControl(app2);
        assertNotNull(control2);
        assertEquals(imeInsetsProvider.getSource().getFrame().height(),
                control2.getInsetsHint().bottom);
    }

    private WindowState createTestWindow(String name) {
        final WindowState win = createWindow(null, TYPE_APPLICATION, name);
        win.setHasSurface(true);
        spyOn(win);
        return win;
    }

    private InsetsStateController getController() {
        return mDisplayContent.getInsetsStateController();
    }
}
