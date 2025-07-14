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

package com.android.packageinstaller.v2.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.PackageUtil;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The utility of UI components.
 */
public class UiUtil {

    /**
     * If material design is enabled, return the material layout resource id. Otherwise, return the
     * default layout resource id.
     */
    public static int getInstallationLayoutResId(@NonNull Context context) {
        if (PackageUtil.isMaterialDesignEnabled(context)) {
            return R.layout.install_fragment_material_layout;
        } else {
            return R.layout.install_fragment_layout;
        }
    }

    /**
     * Gets the negative button in the {@code dialog}. Returns null if the specified
     * button does not exist or the dialog has not yet been fully created.
     */
    public static Button getAlertDialogNegativeButton(@NonNull Dialog dialog) {
        return getAlertiDialogButton(dialog, DialogInterface.BUTTON_NEGATIVE);
    }

    /**
     * Gets the positive button in the {@code dialog}. Returns null if the specified
     * button does not exist or the dialog has not yet been fully created.
     */
    public static Button getAlertDialogPositiveButton(@NonNull Dialog dialog) {
        return getAlertiDialogButton(dialog, DialogInterface.BUTTON_POSITIVE);
    }

    /**
     * Gets one of the buttons used in the {@code dialog}. Returns null if the specified
     * button does not exist or the dialog has not yet been fully created.
     *
     * @param whichButton The identifier of the button that should be returned.
     *                    For example, this can be {@link DialogInterface#BUTTON_POSITIVE}.
     * @return The button from the dialog, or null if a button does not exist.
     */
    @Nullable
    private static Button getAlertiDialogButton(@NonNull Dialog dialog, int whichButton) {
        if (dialog instanceof android.app.AlertDialog alertDialog) {
            return alertDialog.getButton(whichButton);
        } else if (dialog instanceof androidx.appcompat.app.AlertDialog alertDialog) {
            return alertDialog.getButton(whichButton);
        } else {
            return null;
        }
    }

    /**
     * Get the id of the title template of the alert dialog.
     */
    public static int getAlertDialogTitleTemplateId(@NonNull Context context) {
        if (PackageUtil.isMaterialDesignEnabled(context)) {
            return R.id.title_template;
        } else {
            return context.getResources().getIdentifier("title_template",
                    /* defType= */ "id", /* defPackage= */ "android");
        }
    }

    /**
     * If material design is enabled, return the MaterialAlertDialog. Otherwise, return the
     * system AlertDialog.
     */
    public static Dialog getAlertDialog(@NonNull Context context, @NonNull String title,
            @NonNull View contentView, @StringRes int positiveBtnTextResId,
            @StringRes int negativeBtnTextResId,
            @Nullable DialogInterface.OnClickListener positiveBtnListener,
            @Nullable DialogInterface.OnClickListener negativeBtnListener) {
        return getAlertDialog(context, title, contentView, positiveBtnTextResId,
                negativeBtnTextResId, positiveBtnListener, negativeBtnListener,
                /* themeResId= */ 0);
    }

    /**
     * If material design is enabled, return the MaterialAlertDialog. Otherwise, return the
     * system AlertDialog.
     */
    public static Dialog getAlertDialog(@NonNull Context context, @NonNull String title,
            @NonNull View contentView, @NonNull String positiveBtnText,
            @NonNull String negativeBtnText,
            @Nullable DialogInterface.OnClickListener positiveBtnListener,
            @Nullable DialogInterface.OnClickListener negativeBtnListener) {
        return getAlertDialog(context, title, contentView, positiveBtnText,
                negativeBtnText, positiveBtnListener, negativeBtnListener,
                /* themeResId= */ 0);
    }

    /**
     * If material design is enabled, return the MaterialAlertDialog. Otherwise, return the
     * system AlertDialog.
     */
    public static Dialog getAlertDialog(@NonNull Context context,
            @NonNull String title, @NonNull View contentView, @StringRes int positiveBtnTextResId,
            @StringRes int negativeBtnTextResId,
            @Nullable DialogInterface.OnClickListener positiveBtnListener,
            @Nullable DialogInterface.OnClickListener negativeBtnListener, int themeResId) {
        final String positiveBtnText =
                positiveBtnTextResId == Resources.ID_NULL ? null : context.getString(
                        positiveBtnTextResId);
        final String negativeBtnText =
                negativeBtnTextResId == Resources.ID_NULL ? null : context.getString(
                        negativeBtnTextResId);
        return getAlertDialog(context, title, contentView, positiveBtnText, negativeBtnText,
                positiveBtnListener, negativeBtnListener, themeResId);
    }

