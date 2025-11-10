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

package com.android.settingslib.metadata

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.android.settingslib.catalyst.flags.Flags as CatalystFlags

/**
 * Coordinate to locate a preference.
 *
 * Within an app, the preference screen coordinate (unique among screens) plus preference key
 * (unique on the screen) is used to locate a preference.
 */
open class PreferenceCoordinate : PreferenceScreenCoordinate {
    val key: String

    constructor(screenKey: String, key: String) : super(screenKey) {
        this.key = key
    }

    @Deprecated("This constructor will be removed once the catalyst framework stops passing the arguments as a bundle. Use the other constructor instead.")
    constructor(screenKey: String, args: Bundle?, key: String) : super(screenKey, args) {
        this.key = key
    }

    constructor(screenKey: String, keyParameters: KeyParameters?, key: String) : super(screenKey, keyParameters) {
        this.key = key
    }

    constructor(parcel: Parcel) : super(parcel) {
        this.key = parcel.readString()!!
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        super.writeToParcel(parcel, flags)
        parcel.writeString(key)
    }

    override fun describeContents() = 0

    override fun equals(other: Any?) =
        super.equals(other) && key == (other as PreferenceCoordinate).key

    override fun hashCode() = super.hashCode() xor key.hashCode()

    companion object CREATOR : Parcelable.Creator<PreferenceCoordinate> {

        override fun createFromParcel(parcel: Parcel) = PreferenceCoordinate(parcel)

        override fun newArray(size: Int) = arrayOfNulls<PreferenceCoordinate>(size)
    }
}

/** Coordinate to locate a preference screen. */
open class PreferenceScreenCoordinate : Parcelable {
    /** Unique preference screen key. */
    val screenKey: String

    /** Arguments to create parameterized preference screen. */
    @Deprecated("Use keyParameters instead")
    val args: Bundle?

    /** Arguments to create parameterized preference screen. */
    val keyParameters: KeyParameters?

    constructor(screenKey: String) {
        this.screenKey = screenKey
        this.args = null
        this.keyParameters = null
    }

    @Deprecated("This constructor will be removed once the catalyst framework stops passing the arguments as a bundle. Use the other constructor instead.")
    constructor(screenKey: String, args: Bundle?) {
        this.screenKey = screenKey
        this.args = args
        this.keyParameters = null
    }

    constructor(screenKey: String, keyArguments: KeyParameters?) {
        this.screenKey = screenKey
        this.keyParameters = keyArguments
        this.args = null
    }

    constructor(parcel: Parcel) {
        screenKey = parcel.readString()!!
        args = parcel.readBundle(javaClass.classLoader)

        if (CatalystFlags.catalystUseKeyParameters()) {
            if (args != null) {
                val parametersSchema = PreferenceScreenRegistry.getScreenParametersSchema(screenKey)
                // TODO (b/452555836): create the keyParameters from the parcel string, not from the args bundle. Right now we create them from bundle because Devtool uses an older version of the SettingsLib.
                keyParameters = parametersSchema?.prepare(args)
            } else {
                keyParameters = null
            }
        } else {
            keyParameters = null
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(screenKey)
        parcel.writeBundle(args)

        if (CatalystFlags.catalystUseKeyParameters()) {
            // TODO (b/452555836): Write the keyParameters to parcel. Right now we create them from bundle because Devtool uses an older version of the SettingsLib.
            // parcel.writeString(keyParameters?.toParametersString() ?: "")
        }
    }

    override fun describeContents() = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PreferenceScreenCoordinate
        return screenKey == other.screenKey && args.contentEquals(other.args) && keyParameters == other.keyParameters
    }

    // "args" is not included intentionally, otherwise we need to take care of array, etc.
    override fun hashCode() = screenKey.hashCode()

    companion object CREATOR : Parcelable.Creator<PreferenceScreenCoordinate> {

        override fun createFromParcel(parcel: Parcel) = PreferenceScreenCoordinate(parcel)

        override fun newArray(size: Int) = arrayOfNulls<PreferenceScreenCoordinate>(size)
    }
}
