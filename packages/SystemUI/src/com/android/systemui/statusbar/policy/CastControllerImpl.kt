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
package com.android.systemui.statusbar.policy

import android.annotation.AnyThread
import android.annotation.MainThread
import android.annotation.WorkerThread
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRouter
import android.media.MediaRouter.RouteInfo
import android.media.projection.MediaProjectionInfo
import android.media.projection.MediaProjectionManager
import android.media.projection.StopReason
import android.os.Handler
import android.util.ArrayMap
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import androidx.tracing.trace
import com.android.systemui.CoreStartable
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.statusbar.policy.CastControllerLogger.Companion.toLogString
import com.android.systemui.statusbar.policy.CastDevice.Companion.toCastDevice
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Platform implementation of the cast controller. */
@SysUISingleton
class CastControllerImpl
@Inject
constructor(
    private val context: Context,
    private val packageManager: PackageManager,
    private val mediaRouterLazy: dagger.Lazy<MediaRouter>,
    private val projectionManagerLazy: dagger.Lazy<MediaProjectionManager>,
    private val logger: CastControllerLogger,
    @Application private val appScope: CoroutineScope,
    @Main private val mainDispatcher: CoroutineDispatcher,
    @Background private val bgHandler: Handler,
    @Background private val bgDispatcher: CoroutineDispatcher,
    dumpManager: DumpManager,
) : CastController, CoreStartable {

    private val callbacks = CopyOnWriteArrayList<CastController.Callback>()

    @GuardedBy("routes") private val routesByTag = ArrayMap<String, RouteInfo>()

    private val discoveringLock = Any()
    private val projectionLock = Any()

    private lateinit var mediaRouter: MediaRouter
    private lateinit var projectionManager: MediaProjectionManager

    @GuardedBy("discoveringLock") private var discovering = false
    @GuardedBy("discoveringLock") private var callbackRegistered = false
    @GuardedBy("projectionLock") private var projection: MediaProjectionInfo? = null

    init {
        if (Flags.castControllerMediaRouterInBg()) {
            dumpManager.registerNormalDumpable(TAG, this)
        }
    }

    override fun start() {
        if (!Flags.castControllerMediaRouterInBg()) return
        // MediaRouter does Binder calls on init so we need to lazily create it during
        // CoreStarteable startup.
        mediaRouter = mediaRouterLazy.get()
        mediaRouter.setRouterGroupId(MediaRouter.MIRRORING_GROUP_ID)
        projectionManager = projectionManagerLazy.get()
        projectionManager.addCallback(projectionCallback, bgHandler)
        synchronized(projectionLock) { projection = projectionManager.activeProjectionInfo }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("CastController state:")
        pw.println("  mDiscovering=$discovering")
        pw.println("  mCallbackRegistered=$callbackRegistered")
        pw.println("  mCallbacks.size=${callbacks.size}")
        pw.println("  mRoutes.size=${routesByTag.size}")
        routesByTag.values.forEach { pw.println("    ${ it.toLogString() }") }
        pw.println("  mProjection=$projection")
    }

    @AnyThread
    override fun addCallback(callback: CastController.Callback) {
        appScope.launch {
            callbacks.add(callback)
            fireOnCastDevicesChanged(callback)
            handleDiscoveryChange()
        }
    }

    @AnyThread
    override fun removeCallback(callback: CastController.Callback) {
        appScope.launch {
            callbacks.remove(callback)
            handleDiscoveryChange()
        }
    }

    @AnyThread
    override fun setDiscovering(request: Boolean) {
        appScope.launch(bgDispatcher) {
            synchronized(discoveringLock) {
                if (discovering == request) return@launch
                discovering = request
                logger.logDiscovering(request)
                handleDiscoveryChangeLocked()
            }
        }
    }

    @AnyThread
    private suspend fun handleDiscoveryChange() =
        withContext(bgDispatcher) {
            synchronized(discoveringLock) { handleDiscoveryChangeLocked() }
        }

    @WorkerThread
    @GuardedBy("discoveringLock")
    private fun handleDiscoveryChangeLocked() =
        trace("handleDiscoveryChangeLocked") {
            if (callbackRegistered) {
                mediaRouter.removeCallback(mediaCallback)
                callbackRegistered = false
            }

            if (discovering) {
                mediaRouter.addCallback(
                    MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY,
                    mediaCallback,
                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY,
                )
                callbackRegistered = true
            } else {
                val hasCallbacks = callbacks.isEmpty()
                if (!hasCallbacks) {
                    mediaRouter.addCallback(
                        MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY,
                        mediaCallback,
                        MediaRouter.CALLBACK_FLAG_PASSIVE_DISCOVERY,
                    )
                    callbackRegistered = true
                }
            }
        }

    @AnyThread
    override fun setCurrentUserId(currentUserId: Int) {
        appScope.launch(bgDispatcher) { mediaRouter.rebindAsUser(currentUserId) }
    }

    @AnyThread
    override fun getCastDevices(): List<CastDevice> {
        return buildList {
            synchronized(routesByTag) {
                addAll(routesByTag.values.map { it.toCastDevice(context) })
            }

            synchronized(projectionLock) {
                projection?.let { add(it.toCastDevice(context, packageManager, logger)) }
            }
        }
    }

    @AnyThread
    override fun startCasting(device: CastDevice?) {
        if (device?.tag == null) return
        appScope.launch { startCastingSuspend(device) }
    }

    private suspend fun startCastingSuspend(device: CastDevice) =
        withContext(bgDispatcher) {
            trace("startCasting") {
                val route = device.tag as RouteInfo?
                logger.logStartCasting(route!!)
                mediaRouter.selectRoute(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY, route)
            }
        }

    @AnyThread
    override fun stopCasting(device: CastDevice, @StopReason stopReason: Int) {
        appScope.launch { stopCastingSuspend(device, stopReason) }
    }

    private suspend fun stopCastingSuspend(device: CastDevice, @StopReason stopReason: Int) =
        withContext(bgDispatcher) {
            trace("stopCasting") {
                val isProjection = device.tag is MediaProjectionInfo
                logger.logStopCasting(isProjection)
                if (isProjection) {
                    val projection = device.tag as MediaProjectionInfo?
                    if (projectionManager.activeProjectionInfo == projection) {
                        projectionManager.stopActiveProjection(stopReason)
                    } else {
                        logger.logStopCastingNoProjection(projection!!)
                    }
                } else {
                    logger.logStopCastingMediaRouter()
                    mediaRouter.fallbackRoute.select()
                }
            }
        }

    override fun hasConnectedCastDevice(): Boolean =
        castDevices.any { castDevice -> castDevice.state == CastDevice.CastState.Connected }

    @AnyThread
    private suspend fun setProjection(projection: MediaProjectionInfo, started: Boolean) =
        withContext(bgDispatcher) {
            trace("setProjection") {
                var changed = false
                val oldProjection = this@CastControllerImpl.projection
                synchronized(projectionLock) {
                    val isCurrent = projection == this@CastControllerImpl.projection
                    if (started && !isCurrent) {
                        this@CastControllerImpl.projection = projection
                        changed = true
                    } else if (!started && isCurrent) {
                        this@CastControllerImpl.projection = null
                        changed = true
                    }
                }
                if (changed) {
                    logger.logSetProjection(oldProjection, this@CastControllerImpl.projection)
                    fireOnCastDevicesChanged()
                }
            }
        }

    @AnyThread
    private suspend fun updateRemoteDisplays() =
        withContext(bgDispatcher) {
            trace("updateRemoteDisplays") {
                synchronized(routesByTag) {
                    routesByTag.clear()
                    val n = mediaRouter.routeCount
                    for (i in 0..<n) {
                        val route = mediaRouter.getRouteAt(i)
                        if (!route.isEnabled) continue
                        if (!route.matchesTypes(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY)) continue
                        ensureTagExists(route)
                        routesByTag[route.tag.toString()] = route
                    }
                    val selected =
                        mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY)
                    if (selected?.isDefault == false) {
                        ensureTagExists(selected)
                        routesByTag[selected.tag.toString()] = selected
                    }
                }
            }
            fireOnCastDevicesChanged()
        }

    private fun ensureTagExists(route: RouteInfo) {
        if (route.tag == null) {
            route.tag = UUID.randomUUID().toString()
        }
    }

    @AnyThread
    @VisibleForTesting
    suspend fun fireOnCastDevicesChanged() =
        withContext(mainDispatcher) {
            trace("fireOnCastDevicesChanged") {
                for (callback in callbacks) {
                    fireOnCastDevicesChanged(callback)
                }
            }
        }

    @MainThread
    private fun fireOnCastDevicesChanged(callback: CastController.Callback) {
        callback.onCastDevicesChanged()
    }

    private val mediaCallback: MediaRouter.SimpleCallback =
        object : MediaRouter.SimpleCallback() {

            @MainThread
            override fun onRouteAdded(router: MediaRouter, route: RouteInfo) {
                appScope.launch {
                    logger.logRouteAdded(route)
                    updateRemoteDisplays()
                }
            }

            @MainThread
            override fun onRouteChanged(router: MediaRouter, route: RouteInfo) {
                appScope.launch {
                    logger.logRouteChanged(route)
                    updateRemoteDisplays()
                }
            }

            @MainThread
            override fun onRouteRemoved(router: MediaRouter, route: RouteInfo) {
                appScope.launch {
                    logger.logRouteRemoved(route)
                    updateRemoteDisplays()
                }
            }

            @MainThread
            override fun onRouteSelected(router: MediaRouter, type: Int, route: RouteInfo) {
                appScope.launch {
                    logger.logRouteSelected(route, type)
                    updateRemoteDisplays()
                }
            }

            @MainThread
            override fun onRouteUnselected(router: MediaRouter, type: Int, route: RouteInfo) {
                appScope.launch {
                    logger.logRouteUnselected(route, type)
                    updateRemoteDisplays()
                }
            }
        }

    private val projectionCallback: MediaProjectionManager.Callback =
        object : MediaProjectionManager.Callback() {

            @WorkerThread
            override fun onStart(info: MediaProjectionInfo) {
                appScope.launch { setProjection(info, true) }
            }

            @WorkerThread
            override fun onStop(info: MediaProjectionInfo) {
                appScope.launch { setProjection(info, false) }
            }
        }

    companion object {
        private const val TAG = "CastController"
    }
}
