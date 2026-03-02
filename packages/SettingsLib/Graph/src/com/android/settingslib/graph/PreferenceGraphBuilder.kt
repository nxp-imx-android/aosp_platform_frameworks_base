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

@file:Suppress("DEPRECATION")

package com.android.settingslib.graph

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import com.android.settingslib.graph.PreferenceGetterFlags.forceIncludeAllScreens
import com.android.settingslib.graph.PreferenceGetterFlags.includeMetadata
import com.android.settingslib.graph.PreferenceGetterFlags.includeValue
import com.android.settingslib.graph.PreferenceGetterFlags.includeValueDescriptor
import com.android.settingslib.graph.proto.KeyParametersSchemaProto
import com.android.settingslib.graph.proto.ParameterDefinitionProto
import com.android.settingslib.graph.proto.PreferenceGraphProto
import com.android.settingslib.graph.proto.PreferenceGroupProto
import com.android.settingslib.graph.proto.PreferenceProto
import com.android.settingslib.graph.proto.PreferenceProto.ActionTarget
import com.android.settingslib.graph.proto.PreferenceScreenProto
import com.android.settingslib.graph.proto.PreferenceValueDescriptorProto
import com.android.settingslib.graph.proto.TextProto
import com.android.settingslib.metadata.CatalystFlagProviderFactory
import com.android.settingslib.metadata.EXTRA_BINDING_SCREEN_ARGS
import com.android.settingslib.metadata.IntRangeValuePreference
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.KEY_PACKAGE_NAME
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceRestrictionProvider
import com.android.settingslib.metadata.PreferenceScreenBindingKeyProvider
import com.android.settingslib.metadata.PreferenceScreenCoordinate
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.PreferenceScreenMetadataFactory
import com.android.settingslib.metadata.PreferenceScreenMetadataParameterizedFactory
import com.android.settingslib.metadata.PreferenceScreenRegistry
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.PreferenceTitleProvider
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel.Companion.DEEP_LINK_ONLY
import com.android.settingslib.metadata.SensitivityLevel.Companion.DO_NOT_EXPOSE
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.getPreferenceIcon
import com.android.settingslib.metadata.isPreferenceIndexable
import com.android.settingslib.metadata.isUiOnlyPreference
import com.android.settingslib.metadata.preferencesapi.ApiPreference
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreen
import com.android.settingslib.metadata.preferencesapi.types.ApiType
import com.android.settingslib.metadata.preferencesapi.types.FiniteOptionsType
import com.android.settingslib.metadata.preferencesapi.types.IntInRange
import com.android.settingslib.preference.PreferenceScreenCreator
import com.android.settingslib.preference.PreferenceScreenFactory
import com.android.settingslib.preference.PreferenceScreenProvider
import com.android.settingslib.utils.applications.AppUtils
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext

private const val TAG = "PreferenceGraphBuilder"

