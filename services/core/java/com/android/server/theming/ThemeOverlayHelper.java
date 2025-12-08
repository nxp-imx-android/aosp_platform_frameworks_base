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

package com.android.server.theming;

import static android.util.TypedValue.TYPE_INT_COLOR_ARGB8;

import android.content.om.FabricatedOverlay;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayManagerTransaction;
import android.os.UserHandle;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.om.OverlayManagerInternal;
import com.android.systemui.monet.ColorScheme;
import com.android.systemui.monet.DynamicColors;

import com.google.ux.material.libmonet.dynamiccolor.DynamicColor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/**
 * A utility class responsible for creating and applying color-based theme overlays.
 * <p>
 * This class encapsulates the logic for generating {@link FabricatedOverlay} instances
 * from a {@link ColorScheme} and committing them to the {@link OverlayManagerInternal}.
 *
 * @hide
 */
public class ThemeOverlayHelper {
    private static final String TAG = "ThemeOverlayHelper";
    private static final String ANDROID_PACKAGE = "android";
    private static final String SYSUI_PACKAGE = "com.android.systemui";

    private final OverlayManagerInternal mOverlayManager;

    ThemeOverlayHelper(OverlayManagerInternal overlayManager) {
        mOverlayManager = overlayManager;
    }

    /**
     * Applies color overlays for a given user based on their current theme state.
     *
     * @param snapshot      The snapshot containing all necessary user, profile, and color info.
     * @param applyToSystem whenever to apply overlays to the system user as well.
     */
    public void applyCurrentStateOverlays(ThemeStatePair.OverlaySnapshot snapshot,
            boolean applyToSystem) throws CancellationException {

        final ColorScheme lightScheme = snapshot.lightScheme();
        final ColorScheme darkScheme = snapshot.darkScheme();
        final int userId = snapshot.userId();
        final Set<UserHandle> managedProfiles = snapshot.profiles()
                .stream()
                .map(UserHandle::of)
                .collect(Collectors.toSet());

        final FabricatedOverlay neutralOverlay = createNeutralOverlay(lightScheme, darkScheme);
        checkCancellation();
        final FabricatedOverlay accentOverlay = createAccentOverlay(lightScheme, darkScheme);
        checkCancellation();
        final FabricatedOverlay dynamicOverlay = createDynamicOverlay(lightScheme, darkScheme);
        checkCancellation();

        final List<FabricatedOverlay> overlays = List.of(neutralOverlay, accentOverlay,
                dynamicOverlay);

        final OverlayManagerTransaction.Builder transaction =
                new OverlayManagerTransaction.Builder();

        for (FabricatedOverlay overlay : overlays) {
            transaction.registerFabricatedOverlay(overlay);
            final OverlayIdentifier identifier = overlay.getIdentifier();

            Slog.d(TAG, "Enabling overlay " + identifier.getPackageName() + " for user " + userId);
            transaction.setEnabled(identifier, true, userId);

            // All generated color overlays must also be applied to the system user for SystemUI.
            if (applyToSystem && userId != UserHandle.SYSTEM.getIdentifier()) {
                transaction.setEnabled(identifier, true, UserHandle.SYSTEM.getIdentifier());
            }

            // And to all associated managed profiles.
            for (UserHandle userHandle : managedProfiles) {
                transaction.setEnabled(identifier, true, userHandle.getIdentifier());
            }
            checkCancellation();
        }

        try {
            mOverlayManager.commit(transaction.build());
        } catch (SecurityException | IllegalStateException e) {
            Slog.e(TAG, "Could not commit overlays to OverlayManager", e);
        }
    }

    private FabricatedOverlay createNeutralOverlay(ColorScheme lightColorScheme,
            ColorScheme darkColorScheme) {
        FabricatedOverlay overlay = newFabricatedOverlay("neutral");
        assignColorsToOverlay(overlay, DynamicColors.getAllNeutralPalette(), false,
                lightColorScheme, darkColorScheme);
        return overlay;
    }

    private FabricatedOverlay createAccentOverlay(ColorScheme lightColorScheme,
            ColorScheme darkColorScheme) {
        FabricatedOverlay overlay = newFabricatedOverlay("accent");
        assignColorsToOverlay(overlay, DynamicColors.getAllAccentPalette(), false,
                lightColorScheme, darkColorScheme);
        return overlay;
    }


    /**
     * Creates a fabricated overlay for dynamic colors.
     *
     * @param lightColorScheme The color scheme for light theme.
     * @param darkColorScheme The color scheme for dark theme.
     * @return A fabricated overlay containing dynamic colors.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public FabricatedOverlay createDynamicOverlay(ColorScheme lightColorScheme,
            ColorScheme darkColorScheme) {
        FabricatedOverlay overlay = newFabricatedOverlay("dynamic");

        //Themed Colors
        assignColorsToOverlay(overlay, DynamicColors.getAllDynamicColorsMapped(),
                false, lightColorScheme, darkColorScheme);
        // Fixed Colors
        assignColorsToOverlay(overlay, DynamicColors.getFixedColorsMapped(), true, lightColorScheme,
                darkColorScheme);
        //Custom Colors
        assignColorsToOverlay(overlay, DynamicColors.getCustomColorsMapped(), false,
                lightColorScheme, darkColorScheme);
        return overlay;
    }

    private void assignColorsToOverlay(FabricatedOverlay overlay,
            List<Pair<String, DynamicColor>> colors, Boolean isFixed,
            ColorScheme lightColorScheme, ColorScheme darkColorScheme) {
        for (Pair<String, DynamicColor> p : colors) {
            String prefix = "android:color/system_" + p.first;
            if (isFixed) {
                overlay.setResourceValue(prefix, TYPE_INT_COLOR_ARGB8,
                        p.second.getArgb(darkColorScheme.getMaterialScheme()), null);
                continue;
            }
            overlay.setResourceValue(prefix + "_light", TYPE_INT_COLOR_ARGB8,
                    p.second.getArgb(lightColorScheme.getMaterialScheme()), null);
            overlay.setResourceValue(prefix + "_dark", TYPE_INT_COLOR_ARGB8,
                    p.second.getArgb(darkColorScheme.getMaterialScheme()), null);
        }
    }

    private FabricatedOverlay newFabricatedOverlay(String name) {
        return new FabricatedOverlay.Builder(SYSUI_PACKAGE, name, ANDROID_PACKAGE).build();
    }

    private void checkCancellation() throws CancellationException {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Operation cancelled");
        }
    }
}
