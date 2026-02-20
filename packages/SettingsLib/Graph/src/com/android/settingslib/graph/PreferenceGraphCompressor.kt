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

package com.android.settingslib.graph

import com.android.settingslib.graph.proto.PreferenceGraphProto
import com.android.settingslib.graph.proto.PreferenceGroupProto
import com.android.settingslib.graph.proto.PreferenceOrGroupProto
import com.android.settingslib.graph.proto.PreferenceProto
import com.android.settingslib.graph.proto.PreferenceScreenProto

/** Utility to shrink and expand [PreferenceGraphProto] by de-duplicating redundant sibling fields. */
object PreferenceGraphCompressor {

    /** Shrinks a proto by removing redundant purposes within the same parent.
     * Note: Use [expand] to restore the original proto file
     */
    fun shrink(proto: PreferenceGraphProto): PreferenceGraphProto {
        val builder = proto.toBuilder()
        for ((key, screen) in proto.screens) {
            builder.putScreens(key, shrinkScreen(screen))
        }
        return builder.build()
    }

    /** Takes a shrunken proto file using [shrink] and return it to its original state. */
    fun expand(proto: PreferenceGraphProto): PreferenceGraphProto {
        val builder = proto.toBuilder()
        for ((key, screen) in proto.screens) {
            builder.putScreens(key, expandScreen(screen))
        }
        return builder.build()
    }

    private fun shrinkScreen(screen: PreferenceScreenProto): PreferenceScreenProto {
        val b = screen.toBuilder()
        if (screen.hasRoot()) b.root = shrinkGroup(screen.root)
        val children = screen.parameterizedScreensList.toMutableList()
        val keyGroups = children.indices.groupBy { children[it].screen?.root?.preference?.key ?: "" }
        for ((key, indices) in keyGroups) {
            if (key.isEmpty() || indices.size < 2) continue
            val purposes = indices.map { children[it].screen?.root?.preference?.purpose ?: 0 }
            if (purposes.all { it != 0 && it == purposes[0] }) {
                for (i in 1 until indices.size) {
                    val idx = indices[i]
                    val ps = children[idx].toBuilder()
                    val sc = ps.screen.toBuilder()
                    val rt = sc.root.toBuilder()
                    rt.preference = rt.preference.toBuilder().clearPurpose().build()
                    children[idx] = ps.setScreen(sc.setRoot(rt)).build()
                }
            }
        }
        for (i in children.indices) {
            val ps = children[i]
            if (ps.hasScreen()) children[i] = ps.toBuilder().setScreen(shrinkScreen(ps.screen)).build()
        }
        return b.clearParameterizedScreens().addAllParameterizedScreens(children).build()
    }

    private fun expandScreen(screen: PreferenceScreenProto): PreferenceScreenProto {
        val b = screen.toBuilder()
        if (screen.hasRoot()) b.root = expandGroup(screen.root)
        val children = screen.parameterizedScreensList.toMutableList()
        val keyGroups = children.indices.groupBy { children[it].screen?.root?.preference?.key ?: "" }
        for ((key, indices) in keyGroups) {
            if (key.isEmpty()) continue
            val sourcePurpose = indices.map { children[it].screen?.root?.preference?.purpose ?: 0 }.firstOrNull { it != 0 } ?: 0
            if (sourcePurpose != 0) {
                for (idx in indices) {
                    val p = children[idx].screen?.root?.preference
                    if (p != null && !p.hasPurpose()) {
                        val ps = children[idx].toBuilder()
                        val sc = ps.screen.toBuilder()
                        val rt = sc.root.toBuilder()
                        rt.preference = rt.preference.toBuilder().setPurpose(sourcePurpose).build()
                        children[idx] = ps.setScreen(sc.setRoot(rt)).build()
                    }
                }
            }
        }
        for (i in children.indices) {
            val ps = children[i]
            if (ps.hasScreen()) children[i] = ps.toBuilder().setScreen(expandScreen(ps.screen)).build()
        }
        return b.clearParameterizedScreens().addAllParameterizedScreens(children).build()
    }

    private fun shrinkGroup(group: PreferenceGroupProto): PreferenceGroupProto {
        val b = group.toBuilder()
        val children = group.preferencesList.toMutableList()
        val keyGroups = children.indices.groupBy { getPref(children[it])?.key ?: "" }
        for ((key, indices) in keyGroups) {
            if (key.isEmpty() || indices.size < 2) continue
            val purposes = indices.map { getPref(children[it])?.purpose ?: 0 }
            if (purposes.all { it != 0 && it == purposes[0] }) {
                for (i in 1 until indices.size) {
                    val idx = indices[i]
                    val p = getPref(children[idx])!!
                    children[idx] = updatePref(children[idx], p.toBuilder().clearPurpose().build())
                }
            }
        }
        for (i in children.indices) {
            if (children[i].hasGroup()) {
                children[i] = children[i].toBuilder().setGroup(shrinkGroup(children[i].group)).build()
            }
        }
        return b.clearPreferences().addAllPreferences(children).build()
    }

    private fun expandGroup(group: PreferenceGroupProto): PreferenceGroupProto {
        val b = group.toBuilder()
        val children = group.preferencesList.toMutableList()
        val keyGroups = children.indices.groupBy { getPref(children[it])?.key ?: "" }
        for ((key, indices) in keyGroups) {
            if (key.isEmpty()) continue
            val sourcePurpose = indices.map { getPref(children[it])?.purpose ?: 0 }.firstOrNull { it != 0 } ?: 0
            if (sourcePurpose != 0) {
                for (idx in indices) {
                    val p = getPref(children[idx])
                    if (p != null && !p.hasPurpose()) {
                        children[idx] = updatePref(children[idx], p.toBuilder().setPurpose(sourcePurpose).build())
                    }
                }
            }
        }
        for (i in children.indices) {
            if (children[i].hasGroup()) {
                children[i] = children[i].toBuilder().setGroup(expandGroup(children[i].group)).build()
            }
        }
        return b.clearPreferences().addAllPreferences(children).build()
    }

    private fun getPref(child: PreferenceOrGroupProto) = if (child.hasPreference()) child.preference else if (child.hasGroup()) child.group.preference else null
    private fun updatePref(child: PreferenceOrGroupProto, p: PreferenceProto) = if (child.hasPreference()) child.toBuilder().setPreference(p).build() else child.toBuilder().setGroup(child.group.toBuilder().setPreference(p)).build()
}
