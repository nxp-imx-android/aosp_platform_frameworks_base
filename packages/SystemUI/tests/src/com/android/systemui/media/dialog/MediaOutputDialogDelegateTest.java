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

import static android.permission.flags.Flags.FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
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
import com.android.media.flags.Flags;
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
public class MediaOutputDialogDelegateTest extends SysuiTestCase {

    private static final String TEST_PACKAGE = "test_package";

    // Mock
    private MediaOutputAdapter mMediaOutputAdapter = mock(MediaOutputAdapter.class);
    private BroadcastSender mBroadcastSender = mock(BroadcastSender.class);
    private final DialogTransitionAnimator mDialogTransitionAnimator = mock(
            DialogTransitionAnimator.class);
    private final UiEventLogger mUiEventLogger = mock(UiEventLogger.class);

    private MediaOutputDialogDelegate mMediaOutputDialogDelegate;
    private MediaSwitchingController mMediaSwitchingController = mock(
            MediaSwitchingController.class);

    @Before
    public void setUp() {
        MediaOutputColorScheme mockedColorScheme = mock(MediaOutputColorScheme.class);
        when(mMediaSwitchingController.getColorScheme()).thenReturn(mockedColorScheme);
        when(mMediaSwitchingController.getStopButtonStringRes()).thenReturn(
                R.string.media_output_dialog_button_stop_casting);
        mMediaOutputDialogDelegate = createDialog();
        mMediaOutputDialogDelegate.mAdapter = mMediaOutputAdapter;
        mMediaOutputDialogDelegate.onCreate(new Bundle());
    }

    @Test
    public void onCreate_noAppOpenIntent_metadataSectionNonClickable() {
        when(mMediaSwitchingController.getAppLaunchIntent()).thenReturn(null);

        mMediaOutputDialogDelegate = createDialog();
        mMediaOutputDialogDelegate.onCreate(new Bundle());
        final LinearLayout mediaMetadataSectionLayout =
                mMediaOutputDialogDelegate.mDialogView.requireViewById(
                        R.id.media_metadata_section);

        assertThat(mediaMetadataSectionLayout.isClickable()).isFalse();
    }

    @Test
    public void onCreate_appOpenIntentAvailable_metadataSectionClickable() {
        when(mMediaSwitchingController.getAppLaunchIntent()).thenReturn(new Intent(TEST_PACKAGE));

        mMediaOutputDialogDelegate = createDialog();
        mMediaOutputDialogDelegate.onCreate(new Bundle());
        final LinearLayout mediaMetadataSectionLayout =
                mMediaOutputDialogDelegate.mDialogView.requireViewById(
                        R.id.media_metadata_section);

        assertThat(mediaMetadataSectionLayout.isClickable()).isTrue();
    }

    @Test
    public void refresh_withIconCompat_iconIsVisible() {
        when(mMediaSwitchingController.getHeaderIcon()).thenReturn(IconCompat.createWithBitmap(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)));

        mMediaOutputDialogDelegate.refresh();
        final ImageView view = mMediaOutputDialogDelegate.mDialogView.requireViewById(
                R.id.header_icon);

