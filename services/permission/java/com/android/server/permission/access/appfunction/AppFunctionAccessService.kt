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

package com.android.server.permission.access.appfunction

import android.Manifest
import android.app.appfunctions.AppFunctionAccessServiceInterface
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_OTHER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_USER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_OTHER_GRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_PREGRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_USER_GRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_GRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_UNREQUESTABLE
import android.content.pm.SignedPackage
import android.permission.flags.Flags.appFunctionAccessServiceEnabled
import android.util.Slog
import com.android.server.LocalManagerRegistry
import com.android.server.LocalServices
import com.android.server.permission.access.AccessCheckingService
import com.android.server.permission.access.AppFunctionAccessUri
import com.android.server.permission.access.UidUri
import com.android.server.permission.access.util.PermissionEnforcer
import com.android.server.permission.access.util.hasAnyBit
import com.android.server.permission.access.util.hasBits
import com.android.server.pm.PackageManagerLocal
import com.android.server.pm.UserManagerInternal
import com.android.server.pm.pkg.PackageState

class AppFunctionAccessService(private val service: AccessCheckingService) :
    AppFunctionAccessServiceInterface {
    private val policy =
        service.getSchemePolicy(UidUri.SCHEME, AppFunctionAccessUri.SCHEME)
            as AppIdAppFunctionAccessPolicy
    private val context = service.context
    private lateinit var packageManagerLocal: PackageManagerLocal
    private lateinit var userManagerInternal: UserManagerInternal
    private lateinit var permissionEnforcer: PermissionEnforcer

    fun initialize() {
        packageManagerLocal =
            LocalManagerRegistry.getManagerOrThrow(PackageManagerLocal::class.java)
        userManagerInternal = LocalServices.getService(UserManagerInternal::class.java)
        permissionEnforcer = PermissionEnforcer(context)
    }

    override fun checkAppFunctionAccess(
        agentPackageName: String,
        agentUserId: Int,
        targetPackageName: String,
        targetUserId: Int,
    ): Boolean =
        getAppFunctionAccessRequestState(
            agentPackageName,
            agentUserId,
            targetPackageName,
            targetUserId,
        ) == ACCESS_REQUEST_STATE_GRANTED

    override fun getAppFunctionAccessRequestState(
        agentPackageName: String,
        agentUserId: Int,
        targetPackageName: String,
        targetUserId: Int,
    ): Int {
        if (!appFunctionAccessServiceEnabled()) {
            return ACCESS_REQUEST_STATE_UNREQUESTABLE
        }

        val methodName = "getAppFunctionAccessFlags"
        enforceCallingOrSelfCrossUserPermission(methodName, agentUserId, targetUserId)
        if (!allUsersExist(methodName, agentUserId, targetUserId)) {
            return ACCESS_REQUEST_STATE_UNREQUESTABLE
        }

        permissionEnforcer.enforceCallingOrSelfAnyPermission(
            methodName,
            Manifest.permission.MANAGE_APP_FUNCTION_ACCESS,
        )

        val agentPackageState =
            getFilteredPackageState(agentPackageName, agentUserId, methodName)
                ?: return ACCESS_REQUEST_STATE_UNREQUESTABLE
        val targetPackageState =
            getFilteredPackageState(targetPackageName, targetUserId, methodName)
                ?: return ACCESS_REQUEST_STATE_UNREQUESTABLE

        service.getState {
            with(policy) {
                val flags =
                    getAppFunctionAccessFlags(
                        agentPackageState.appId,
                        agentUserId,
                        targetPackageState.appId,
                        targetUserId,
                    )
                return if (isAccessGranted(flags)) {
                    ACCESS_REQUEST_STATE_GRANTED
                } else {
                    ACCESS_REQUEST_STATE_DENIED
                }
            }
        }
    }

    override fun getAppFunctionAccessFlags(
        agentPackageName: String,
        agentUserId: Int,
        targetPackageName: String,
        targetUserId: Int,
    ): Int {
        if (!appFunctionAccessServiceEnabled()) {
            return 0
        }

        val methodName = "getAppFunctionAccessFlags"
        enforceCallingOrSelfCrossUserPermission(methodName, agentUserId, targetUserId)
        if (!allUsersExist(methodName, agentUserId, targetUserId)) {
            return 0
        }

        permissionEnforcer.enforceCallingOrSelfAnyPermission(
            methodName,
            Manifest.permission.MANAGE_APP_FUNCTION_ACCESS,
        )

        val agentPackageState =
            getFilteredPackageState(agentPackageName, agentUserId, methodName) ?: return 0
        val targetPackageState =
            getFilteredPackageState(targetPackageName, targetUserId, methodName) ?: return 0

        return service.getState {
            with(policy) {
                getAppFunctionAccessFlags(
                    agentPackageState.appId,
                    agentUserId,
                    targetPackageState.appId,
                    targetUserId,
                )
            }
        }
    }

    override fun setAgentAllowlist(agentAllowlist: List<SignedPackage>) {
        service.onAgentAllowlistChanged(agentAllowlist)
    }

    override fun updateAppFunctionAccessFlags(
        agentPackageName: String,
        agentUserId: Int,
        targetPackageName: String,
        targetUserId: Int,
        flagMask: Int,
        flags: Int,
    ): Boolean {
        if (!appFunctionAccessServiceEnabled()) {
            return false
        }

        val methodName = "setAppFunctionAccessFlags"
        enforceCallingOrSelfCrossUserPermission(methodName, agentUserId, targetUserId)
        if (!allUsersExist(methodName, agentUserId, targetUserId)) {
            return false
        }

        permissionEnforcer.enforceCallingOrSelfAnyPermission(
            methodName,
            Manifest.permission.MANAGE_APP_FUNCTION_ACCESS,
        )

        val agentPackageState =
            getFilteredPackageState(agentPackageName, agentUserId, methodName) ?: return false
        val targetPackageState =
            getFilteredPackageState(targetPackageName, targetUserId, methodName) ?: return false
        var changed = true
        service.mutateState {
            with(policy) {
                changed =
                    updateAppFunctionAccessFlags(
                        agentPackageState.appId,
                        agentUserId,
                        targetPackageState.appId,
                        targetUserId,
                        flagMask,
                        flags,
                    )
            }
        }
        return changed
    }

    private fun enforceCallingOrSelfCrossUserPermission(
        message: String?,
        agentUserId: Int,
        targetUserId: Int,
    ) {
        permissionEnforcer.enforceCallingOrSelfCrossUserPermission(
            agentUserId,
            enforceFullPermission = true,
            enforceShellRestriction = true,
            message,
        )
        permissionEnforcer.enforceCallingOrSelfCrossUserPermission(
            targetUserId,
            enforceFullPermission = true,
            enforceShellRestriction = true,
            message,
        )
    }

    private fun allUsersExist(methodName: String, userId1: Int, userId2: Int): Boolean =
        userExists(methodName, userId1) && userExists(methodName, userId2)

    private fun userExists(methodName: String, userId: Int): Boolean {
        val exists = userManagerInternal.exists(userId)
        if (!exists) {
            Slog.w(LOG_TAG, "$methodName: Unknown user $userId")
        }
        return exists
    }

    private fun getFilteredPackageState(
        packageName: String,
        userId: Int,
        methodName: String,
    ): PackageState? =
        packageManagerLocal.withFilteredSnapshot().use {
            val packageState = it.getPackageState(packageName)
            if (packageState?.userStates[userId]?.isInstalled == true) {
                packageState
            } else {
                Slog.w(LOG_TAG, "$methodName: Unknown package(s) $packageName")
                null
            }
        }

    companion object {
        private val LOG_TAG = AppFunctionAccessService::class.java.simpleName

        // Grant logic ordering goes as follows: USER flags override OTHER flags.
        // If no other DENIED flags are applied, PREGRANTED flag means granted.
        private fun isAccessGranted(flags: Int): Boolean {
            if (flags.hasAnyBit(ACCESS_FLAG_MASK_USER)) {
                return flags.hasBits(ACCESS_FLAG_USER_GRANTED)
            }
            if (flags.hasAnyBit(ACCESS_FLAG_MASK_OTHER)) {
                return flags.hasBits(ACCESS_FLAG_OTHER_GRANTED)
            }
            return flags.hasBits(ACCESS_FLAG_PREGRANTED)
        }
    }
}