/** Builder of preference graph. */
class PreferenceGraphBuilder
private constructor(
    private val context: Context,
    private val callingPid: Int,
    private val callingUid: Int,
    private val request: GetPreferenceGraphRequest,
    private val coroutineScope: CoroutineScope,
) {
    private val preferenceScreenFactory by lazy {
        PreferenceScreenFactory(context.ofLocale(request.locale))
    }
    private val builder by lazy { PreferenceGraphProto.newBuilder() }
    private val valueDescriptors = mutableMapOf<String, PreferenceValueDescriptorProto>()
    private val visitedScreens = request.visitedScreens.toMutableSet()
    private val screens = mutableMapOf<String, PreferenceScreenProto.Builder>()
    private val forceIncludeAllScreens = request.flags.forceIncludeAllScreens()
    private val includeParameters = (request.flags and PreferenceGetterFlags.PARAMETERS) != 0
    private val includeHierarchy = (request.flags and PreferenceGetterFlags.EXCLUDE_HIERARCHY) == 0
    private val shrinkHierarchy = (request.flags and PreferenceGetterFlags.SHRINK_HIERARCHY) != 0
    private val excludeUiOnlyPreferences =
        !AppUtils.isDebuggable() ||
            Settings.Global.getInt(
                context.contentResolver,
                "com.android.settings.EXCLUDE_UI_ONLY_PREFERENCES",
                1,
            ) == 1

    private val HIERARCHY_CHILD_LIMIT = 50

    private suspend fun init() {
        val factories = PreferenceScreenRegistry.preferenceScreenMetadataFactories
        for (screen in request.screens) {
            val screenKey = screen.screenKey
            val factory = factories[screenKey] ?: continue
            val hasParameters =
                if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                    screen.keyParameters != null
                } else {
                    screen.args != null
                }
            if (!hasParameters && factory is PreferenceScreenMetadataParameterizedFactory) {
                addPreferenceScreen(screenKey, factory)
            } else {
                PreferenceScreenRegistry.create(context, screen)?.let { addPreferenceScreen(it) }
            }
        }
    }

    fun build(): PreferenceGraphProto {
        for ((key, screenBuilder) in screens) builder.putScreens(key, screenBuilder.build())
        builder.putAllValueDescriptors(valueDescriptors)
        return builder.build()
    }

    /**
     * Adds an activity to the graph.
     *
     * Reflection is used to create the instance. To avoid security vulnerability, the code ensures
     * given [activityClassName] must be declared as an <activity> entry in AndroidManifest.xml.
     */
    suspend fun add(activityClassName: String) {
        try {
            val intent = Intent()
            intent.setClassName(context, activityClassName)
            if (
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ==
                    null
            ) {
                Log.e(TAG, "$activityClassName is not activity")
                return
            }
            val activityClass = context.classLoader.loadClass(activityClassName)
            if (addPreferenceScreenKeyProvider(activityClass)) return
            if (PreferenceScreenProvider::class.java.isAssignableFrom(activityClass)) {
                addPreferenceScreenProvider(activityClass)
            } else {
                Log.w(TAG, "$activityClass does not implement PreferenceScreenProvider")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fail to add $activityClassName", e)
        }
    }

    private suspend fun addPreferenceScreenKeyProvider(activityClass: Class<*>): Boolean {
        if (!PreferenceScreenBindingKeyProvider::class.java.isAssignableFrom(activityClass)) {
            return false
        }
        val key = getPreferenceScreenKey { activityClass.newInstance() } ?: return false
        if (addPreferenceScreenFromRegistry(key)) {
            builder.addRoots(key)
            return true
        }
        return false
    }

    private suspend fun getPreferenceScreenKey(newInstance: () -> Any): String? =
        withContext(Dispatchers.Main) {
            try {
                val instance = newInstance()
                if (instance is PreferenceScreenBindingKeyProvider) {
                    return@withContext instance.getPreferenceScreenBindingKey(context)
                } else {
                    Log.w(TAG, "$instance is not PreferenceScreenKeyProvider")
                }
            } catch (e: Exception) {
                Log.e(TAG, "getPreferenceScreenKey failed", e)
            }
            null
        }

    private suspend fun addPreferenceScreenFromRegistry(key: String): Boolean {
        val factory =
            PreferenceScreenRegistry.preferenceScreenMetadataFactories[key] ?: return false
        return addPreferenceScreen(key, factory)
    }

    suspend fun addPreferenceScreenProvider(activityClass: Class<*>) {
        Log.d(TAG, "add $activityClass")
        createPreferenceScreen { activityClass.newInstance() }
            ?.let {
                addPreferenceScreen(Intent(context, activityClass), it)
                builder.addRoots(it.key)
            }
    }

    /**
     * Creates [PreferenceScreen].
     *
     * Androidx Activity/Fragment instance must be created in main thread, otherwise an exception is
     * raised.
     */
    private suspend fun createPreferenceScreen(newInstance: () -> Any): PreferenceScreen? =
        withContext(Dispatchers.Main) {
            try {
                val instance = newInstance()
                Log.d(TAG, "createPreferenceScreen $instance")
                if (instance is PreferenceScreenProvider) {
                    return@withContext instance.createPreferenceScreen(
                        preferenceScreenFactory,
                        coroutineScope,
                    )
                } else {
                    Log.w(TAG, "$instance is not PreferenceScreenProvider")
                }
            } catch (e: Exception) {
                Log.e(TAG, "createPreferenceScreen failed", e)
            }
            return@withContext null
        }

    private suspend fun addPreferenceScreen(intent: Intent, preferenceScreen: PreferenceScreen?) {
        val key = preferenceScreen?.key
        if (key.isNullOrEmpty()) {
            Log.e(TAG, "\"$preferenceScreen\" has no key")
            return
        }

        val args = preferenceScreen.peekExtras()?.getBundle(EXTRA_BINDING_SCREEN_ARGS)

        if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
            val parametersSchema = PreferenceScreenRegistry.getScreenParametersSchema(key)
            val keyParameters = args?.let { parametersSchema?.prepare(it) }

            addPreferenceScreenWithKeyParameters(key, keyParameters) {
                this.intent = intent.toProto()
                root = preferenceScreen.toProto()
            }
        } else {
            @Suppress("CheckReturnValue")
            addPreferenceScreen(key, args) {
                this.intent = intent.toProto()
                root = preferenceScreen.toProto()
            }
        }
    }

    @CanIgnoreReturnValue
    suspend fun addPreferenceScreen(
        screenKey: String,
        factory: PreferenceScreenMetadataFactory,
    ): Boolean {
        if (factory !is PreferenceScreenMetadataParameterizedFactory) {
            return addPreferenceScreen(factory.create(context))
        }
        if (visitedScreens.add(PreferenceScreenCoordinate(screenKey))) {
            val screen = screens.getOrPut(screenKey) { PreferenceScreenProto.newBuilder() }
            screen.root = preferenceGroupProto { preference = preferenceProto { key = screenKey } }
            screen.parameterized = true
            if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                screen.parametersSchema =
                    factory.parametersSchema.toProto(context, valueDescriptors)
            }
            if (includeParameters) {
                if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                    factory.keyParameters(context).collect {
                        screen.addKeyParameters(it.toProto()) }
                } else {
                    factory.parameters(context).collect { screen.addParameters(it.toProto()) }
                }
            }
        }
        if (includeHierarchy) {
            val takeCount = if (factory.parametersSchema.containsKey(KEY_PACKAGE_NAME)) 1 else HIERARCHY_CHILD_LIMIT
            var flagEnabled: Boolean? = null
            if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                factory.keyParameters(context).take(takeCount).collect {
                    if (flagEnabled == false) return@collect
                    val screenMetadata = factory.createWithKeyParameters(context, it)
                    if (flagEnabled == null) flagEnabled = checkScreenFlag(screenMetadata)
                    if (flagEnabled) addPreferenceScreen(screenMetadata)
                }
            } else {
                factory.parameters(context).take(takeCount).collect {
                    if (flagEnabled == false) return@collect
                    val screenMetadata = factory.create(context, it)
                    if (flagEnabled == null) flagEnabled = checkScreenFlag(screenMetadata)
                    if (flagEnabled) addPreferenceScreen(screenMetadata)
                }
            }
        }
        return true
    }

    @CanIgnoreReturnValue
    private suspend fun addPreferenceScreen(metadata: PreferenceScreenMetadata): Boolean {
        if (!checkScreenFlag(metadata)) return false

        return if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
            addPreferenceScreenWithKeyParameters(metadata.key, metadata.keyParameters) {
                completeHierarchy = metadata.hasCompleteHierarchy()
                root =
                    if (includeHierarchy) {
                        metadata
                            .getPreferenceHierarchy(context, coroutineScope)
                            .toProto(metadata, true)
                    } else {
                        preferenceGroupProto { preference = toProto(metadata, metadata, true) }
                    }
            }
        } else {
            addPreferenceScreen(metadata.key, metadata.arguments) {
                completeHierarchy = metadata.hasCompleteHierarchy()
                root =
                    if (includeHierarchy) {
                        metadata
                            .getPreferenceHierarchy(context, coroutineScope)
                            .toProto(metadata, true)
                    } else {
                        preferenceGroupProto { preference = toProto(metadata, metadata, true) }
                    }
            }
        }
    }

    private fun checkScreenFlag(metadata: PreferenceScreenMetadata): Boolean {
        val isFlagDisabled =
            when (metadata) {
                is PreferenceScreenCreator,
                is PreferencesApiScreen -> {
                    !metadata.isFlagEnabled(context)
                }
                else -> {
                    false
                }
            }

        if (!forceIncludeAllScreens && isFlagDisabled) {
            Log.w(TAG, "Ignore ${metadata.key} as the flag is disabled")
            return false
        }
        return true
    }

    @CanIgnoreReturnValue
    private suspend fun addPreferenceScreen(
        key: String,
        args: Bundle?,
        init: suspend PreferenceScreenProto.Builder.() -> Unit,
    ): Boolean {
        if (!visitedScreens.add(PreferenceScreenCoordinate(key, args))) return false
        fun newParameterizedScreenBuilder() =
            PreferenceScreenProto.newBuilder().also { it.parameterized = true }
        if (args == null) { // normal screen
            screens[key] = PreferenceScreenProto.newBuilder().also { init(it) }
        } else if (args.isEmpty) { // parameterized screen with backward compatibility
            val builder = screens.getOrPut(key) { newParameterizedScreenBuilder() }
            init(builder)
        } else { // parameterized screen with non-empty arguments
            val builder = screens.getOrPut(key) { newParameterizedScreenBuilder() }
            val parameterizedScreen = parameterizedPreferenceScreenProto {
                setArgs(args.toProto())
                setScreen(newParameterizedScreenBuilder().also { init(it) })
            }
            builder.addParameterizedScreens(parameterizedScreen)
        }
        return true
    }

    @CanIgnoreReturnValue
    private suspend fun addPreferenceScreenWithKeyParameters(
        key: String,
        keyParameters: ValidatedKeyParameters?,
        init: suspend PreferenceScreenProto.Builder.() -> Unit,
    ): Boolean {
        if (!visitedScreens.add(PreferenceScreenCoordinate(key, keyParameters))) return false

        fun newParameterizedScreenBuilder() =
            PreferenceScreenProto.newBuilder().also {
                it.parameterized = true
                PreferenceScreenRegistry.getScreenParametersSchema(key)?.let { schema ->
                    it.parametersSchema = schema.toProto(context, valueDescriptors)
                }
            }

        if (keyParameters == null) { // normal screen
            screens[key] = PreferenceScreenProto.newBuilder().also { init(it) }
        } else if (keyParameters.isEmpty) { // parameterized screen with backward compatibility
            val builder = screens.getOrPut(key) { newParameterizedScreenBuilder() }
            init(builder)
        } else { // parameterized screen with non-empty arguments
            val builder = screens.getOrPut(key) { newParameterizedScreenBuilder() }
            val parameterizedScreen = parameterizedPreferenceScreenProto {
                setKeyParameters(keyParameters.toProto())
                setScreen(newParameterizedScreenBuilder().also { init(it) })
            }
            builder.addParameterizedScreens(parameterizedScreen)
        }
        return true
    }

    private suspend fun PreferenceGroup.toProto(): PreferenceGroupProto = preferenceGroupProto {
        preference = (this@toProto as Preference).toProto()
        for (index in 0 until preferenceCount) {
            val child = getPreference(index)
            addPreferences(
                preferenceOrGroupProto {
                    if (child is PreferenceGroup) {
                        group = child.toProto()
                    } else {
                        preference = child.toProto()
                    }
                }
            )
        }
    }

    private suspend fun Preference.toProto(): PreferenceProto = preferenceProto {
        this@toProto.key?.let { key = it }
        this@toProto.title?.let { title = textProto { string = it.toString() } }
        this@toProto.summary?.let { summary = textProto { string = it.toString() } }
        val preferenceExtras = peekExtras()
        preferenceExtras?.let { extras = it.toProto() }
        enabled = isEnabled
        available = isVisible
        persistent = isPersistent
        if (request.flags.includeValue() && isPersistent && this@toProto is TwoStatePreference) {
            value = preferenceValueProto { booleanValue = this@toProto.isChecked }
        }
        this@toProto.fragment.toActionTarget(preferenceExtras)?.let {
            actionTarget = it
            return@preferenceProto
        }
        this@toProto.intent?.let { actionTarget = it.toActionTarget() }
    }

    private suspend fun PreferenceHierarchy.toProto(
        screenMetadata: PreferenceScreenMetadata,
        isRoot: Boolean,
    ): PreferenceGroupProto = preferenceGroupProto {
        if (!excludeUiOnlyPreferences || !this@toProto.metadata.isUiOnlyPreference(context)) {
            preference = toProto(screenMetadata, this@toProto.metadata, isRoot)
        }
        forEachAsync {
            addPreferences(
                preferenceOrGroupProto {
                    if (it is PreferenceHierarchy) {
                        group = it.toProto(screenMetadata, false)
                    } else {
                        if (!excludeUiOnlyPreferences || !it.metadata.isUiOnlyPreference(context)) {
                            preference = toProto(screenMetadata, it.metadata, false)
                        }
                    }
                }
            )
        }
    }

    private suspend fun toProto(
        screenMetadata: PreferenceScreenMetadata,
        metadata: PreferenceMetadata,
        isRoot: Boolean,
    ) =
        try {
            metadata
                .toProto(
                    context,
                    callingPid,
                    callingUid,
                    screenMetadata,
                    isRoot,
                    request.flags,
                    valueDescriptors,
                )
                .also {
                    if (!isRoot && shrinkHierarchy) return@also
                    if (metadata is PreferenceScreenMetadata) {
                        @Suppress("CheckReturnValue") addPreferenceScreen(metadata)
                    }
                    metadata.intent(context)?.resolveActivity(context.packageManager)?.let {
                        if (it.packageName == context.packageName) {
                            add(it.className)
                        }
                    }
                }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Fail to convert $screenMetadata $metadata", e)
            throw e
        }

    private suspend fun String?.toActionTarget(extras: Bundle?): ActionTarget? {
        if (this.isNullOrEmpty()) return null
        try {
            val fragmentClass = context.classLoader.loadClass(this)
            if (Fragment::class.java.isAssignableFrom(fragmentClass)) {
                @Suppress("UNCHECKED_CAST")
                return (fragmentClass as Class<out Fragment>).toActionTarget(extras)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot loadClass $this", e)
        }
        return null
    }

    private suspend fun Class<out Fragment>.toActionTarget(extras: Bundle?): ActionTarget? {
        if (
            !PreferenceScreenProvider::class.java.isAssignableFrom(this) &&
                !PreferenceScreenBindingKeyProvider::class.java.isAssignableFrom(this)
        ) {
            return null
        }
        val fragment =
            withContext(Dispatchers.Main) {
                return@withContext try {
                    newInstance().apply { arguments = extras }
                } catch (e: Exception) {
                    Log.e(TAG, "Fail to instantiate fragment ${this@toActionTarget}", e)
                    null
                }
            }
        if (fragment is PreferenceScreenBindingKeyProvider) {
            val screenKey = fragment.getPreferenceScreenBindingKey(context)
            if (screenKey != null && addPreferenceScreenFromRegistry(screenKey)) {
                return actionTargetProto { key = screenKey }
            }
        }
        if (fragment is PreferenceScreenProvider) {
            try {
                val screen =
                    fragment.createPreferenceScreen(preferenceScreenFactory, coroutineScope)
                val screenKey = screen?.key
                if (!screenKey.isNullOrEmpty()) {
                    if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                        addPreferenceScreenWithKeyParameters(screenKey, null) {
                            root = screen.toProto()
                        }
                    } else {
                        @Suppress("CheckReturnValue")
                        addPreferenceScreen(screenKey, null) { root = screen.toProto() }
                    }
                    return actionTargetProto { key = screenKey }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fail to createPreferenceScreen for $fragment", e)
            }
        }
        return null
    }

    private suspend fun Intent.toActionTarget() =
        toActionTarget(context).also {
            resolveActivity(context.packageManager)?.let {
                if (it.packageName == context.packageName) {
                    add(it.className)
                }
            }
        }

    companion object {
        suspend fun of(
            context: Context,
            callingPid: Int,
            callingUid: Int,
            request: GetPreferenceGraphRequest,
            coroutineScope: CoroutineScope,
        ) =
            PreferenceGraphBuilder(context, callingPid, callingUid, request, coroutineScope).also {
                it.init()
            }
    }
}

