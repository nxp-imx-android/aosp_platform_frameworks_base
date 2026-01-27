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

    private static final String OVERLAY_NAME_NEUTRAL = "neutral";
    private static final String OVERLAY_NAME_ACCENT = "accent";
    private static final String OVERLAY_NAME_DYNAMIC = "dynamic";

    // Order matters for consistency, though logically independent.
    private static final List<String> OVERLAY_NAMES = List.of(
            OVERLAY_NAME_NEUTRAL,
            OVERLAY_NAME_ACCENT,
            OVERLAY_NAME_DYNAMIC
    );

    private final OverlayManagerInternal mOverlayManager;

    ThemeOverlayHelper(OverlayManagerInternal overlayManager) {
        mOverlayManager = overlayManager;
    }

    /**
     * Applies color overlays for a given user based on their current theme state.
     *
     * @param snapshot       The snapshot containing all necessary user, profile, and color info.
     * @param applyToSystem  Whether to apply overlays to the system user as well.
     * @param shouldRegister Whether to register the overlays (true) or just enable them (false).
     */
    public void applyCurrentStateOverlays(ThemeStatePair.OverlaySnapshot snapshot,
            boolean applyToSystem, boolean shouldRegister) throws CancellationException {
        if (shouldRegister) {
            registerAndEnableOverlays(snapshot, applyToSystem);
        } else if (applyToSystem) {
            enableOverlaysOnly(snapshot);
        }
    }

    /**
     * The "Light" path: Only enables existing overlays.
     * Avoids expensive color calculation and object allocation.
     */
    private void enableOverlaysOnly(ThemeStatePair.OverlaySnapshot snapshot) {
        final int userId = snapshot.userId();

        Slog.d(TAG, "Enabling existing overlays for user " + userId);
        if (userId != UserHandle.SYSTEM.getIdentifier()) {
            Slog.d(TAG, "Enabling existing overlays for System User");
        }

        final OverlayManagerTransaction.Builder transaction =
                new OverlayManagerTransaction.Builder();

        for (String overlayName : OVERLAY_NAMES) {
            final OverlayIdentifier identifier = new OverlayIdentifier(SYSUI_PACKAGE,
                    overlayName + "_" + userId);
            addToTransaction(transaction, identifier, userId, /* applyToSystem */ true,
                    snapshot.profiles());
            checkCancellation();
        }

        commitTransaction(transaction);
    }

    /**
     * The "Heavy" path: Creates, Registers, and Enables overlays.
     */
    private void registerAndEnableOverlays(ThemeStatePair.OverlaySnapshot snapshot,
            boolean applyToSystem) {
        final int userId = snapshot.userId();
        final List<FabricatedOverlay> overlays = createOverlays(snapshot.lightScheme(),
                snapshot.darkScheme(), userId);

        final List<String> overlayNames = overlays.stream().map(
                o -> o.getIdentifier().getOverlayName()).collect(Collectors.toList());

        Slog.d(TAG, "Registering and Enabling overlays " + overlayNames + " for user " + userId);

        final OverlayManagerTransaction.Builder transaction =
                new OverlayManagerTransaction.Builder();

        for (FabricatedOverlay overlay : overlays) {
            transaction.registerFabricatedOverlay(overlay);
            addToTransaction(transaction, overlay.getIdentifier(), userId, applyToSystem,
                    snapshot.profiles());
            checkCancellation();
        }

        commitTransaction(transaction);
    }

    private void addToTransaction(OverlayManagerTransaction.Builder transaction,
            OverlayIdentifier identifier, int userId, boolean applyToSystem,
            Set<Integer> profileIds) {
        transaction.setEnabled(identifier, true, userId);

        // All generated color overlays must also be applied to the system user for SystemUI.
        if (applyToSystem && userId != UserHandle.SYSTEM.getIdentifier()) {
            transaction.setEnabled(identifier, true, UserHandle.SYSTEM.getIdentifier());
        }

        // And to all associated managed profiles.
        for (int profileId : profileIds) {
            transaction.setEnabled(identifier, true, profileId);
        }
    }

    private boolean commitTransaction(OverlayManagerTransaction.Builder transaction) {
        try {
            mOverlayManager.commit(transaction.build());
            return true;
        } catch (SecurityException | IllegalStateException e) {
            Slog.e(TAG, "Could not commit overlays to OverlayManager", e);
            return false;
        }
    }

    private List<FabricatedOverlay> createOverlays(ColorScheme light, ColorScheme dark,
            int userId) {
        return List.of(
                createOverlay(OVERLAY_NAME_NEUTRAL, DynamicColors.getAllNeutralPalette(), light,
                        dark, userId),
                createOverlay(OVERLAY_NAME_ACCENT, DynamicColors.getAllAccentPalette(), light, dark,
                        userId),
                createDynamicOverlay(light, dark, userId));
    }

    private FabricatedOverlay createOverlay(String name, List<Pair<String, DynamicColor>> colors,
            ColorScheme light, ColorScheme dark, int userId) {
        FabricatedOverlay overlay = newFabricatedOverlay(name, userId);
        assignColorsToOverlay(overlay, colors, false, light, dark);
        return overlay;
    }


    /**
     * Creates a fabricated overlay for dynamic colors.
     *
     * @param lightColorScheme The color scheme for light theme.
     * @param darkColorScheme  The color scheme for dark theme.
     * @return A fabricated overlay containing dynamic colors.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public FabricatedOverlay createDynamicOverlay(ColorScheme lightColorScheme,
            ColorScheme darkColorScheme, int userId) {
        FabricatedOverlay overlay = newFabricatedOverlay(OVERLAY_NAME_DYNAMIC, userId);

        //Themed Colors
        assignColorsToOverlay(overlay, DynamicColors.getAllDynamicColorsMapped(), false,
                lightColorScheme, darkColorScheme);
        // Fixed colors intentionally use only the lightscheme, hence the "fixed" in name.
        assignColorsToOverlay(overlay, DynamicColors.getFixedColorsMapped(), true, lightColorScheme,
                lightColorScheme);
        //Custom Colors
        assignColorsToOverlay(overlay, DynamicColors.getCustomColorsMapped(), false,
                lightColorScheme, darkColorScheme);
        return overlay;
    }

    private void assignColorsToOverlay(FabricatedOverlay overlay,
            List<Pair<String, DynamicColor>> colors, Boolean isFixed, ColorScheme lightColorScheme,
            ColorScheme darkColorScheme) {
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

    private FabricatedOverlay newFabricatedOverlay(String name, int userId) {
        return new FabricatedOverlay.Builder(SYSUI_PACKAGE, name + "_" + userId,
                ANDROID_PACKAGE).build();
    }

    private void checkCancellation() throws CancellationException {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Operation cancelled");
        }
    }
}