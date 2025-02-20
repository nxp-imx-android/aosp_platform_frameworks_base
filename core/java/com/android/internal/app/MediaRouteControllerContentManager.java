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

package com.android.internal.app;

import android.content.Context;
import android.media.MediaRouter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.android.internal.R;

/**
 * This class manages the content display within the media route controller UI.
 */
public class MediaRouteControllerContentManager {
    /**
     * A delegate interface that a MediaRouteController UI should implement. It allows the content
     * manager to inform the UI of any UI changes that need to be made in response to content
     * updates.
     */
    public interface Delegate {
        /**
         * Updates the title of the cast device
         */
        void setCastDeviceTitle(CharSequence title);

        /**
         * Dismiss the UI to transition to a different workflow.
         */
        void dismissView();
    }

    private final Delegate mDelegate;

    // Time to wait before updating the volume when the user lets go of the seek bar
    // to allow the route provider time to propagate the change and publish a new
    // route descriptor.
    private static final int VOLUME_UPDATE_DELAY_MILLIS = 250;

    private final MediaRouter mRouter;
    private final MediaRouter.RouteInfo mRoute;

    private LinearLayout mVolumeLayout;
    private SeekBar mVolumeSlider;
    private boolean mVolumeSliderTouched;

    public MediaRouteControllerContentManager(Context context, Delegate delegate) {
        mDelegate = delegate;
        mRouter = context.getSystemService(MediaRouter.class);
        mRoute = mRouter.getSelectedRoute();
    }

    /**
     * Starts binding all the views (volume layout, slider, etc.) using the
     * given container view.
     */
    public void bindViews(View containerView) {
        mDelegate.setCastDeviceTitle(mRoute.getName());
        mVolumeLayout = containerView.findViewById(R.id.media_route_volume_layout);
        mVolumeSlider = containerView.findViewById(R.id.media_route_volume_slider);
        mVolumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private final Runnable mStopTrackingTouch = new Runnable() {
                @Override
                public void run() {
                    if (mVolumeSliderTouched) {
                        mVolumeSliderTouched = false;
                        updateVolume();
                    }
                }
            };

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (mVolumeSliderTouched) {
                    mVolumeSlider.removeCallbacks(mStopTrackingTouch);
                } else {
                    mVolumeSliderTouched = true;
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Defer resetting mVolumeSliderTouched to allow the media route provider
                // a little time to settle into its new state and publish the final
                // volume update.
                mVolumeSlider.postDelayed(mStopTrackingTouch, VOLUME_UPDATE_DELAY_MILLIS);
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mRoute.requestSetVolume(progress);
                }
            }
        });
    }

    /**
     * Updates all the views to reflect new states.
     */
    public void update() {
        mDelegate.setCastDeviceTitle(mRoute.getName());
        updateVolume();
    }

    /**
     * Updates the volume layout and slider.
     */
    public void updateVolume() {
        if (!mVolumeSliderTouched) {
            if (isVolumeControlAvailable()) {
                mVolumeLayout.setVisibility(View.VISIBLE);
                mVolumeSlider.setMax(mRoute.getVolumeMax());
                mVolumeSlider.setProgress(mRoute.getVolume());
            } else {
                mVolumeLayout.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Callback function to triggered after the disconnect button is clicked.
     */
    public void onDisconnectButtonClick() {
        if (mRoute.isSelected()) {
            if (mRoute.isBluetooth()) {
                mRouter.getDefaultRoute().select();
            } else {
                mRouter.getFallbackRoute().select();
            }
        }
        mDelegate.dismissView();
    }

    private boolean isVolumeControlAvailable() {
        return mRoute.getVolumeHandling() == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE;
    }
}