fun PreferenceMetadata.toProto(
    context: Context,
    callingPid: Int,
    callingUid: Int,
    screenMetadata: PreferenceScreenMetadata,
    isRoot: Boolean,
    flags: Int,
    valueDescriptors: MutableMap<String, PreferenceValueDescriptorProto>? = null,
) = preferenceProto {
    val metadata = this@toProto
    key = metadata.bindingKey
    if (flags.includeMetadata()) {
        metadata.getTitleTextProto(context, isRoot)?.let { title = it }
        if (metadata.summary != 0) {
            summary = textProto { resourceId = metadata.summary }
        } else {
            (metadata as? PreferenceSummaryProvider)?.getSummary(context)?.let {
                summary = textProto { string = it.toString() }
            }
        }
        val metadataIcon = metadata.getPreferenceIcon(context)
        writable =
            if (metadata is ApiPreference<*>) {
                metadata.set != null
            } else {
                false // Legacy preferences are not writable
            }

        if (metadataIcon != 0) icon = metadataIcon
        if (metadata.keywords != 0) keywords = metadata.keywords
        val preferenceExtras = metadata.extras(context)
        preferenceExtras?.let { extras = it.toProto() }
        indexable = metadata.isPreferenceIndexable(context)
        enabled = metadata.isEnabled(context)
        if (metadata is PreferenceAvailabilityProvider) {
            available = metadata.isAvailable(context)
        }
        if (metadata is PreferenceRestrictionProvider) {
            restricted = metadata.isRestricted(context)
        }
        if (metadata is PreferenceScreenMetadata) {
            actionTarget = actionTargetProto {
                key = metadata.key
                if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                    metadata.keyParameters?.let { keyParameters = it.toProto() }
                } else {
                    metadata.arguments?.let { args = it.toProto() }
                }
            }
        } else {
            metadata.intent(context)?.let { actionTarget = it.toActionTarget(context) }
        }

        if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
            if (metadata is PreferenceScreenMetadata) {
                metadata.keyParametersSchema?.let {
                    parametersSchema = it.toProto(context, valueDescriptors)
                }
                metadata.keyParameters?.let { keyParameters = it.toProto() }
            } else if (metadata is ApiPreference<*>) {
                metadata.getParametersSchema()?.let {
                    parametersSchema = it.toProto(context, valueDescriptors)
                }
                metadata.getParameters()?.let { keyParameters = it.toProto() }
            }
        }

        val launchTarget = if (screenMetadata != metadata) metadata else null
        screenMetadata.getLaunchIntent(context, launchTarget)?.let { launchIntent = it.toProto() }
        for (tag in metadata.tags(context)) addTags(tag)
    }
    purpose = metadata.purpose
    if (metadata is ApiPreference<*>) {
        metadata.screenPreconditions?.getDescription(context)?.let { addGetPreconditions(it) }
        metadata.preconditions?.getDescription(context)?.let { addGetPreconditions(it) }
        metadata.get.preconditions?.getDescription(context)?.let { addGetPreconditions(it) }
        metadata.set?.preconditions?.getDescription(context)?.let { addSetPreconditions(it) }
        metadata.set?.valuePreconditions?.getDescription(context)?.let { addSetPreconditions(it) }
    } else if (metadata is PreferencesApiScreen) {
        metadata.screenPreconditions?.getDescription(context)?.let { addGetPreconditions(it) }
    }
    persistent = metadata.isPersistent(context)
    if (metadata !is PersistentPreference<*>) return@preferenceProto
    sensitivityLevel = metadata.sensitivityLevel
    metadata.getReadPermissions(context)?.let { if (it.size > 0) readPermissions = it.toProto() }
    metadata.getWritePermissions(context)?.let { if (it.size > 0) writePermissions = it.toProto() }
    val readPermit = metadata.evalReadPermit(context, callingPid, callingUid)
    val writePermit =
        metadata.evalWritePermit(context, callingPid, callingUid) ?: ReadWritePermit.ALLOW
    readWritePermit = ReadWritePermit.make(readPermit, writePermit)
    if (
        flags.includeValue() &&
            enabled &&
            (!hasAvailable() || available) &&
            (!hasRestricted() || !restricted) &&
            readPermit == ReadWritePermit.ALLOW
    ) {
        val storage = metadata.storage(context)
        value = preferenceValueProto {
            val key = metadata.bindingKey
            when (metadata.valueType) {
                Int::class.java,
                Int::class.javaObjectType -> storage.getInt(key)?.let { intValue = it }
                Boolean::class.java,
                Boolean::class.javaObjectType -> storage.getBoolean(key)?.let { booleanValue = it }
                Float::class.java,
                Float::class.javaObjectType -> storage.getFloat(key)?.let { floatValue = it }
                Long::class.java,
                Long::class.javaObjectType -> storage.getLong(key)?.let { longValue = it }
                String::class.java,
                String::class.javaObjectType -> storage.getString(key)?.let { stringValue = it }
                else -> error("Error: Unsupported type ${metadata.valueType}")
            }
        }
    }
    if (flags.includeValueDescriptor()) {
        if (metadata is ApiPreference<*>) {
            valueDescriptor = metadata.type.toProto(context, valueDescriptors)
        } else {
            valueDescriptor = preferenceValueDescriptorProto {
                if (metadata is IntRangeValuePreference) {
                    rangeValue = rangeValueProto {
                        min = metadata.getMinValue(context)
                        max = metadata.getMaxValue(context)
                        step = metadata.getIncrementStep(context)
                    }
                }
                when (metadata.valueType) {
                    Int::class.java,
                    Int::class.javaObjectType -> {
                        if (!hasRangeValue()) {
                            rangeValue = rangeValueProto {}
                        }
                    }
                    Boolean::class.java,
                    Boolean::class.javaObjectType -> booleanType = true
                    Float::class.java,
                    Float::class.javaObjectType -> floatType = true
                    Long::class.java,
                    Long::class.javaObjectType -> longType = true
                    String::class.java,
                    String::class.javaObjectType -> stringType = true
                    else -> error("Error: Unsupported type ${metadata.valueType}")
                }
            }
        }
    }
}

