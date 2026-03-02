package com.android.systemui.qs.pipeline.data.repository

import android.annotation.UserIdInt
import android.database.ContentObserver
import android.provider.Settings
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.Flags.hsuQsChanges
import com.android.systemui.common.coroutine.ConflatedCallbackFlow
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.panels.data.repository.QSPreferencesRepository
import com.android.systemui.qs.pipeline.data.model.RestoreData
import com.android.systemui.qs.pipeline.shared.InternetTileMigration
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.pipeline.shared.TilesUpgradePath
import com.android.systemui.qs.pipeline.shared.logging.QSPipelineLogger
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.user.domain.interactor.HeadlessSystemUserMode
import com.android.systemui.util.settings.SecureSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Single user version of [TileSpecRepository]. It provides a similar interface as
 * [TileSpecRepository], but focusing solely on the user it was created for.
 *
 * This is the source of truth for that user's tiles, after the user has been started. Persisting
 * all the changes to [Settings]. Changes in [Settings] that disagree with this repository will be
 * reverted
 *
 * All operations against [Settings] will be performed in a background thread.
 */
class UserTileSpecRepository
@AssistedInject
constructor(
    @Assisted private val userId: Int,
    private val defaultTilesRepository: DefaultTilesRepository,
    private val secureSettings: SecureSettings,
    private val hsum: HeadlessSystemUserMode,
    private val logger: QSPipelineLogger,
    private val qsPreferencesRepository: QSPreferencesRepository,
    private val internetTileMigration: InternetTileMigration,
    private val userRepository: UserRepository,
    @Application private val applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) {

    private val _tilesUpgradePath = Channel<TilesUpgradePath>(capacity = 3)
    val tilesUpgradePath: ReceiveChannel<TilesUpgradePath> = _tilesUpgradePath

    private val defaultTiles: List<TileSpec>
        get() = defaultTilesRepository.getDefaultTiles(isHeadlessSystemUser)

    private val changeEvents =
        MutableSharedFlow<ChangeAction>(extraBufferCapacity = CHANGES_BUFFER_SIZE)

    private var isHeadlessSystemUser = false

    private lateinit var _tiles: StateFlow<List<TileSpec>>

    suspend fun tiles(): Flow<List<TileSpec>> {
        if (!::_tiles.isInitialized) {
            withContext(backgroundDispatcher) {
                isHeadlessSystemUser = hsuQsChanges() && hsum.isHeadlessSystemUser(userId)
            }
            _tiles =
                changeEvents
                    .scan(loadTilesFromSettingsAndParse(userId).migrationSteps()) { current, change
                        ->
                        change
                            .apply(current)
                            .run { migrationSteps() }
                            .also { afterRestore ->
                                if (current != afterRestore) {
                                    if (change is RestoreTiles) {
                                        logger.logTilesRestoredAndReconciled(
                                            current,
                                            afterRestore,
                                            userId,
                                        )
                                    } else {
                                        logger.logProcessTileChange(change, afterRestore, userId)
                                    }
                                }
                                if (change is RestoreTiles) {
                                    _tilesUpgradePath.send(
                                        TilesUpgradePath.RestoreFromBackup(afterRestore.toSet())
                                    )
                                }
                            }
                            // Distinct preserves the order of the elements removing later
                            // duplicates,
                            // all tiles should be different
                            .distinct()
                    }
                    .flowOn(backgroundDispatcher)
                    .stateIn(applicationScope)
                    .also { startFlowCollections(it) }
        }
        return _tiles
    }

    private fun startFlowCollections(tiles: StateFlow<List<TileSpec>>) {
        applicationScope.launch(context = backgroundDispatcher) {
            launch { tiles.collect { storeTiles(userId, it) } }
            launch {
                // As Settings is not the source of truth, once we started tracking tiles for a
                // user, we don't want anyone to change the underlying setting. Therefore, if there
                // are any changes that don't match with the source of truth (this class), we
                // overwrite them with the current value.
                ConflatedCallbackFlow.conflatedCallbackFlow {
                        val observer =
                            object : ContentObserver(null) {
                                override fun onChange(selfChange: Boolean) {
                                    trySend(Unit)
                                }
                            }
                        secureSettings.registerContentObserverForUserSync(SETTING, observer, userId)
                        awaitClose { secureSettings.unregisterContentObserverSync(observer) }
                    }
                    .map { loadTilesFromSettings(userId) }
                    .flowOn(backgroundDispatcher)
                    .collect { setting ->
                        val current = tiles.value
                        if (setting != current) {
                            storeTiles(userId, current)
                        }
                    }
            }
        }
    }

    private suspend fun storeTiles(@UserIdInt forUser: Int, tiles: List<TileSpec>) {
        val toStore =
            tiles
                .filter { it !is TileSpec.Invalid }
                .joinToString(DELIMITER, transform = TileSpec::spec)
        withContext(backgroundDispatcher) {
            secureSettings.putStringForUser(SETTING, toStore, null, false, forUser, true)
        }
    }

    suspend fun addTile(tile: TileSpec, position: Int = TileSpecRepository.POSITION_AT_END) {
        if (tile is TileSpec.Invalid) {
            return
        }
        changeEvents.emit(AddTile(tile, position))
    }

    suspend fun removeTiles(tiles: Collection<TileSpec>) {
        changeEvents.emit(RemoveTiles(tiles))
    }

    suspend fun setTiles(tiles: List<TileSpec>) {
        changeEvents.emit(ChangeTiles(tiles))
    }

    suspend fun onPackageRemoved(packageName: String) {
        changeEvents.emit(PackageRemoved(packageName))
    }

    private fun parseTileSpecs(fromSettings: List<TileSpec>, user: Int): List<TileSpec> {
        return if (fromSettings.isNotEmpty()) {
            fromSettings.also { logger.logParsedTiles(it, false, user) }
        } else {
            defaultTiles.also { logger.logParsedTiles(it, true, user) }
        }
    }

    private suspend fun loadTilesFromSettingsAndParse(userId: Int): List<TileSpec> {
        val loadedTiles = loadTilesFromSettings(userId)
        if (loadedTiles.isNotEmpty()) {
            _tilesUpgradePath.send(TilesUpgradePath.ReadFromSettings(loadedTiles.toSet()))
        } else {
            _tilesUpgradePath.send(TilesUpgradePath.DefaultSet)
        }
        return parseTileSpecs(loadedTiles, userId)
    }

    private suspend fun loadTilesFromSettings(userId: Int): List<TileSpec> {
        return withContext(backgroundDispatcher) {
                secureSettings.getStringForUser(SETTING, userId) ?: ""
            }
            .toTilesList()
    }

    suspend fun reconcileRestore(restoreData: RestoreData, currentAutoAdded: Set<TileSpec>) {
        changeEvents.emit(RestoreTiles(restoreData, currentAutoAdded))
    }

    suspend fun prependDefault() {
        changeEvents.emit(PrependDefault(defaultTiles))
    }

    suspend fun resetToDefault(): List<TileSpec> {
        changeEvents.emit(ResetToDefault(defaultTiles))
        return defaultTiles
    }

    sealed interface ChangeAction {
        fun apply(currentTiles: List<TileSpec>): List<TileSpec>
    }

    private data class AddTile(
        val tileSpec: TileSpec,
        val position: Int = TileSpecRepository.POSITION_AT_END,
    ) : ChangeAction {
        override fun apply(currentTiles: List<TileSpec>): List<TileSpec> {
            val tilesList = currentTiles.toMutableList()
            if (tileSpec !in tilesList) {
                if (position < 0 || position >= tilesList.size) {
                    tilesList.add(tileSpec)
                } else {
                    tilesList.add(position, tileSpec)
                }
            }
            return tilesList
        }
    }

    private data class RemoveTiles(val tileSpecs: Collection<TileSpec>) : ChangeAction {
        override fun apply(currentTiles: List<TileSpec>): List<TileSpec> {
            return currentTiles.toMutableList().apply { removeAll(tileSpecs) }
        }
    }

    private data class ChangeTiles(val newTiles: List<TileSpec>) : ChangeAction {
        override fun apply(currentTiles: List<TileSpec>): List<TileSpec> {
            val new = newTiles.filter { it !is TileSpec.Invalid }
            return if (new.isNotEmpty()) new else currentTiles
        }
    }

    private data class PrependDefault(val defaultTiles: List<TileSpec>) : ChangeAction {
        override fun apply(currentTiles: List<TileSpec>): List<TileSpec> {
            return defaultTiles + currentTiles
        }
    }

    private data class ResetToDefault(val defaultTiles: List<TileSpec>) : ChangeAction {
        override fun apply(currentTiles: List<TileSpec>): List<TileSpec> {
            return defaultTiles
        }
    }

    private data class RestoreTiles(
        val restoreData: RestoreData,
        val currentAutoAdded: Set<TileSpec>,
    ) : ChangeAction {

        override fun apply(currentTiles: List<TileSpec>): List<TileSpec> {
            return reconcileTiles(currentTiles, currentAutoAdded, restoreData)
        }
    }

    private data class PackageRemoved(val packageName: String) : ChangeAction {
        override fun apply(currentTiles: List<TileSpec>): List<TileSpec> {
            return currentTiles.filterNot {
                it is TileSpec.CustomTileSpec && it.componentName.packageName == packageName
            }
        }
    }

    /**
     * Steps for migrating tiles in place.
     *
     * This is usually needed for a feature that requires a complex migration of tile specs (and
     * possibly other databases). This is applied as part of the flow construction for [tiles], so
     * that all downstream see the correct tiles. This is not exclusive to tiles that are new, it
     * could be used for existing tiles that require complex migration, based on the state of the
     * device beyond the current list of tiles.
     *
     * Each step should be a `List<TileSpec>.() -> List<TileSpec>`, mapping the list of tiles
     * pre-migration to the tiles post-migration. Be mindful (not completely banned) of side
     * effects. An example of a valid side effect is updating another related data source (e.g. the
     * set of large tiles).
     *
     * The migration step should provide the forward (when the flag is enabled) and backward (when
     * the flag is disabled) migration. The latter can be removed after the corresponding flag is
     * released.
     *
     * Steps should be idempotent, as they will be applied to every list, regardless of whether the
     * migration has happened for this user. This means that after a list is migrated, further
     * applications of the step should early return with no changes to the list and no side effects
     * run.
     */
    private fun List<TileSpec>.migrationSteps(): List<TileSpec> {
        return migrateInternetTileSpecs()
    }

    /**
     * Migration between internet <-> wifi + cell.
     *
     * The internet tile has been retired in favor of separate wifi and cell tiles. As the exact
     * migration for the list of tiles is dependent on the current set of large tiles, this has been
     * extracted into [migrateInternetTile] due to the complexity.
     *
     * It has side effects as the migration is tied to the size of the tiles.
     *
     * @see migrateInternetTile for the exact migration when the flag is enabled or disabled
     */
    private fun List<TileSpec>.migrateInternetTileSpecs(): List<TileSpec> {
        val largeTiles = qsPreferencesRepository.getLargeTilesForUser(userId)
        val isMainUser = userRepository.mainUserId == userId
        val (newTiles, newLargeTiles) =
            internetTileMigration.migrateInternetTile(this, largeTiles, isMainUser) { scenario ->
                logger.logInternetTileMigrated(userId, scenario)
            }
        if (newLargeTiles != largeTiles) {
            qsPreferencesRepository.setLargeTilesForUser(userId, newLargeTiles)
        }
        return newTiles.distinct()
    }

    companion object {
        private const val SETTING = Settings.Secure.QS_TILES
        private const val DELIMITER = TilesSettingConverter.DELIMITER
        // We want a small buffer in case multiple changes come in at the same time (sometimes
        // happens in first start. This should be enough to not lose changes.
        private const val CHANGES_BUFFER_SIZE = 10

        private fun String.toTilesList() = TilesSettingConverter.toTilesList(this)

        fun reconcileTiles(
            currentTiles: List<TileSpec>,
            currentAutoAdded: Set<TileSpec>,
            restoreData: RestoreData,
        ): List<TileSpec> {
            val toRestore = restoreData.restoredTiles.toMutableList()
            val freshlyAutoAdded =
                currentAutoAdded.filterNot { it in restoreData.restoredAutoAddedTiles }
            freshlyAutoAdded
                .filter { it in currentTiles && it !in restoreData.restoredTiles }
                .map { it to currentTiles.indexOf(it) }
                .sortedBy { it.second }
                .forEachIndexed { iteration, (tile, position) ->
                    val insertAt = position + iteration
                    if (insertAt > toRestore.size) {
                        toRestore.add(tile)
                    } else {
                        toRestore.add(insertAt, tile)
                    }
                }

            return toRestore
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(userId: Int): UserTileSpecRepository
    }
}
