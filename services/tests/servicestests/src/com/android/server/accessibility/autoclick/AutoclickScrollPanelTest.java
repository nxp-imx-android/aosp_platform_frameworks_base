/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.accessibility.autoclick;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.view.WindowManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test cases for {@link AutoclickScrollPanel}. */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AutoclickScrollPanelTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public TestableContext mTestableContext =
            new TestableContext(getInstrumentation().getContext());

    @Mock private WindowManager mMockWindowManager;
    private AutoclickScrollPanel mScrollPanel;

    @Before
    public void setUp() {
        mTestableContext.addMockSystemService(Context.WINDOW_SERVICE, mMockWindowManager);
        mScrollPanel = new AutoclickScrollPanel(mTestableContext, mMockWindowManager);
    }

    @Test
    public void show_addsViewToWindowManager() {
        mScrollPanel.show();

        // Verify view is added to window manager.
        verify(mMockWindowManager).addView(any(), any(WindowManager.LayoutParams.class));

        // Verify isVisible reflects correct state.
        assertThat(mScrollPanel.isVisible()).isTrue();
    }

    @Test
    public void show_alreadyVisible_doesNotAddAgain() {
        // Show twice.
        mScrollPanel.show();
        mScrollPanel.show();

        // Verify addView was only called once.
        verify(mMockWindowManager, times(1)).addView(any(), any());
    }

    @Test
    public void hide_removesViewFromWindowManager() {
        // First show the panel.
        mScrollPanel.show();
        // Then hide it.
        mScrollPanel.hide();
        // Verify view is removed from window manager.
        verify(mMockWindowManager).removeView(any());
        // Verify scroll panel is hidden.
        assertThat(mScrollPanel.isVisible()).isFalse();
    }
}