/** Evaluates the read permit of a persistent preference. */
fun <T> PersistentPreference<T>.evalReadPermit(
    context: Context,
    callingPid: Int,
    callingUid: Int,
): Int =
    when {
        getReadPermissions(context)?.check(context, callingPid, callingUid) == false ->
            ReadWritePermit.REQUIRE_APP_PERMISSION
        else -> getReadPermit(context, callingPid, callingUid)
    }

/** Evaluates the write permit of a persistent preference. */
fun <T> PersistentPreference<T>.evalWritePermit(
    context: Context,
    callingPid: Int,
    callingUid: Int,
): Int? {
    val isDebuggable = AppUtils.isDebuggable()

    // Use the global setting as a gate for debug environments
    val hasUnknownSensitivitySettings =
        Settings.Global.getInt(
            context.contentResolver,
            "com.android.settings.UNKNOWN_SENSITIVITY_IS_AVAILABLE",
            0,
        ) == 1

    return when {
        // High sensitivity is strictly disallowed.
        sensitivityLevel == DEEP_LINK_ONLY -> ReadWritePermit.DISALLOW

        // Unknown sensitivity is disallowed, unless we are on a debuggable build
        // and the caller holds the WRITE_SECURE_SETTINGS permission.
        sensitivityLevel == DO_NOT_EXPOSE &&
            !(isDebuggable && hasUnknownSensitivitySettings) -> ReadWritePermit.DISALLOW

        // If the app lacks the required permissions, require them.
        getWritePermissions(context)?.check(context, callingPid, callingUid) == false ->
            ReadWritePermit.REQUIRE_APP_PERMISSION

        // Otherwise, delegate to the specific permit logic.
        else -> getWritePermit(context, callingPid, callingUid)
    }
}

