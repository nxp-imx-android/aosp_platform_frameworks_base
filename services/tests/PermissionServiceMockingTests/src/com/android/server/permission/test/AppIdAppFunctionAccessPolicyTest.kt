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

package com.android.server.permission.test

import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_ALL
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_OTHER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_USER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_OTHER_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_PREGRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_USER_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_USER_GRANTED
import android.util.ArrayMap
import android.util.SparseArray
import com.android.server.permission.access.GetStateScope
import com.android.server.permission.access.MutableAccessState
import com.android.server.permission.access.MutableUserState
import com.android.server.permission.access.MutateStateScope
import com.android.server.permission.access.appfunction.AppIdAppFunctionAccessPolicy
import com.android.server.permission.access.immutable.*
import com.android.server.pm.pkg.PackageState
import com.android.server.pm.pkg.PackageUserState
import com.android.server.testutils.mock
import com.android.server.testutils.whenever
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test

class AppIdAppFunctionAccessPolicyTest {

    private lateinit var oldState: MutableAccessState
    private lateinit var newState: MutableAccessState
    private val appIdAppFunctionPolicy = AppIdAppFunctionAccessPolicy()

    private fun mockPackageState(
        appId: Int,
        packageName: String,
        installedUsers: List<Int> = listOf(USER_ID_0),
    ): PackageState = mock {
        whenever(this.appId).thenReturn(appId)
        whenever(this.packageName).thenReturn(packageName)
        whenever(this.androidPackage).thenReturn(null)

        val userStates =
            SparseArray<PackageUserState>().apply {
                installedUsers.forEach { user ->
                    put(user, mock { whenever(this.isInstalled).thenReturn(true) })
                }
            }
        whenever(this.userStates).thenReturn(userStates)
    }

    @Before
    fun setUp() {
        oldState = MutableAccessState()
        createUserState(USER_ID_0)
        oldState.mutateExternalState().setPackageStates(ArrayMap())
    }

    private fun createUserState(userId: Int) {
        oldState.mutateExternalState().mutateUserIds().add(userId)
        oldState.mutateUserStatesNoWrite().put(userId, MutableUserState())
    }

    private fun getAppFunctionFlags(
        agentAppId: Int,
        userId: Int,
        targetUid: Int,
        state: MutableAccessState = newState,
    ): Int =
        state.userStates[userId]?.appIdAppFunctionAccessFlags?.get(agentAppId)
            .getWithDefault(targetUid, 0)

    private fun setAppFunctionAccessFlags(
        agentAppId: Int,
        userId: Int,
        targetUid: Int,
        flags: Int,
        state: MutableAccessState = oldState,
    ) =
        state
            .mutateUserState(userId)!!
            .mutateAppIdAppFunctionAccessFlags()
            .mutateOrPut(agentAppId) { MutableIntMap() }
            .put(targetUid, flags)

    private fun addPackageState(
        packageState: PackageState,
        state: MutableAccessState = oldState,
    ) {
        state.mutateExternalState().apply {
            setPackageStates(
                packageStates.toMutableMap().apply { put(packageState.packageName, packageState) }
            )
            mutateAppIdPackageNames()
                .mutateOrPut(packageState.appId) { MutableIndexedListSet() }
                .add(packageState.packageName)
        }
    }

    private inline fun mutateState(action: MutateStateScope.() -> Unit) {
        newState = oldState.toMutable()
        MutateStateScope(oldState, newState).action()
    }

