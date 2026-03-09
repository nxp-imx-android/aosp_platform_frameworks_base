/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.hierarchy.experimental.testsplit

import android.app.ActivityOptions
import android.app.WindowConfiguration
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.widget.FrameLayout
import android.widget.TextView
import android.window.TaskCreationParams
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.core.graphics.toRect
import com.android.wm.shell.common.split.SplitScreenUtils
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.containers.ViewOverlayContainer
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.RootContainerProperties
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.hierarchy.utils.HierarchyUtils
import com.android.wm.shell.transition.AnimationPlan
import java.io.PrintWriter

/**
 * a basic Split-mode to prototype basic configurations of a split like 1x1, 2x1.
 */
class SplitMode(
    private val context: Context,
    private val hierarchy: ContainerHierarchy,
) : Mode {
    private val rootsPerDisplay = mutableMapOf<Int, Container>()
    private lateinit var splitRoot: Container
    private val nodeRoots = mutableMapOf<Container, MutableList<Container>>()
    private val overlays = mutableMapOf<WindowContainerToken, ViewOverlayContainer>()
    private var isLeftRightSplit: Boolean = false
    private var isDividerDragging = false
    private var buttonLaunch = false
    private val EXAMPLE_APPS = mutableListOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
    )

    val TAG = SplitMode::class.simpleName

    private lateinit var verticalDividerWindowManager: SplitWindowManager
    private lateinit var horizontalDividerWindowManager: SplitWindowManager

    private var isVerticalDividerInitialized = false
    private var isHorizontalDividerInitialized = false
    private var activeContainerCount = 0

    /** @see Mode.prepareForDisplay */
    override fun prepareForDisplay(updateContext: Mode.UpdateContext, display: Container) {
        Log.d(TAG, "prepareForDisplay: $display")
        val displayId = display.props<DisplayContainerProperties>().displayId
        // Create a top level root task
        var params = TaskCreationParams.Builder()
            .setName("SplitMode_${displayId}")
            .setDisplayId(displayId)
            .setWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN)
            .build()
        splitRoot = hierarchy.update.createTask(params, this)

        rootsPerDisplay[displayId] = splitRoot
        nodeRoots[splitRoot] = mutableListOf()

        // Determine orientation
        updateLeftRightSplit(display.props())

        verticalDividerWindowManager =
            SplitWindowManager(
                "VerticalDivider",
                context,
                context.resources.configuration
            ) { builder ->
                try {
                    builder.setParent(splitRoot.leash)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set parent for vertical divider", e)
                }
            }

        horizontalDividerWindowManager =
            SplitWindowManager(
                "HorizontalDivider",
                context,
                context.resources.configuration
            ) { builder ->
                try {
                    builder.setParent(splitRoot.leash)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set parent for horizontal divider", e)
                }
            }

        // Create child node roots for leaf tasks/apps to go under
        val nodeRoots = nodeRoots[splitRoot]
        val wct = WindowContainerTransaction()
        for (i in 0 until 3) {
            params = TaskCreationParams.Builder()
                .setName("SplitMode_NodeRoot_${i}_display_${displayId}")
                .setDisplayId(displayId)
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW)
                .build()
            val nodeRoot = hierarchy.update.createTask(params)
            Log.d(TAG, "nodeRoot created: $nodeRoot ")
            nodeRoots?.add(nodeRoot)
            wct.reparent(nodeRoot.token, splitRoot.token, true)
        }

        // Set second nodeRoot as launchAdjacentRoot
        wct.setAdjacentRoots(nodeRoots!![0].token, nodeRoots[1].token, nodeRoots[2].token)
        wct.setLaunchAdjacentFlagRoot(nodeRoots[1].token)
        hierarchy.update.wm("add child container", TRANSIT_CHANGE, wct)

        setupOverlay(updateContext, displayId)
    }

    private fun updateLeftRightSplit(display: DisplayContainerProperties) {
        isLeftRightSplit = SplitScreenUtils.isLeftRightSplit(
            true /*allowLeftRightSplitInPortrait*/,
            display.config, display.displayId
        )
    }

    private fun setupOverlay(updateContext: Mode.UpdateContext, displayId: Int) {
        // Create an overlay that can launch tasks into this root
        val overlay = ViewOverlayContainer(
            WindowContainerToken.createProxy(":split_mode_overlay"),
            { context, _ ->
                FrameLayout(context)
            },
            overrideWidth = 800,
            overrideHeight = 200,
        )
        overlay.parent = splitRoot
        overlay.initialize(context)

        val newBounds = RectF(overlay.props.bounds)
        newBounds.offset(100f, 100f)

        val tx = updateContext.preTransitionTx!!
        overlay.surface
            .setBounds(tx, newBounds)
            .setLayer(tx, Integer.MAX_VALUE)
            .show(tx)
        overlays[splitRoot.token] = overlay

        // Create two buttons in the root view that will add and remove sub containers
        val button1 = FrameLayout(context)
        button1.setLayoutParams(FrameLayout.LayoutParams(100, MATCH_PARENT))
        button1.setBackgroundColor(Color.argb(128, 0, 255, 0))
        val text1 = TextView(context).apply {
            text = "1x0"
            gravity = Gravity.CENTER
        }
        button1.addView(text1)
        button1.setOnClickListener {
            buttonLaunch = true
            removeAllContainers(displayId)
            addContainersAndLaunch(1 /*containerCount*/)
        }
        val button2 = FrameLayout(context)
        button2.setBackgroundColor(Color.argb(128, 100, 128, 0))
        button2.setLayoutParams(FrameLayout.LayoutParams(100, MATCH_PARENT).apply {
            leftMargin = 100
        })
        val text2 = TextView(context).apply {
            text = "1x1"
            gravity = Gravity.CENTER
        }
        button2.addView(text2)
        button2.setOnClickListener {
            buttonLaunch = true
            removeAllContainers(displayId)
            addContainersAndLaunch(2)
        }

        val button3 = FrameLayout(context)
        button3.setBackgroundColor(Color.argb(128, 160, 0, 100))
        button3.setLayoutParams(FrameLayout.LayoutParams(100, MATCH_PARENT).apply {
            leftMargin = 200
        })
        val text3 = TextView(context).apply {
            text = "2x1"
            gravity = Gravity.CENTER
        }
        button3.addView(text3)
        button3.setOnClickListener {
            buttonLaunch = true
            removeAllContainers(displayId)
            addContainersAndLaunch(3)
        }

        val button4 = FrameLayout(context)
        button4.setBackgroundColor(Color.argb(128, 255, 0, 0))
        button4.setLayoutParams(FrameLayout.LayoutParams(100, MATCH_PARENT).apply {
            leftMargin = 300
        })
        val text4 = TextView(context).apply {
            text = "CLEAR"
            gravity = Gravity.CENTER
        }
        button4.addView(text4)
        button4.setOnClickListener {
            buttonLaunch = true
            removeAllContainers(displayId)
        }

        val parent = overlay.rootView as ViewGroup
        parent.addView(button1)
        parent.addView(button2)
        parent.addView(button3)
        parent.addView(button4)
    }

    private fun addContainersAndLaunch(containerCount: Int) {
        // Collect the node roots to add sample apps into
        val nodeRootContainers = nodeRoots[splitRoot]
        val tokenList = ArrayList<WindowContainerToken>();
        checkNotNull(nodeRootContainers)
        for (i in 0 until containerCount) {
            tokenList.add(nodeRootContainers[i].token)
        }
        Log.d(TAG, "launching ${tokenList.size} apps")
        val wct = WindowContainerTransaction()

        val bounds = splitRoot.props.bounds

        updateDividers(containerCount, bounds.toRect())

        layoutChildren(splitRoot.props.bounds, tokenList, wct)
        hierarchy.update.wm("resize split node roots", TRANSIT_CHANGE, wct)
        launchExampleAppIntoContainer(tokenList)
    }

    private fun updateDividers(containerCount: Int, bounds: Rect) {
        val showVertical = containerCount >= 3 || (containerCount == 2 && isLeftRightSplit)
        val showHorizontal = containerCount >= 3 || (containerCount == 2 && !isLeftRightSplit)
        Log.d(
            TAG,
            "updateDividers count=$containerCount bound=$bounds showVert=$showVertical showHoriz=$showHorizontal"
        )

        try {
            verticalDividerWindowManager.ensureVisible(
                this, bounds, DividerView.TYPE_VERTICAL, true, showVertical
            )
            isVerticalDividerInitialized = showVertical
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update vertical divider", e)
        }

        try {
            horizontalDividerWindowManager.ensureVisible(
                this, bounds, DividerView.TYPE_HORIZONTAL, false, showHorizontal
            )
            isHorizontalDividerInitialized = showHorizontal
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update horizontal divider", e)
        }
    }

    private fun launchExampleAppIntoContainer(tokenList: List<WindowContainerToken>) {
        for (i in tokenList.indices) {
            val intent = EXAMPLE_APPS[i]
            val opts = ActivityOptions.makeBasic().apply {
                setLaunchRootTask(tokenList.get(i))
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent, opts.toBundle())
        }
    }

    fun onDividerDragging(dividerType: Int, offset: Int, finished: Boolean) {
        Log.d(TAG, "onDividerDragging type=$dividerType offset=$offset finished=$finished")
        isDividerDragging = !finished
        val bounds = splitRoot.props.bounds

        if (dividerType == DividerView.TYPE_VERTICAL) {
            val max = bounds.width().toFloat()
            verticalDividerWindowManager.clampPosition(0f, max)
        } else {
            val max = bounds.height().toFloat()
            horizontalDividerWindowManager.clampPosition(0f, max)
        }

        val wct = WindowContainerTransaction()
        val nodeRootContainers = nodeRoots[splitRoot]
        if (nodeRootContainers != null && activeContainerCount > 0) {
            val tokens = nodeRootContainers.take(activeContainerCount).map { it.token }
            layoutChildren(bounds, tokens, wct, finished)
        }

        if (finished) {
            hierarchy.update.wm("divider drag finish", TRANSIT_CHANGE, wct)
        }
    }

    /**
     * Be sure to have called updateDivider before this, need a valid divider position before
     * laying out children.
     */
    private fun layoutChildren(
        rootBounds: RectF,
        childRoots: List<WindowContainerToken>,
        wct: WindowContainerTransaction,
        updateApps: Boolean = true
    ) {
        activeContainerCount = childRoots.size
        Log.d(TAG, "layoutChildren $childRoots $activeContainerCount")
        Log.d(TAG, "$rootBounds isLeftRightSplit $isLeftRightSplit")
        // Layout all the children
        val childBounds = Array(childRoots.size) {
            RectF()
        }

        if (childRoots.isEmpty())
            return
        else if (childRoots.size == 1) {
            // 1x0: full-width, full length
            wct.setBounds(childRoots[0], rootBounds.toRect())
        } else if (childRoots.size == 2) {
            layout1x1(rootBounds, childRoots, childBounds, wct, updateApps)
        } else if (childRoots.size == 3) {
            layout2x1(rootBounds, childRoots, childBounds, wct, updateApps)
        }
    }

    private fun removeAllContainers(displayId: Int) {
        val root = rootsPerDisplay.getValue(displayId)
        val nodeRoots = nodeRoots.getValue(root)
        if (nodeRoots.isEmpty()) {
            Log.d(TAG, "No roots to remove")
            return
        }

        // Reparent all children outside of node containers to bottom of display
        val wct = WindowContainerTransaction()
        for (nodeRoot in nodeRoots) {
            wct.reparentTasks(
                nodeRoot.token,
                /*newParent*/null,
                /*windowingMode*/null,
                /*activityTypes*/null,
                /*onTop*/false
            )
            for (leafTask in nodeRoot.children) {
                Log.d(TAG, "reparenting ${leafTask.name}")
            }
        }

        cleanupDivider()

        // Layout again
        wct.reorder(root.token, false, true)
        hierarchy.update.wm("remove child container", TRANSIT_TO_BACK, wct)
        activeContainerCount = 0
    }

    override fun containersChanged(
        updateContext: Mode.UpdateContext,
        enteringContainers: List<Container>,
        changedContainers: List<Container>,
        globalStateChanged: Boolean,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan?
    ) {
        val modeContainers = (enteringContainers + changedContainers)
            .groupBy { HierarchyUtils.getModeContainer(it) }
            .keys
            .filterNotNull()
        if (modeContainers.isEmpty()) {
            // Only global state changes (to update in the future)
            return
        }

        for (modeContainer in modeContainers) {
            var enteringContainersInRoot =
                enteringContainers.filter { HierarchyUtils.getModeContainer(it) == modeContainer }
            var changedContainersInRoot =
                changedContainers.filter { HierarchyUtils.getModeContainer(it) == modeContainer }
            val splitRootChanges = snapshot.getChanges(modeContainer)
            val nodeRootChanges = nodeRoots[modeContainer]
                ?.firstOrNull { !snapshot.getChanges(it).isEmpty }

            if (splitRootChanges.isEmpty && nodeRootChanges == null) {
                Log.d(TAG, "no valid changes")
                return
            }
            Log.d(
                TAG, "containersChanged container: ${modeContainer.name} " +
                        "changes: $splitRootChanges " +// buttonLaunch: $buttonLaunch " +
                        "odeRootChanges: $nodeRootChanges"
            )

            if (enteringContainersInRoot.isNotEmpty() && !buttonLaunch) {
                // We'll need to modify this in the future for different ways split is entered
                val currentNodeRoot: Container?
                var addedNodeRoot: Container? = null
                for (addedContainer in enteringContainersInRoot) {
                    Log.d(
                        TAG,
                        "addedContainer: ${addedContainer.name} parent: ${addedContainer.parent?.name}"
                    )
                    if (addedContainer.parent != null) {
                        addedNodeRoot = addedContainer.parent
                        break
                    }
                }
                currentNodeRoot = nodeRoots[splitRoot]
                    ?.find { it.children.isNotEmpty() && it != addedNodeRoot }
                Log.d(TAG, "currentNodeRoot: $currentNodeRoot addedNodeRoot: $addedNodeRoot")
                if (currentNodeRoot != null && addedNodeRoot != null && activeContainerCount == 1) {
                    Log.d(TAG, "assuming launch adjacent")
                    // Def launch adjacent
                    val wct = WindowContainerTransaction()
                    updateDividers(2, splitRoot.props.bounds.toRect())
                    layoutChildren(
                        splitRoot.props.bounds,
                        listOf(currentNodeRoot.token, addedNodeRoot.token),
                        wct
                    )
                    hierarchy.update.wm("resize split node roots launchAdj", TRANSIT_CHANGE, wct)
                }
                buttonLaunch = false
            }
            for (changedContainer in changedContainersInRoot) {
                Log.d(TAG, "changedContainer: ${changedContainer.name}")
            }
        }
    }

    /** @see Mode.cleanupForDisplay */
    override fun cleanupForDisplay(updateContext: Mode.UpdateContext, display: Container) {
        val displayId = display.props<DisplayContainerProperties>().displayId
        val root = rootsPerDisplay.remove(displayId)
        hierarchy.update.removeTask(root!!)

        val overlay = overlays.remove(root.token)
        if (overlay != null) {
            val tx = updateContext.preTransitionTx!!
            overlay.release(tx)
            overlay.parent = null
        }

        cleanupDivider()
    }

    private fun cleanupDivider() {
        if (isVerticalDividerInitialized) {
            verticalDividerWindowManager.release()
            isVerticalDividerInitialized = false
        }
        if (isHorizontalDividerInitialized) {
            horizontalDividerWindowManager.release()
            isHorizontalDividerInitialized = false
        }
    }

    /** @see Mode.requestUpdateForDisplayChange */
    override fun requestUpdateForDisplayChange(
        directlyAssignedContainer: Container,
        curDisplayProps: DisplayContainerProperties,
        newDisplayProps: DisplayContainerProperties,
        wct: WindowContainerTransaction
    ) {
        Log.d(TAG, "requestUpdateForDisplayChange: $curDisplayProps -> $newDisplayProps")
        updateLeftRightSplit(newDisplayProps)
        val root = rootsPerDisplay.getValue(curDisplayProps.displayId)
        val childRoots = nodeRoots.getValue(root)
            .filter { it.children.isNotEmpty() }
            .map { it.token }
        if (childRoots.isEmpty()) {
            Log.d(TAG, "No roots to remove")
            return
        }

        updateDividers(childRoots.size, newDisplayProps.bounds.toRect())
        // Force update of divider position on rotation if needed
        // For prototype, just re-layout.
        layoutChildren(newDisplayProps.bounds, childRoots, wct)
    }

    /** @see Mode.requestEnterMode */
    override fun requestEnterMode(
        task: Container,
        request: Mode.EnterRequestContext,
        wct: WindowContainerTransaction
    ): Boolean {
        Log.d(TAG, "requestEnter: $task")
        return true
    }

    /** @see Mode.requestEnterMode */
    override fun onShellCommand(displayId: Int, args: MutableList<String>, pw: PrintWriter) {
        val action = args.removeFirst()
        when (action.lowercase()) {

            "launch_new_activity" -> {
                addContainersAndLaunch(1)
            }

            "move_focused_task" -> {
                val rootProps = hierarchy.root.props<RootContainerProperties>()
                val taskId = rootProps.focusState.globallyFocusedTaskId
                val task = hierarchy.getTask(taskId)
                if (task == null) {
                    pw.println("No task with id=$taskId")
                    return
                }
                if (HierarchyUtils.getMode(task) == this) {
                    pw.println("Task id=$taskId is already associated with ${getId()}")
                    return
                }

                val wct = WindowContainerTransaction()
                requestEnterMode(task, Mode.EnterRequestContext(displayId), wct)
                hierarchy.update.wm(
                    "Moving focused task $taskId into ${getId()}",
                    TRANSIT_CHANGE,
                    wct,
                )
            }

            else -> {
                pw.println("Unknown action: $action")
            }
        }
    }

    private fun layout1x1(
        rootBounds: RectF,
        childRoots: List<WindowContainerToken>,
        childBounds: Array<RectF>,
        wct: WindowContainerTransaction,
        updateApps: Boolean = true
    ) {
        val dividerPosition = if (isLeftRightSplit)
            verticalDividerWindowManager.dividerPosition.toInt() else
            horizontalDividerWindowManager.dividerPosition.toInt()
        Log.d(TAG, "layout 1x1 dividerPos: $dividerPosition")
        if (isLeftRightSplit) {
            SplitWindowManager.splitOneVertical(rootBounds.toRect(), dividerPosition, childBounds)
        } else {
            SplitWindowManager.splitOneHorizontal(rootBounds.toRect(), dividerPosition, childBounds)
        }
        applyLayoutToChildren(childRoots, childBounds, wct, updateApps)
    }

    private fun layout2x1(
        rootBounds: RectF,
        childRoots: List<WindowContainerToken>,
        childBounds: Array<RectF>,
        wct: WindowContainerTransaction,
        updateApps: Boolean = true
    ) {
        val horizontalPos = horizontalDividerWindowManager.getDividerPosition().toInt()
        val verticalPos = verticalDividerWindowManager.getDividerPosition().toInt()
        val boundsRect = rootBounds.toRect()

        if (isLeftRightSplit) {
            SplitWindowManager.splitTwoVerticalOneHorizontal(
                boundsRect, horizontalPos, verticalPos, childBounds
            )
        } else {
            SplitWindowManager.splitTwoHorizontalOneVertical(
                boundsRect, horizontalPos, verticalPos, childBounds
            )
        }
        applyLayoutToChildren(childRoots, childBounds, wct, updateApps)
    }

    private fun applyLayoutToChildren(
        childRoots: List<WindowContainerToken>,
        childBounds: Array<RectF>,
        wct: WindowContainerTransaction,
        updateApps: Boolean
    ) {
        if (updateApps) {
            for ((i, container) in childRoots.withIndex()) {
                if (i < childBounds.size) {
                    wct.setBounds(container, childBounds[i].toRect())
                }
            }
        }

        // Update Dividers
        val density = context.resources.displayMetrics.density
        val dividerWindowWidth = (DividerView.DIVIDER_SIZE_DP * density).toInt()
        val verticalPos = verticalDividerWindowManager.getDividerPosition().toInt()
        val horizontalPos = horizontalDividerWindowManager.getDividerPosition().toInt()

        if (isLeftRightSplit) {
            if (isVerticalDividerInitialized) {
                verticalDividerWindowManager.updateLayout(true /*isLeftRight */, null)
            }
            if (isHorizontalDividerInitialized) {
                // Horizontal divider is ONLY for LEFT half in 2x1.
                val crop =
                    if (activeContainerCount == 3)
                        Rect(0, 0, verticalPos, dividerWindowWidth)
                    else null
                horizontalDividerWindowManager.updateLayout(false /*isLeftRight */, crop)
            }
        } else {
            if (isVerticalDividerInitialized) {
                // Vertical divider is ONLY for TOP half in 2x1.
                val crop =
                    if (activeContainerCount == 3)
                        Rect(0, 0, dividerWindowWidth, horizontalPos)
                    else null
                verticalDividerWindowManager.updateLayout(true /*isLeftRight */, crop)
            }
            if (isHorizontalDividerInitialized) {
                horizontalDividerWindowManager.updateLayout(false /*isLeftRight */, null)
            }
        }
    }

}