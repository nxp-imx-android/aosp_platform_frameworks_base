/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.row.icon

import android.annotation.WorkerThread
import android.app.ActivityManager
import android.app.Flags.notificationsRedesignThemedAppIcons
import android.content.Context
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.UserHandle
import com.android.internal.R
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.mono.ColorList
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.util.UserIconInfo
import com.android.settingslib.Utils
import com.android.systemui.Dumpable
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.util.asIndenting
import com.android.systemui.util.dpToPx
import com.android.systemui.util.printSection
import com.android.systemui.util.time.SystemClock
import com.android.users.UserType
import java.io.PrintWriter
import javax.inject.Inject
import kotlin.math.ceil

/** A provider used to cache and fetch app icons used by notifications. */
interface AppIconProvider {
    /**
     * Loads the icon corresponding to [packageName] into cache, or fetches it from there if already
     * present. This should only be called from the background.
     */
    @Throws(NameNotFoundException::class)
    @WorkerThread
    fun getOrFetchAppIcon(
        packageName: String,
        userHandle: UserHandle,
        instanceKey: String,
    ): Drawable

    /**
     * Loads the skeleton (black and white)-themed icon corresponding to [packageName] into cache,
     * or fetches it from there if already present. This should only be called from the background.
     */
    @Throws(NameNotFoundException::class)
    @WorkerThread
    fun getOrFetchSkeletonAppIcon(packageName: String, userHandle: UserHandle): Drawable

    /**
     * Mark all the entries in the cache that are NOT in [wantedPackages] to be cleared. If they're
     * still not needed on the next call of this method (made after a timeout of 1s, in case they
     * happen more frequently than that), they will be purged. This can be done from any thread.
     */
    fun purgeCache(wantedPackages: Collection<String>)
}

