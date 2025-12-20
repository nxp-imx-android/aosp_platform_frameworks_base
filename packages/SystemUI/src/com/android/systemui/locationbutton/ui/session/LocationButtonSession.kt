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
package com.android.systemui.locationbutton.ui.session

import android.Manifest
import android.app.PendingIntent
import android.app.permissionui.ILocationButtonClient
import android.app.permissionui.ILocationButtonSession
import android.app.permissionui.LocationButtonClient
import android.app.permissionui.LocationButtonRequest
import android.app.permissionui.LocationButtonSession.TextType
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.ParcelableException
import android.os.Process
import android.os.RemoteCallback
import android.os.RemoteException
import android.util.Slog
import android.view.SurfaceControlViewHost
import android.view.WindowManager
import android.window.TrustedPresentationThresholds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import com.android.internal.graphics.ColorUtils
import com.android.systemui.locationbutton.domain.interactor.LocationButtonInteractor
import com.android.systemui.locationbutton.shared.model.ButtonModel
import com.android.systemui.locationbutton.ui.compose.LocationButton
import com.android.systemui.locationbutton.ui.view.LocationButtonRootView
import com.android.systemui.locationbutton.ui.viewmodel.LocationButtonViewModel
import com.android.systemui.res.R
import java.util.concurrent.Executor

/**
 * Manages a single location button session, handling client communication and lifecycle.
 *
 * This class is the server-side implementation of a location button session. It is created by
 * `com.android.systemui.locationbutton.LocationButtonRenderService` for each new session request.
 * Its primary responsibilities include:
 * * **IPC Handling:** Implements the [ILocationButtonSession.Stub] interface to handle remote calls
 *   from the client.
 * * **Remote Rendering:** Uses a [SurfaceControlViewHost] to render the location button into the
 *   client application's view hierarchy.
 * * **Security:** Registers a [android.window.TrustedPresentationListener] to monitor the button's
 *   visibility and detect potential occlusion.
 * * **Client Lifecycle:** Implements [IBinder.DeathRecipient] to listen for the client process's
 *   death, ensuring proper cleanup.
 * * **View Management:** Delegates view creation to [setupComposeView], which hosts the
 *   [LocationButton] composable. State is managed via the [LocationButtonInteractor] and
 *   [LocationButtonViewModel].
 *
 * @param context The SystemUI context.
 * @param hostToken The client's host token for [SurfaceControlViewHost].
 * @param displayId The ID of the display to render on.
 * @param packageName The package name of the client (currently unused, but available).
 * @param request The initial [LocationButtonRequest] containing size and style info.
 * @param locationButtonClient The AIDL client interface for callbacks (e.g., error).
 * @param executor The main executor to run operations on.
 * @param interactor The business logic layer for managing button state.
 * @param viewModelFactory The factory to create the [LocationButtonViewModel].
 * @param sessionId A unique ID for this session.
 * @see com.android.systemui.locationbutton.LocationButtonRenderService
 */
