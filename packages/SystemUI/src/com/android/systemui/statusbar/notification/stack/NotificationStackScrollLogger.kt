package com.android.systemui.statusbar.notification.stack

import android.view.MotionEvent
import android.view.ViewGroup
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel.DEBUG
import com.android.systemui.log.core.LogLevel.ERROR
import com.android.systemui.log.core.LogLevel.INFO
import com.android.systemui.log.core.LogLevel.WARNING
import com.android.systemui.log.dagger.NotificationHeadsUpLog
import com.android.systemui.log.dagger.NotificationRenderLog
import com.android.systemui.log.dagger.ShadeLog
import com.android.systemui.log.dagger.ShadeTouchLog
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_ACTIVATED_CHILD
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_ADD
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_CHANGE_POSITION
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_DIMMED
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_EVERYTHING
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_GO_TO_FULL_SHADE
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_GROUP_EXPANSION_CHANGED
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HEADS_UP_APPEAR
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HEADS_UP_CYCLING_IN
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HEADS_UP_CYCLING_OUT
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HEADS_UP_DISAPPEAR
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HEADS_UP_DISAPPEAR_CLICK
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HEADS_UP_OTHER
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HIDE_SENSITIVE
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_REMOVE
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_REMOVE_SWIPED_OUT
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_TOP_PADDING_CHANGED
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_VIEW_RESIZE
import com.google.errorprone.annotations.CompileTimeConstant
import javax.inject.Inject