// TODO: b/476412775 - This class shouldn't be open, instead the open methods should be moved to
//  an interface we can inject.
@SysUISingleton
open class AppIconProviderImpl
@Inject
constructor(
    @ShadeDisplayAware private val sysuiContext: Context,
    dumpManager: DumpManager,
    systemClock: SystemClock,
) : AppIconProvider, Dumpable {
    init {
        dumpManager.registerNormalDumpable(TAG, this)
    }

    private val iconSize: Int
        get() =
            sysuiContext.resources.getDimensionPixelSize(
                if (ActivityManager.isLowRamDeviceStatic()) {
                    R.dimen.notification_small_icon_size_low_ram
                } else {
                    R.dimen.notification_small_icon_size
                }
            )

    private val densityDpi: Int
        get() = sysuiContext.resources.configuration.densityDpi

    private val standardIconFactory: BaseIconFactory
        get() =
            BaseIconFactory(
                context = sysuiContext,
                fullResIconDpi = densityDpi,
                iconBitmapSize = iconSize,
                // Initialize the controller so that we can support themed icons.
                themeController =
                    if (notificationsRedesignThemedAppIcons())
                        MonoIconThemeController(
                            shouldForceThemeIcon = true,
                            colorProvider = { ctx ->
                                val res = ctx.resources
                                val bgColor = res.getColor(R.color.materialColorPrimary, null)
                                val foregroundColor =
                                    res.getColor(R.color.materialColorSurfaceContainerHigh, null)
                                ColorList(
                                    iconBackgroundColor = bgColor,
                                    iconForegroundColor = foregroundColor,
                                    iconAdaptiveBackgroundColor = bgColor,
                                    badgeBackgroundColor = bgColor,
                                    badgeForegroundColor = foregroundColor,
                                )
                            },
                        )
                    else null,
            )

    private val skeletonIconFactory: BaseIconFactory
        get() =
            BaseIconFactory(
                context = sysuiContext,
                fullResIconDpi = densityDpi,
                iconBitmapSize = iconSize,
                themeController =
                    MonoIconThemeController(
                        shouldForceThemeIcon = true,
                        colorProvider = { _ ->
                            ColorList(
                                iconBackgroundColor = Color.BLACK,
                                iconForegroundColor = Color.WHITE,
                                iconAdaptiveBackgroundColor = Color.BLACK,
                                badgeBackgroundColor = Color.BLACK,
                                badgeForegroundColor = Color.WHITE,
                            )
                        },
                    ),
            )

    /** Cache of standard-appearance icons as used in the notification row and guts */
    private val standardCache = AppIconCache(systemClock = systemClock)

    /** Cache of black and white icons for use on AOD */
    private val skeletonCache = AppIconCache(systemClock = systemClock)

    override fun getOrFetchAppIcon(
        packageName: String,
        userHandle: UserHandle,
        instanceKey: String,
    ): Drawable =
        standardCache.getOrFetchAppIcon(
            packageName = packageName,
            userHandle = userHandle,
            drawableInstanceKey = instanceKey,
            createDrawable = {
                it.createIconDrawable(themed = notificationsRedesignThemedAppIcons())
            },
        ) {
            fetchAppIconBitmapInfo(
                standardIconFactory,
                packageName,
                userHandle,
                allowProfileBadge = true,
            )
        }

    override fun getOrFetchSkeletonAppIcon(packageName: String, userHandle: UserHandle): Drawable =
        skeletonCache.getOrFetchAppIcon(
            packageName = packageName,
            userHandle = null, // these aren't badged, so they don't need to be sharded by user
            drawableInstanceKey = "SKELETON",
            createDrawable = {
                it.createIconDrawable(themed = true, outlined = Flags.aodNotifIconOutline())
            },
        ) {
            fetchAppIconBitmapInfo(
                skeletonIconFactory,
                packageName,
                userHandle,
                allowProfileBadge = true,
            )
        }

    /**
     * Get the unstyled, unbadged icon corresponding to the given package and user. By default this
     * calls directly into PackageManager, but it can be overridden if a different approach is
     * needed (e.g. in tests).
     */
    protected open fun getRawIcon(packageName: String, userHandle: UserHandle): Drawable? {
        val pm = sysuiContext.packageManager
        val userId = userHandle.identifier
        return pm.getApplicationInfoAsUser(packageName, MATCH_UNINSTALLED_PACKAGES, userId)
            .loadUnbadgedIcon(pm)
    }

    @WorkerThread
    private fun fetchAppIconBitmapInfo(
        iconFactory: BaseIconFactory,
        packageName: String,
        userHandle: UserHandle,
        allowProfileBadge: Boolean,
    ): BitmapInfo {
        val icon = getRawIcon(packageName, userHandle)
        val options =
            iconOptions(getUserIconInfo(userHandle, allowProfileBadge = allowProfileBadge))
        return iconFactory.createBadgedIconBitmap(icon, options)
    }

    private fun BitmapInfo.createIconDrawable(
        themed: Boolean,
        outlined: Boolean = false,
    ): Drawable {
        val icon =
            newIcon(
                    context = sysuiContext,
                    creationFlags = if (themed) BitmapInfo.FLAG_THEMED else 0,
                )
                .apply { isAnimationEnabled = false }

        if (outlined) {
            val outline =
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(
                        // This should look similar to the Compose border from
                        // AODPromotedNotification, so we're using ceil to emulate how Compose
                        // makes this conversion.
                        ceil(0.5.dpToPx(sysuiContext)).toInt().coerceAtLeast(1),
                        Color.argb((255 * 0.32f).toInt(), 255, 255, 255),
                    )
                }
            val outlinedIcon = LayerDrawable(arrayOf(outline, icon))

            // Add a 1dp inset to the ring so that it better matches the size of the app icon
            // circle. Launcher adds a bit of space around the icon we need to account for.
            val padding = 1.dpToPx(sysuiContext).toInt()
            // The outline is at index 0, so set the inset on the right layer.
            outlinedIcon.setLayerInset(0, padding, padding, padding, padding)
            return outlinedIcon
        } else {
            return icon
        }
    }

    private fun iconOptions(userIconInfo: UserIconInfo): IconOptions {
        return IconOptions().apply {
            setUser(userIconInfo)
            setBitmapGenerationMode(BaseIconFactory.MODE_HARDWARE)
            // This color will not be used, but we're just setting it so that the icon factory
            // doesn't try to extract colors from our bitmap (since it won't work, given it's a
            // hardware bitmap).
            setExtractedColor(Color.BLUE)
        }
    }

    protected open fun getUserIconInfo(
        userHandle: UserHandle,
        allowProfileBadge: Boolean,
    ): UserIconInfo =
        if (allowProfileBadge) {
            // Look up the user to determine if it is a profile, and if so which badge to use
            Utils.fetchUserIconInfo(sysuiContext, userHandle)
        } else {
            // For a main user the IconFactory does not add a badge
            UserIconInfo(/* user= */ userHandle, /* type= */ UserType.MAIN)
        }

    override fun purgeCache(wantedPackages: Collection<String>) {
        standardCache.purgeCache(wantedPackages)
        skeletonCache.purgeCache(wantedPackages)
    }

    override fun dump(pwOrig: PrintWriter, args: Array<out String>) {
        val pw = pwOrig.asIndenting()
        pw.printSection("standard cache") { standardCache.dump(pw, args) }
        pw.printSection("skeleton cache") { skeletonCache.dump(pw, args) }
        pw.printSection("icon factory info") {
            val standardIconFactory = standardIconFactory
            pw.println("fullResIconDpi = ${standardIconFactory.fullResIconDpi}")
            pw.println("iconSize = ${standardIconFactory.iconBitmapSize}")
        }
    }

    companion object {
        const val TAG = "AppIconProviderImpl"
    }
}
