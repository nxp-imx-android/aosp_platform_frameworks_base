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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.IBinder
import android.service.selectiontoolbar.SelectionToolbarRenderService.TransferTouchListener
import android.util.Log
import android.view.MotionEvent
import android.widget.LinearLayout
import java.io.PrintWriter

/**
 * This class is the root view for the selection toolbar. It is responsible for detecting the click
 * on the item and to also transfer input focus to the application.
 *
 * @hide
 */
@SuppressLint("ViewConstructor")
class FloatingToolbarRoot(
    context: Context?,
    private val mTargetInputToken: IBinder?,
    transferTouchListener: TransferTouchListener,
) : LinearLayout(context) {
    private val mTransferTouchListener: TransferTouchListener
    private val mContentRect = Rect()

    private var mLastDownX = -1
    private var mLastDownY = -1

    init {
        mTransferTouchListener = transferTouchListener
        setFocusable(false)
    }

    /** Sets the Rect that shows the selection toolbar content. */
    fun setContentRect(contentRect: Rect) {
        mContentRect.set(contentRect)
    }

    /** Sets the Rect that shows the selection toolbar content to empty. */
    fun setContentRectEmpty() {
        mContentRect.setEmpty()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mLastDownX = event.getX().toInt()
            mLastDownY = event.getY().toInt()
            if (DEBUG) {
                Log.d(TAG, "downX=" + mLastDownX + " downY=" + mLastDownY)
            }
            // TODO(b/215497659): Check FLAG_WINDOW_IS_PARTIALLY_OBSCURED
            if (!mContentRect.contains(mLastDownX, mLastDownY)) {
                if (DEBUG) {
                    Log.d(TAG, "Transfer touch focus to application.")
                }
                mTransferTouchListener.onTransferTouch(
                    getViewRootImpl().getInputToken(),
                    mTargetInputToken,
                )
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /** Dumps information about this class. */
    fun dump(prefix: String?, pw: PrintWriter) {
        pw.print(prefix)
        pw.println("FloatingToolbarRoot:")
        pw.print(prefix + "  ")
        pw.print("last down X: ")
        pw.println(mLastDownX)
        pw.print(prefix + "  ")
        pw.print("last down Y: ")
        pw.println(mLastDownY)
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "FloatingToolbarRoot"
    }
}
