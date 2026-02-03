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

package com.android.settingslib.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.android.settingslib.widget.BannerMessagePreferenceGroup.Companion.DEFAULT_VISIBLE_PREFERENCES_WHEN_COLLAPSED
import com.android.settingslib.widget.preference.banner.R
import kotlin.math.max
import kotlin.math.min

/**
 * Custom PreferenceGroup that allows expanding and collapsing child [BannerMessagePreference]s.
 *
 * This group will always display the first BannerMessagePreference added to it. If more than one
 * banners are added, the remaining preferences will be hidden and a collapse button will show,
 * displaying the number of collapsed preferences. When expanded, a configurable collapse button
 * will be shown.
 *
 * This group can also have a simple subsection added, which is a titled preference category that
 * also contains banner preferences. This subsection will be shown and collapsed along with the
 * remaining banner preferences added directly to the group.
 */
class BannerMessagePreferenceGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : PreferenceGroup(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {

    private var isExpanded = false

    val handler = Handler(Looper.getMainLooper())
    private var expandPreference: NumberButtonPreference? = null
    private var collapsePreference: SectionButtonPreference? = null
    private var subsectionCategory: PreferenceCategory? = null
    private val childPreferences = mutableListOf<BannerMessagePreference>()
    private var expandKey: String? = null
    private var expandTitle: CharSequence? = null
    private var collapseKey: String? = null
    private var collapseTitle: CharSequence? = null
    private var collapseIcon: Drawable? = null
    // Add dismissed preferences
    private var showDismissedPreferences = false
    private var isDismissedExpanded = false
    private var dismissedPreferences = mutableListOf<BannerMessagePreference>()
    private var expandDismissedPreference: NumberButtonPreference? = null
    private var collapseDismissedPreference: SectionButtonPreference? = null
    private var expandDismissedKey: String? = null
    private var expandDismissedTitle: String? = null
    private var collapseDismissedKey: String? = null
    private var collapseDismissedTitle: String? = null
    private var collapseDismissedIcon: Drawable? = null

    /**
     * Number of preferences to always show at the top, even when collapsed.
     * Defaults to [DEFAULT_VISIBLE_PREFERENCES_WHEN_COLLAPSED].
     */
    var visiblePreferencesWhenCollapsedCount: Int = DEFAULT_VISIBLE_PREFERENCES_WHEN_COLLAPSED
        set(value) {
            field = max(1, value)
            updateVisibilities()
            updateCollapsedItemCount()
        }

    private val collapsiblePreferenceCount
        get() = max(childPreferences.size - visiblePreferencesWhenCollapsedCount, 0) +
                subsectionPreferenceCount

    private val subsectionPreferenceCount
        get() = subsectionCategory?.preferenceCount ?: 0

    var expandContentDescription: Int = 0
        set(value) {
            field = value
            expandPreference?.btnContentDescription = expandContentDescription
        }

    var expandDismissedContentDescription: Int = 0
        set(value) {
            field = value
            expandDismissedPreference?.btnContentDescription = expandDismissedContentDescription
        }

    init {
        isPersistent = false
        layoutResource = R.layout.settingslib_banner_message_preference_group
        initAttributes(context, attrs, defStyleAttr)
    }

    // Add preference to the group. If showDismissedPreferences is true and isDismissed is true, add
    // the preference to the dismissedPreferences list.
    fun addPreference(preference: Preference, isDismissed: Boolean): Boolean {
        if (preference !is BannerMessagePreference) {
            return false
        }
        var result: Boolean
        if (showDismissedPreferences && isDismissed) {
            addDismissedPreference(preference)
            result = super.addPreference(preference)
        } else {
            result = addPreference(preference)
        }
        return result
    }

    override fun addPreference(preference: Preference): Boolean {
        if (preference !is BannerMessagePreference) {
            return false
        }
        if (childPreferences.contains(preference)) {
            return true
        }

        // Determine initial visibility based on expansion state or collapse limit
        val shouldBeVisible = isExpanded ||
                childPreferences.size < visiblePreferencesWhenCollapsedCount

        preference.isVisible = shouldBeVisible

        if (shouldBeVisible) {
            preference.signalExpand()
        }

        val wasAdded = super.addPreference(preference)
        if (wasAdded) {
            childPreferences.add(preference)
            maybeCreateExpandCollapsePreference()
            updateCollapsedItemCount()
            // Do not call updateVisibilities here to preserve existing animations
        }
        return wasAdded
    }

    override fun removePreference(preference: Preference): Boolean {
        if (preference !is BannerMessagePreference) {
            return false
        }

        // Scenario 1: Removing an ACTIVE preference
        if (childPreferences.contains(preference)) {
            val wasRemoved = super.removePreference(preference)
            if (wasRemoved) {
                childPreferences.remove(preference)
                updateCollapsedItemCount()
                updateVisibilities()

                // Move to Dismissed if enabled
                if (showDismissedPreferences) {
                    addDismissedPreference(preference)
                    // Re-add to the group (it will appear at the bottom due to order)
                    super.addPreference(preference)
                }
            }
            return wasRemoved
        }

        // Scenario 2: Removing a DISMISSED preference (Deleting it entirely)
        if (dismissedPreferences.contains(preference)) {
            val wasRemoved = super.removePreference(preference)
            if (wasRemoved) {
                dismissedPreferences.remove(preference)
                // Update the count on the button
                expandDismissedPreference?.let { it.count = dismissedPreferences.size }
                // Check if we need to hide the "Show Dismissed" buttons (if list is empty)
                updateDismissedChildrenVisibility()
            }
            return wasRemoved
        }

        if (subsectionCategory?.removePreference(preference) == true) {
            updateCollapsedItemCount()
            updateVisibilities()
            return true
        }

        return false
    }

    override fun removeAll() {
        subsectionCategory?.removeAll()
        childPreferences.forEach { super.removePreference(it) }
        childPreferences.clear()
        updateCollapsedItemCount()
        updateVisibilities()
        if (showDismissedPreferences) {
            for (i in 0..<dismissedPreferences.size) {
                val child = dismissedPreferences[i]
                super.removePreference(child)
            }
            dismissedPreferences.clear()
            expandDismissedPreference?.let { it.count = dismissedPreferences.size }
            updateDismissedChildrenVisibility()
        }
    }

    private fun updateVisibilities() {
        childPreferences.sortBy { it.order }

        var visibleCount = 0
        childPreferences.forEach { child ->
            val shouldShow = isExpanded || visibleCount < visiblePreferencesWhenCollapsedCount

            if (shouldShow) {
                child.signalExpand()
                visibleCount++
            } else {
                child.signalCollapse()
            }
        }

        expandPreference?.isVisible = !isExpanded && collapsiblePreferenceCount > 0
        collapsePreference?.isVisible = isExpanded && collapsiblePreferenceCount > 0
        subsectionCategory?.isVisible = isExpanded && subsectionPreferenceCount > 0
    }

    private fun toggleExpansion(anchorView: View?) {
        handler.removeCallbacksAndMessages(null)

        if (!isExpanded) {
            isExpanded = true
            expandPreference?.isVisible = false
            collapsePreference?.isVisible = true

            childPreferences.forEach { child ->
                child.isVisible = true
                child.signalExpand()
            }
        } else {
            // Collapsing logic
            val recyclerView = getParentRecyclerView(anchorView)
            childPreferences.sortBy { it.order }

            val keepCount = visiblePreferencesWhenCollapsedCount
            var isFenceVisible = false

            // Scroll to top if the user has scrolled deep into the list
            if (recyclerView != null && childPreferences.isNotEmpty()) {
                val adapter = recyclerView.adapter as? PreferenceGroupAdapter
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager

                if (adapter != null && layoutManager != null) {
                    val lastKeeperIndex = min(keepCount, childPreferences.size) - 1

                    if (lastKeeperIndex >= 0) {
                        val lastKeeperItem = childPreferences[lastKeeperIndex]
                        val fencePos = adapter.getPreferenceAdapterPosition(lastKeeperItem)

                        if (fencePos != RecyclerView.NO_POSITION) {
                            isFenceVisible = layoutManager.findViewByPosition(fencePos) != null

                            if (!isFenceVisible) {
                                val firstItem = childPreferences.firstOrNull()
                                if (firstItem != null) {
                                    val startPos = adapter.getPreferenceAdapterPosition(firstItem)
                                    val scroller = ForcedStartSmoothScroller(context)
                                    scroller.targetPosition = startPos
                                    layoutManager.startSmoothScroll(scroller)
                                }
                            }
                        }
                    }
                }
            }

            val totalVictims = childPreferences.size - keepCount
            val lastAnimatedIndex = keepCount + totalVictims - 1

            val startDelay = if (isFenceVisible) 0L else 20L

            handler.postDelayed({
                childPreferences.forEachIndexed { index, child ->
                    val bannerDelay = index * 75L

                    if (index >= keepCount) {
                        handler.postDelayed({
                            if (index == lastAnimatedIndex) {
                                child.signalCollapse(false) {
                                    isExpanded = false

                                    // Batch remove hidden items from adapter
                                    childPreferences.forEachIndexed { i, p ->
                                        if (i >= keepCount) {
                                            p.isVisible = false
                                        }
                                    }

                                    // Toggle control buttons
                                    if (collapsiblePreferenceCount > 0) {
                                        collapsePreference?.isVisible = false
                                        expandPreference?.isVisible = true
                                    }
                                }
                            } else {
                                child.signalCollapse(false, null)
                            }
                        }, bannerDelay)
                    }
                }
            }, startDelay)
        }
    }

    private fun getParentRecyclerView(view: View?): RecyclerView? {
        var parent = view?.parent
        while (parent != null) {
            if (parent is RecyclerView) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    /** Sets the title of the expand button shown when this group is collapsed. */
    override fun setTitle(title: CharSequence?) {
        expandTitle = title
    }

    /** Sets the title of the collapse button shown when this group is expanded. */
    fun setCollapseTitle(title: CharSequence?) {
        collapseTitle = title
    }

    /**
     * Adds a titled subsection to this group.
     *
     * The subsection displays beneath any directly added BannerMessagePreferences (via
     * [addPreference]. It may only contain [BannerMessagePreference]s. When the group is expanded,
     * the subsection and its contents are shown. They're hidden when the group is collapsed.
     * Only one subsection can be added. Subsequent calls will be no-ops.
     */
    fun addSubsection(subsectionTitle: CharSequence) {
        if (subsectionCategory == null) {
            subsectionCategory = PreferenceCategory(context).apply {
                key = SUBSECTION_KEY
                title = subsectionTitle
                order = SUBSECTION_ORDER
                super.addPreference(this)
                isVisible = false
            }
        }
    }

    /** Sets the title of the subsection, if one has been added. */
    fun setSubsectionTitle(subsectionTitle: CharSequence?) {
        subsectionCategory?.title = subsectionTitle
    }

    /** Removes the subsection, if one has been added. */
    fun removeSubsection() {
      subsectionCategory?.let {
        super.removePreference(it)
        subsectionCategory = null
      }
      updateCollapsedItemCount()
      updateVisibilities()
    }

    /**
     * Adds a preference to this group's subsection. No-ops if the subsection hasn't been created
     * previously.
     *
     * The preference can be removed by calling [removePreference] or [removePreferenceRecursively].
     */
    fun addSubsectionPreference(preference: BannerMessagePreference) {
        if (subsectionCategory?.addPreference(preference) == true) {
            preference.isVisible = isExpanded
            maybeCreateExpandCollapsePreference()
            updateCollapsedItemCount()
            updateVisibilities()
        }
    }

    override fun removePreferenceRecursively(key: CharSequence): Boolean {
        val preference = findPreference<Preference>(key) ?: return false

        if (preference !is BannerMessagePreference) {
            return false
        }

        val isFromActive = childPreferences.contains(preference)
        val isFromDismissed = dismissedPreferences.contains(preference)

        var wasRemoved = super.removePreferenceRecursively(key)
        if (wasRemoved) {
            if (isFromActive) {
                childPreferences.remove(preference)
                updateCollapsedItemCount()
                updateVisibilities()

                if (showDismissedPreferences) {
                    addDismissedPreference(preference)
                    super.addPreference(preference)
                }
            } else if (isFromDismissed) {
                dismissedPreferences.remove(preference)
                expandDismissedPreference?.let { it.count = dismissedPreferences.size }
                updateDismissedChildrenVisibility()
                // Done. It is deleted.
            }
        }
        return wasRemoved
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        maybeCreateExpandCollapsePreference()
        updateVisibilities()

        // Add dismissed preferences
        if (showDismissedPreferences) {
            maybeCreateExpandCollapseDismissedPreference()
            updateDismissedChildrenVisibility()
        }
    }

    /** Add dismissed preferences if showDismissedPreferences is true. */
    private fun addDismissedPreference(preference: BannerMessagePreference) {
        if (!showDismissedPreferences || dismissedPreferences.contains(preference)) {
            return
        }
        preference.order = EXPAND_DISMISSED_ORDER + dismissedPreferences.size
        dismissedPreferences.add(preference)
        maybeCreateExpandCollapseDismissedPreference()
        expandDismissedPreference?.let { it.count = dismissedPreferences.size }
        preference.isVisible = isDismissedExpanded
        if (isDismissedExpanded) {
            preference.signalExpand()
        }
        updateDismissedChildrenVisibility()
    }

    private fun maybeCreateExpandCollapsePreference() {
        if (collapsiblePreferenceCount > 0) {
            if (expandPreference == null) {
                expandPreference = NumberButtonPreference(context).apply {
                    key = expandKey
                    title = expandTitle
                    count = collapsiblePreferenceCount
                    btnContentDescription = expandContentDescription
                    order = EXPAND_ORDER
                    clickListener = View.OnClickListener { v -> toggleExpansion(v) }
                    isVisible = !isExpanded
                    super.addPreference(this)
                }
            }
            if (collapsePreference == null) {
                collapsePreference = SectionButtonPreference(context).apply {
                    key = collapseKey
                    title = collapseTitle
                    icon = collapseIcon
                    order = COLLAPSE_ORDER
                    setOnClickListener { v -> toggleExpansion(v) }
                    isVisible = isExpanded
                    super.addPreference(this)
                }
            }
        }
    }

    private fun maybeCreateExpandCollapseDismissedPreference() {
        if (dismissedPreferences.size > 0) {
            if (expandDismissedPreference == null) {
                expandDismissedPreference =
                    NumberButtonPreference(context).apply {
                        key = expandDismissedKey
                        title = expandDismissedTitle
                        count = dismissedPreferences.size
                        btnContentDescription = expandDismissedContentDescription
                        order = EXPAND_DISMISSED_ORDER
                        clickListener = View.OnClickListener { v -> toggleDismissedExpansion(v) }
                    }
                super.addPreference(expandDismissedPreference!!)
            }
            if (collapseDismissedPreference == null) {
                collapseDismissedPreference =
                    SectionButtonPreference(context).apply {
                        key = collapseDismissedKey
                        title =
                            if (collapseDismissedTitle == null) collapseTitle
                            else collapseDismissedTitle
                        icon =
                            if (collapseDismissedIcon == null) collapseIcon
                            else collapseDismissedIcon
                        order = COLLAPSE_DISMISSED_ORDER
                        setOnClickListener { v -> toggleDismissedExpansion(v) }
                    }
                super.addPreference(collapseDismissedPreference!!)
            }
        }
    }

    private fun updateCollapsedItemCount() {
        expandPreference?.count = collapsiblePreferenceCount
        expandPreference?.let {
            it.count = collapsiblePreferenceCount
        }
    }

    private fun updateDismissedChildrenVisibility() {
        for (i in 0..<dismissedPreferences.size) {
            val child = dismissedPreferences[i]
            child.isVisible = isDismissedExpanded
        }

        expandDismissedPreference?.isVisible = !isDismissedExpanded && dismissedPreferences.size > 0
        collapseDismissedPreference?.isVisible =
            isDismissedExpanded && dismissedPreferences.size > 0
    }

    private fun toggleDismissedExpansion(anchorView: View?) {
        handler.removeCallbacksAndMessages(null)

        // Case 1: Expanding
        if (!isDismissedExpanded) {
            isDismissedExpanded = true
            expandDismissedPreference?.isVisible = false
            collapseDismissedPreference?.isVisible = true
            dismissedPreferences.forEach { child ->
                child.isVisible = true
                child.signalExpand()
            }
            return
        }

        // Case 2: Collapsing (Complex)
        val recyclerView = getParentRecyclerView(anchorView)
        dismissedPreferences.sortBy { it.order }

        // 1. ANCHOR & SCROLL
        // Anchor to the first item so we scroll to the top of the section
        val anchorItem: Preference? = if (collapsePreference?.isVisible == true) {
            collapsePreference
        } else if (expandPreference?.isVisible == true) {
            expandPreference
        } else if (childPreferences.isNotEmpty()) {
            childPreferences.last()
        } else {
            dismissedPreferences.firstOrNull()
        }

        var isFenceVisible = false

        if (recyclerView != null && anchorItem != null) {
            val adapter = recyclerView.adapter as? PreferenceGroupAdapter
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager

            if (adapter != null && layoutManager != null) {
                val fencePos = adapter.getPreferenceAdapterPosition(anchorItem)
                if (fencePos != RecyclerView.NO_POSITION) {
                    val fenceView = layoutManager.findViewByPosition(fencePos)
                    isFenceVisible = fenceView != null &&
                            (layoutManager.getDecoratedTop(fenceView!!) >= 0)

                    if (!isFenceVisible) {
                        val scroller = ForcedStartSmoothScroller(context)
                        scroller.targetPosition = fencePos
                        layoutManager.startSmoothScroll(scroller)
                    }
                }
            }
        }

        // 2. ANIMATION LOOP
        val totalVictims = dismissedPreferences.size
        val lastAnimatedIndex = totalVictims - 1

        // Wait for scroll to potentially start before animating
        val startDelay = if (isFenceVisible) 0L else 200L

        handler.postDelayed({
            dismissedPreferences.forEachIndexed { index, child ->
                val bannerDelay = index * 75L

                handler.postDelayed({
                    if (index == lastAnimatedIndex) {
                        // The last item triggers the state change
                        child.signalCollapse(false) {
                            // ANIMATION DONE: Now we safely update the state
                            isDismissedExpanded = false

                            // Force update visibilities
                            dismissedPreferences.forEach { it.isVisible = false }
                            collapseDismissedPreference?.isVisible = false
                            expandDismissedPreference?.isVisible = true
                        }
                    } else {
                        child.signalCollapse(false, null)
                    }
                }, bannerDelay)
            }
        }, startDelay)
    }

    private fun initAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.BannerMessagePreferenceGroup, defStyleAttr, 0
        ).apply {
            expandKey = getString(R.styleable.BannerMessagePreferenceGroup_expandKey)
            expandTitle = getString(R.styleable.BannerMessagePreferenceGroup_expandTitle)
            collapseKey = getString(R.styleable.BannerMessagePreferenceGroup_collapseKey)
            collapseTitle = getString(R.styleable.BannerMessagePreferenceGroup_collapseTitle)
            collapseIcon = getDrawable(R.styleable.BannerMessagePreferenceGroup_collapseIcon)
            showDismissedPreferences =
                getBoolean(R.styleable.BannerMessagePreferenceGroup_showDismissedPreferences, false)
            expandDismissedKey = getString(R.styleable.BannerMessagePreferenceGroup_expandDismissedKey)
            expandDismissedTitle =
                getString(R.styleable.BannerMessagePreferenceGroup_expandDismissedTitle)
            collapseDismissedKey =
                getString(R.styleable.BannerMessagePreferenceGroup_collapseDismissedKey)
            collapseDismissedTitle =
                getString(R.styleable.BannerMessagePreferenceGroup_collapseDismissedTitle)
            collapseDismissedIcon =
                getDrawable(R.styleable.BannerMessagePreferenceGroup_collapseDismissedIcon)
            recycle()
        }
    }

    companion object {
        private const val SUBSECTION_KEY = "banner_message_preference_group_subsection"
        private const val DEFAULT_VISIBLE_PREFERENCES_WHEN_COLLAPSED = 2

        // Arbitrary large order numbers for the three preferences
        // needed to make sure any Banners are added above them
        private const val SUBSECTION_ORDER = 999
        private const val EXPAND_ORDER = 1000
        private const val COLLAPSE_ORDER = 2000
        private const val EXPAND_DISMISSED_ORDER = 10000
        private const val COLLAPSE_DISMISSED_ORDER = 20000
    }
}

/**
 * A LinearSmoothScroller that strictly enforces SNAP_TO_START and calculates
 * exact scroll offsets, ensuring the target view aligns precisely with the top container padding.
 */
private class ForcedStartSmoothScroller(context: Context) : LinearSmoothScroller(context) {

    private val interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    override fun getVerticalSnapPreference(): Int {
        return SNAP_TO_START
    }

    override fun calculateTimeForScrolling(dx: Int): Int {
        return 300
    }

    override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
        val layoutManager = layoutManager ?: return 0
        val params = view.layoutParams as RecyclerView.LayoutParams
        val viewTop = layoutManager.getDecoratedTop(view) - params.topMargin
        val containerTop = layoutManager.paddingTop
        return viewTop - containerTop
    }

    override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
        val dy = calculateDyToMakeVisible(targetView, SNAP_TO_START)
        if (Math.abs(dy) > 0) {
            action.update(0, dy, 400, interpolator)
        }
    }
}