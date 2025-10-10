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

import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.WallpaperColors;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.drawable.IconCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;

import com.google.android.material.button.MaterialButton;

import java.util.concurrent.Executor;

/** Base dialog for media output UI */
public class MediaOutputBaseDialog extends SystemUIDialog
        implements MediaSwitchingController.Callback, Window.Callback {

    private static final String TAG = "MediaOutputDialog";
    public static final int SMALL_SCREEN_HEIGHT_DP = 400;

    protected final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private final LinearLayoutManager mLayoutManager;

    final Context mContext;
    final MediaSwitchingController mMediaSwitchingController;
    final BroadcastSender mBroadcastSender;

    /**
     * Signals whether the dialog should NOT show app-related metadata.
     *
     * <p>A metadata-less dialog hides the title, subtitle, and app icon in the header.
     */
    private final boolean mIncludePlaybackAndAppMetadata;

    @VisibleForTesting
    View mDialogView;
    private TextView mHeaderTitle;
    private TextView mHeaderSubtitle;
    private ImageView mHeaderIcon;
    private ImageView mAppResourceIcon;
    private RecyclerView mDevicesRecyclerView;
    private ViewGroup mDeviceListLayout;
    private ViewGroup mQuickAccessShelf;
    private MaterialButton mConnectDeviceButton;
    private MaterialButton mAudioSharingButton;
    private LinearLayout mMediaMetadataSectionLayout;
    private Button mDoneButton;
    private ViewGroup mDialogFooter;
    private Button mStopButton;
    private WallpaperColors mWallpaperColors;
    private boolean mDismissing;

    @VisibleForTesting
    MediaOutputAdapter mAdapter;

    protected Executor mExecutor;

    private final DialogTransitionAnimator mDialogTransitionAnimator;
    private final UiEventLogger mUiEventLogger;
    @Nullable
    private final MediaOutputDialog.OnDialogEventListener mOnDialogEventListener;

    private class LayoutManagerWrapper extends LinearLayoutManager {
        LayoutManagerWrapper(Context context) {
            super(context);
        }

        @Override
        public void onLayoutCompleted(RecyclerView.State state) {
            super.onLayoutCompleted(state);
            mMediaSwitchingController.setRefreshing(false);
            mMediaSwitchingController.refreshDataSetIfNeeded();
        }
    }

    public MediaOutputBaseDialog(
            Context context,
            boolean aboveStatusbar,
            BroadcastSender broadcastSender,
            MediaSwitchingController mediaSwitchingController,
            DialogTransitionAnimator dialogTransitionAnimator,
            UiEventLogger uiEventLogger,
            boolean includePlaybackAndAppMetadata,
            @Nullable MediaOutputDialog.OnDialogEventListener onDialogEventListener) {
        super(context, R.style.Theme_SystemUI_Dialog_Media);

        // Save the context that is wrapped with our theme.
        mContext = getContext();
        mBroadcastSender = broadcastSender;
        mMediaSwitchingController = mediaSwitchingController;
        mLayoutManager = new LayoutManagerWrapper(mContext);
        mIncludePlaybackAndAppMetadata = includePlaybackAndAppMetadata;
        mDialogTransitionAnimator = dialogTransitionAnimator;
        mUiEventLogger = uiEventLogger;
        mAdapter = new MediaOutputAdapter(mMediaSwitchingController);
        mOnDialogEventListener = onDialogEventListener;
        if (!aboveStatusbar) {
            getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDialogView = LayoutInflater.from(mContext).inflate(R.layout.media_output_dialog, null);
        final Window window = getWindow();
        final WindowManager.LayoutParams lp = window.getAttributes();
        lp.gravity = Gravity.CENTER;
        // Config insets to make sure the layout is above the navigation bar
        lp.setFitInsetsTypes(statusBars() | navigationBars());
        lp.setFitInsetsSides(WindowInsets.Side.all());
        lp.setFitInsetsIgnoringVisibility(true);
        window.setAttributes(lp);
        window.setContentView(mDialogView);
        window.setTitle(mContext.getString(R.string.media_output_dialog_accessibility_title));
        window.setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL);

        mHeaderTitle = mDialogView.requireViewById(R.id.header_title);
        mHeaderSubtitle = mDialogView.requireViewById(R.id.header_subtitle);
        mHeaderIcon = mDialogView.requireViewById(R.id.header_icon);
        mQuickAccessShelf = mDialogView.requireViewById(R.id.quick_access_shelf);
        mConnectDeviceButton = mDialogView.requireViewById(R.id.connect_device);
        mAudioSharingButton = mDialogView.requireViewById(R.id.audio_sharing);
        mDevicesRecyclerView = mDialogView.requireViewById(R.id.list_result);
        mDialogFooter = mDialogView.requireViewById(R.id.dialog_footer);
        mMediaMetadataSectionLayout = mDialogView.requireViewById(R.id.media_metadata_section);
        mDeviceListLayout = mDialogView.requireViewById(R.id.device_list);
        mDoneButton = mDialogView.requireViewById(R.id.done);
        mStopButton = mDialogView.requireViewById(R.id.stop);

        boolean isSmallScreenHeight =
                mContext.getResources().getConfiguration().screenHeightDp <= SMALL_SCREEN_HEIGHT_DP;
        mAppResourceIcon = mDialogView.requireViewById(
                isSmallScreenHeight ? R.id.app_source_icon_small_screen_height
                        : R.id.app_source_icon);
        mAppResourceIcon.setVisibility(View.VISIBLE);
        mMediaMetadataSectionLayout.setVisibility(isSmallScreenHeight ? View.GONE : View.VISIBLE);

        // Init device list
        mLayoutManager.setAutoMeasureEnabled(true);
        mDevicesRecyclerView.setLayoutManager(mLayoutManager);
        mDevicesRecyclerView.setAdapter(mAdapter);
        mDevicesRecyclerView.setHasFixedSize(false);
        // Init bottom buttons
        mDoneButton.setOnClickListener(v -> dismiss());
        mStopButton.setOnClickListener(v -> onStopButtonClick());
        if (mMediaSwitchingController.getAppLaunchIntent() != null) {
            // For a11y purposes only add listener if a section is clickable.
            mMediaMetadataSectionLayout.setOnClickListener(
                    mMediaSwitchingController::tryToLaunchMediaApplication);
        }

        mDismissing = false;

        // Change footer background color on scroll.
        mDevicesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                changeFooterColorForScroll();
            }
        });
        // Changes footer background when the list dimensions changed without scroll.
        mDevicesRecyclerView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    changeFooterColorForScroll();
                });

        mUiEventLogger.log(MediaOutputEvent.MEDIA_OUTPUT_DIALOG_SHOW);

        if (mOnDialogEventListener != null) {
            mOnDialogEventListener.onCreate(this);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (mOnDialogEventListener != null) {
            mOnDialogEventListener.onConfigurationChanged(this, configuration);
        }
    }

    @Override
    public void dismiss() {
        // TODO(287191450): remove this once expensive binder calls are removed from refresh().
        // Due to these binder calls on the UI thread, calling refresh() during dismissal causes
        // significant frame drops for the dismissal animation. Since the dialog is going away
        // anyway, we use this state to turn refresh() into a no-op.
        mDismissing = true;
        super.dismiss();
    }

    @Override
    public void start() {
        mMediaSwitchingController.start(this);
    }

    @Override
    public void stop() {
        mMediaSwitchingController.stop();
    }

    @VisibleForTesting
    void refresh() {
        refresh(false);
    }

    void refresh(boolean deviceSetChanged) {
        // TODO(287191450): remove binder calls in this method from the UI thread.
        // If the dialog is going away or is already refreshing, do nothing.
        if (mDismissing || mMediaSwitchingController.isRefreshing()) {
            return;
        }
        mMediaSwitchingController.setRefreshing(true);
        // Update header icon
        final IconCompat headerIcon = mMediaSwitchingController.getHeaderIcon();
        final IconCompat appSourceIcon = mMediaSwitchingController.getNotificationSmallIcon();
        boolean colorSetUpdated = false;
        if (headerIcon != null) {
            Icon icon = headerIcon.toIcon(mContext);
            if (icon.getType() != Icon.TYPE_BITMAP && icon.getType() != Icon.TYPE_ADAPTIVE_BITMAP) {
                // icon doesn't support getBitmap, use default value for color scheme
                updateButtonBackgroundColorFilter();
                updateDialogBackgroundColor();
            } else {
                Configuration config = mContext.getResources().getConfiguration();
                int currentNightMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                boolean isDarkThemeOn = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
                WallpaperColors wallpaperColors = WallpaperColors.fromBitmap(icon.getBitmap());
                colorSetUpdated = !wallpaperColors.equals(mWallpaperColors);
                if (colorSetUpdated) {
                    mMediaSwitchingController.updateCurrentColorScheme(wallpaperColors,
                            isDarkThemeOn);
                    updateButtonBackgroundColorFilter();
                    updateDialogBackgroundColor();
                }
            }
            mHeaderIcon.setVisibility(View.VISIBLE);
            mHeaderIcon.setImageIcon(icon);
        } else {
            updateButtonBackgroundColorFilter();
            updateDialogBackgroundColor();
            mHeaderIcon.setVisibility(View.GONE);
        }

        if (!mIncludePlaybackAndAppMetadata) {
            mAppResourceIcon.setVisibility(View.GONE);
        } else if (appSourceIcon != null) {
            Icon appIcon = appSourceIcon.toIcon(mContext);
            mAppResourceIcon.setColorFilter(
                    mMediaSwitchingController.getColorScheme().getSecondary());
            mAppResourceIcon.setImageIcon(appIcon);
        } else {
            Drawable appIconDrawable = mMediaSwitchingController.getAppSourceIconFromPackage();
            if (appIconDrawable != null) {
                mAppResourceIcon.setImageDrawable(appIconDrawable);
            } else {
                mAppResourceIcon.setVisibility(View.GONE);
            }
        }

        if (!mIncludePlaybackAndAppMetadata) {
            mHeaderTitle.setVisibility(View.GONE);
            mHeaderSubtitle.setVisibility(View.GONE);
        } else {
            // Update title and subtitle
            mHeaderTitle.setText(mMediaSwitchingController.getHeaderTitle());
            mHeaderTitle.setTextColor(mMediaSwitchingController.getColorScheme().getOnSurface());
            final CharSequence subTitle = mMediaSwitchingController.getHeaderSubTitle();
            if (TextUtils.isEmpty(subTitle)) {
                mHeaderSubtitle.setVisibility(View.GONE);
                mHeaderTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            } else {
                mHeaderSubtitle.setVisibility(View.VISIBLE);
                mHeaderSubtitle.setText(subTitle);
                mHeaderSubtitle.setTextColor(mMediaSwitchingController
                        .getColorScheme().getOnSurface());
                mHeaderTitle.setGravity(Gravity.NO_GRAVITY);
            }
        }

        refreshQuickAccessShelf();

        // Show when remote media session is available or
        //      when the device supports BT LE audio + media is playing
        mStopButton.setVisibility(
                mMediaSwitchingController.hasStopButton() ? View.VISIBLE : View.GONE);
        mStopButton.setEnabled(true);
        mStopButton.setText(mContext.getString(mMediaSwitchingController.getStopButtonStringRes()));
        mStopButton.setOnClickListener(v -> onStopButtonClick());

        if (!mAdapter.isDragging()) {
            int currentActivePosition = mAdapter.getCurrentActivePosition();
            if (!colorSetUpdated && !deviceSetChanged && currentActivePosition >= 0
                    && currentActivePosition < mAdapter.getItemCount()) {
                mAdapter.notifyItemChanged(currentActivePosition);
            } else {
                mAdapter.updateItems();
            }
        } else {
            mMediaSwitchingController.setRefreshing(false);
            mMediaSwitchingController.refreshDataSetIfNeeded();
        }
    }

    private void updateButtonBackgroundColorFilter() {
        mDoneButton.getBackground().setTint(
                mMediaSwitchingController.getColorScheme().getPrimary());
        mDoneButton.setTextColor(mMediaSwitchingController.getColorScheme().getOnPrimary());
        mStopButton.getBackground().setTint(
                mMediaSwitchingController.getColorScheme().getOutlineVariant());
        mStopButton.setTextColor(mMediaSwitchingController.getColorScheme().getPrimary());
        mConnectDeviceButton.setTextColor(
                mMediaSwitchingController.getColorScheme().getOnSurfaceVariant());
        mConnectDeviceButton.setStrokeColor(ColorStateList.valueOf(
                mMediaSwitchingController.getColorScheme().getOutlineVariant()));
        mConnectDeviceButton.setIconTint(ColorStateList.valueOf(
                mMediaSwitchingController.getColorScheme().getPrimary()));

        MediaOutputColorScheme colorScheme = mMediaSwitchingController.getColorScheme();
        mAudioSharingButton.setTextColor(
                getButtonColorStateList(
                        /* defaultColor= */ colorScheme.getOnSurfaceVariant(),
                        /* activatedColor= */ colorScheme.getOnPrimary()));
        mAudioSharingButton.setStrokeColor(
                getButtonColorStateList(
                        /* defaultColor= */ colorScheme.getOutlineVariant(),
                        /* activatedColor= */ colorScheme.getPrimary()));
        mAudioSharingButton.setBackgroundTintList(
                getButtonColorStateList(
                        /* defaultColor= */ colorScheme.getSurfaceContainer(),
                        /* activatedColor= */ colorScheme.getPrimary()));
        mAudioSharingButton.setIconTint(
                getButtonColorStateList(
                        /* defaultColor= */ colorScheme.getPrimary(),
                        /* activatedColor= */ colorScheme.getOnPrimary()));
    }

    private ColorStateList getButtonColorStateList(int defaultColor, int activatedColor) {
        return new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_activated}, new int[] {}},
                new int[] {activatedColor, defaultColor});
    }

    private void updateDialogBackgroundColor() {
        int backgroundColor = mMediaSwitchingController.getColorScheme().getSurfaceContainer();
        getDialogView().getBackground().setTint(backgroundColor);
        mDeviceListLayout.setBackgroundColor(backgroundColor);
    }

    private void changeFooterColorForScroll() {
        int totalItemCount = mLayoutManager.getItemCount();
        int lastVisibleItemPosition =
                mLayoutManager.findLastCompletelyVisibleItemPosition();
        boolean hasBottomScroll =
                totalItemCount > 0 && lastVisibleItemPosition != totalItemCount - 1;
        mDialogFooter.getBackground().setTint(
                hasBottomScroll
                        ? mMediaSwitchingController.getColorScheme().getSurfaceContainerHigh()
                        : mMediaSwitchingController.getColorScheme().getSurfaceContainer());
    }

    private void refreshQuickAccessShelf() {
        boolean showQuickAccessShelf = false;
        AudioSharingButtonState buttonState =
                mMediaSwitchingController.getAudioSharingButtonState();
        if (buttonState == null) {
            mAudioSharingButton.setVisibility(View.GONE);
        } else {
            showQuickAccessShelf = true;
            mAudioSharingButton.setVisibility(View.VISIBLE);
            mAudioSharingButton.setText(buttonState.getResId());
            mAudioSharingButton.setActivated(buttonState.isActive());
            mAudioSharingButton.setOnClickListener(
                    mMediaSwitchingController::launchAudioSharing);
        }

        if (mMediaSwitchingController.hasConnectDeviceButton()) {
            showQuickAccessShelf = true;
            mConnectDeviceButton.setVisibility(View.VISIBLE);
            mConnectDeviceButton.setOnClickListener(
                    mMediaSwitchingController::launchBluetoothPairing);
        } else {
            mConnectDeviceButton.setVisibility(View.GONE);
        }

        mQuickAccessShelf.setVisibility(showQuickAccessShelf ? View.VISIBLE : View.GONE);
    }

    @VisibleForTesting
    void onStopButtonClick() {
        mMediaSwitchingController.releaseSession();
        mDialogTransitionAnimator.disableAllCurrentDialogsExitAnimations();
        dismiss();
    }

    @Override
    public void onMediaChanged() {
        mMainThreadHandler.post(() -> refresh());
    }

    @Override
    public void onMediaStoppedOrPaused() {
        if (isShowing()) {
            dismiss();
        }
    }

    @Override
    public void onRouteChanged() {
        mMainThreadHandler.post(() -> refresh());
    }

    @Override
    public void onDeviceListChanged() {
        mMainThreadHandler.post(() -> refresh(true));
    }

    @Override
    public void dismissDialog() {
        mBroadcastSender.closeSystemDialogs();
    }

    @Override
    public void onQuickAccessButtonsChanged() {
        mMainThreadHandler.post(this::refreshQuickAccessShelf);
    }

    View getDialogView() {
        return mDialogView;
    }

    @VisibleForTesting
    public enum MediaOutputEvent implements UiEventLogger.UiEventEnum {
        @UiEvent(doc = "The MediaOutput dialog became visible on the screen.")
        MEDIA_OUTPUT_DIALOG_SHOW(655);

        private final int mId;

        MediaOutputEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }

}
