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

package com.android.server.wm;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;

import static com.google.common.truth.Truth.assertWithMessage;

import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.inputmethod.Flags;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link ImeWindowToken} class.
 *
 * <p>Build/Install/Run:
 * atest WmTests:ImeWindowTokenTests
 */
@SmallTest
@Presubmit
@RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
@RunWith(WindowTestRunner.class)
public final class ImeWindowTokenTests extends WindowTestsBase {

    /**
     * Verifies that the ImeWindowToken visibility does not depend on its children visibility
     * (in contrast with the normal WindowToken).
     */
    @Test
    public void testSetVisible_independentOfChildrenVisibility() {
        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        assertWithMessage("Token should be initially hidden with no child")
                .that(token.isVisible())
                .isFalse();

        final WindowState win = newWindowBuilder("win", TYPE_APPLICATION).setWindowToken(token)
                .build();
        token.addWindow(win);
        assertWithMessage("Token should still be not visible with initially hidden child")
                .that(token.isVisible())
                .isFalse();
        assertWithMessage("Window should not be initially visibleRequested with hidden token")
                .that(win.isVisibleRequested())
                .isFalse();

        token.setVisible(true);
        assertWithMessage("Token should be visible after setVisible")
                .that(token.isVisible())
                .isTrue();
        assertWithMessage("Window should be visibleRequested with visible token, before hiding")
                .that(win.isVisibleRequested())
                .isTrue();

        win.hide(false, false);
        assertWithMessage("Token should still be visible with hidden child")
                .that(token.isVisible())
                .isTrue();
        assertWithMessage("Window should not be visibleRequested with visible token, after hiding")
                .that(win.isVisibleRequested())
                .isFalse();

        token.setVisible(false);
        assertWithMessage("Token should be not be visible after setVisible")
                .that(token.isVisible())
                .isFalse();
        assertWithMessage("Window should not be visible with hidden token")
                .that(win.isVisibleRequested())
                .isFalse();

        win.show(false, false);
        assertWithMessage("Token should still be not visible with hidden child")
                .that(token.isVisible())
                .isFalse();
        assertWithMessage("Window should not be visibleRequested with hidden token")
                .that(win.isVisibleRequested())
                .isFalse();
    }

    /**
     * Verifies that removing an IME Window Token will update the DisplayContent state only if
     * it is the current token.
     */
    @Test
    public void testRemoveImmediately_updatesDisplayImeWindowToken() {
        final ImeWindowToken token1 = createImeWindowToken(mDisplayContent);
        final ImeWindowToken token2 = createImeWindowToken(mDisplayContent);
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();

        imeContainer.setImeWindowToken(token1);
        assertWithMessage("Token1 should be the current display token")
                .that(imeContainer.getImeWindowToken())
                .isEqualTo(token1);

        token2.removeImmediately();
        assertWithMessage("Token1 should still be the current token after token2 is removed")
                .that(imeContainer.getImeWindowToken())
                .isEqualTo(token1);

        token1.removeImmediately();
        assertWithMessage("Token1 should no longer be the current token after it is removed")
                .that(imeContainer.getImeWindowToken())
                .isNull();
    }

    /**
     * Verifies that setting an ImeWindowToken on the display sets it visible, and also sets all the
     * other ImeWindowTokens not visible.
     */
    @Test
    public void testOnImeWindowTokenChanged_setsOnlyCurrentTokenVisible() {
        final ImeWindowToken token1 = createImeWindowToken(mDisplayContent);
        final ImeWindowToken token2 = createImeWindowToken(mDisplayContent);
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();

        imeContainer.setImeWindowToken(token1);
        assertWithMessage("Token1 should be the current display token")
                .that(imeContainer.getImeWindowToken())
                .isEqualTo(token1);
        assertWithMessage("Token1 should be visible")
                .that(token1.isVisible())
                .isTrue();

        imeContainer.setImeWindowToken(token2);
        assertWithMessage("Token2 should be the current display token")
                .that(imeContainer.getImeWindowToken())
                .isEqualTo(token2);
        assertWithMessage("Token2 should be visible")
                .that(token2.isVisible())
                .isTrue();
        assertWithMessage("Token1 should no longer be visible")
                .that(token1.isVisible())
                .isFalse();
    }

    /** Verifies that setting a null ImeWindowToken will also set the IME window to null. */
    @Test
    public void testOnImeWindowTokenChanged_nullTokenSetsNullImeWindow() {
        assertWithMessage("IME window should be initially null")
                .that(mDisplayContent.getImeWindow())
                .isNull();

        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        final WindowState imeWin = newWindowBuilder("imeWin", TYPE_INPUT_METHOD)
                .setWindowToken(token).build();
        token.addWindow(imeWin);
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();
        imeContainer.setImeWindowToken(token);
        assertWithMessage("IME window should be set on the display")
                .that(mDisplayContent.getImeWindow())
                .isEqualTo(imeWin);

        imeContainer.setImeWindowToken(null /* token */);
        assertWithMessage("IME window should now be null after ImeWindowToken was set to null")
                .that(mDisplayContent.getImeWindow())
                .isNull();
    }

