/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.selectiontoolbar.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.selectiontoolbar.SelectionToolbarRenderService.OnPasteActionCallback
import android.service.selectiontoolbar.SelectionToolbarRenderService.RemoteCallbackWrapper
import android.service.selectiontoolbar.SelectionToolbarRenderService.TransferTouchListener
import android.text.TextUtils
import android.util.Log
import android.util.Size
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceControlViewHost
import android.view.SurfaceControlViewHost.SurfacePackage
import android.view.View
import android.view.View.MeasureSpec
import android.view.View.OnLayoutChangeListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.view.animation.Transformation
import android.view.selectiontoolbar.ShowInfo
import android.view.selectiontoolbar.ToolbarMenuItem
import android.view.selectiontoolbar.WidgetInfo
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.window.InputTransferToken
import com.android.internal.R
import com.android.internal.util.Preconditions
import com.android.internal.widget.floatingtoolbar.FloatingToolbar
import java.io.PrintWriter
import java.util.Objects
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * This class is responsible for rendering/animation of the selection toolbar in the remote system
 * process. It holds 2 panels (i.e. main panel and overflow panel) and an overflow button to
 * transition between panels.
 *
 * @hide
 */
// TODO(b/215497659): share code with LocalFloatingToolbarPopup
class RemoteSelectionToolbar(
    uid: Int,
    context: Context?,
    showInfo: ShowInfo,
    callbackWrapper: RemoteCallbackWrapper,
    transferTouchListener: TransferTouchListener,
    onPasteActionCallback: OnPasteActionCallback,
) {
    private val mUid: Int

    private val mContext: Context

    /* Margins between the popup window and its content. */
    private val mMarginHorizontal: Int
    private val mMarginVertical: Int

    /* View components */
    private val mContentHolder: FloatingToolbarRoot
    private val mContentContainer: ViewGroup // holds all contents.
    private val mMainPanel: ViewGroup // holds menu items that are initially displayed.

    // holds menu items hidden in the overflow.
    private val mOverflowPanel: OverflowPanel
    private val mOverflowButton: ImageButton // opens/closes the overflow.

    /* overflow button drawables. */
    private val mArrow: Drawable
    private val mOverflow: Drawable
    private val mToArrow: AnimatedVectorDrawable
    private val mToOverflow: AnimatedVectorDrawable

    private val mOverflowPanelViewHelper: OverflowPanelViewHelper

    /* Animation interpolators. */
    private val mLogAccelerateInterpolator: Interpolator
    private val mFastOutSlowInInterpolator: Interpolator?
    private val mLinearOutSlowInInterpolator: Interpolator?
    private val mFastOutLinearInInterpolator: Interpolator?

    /* Animations. */
    private val mShowAnimation: AnimatorSet
    private val mDelayedHideAnimation: AnimatorSet
    private val mImmediateHideAnimation: AnimatorSet
    private val mOpenOverflowAnimation: AnimationSet
    private val mCloseOverflowAnimation: AnimationSet
    private val mOverflowAnimationListener: Animation.AnimationListener

    private val mViewPortOnScreen = Rect() // portion of screen we can draw in.

    private val mLineHeight: Int
    private val mIconTextSpacing: Int

    private val mHostInputToken: IBinder
    private val mCallbackWrapper: RemoteCallbackWrapper
    private val mTransferTouchListener: TransferTouchListener
    private val mOnPasteActionCallback: OnPasteActionCallback

    private var mPopupWidth = 0
    private var mPopupHeight = 0

    // Coordinates to show the toolbar relative to the specified view port
    private val mRelativeCoordsForToolbar = Point()
    private var mSequenceNumber = 0
    private var mMenuItems: MutableList<ToolbarMenuItem>? = null
    private var mSurfaceControlViewHost: SurfaceControlViewHost? = null
    private var mSurfacePackage: SurfacePackage? = null
    private val mHandler: Handler

    /** @see OverflowPanelViewHelper.preparePopupContent */
    private val mPreparePopupContentRTLHelper: Runnable =
        object : Runnable {
            override fun run() {
                setPanelsStatesAtRestingPosition()
                mContentContainer.setAlpha(1f)
            }
        }

    // Tracks this selection toolbar state.
    private var mState: Int = TOOLBAR_STATE_DISMISSED

    /* Calculated sizes for panels and overflow button. */
    private val mOverflowButtonSize: Size
    private var mOverflowPanelSize: Size? = null // Should be null when there is no overflow.
    private var mMainPanelSize: Size? = null

    /* Menu items and click listeners */
    private val mMenuItemButtonOnClickListener: View.OnClickListener

    private var mOpenOverflowUpwards = false // Whether the overflow opens upwards or downwards.
    private var mIsOverflowOpen = false

    private var mTransitionDurationScale = 0 // Used to scale the toolbar transition duration.

    private val mPreviousContentRect = Rect()

    private val mTempContentRect = Rect()
    private val mTempContentRectForRoot = Rect()
    private val mTempCoords = IntArray(2)

    init {
        val looper = Looper.myLooper()
        checkNotNull(looper) { "RemoteSelectionToolbar must be created on a looper thread" }
        mHandler = Handler(looper)
        mUid = uid
        mContext = wrapContext(context, showInfo)
        mCallbackWrapper = callbackWrapper
        mTransferTouchListener = transferTouchListener
        mOnPasteActionCallback = onPasteActionCallback
        mHostInputToken = showInfo.hostInputToken
        mContentHolder = FloatingToolbarRoot(mContext, mHostInputToken, mTransferTouchListener)
        mContentContainer = createContentContainer(mContext)
        mContentHolder.addView(mContentContainer)
        mMarginHorizontal =
            mContext
                .getResources()
                .getDimensionPixelSize(R.dimen.floating_toolbar_horizontal_margin)
        mMarginVertical =
            mContext.getResources().getDimensionPixelSize(R.dimen.floating_toolbar_vertical_margin)
        mLineHeight = mContext.getResources().getDimensionPixelSize(R.dimen.floating_toolbar_height)
        mIconTextSpacing =
            mContext
                .getResources()
                .getDimensionPixelSize(R.dimen.floating_toolbar_icon_text_spacing)

        // Interpolators
        mLogAccelerateInterpolator = LogAccelerateInterpolator()
        mFastOutSlowInInterpolator =
            AnimationUtils.loadInterpolator(mContext, android.R.interpolator.fast_out_slow_in)
        mLinearOutSlowInInterpolator =
            AnimationUtils.loadInterpolator(mContext, android.R.interpolator.linear_out_slow_in)
        mFastOutLinearInInterpolator =
            AnimationUtils.loadInterpolator(mContext, android.R.interpolator.fast_out_linear_in)

        // Drawables. Needed for views.
        mArrow =
            mContext.getResources().getDrawable(R.drawable.ft_avd_tooverflow, mContext.getTheme())
        mArrow.setAutoMirrored(true)
        mOverflow =
            mContext.getResources().getDrawable(R.drawable.ft_avd_toarrow, mContext.getTheme())
        mOverflow.setAutoMirrored(true)
        mToArrow =
            mContext
                .getResources()
                .getDrawable(R.drawable.ft_avd_toarrow_animation, mContext.getTheme())
                as AnimatedVectorDrawable
        mToArrow.setAutoMirrored(true)
        mToOverflow =
            mContext
                .getResources()
                .getDrawable(R.drawable.ft_avd_tooverflow_animation, mContext.getTheme())
                as AnimatedVectorDrawable
        mToOverflow.setAutoMirrored(true)

        // Views
        mOverflowButton = createOverflowButton()
        mOverflowButtonSize = measure(mOverflowButton)
        mMainPanel = createMainPanel()
        mOverflowPanelViewHelper = OverflowPanelViewHelper(mContext, mIconTextSpacing)
        mOverflowPanel = createOverflowPanel()

        // Animation. Need views.
        mOverflowAnimationListener = createOverflowAnimationListener()
        mOpenOverflowAnimation = AnimationSet(true)
        mOpenOverflowAnimation.setAnimationListener(mOverflowAnimationListener)
        mCloseOverflowAnimation = AnimationSet(true)
        mCloseOverflowAnimation.setAnimationListener(mOverflowAnimationListener)
        mContentContainer.addOnLayoutChangeListener(
            object : OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View?,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int,
                ) {
                    updateFloatingToolbarRootContentRect()
                }
            }
        )
        mShowAnimation = createEnterAnimation(mContentContainer)
        mDelayedHideAnimation =
            createExitAnimation(
                mContentContainer,
                150, // startDelay
                object : AnimatorListenerAdapter() {
                    private var mCanceled = false

                    override fun onAnimationCancel(animation: Animator) {
                        mCanceled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (mCanceled) {
                            mCanceled = false
                            return
                        }
                        releaseSurfaceControlViewHost()
                        mCallbackWrapper.onInvisible()
                    }
                },
            )
        mImmediateHideAnimation =
            createExitAnimation(
                mContentContainer,
                0, // startDelay
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        releaseSurfaceControlViewHost()
                        mCallbackWrapper.onInvisible()
                    }
                },
            )
        mMenuItemButtonOnClickListener =
            View.OnClickListener { v: View? ->
                // Post the callback to fg thread because the onPasteAction() callback
                // needs to be synchronous but it shouldn't block the main thread.
                mHandler.post(
                    Runnable {
                        val tag = v!!.getTag()
                        if (tag is ToolbarMenuItem) {
                            if (tag.itemId == R.id.paste || tag.itemId == R.id.pasteAsPlainText) {
                                mOnPasteActionCallback.onPasteAction(mUid)
                            }
                            mCallbackWrapper.onMenuItemClicked(tag.itemIndex)
                        }
                    }
                )
            }
    }

    private fun updateFloatingToolbarRootContentRect() {
        if (mSurfaceControlViewHost == null) {
            return
        }
        val root = mSurfaceControlViewHost!!.getView() as FloatingToolbarRoot
        mContentContainer.getLocationOnScreen(mTempCoords)
        val contentLeft = mTempCoords[0]
        val contentTop = mTempCoords[1]
        mTempContentRectForRoot.set(
            contentLeft,
            contentTop,
            contentLeft + mContentContainer.getWidth(),
            contentTop + mContentContainer.getHeight(),
        )
        root.setContentRect(mTempContentRectForRoot)
    }

    private fun createWidgetInfo(): WidgetInfo {
        mTempContentRect.set(
            mRelativeCoordsForToolbar.x,
            mRelativeCoordsForToolbar.y,
            mRelativeCoordsForToolbar.x + mPopupWidth,
            mRelativeCoordsForToolbar.y + mPopupHeight,
        )
        val widgetInfo = WidgetInfo()
        widgetInfo.sequenceNumber = mSequenceNumber
        widgetInfo.contentRect = mTempContentRect
        widgetInfo.surfacePackage = this.surfacePackage
        return widgetInfo
    }

    private val surfacePackage: SurfacePackage?
        get() {
            if (mSurfaceControlViewHost == null) {
                check(mHandler.getLooper().getThread() === Thread.currentThread()) {
                    ("UI operations must be called on the same thread as the constructor." +
                        "thisThread = " +
                        Thread.currentThread() +
                        " handlerThread = " +
                        mHandler.getLooper().getThread())
                }
                mSurfaceControlViewHost =
                    SurfaceControlViewHost(
                        mContext,
                        mContext.getDisplay(),
                        InputTransferToken(mHostInputToken),
                        "RemoteSelectionToolbar",
                    )
                mSurfaceControlViewHost!!.setView(mContentHolder, mPopupWidth, mPopupHeight)
            }
            if (mSurfacePackage == null) {
                mSurfacePackage = mSurfaceControlViewHost!!.getSurfacePackage()
            }
            return mSurfacePackage
        }

    private fun releaseSurfaceControlViewHost() {
        mContentContainer.removeAllViews()
        if (mSurfaceControlViewHost != null) {
            // If hiding has finished before dismissing there will not be a SCVH to release at this
            // point.
            mSurfaceControlViewHost!!.release()
            mSurfaceControlViewHost = null
        }
        mSurfacePackage = null
    }

    private fun layoutMenuItems(menuItems: MutableList<ToolbarMenuItem>, suggestedWidth: Int) {
        var menuItems = menuItems
        cancelOverflowAnimations()
        clearPanels()

        menuItems = layoutMainPanelItems(menuItems, getAdjustedToolbarWidth(suggestedWidth))
        if (!menuItems.isEmpty()) {
            // Add remaining items to the overflow.
            layoutOverflowPanelItems(menuItems)
        }
        updatePopupSize()
    }

    /** Show the specified selection toolbar. */
    fun show(showInfo: ShowInfo) {
        debugLog("show() for " + showInfo)

        mSequenceNumber = showInfo.sequenceNumber
        mMenuItems = transformMenuItems(mContext, showInfo.menuItems)
        mViewPortOnScreen.set(showInfo.viewPortOnScreen)

        if (showInfo.layoutRequired) {
            layoutMenuItems(mMenuItems!!, showInfo.suggestedWidth)
        }

        if (this.isHidden) {
            cancelDismissAndHideAnimations()
        }
        cancelOverflowAnimations()
        refreshCoordinatesAndOverflowDirection(showInfo.contentRect)
        preparePopupContent()

        if (!this.isShowing) {
            mShowAnimation.start()
            mCallbackWrapper.onShown(createWidgetInfo())
        } else {
            mCallbackWrapper.onWidgetUpdated(createWidgetInfo())
        }

        mSurfaceControlViewHost!!.relayout(mPopupWidth, mPopupHeight)

        mState = TOOLBAR_STATE_SHOWN
        // TODO(b/215681595): Use Choreographer to coordinate for show between different thread
        mPreviousContentRect.set(showInfo.contentRect)
    }

    /** Dismiss the specified selection toolbar. */
    fun dismiss(uid: Int) {
        debugLog("dismiss for uid: " + uid)
        if (mState == TOOLBAR_STATE_DISMISSED) {
            return
        }

        if (!mImmediateHideAnimation.isStarted()) {
            mDelayedHideAnimation.start()
        }
        mContentHolder.setContentRectEmpty()
        mState = TOOLBAR_STATE_DISMISSED
    }

    /** Hide the specified selection toolbar. */
    fun hide(uid: Int) {
        debugLog("hide for uid: " + uid)
        if (!this.isShowing) {
            return
        }
        mDelayedHideAnimation.cancel()
        mImmediateHideAnimation.start()
        mContentHolder.setContentRectEmpty()
        mState = TOOLBAR_STATE_HIDDEN
    }

    val isShowing: Boolean
        get() = mState == TOOLBAR_STATE_SHOWN

    val isHidden: Boolean
        get() = mState == TOOLBAR_STATE_HIDDEN

    private fun refreshCoordinatesAndOverflowDirection(contentRectOnScreen: Rect) {
        val x: Int
        if (mPopupWidth > mViewPortOnScreen.width()) {
            // Not enough space - prefer to position as far left as possible
            x = mViewPortOnScreen.left
        } else {
            // Initialize x ensuring that the toolbar isn't rendered behind the system bar insets
            x =
                Math.clamp(
                    (contentRectOnScreen.centerX() - mPopupWidth / 2).toLong(),
                    mViewPortOnScreen.left,
                    mViewPortOnScreen.right - mPopupWidth,
                )
        }

        val y: Int

        val availableHeightAboveContent = contentRectOnScreen.top - mViewPortOnScreen.top
        val availableHeightBelowContent = mViewPortOnScreen.bottom - contentRectOnScreen.bottom

        val margin = 2 * mMarginVertical
        val toolbarHeightWithVerticalMargin = mLineHeight + margin

        if (!hasOverflow()) {
            if (availableHeightAboveContent >= toolbarHeightWithVerticalMargin) {
                // There is enough space at the top of the content.
                y = contentRectOnScreen.top - toolbarHeightWithVerticalMargin
            } else if (availableHeightBelowContent >= toolbarHeightWithVerticalMargin) {
                // There is enough space at the bottom of the content.
                y = contentRectOnScreen.bottom
            } else if (availableHeightBelowContent >= mLineHeight) {
                // Just enough space to fit the toolbar with no vertical margins.
                y = contentRectOnScreen.bottom - mMarginVertical
            } else {
                // Not enough space. Prefer to position as high as possible.
                y =
                    max(
                        mViewPortOnScreen.top,
                        contentRectOnScreen.top - toolbarHeightWithVerticalMargin,
                    )
            }
        } else {
            // Has an overflow.
            val minimumOverflowHeightWithMargin =
                calculateOverflowHeight(MIN_OVERFLOW_SIZE) + margin
            val availableHeightThroughContentDown =
                (mViewPortOnScreen.bottom - contentRectOnScreen.top +
                    toolbarHeightWithVerticalMargin)
            val availableHeightThroughContentUp =
                (contentRectOnScreen.bottom - mViewPortOnScreen.top +
                    toolbarHeightWithVerticalMargin)

            if (availableHeightAboveContent >= minimumOverflowHeightWithMargin) {
                // There is enough space at the top of the content rect for the overflow.
                // Position above and open upwards.
                updateOverflowHeight(availableHeightAboveContent - margin)
                y = contentRectOnScreen.top - mPopupHeight
                mOpenOverflowUpwards = true
            } else if (
                availableHeightAboveContent >= toolbarHeightWithVerticalMargin &&
                    availableHeightThroughContentDown >= minimumOverflowHeightWithMargin
            ) {
                // There is enough space at the top of the content rect for the main panel
                // but not the overflow.
                // Position above but open downwards.
                updateOverflowHeight(availableHeightThroughContentDown - margin)
                y = contentRectOnScreen.top - toolbarHeightWithVerticalMargin
                mOpenOverflowUpwards = false
            } else if (availableHeightBelowContent >= minimumOverflowHeightWithMargin) {
                // There is enough space at the bottom of the content rect for the overflow.
                // Position below and open downwards.
                updateOverflowHeight(availableHeightBelowContent - margin)
                y = contentRectOnScreen.bottom
                mOpenOverflowUpwards = false
            } else if (
                availableHeightBelowContent >= toolbarHeightWithVerticalMargin &&
                    mViewPortOnScreen.height() >= minimumOverflowHeightWithMargin
            ) {
                // There is enough space at the bottom of the content rect for the main panel
                // but not the overflow.
                // Position below but open upwards.
                updateOverflowHeight(availableHeightThroughContentUp - margin)
                y = (contentRectOnScreen.bottom + toolbarHeightWithVerticalMargin - mPopupHeight)
                mOpenOverflowUpwards = true
            } else {
                // Not enough space.
                // Position at the top of the view port and open downwards.
                updateOverflowHeight(mViewPortOnScreen.height() - margin)
                y = mViewPortOnScreen.top
                mOpenOverflowUpwards = false
            }
        }
        mRelativeCoordsForToolbar.set(x, y)
    }

    private fun cancelDismissAndHideAnimations() {
        mDelayedHideAnimation.cancel()
        mImmediateHideAnimation.cancel()
    }

    private fun cancelOverflowAnimations() {
        mContentContainer.clearAnimation()
        mMainPanel.animate().cancel()
        mOverflowPanel.animate().cancel()
        mToArrow.stop()
        mToOverflow.stop()
    }

    private fun openOverflow() {
        val targetWidth = mOverflowPanelSize!!.getWidth()
        val targetHeight = mOverflowPanelSize!!.getHeight()
        val startWidth = mContentContainer.getWidth()
        val startHeight = mContentContainer.getHeight()
        val startY = mContentContainer.getY()
        val left = mContentContainer.getX()
        val right = left + mContentContainer.getWidth()
        val widthAnimation: Animation =
            object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                    val deltaWidth = (interpolatedTime * (targetWidth - startWidth)).toInt()
                    setWidth(mContentContainer, startWidth + deltaWidth)
                    if (isInRtlMode()) {
                        mContentContainer.setX(left)

                        // Lock the panels in place.
                        mMainPanel.setX(0f)
                        mOverflowPanel.setX(0f)
                    } else {
                        mContentContainer.setX(right - mContentContainer.getWidth())

                        // Offset the panels' positions so they look like they're locked in place
                        // on the screen.
                        mMainPanel.setX((mContentContainer.getWidth() - startWidth).toFloat())
                        mOverflowPanel.setX((mContentContainer.getWidth() - targetWidth).toFloat())
                    }
                }
            }
        val heightAnimation: Animation =
            object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                    val deltaHeight = (interpolatedTime * (targetHeight - startHeight)).toInt()
                    setHeight(mContentContainer, startHeight + deltaHeight)
                    if (mOpenOverflowUpwards) {
                        mContentContainer.setY(
                            startY - (mContentContainer.getHeight() - startHeight)
                        )
                        positionContentYCoordinatesIfOpeningOverflowUpwards()
                    }
                }
            }
        val overflowButtonStartX = mOverflowButton.getX()
        val overflowButtonTargetX =
            if (isInRtlMode()) overflowButtonStartX + targetWidth - mOverflowButton.getWidth()
            else overflowButtonStartX - targetWidth + mOverflowButton.getWidth()
        val overflowButtonAnimation: Animation =
            object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                    val overflowButtonX =
                        (overflowButtonStartX +
                            interpolatedTime * (overflowButtonTargetX - overflowButtonStartX))
                    val deltaContainerWidth =
                        (if (isInRtlMode()) 0 else mContentContainer.getWidth() - startWidth)
                            .toFloat()
                    val actualOverflowButtonX = overflowButtonX + deltaContainerWidth
                    mOverflowButton.setX(actualOverflowButtonX)
                    updateFloatingToolbarRootContentRect()
                }
            }
        widthAnimation.setInterpolator(mLogAccelerateInterpolator)
        widthAnimation.setDuration(this.animationDuration.toLong())
        heightAnimation.setInterpolator(mFastOutSlowInInterpolator)
        heightAnimation.setDuration(this.animationDuration.toLong())
        overflowButtonAnimation.setInterpolator(mFastOutSlowInInterpolator)
        overflowButtonAnimation.setDuration(this.animationDuration.toLong())
        mOpenOverflowAnimation.getAnimations().clear()
        mOpenOverflowAnimation.addAnimation(widthAnimation)
        mOpenOverflowAnimation.addAnimation(heightAnimation)
        mOpenOverflowAnimation.addAnimation(overflowButtonAnimation)
        mContentContainer.startAnimation(mOpenOverflowAnimation)
        mIsOverflowOpen = true
        mMainPanel
            .animate()
            .alpha(0f)
            .withLayer()
            .setInterpolator(mLinearOutSlowInInterpolator)
            .setDuration(250)
            .start()
        mOverflowPanel.setAlpha(1f) // fadeIn in 0ms.
    }

    private fun closeOverflow() {
        val targetWidth = mMainPanelSize!!.getWidth()
        val startWidth = mContentContainer.getWidth()
        val left = mContentContainer.getX()
        val right = left + mContentContainer.getWidth()
        val widthAnimation: Animation =
            object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                    val deltaWidth = (interpolatedTime * (targetWidth - startWidth)).toInt()
                    setWidth(mContentContainer, startWidth + deltaWidth)
                    if (isInRtlMode()) {
                        mContentContainer.setX(left)

                        // Lock the panels in place.
                        mMainPanel.setX(0f)
                        mOverflowPanel.setX(0f)
                    } else {
                        mContentContainer.setX(right - mContentContainer.getWidth())

                        // Offset the panels' positions so they look like they're locked in place
                        // on the screen.
                        mMainPanel.setX((mContentContainer.getWidth() - targetWidth).toFloat())
                        mOverflowPanel.setX((mContentContainer.getWidth() - startWidth).toFloat())
                    }
                }
            }
        val targetHeight = mMainPanelSize!!.getHeight()
        val startHeight = mContentContainer.getHeight()
        val bottom = mContentContainer.getY() + mContentContainer.getHeight()
        val heightAnimation: Animation =
            object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                    val deltaHeight = (interpolatedTime * (targetHeight - startHeight)).toInt()
                    setHeight(mContentContainer, startHeight + deltaHeight)
                    if (mOpenOverflowUpwards) {
                        mContentContainer.setY(bottom - mContentContainer.getHeight())
                        positionContentYCoordinatesIfOpeningOverflowUpwards()
                    }
                }
            }
        val overflowButtonStartX = mOverflowButton.getX()
        val overflowButtonTargetX =
            if (isInRtlMode()) overflowButtonStartX - startWidth + mOverflowButton.getWidth()
            else overflowButtonStartX + startWidth - mOverflowButton.getWidth()
        val overflowButtonAnimation: Animation =
            object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                    val overflowButtonX =
                        (overflowButtonStartX +
                            interpolatedTime * (overflowButtonTargetX - overflowButtonStartX))
                    val deltaContainerWidth =
                        (if (isInRtlMode()) 0 else mContentContainer.getWidth() - startWidth)
                            .toFloat()
                    val actualOverflowButtonX = overflowButtonX + deltaContainerWidth
                    mOverflowButton.setX(actualOverflowButtonX)
                    updateFloatingToolbarRootContentRect()
                }
            }
        widthAnimation.setInterpolator(mFastOutSlowInInterpolator)
        widthAnimation.setDuration(this.animationDuration.toLong())
        heightAnimation.setInterpolator(mLogAccelerateInterpolator)
        heightAnimation.setDuration(this.animationDuration.toLong())
        overflowButtonAnimation.setInterpolator(mFastOutSlowInInterpolator)
        overflowButtonAnimation.setDuration(this.animationDuration.toLong())
        mCloseOverflowAnimation.getAnimations().clear()
        mCloseOverflowAnimation.addAnimation(widthAnimation)
        mCloseOverflowAnimation.addAnimation(heightAnimation)
        mCloseOverflowAnimation.addAnimation(overflowButtonAnimation)
        mContentContainer.startAnimation(mCloseOverflowAnimation)
        mIsOverflowOpen = false
        mMainPanel
            .animate()
            .alpha(1f)
            .withLayer()
            .setInterpolator(mFastOutLinearInInterpolator)
            .setDuration(100)
            .start()
        mOverflowPanel
            .animate()
            .alpha(0f)
            .withLayer()
            .setInterpolator(mLinearOutSlowInInterpolator)
            .setDuration(150)
            .start()
    }

    /**
     * Defines the position of the floating toolbar popup panels when transition animation has
     * stopped.
     */
    private fun setPanelsStatesAtRestingPosition() {
        mOverflowButton.setEnabled(true)
        mOverflowPanel.awakenScrollBars()

        if (mIsOverflowOpen) {
            // Set open state.
            val containerSize = mOverflowPanelSize
            Companion.setSize(mContentContainer, containerSize!!)
            mMainPanel.setAlpha(0f)
            mMainPanel.setVisibility(View.INVISIBLE)
            mOverflowPanel.setAlpha(1f)
            mOverflowPanel.setVisibility(View.VISIBLE)
            mOverflowButton.setImageDrawable(mArrow)
            mOverflowButton.setContentDescription(
                mContext.getString(R.string.floating_toolbar_close_overflow_description)
            )

            // Update x-coordinates depending on RTL state.
            if (isInRtlMode()) {
                mContentContainer.setX(mMarginHorizontal.toFloat()) // align left
                mMainPanel.setX(0f) // align left
                mOverflowButton.setX( // align right
                    (containerSize.getWidth() - mOverflowButtonSize.getWidth()).toFloat()
                )
                mOverflowPanel.setX(0f) // align left
            } else {
                mContentContainer.setX( // align right
                    (mPopupWidth - containerSize.getWidth() - mMarginHorizontal).toFloat()
                )
                mMainPanel.setX(-mContentContainer.getX()) // align right
                mOverflowButton.setX(0f) // align left
                mOverflowPanel.setX(0f) // align left
            }

            // Update y-coordinates depending on overflow's open direction.
            if (mOpenOverflowUpwards) {
                mContentContainer.setY(mMarginVertical.toFloat()) // align top
                mMainPanel.setY( // align bottom
                    (containerSize.getHeight() - mContentContainer.getHeight()).toFloat()
                )
                mOverflowButton.setY( // align bottom
                    (containerSize.getHeight() - mOverflowButtonSize.getHeight()).toFloat()
                )
                mOverflowPanel.setY(0f) // align top
            } else {
                // opens downwards.
                mContentContainer.setY(mMarginVertical.toFloat()) // align top
                mMainPanel.setY(0f) // align top
                mOverflowButton.setY(0f) // align top
                mOverflowPanel.setY(mOverflowButtonSize.getHeight().toFloat()) // align bottom
            }
        } else {
            // Overflow not open. Set closed state.
            val containerSize = mMainPanelSize
            Companion.setSize(mContentContainer, containerSize!!)
            mMainPanel.setAlpha(1f)
            mMainPanel.setVisibility(View.VISIBLE)
            mOverflowPanel.setAlpha(0f)
            mOverflowPanel.setVisibility(View.INVISIBLE)
            mOverflowButton.setImageDrawable(mOverflow)
            mOverflowButton.setContentDescription(
                mContext.getString(R.string.floating_toolbar_open_overflow_description)
            )

            if (hasOverflow()) {
                // Update x-coordinates depending on RTL state.
                if (isInRtlMode()) {
                    mContentContainer.setX(mMarginHorizontal.toFloat()) // align left
                    mMainPanel.setX(0f) // align left
                    mOverflowButton.setX(0f) // align left
                    mOverflowPanel.setX(0f) // align left
                } else {
                    mContentContainer.setX( // align right
                        (mPopupWidth - containerSize.getWidth() - mMarginHorizontal).toFloat()
                    )
                    mMainPanel.setX(0f) // align left
                    mOverflowButton.setX( // align right
                        (containerSize.getWidth() - mOverflowButtonSize.getWidth()).toFloat()
                    )
                    mOverflowPanel.setX( // align right
                        (containerSize.getWidth() - mOverflowPanelSize!!.getWidth()).toFloat()
                    )
                }

                // Update y-coordinates depending on overflow's open direction.
                if (mOpenOverflowUpwards) {
                    mContentContainer.setY( // align bottom
                        (mMarginVertical + mOverflowPanelSize!!.getHeight() -
                                containerSize.getHeight())
                            .toFloat()
                    )
                    mMainPanel.setY(0f) // align top
                    mOverflowButton.setY(0f) // align top
                    mOverflowPanel.setY( // align bottom
                        (containerSize.getHeight() - mOverflowPanelSize!!.getHeight()).toFloat()
                    )
                } else {
                    // opens downwards.
                    mContentContainer.setY(mMarginVertical.toFloat()) // align top
                    mMainPanel.setY(0f) // align top
                    mOverflowButton.setY(0f) // align top
                    mOverflowPanel.setY(mOverflowButtonSize.getHeight().toFloat()) // align bottom
                }
            } else {
                // No overflow.
                mContentContainer.setX(mMarginHorizontal.toFloat()) // align left
                mContentContainer.setY(mMarginVertical.toFloat()) // align top
                mMainPanel.setX(0f) // align left
                mMainPanel.setY(0f) // align top
            }
        }
    }

    private fun updateOverflowHeight(suggestedHeight: Int) {
        if (hasOverflow()) {
            val maxItemSize = (suggestedHeight - mOverflowButtonSize.getHeight()) / mLineHeight
            val newHeight = calculateOverflowHeight(maxItemSize)
            if (mOverflowPanelSize!!.getHeight() != newHeight) {
                mOverflowPanelSize = Size(mOverflowPanelSize!!.getWidth(), newHeight)
            }
            Companion.setSize(mOverflowPanel, mOverflowPanelSize!!)
            if (mIsOverflowOpen) {
                Companion.setSize(mContentContainer, mOverflowPanelSize!!)
                if (mOpenOverflowUpwards) {
                    val deltaHeight = mOverflowPanelSize!!.getHeight() - newHeight
                    mContentContainer.setY(mContentContainer.getY() + deltaHeight)
                    mOverflowButton.setY(mOverflowButton.getY() - deltaHeight)
                }
            } else {
                Companion.setSize(mContentContainer, mMainPanelSize!!)
            }
            updatePopupSize()
        }
    }

    private fun updatePopupSize() {
        var width = 0
        var height = 0
        if (mMainPanelSize != null) {
            width = max(width, mMainPanelSize!!.getWidth())
            height = max(height, mMainPanelSize!!.getHeight())
        }
        if (mOverflowPanelSize != null) {
            width = max(width, mOverflowPanelSize!!.getWidth())
            height = max(height, mOverflowPanelSize!!.getHeight())
        }

        mPopupWidth = width + mMarginHorizontal * 2
        mPopupHeight = height + mMarginVertical * 2
        maybeComputeTransitionDurationScale()
    }

    private fun getAdjustedToolbarWidth(suggestedWidth: Int): Int {
        var width = suggestedWidth
        val maximumWidth =
            mViewPortOnScreen.width() -
                2 *
                    mContext
                        .getResources()
                        .getDimensionPixelSize(R.dimen.floating_toolbar_horizontal_margin)
        if (width <= 0) {
            width =
                mContext
                    .getResources()
                    .getDimensionPixelSize(R.dimen.floating_toolbar_preferred_width)
        }
        return min(width, maximumWidth)
    }

    private fun isInRtlMode(): Boolean =
        // TODO STOPSHIP b/411457891 the context might not have the right information
        mContext.getApplicationInfo().hasRtlSupport() &&
            (mContext.getResources().getConfiguration().getLayoutDirection() ==
                View.LAYOUT_DIRECTION_RTL)

    private fun hasOverflow(): Boolean {
        return mOverflowPanelSize != null
    }

    /**
     * Fits as many menu items in the main panel and returns a list of the menu items that were not
     * fit in.
     *
     * @return The menu items that are not included in this main panel.
     */
    private fun layoutMainPanelItems(
        menuItems: MutableList<ToolbarMenuItem>,
        toolbarWidth: Int,
    ): MutableList<ToolbarMenuItem> {
        val remainingMenuItems = ArrayList<ToolbarMenuItem>()
        // add the overflow menu items to the end of the remainingMenuItems list.
        val overflowMenuItems = mutableListOf<ToolbarMenuItem>()
        for (menuItem in menuItems) {
            if (
                menuItem.itemId != android.R.id.textAssist &&
                    menuItem.priority == ToolbarMenuItem.PRIORITY_OVERFLOW
            ) {
                overflowMenuItems.add(menuItem)
            } else {
                remainingMenuItems.add(menuItem)
            }
        }
        remainingMenuItems.addAll(overflowMenuItems)

        mMainPanel.removeAllViews()
        mMainPanel.setPaddingRelative(0, 0, 0, 0)

        var availableWidth = toolbarWidth
        var isFirstItem = true
        while (!remainingMenuItems.isEmpty()) {
            val menuItem = remainingMenuItems.get(0)
            // if this is the first item, regardless of requiresOverflow(), it should be
            // displayed on the main panel. Otherwise all items including this one will be
            // overflow items, and should be displayed in overflow panel.
            if (!isFirstItem && menuItem.priority == ToolbarMenuItem.PRIORITY_OVERFLOW) {
                break
            }
            val showIcon = isFirstItem && menuItem.itemId == R.id.textAssist
            val menuItemButton: View =
                createMenuItemButton(mContext, menuItem, mIconTextSpacing, showIcon)
            if (!showIcon && menuItemButton is LinearLayout) {
                menuItemButton.setGravity(Gravity.CENTER)
            }
            // Adding additional start padding for the first button to even out button spacing.
            if (isFirstItem) {
                menuItemButton.setPaddingRelative(
                    (1.5 * menuItemButton.getPaddingStart()).toInt(),
                    menuItemButton.getPaddingTop(),
                    menuItemButton.getPaddingEnd(),
                    menuItemButton.getPaddingBottom(),
                )
            }
            // Adding additional end padding for the last button to even out button spacing.
            val isLastItem = remainingMenuItems.size == 1
            if (isLastItem) {
                menuItemButton.setPaddingRelative(
                    menuItemButton.getPaddingStart(),
                    menuItemButton.getPaddingTop(),
                    (1.5 * menuItemButton.getPaddingEnd()).toInt(),
                    menuItemButton.getPaddingBottom(),
                )
            }
            menuItemButton.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            val menuItemButtonWidth = min(menuItemButton.getMeasuredWidth(), toolbarWidth)
            // Check if we can fit an item while reserving space for the overflowButton.
            val canFitWithOverflow =
                menuItemButtonWidth <= availableWidth - mOverflowButtonSize.getWidth()
            val canFitNoOverflow = isLastItem && menuItemButtonWidth <= availableWidth
            if (canFitWithOverflow || canFitNoOverflow) {
                menuItemButton.setTag(menuItem)
                menuItemButton.setOnClickListener(mMenuItemButtonOnClickListener)
                // Set tooltips for main panel items, but not overflow items (b/35726766).
                menuItemButton.setTooltipText(menuItem.tooltipText)
                mMainPanel.addView(menuItemButton)
                val params = menuItemButton.getLayoutParams()
                params.width = menuItemButtonWidth
                menuItemButton.setLayoutParams(params)
                availableWidth -= menuItemButtonWidth
                remainingMenuItems.removeAt(0)
            } else {
                break
            }
            isFirstItem = false
        }
        if (!remainingMenuItems.isEmpty()) {
            // Reserve space for overflowButton.
            mMainPanel.setPaddingRelative(0, 0, mOverflowButtonSize.getWidth(), 0)
        }
        mMainPanelSize = measure(mMainPanel)

        return remainingMenuItems
    }

    private fun layoutOverflowPanelItems(menuItems: MutableList<ToolbarMenuItem>) {
        val overflowPanelAdapter = mOverflowPanel.getAdapter() as ArrayAdapter<ToolbarMenuItem?>
        overflowPanelAdapter.clear()
        val size = menuItems.size
        for (i in 0..<size) {
            overflowPanelAdapter.add(menuItems.get(i))
        }
        mOverflowPanel.setAdapter(overflowPanelAdapter)
        if (mOpenOverflowUpwards) {
            mOverflowPanel.setY(0f)
        } else {
            mOverflowPanel.setY(mOverflowButtonSize.getHeight().toFloat())
        }
        val width = max(this.overflowWidth, mOverflowButtonSize.getWidth())
        val height = calculateOverflowHeight(MAX_OVERFLOW_SIZE)
        mOverflowPanelSize = Size(width, height)
        Companion.setSize(mOverflowPanel, mOverflowPanelSize!!)
    }

    /** Resets the content container and appropriately position it's panels. */
    private fun preparePopupContent() {
        mContentContainer.removeAllViews()
        // Add views in the specified order so they stack up as expected.
        // Order: overflowPanel, mainPanel, overflowButton.
        if (hasOverflow()) {
            mContentContainer.addView(mOverflowPanel)
        }
        mContentContainer.addView(mMainPanel)
        if (hasOverflow()) {
            mContentContainer.addView(mOverflowButton)
        }
        setPanelsStatesAtRestingPosition()

        // The positioning of contents in RTL is wrong when the view is first rendered.
        // Hide the view and post a runnable to recalculate positions and render the view.
        // TODO: Investigate why this happens and fix.
        if (isInRtlMode()) {
            mContentContainer.setAlpha(0f)
            mContentContainer.post(mPreparePopupContentRTLHelper)
        }
    }

    /** Clears out the panels and their container. Resets their calculated sizes. */
    private fun clearPanels() {
        mIsOverflowOpen = false
        mMainPanelSize = null
        mMainPanel.removeAllViews()
        mOverflowPanelSize = null
        val overflowPanelAdapter = mOverflowPanel.getAdapter() as ArrayAdapter<ToolbarMenuItem?>
        overflowPanelAdapter.clear()
        mOverflowPanel.setAdapter(overflowPanelAdapter)
        mContentContainer.removeAllViews()
    }

    private fun positionContentYCoordinatesIfOpeningOverflowUpwards() {
        if (mOpenOverflowUpwards) {
            mMainPanel.setY(
                (mContentContainer.getHeight() - mMainPanelSize!!.getHeight()).toFloat()
            )
            mOverflowButton.setY(
                (mContentContainer.getHeight() - mOverflowButton.getHeight()).toFloat()
            )
            mOverflowPanel.setY(
                (mContentContainer.getHeight() - mOverflowPanelSize!!.getHeight()).toFloat()
            )
        }
    }

    private val overflowWidth: Int
        get() {
            var overflowWidth = 0
            val count = mOverflowPanel.getAdapter().getCount()
            for (i in 0..<count) {
                val menuItem = mOverflowPanel.getAdapter().getItem(i) as ToolbarMenuItem
                overflowWidth =
                    max(mOverflowPanelViewHelper.calculateWidth(menuItem), overflowWidth)
            }
            return overflowWidth
        }

    private fun calculateOverflowHeight(maxItemSize: Int): Int {
        // Maximum of 4 items, minimum of 2 if the overflow has to scroll.
        val actualSize =
            min(
                MAX_OVERFLOW_SIZE,
                min(max(MIN_OVERFLOW_SIZE, maxItemSize), mOverflowPanel.getCount()),
            )
        var extension = 0
        if (actualSize < mOverflowPanel.getCount()) {
            // The overflow will require scrolling to get to all the items.
            // Extend the height so that part of the hidden items is displayed.
            extension = (mLineHeight * 0.5f).toInt()
        }
        return (actualSize * mLineHeight + mOverflowButtonSize.getHeight() + extension)
    }

    private val animationDuration: Int
        /**
         * NOTE: Use only in android.view.animation.* animations. Do not use in android.animation.*
         * animations. See comment about this in the code.
         */
        get() {
            if (mTransitionDurationScale < 150) {
                // For smaller transition, decrease the time.
                return 200
            } else if (mTransitionDurationScale > 300) {
                // For bigger transition, increase the time.
                return 300
            }

            // Scale the animation duration with getDurationScale(). This allows
            // android.view.animation.* animations to scale just like android.animation.* animations
            // when  animator duration scale is adjusted in "Developer Options".
            // For this reason, do not use this method for android.animation.* animations.
            return (250 * ValueAnimator.getDurationScale()).toInt()
        }

    private fun maybeComputeTransitionDurationScale() {
        if (mMainPanelSize != null && mOverflowPanelSize != null) {
            val w = mMainPanelSize!!.getWidth() - mOverflowPanelSize!!.getWidth()
            val h = mOverflowPanelSize!!.getHeight() - mMainPanelSize!!.getHeight()
            mTransitionDurationScale =
                (sqrt((w * w + h * h).toDouble()) /
                        mContentContainer.getContext().getResources().getDisplayMetrics().density)
                    .toInt()
        }
    }

    private fun createMainPanel(): ViewGroup {
        return object : LinearLayout(mContext) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                var widthMeasureSpec = widthMeasureSpec
                if (isOverflowAnimating()) {
                    // Update widthMeasureSpec to make sure that this view is not clipped
                    // as we offset its coordinates with respect to its parent.
                    widthMeasureSpec =
                        MeasureSpec.makeMeasureSpec(
                            mMainPanelSize!!.getWidth(),
                            MeasureSpec.EXACTLY,
                        )
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }

            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
                // Intercept the touch event while the overflow is animating.
                return isOverflowAnimating()
            }
        }
    }

    private fun createOverflowButton(): ImageButton {
        val overflowButton =
            LayoutInflater.from(mContext).inflate(R.layout.floating_popup_overflow_button, null)
                as ImageButton
        overflowButton.setImageDrawable(mOverflow)
        overflowButton.setOnClickListener(
            View.OnClickListener { v: View? ->
                if (this.isShowing) {
                    preparePopupContent()
                    val widgetInfo = createWidgetInfo()
                    mSurfaceControlViewHost!!.relayout(mPopupWidth, mPopupHeight)
                    mCallbackWrapper.onWidgetUpdated(widgetInfo)
                }
                if (mIsOverflowOpen) {
                    overflowButton.setImageDrawable(mToOverflow)
                    mToOverflow.start()
                    closeOverflow()
                } else {
                    overflowButton.setImageDrawable(mToArrow)
                    mToArrow.start()
                    openOverflow()
                }
            }
        )
        return overflowButton
    }

    private fun createOverflowPanel(): OverflowPanel {
        val overflowPanel = OverflowPanel(this)
        overflowPanel.setLayoutParams(
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        )
        overflowPanel.setDivider(null)
        overflowPanel.setDividerHeight(0)

        val adapter: ArrayAdapter<*> =
            object : ArrayAdapter<ToolbarMenuItem?>(mContext, 0) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return mOverflowPanelViewHelper.getView(
                        getItem(position),
                        mOverflowPanelSize!!.getWidth(),
                        convertView,
                    )
                }
            }
        overflowPanel.setAdapter(adapter)
        overflowPanel.setOnItemClickListener(
            OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                val menuItem = overflowPanel.getAdapter().getItem(position) as ToolbarMenuItem
                mCallbackWrapper.onMenuItemClicked(menuItem.itemIndex)
            }
        )
        return overflowPanel
    }

    private fun isOverflowAnimating(): Boolean {
        val overflowOpening =
            mOpenOverflowAnimation.hasStarted() && !mOpenOverflowAnimation.hasEnded()
        val overflowClosing =
            mCloseOverflowAnimation.hasStarted() && !mCloseOverflowAnimation.hasEnded()
        return overflowOpening || overflowClosing
    }

    private fun createOverflowAnimationListener(): Animation.AnimationListener {
        return object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                // Disable the overflow button while it's animating.
                // It will be re-enabled when the animation stops.
                mOverflowButton.setEnabled(false)
                // Ensure both panels have visibility turned on when the overflow animation
                // starts.
                mMainPanel.setVisibility(View.VISIBLE)
                mOverflowPanel.setVisibility(View.VISIBLE)
            }

            override fun onAnimationEnd(animation: Animation?) {
                // Posting this because it seems like this is called before the animation
                // actually ends.
                mContentContainer.post(Runnable { setPanelsStatesAtRestingPosition() })
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        }
    }

    /** A custom ListView for the overflow panel. */
    private inner class OverflowPanel(private val mPopup: RemoteSelectionToolbar) :
        ListView(Objects.requireNonNull<RemoteSelectionToolbar?>(mPopup).mContext) {
        init {
            setScrollBarDefaultDelayBeforeFade(ViewConfiguration.getScrollDefaultDelay() * 3)
            setScrollIndicators(SCROLL_INDICATOR_TOP or SCROLL_INDICATOR_BOTTOM)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // Update heightMeasureSpec to make sure that this view is not clipped
            // as we offset it's coordinates with respect to its parent.
            var heightMeasureSpec = heightMeasureSpec
            val height =
                (mPopup.mOverflowPanelSize!!.getHeight() - mPopup.mOverflowButtonSize.getHeight())
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
            if (isOverflowAnimating()) {
                // Eat the touch event.
                return true
            }
            return super.dispatchTouchEvent(ev)
        }

        public override fun awakenScrollBars(): Boolean {
            return super.awakenScrollBars()
        }
    }

    /** A custom interpolator used for various floating toolbar animations. */
    private class LogAccelerateInterpolator : Interpolator {
        override fun getInterpolation(t: Float): Float {
            return 1 - computeLog(1 - t, BASE) * LOGS_SCALE
        }

        companion object {
            private const val BASE = 100
            private val LOGS_SCALE: Float = 1f / computeLog(1f, BASE)

            private fun computeLog(t: Float, base: Int): Float {
                return (1 - base.toDouble().pow(-t.toDouble())).toFloat()
            }
        }
    }

    /** A helper for generating views for the overflow panel. */
    private class OverflowPanelViewHelper(context: Context?, private val mIconTextSpacing: Int) {
        private val mContext: Context
        private val mCalculator: View
        private val mSidePadding: Int

        init {
            mContext = Objects.requireNonNull<Context>(context)
            mSidePadding =
                context!!
                    .getResources()
                    .getDimensionPixelSize(R.dimen.floating_toolbar_overflow_side_padding)
            mCalculator = createMenuButton(null)
        }

        fun getView(menuItem: ToolbarMenuItem?, minimumWidth: Int, convertView: View?): View {
            var convertView = convertView
            Objects.requireNonNull<ToolbarMenuItem?>(menuItem)
            if (convertView != null) {
                Companion.updateMenuItemButton(
                    convertView,
                    menuItem!!,
                    mIconTextSpacing,
                    shouldShowIcon(menuItem),
                )
            } else {
                convertView = createMenuButton(menuItem)
            }
            convertView.setMinimumWidth(minimumWidth)
            return convertView
        }

        fun calculateWidth(menuItem: ToolbarMenuItem): Int {
            updateMenuItemButton(mCalculator, menuItem, mIconTextSpacing, shouldShowIcon(menuItem))
            mCalculator.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            return mCalculator.getMeasuredWidth()
        }

        fun createMenuButton(menuItem: ToolbarMenuItem?): View {
            val button: View =
                createMenuItemButton(mContext, menuItem, mIconTextSpacing, shouldShowIcon(menuItem))
            button.setPadding(mSidePadding, 0, mSidePadding, 0)
            return button
        }

        fun shouldShowIcon(menuItem: ToolbarMenuItem?): Boolean {
            if (menuItem != null) {
                return menuItem.groupId == android.R.id.textAssist
            }
            return false
        }
    }

    /** Dumps information about this class. */
    fun dump(prefix: String?, pw: PrintWriter) {
        pw.print(prefix)
        pw.print("state: ")
        pw.println(
            when (mState) {
                TOOLBAR_STATE_SHOWN -> "SHOWN"
                TOOLBAR_STATE_HIDDEN -> "HIDDEN"
                TOOLBAR_STATE_DISMISSED -> "DISMISSED"
                else -> "UNKNOWN"
            }
        )
        pw.print(prefix)
        pw.print("popup width: ")
        pw.println(mPopupWidth)
        pw.print(prefix)
        pw.print("popup height: ")
        pw.println(mPopupHeight)
        pw.print(prefix)
        pw.print("relative coords: ")
        pw.println(mRelativeCoordsForToolbar)
        pw.print(prefix)
        pw.print("main panel size: ")
        pw.println(mMainPanelSize)
        val hasOverflow = hasOverflow()
        pw.print(prefix)
        pw.print("has overflow: ")
        pw.println(hasOverflow)
        if (hasOverflow) {
            pw.print(prefix)
            pw.print("overflow open: ")
            pw.println(mIsOverflowOpen)
            pw.print(prefix)
            pw.print("overflow size: ")
            pw.println(mOverflowPanelSize)
        }
        if (mSurfaceControlViewHost != null) {
            val root = mSurfaceControlViewHost!!.getView() as FloatingToolbarRoot
            root.dump(prefix, pw)
        }
        if (mMenuItems != null) {
            val menuItemSize = mMenuItems!!.size
            pw.print(prefix)
            pw.print("number menu items: ")
            pw.println(menuItemSize)
            for (i in 0..<menuItemSize) {
                pw.print(prefix)
                pw.print("#")
                pw.println(i)
                pw.print(prefix + "  ")
                pw.println(mMenuItems!!.get(i))
            }
        }
    }

    companion object {
        private const val TAG = "RemoteSelectionToolbar"

        /* Minimum and maximum number of items allowed in the overflow. */
        private const val MIN_OVERFLOW_SIZE = 2
        private const val MAX_OVERFLOW_SIZE = 4

        private const val TOOLBAR_STATE_SHOWN = 1
        private const val TOOLBAR_STATE_HIDDEN = 2
        private const val TOOLBAR_STATE_DISMISSED = 3

        private fun measure(view: View): Size {
            Preconditions.checkState(view.getParent() == null)
            view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            return Size(view.getMeasuredWidth(), view.getMeasuredHeight())
        }

        private fun setSize(view: View, width: Int, height: Int) {
            view.setMinimumWidth(width)
            view.setMinimumHeight(height)
            var params = view.getLayoutParams()
            params = if (params == null) ViewGroup.LayoutParams(0, 0) else params
            params.width = width
            params.height = height
            view.setLayoutParams(params)
        }

        private fun setSize(view: View, size: Size) {
            setSize(view, size.getWidth(), size.getHeight())
        }

        private fun setWidth(view: View, width: Int) {
            val params = view.getLayoutParams()
            setSize(view, width, params.height)
        }

        private fun setHeight(view: View, height: Int) {
            val params = view.getLayoutParams()
            setSize(view, params.width, height)
        }

        private fun transformMenuItems(
            context: Context,
            menuItems: MutableList<ToolbarMenuItem>,
        ): MutableList<ToolbarMenuItem> {
            val transformedMenuItems = ArrayList<ToolbarMenuItem>(menuItems.size)
            for (i in menuItems.indices) {
                val menuItem = menuItems.get(i)

                transformedMenuItems.add(
                    when (menuItem.itemId) {
                        android.R.id.paste -> {
                            val newMenuItem = ToolbarMenuItem()

                            newMenuItem.groupId = menuItem.groupId
                            newMenuItem.priority = menuItem.priority
                            newMenuItem.itemIndex = menuItem.itemIndex

                            newMenuItem.itemId = android.R.id.paste
                            newMenuItem.title = context.getString(R.string.paste)
                            newMenuItem.contentDescription = context.getString(R.string.paste)

                            newMenuItem
                        }

                        android.R.id.pasteAsPlainText -> {
                            val newMenuItem = ToolbarMenuItem()

                            newMenuItem.groupId = menuItem.groupId
                            newMenuItem.priority = menuItem.priority
                            newMenuItem.itemIndex = menuItem.itemIndex

                            newMenuItem.itemId = android.R.id.pasteAsPlainText
                            newMenuItem.title = context.getString(R.string.paste_as_plain_text)
                            newMenuItem.contentDescription =
                                context.getString(R.string.paste_as_plain_text)

                            newMenuItem
                        }

                        else -> menuItem
                    }
                )
            }

            return transformedMenuItems
        }

        /** Creates and returns a menu button for the specified menu item. */
        private fun createMenuItemButton(
            context: Context?,
            menuItem: ToolbarMenuItem?,
            iconTextSpacing: Int,
            showIcon: Boolean,
        ): View {
            val menuItemButton =
                LayoutInflater.from(context).inflate(R.layout.floating_popup_menu_button, null)
            if (menuItem != null) {
                updateMenuItemButton(menuItemButton, menuItem, iconTextSpacing, showIcon)
            }
            return menuItemButton
        }

        /** Updates the specified menu item button with the specified menu item data. */
        private fun updateMenuItemButton(
            menuItemButton: View,
            menuItem: ToolbarMenuItem,
            iconTextSpacing: Int,
            showIcon: Boolean,
        ) {
            val buttonText =
                menuItemButton.findViewById<TextView>(R.id.floating_toolbar_menu_item_text)
            buttonText.setEllipsize(null)
            if (TextUtils.isEmpty(menuItem.title)) {
                buttonText.setVisibility(View.GONE)
            } else {
                buttonText.setVisibility(View.VISIBLE)
                buttonText.setText(menuItem.title)
            }
            val buttonIcon =
                menuItemButton.findViewById<ImageView>(R.id.floating_toolbar_menu_item_image)
            if (menuItem.icon == null || !showIcon) {
                buttonIcon.setVisibility(View.GONE)
                buttonText.setPaddingRelative(0, 0, 0, 0)
            } else {
                buttonIcon.setVisibility(View.VISIBLE)
                buttonIcon.setImageDrawable(menuItem.icon.loadDrawable(menuItemButton.getContext()))
                buttonText.setPaddingRelative(iconTextSpacing, 0, 0, 0)
            }
            val contentDescription = menuItem.contentDescription
            if (TextUtils.isEmpty(contentDescription)) {
                menuItemButton.setContentDescription(menuItem.title)
            } else {
                menuItemButton.setContentDescription(contentDescription)
            }
        }

        private fun createContentContainer(context: Context?): ViewGroup {
            val contentContainer =
                LayoutInflater.from(context).inflate(R.layout.floating_popup_container, null)
                    as ViewGroup
            contentContainer.setLayoutParams(
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
            contentContainer.setTag(FloatingToolbar.FLOATING_TOOLBAR_TAG)
            contentContainer.setClipToOutline(true)
            return contentContainer
        }

        /**
         * Creates an "appear" animation for the specified view.
         *
         * @param view The view to animate
         */
        private fun createEnterAnimation(view: View?): AnimatorSet {
            val animation = AnimatorSet()
            animation.playTogether(
                ObjectAnimator.ofFloat<View?>(view, View.ALPHA, 0f, 1f).setDuration(150)
            )
            return animation
        }

        /**
         * Creates a "disappear" animation for the specified view.
         *
         * @param view The view to animate
         * @param startDelay The start delay of the animation
         * @param listener The animation listener
         */
        private fun createExitAnimation(
            view: View?,
            startDelay: Int,
            listener: Animator.AnimatorListener?,
        ): AnimatorSet {
            val animation = AnimatorSet()
            animation.playTogether(
                ObjectAnimator.ofFloat<View?>(view, View.ALPHA, 1f, 0f).setDuration(100)
            )
            animation.setStartDelay(startDelay.toLong())
            if (listener != null) {
                animation.addListener(listener)
            }
            return animation
        }

        /** Returns a re-themed context with controlled look and feel for views. */
        private fun wrapContext(originalContext: Context?, showInfo: ShowInfo): Context {
            val themeId =
                if (showInfo.isLightTheme) R.style.Theme_DeviceDefault_Light
                else R.style.Theme_DeviceDefault
            return ContextThemeWrapper(originalContext, themeId)
                .createConfigurationContext(showInfo.configuration)
        }

        private fun debugLog(message: String) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, message)
            }
        }
    }
}