class NotificationStackScrollLogger
@Inject
constructor(
    @NotificationHeadsUpLog private val buffer: LogBuffer,
    @NotificationRenderLog private val notificationRenderBuffer: LogBuffer,
    @ShadeLog private val shadeLogBuffer: LogBuffer,
    @ShadeTouchLog private val shadeTouchLogBuffer: LogBuffer,
) {
    fun hunAnimationSkipped(entry: String, reason: String) {
        buffer.log(
            TAG,
            INFO,
            {
                str1 = entry
                str2 = reason
            },
            { "heads up animation skipped: key: $str1 reason: $str2" },
        )
    }

    fun hunAnimationEventAdded(entry: String, type: Int) {
        val reason: String
        reason =
            if (type == ANIMATION_TYPE_ADD) {
                "ADD"
            } else if (type == ANIMATION_TYPE_REMOVE) {
                "REMOVE"
            } else if (type == ANIMATION_TYPE_REMOVE_SWIPED_OUT) {
                "REMOVE_SWIPED_OUT"
            } else if (type == ANIMATION_TYPE_TOP_PADDING_CHANGED) {
                "TOP_PADDING_CHANGED"
            } else if (type == ANIMATION_TYPE_ACTIVATED_CHILD) {
                "ACTIVATED_CHILD"
            } else if (type == ANIMATION_TYPE_DIMMED) {
                "DIMMED"
            } else if (type == ANIMATION_TYPE_CHANGE_POSITION) {
                "CHANGE_POSITION"
            } else if (type == ANIMATION_TYPE_GO_TO_FULL_SHADE) {
                "GO_TO_FULL_SHADE"
            } else if (type == ANIMATION_TYPE_HIDE_SENSITIVE) {
                "HIDE_SENSITIVE"
            } else if (type == ANIMATION_TYPE_VIEW_RESIZE) {
                "VIEW_RESIZE"
            } else if (type == ANIMATION_TYPE_GROUP_EXPANSION_CHANGED) {
                "GROUP_EXPANSION_CHANGED"
            } else if (type == ANIMATION_TYPE_HEADS_UP_APPEAR) {
                "HEADS_UP_APPEAR"
            } else if (type == ANIMATION_TYPE_HEADS_UP_DISAPPEAR) {
                "HEADS_UP_DISAPPEAR"
            } else if (type == ANIMATION_TYPE_HEADS_UP_DISAPPEAR_CLICK) {
                "HEADS_UP_DISAPPEAR_CLICK"
            } else if (type == ANIMATION_TYPE_HEADS_UP_OTHER) {
                "HEADS_UP_OTHER"
            } else if (type == ANIMATION_TYPE_EVERYTHING) {
                "EVERYTHING"
            } else if (type == ANIMATION_TYPE_HEADS_UP_CYCLING_OUT) {
                "HEADS_UP_CYCLING_OUT"
            } else if (type == ANIMATION_TYPE_HEADS_UP_CYCLING_IN) {
                "HEADS_UP_CYCLING_IN"
            } else {
                type.toString()
            }
        buffer.log(
            TAG,
            INFO,
            {
                str1 = entry
                str2 = reason
            },
            { "heads up animation added: $str1 with type $str2" },
        )
    }

    fun hunSkippedForUnexpectedState(entry: String, expected: Boolean, actual: Boolean) {
        buffer.log(
            TAG,
            INFO,
            {
                str1 = entry
                bool1 = expected
                bool2 = actual
            },
            {
                "HUN animation skipped for unexpected hun state: " +
                    "key: $str1 expected: $bool1 actual: $bool2"
            },
        )
    }

    fun logShadeDebugEvent(@CompileTimeConstant msg: String) = shadeLogBuffer.log(TAG, DEBUG, msg)

    fun logEmptySpaceClick(
        isBelowLastNotification: Boolean,
        statusBarState: Int,
        touchIsClick: Boolean,
        motionEventDesc: String,
    ) {
        shadeLogBuffer.log(
            TAG,
            DEBUG,
            {
                int1 = statusBarState
                bool1 = touchIsClick
                bool2 = isBelowLastNotification
                str1 = motionEventDesc
            },
            {
                "handleEmptySpaceClick: statusBarState: $int1 isTouchAClick: $bool1 " +
                    "isTouchBelowNotification: $bool2 motionEvent: $str1"
            },
        )
    }

    fun transientNotificationRowTraversalCleaned(entry: String, reason: String) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = entry
                str2 = reason
            },
            { "transientNotificationRowTraversalCleaned: key: $str1 reason: $str2" },
        )
    }

    fun addTransientChildNotificationToChildContainer(childEntry: String, containerEntry: String) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = childEntry
                str2 = containerEntry
            },
            {
                "addTransientChildToContainer from onViewRemovedInternal: childKey: $str1 " +
                    "-- containerKey: $str2"
            },
        )
    }

    fun addTransientChildNotificationToNssl(childEntry: String) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            { str1 = childEntry },
            { "addTransientRowToNssl from onViewRemovedInternal: childKey: $str1" },
        )
    }

    fun addTransientChildNotificationToViewGroup(childEntry: String, container: ViewGroup) {
        notificationRenderBuffer.log(
            TAG,
            ERROR,
            {
                str1 = childEntry
                str2 = container.toString()
            },
            {
                "addTransientRowTo unhandled ViewGroup from onViewRemovedInternal: childKey: $str1 " +
                    "-- ViewGroup: $str2"
            },
        )
    }

    fun addTransientRow(childEntry: String, index: Int) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = childEntry
                int1 = index
            },
            { "addTransientRow to NSSL: childKey: $str1 -- index: $int1" },
        )
    }

    fun removeTransientRow(childEntry: String) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            { str1 = childEntry },
            { "removeTransientRow from NSSL: childKey: $str1" },
        )
    }

    fun logUpdateSensitivenessWithAnimation(
        shouldAnimate: Boolean,
        isSensitive: Boolean,
        isSensitiveContentProtectionActive: Boolean,
        isAnyProfilePublic: Boolean,
    ) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                bool1 = shouldAnimate
                bool2 = isSensitive
                bool3 = isSensitiveContentProtectionActive
                bool4 = isAnyProfilePublic
            },
            {
                "updateSensitivenessWithAnimation from NSSL: shouldAnimate=$bool1 " +
                    "isSensitive(hideSensitive)=$bool2 isSensitiveContentProtectionActive=$bool3 " +
                    "isAnyProfilePublic=$bool4"
            },
        )
    }

    fun logUpdateSensitivenessWithAnimation(animate: Boolean, anyProfilePublicMode: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                bool1 = animate
                bool2 = anyProfilePublicMode
            },
            {
                "updateSensitivenessWithAnimation from NSSL: animate=$bool1 " +
                    "anyProfilePublicMode(hideSensitive)=$bool2"
            },
        )
    }

    fun childHeightUpdated(row: ExpandableNotificationRow, needsAnimation: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = row.key
                bool1 = needsAnimation
            },
            { "childHeightUpdated: childKey: $str1 -- needsAnimation: $bool1" },
        )
    }

    fun setMaxDisplayedNotifications(maxDisplayedNotifications: Int) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            { int1 = maxDisplayedNotifications },
            { "setMaxDisplayedNotifications: $int1" },
        )
    }

    fun logAddSwipedOutView(loggingKey: String, clearAllInProgress: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                bool1 = clearAllInProgress
            },
            { "addSwipedOutView from NSSL: childKey = $str1 -- clearAllInProgress:$bool1" },
        )
    }

    fun logRemoveSwipedOutView(loggingKey: String, clearAllInProgress: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                bool1 = clearAllInProgress
            },
            { "removeSwipedOutView from NSSL: childKey = $str1 -- clearAllInProgress:$bool1" },
        )
    }

    fun logOnChildDismissed(loggingKey: String, clearAllInProgress: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                bool1 = clearAllInProgress
            },
            { "onChildDismissed from NSSL: childKey = $str1 -- clearAllInProgress:$bool1" },
        )
    }

    fun logOnSwipeBegin(loggingKey: String, reason: String, clearAllInProgress: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                str2 = reason
                bool1 = clearAllInProgress
            },
            { "onSwipeBegin from $str2: childKey = $str1 -- clearAllInProgress:$bool1" },
        )
    }

    fun logOnSwipeEnd(loggingKey: String, reason: String, clearAllInProgress: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                str2 = reason
                bool1 = clearAllInProgress
            },
            { "onSwipeEnd from $str2: childKey = $str1 -- clearAllInProgress:$bool1" },
        )
    }

    fun logOnChildNotDismissed(
        loggingKey: String,
        animationCancelled: Boolean,
        viewWasRemoved: Boolean,
    ) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                bool1 = animationCancelled
                bool2 = viewWasRemoved
            },
            {
                "onChildNotDismissed (ERROR) childKey = $str1 " +
                    "-- animationCancelled:$bool1 -- viewWasRemoved:$bool2"
            },
        )
    }

    fun logNsslcInterceptTouchEvent(
        action: Int,
        result: Boolean,
        skipForDragging: Boolean,
        isTouchInGuts: Boolean,
        longPressWantsIt: Boolean,
        expandWantsIt: Boolean,
        lockscreenExpandWantsIt: Boolean,
        scrollWantsIt: Boolean,
        hunWantsIt: Boolean,
        swipeWantsIt: Boolean,
        isUp: Boolean,
        shouldLockscreenExpand: Boolean,
        shouldHeadsUp: Boolean,
        onlyScrollingInThisMotion: Boolean,
        expandingNotification: Boolean,
        expandedInThisMotion: Boolean,
        disallowDismissInThisMotion: Boolean,
        isBeingDragged: Boolean,
        isExpanded: Boolean,
        sceneContainerEnabled: Boolean,
    ) {
        shadeTouchLogBuffer.log(
            TAG,
            DEBUG,
            {
                int1 = action
                bool1 = result
                bool2 = skipForDragging
                bool3 = isTouchInGuts
                bool4 = longPressWantsIt
                bool5 = expandWantsIt
            },
            {
                "NSSLC.interceptTouchEvent.log1: ${MotionEvent.actionToString(int1)}, result=$bool1, skipForDragging=$bool2, isTouchInGuts=$bool3, longPressWantsIt=$bool4, expandWantsIt=$bool5"
            },
        )
        shadeTouchLogBuffer.log(
            TAG,
            DEBUG,
            {
                int1 = lockscreenExpandWantsIt.toInt()
                int2 = scrollWantsIt.toInt()
                bool1 = hunWantsIt
                bool2 = swipeWantsIt
                bool3 = isUp
                bool4 = shouldLockscreenExpand
                bool5 = shouldHeadsUp
            },
            {
                "NSSLC.interceptTouchEvent.log2: lockscreenExpandWantsIt=$int1, scrollWantsIt=$int2, hunWantsIt=$bool1, swipeWantsIt=$bool2, isUp=$bool3, shouldLockscreenExpand=$bool4, shouldHeadsUp=$bool5"
            },
        )
        shadeTouchLogBuffer.log(
            TAG,
            DEBUG,
            {
                int1 = onlyScrollingInThisMotion.toInt()
                int2 = expandingNotification.toInt()
                bool1 = expandedInThisMotion
                bool2 = disallowDismissInThisMotion
                bool3 = isBeingDragged
                bool4 = isExpanded
                bool5 = sceneContainerEnabled
            },
            {
                "NSSLC.interceptTouchEvent.log3: onlyScrollingInThisMotion= $int1, expandingNotification=$int2, expandedInThisMotion=$bool1, disallowDismissInThisMotion=$bool2, isBeingDragged=$bool3, isExpanded=$bool4, sceneContainerEnabled=$bool5"
            },
        )
    }

    fun logNsslShouldRefuseTouchEvent(
        action: Int,
        result: Boolean,
        interactive: Boolean,
        isOutBoundsDown: Boolean,
    ) {
        shadeTouchLogBuffer.log(
            TAG,
            if (result) WARNING else DEBUG,
            {
                int1 = action
                bool1 = result
                bool2 = interactive
                bool3 = isOutBoundsDown
            },
            {
                "NSSL.shouldRefuseTouchEvent: ${MotionEvent.actionToString(int1)}, result=$bool1, interactive=$bool2, isOutBoundsDown=$bool3"
            },
        )
    }

    fun logNsslcTouchEvent(
        action: Int,
        result: Boolean,
        sceneContainerEnabled: Boolean,
        longPressWantsIt: Boolean,
        expandWantsIt: Boolean,
        lockscreenExpandWantsIt: Boolean,
        horizontalSwipeWantsIt: Boolean,
        scrollerWantsIt: Boolean,
        hunWantsIt: Boolean,
        onlyScrollingInThisMotion: Boolean,
        expandingNotification: Boolean,
        lockscreenExpandHandled: Boolean,
        isCancelOrUp: Boolean,
    ) {
        shadeTouchLogBuffer.log(
            TAG,
            DEBUG,
            {
                int1 = action
                bool1 = result
                bool2 = sceneContainerEnabled
                bool3 = longPressWantsIt
                bool4 = expandWantsIt
                bool5 = lockscreenExpandWantsIt
            },
            {
                "NSSLC.touchEvent.log1: ${MotionEvent.actionToString(int1)}, result=$bool1, sceneContainerEnabled=$bool2, longPressWantsIt=$bool3, expandWantsIt=$bool4, lockscreenExpandWantsIt=$bool5"
            },
        )
        shadeTouchLogBuffer.log(
            TAG,
            DEBUG,
            {
                int1 = horizontalSwipeWantsIt.toInt()
                int2 = scrollerWantsIt.toInt()
                bool1 = hunWantsIt
                bool2 = onlyScrollingInThisMotion
                bool3 = expandingNotification
                bool4 = lockscreenExpandHandled
                bool5 = isCancelOrUp
            },
            {
                "NSSLC.touchEvent.log2: horizontalSwipeWantsIt=$int1, scrollerWantsIt=$int2, hunWantsIt=$bool1, onlyScrollingInThisMotion=$bool2, expandingNotification=$bool3, lockscreenExpandHandled=$bool4, isCancelOrUp=$bool5"
            },
        )
    }

    fun logNsslOnInterceptTouchEvent(
        action: Int,
        result: Boolean,
        shouldRefuse: Boolean,
        touchHandlerIntercepted: Boolean,
    ) {
        shadeTouchLogBuffer.log(
            TAG,
            DEBUG,
            {
                int1 = action
                bool1 = result
                bool2 = shouldRefuse
                bool3 = touchHandlerIntercepted
            },
            {
                "NSSL.onInterceptTouchEvent: ${MotionEvent.actionToString(int1)}, result=$bool1, shouldRefuse=$bool2, touchHandlerIntercepted=$bool3"
            },
        )
    }

    companion object {
        private const val TAG = "NotificationStackScroll"

        private fun Boolean.toInt() = if (this) 1 else 0
    }
}