private fun PreferenceMetadata.getTitleTextProto(context: Context, isRoot: Boolean): TextProto? {
    if (isRoot && this is PreferenceScreenMetadata) {
        val titleRes = screenTitle
        if (titleRes != 0) {
            return textProto { resourceId = titleRes }
        } else {
            getScreenTitle(context)?.let {
                return textProto { string = it.toString() }
            }
        }
    } else {
        val titleRes = title
        if (titleRes != 0) {
            return textProto { resourceId = titleRes }
        }
    }
    return (this as? PreferenceTitleProvider)?.getTitle(context)?.let {
        textProto { string = it.toString() }
    }
}

private fun Intent.toActionTarget(context: Context): ActionTarget {
    if (component?.packageName == "") {
        setClassName(context, component!!.className)
    }
    return actionTargetProto { intent = toProto() }
}

private fun KeyParametersSchema.toProto(
    context: Context,
    valueDescriptors: MutableMap<String, PreferenceValueDescriptorProto>? = null,
): KeyParametersSchemaProto {
    val builder = KeyParametersSchemaProto.newBuilder()
    getParameters().forEach { (name, definition) ->
        val schemaMap = definition.toParameterSchemaMap(context)
        val purpose = schemaMap[KeyParametersSchema.ParameterDefinition.PURPOSE_KEY] as? String
        val required =
            schemaMap[KeyParametersSchema.ParameterDefinition.REQUIRED_KEY] as? Boolean ?: false
        val paramProto = ParameterDefinitionProto.newBuilder().setRequired(required)
        purpose?.let { paramProto.setPurpose(it) }

        paramProto.setValueDescriptor(definition.type.toProto(context, valueDescriptors))

        builder.putParameters(name, paramProto.build())
    }
    return builder.build()
}

