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

package com.android.internal.inputmethod;

import android.content.Intent;

import com.android.internal.inputmethod.IImeSwitcherMenuListener;

/**
 * Interface to send calls to the IME Switcher Menu controller.
 */
oneway interface IImeSwitcherMenu {

    /** An item in the IME Switcher Menu list. */
    parcelable Item {

        /** The input method's name. */
        CharSequence imeName;

        /** The subtype's name, or {@code null} if this item doesn't have a subtype. */
        @nullable
        CharSequence subtypeName;

        /**
         * The subtype's layout name, or {@code null} if this item doesn't have a subtype, or
         * doesn't specify a layout.
         */
        @nullable
        CharSequence layoutName;

        /** The ID of the Input Method associated with this item. */
        String imeId;

        /**
         * The index of the subtype in the input method's array of subtypes, or {@code -1} if this
         * item doesn't have a subtype.
         */
        int subtypeIndex;
    }

    /**
     * Shows the Input Method Switcher Menu, with a list of IMEs and their subtypes.
     *
     * @param items                     the list of input method and subtype items.
     * @param selectedImeId             the ID of the selected input method.
     * @param selectedSubtypeIndex      the index of the selected subtype in the input method's
     *                                  array of subtypes, or {@code -1} if no subtype is selected.
     * @param selectedImeSettingsIntent the intent for the settings activity of the selected IME, or
     *                                  {@code null} if no IME is selected, or the selected IME does
     *                                  not have a settings activity.
     * @param isScreenLocked            whether the screen is current locked.
     * @param displayId                 the ID of the display where the menu was requested.
     * @param userId                    the ID of the user that requested the menu.
     */
    void show(in List<Item> items, in @nullable String selectedImeId, int selectedSubtypeIndex,
            in @nullable Intent selectedImeSettingsIntent, boolean isScreenLocked, int displayId,
            int userId);

    /**
     * Hides the Input Method Switcher Menu.
     *
     * @param displayId the ID of the display from where the menu should be hidden.
     * @param userId    the ID of the user for which the menu should be hidden.
     */
    void hide(int displayId, int userId);

    /**
     * Registers an interface to receive callbacks from the IME Switcher Menu controller.
     *
     * @param listener the listener to receive callbacks on.
     */
    void registerListener(in IImeSwitcherMenuListener listener);
}