        assertThat(view.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void refresh_noIcon_iconLayoutNotVisible() {
        when(mMediaSwitchingController.getHeaderIcon()).thenReturn(null);

        mMediaOutputDialogDelegate.refresh();
        final ImageView view = mMediaOutputDialogDelegate.mDialogView.requireViewById(
                R.id.header_icon);

        assertThat(view.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void refresh_checkTitle() {
        String headerTitle = "test_string";
        when(mMediaSwitchingController.getHeaderTitle()).thenReturn(headerTitle);

        mMediaOutputDialogDelegate.refresh();
        final TextView titleView = mMediaOutputDialogDelegate.mDialogView.requireViewById(
                R.id.header_title);

        assertThat(titleView.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(titleView.getText().toString()).isEqualTo(headerTitle);
    }

    @Test
    public void refresh_withSubtitle_checkSubtitle() {
        String headerSubtitle = "test_string";
        when(mMediaSwitchingController.getHeaderSubTitle()).thenReturn(headerSubtitle);

        mMediaOutputDialogDelegate.refresh();
        final TextView subtitleView = mMediaOutputDialogDelegate.mDialogView.requireViewById(
                R.id.header_subtitle);

        assertThat(subtitleView.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(subtitleView.getText().toString()).isEqualTo(headerSubtitle);
    }

    @Test
    public void refresh_noSubtitle_checkSubtitle() {
        mMediaOutputDialogDelegate.refresh();
        final TextView subtitleView = mMediaOutputDialogDelegate.mDialogView.requireViewById(
                R.id.header_subtitle);

        assertThat(subtitleView.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void refresh_inDragging_notUpdateAdapter() {
        when(mMediaOutputAdapter.isDragging()).thenReturn(true);
        mMediaOutputDialogDelegate.refresh();

        verify(mMediaOutputAdapter, never()).notifyDataSetChanged();
    }

    @Test
    public void refresh_inDragging_directSetRefreshingToFalse() {
        when(mMediaOutputAdapter.isDragging()).thenReturn(true);
        mMediaOutputDialogDelegate.refresh();

        assertThat(mMediaSwitchingController.isRefreshing()).isFalse();
    }

    @Test
    public void refresh_notInDragging_verifyUpdateAdapter() {
        when(mMediaOutputAdapter.getCurrentActivePosition()).thenReturn(-1);
        when(mMediaOutputAdapter.isDragging()).thenReturn(false);
        mMediaOutputDialogDelegate.refresh();

        verify(mMediaOutputAdapter).updateItems();
    }

    @Test
    public void dismissDialog_closesDialogByBroadcastSender() {
        mMediaOutputDialogDelegate.dismissDialog();

        verify(mBroadcastSender).closeSystemDialogs();
    }

    @Test
    public void refresh_checkStopText() {
        int stopResId = R.string.media_output_dialog_button_stop_casting;
        when(mMediaSwitchingController.getStopButtonStringRes()).thenReturn(stopResId);
        mMediaOutputDialogDelegate.refresh();
        final Button stop = mMediaOutputDialogDelegate.mDialogView.requireViewById(R.id.stop);

        assertThat(stop.getText().toString()).isEqualTo(mContext.getString(stopResId));
    }

    @Test
    public void onCreate_normalScreenHeight_showsNormalIconAndMetadata () {
        when(mMediaSwitchingController.getAppIcon()).thenReturn(new BitmapDrawable());
        mContext.getResources().getConfiguration().screenHeightDp = 500;

        mMediaOutputDialogDelegate.refresh();

        assertThat(mMediaOutputDialogDelegate.mDialogView
                .requireViewById(R.id.app_source_icon_small_screen_height)
                .getVisibility()).isEqualTo(View.GONE);
        assertThat(mMediaOutputDialogDelegate.mDialogView.requireViewById(R.id.app_source_icon)
                .getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onCreate_smallScreenHeight_showsSmallIconAndHidesMetadata () {
        when(mMediaSwitchingController.getAppIcon()).thenReturn(new BitmapDrawable());
        mContext.getResources().getConfiguration().screenHeightDp = 300;

        mMediaOutputDialogDelegate.refresh();

        assertThat(mMediaOutputDialogDelegate.mDialogView.requireViewById(R.id.app_source_icon)
                .getVisibility()).isEqualTo(View.GONE);
        assertThat(mMediaOutputDialogDelegate.mDialogView
                .requireViewById(R.id.app_source_icon_small_screen_height)
                .getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void refresh_fromNormalToSmallScreenHeight_showsSmallIcon () {
        when(mMediaSwitchingController.getAppIcon()).thenReturn(new BitmapDrawable());
        mContext.getResources().getConfiguration().screenHeightDp = 500;
        mMediaOutputDialogDelegate.refresh();
        mContext.getResources().getConfiguration().screenHeightDp = 300;

        mMediaOutputDialogDelegate.refresh();

        assertThat(mMediaOutputDialogDelegate.mDialogView.requireViewById(R.id.app_source_icon)
                .getVisibility()).isEqualTo(View.GONE);
        assertThat(mMediaOutputDialogDelegate.mDialogView
                .requireViewById(R.id.app_source_icon_small_screen_height)
                .getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void refresh_fromSmallToNormalScreenHeight_showsNormalIcon () {
        when(mMediaSwitchingController.getAppIcon()).thenReturn(new BitmapDrawable());
        mContext.getResources().getConfiguration().screenHeightDp = 300;
        mMediaOutputDialogDelegate.refresh();
        mContext.getResources().getConfiguration().screenHeightDp = 500;

        mMediaOutputDialogDelegate.refresh();

        assertThat(mMediaOutputDialogDelegate.mDialogView
                .requireViewById(R.id.app_source_icon_small_screen_height)
                .getVisibility()).isEqualTo(View.GONE);
        assertThat(mMediaOutputDialogDelegate.mDialogView.requireViewById(R.id.app_source_icon)
                .getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onStopButtonClick_releaseSession() {
        when(mMediaSwitchingController.getStopButtonStringRes())
                .thenReturn(R.string.media_output_dialog_button_stop_casting);
        mMediaOutputDialogDelegate.onStopButtonClick();

        verify(mMediaSwitchingController).releaseSession();
        verify(mDialogTransitionAnimator).disableAllCurrentDialogsExitAnimations();
    }

    @Test
    public void onCreate_ShouldLogVisibility() {
        verify(mUiEventLogger)
                .log(MediaOutputDialogDelegate.MediaOutputEvent.MEDIA_OUTPUT_DIALOG_SHOW);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    public void refresh_noMissingPermissionsResolveIntent_warningSectionGone() {
        when(mMediaSwitchingController.getMissingPermissionsResolveIntent()).thenReturn(null);

        mMediaOutputDialogDelegate.refresh();

        assertThat(mMediaOutputDialogDelegate.mDialogView.requireViewById(R.id.warning_section)
                .getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    public void onWarningFixButtonClick_callsController() {
        mMediaOutputDialogDelegate = createDialog();
        mMediaOutputDialogDelegate.onCreate(new Bundle());
        Intent testIntent = new Intent("test.action.intent");
        when(mMediaSwitchingController.getMissingPermissionsResolveIntent()).thenReturn(testIntent);

        mMediaOutputDialogDelegate.refresh();
        mMediaOutputDialogDelegate.mDialogView.requireViewById(R.id.warning_fix_button)
                .performClick();

        verify(mMediaSwitchingController).tryToLaunchMissingPermissionsResolveIntent();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MEDIA_OUTPUT_SWITCHER_ENTRY_POINT_THEMING)
    public void refresh_withoutUsingSystemColors_updatesColorScheme() {
        mMediaOutputDialogDelegate = createDialog(/* useSystemColors= */ false);
        mMediaOutputDialogDelegate.onCreate(new Bundle());
        IconCompat icon = IconCompat.createWithBitmap(
                Bitmap.createBitmap(/* width= */ 1, /* height= */ 1, Bitmap.Config.ARGB_8888));
        when(mMediaSwitchingController.getHeaderIcon()).thenReturn(icon);

        mMediaOutputDialogDelegate.refresh();

        verify(mMediaSwitchingController).updateCurrentColorScheme(any(), anyBoolean());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MEDIA_OUTPUT_SWITCHER_ENTRY_POINT_THEMING)
    public void refresh_withUsingSystemColors_doesNotUpdateColorScheme() {
        mMediaOutputDialogDelegate = createDialog(/* useSystemColors= */ true);
        mMediaOutputDialogDelegate.onCreate(new Bundle());
        IconCompat icon = IconCompat.createWithBitmap(
                Bitmap.createBitmap(/* width= */ 1, /* height= */ 1, Bitmap.Config.ARGB_8888));
        when(mMediaSwitchingController.getHeaderIcon()).thenReturn(icon);

        mMediaOutputDialogDelegate.refresh();

        verify(mMediaSwitchingController, never()).updateCurrentColorScheme(any(), anyBoolean());
    }

    private MediaOutputDialogDelegate createDialog(boolean useSystemColors) {
        return new MediaOutputDialogDelegate(
                mContext,
                /* aboveStatusBar= */ false,
                mBroadcastSender,
                mMediaSwitchingController,
                mDialogTransitionAnimator,
                mUiEventLogger,
                /* mIncludePlaybackAndAppMetadata= */ true,
                /* mOnDialogEventListener= */ null,
                useSystemColors
        );
    }

    private MediaOutputDialogDelegate createDialog() {
        return createDialog(/* useSystemColors= */ true);
    }
}
