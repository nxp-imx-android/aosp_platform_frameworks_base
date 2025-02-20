/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.AlertDialog;
import android.app.MediaRouteActionProvider;
import android.app.MediaRouteButton;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteGroup;
import android.media.MediaRouter.RouteInfo;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;

import com.android.internal.R;

/**
 * This class implements the route controller dialog for {@link MediaRouter}.
 * <p>
 * This dialog allows the user to control or disconnect from the currently selected route.
 * </p>
 *
 * @see MediaRouteButton
 * @see MediaRouteActionProvider
 *
 * TODO: Move this back into the API, as in the support library media router.
 */
public class MediaRouteControllerDialog extends AlertDialog implements
        MediaRouteControllerContentManager.Delegate {
    // TODO(b/360050020): Eventually these 3 variables should be in the content manager instead of
    //  here. So these should be removed when the migration is completed.
    private final MediaRouter mRouter;
    private final MediaRouterCallback mCallback;
    private final MediaRouter.RouteInfo mRoute;

    private Drawable mMediaRouteButtonDrawable;
    private int[] mMediaRouteConnectingState = { R.attr.state_checked, R.attr.state_enabled };
    private int[] mMediaRouteOnState = { R.attr.state_activated, R.attr.state_enabled };
    private Drawable mCurrentIconDrawable;

    private boolean mAttachedToWindow;

    private final MediaRouteControllerContentManager mContentManager;

    public MediaRouteControllerDialog(Context context, int theme) {
        super(context, theme);

        mContentManager = new MediaRouteControllerContentManager(context, this);
        mRouter = (MediaRouter) context.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mCallback = new MediaRouterCallback();
        mRoute = mRouter.getSelectedRoute();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Resources res = getContext().getResources();
        setButton(BUTTON_NEGATIVE, res.getString(R.string.media_route_controller_disconnect),
                (dialogInterface, id) -> mContentManager.onDisconnectButtonClick());
        View customView = getLayoutInflater().inflate(R.layout.media_route_controller_dialog, null);
        setView(customView, 0, 0, 0, 0);
        mContentManager.bindViews(customView);
        super.onCreate(savedInstanceState);

        View customPanelView = getWindow().findViewById(R.id.customPanel);
        if (customPanelView != null) {
            customPanelView.setMinimumHeight(0);
        }

        mMediaRouteButtonDrawable = obtainMediaRouteButtonDrawable();
        update();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;

        mRouter.addCallback(0, mCallback, MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS);
        update();
    }

    @Override
    public void onDetachedFromWindow() {
        mRouter.removeCallback(mCallback);
        mAttachedToWindow = false;

        super.onDetachedFromWindow();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mRoute.requestUpdateVolume(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ? -1 : 1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void setCastDeviceTitle(CharSequence title) {
        setTitle(title);
    }

    @Override
    public void dismissView() {
        dismiss();
    }

    private void update() {
        if (!mRoute.isSelected() || mRoute.isDefault()) {
            dismissView();
        }

        mContentManager.update();

        Drawable icon = getIconDrawable();
        if (icon != mCurrentIconDrawable) {
            mCurrentIconDrawable = icon;
            if (icon instanceof AnimationDrawable animDrawable) {
                if (!mAttachedToWindow && !mRoute.isConnecting()) {
                    // When the route is already connected before the view is attached, show the
                    // last frame of the connected animation immediately.
                    if (animDrawable.isRunning()) {
                        animDrawable.stop();
                    }
                    icon = animDrawable.getFrame(animDrawable.getNumberOfFrames() - 1);
                } else if (!animDrawable.isRunning()) {
                    animDrawable.start();
                }
            }
            setIcon(icon);
        }
    }

    private Drawable obtainMediaRouteButtonDrawable() {
        Context context = getContext();
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(R.attr.mediaRouteButtonStyle, value, true)) {
            return null;
        }
        int[] drawableAttrs = new int[] { R.attr.externalRouteEnabledDrawable };
        TypedArray a = context.obtainStyledAttributes(value.data, drawableAttrs);
        Drawable drawable = a.getDrawable(0);
        a.recycle();
        return drawable;
    }

    private Drawable getIconDrawable() {
        if (!(mMediaRouteButtonDrawable instanceof StateListDrawable)) {
            return mMediaRouteButtonDrawable;
        } else if (mRoute.isConnecting()) {
            StateListDrawable stateListDrawable = (StateListDrawable) mMediaRouteButtonDrawable;
            stateListDrawable.setState(mMediaRouteConnectingState);
            return stateListDrawable.getCurrent();
        } else {
            StateListDrawable stateListDrawable = (StateListDrawable) mMediaRouteButtonDrawable;
            stateListDrawable.setState(mMediaRouteOnState);
            return stateListDrawable.getCurrent();
        }
    }

    private final class MediaRouterCallback extends MediaRouter.SimpleCallback {
        @Override
        public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) {
            update();
        }

        @Override
        public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {
            update();
        }

        @Override
        public void onRouteVolumeChanged(MediaRouter router, MediaRouter.RouteInfo route) {
            if (route == mRoute) {
                mContentManager.updateVolume();
            }
        }

        @Override
        public void onRouteGrouped(MediaRouter router, RouteInfo info, RouteGroup group,
                int index) {
            update();
        }

        @Override
        public void onRouteUngrouped(MediaRouter router, RouteInfo info, RouteGroup group) {
            update();
        }
    }
}