    /**
     * If material design is enabled, return the MaterialAlertDialog. Otherwise, return the
     * system AlertDialog.
     */
    public static Dialog getAlertDialog(@NonNull Context context,
            @NonNull String title, @NonNull View contentView, @Nullable String positiveBtnText,
            @Nullable String negativeBtnText,
            @Nullable DialogInterface.OnClickListener positiveBtnListener,
            @Nullable DialogInterface.OnClickListener negativeBtnListener, int themeResId) {
        if (PackageUtil.isMaterialDesignEnabled(context)) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context, themeResId)
                    .setTitle(title)
                    .setView(contentView);
            if (positiveBtnText != null) {
                builder.setPositiveButton(positiveBtnText, positiveBtnListener);
            }

            if (negativeBtnText != null) {
                builder.setNegativeButton(negativeBtnText, negativeBtnListener);
            }
            return builder.create();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(context, themeResId)
                    .setTitle(title)
                    .setView(contentView);
            if (positiveBtnText != null) {
                builder.setPositiveButton(positiveBtnText, positiveBtnListener);
            }

            if (negativeBtnText != null) {
                builder.setNegativeButton(negativeBtnText, negativeBtnListener);
            }
            return builder.create();
        }
    }

    /**
     * If material design is enabled, return the MaterialAlertDialog. Otherwise, return the
     * system AlertDialog.
     */
    public static Dialog getAlertDialog(@NonNull Context context, @NonNull View titleView,
            @NonNull View contentView, @StringRes int negativeBtnTextResId,
            @Nullable DialogInterface.OnClickListener negativeBtnListener) {
        if (PackageUtil.isMaterialDesignEnabled(context)) {
            return new MaterialAlertDialogBuilder(context)
                    .setCustomTitle(titleView)
                    .setView(contentView)
                    .setNegativeButton(negativeBtnTextResId, negativeBtnListener)
                    .create();
        } else {
            return new AlertDialog.Builder(context)
                    .setCustomTitle(titleView)
                    .setView(contentView)
                    .setNegativeButton(negativeBtnTextResId, negativeBtnListener)
                    .create();
        }
    }

    /**
     * Apply the filled button style to the {@code button}.
     *
     * @param context the context to get color
     */
    public static void applyFilledButtonStyle(@NonNull Context context,
            @NonNull Button button) {
        if (!PackageUtil.isMaterialDesignEnabled(context)) {
            return;
        }
        if (button instanceof MaterialButton materialButton) {
            materialButton.setBackgroundTintList(
                    ColorStateList.valueOf(context.getColor(R.color.primaryColor)));
            materialButton.setTextColor(ColorStateList.valueOf(
                    context.getColor(R.color.onPrimaryColor)));
            materialButton.setStrokeColor(
                    ColorStateList.valueOf(context.getColor(android.R.color.transparent)));
        }
    }

    /**
     * Apply the outlined button style to the {@code button}.
     *
     * @param context the context to get color
     */
    public static void applyOutlinedButtonStyle(@NonNull Context context,
            @NonNull Button button) {
        if (!PackageUtil.isMaterialDesignEnabled(context)) {
            return;
        }
        if (button instanceof MaterialButton materialButton) {
            materialButton.setBackgroundTintList(
                    ColorStateList.valueOf(context.getColor(android.R.color.transparent)));
            materialButton.setTextColor(
                    ColorStateList.valueOf(context.getColor(R.color.primaryColor)));
            materialButton.setStrokeColor(
                    ColorStateList.valueOf(context.getColor(R.color.outlineVariantColor)));
        }
    }

    /**
     * Apply the text button style to the {@code button}.
     *
     * @param context the context to get color
     */
    public static void applyTextButtonStyle(@NonNull Context context,
            @NonNull Button button) {
        if (!PackageUtil.isMaterialDesignEnabled(context)) {
            return;
        }
        if (button instanceof MaterialButton materialButton) {
            materialButton.setBackgroundTintList(
                    ColorStateList.valueOf(context.getColor(android.R.color.transparent)));
            materialButton.setTextColor(
                    ColorStateList.valueOf(context.getColor(R.color.primaryColor)));
            materialButton.setStrokeColor(
                    ColorStateList.valueOf(context.getColor(android.R.color.transparent)));
        }
    }
}
