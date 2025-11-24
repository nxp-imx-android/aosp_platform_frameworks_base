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
package com.android.settingslib.widget

import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.theme.R

/**
 * Holds the configuration for a single preference footer.
 */
data class FooterData(
    val text: CharSequence,
    val listener: View.OnClickListener,
)

/**
 * Finds or creates the footer view and binds the provided data to it.
 * Handles view recycling by explicitly showing/hiding the footer.
 */
fun bindFooter(holder: PreferenceViewHolder, data: FooterData?) {
    val footerView = holder.itemView.findViewById<TextView>(R.id.settingslib_expressive_link_footer)

    footerView?.apply {
        val shouldShowFooter = data != null && data.text.isNotEmpty()
        if (shouldShowFooter) {
            text = data.text
            setOnClickListener(data.listener)
            movementMethod = LinkMovementMethod.getInstance()
            visibility = View.VISIBLE

            val icon = holder.itemView.findViewById<View>(android.R.id.icon)
            if (icon != null && icon.isVisible) {
                setPaddingRelative(
                    resources.getDimensionPixelSize(R.dimen.settingslib_expressive_preference_footer_padding_start),
                    paddingTop,
                    paddingEnd,
                    paddingBottom
                )
            } else {
                setPaddingRelative(
                    0,
                    paddingTop,
                    paddingEnd,
                    paddingBottom
                )
            }
        } else {
            visibility = View.GONE
        }
    }
}