private fun ApiType<*>.toProto(
    context: Context,
    valueDescriptors: MutableMap<String, PreferenceValueDescriptorProto>?,
): PreferenceValueDescriptorProto {
    val descriptorKey = getKey()

    fun PreferenceValueDescriptorProto.Builder.setType() {
        if (this@toProto is IntInRange) {
            rangeValue = rangeValueProto {
                this@toProto.min?.let { min = it }
                this@toProto.max?.let { max = it }
                this@toProto.step.let { step = it }
            }
        }
        when (val valueType = this@toProto.getType()) {
            Int::class.java,
            Int::class.javaObjectType -> {
                if (!hasRangeValue()) {
                    rangeValue = rangeValueProto {}
                }
            }
            Boolean::class.java,
            Boolean::class.javaObjectType -> booleanType = true
            Float::class.java,
            Float::class.javaObjectType -> floatType = true
            Long::class.java,
            Long::class.javaObjectType -> longType = true
            String::class.java,
            String::class.javaObjectType -> stringType = true
            else -> error("Error: Unsupported type $valueType")
        }
    }

    fun createFullDescriptor() = preferenceValueDescriptorProto {
        valueDescriptorKey = descriptorKey
        description = this@toProto.getDescription(context)
        this@toProto.getParametersSchema()?.let {
            parametersSchema = it.toProto(context, valueDescriptors)
        }
        this@toProto.getParameters()?.let { parameters = it.toProto() }

        setType()

        if (this@toProto is FiniteOptionsType<*>) {
            this@toProto.getOptions(context).forEach {
                addPossibleValues(
                    possibleValueProto {
                        value = preferenceValueProto {
                            when (this@toProto.getType()) {
                                Int::class.java,
                                Int::class.javaObjectType -> intValue = it.first as Int
                                Boolean::class.java,
                                Boolean::class.javaObjectType -> booleanValue = it.first as Boolean
                                Float::class.java,
                                Float::class.javaObjectType -> floatValue = it.first as Float
                                Long::class.java,
                                Long::class.javaObjectType -> longValue = it.first as Long
                                String::class.java,
                                String::class.javaObjectType -> stringValue = it.first as String
                                else -> error("Error: Unsupported type ${this@toProto.getType()}")
                            }
                        }
                        description = it.second
                    }
                )
            }
        }
    }

    if (valueDescriptors != null) {
        valueDescriptors.getOrPut(descriptorKey) { createFullDescriptor() }
        return preferenceValueDescriptorProto {
            valueDescriptorKey = descriptorKey
            setType()
        }
    } else {
        return createFullDescriptor()
    }
}

@SuppressLint("AppBundleLocaleChanges")
internal fun Context.ofLocale(locale: Locale?): Context {
    if (locale == null) return this
    val baseConfig: Configuration = resources.configuration
    val baseLocale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            baseConfig.locales[0]
        } else {
            baseConfig.locale
        }
    if (locale == baseLocale) {
        return this
    }
    val newConfig = Configuration(baseConfig)
    newConfig.setLocale(locale)
    return createConfigurationContext(newConfig)
}