    @Test
    fun testOnPackageInstalled_defaultToNoFlags() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        assertWithMessage("Flags should default to 0")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, oldState))
            .isEqualTo(0)
    }

    @Test
    fun testGetAppFunctionAccessFlags() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        setAppFunctionAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        val flags: Int
        GetStateScope(oldState).apply {
            with(appIdAppFunctionPolicy) {
                flags = getAppFunctionAccessFlags(
                    AGENT_APP_ID,
                    USER_ID_0,
                    TARGET_APP_ID,
                    USER_ID_0,
                )
            }
        }
        assertWithMessage("Flags should be $ACCESS_FLAG_USER_GRANTED when set").that(flags)
            .isEqualTo(ACCESS_FLAG_USER_GRANTED)
    }

    @Test
    fun testUpdateAppFunctionAccessFlags_invalidFlag_throwsException() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                try {
                    updateAppFunctionAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                        NONEXISTENT_FLAGS, NONEXISTENT_FLAGS
                    )
                    assertWithMessage("Specifying an invalid flag should result in an exception")
                        .fail()
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test
    fun testUpdateAppFunctionAccessFlags_provideFlagNotInMask_throwsException() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                try {
                    updateAppFunctionAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0, 0,
                        ACCESS_FLAG_PREGRANTED
                    )
                    assertWithMessage("If a flag is specified without being in the flag mask, an " +
                            "exception should be thrown").fail()
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test
    fun testUpdateAppFunctionAccessFlags_setOpposingFlags_throwsException() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                try {
                    updateAppFunctionAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                        ACCESS_FLAG_MASK_USER, ACCESS_FLAG_MASK_USER
                    )
                    assertWithMessage(
                        "Expected an exception to be thrown when USER flags are set together"
                    ).fail()
                } catch (_: IllegalArgumentException) { }

                try {
                    updateAppFunctionAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                        ACCESS_FLAG_MASK_OTHER, ACCESS_FLAG_MASK_OTHER
                    )
                    assertWithMessage(
                        "Expected an exception to be thrown when OTHER flags are set together"
                    ).fail()
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test
    fun testUpdateAppFunctionAccessFlags_setWithoutClearingOpposing_throwsException() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)

        mutateState {
            with(appIdAppFunctionPolicy) {
                try {
                    updateAppFunctionAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                        ACCESS_FLAG_USER_DENIED, ACCESS_FLAG_USER_DENIED
                    )
                    assertWithMessage(
                        "Expected an exception to be thrown when the USER_DENIED flag is set " +
                                " without clearing the GRANTED flag"
                    ).fail()
                } catch (_: IllegalArgumentException) { }

                try {
                    updateAppFunctionAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                        ACCESS_FLAG_OTHER_DENIED, ACCESS_FLAG_OTHER_DENIED
                    )
                    assertWithMessage(
                        "Expected an exception to be thrown when the OTHER_DENIED flag is set " +
                                "without clearing the GRANTED flag"
                    ).fail()
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test
    fun testUpdateAppFunctionAccessFlags_clearSingleOpposing_doesntThrow() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        setAppFunctionAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)

        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAppFunctionAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_USER_GRANTED, 0)
            }
        }
    }

    @Test
    fun testUpdateAppFunctionAccessFlags_agentNotInstalled() {
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAppFunctionAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_PREGRANTED, ACCESS_FLAG_PREGRANTED
                )
            }
        }

        assertWithMessage("Flags should not be set for non-installed agent")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testUpdateAppFunctionAccessFlags_targetNotInstalled() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        addPackageState(agent)
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAppFunctionAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_PREGRANTED, ACCESS_FLAG_PREGRANTED
                )
            }
        }

        assertWithMessage("Flags should not be set for non-installed target")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testUpdateAppFunctionAccessFlags_invalidUser() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAppFunctionAccessFlags(
                    AGENT_APP_ID, USER_ID_1, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_PREGRANTED, ACCESS_FLAG_PREGRANTED
                )
            }
        }

        assertWithMessage("Invalid users should not have flags set")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_1, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }
    @Test
    fun testUpdateAppFunctionAccessFlags_validFlagsSet() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)

        val flags = ACCESS_FLAG_USER_GRANTED or ACCESS_FLAG_PREGRANTED
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAppFunctionAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    flags or ACCESS_FLAG_USER_DENIED, flags
                )
            }
        }
        assertWithMessage("Valid flags for valid app IDs should have flags set")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(flags)
    }


    @Test
    fun testOnAppIdRemoved_agentFlagsCleared() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val otherAgent = mockPackageState(OTHER_AGENT_APP_ID, OTHER_AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        val otherTarget = mockPackageState(TARGET_APP_ID, OTHER_TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(otherAgent)
        addPackageState(target)
        addPackageState(otherTarget)
        setAppFunctionAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        setAppFunctionAccessFlags(AGENT_APP_ID, USER_ID_0, OTHER_TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
        setAppFunctionAccessFlags(OTHER_AGENT_APP_ID, USER_ID_0, TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
        mutateState {
            with(appIdAppFunctionPolicy) {
                onAppIdRemoved(AGENT_APP_ID)
            }
        }
        assertWithMessage("all flags for an agent should be cleared when its app id is removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
        assertWithMessage("all flags for an agent should be cleared when its app id is removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, OTHER_TARGET_APP_ID, newState))
            .isEqualTo(0)
        assertWithMessage("only the agent being removed should have its flags cleared")
            .that(getAppFunctionFlags(OTHER_AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(ACCESS_FLAG_USER_GRANTED)
    }

    @Test
    fun testOnAppIdRemoved_targetFlagsCleared() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        val otherTarget = mockPackageState(TARGET_APP_ID, OTHER_TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        addPackageState(otherTarget)
        setAppFunctionAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        setAppFunctionAccessFlags(AGENT_APP_ID, USER_ID_0, OTHER_TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
            setAppFunctionAccessFlags(OTHER_AGENT_APP_ID, USER_ID_0, OTHER_TARGET_APP_ID,
                ACCESS_FLAG_USER_GRANTED)
        mutateState {
            with(appIdAppFunctionPolicy) {
                onAppIdRemoved(TARGET_APP_ID)
            }
        }
        assertWithMessage("target flags should be removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
        assertWithMessage("only flags related to the removed app id should be removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, OTHER_TARGET_APP_ID, newState))
            .isEqualTo(ACCESS_FLAG_USER_GRANTED)
    }

    @Test
    fun testOnUserRemoved_targetFlagsClearedAcrossAllUsers() {
        createUserState(USER_ID_1)
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        // Target app is user 0, agent is user 1
        setAppFunctionAccessFlags(AGENT_APP_ID, USER_ID_1, TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
        mutateState {
            with(appIdAppFunctionPolicy) {
                onUserRemoved(USER_ID_0)
            }
        }
        assertWithMessage("all target flags corresponding to the removed user should be removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_1, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testOnAgentRemovedFromAllowlist_flagsCleared() {
        // TODO b/416661798: implement when allowlist is active
    }


    companion object {
        const val AGENT_PKG_NAME = "agentPkg"
        const val OTHER_AGENT_PKG_NAME = "otherAgentPkg"
        const val TARGET_PKG_NAME = "targetPkg"
        const val OTHER_TARGET_PKG_NAME = "otherTargetPkg"
        const val AGENT_APP_ID = 0
        const val TARGET_APP_ID = 1
        const val OTHER_AGENT_APP_ID = 2
        const val OTHER_TARGET_APP_ID = 3
        const val USER_ID_0 = 0
        const val USER_ID_1 = 1

        const val NONEXISTENT_FLAGS = ACCESS_FLAG_MASK_ALL.inv()
    }
}