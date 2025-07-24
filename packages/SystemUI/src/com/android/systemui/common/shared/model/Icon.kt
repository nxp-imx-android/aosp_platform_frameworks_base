/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.common.shared.model

import android.annotation.DrawableRes
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.android.systemui.common.shared.model.Icon.Loaded

/**
 * Models an icon, that can either be already [loaded][Icon.Loaded] or be a [reference]
 * [Icon.Resource] to a resource. In case of [Loaded], the resource ID [res] is optional.
 */
@Stable
sealed class Icon {
    abstract val contentDescription: ContentDescription?

    /**
     * An icon that is already loaded.
     *
     * @param res The resource ID of the icon. For when we want to have Loaded icon, but still keep
     *   a reference to the resource id. A use case would be for tests that have to compare animated
     *   drawables. It should only be used for SystemUI/frameworks resources and not for other
     *   packages' resources as they may collide with SystemUI.
     */
    data class Loaded
    @JvmOverloads
    constructor(
        val drawable: Drawable,
        override val contentDescription: ContentDescription?,
        /**
         * Serves as an id to compare two instances. When provided this is used alongside
         * [contentDescription] to determine equality. This is useful when comparing icons
         * representing the same UI, but with different [drawable] instances.
         */
        @DrawableRes val res: Int? = null,
    ) : Icon() {

        override fun equals(other: Any?): Boolean {
            val that = other as? Loaded ?: return false

            if (this.res != null && that.res != null) {
                return this.res == that.res && this.contentDescription == that.contentDescription
            }

            return this.res == that.res &&
                this.drawable == that.drawable &&
                this.contentDescription == that.contentDescription
        }

        override fun hashCode(): Int {
            var result = contentDescription?.hashCode() ?: 0
            result =
                if (res != null) {
                    31 * result + res.hashCode()
                } else {
                    31 * result + drawable.hashCode()
                }
            return result
        }
    }

    data class Resource(
        @DrawableRes val res: Int,
        override val contentDescription: ContentDescription?,
    ) : Icon()
}

/**
 * Creates [Icon.Loaded] for a given drawable with an optional [contentDescription] and an optional
 * [res].
 */
fun Drawable.asIcon(
    contentDescription: ContentDescription? = null,
    @DrawableRes res: Int? = null,
): Loaded = Loaded(this, contentDescription, res)

/**
 * Creates [ImageBitmap] for a given [Icon.Loaded]. It avoids IllegalArgumentException by providing
 * 1x1 bitmap if [Drawable.getIntrinsicWidth] or [Drawable.getIntrinsicHeight] is <= 0
 */
fun Loaded.asImageBitmap(): ImageBitmap {
    return with(drawable) {
        if (this is BitmapDrawable) {
            bitmap.asImageBitmap()
        } else {
            toBitmap(
                    width = intrinsicWidth.takeIf { it > 0 } ?: 1,
                    height = intrinsicWidth.takeIf { it > 0 } ?: 1,
                )
                .asImageBitmap()
        }
    }
}
