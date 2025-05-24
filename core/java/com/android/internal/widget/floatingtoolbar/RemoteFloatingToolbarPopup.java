/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.widget.floatingtoolbar;

import static android.view.selectiontoolbar.ISelectionToolbarCallback.ERROR_DO_NOT_ALLOW_MULTIPLE_TOOL_BAR;
import static android.view.selectiontoolbar.ISelectionToolbarCallback.ERROR_UNKNOWN_WIDGET_TOKEN;
import static android.view.selectiontoolbar.SelectionToolbarManager.NO_TOOLBAR_ID;
import static android.view.selectiontoolbar.ToolbarMenuItem.PRIORITY_OVERFLOW;
import static android.view.selectiontoolbar.ToolbarMenuItem.PRIORITY_PRIMARY;
import static android.view.selectiontoolbar.ToolbarMenuItem.PRIORITY_UNKNOWN;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UiThread;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import android.view.selectiontoolbar.ISelectionToolbarCallback;
import android.view.selectiontoolbar.SelectionToolbarManager;
import android.view.selectiontoolbar.ShowInfo;
import android.view.selectiontoolbar.ToolbarMenuItem;
import android.view.selectiontoolbar.WidgetInfo;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.android.internal.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A popup window used by the floating toolbar to render menu items in the remote system process.
 *
 * It holds 2 panels (i.e. main panel and overflow panel) and an overflow button
 * to transition between panels.
 */
public final class RemoteFloatingToolbarPopup implements FloatingToolbarPopup {

    private static final boolean DEBUG =
            Log.isLoggable(FloatingToolbar.FLOATING_TOOLBAR_TAG, Log.VERBOSE);

    private static final int TOOLBAR_STATE_SHOWN = 1;
    private static final int TOOLBAR_STATE_HIDDEN = 2;
    private static final int TOOLBAR_STATE_DISMISSED = 3;

    @IntDef(prefix = {"TOOLBAR_STATE_"}, value = {
            TOOLBAR_STATE_SHOWN,
            TOOLBAR_STATE_HIDDEN,
            TOOLBAR_STATE_DISMISSED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ToolbarState {
    }

    @NonNull
    private final Context mContext;

    @NonNull
    private final SelectionToolbarManager mSelectionToolbarManager;
    // Parent for the popup window.
    @NonNull
    private final View mParent;
    // A popup window used for showing menu items rendered by the remote system process
    @NonNull
    private final PopupWindow mPopupWindow;
    // The callback to handle remote rendered selection toolbar.
    @NonNull
    private final SelectionToolbarCallbackImpl mSelectionToolbarCallback;

    // tracks this popup state.
    private @ToolbarState int mState;

    private int mNextSequenceNumber;
    private final SparseArray<MenuItem.OnMenuItemClickListener> mPendingMenuItemClickListeners =
            new SparseArray<>();
    private final SparseArray<List<MenuItem>> mPendingMenuItems = new SparseArray<>();
    private final SparseArray<ShowInfo> mPendingShowInfos = new SparseArray<>();

    // To be updated in onShow
    private final Rect mContentRect = new Rect();
    private List<MenuItem> mMenuItems;
    private MenuItem.OnMenuItemClickListener mMenuItemClickListener;
    // The widget token of the current showing floating toolbar provided by the render service.
    private long mFloatingToolbarToken;

    private int mSuggestedWidth;
    private final Rect mScreenViewPort = new Rect();
    private boolean mWidthChanged = true;
    private final boolean mIsLightTheme;

    private final int[] mCoordsOnScreen = new int[2];
    private final int[] mCoordsOnWindow = new int[2];

    public RemoteFloatingToolbarPopup(Context context, View parent) {
        mContext = context;
        mParent = Objects.requireNonNull(parent);
        mPopupWindow = createPopupWindow(context);
        mSelectionToolbarManager = context.getSystemService(SelectionToolbarManager.class);
        mSelectionToolbarCallback = new SelectionToolbarCallbackImpl(this);
        mIsLightTheme = isLightTheme(context);
        mFloatingToolbarToken = NO_TOOLBAR_ID;
    }

    private boolean isLightTheme(Context context) {
        TypedArray a = context.obtainStyledAttributes(new int[]{R.attr.isLightTheme});
        boolean isLightTheme = a.getBoolean(0, true);
        a.recycle();
        return isLightTheme;
    }

    @UiThread
    @Override
    public void show(List<MenuItem> menuItems,
            MenuItem.OnMenuItemClickListener menuItemClickListener, Rect contentRect) {
        Objects.requireNonNull(menuItems);
        Objects.requireNonNull(menuItemClickListener);

        ViewRootImpl viewRootImpl = mParent.getViewRootImpl();
        if (viewRootImpl == null) {
            Log.w(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                    "RemoteFloatingToolbarPopup.show(): viewRootImpl is null.");
            return;
        }

        if (isShowing() && Objects.equals(menuItems, mMenuItems)
                && Objects.equals(contentRect, mContentRect)) {
            if (DEBUG) {
                Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "Ignore duplicate show() for the same content.");
            }
            return;
        }

        if (isLatestPendingOrCurrent(menuItems, menuItemClickListener, contentRect)) {
            return;
        }

        boolean isLayoutRequired = mMenuItems == null
                || !MenuItemRepr.reprEquals(menuItems, mMenuItems)
                || mWidthChanged;
        if (isLayoutRequired) {
            mSelectionToolbarManager.dismissToolbar(mFloatingToolbarToken);
            doDismissPopupWindow();
        }

        mParent.getWindowVisibleDisplayFrame(mScreenViewPort);
        final int suggestWidth = mSuggestedWidth > 0
                ? mSuggestedWidth
                : mParent.getResources().getDimensionPixelSize(
                        R.dimen.floating_toolbar_preferred_width);
        int sequenceNumber = mNextSequenceNumber++;
        final ShowInfo showInfo = new ShowInfo();
        showInfo.sequenceNumber = sequenceNumber;
        showInfo.widgetToken = mFloatingToolbarToken;
        showInfo.layoutRequired = isLayoutRequired;
        showInfo.menuItems = getToolbarMenuItems(menuItems);
        showInfo.contentRect = contentRect;
        showInfo.suggestedWidth = suggestWidth;
        showInfo.viewPortOnScreen = mScreenViewPort;
        showInfo.hostInputToken = viewRootImpl.getInputToken();
        showInfo.isLightTheme = mIsLightTheme;
        showInfo.configuration = mContext.getResources().getConfiguration();
        if (DEBUG) {
            Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                    "RemoteFloatingToolbarPopup.show() for " + showInfo);
        }
        mPendingMenuItemClickListeners.put(sequenceNumber, menuItemClickListener);
        mPendingMenuItems.put(sequenceNumber, menuItems);
        mPendingShowInfos.put(sequenceNumber, showInfo);
        mSelectionToolbarManager.showToolbar(showInfo, mSelectionToolbarCallback);
    }

