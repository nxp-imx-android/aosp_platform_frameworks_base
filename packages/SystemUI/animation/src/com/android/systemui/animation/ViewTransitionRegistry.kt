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

package com.android.systemui.animation

import android.os.Trace
import android.view.View
import java.lang.ref.WeakReference

/**
 * A registry to temporarily store the view being transitioned into a Dialog (using
 * [DialogTransitionAnimator]) or an Activity (using [ActivityTransitionAnimator])
 */
class ViewTransitionRegistry : IViewTransitionRegistry {

    /**
     * A map of a unique token to a WeakReference of the View being transitioned. WeakReference
     * ensures that Views are garbage collected whenever they become eligible and avoid any
     * memory leaks
     */
    private val registry by lazy { mutableMapOf<ViewTransitionToken, WeakReference<View>>() }

    /**
     * A [View.OnAttachStateChangeListener] to be attached to all views stored in the registry to
     * ensure that views (and their corresponding entry) is automatically removed when the view is
     * detached from the Window
     */
    private val listener by lazy {
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                // empty
            }

            override fun onViewDetachedFromWindow(view: View) {
                getViewToken(view)?.let { token -> unregister(token) }
            }
        }
    }

    /**
     * Creates an entry of a unique "token" mapped to "transitioning view" in the registry
     *
     * @param token unique token associated with the transitioning view
     * @param view view undergoing transitions
     */
    override fun register(token: ViewTransitionToken, view: View) {
        // token embedded as a view tag enables to use a single listener for all views
        view.setTag(R.id.tag_view_transition_token, token)
        view.addOnAttachStateChangeListener(listener)
        registry[token] = WeakReference(view)
        onRegistryUpdate()
    }

    /**
     * Removes the entry associated with the unique "token" in the registry
     *
     * @param token unique token associated with the transitioning view
     */
    override fun unregister(token: ViewTransitionToken) {
        registry.remove(token)?.let {
            it.get()?.let { view ->
                view.removeOnAttachStateChangeListener(listener)
                view.setTag(R.id.tag_view_transition_token, null)
            }
            it.clear()
            onRegistryUpdate()
        }
    }

    /**
     * Access a view from registry using unique "token" associated with it
     * WARNING - this returns a StrongReference to the View stored in the registry
     */
    override fun getView(token: ViewTransitionToken): View? {
        return registry[token]?.get()
    }

    /**
     * Return token mapped to the [view], if it is present in the registry
     *
     * @param view the transitioning view whose token we are requesting
     * @return token associated with the [view] if present, else null
     */
    override fun getViewToken(view: View): ViewTransitionToken? {
        return (view.getTag(R.id.tag_view_transition_token) as? ViewTransitionToken)?.let { token ->
            getView(token)?.let { token }
        }
    }

    /** Event call to run on registry update (on both [register] and [unregister]) */
    override fun onRegistryUpdate() {
        emitCountForTrace()
    }

    /**
     * Utility function to emit number of non-null views in the registry whenever the registry is
     * updated (via [register] or [unregister])
     */
    private fun emitCountForTrace() {
        Trace.setCounter("transition_registry_view_count", registry.count().toLong())
    }

    companion object {
        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ViewTransitionRegistry() }
    }
}
