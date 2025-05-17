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

package com.android.systemui.topwindoweffects.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface InvocationEffectPreferences {

    fun saveCurrentAssistant()

    fun saveCurrentUserId()

    fun setInvocationEffectEnabledByAssistant(enabled: Boolean)

    fun isInvocationEffectEnabledByAssistant(): Boolean

    fun setInwardAnimationPaddingDurationMillis(duration: Long)

    fun getInwardAnimationPaddingDurationMillis(): Long

    fun setOutwardAnimationDurationMillis(duration: Long)

    fun getOutwardAnimationDurationMillis(): Long

    fun registerOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)

    fun unregisterOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)
}

@SysUISingleton
class InvocationEffectPreferencesImpl
@Inject
constructor(@Application context: Context, @Background private val bgScope: CoroutineScope) :
    InvocationEffectPreferences {

    // TODO(b/33606670): Detect change in current active user and assistant
    private var activeUser: Int = Int.MIN_VALUE
    private var activeAssistant = ""

    private val sharedPreferences by lazy {
        context.getSharedPreferences(SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
    }

    override fun saveCurrentAssistant() {
        setInPreferences { putString(PERSISTED_FOR_ASSISTANT_PREFERENCE, activeAssistant) }
    }

    private fun getSavedAssistant(): String =
        getOrDefault<String>(
            key = PERSISTED_FOR_ASSISTANT_PREFERENCE,
            default = "",
            checkUserAndAssistant = false,
        )

    override fun saveCurrentUserId() {
        setInPreferences { putInt(PERSISTED_FOR_USER_PREFERENCE, activeUser) }
    }

    private fun getSavedUserId(): Int =
        getOrDefault<Int>(
            key = PERSISTED_FOR_USER_PREFERENCE,
            default = Integer.MIN_VALUE,
            checkUserAndAssistant = false,
        )

    override fun setInvocationEffectEnabledByAssistant(enabled: Boolean) {
        setInPreferences {
            putBoolean(IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE, enabled)
        }
    }

    override fun isInvocationEffectEnabledByAssistant(): Boolean =
        getOrDefault<Boolean>(
            key = IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE,
            default = true,
            checkUserAndAssistant = true,
        ) && activeAssistant.isNotEmpty()

    override fun setInwardAnimationPaddingDurationMillis(duration: Long) {
        setInPreferences {
            if (duration in 0..1000) {
                putLong(INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS, duration)
            } else {
                putLong(
                    INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS,
                    DEFAULT_INWARD_EFFECT_PADDING_DURATION_MS,
                )
            }
        }
    }

    override fun getInwardAnimationPaddingDurationMillis(): Long =
        getOrDefault<Long>(
            key = INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS,
            default = DEFAULT_INWARD_EFFECT_PADDING_DURATION_MS,
            checkUserAndAssistant = true,
        )

    // TODO(b/418685731): Should we have a positive non-zero min value for out effect duration?
    override fun setOutwardAnimationDurationMillis(duration: Long) {
        setInPreferences {
            if (duration in 0..1000) {
                putLong(INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS, duration)
            } else {
                putLong(
                    INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS,
                    DEFAULT_OUTWARD_EFFECT_DURATION_MS,
                )
            }
        }
    }

    override fun getOutwardAnimationDurationMillis(): Long =
        getOrDefault<Long>(
            key = INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS,
            default = DEFAULT_OUTWARD_EFFECT_DURATION_MS,
            checkUserAndAssistant = true,
        )

    private fun isCurrentUserAndAssistantPersisted(): Boolean =
        activeUser == getSavedUserId() && activeAssistant == getSavedAssistant()

    private fun setInPreferences(block: SharedPreferences.Editor.() -> Unit) {
        bgScope.launch { sharedPreferences.edit { block() } }
    }

    private inline fun <reified T> getOrDefault(
        key: String,
        default: T,
        checkUserAndAssistant: Boolean,
    ): T {
        val value: Any? =
            try {
                when (T::class) {
                    Int::class -> sharedPreferences.getInt(key, default as Int)
                    Long::class -> sharedPreferences.getLong(key, default as Long)
                    Boolean::class -> sharedPreferences.getBoolean(key, default as Boolean)
                    String::class -> sharedPreferences.getString(key, default as String)
                    else -> null
                }
            } catch (e: ClassCastException /* ignore */) {
                null
            }

        val result = value ?: default

        return if (checkUserAndAssistant) {
            if (isCurrentUserAndAssistantPersisted()) {
                result as T
            } else {
                default
            }
        } else {
            result as T
        }
    }

    override fun registerOnChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val TAG = "InvocationEffectPreferences"
        private const val SHARED_PREFERENCES_FILE_NAME = "assistant_invocation_effect_preferences"
        private const val PERSISTED_FOR_ASSISTANT_PREFERENCE = "persisted_for_assistant"
        private const val PERSISTED_FOR_USER_PREFERENCE = "persisted_for_user"
        const val IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE =
            "is_invocation_effect_enabled"
        const val INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS =
            "invocation_effect_animation_in_duration_padding_ms"
        const val INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS =
            "invocation_effect_animation_out_duration_ms"
        const val DEFAULT_INWARD_EFFECT_PADDING_DURATION_MS = 450L
        const val DEFAULT_OUTWARD_EFFECT_DURATION_MS = 400L
    }
}
