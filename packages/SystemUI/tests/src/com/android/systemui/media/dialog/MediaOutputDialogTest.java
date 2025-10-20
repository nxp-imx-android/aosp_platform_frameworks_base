/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.media.dialog;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.testing.TestableLooper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.drawable.IconCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.logging.UiEventLogger;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.res.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class MediaOutputDialogTest extends SysuiTestCase {

    private static final String TEST_PACKAGE = "test_package";

    // Mock
    private MediaOutputAdapter mMediaOutputAdapter = mock(MediaOutputAdapter.class);
    private BroadcastSender mBroadcastSender = mock(BroadcastSender.class);
    private final DialogTransitionAnimator mDialogTransitionAnimator = mock(
            DialogTransitionAnimator.class);
    private final UiEventLogger mUiEventLogger = mock(UiEventLogger.class);

    private MediaOutputDialog mMediaOutputDialog;
    private MediaSwitchingController mMediaSwitchingController = mock(
            MediaSwitchingController.class);

    @Before
    public void setUp() {
        MediaOutputColorScheme mockedColorScheme = mock(MediaOutputColorScheme.class);
        when(mMediaSwitchingController.getColorScheme()).thenReturn(mockedColorScheme);
        when(mMediaSwitchingController.getStopButtonStringRes()).thenReturn(
                R.string.media_output_dialog_button_stop_casting);
        mMediaOutputDialog = createDialog();
        mMediaOutputDialog.mAdapter = mMediaOutputAdapter;
        mMediaOutputDialog.onCreate(new Bundle());
    }

    @Test
    public void onCreate_noAppOpenIntent_metadataSectionNonClickable() {
        when(mMediaSwitchingController.getAppLaunchIntent()).thenReturn(null);

        mMediaOutputDialog = createDialog();
        mMediaOutputDialog.onCreate(new Bundle());
        final LinearLayout mediaMetadataSectionLayout =
                mMediaOutputDialog.mDialogView.requireViewById(
                        R.id.media_metadata_section);

        assertThat(mediaMetadataSectionLayout.isClickable()).isFalse();
    }

    @Test
    public void onCreate_appOpenIntentAvailable_metadataSectionClickable() {
        when(mMediaSwitchingController.getAppLaunchIntent()).thenReturn(new Intent(TEST_PACKAGE));

        mMediaOutputDialog = createDialog();
        mMediaOutputDialog.onCreate(new Bundle());
        final LinearLayout mediaMetadataSectionLayout =
                mMediaOutputDialog.mDialogView.requireViewById(
                        R.id.media_metadata_section);

        assertThat(mediaMetadataSectionLayout.isClickable()).isTrue();
    }

    @Test
    public void refresh_withIconCompat_iconIsVisible() {
        when(mMediaSwitchingController.getHeaderIcon()).thenReturn(IconCompat.createWithBitmap(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)));

        mMediaOutputDialog.refresh();
        final ImageView view = mMediaOutputDialog.mDialogView.requireViewById(
                R.id.header_icon);

        assertThat(view.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void refresh_noIcon_iconLayoutNotVisible() {
        when(mMediaSwitchingController.getHeaderIcon()).thenReturn(null);

        mMediaOutputDialog.refresh();
        final ImageView view = mMediaOutputDialog.mDialogView.requireViewById(
                R.id.header_icon);

        assertThat(view.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void refresh_checkTitle() {
        String headerTitle = "test_string";
        when(mMediaSwitchingController.getHeaderTitle()).thenReturn(headerTitle);

        mMediaOutputDialog.refresh();
        final TextView titleView = mMediaOutputDialog.mDialogView.requireViewById(
                R.id.header_title);

        assertThat(titleView.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(titleView.getText().toString()).isEqualTo(headerTitle);
    }

    @Test
    public void refresh_withSubtitle_checkSubtitle() {
        String headerSubtitle = "test_string";
        when(mMediaSwitchingController.getHeaderSubTitle()).thenReturn(headerSubtitle);

        mMediaOutputDialog.refresh();
        final TextView subtitleView = mMediaOutputDialog.mDialogView.requireViewById(
                R.id.header_subtitle);

        assertThat(subtitleView.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(subtitleView.getText().toString()).isEqualTo(headerSubtitle);
    }

    @Test
    public void refresh_noSubtitle_checkSubtitle() {
        mMediaOutputDialog.refresh();
        final TextView subtitleView = mMediaOutputDialog.mDialogView.requireViewById(
                R.id.header_subtitle);

        assertThat(subtitleView.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void refresh_inDragging_notUpdateAdapter() {
        when(mMediaOutputAdapter.isDragging()).thenReturn(true);
        mMediaOutputDialog.refresh();

        verify(mMediaOutputAdapter, never()).notifyDataSetChanged();
    }

    @Test
    public void refresh_inDragging_directSetRefreshingToFalse() {
        when(mMediaOutputAdapter.isDragging()).thenReturn(true);
        mMediaOutputDialog.refresh();

        assertThat(mMediaSwitchingController.isRefreshing()).isFalse();
    }

    @Test
    public void refresh_notInDragging_verifyUpdateAdapter() {
        when(mMediaOutputAdapter.getCurrentActivePosition()).thenReturn(-1);
        when(mMediaOutputAdapter.isDragging()).thenReturn(false);
        mMediaOutputDialog.refresh();

        verify(mMediaOutputAdapter).updateItems();
    }

    @Test
    public void dismissDialog_closesDialogByBroadcastSender() {
        mMediaOutputDialog.dismissDialog();

        verify(mBroadcastSender).closeSystemDialogs();
    }

    @Test
    public void refresh_checkStopText() {
        int stopResId = R.string.media_output_dialog_button_stop_casting;
        when(mMediaSwitchingController.getStopButtonStringRes()).thenReturn(stopResId);
        mMediaOutputDialog.refresh();
        final Button stop = mMediaOutputDialog.mDialogView.requireViewById(R.id.stop);

        assertThat(stop.getText().toString()).isEqualTo(mContext.getString(stopResId));
    }

    @Test
    public void onStopButtonClick_releaseSession() {
        when(mMediaSwitchingController.hasStopButton()).thenReturn(true);
        mMediaOutputDialog.onStopButtonClick();

        verify(mMediaSwitchingController).releaseSession();
        verify(mDialogTransitionAnimator).disableAllCurrentDialogsExitAnimations();
    }

    @Test
    public void onCreate_ShouldLogVisibility() {
        verify(mUiEventLogger)
                .log(MediaOutputDialog.MediaOutputEvent.MEDIA_OUTPUT_DIALOG_SHOW);
    }

    private MediaOutputDialog createDialog() {
        return new MediaOutputDialog(
                mContext,
                false,
                mBroadcastSender,
                mMediaSwitchingController,
                mDialogTransitionAnimator,
                mUiEventLogger,
                true,
                null);
    }
}