class LocationButtonSession(
    val context: Context,
    hostToken: IBinder,
    displayId: Int,
    val packageName: String,
    val packageUid: Int,
    request: LocationButtonRequest,
    val locationButtonClient: ILocationButtonClient,
    val executor: Executor,
    private val interactor: LocationButtonInteractor,
    private val viewModelFactory: LocationButtonViewModel.Factory,
    private val sessionId: Int,
) : ILocationButtonSession.Stub(), IBinder.DeathRecipient {
    private val surfaceControlViewHost: SurfaceControlViewHost

    // Service set up this listener to track active sessions.
    private var onLocationButtonSessionCloseListener: OnLocationButtonSessionCloseListener? = null
    var isActive = false
    private var isButtonUiInTrustedState = false

    init {
        if (DEBUG) {
            Slog.d(TAG, "Creating new location button session $sessionId for request: $request")
        }
        interactor.setButtonState(sessionId, request.toButtonModel())
        surfaceControlViewHost = setupSurfaceControlViewHost(hostToken, displayId)
        setupComposeView(request)
        registerTrustedPresentationListener()
        linkToDeath()
    }

    private fun setupSurfaceControlViewHost(
        hostToken: IBinder,
        displayId: Int,
    ): SurfaceControlViewHost {
        val displayManager = context.getSystemService(DisplayManager::class.java)!!
        return SurfaceControlViewHost(context, displayManager.getDisplay(displayId), hostToken)
    }

    private fun setupComposeView(request: LocationButtonRequest) {
        val rootView = LocationButtonRootView(context)
        val composeView = ComposeView(context)
        rootView.addView(composeView)
        composeView.setContent {
            LocationButton(
                viewModelFactory = viewModelFactory,
                sessionId = sessionId,
                onClick = { handleLocationButtonClick() },
            )
        }
        surfaceControlViewHost.setView(rootView, request.width, request.height)
    }

    private fun linkToDeath() {
        try {
            locationButtonClient.asBinder().linkToDeath(this, 0)
            isActive = true
        } catch (ex: RemoteException) {
            Slog.e(TAG, "Client died, closing new session.", ex)
            close()
        }
    }

    val surfacePackage = surfaceControlViewHost.surfacePackage!!

    private fun handleLocationButtonClick() {
        if (!isButtonUiInTrustedState) {
            Slog.w(TAG, "Location button click received, but not trusted.")
            return
        }
        if (
            context.checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Process.INVALID_PID,
                packageUid,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationButtonClient.onPermissionsResult(true)
            return
        }

        val remoteCallback = RemoteCallback { bundle ->
            val isPermissionGranted =
                bundle?.getBoolean(LocationButtonClient.EXTRA_PERMISSION_RESULT) ?: false
            locationButtonClient.onPermissionsResult(isPermissionGranted)
        }

        val intent =
            Intent(LocationButtonClient.ACTION_REQUEST_LOCATION_BUTTON_PERMISSIONS).apply {
                setPackage(context.packageManager.permissionControllerPackageName)
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                putExtra(Intent.EXTRA_REMOTE_CALLBACK, remoteCallback)
            }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        val pendingIntent = PendingIntent.getActivity(context, /* requestCode */ 0, intent, flags)
        try {
            locationButtonClient.onRequestPermissions(pendingIntent)
        } catch (e: RemoteException) {
            Slog.e(TAG, "Client died or failed to respond on button click, close session.", e)
            close()
        }
    }

    private fun registerTrustedPresentationListener() {
        val windowManager = context.getSystemService(WindowManager::class.java)!!
        val trustedPresentationThresholds =
            TrustedPresentationThresholds(
                MIN_ALPHA_FOR_TRUSTED_PRESENTATION,
                MIN_FRACTION_RENDERED_FOR_TRUSTED_PRESENTATION,
                TRUSTED_PRESENTATION_STABILITY_MS,
            )
        windowManager.registerTrustedPresentationListener(
            surfaceControlViewHost.windowToken.asBinder(),
            trustedPresentationThresholds,
            executor,
        ) { inTrustedPresentationState ->
            Slog.i(TAG, "TPL callback received with trusted state as $inTrustedPresentationState")
            isButtonUiInTrustedState = inTrustedPresentationState
        }
    }

    override fun setCornerRadius(cornerRadius: Float) {
        executor.execute {
            ensureActiveSession()
            interactor.setCornerRadius(sessionId, cornerRadius)
        }
    }

    override fun setBackgroundColor(color: Int) {
        Slog.i(TAG, "setBackgroundColor: $color")
        executor.execute {
            ensureActiveSession()
            // For security, ensure the background is always opaque.
            val opaqueBackgroundColor = ColorUtils.setAlphaComponent(color, 255)
            interactor.setBackgroundColor(sessionId, opaqueBackgroundColor)
        }
    }

    override fun setTextColor(textColor: Int) {
        executor.execute {
            ensureActiveSession()
            interactor.setTextColor(sessionId, textColor)
        }
    }

    override fun setIconTint(color: Int) {
        executor.execute {
            ensureActiveSession()
            interactor.setIconTint(sessionId, color)
        }
    }

    override fun setTextType(textType: Int) {
        executor.execute {
            ensureActiveSession()
            interactor.setTextId(sessionId, getTextResourceId(textType))
        }
    }

    override fun resize(width: Int, height: Int) {
        executor.execute {
            ensureActiveSession()
            interactor.setSize(sessionId, width, height)
            surfaceControlViewHost.relayout(width, height)
        }
    }

    override fun setStrokeColor(color: Int) {
        executor.execute {
            ensureActiveSession()
            interactor.setStrokeColor(sessionId, color)
        }
    }

    override fun setStrokeWidth(width: Int) {
        executor.execute {
            ensureActiveSession()
            interactor.setStrokeWidth(sessionId, width)
        }
    }

    override fun changeConfiguration(newConfig: Configuration) {
        executor.execute {
            executor.execute {
                ensureActiveSession()
                interactor.setConfiguration(sessionId, newConfig)
            }
        }
    }

    override fun close() {
        executor.execute {
            if (!isActive) {
                return@execute
            }
            isActive = false
            surfaceControlViewHost.release()
            try {
                locationButtonClient.asBinder().unlinkToDeath(this, 0)
            } catch (_: NoSuchElementException) {
                // ignore
            }
            interactor.removeButtonState(sessionId)
            onLocationButtonSessionCloseListener?.onSessionClose(this)
        }
    }

    private fun ensureActiveSession() {
        if (!isActive) {
            Slog.w(TAG, "Session is already closed, can't use a closed session.")
            try {
                locationButtonClient.onSessionError(
                    ParcelableException(
                        IllegalStateException(
                            "Attempted to use a session that has already been closed."
                        )
                    )
                )
            } catch (e: RemoteException) {
                Slog.w(TAG, "ensureActiveSession: Failed to notify client of closed session.", e)
            }
        }
    }

    private fun getTextResourceId(@TextType textType: Int): Int? {
        return when (textType) {
            android.app.permissionui.LocationButtonSession.TEXT_TYPE_PRECISE_LOCATION ->
                R.string.location_button_text_precise_location

            android.app.permissionui.LocationButtonSession.TEXT_TYPE_SHARE_PRECISE_LOCATION ->
                R.string.location_button_text_share_precise_location

            android.app.permissionui.LocationButtonSession.TEXT_TYPE_USE_PRECISE_LOCATION ->
                R.string.location_button_text_use_precise_location

            android.app.permissionui.LocationButtonSession.TEXT_TYPE_NEAR_PRECISE_LOCATION ->
                R.string.location_button_text_near_precise_location

            android.app.permissionui.LocationButtonSession.TEXT_TYPE_NONE -> null
            else -> {
                Slog.w("LocationButton", "Text type $textType is not supported. Using no text.")
                null
            }
        }
    }

    override fun binderDied() {
        close()
    }

    fun setOnLocationButtonSessionCloseListener(listener: OnLocationButtonSessionCloseListener?) {
        onLocationButtonSessionCloseListener = listener
    }

    fun interface OnLocationButtonSessionCloseListener {
        fun onSessionClose(locationButtonSession: LocationButtonSession)
    }

    fun LocationButtonRequest.toButtonModel(): ButtonModel {
        return ButtonModel(
            width = width,
            height = height,
            // For security, ensure the background is always opaque.
            backgroundColor = Color(ColorUtils.setAlphaComponent(backgroundColor, 255)),
            strokeColor = Color(strokeColor),
            strokeWidth = strokeWidth,
            cornerRadius = cornerRadius,
            iconTint = Color(iconTint),
            textResId = getTextResourceId(textType),
            textColor = Color(textColor),
            configuration = configuration,
        )
    }

    companion object {
        private const val TAG = "LocationButtonSessionBinder"
        private const val DEBUG = false

        /** The minimum alpha the button must have to be considered trusted. 90% opaque. */
        private const val MIN_ALPHA_FOR_TRUSTED_PRESENTATION = 0.9f

        /** The minimum fraction of the button that must be visible. 90% visible. */
        private const val MIN_FRACTION_RENDERED_FOR_TRUSTED_PRESENTATION = 0.9f

        /** The minimum time in ms the button must be stable to be considered trusted. */
        private const val TRUSTED_PRESENTATION_STABILITY_MS = 200
    }
}