    private boolean isLatestPendingOrCurrent(List<MenuItem> menuItems,
            MenuItem.OnMenuItemClickListener menuItemClickListener, Rect contentRect) {
        if (mPendingMenuItems.size() == 0) {
            return Objects.equals(menuItems, mMenuItems)
                    && menuItemClickListener == mMenuItemClickListener
                    && Objects.equals(contentRect, mContentRect);
        }

        int lastPendingIndex = mPendingMenuItems.size() - 1;
        return menuItemClickListener == mPendingMenuItemClickListeners.valueAt(lastPendingIndex)
                && Objects.equals(menuItems, mPendingMenuItems.valueAt(lastPendingIndex))
                && Objects.equals(
                        contentRect, mPendingShowInfos.valueAt(lastPendingIndex).contentRect);
    }

    @UiThread
    @Override
    public void dismiss() {
        if (mState == TOOLBAR_STATE_DISMISSED) {
            Log.w(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                    "The floating toolbar already dismissed.");
            return;
        }
        if (DEBUG) {
            Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                    "RemoteFloatingToolbarPopup.dismiss().");
        }
        mSelectionToolbarManager.dismissToolbar(mFloatingToolbarToken);
        doDismissPopupWindow();
    }

    @UiThread
    @Override
    public void hide() {
        if (mState == TOOLBAR_STATE_DISMISSED || mState == TOOLBAR_STATE_HIDDEN) {
            if (DEBUG) {
                Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "The floating toolbar already dismissed or hidden.");
            }
            return;
        }
        if (DEBUG) {
            Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                    "RemoteFloatingToolbarPopup.hide().");
        }
        mSelectionToolbarManager.hideToolbar(mFloatingToolbarToken);
        mState = TOOLBAR_STATE_HIDDEN;
        mPopupWindow.dismiss();
    }

    @UiThread
    @Override
    public void setSuggestedWidth(int suggestedWidth) {
        int difference = Math.abs(suggestedWidth - mSuggestedWidth);
        mWidthChanged = difference > (mSuggestedWidth * 0.2);
        mSuggestedWidth = suggestedWidth;
    }

    @Override
    public void setWidthChanged(boolean widthChanged) {
        mWidthChanged = widthChanged;
    }

    @UiThread
    @Override
    public boolean isHidden() {
        return mState == TOOLBAR_STATE_HIDDEN;
    }

    @UiThread
    @Override
    public boolean isShowing() {
        return mState == TOOLBAR_STATE_SHOWN;
    }

    @UiThread
    @Override
    public boolean setOutsideTouchable(boolean outsideTouchable,
            @Nullable PopupWindow.OnDismissListener onDismiss) {
        if (mState == TOOLBAR_STATE_DISMISSED) {
            return false;
        }
        boolean ret = false;
        if (mPopupWindow.isOutsideTouchable() ^ outsideTouchable) {
            mPopupWindow.setOutsideTouchable(outsideTouchable);
            mPopupWindow.setFocusable(!outsideTouchable);
            mPopupWindow.update();
            ret = true;
        }
        mPopupWindow.setOnDismissListener(onDismiss);
        return ret;
    }

    private void updatePopupWindowContent(WidgetInfo widgetInfo) {
        if (DEBUG) {
            Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG, "updatePopupWindowContent.");
        }
        ViewGroup contentContainer = (ViewGroup) mPopupWindow.getContentView();
        contentContainer.removeAllViews();
        SurfaceView surfaceView = new SurfaceView(mParent.getContext());
        surfaceView.setZOrderOnTop(true);
        surfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
        surfaceView.setChildSurfacePackage(widgetInfo.surfacePackage);
        contentContainer.addView(surfaceView);
    }

    private Point getCoordinatesInWindow(int x, int y) {
        // We later specify the location of PopupWindow relative to the attached window.
        // The idea here is that 1) we can get the location of a View in both window coordinates
        // and screen coordinates, where the offset between them should be equal to the window
        // origin, and 2) we can use an arbitrary for this calculation while calculating the
        // location of the rootview is supposed to be least expensive.
        // TODO: Consider to use PopupWindow.setIsLaidOutInScreen(true) so that we can avoid
        // the following calculation.
        mParent.getRootView().getLocationOnScreen(mCoordsOnScreen);
        mParent.getRootView().getLocationInWindow(mCoordsOnWindow);
        int windowLeftOnScreen = mCoordsOnScreen[0] - mCoordsOnWindow[0];
        int windowTopOnScreen = mCoordsOnScreen[1] - mCoordsOnWindow[1];
        return new Point(Math.max(0, x - windowLeftOnScreen), Math.max(0, y - windowTopOnScreen));
    }

    private static List<ToolbarMenuItem> getToolbarMenuItems(List<MenuItem> menuItems) {
        final List<ToolbarMenuItem> list = new ArrayList<>(menuItems.size());
        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem menuItem = menuItems.get(i);
            ToolbarMenuItem toolbarMenuItem = new ToolbarMenuItem();
            toolbarMenuItem.itemId = menuItem.getItemId();
            toolbarMenuItem.itemIndex = i;
            toolbarMenuItem.title = menuItem.getTitle();
            toolbarMenuItem.contentDescription = menuItem.getContentDescription();
            toolbarMenuItem.groupId = menuItem.getGroupId();
            toolbarMenuItem.icon = convertDrawableToIcon(menuItem.getIcon());
            toolbarMenuItem.tooltipText = menuItem.getTooltipText();
            toolbarMenuItem.priority = getPriorityFromMenuItem(menuItem);

            list.add(toolbarMenuItem);
        }
        return list;
    }

    private static Icon convertDrawableToIcon(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        if (drawable instanceof BitmapDrawable) {
            final BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return Icon.createWithBitmap(bitmapDrawable.getBitmap());
            }
        }
        final Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(),  canvas.getHeight());
        drawable.draw(canvas);
        return Icon.createWithBitmap(bitmap);
    }

    /**
     * Returns the priority from a given {@link MenuItem}.
     */
    private static int getPriorityFromMenuItem(MenuItem menuItem) {
        if (menuItem.requiresActionButton()) {
            return PRIORITY_PRIMARY;
        } else if (menuItem.requiresOverflow()) {
            return PRIORITY_OVERFLOW;
        }
        return PRIORITY_UNKNOWN;
    }

    private static PopupWindow createPopupWindow(Context content) {
        ViewGroup popupContentHolder = new LinearLayout(content);
        PopupWindow popupWindow = new PopupWindow(popupContentHolder);
        popupWindow.setClippingEnabled(false);
        popupWindow.setWindowLayoutType(
                WindowManager.LayoutParams.TYPE_APPLICATION_ABOVE_SUB_PANEL);
        popupWindow.setAnimationStyle(0);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return popupWindow;
    }

    private void doDismissPopupWindow() {
        if (DEBUG) {
            Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG, "RemoteFloatingToolbarPopup.doDismiss().");
        }
        mState = TOOLBAR_STATE_DISMISSED;
        mMenuItems = null;
        mMenuItemClickListener = null;
        mFloatingToolbarToken = 0;
        mSuggestedWidth = 0;
        mWidthChanged = true;
        resetCoords();
        mContentRect.setEmpty();
        mScreenViewPort.setEmpty();
        mPopupWindow.dismiss();
    }

    private void resetCoords() {
        mCoordsOnScreen[0] = 0;
        mCoordsOnScreen[1] = 0;
        mCoordsOnWindow[0] = 0;
        mCoordsOnWindow[1] = 0;
    }

    private void runOnUiThread(Runnable runnable) {
        mParent.post(runnable);
    }

    private void onShow(WidgetInfo info) {
        runOnUiThread(() -> {
            int sequenceNumber = info.sequenceNumber;
            mMenuItemClickListener = Objects.requireNonNull(
                    mPendingMenuItemClickListeners.removeReturnOld(sequenceNumber));
            mMenuItems = Objects.requireNonNull(
                    mPendingMenuItems.removeReturnOld(sequenceNumber));
            ShowInfo showInfo = Objects.requireNonNull(
                    mPendingShowInfos.removeReturnOld(sequenceNumber));
            mContentRect.set(showInfo.contentRect);

            mFloatingToolbarToken = info.widgetToken;
            mState = TOOLBAR_STATE_SHOWN;
            updatePopupWindowContent(info);
            Rect contentRect = info.contentRect;
            mPopupWindow.setWidth(contentRect.width());
            mPopupWindow.setHeight(contentRect.height());
            final Point coords = getCoordinatesInWindow(contentRect.left, contentRect.top);
            mPopupWindow.showAtLocation(mParent, Gravity.NO_GRAVITY, coords.x, coords.y);
        });
    }

    private void onWidgetUpdated(WidgetInfo info) {
        runOnUiThread(() -> {
            if (!isShowing()) {
                Log.w(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "onWidgetUpdated(): The widget isn't showing.");
                return;
            }
            updatePopupWindowContent(info);
            Rect contentRect = info.contentRect;
            Point coords = getCoordinatesInWindow(contentRect.left, contentRect.top);
            if (DEBUG) {
                Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "PopupWindow x= " + coords.x + " y= " + coords.y + " w="
                                + contentRect.width() + " h=" + contentRect.height());
            }
            mPopupWindow.update(coords.x, coords.y, contentRect.width(), contentRect.height());
        });
    }

    private void onToolbarShowTimeout() {
        runOnUiThread(() -> {
            if (mState == TOOLBAR_STATE_DISMISSED) {
                return;
            }
            doDismissPopupWindow();
        });
    }

    private void onMenuItemClicked(int itemIndex) {
        runOnUiThread(() -> {
            if (mMenuItems == null || mMenuItemClickListener == null) {
                return;
            }
            MenuItem item = mMenuItems.get(itemIndex);
            if (DEBUG) {
                Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "SelectionToolbarCallbackImpl onMenuItemClicked. itemIndex="
                                + itemIndex + " item=" + item);
            }
            // TODO: handle the menu item like clipboard
            if (item != null) {
                mMenuItemClickListener.onMenuItemClick(item);
            } else {
                Log.e(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "onMenuItemClicked: cannot find menu item.");
            }
        });
    }

    private void onError(int errorCode, int sequenceNumber) {
        runOnUiThread(() -> {
            switch (errorCode) {
                case ERROR_DO_NOT_ALLOW_MULTIPLE_TOOL_BAR:
                    Log.e(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                            "onError: attempt to show multiple toolbars");
                    break;
                case ERROR_UNKNOWN_WIDGET_TOKEN:
                    Log.e(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                            "onError: unknown widget token");
                    break;
            }

            mPendingMenuItemClickListeners.remove(sequenceNumber);
            mPendingMenuItems.remove(sequenceNumber);
            mPendingShowInfos.remove(sequenceNumber);
        });
    }

    private static class SelectionToolbarCallbackImpl extends ISelectionToolbarCallback.Stub {

        private final WeakReference<RemoteFloatingToolbarPopup> mRemotePopup;

        SelectionToolbarCallbackImpl(RemoteFloatingToolbarPopup popup) {
            mRemotePopup = new WeakReference<>(popup);
        }

        @Override
        public void onShown(WidgetInfo info) {
            if (DEBUG) {
                Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "SelectionToolbarCallbackImpl onShown: " + info);
            }
            final RemoteFloatingToolbarPopup remoteFloatingToolbarPopup = mRemotePopup.get();
            if (remoteFloatingToolbarPopup != null) {
                remoteFloatingToolbarPopup.onShow(info);
            } else {
                Log.w(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "Lost remoteFloatingToolbarPopup reference for onShown.");
            }
        }

        @Override
        public void onWidgetUpdated(WidgetInfo info) {
            if (DEBUG) {
                Log.v(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "SelectionToolbarCallbackImpl onWidgetUpdated: info = " + info);
            }
            final RemoteFloatingToolbarPopup remoteFloatingToolbarPopup = mRemotePopup.get();
            if (remoteFloatingToolbarPopup != null) {
                remoteFloatingToolbarPopup.onWidgetUpdated(info);
            } else {
                Log.w(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "Lost remoteFloatingToolbarPopup reference for onWidgetUpdated.");
            }
        }

        @Override
        public void onToolbarShowTimeout() {
            final RemoteFloatingToolbarPopup remoteFloatingToolbarPopup = mRemotePopup.get();
            if (remoteFloatingToolbarPopup != null) {
                remoteFloatingToolbarPopup.onToolbarShowTimeout();
            } else {
                Log.w(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "Lost remoteFloatingToolbarPopup reference for onToolbarShowTimeout.");
            }
        }

        @Override
        public void onMenuItemClicked(int itemIndex) {
            final RemoteFloatingToolbarPopup remoteFloatingToolbarPopup = mRemotePopup.get();
            if (remoteFloatingToolbarPopup != null) {
                remoteFloatingToolbarPopup.onMenuItemClicked(itemIndex);
            } else {
                Log.w(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "Lost remoteFloatingToolbarPopup reference for onMenuItemClicked.");
            }
        }

        @Override
        public void onError(int errorCode, int sequenceNumber) {
            final RemoteFloatingToolbarPopup remoteFloatingToolbarPopup = mRemotePopup.get();
            if (remoteFloatingToolbarPopup != null) {
                remoteFloatingToolbarPopup.onError(errorCode, sequenceNumber);
            } else {
                Log.w(FloatingToolbar.FLOATING_TOOLBAR_TAG,
                        "Lost remoteFloatingToolbarPopup reference for onError.");
            }
        }
    }

    /**
     * Represents the identity of a MenuItem that is rendered in a FloatingToolbarPopup.
     */
    static final class MenuItemRepr {

        public final int mItemId;
        public final int mGroupId;
        @Nullable
        public final String mTitle;
        @Nullable private final Drawable mIcon;

        private MenuItemRepr(
                int itemId, int groupId, @Nullable CharSequence title,
                @Nullable Drawable icon) {
            mItemId = itemId;
            mGroupId = groupId;
            mTitle = (title == null) ? null : title.toString();
            mIcon = icon;
        }

        /**
         * Creates an instance of MenuItemRepr for the specified menu item.
         */
        public static MenuItemRepr of(MenuItem menuItem) {
            return new MenuItemRepr(
                    menuItem.getItemId(),
                    menuItem.getGroupId(),
                    menuItem.getTitle(),
                    menuItem.getIcon());
        }

        /**
         * Returns this object's hashcode.
         */
        @Override
        public int hashCode() {
            return Objects.hash(mItemId, mGroupId, mTitle, mIcon);
        }

        /**
         * Returns true if this object is the same as the specified object.
         */
        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof LocalFloatingToolbarPopup.MenuItemRepr)) {
                return false;
            }
            final MenuItemRepr other = (MenuItemRepr) o;
            return mItemId == other.mItemId
                    && mGroupId == other.mGroupId
                    && TextUtils.equals(mTitle, other.mTitle)
                    // Many Drawables (icons) do not implement equals(). Using equals() here instead
                    // of reference comparisons in case a Drawable subclass implements equals().
                    && Objects.equals(mIcon, other.mIcon);
        }

        /**
         * Returns true if the two menu item collections are the same based on MenuItemRepr.
         */
        public static boolean reprEquals(
                Collection<MenuItem> menuItems1, Collection<MenuItem> menuItems2) {
            if (menuItems1.size() != menuItems2.size()) {
                return false;
            }

            final Iterator<MenuItem> menuItems2Iter = menuItems2.iterator();
            for (MenuItem menuItem1 : menuItems1) {
                final MenuItem menuItem2 = menuItems2Iter.next();
                if (!MenuItemRepr.of(menuItem1).equals(
                        MenuItemRepr.of(menuItem2))) {
                    return false;
                }
            }
            return true;
        }
    }
}
