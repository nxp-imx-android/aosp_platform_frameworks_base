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
package com.android.wm.shell.hierarchy.utils

import android.window.WindowContainerToken
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.updates.EMPTY_SNAPSHOT
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_MODES

/**
 * Utility functions to support debugging the hierarchy.
 */
class HierarchyDebugUtils {
    companion object {
        val UNDERLINE = "\u001b[4m"
        val RED = "\u001b[31m"
        val GREEN = "\u001b[32m"
        val YELLOW = "\u001b[33m"
        val PURPLE = "\u001b[35m"
        val WHITE = "\u001b[37m"
        val NONE = "\u001b[0m"

        /**
         * Dumps the hierarchy to protolog.
         */
        fun dumpHierarchy(
            hierarchy: ContainerHierarchy,
            snapshot: HierarchySnapshot
        ) {
            ProtoLog.v(WM_SHELL_MODES, "=======================================================")
            @SuppressWarnings("ProtoLogNoContext")
            ProtoLog.v(WM_SHELL_MODES, "%s", dumpToString(hierarchy.root, "", snapshot, WHITE))
            ProtoLog.v(WM_SHELL_MODES, "=======================================================")
        }

        /**
         * Returns a string representation of a hierarchy rooted at the given container.
         */
        fun dumpToString(
            container: Container,
            prefix: String = "",
            snapshot: HierarchySnapshot = EMPTY_SNAPSHOT,
            withColor: String? = null,
        ): String {
            val changeFlags = snapshot.getChanges(container)
            val oldState =
                if (!changeFlags.isEmpty) {
                    snapshot.snapshots.getValue(container)
                } else null
            val startColorTag = withColor ?: ""
            val endColorTag = if (withColor != null) NONE else ""
            var output = ""

            // Dump basic info
            val startModeColorTag = if (withColor != null) PURPLE else ""
            val endModeColorTag = if (withColor != null) NONE else ""
            val modeStr = if (container.mode != null)
                " ${startModeColorTag}${container.mode!!.getId()}${endModeColorTag}" else ""
            val changeFlagStr = if (!changeFlags.isEmpty) " changes=$changeFlags" else ""
            output += "${prefix}${startColorTag}${container.name}${endColorTag}" +
                    "${modeStr}${changeFlagStr}\n"

            // Dump current & previous window oinfo
            if (oldState != null) {
                output += "${prefix}$YELLOW(from)$NONE ${oldState.props}\n"
                output += "${prefix}$YELLOW  (to)$NONE ${container.props}\n"
            } else {
                output += "${prefix}${container.props}\n"
            }

            // Dump current surface info
            output += "${prefix}${container.surface}\n"

            // Identify changed children
            val reorderedChildren = mutableListOf<Container>()
            val removedChildren = mutableListOf<Container>()
            val addedChildren = mutableListOf<Container>()
            if (oldState != null) {
                if (!oldState.children.isEmpty()) {
                    removedChildren.addAll(oldState.children - container.children.toSet())
                    addedChildren.addAll(container.children - oldState.children.toSet())
                    for (child in oldState.children) {
                        if (child in container.children) {
                            if (container.children.indexOf(child)
                                != oldState.children.indexOf(child)
                            ) {
                                reorderedChildren.add(child)
                            }
                        }
                    }
                }
            }

            // Dump the current children
            if (container.children.isNotEmpty()) {
                val innerPrefix = "${prefix}| "
                val allChildren = removedChildren + container.children
                for ((index, child) in allChildren.withIndex()) {
                    if (index > 0) {
                        output += "${prefix}| -\n"
                    }
                    var withColor = WHITE
                    if (child in removedChildren) {
                        withColor = RED
                    } else if (child in reorderedChildren) {
                        withColor = YELLOW
                    } else if (child in addedChildren) {
                        withColor = GREEN
                    }
                    output += dumpToString(child, innerPrefix, snapshot, withColor)
                    output += "\n"
                }
            }
            return output.trimEnd()
        }

        /**
         * Returns a string representation for a token.
         */
        fun tokenToString(token: WindowContainerToken): String {
            val windowTokenStr = token.toString()
                .removePrefix("WCT{android.os.BinderProxy@")
                .removePrefix("WCT{android.os.Binder@")
                .removeSuffix("}")
            return "Token=${windowTokenStr}"
        }
    }
}