    /** Verifies that setting an empty ImeWindowToken will also set the IME window to null. */
    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testOnImeWindowTokenChanged_emptyTokenSetsNullImeWindow() {
        assertWithMessage("IME window should be initially not null")
                .that(mDisplayContent.getImeWindow())
                .isNotNull();

        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();
        imeContainer.setImeWindowToken(token);
        assertWithMessage("IME window should now be null after an empty ImeWindowToken was set")
                .that(mDisplayContent.getImeWindow())
                .isNull();
    }

    /**
     * Verifies that setting a new ImeWindowToken will set one of its children as the IME window
     * only if the current IME window is null.
     */
    @Test
    public void testOnImeWindowTokenChanged_setChildAsImeWindowOnlyIfCurrentNullImeWindow() {
        assertWithMessage("IME window should be initially null")
                .that(mDisplayContent.getImeWindow())
                .isNull();

        final ImeWindowToken token1 = createImeWindowToken(mDisplayContent);
        final WindowState imeWin1 = newWindowBuilder("imeWin1", TYPE_INPUT_METHOD)
                .setWindowToken(token1).build();
        token1.addWindow(imeWin1);
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();
        imeContainer.setImeWindowToken(token1);
        assertWithMessage("Token 1 should be the current one")
                .that(imeContainer.getImeWindowToken())
                .isEqualTo(token1);
        assertWithMessage("IME window 1 should be set on the display")
                .that(mDisplayContent.getImeWindow())
                .isEqualTo(imeWin1);

        final ImeWindowToken token2 = createImeWindowToken(mDisplayContent);
        final WindowState imeWin2 = newWindowBuilder("imeWin2", TYPE_INPUT_METHOD)
                .setWindowToken(token2).build();
        token2.addWindow(imeWin2);
        imeContainer.setImeWindowToken(token2);
        assertWithMessage("Token 2 should be the current one")
                .that(imeContainer.getImeWindowToken())
                    .isEqualTo(token2);
        assertWithMessage("IME window 1 should still be the current one")
                .that(mDisplayContent.getImeWindow())
                .isEqualTo(imeWin1);
    }

    /**
     * Verifies that setting a new ImeWindowToken will set one of its children as the IME window
     * only if valid (window of type INPUT_METHOD that is touchable).
     */
    @Test
    public void testOnImeWindowTokenChanged_setChildAsImeWindowOnlyIfValid() {
        assertWithMessage("IME window should be initially null")
                .that(mDisplayContent.getImeWindow())
                .isNull();

        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        final WindowState appWin = newWindowBuilder("appWin", TYPE_APPLICATION)
                .setWindowToken(token).build();
        token.addWindow(appWin);
        final WindowState inkWin = newWindowBuilder("inkWin", TYPE_INPUT_METHOD)
                .setWindowToken(token).build();
        inkWin.mAttrs.flags |= FLAG_NOT_TOUCHABLE;
        token.addWindow(inkWin);
        final WindowState imeWin = newWindowBuilder("imeWin", TYPE_INPUT_METHOD)
                .setWindowToken(token).build();
        token.addWindow(imeWin);
        mDisplayContent.getImeContainer().setImeWindowToken(token);
        assertWithMessage("IME window 1 should be set on the display")
                .that(mDisplayContent.getImeWindow())
                .isEqualTo(imeWin);
    }

    /**
     * Verifies that setting a new ImeWindowToken will set the first valid one of its children
     * (in top-to-bottom traversal order) as the IME window.
     */
    @Test
    public void testOnImeWindowTokenChanged_setFirstValidChildAsImeWindow() {
        assertWithMessage("IME window should be initially null")
                .that(mDisplayContent.getImeWindow())
                .isNull();

        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        final WindowState imeWin1 = newWindowBuilder("imeWin1", TYPE_INPUT_METHOD)
                .setWindowToken(token).build();
        token.addWindow(imeWin1);
        final WindowState imeWin2 = newWindowBuilder("imeWin2", TYPE_INPUT_METHOD)
                .setWindowToken(token).build();
        token.addWindow(imeWin2);
        mDisplayContent.getImeContainer().setImeWindowToken(token);
        assertWithMessage("IME window 2 should be set on the display")
                .that(mDisplayContent.getImeWindow())
                .isEqualTo(imeWin2);
    }
}
