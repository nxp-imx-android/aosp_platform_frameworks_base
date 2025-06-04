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

package com.android.settingslib.spa.system.restricted

import android.Manifest.permission.INTERACT_ACROSS_USERS
import android.Manifest.permission.MANAGE_USERS
import android.content.Context
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.os.bundleOf
import com.android.settingslib.spa.restricted.NoRestricted
import com.android.settingslib.spa.restricted.RestrictedMode
import com.android.settingslib.spa.restricted.RestrictedRepository
import com.android.settingslib.spa.restricted.Restrictions

/**
 * To use this, please register in [com.android.settingslib.spa.framework.common.SpaEnvironment].
 *
 * Would either need [MANAGE_USERS] or [INTERACT_ACROSS_USERS] permission to call, otherwise always
 * no restricted.
 */
class SystemRestrictedRepository(private val context: Context) : RestrictedRepository {

    private val userManager = context.getSystemService(UserManager::class.java)

    /**
     * Gets the restricted mode.
     *
     * Would either need [MANAGE_USERS] or [INTERACT_ACROSS_USERS] permission to call, otherwise
     * always no restricted.
     */
    @RequiresPermission(anyOf = [MANAGE_USERS, INTERACT_ACROSS_USERS])
    override fun getRestrictedMode(restrictions: Restrictions): RestrictedMode {
        check(restrictions is SystemRestrictions)
        val userRestrictions = getUserRestrictions()
        if (restrictions.keys.any { key -> userRestrictions.getBoolean(key) })
            return SystemBlockedByAdmin(context)
        return NoRestricted
    }

    @RequiresPermission(anyOf = [MANAGE_USERS, INTERACT_ACROSS_USERS])
    private fun getUserRestrictions(): Bundle =
        try {
            userManager?.userRestrictions ?: bundleOf()
        } catch (e: Exception) {
            Log.w(TAG, "userManager.getUserRestrictions() failed", e)
            bundleOf()
        }

    private companion object {
        private const val TAG = "SystemRestrictedRepo"
    }
}
