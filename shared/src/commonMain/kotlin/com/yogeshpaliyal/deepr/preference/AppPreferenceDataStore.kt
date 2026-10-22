package com.yogeshpaliyal.deepr.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yogeshpaliyal.deepr.ui.screens.home.ViewType
import com.yogeshpaliyal.deepr.viewmodel.SortType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class AppPreferenceDataStore(
    private val dataStore: DataStore<Preferences>,
) : PreferenceRepository {
    companion object {
        private val SORTING_ORDER = stringPreferencesKey("sorting_order")
        private val USE_LINK_BASED_ICONS = booleanPreferencesKey("use_link_based_icons")
        private val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        private val SYNC_FILE_PATH = stringPreferencesKey("sync_file_path")
        private val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        private val LANGUAGE_CODE = stringPreferencesKey("language_code")
        private val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val AUTO_BACKUP_LOCATION = stringPreferencesKey("auto_backup_location")
        private val AUTO_BACKUP_INTERVAL = longPreferencesKey("auto_backup_interval")
        private val LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
        private val DEFAULT_PAGE_FAVOURITES = booleanPreferencesKey("default_page_favourites")
        private val IS_THUMBNAIL_ENABLE = booleanPreferencesKey("is_thumbnail_enable")
        private val SERVER_PORT = stringPreferencesKey("server_port")
        private val VIEW_TYPE = intPreferencesKey("view_type")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val SHOW_OPEN_COUNTER = booleanPreferencesKey("show_open_counter")
        private val SELECTED_PROFILE_ID = longPreferencesKey("selected_profile_id")
        private val SILENT_SAVE_PROFILE_ID = longPreferencesKey("silent_save_profile_id")
        private val GOOGLE_DRIVE_AUTO_BACKUP_ENABLED =
            booleanPreferencesKey("google_drive_auto_backup_enabled")
        private val CLIPBOARD_LINK_DETECTION_ENABLED =
            booleanPreferencesKey("clipboard_link_detection_enabled")
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    }

    override val getSortingOrder: Flow<@SortType String> =
        dataStore.data.map { preferences ->
            preferences[SORTING_ORDER] ?: SortType.SORT_CREATED_BY_DESC
        }

    override val viewType: Flow<@ViewType Int> =
        dataStore.data.map { preferences ->
            preferences[VIEW_TYPE] ?: ViewType.LIST
        }

    override val getUseLinkBasedIcons: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[USE_LINK_BASED_ICONS] ?: true // Default to link-based icons
        }

    override val getSyncEnabled: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SYNC_ENABLED] ?: false // Default to disabled
        }

    override val getSyncFilePath: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[SYNC_FILE_PATH] ?: "" // Default to empty path
        }

    override val getLastSyncTime: Flow<Long> =
        dataStore.data.map { preferences ->
            preferences[LAST_SYNC_TIME] ?: 0L // Default to 0 (never synced)
        }

    override val getLanguageCode: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[LANGUAGE_CODE] ?: "" // Default to system language
        }

    override val getAutoBackupEnabled: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[AUTO_BACKUP_ENABLED] ?: false // Default to disabled
        }

    override val getAutoBackupLocation: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[AUTO_BACKUP_LOCATION] ?: "" // Default to empty path
        }

    override val getLastBackupTime: Flow<Long> =
        dataStore.data.map { preferences ->
            preferences[LAST_BACKUP_TIME] ?: 0L // Default to 0 (never backed up)
        }

    override val getDefaultPageFavourites: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[DEFAULT_PAGE_FAVOURITES] ?: false // Default to All (-1)
        }

    override val isThumbnailEnable: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[IS_THUMBNAIL_ENABLE] ?: false
        }

    override val getServerPort: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[SERVER_PORT] ?: "" // Default to empty string
        }

    override val getThemeMode: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[THEME_MODE] ?: "system" // Default to system theme
        }

    override val getShowOpenCounter: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SHOW_OPEN_COUNTER] ?: true // Default to showing counter
        }

    override val getSelectedProfileId: Flow<Long> =
        dataStore.data.map { preferences ->
            preferences[SELECTED_PROFILE_ID] ?: 1L // Default to profile ID 1
        }

    override val getSilentSaveProfileId: Flow<Long> =
        dataStore.data.map { preferences ->
            preferences[SILENT_SAVE_PROFILE_ID] ?: getSelectedProfileId.firstOrNull() ?: 1L // Default to profile ID 1
        }

    override val getGoogleDriveAutoBackupEnabled: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[GOOGLE_DRIVE_AUTO_BACKUP_ENABLED] ?: false // Default to disabled
        }

    override val getClipboardLinkDetectionEnabled: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[CLIPBOARD_LINK_DETECTION_ENABLED] ?: true // Default to enabled
        }

    override val getAppLockEnabled: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[APP_LOCK_ENABLED] ?: false // Default to disabled
        }

    override suspend fun setSortingOrder(order: @SortType String) {
        dataStore.edit { prefs ->
            prefs[SORTING_ORDER] = order
        }
    }

    override suspend fun setUseLinkBasedIcons(useLink: Boolean) {
        dataStore.edit { prefs ->
            prefs[USE_LINK_BASED_ICONS] = useLink
        }
    }

    override suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SYNC_ENABLED] = enabled
        }
    }

    override suspend fun setViewType(viewType: @ViewType Int) {
        dataStore.edit { prefs ->
            prefs[VIEW_TYPE] = viewType
        }
    }

    override suspend fun setSyncFilePath(path: String) {
        dataStore.edit { prefs ->
            prefs[SYNC_FILE_PATH] = path
        }
    }

    override suspend fun setLastSyncTime(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_SYNC_TIME] = timestamp
        }
    }

    override suspend fun setLanguageCode(code: String) {
        dataStore.edit { prefs ->
            prefs[LANGUAGE_CODE] = code
        }
    }

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_BACKUP_ENABLED] = enabled
        }
    }

    override suspend fun setAutoBackupLocation(location: String) {
        dataStore.edit { prefs ->
            prefs[AUTO_BACKUP_LOCATION] = location
        }
    }

    override suspend fun setAutoBackupInterval(interval: Long) {
        dataStore.edit { prefs ->
            prefs[AUTO_BACKUP_INTERVAL] = interval
        }
    }

    override suspend fun setLastBackupTime(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_BACKUP_TIME] = timestamp
        }
    }

    override suspend fun setDefaultPageFavourites(favourites: Boolean) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_PAGE_FAVOURITES] = favourites
        }
    }

    override suspend fun setThumbnailEnable(thumbnail: Boolean) {
        dataStore.edit { prefs ->
            prefs[IS_THUMBNAIL_ENABLE] = thumbnail
        }
    }

    override suspend fun setServerPort(port: String) {
        dataStore.edit { prefs ->
            prefs[SERVER_PORT] = port
        }
    }

    override suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }

    override suspend fun setShowOpenCounter(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_OPEN_COUNTER] = show
        }
    }

    override suspend fun setSelectedProfileId(profileId: Long) {
        dataStore.edit { prefs ->
            prefs[SELECTED_PROFILE_ID] = profileId
        }
    }

    override suspend fun setSilentSaveProfileId(profileId: Long) {
        dataStore.edit { prefs ->
            prefs[SILENT_SAVE_PROFILE_ID] = profileId
        }
    }

    override suspend fun setGoogleDriveAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[GOOGLE_DRIVE_AUTO_BACKUP_ENABLED] = enabled
        }
    }

    override suspend fun setClipboardLinkDetectionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[CLIPBOARD_LINK_DETECTION_ENABLED] = enabled
        }
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[APP_LOCK_ENABLED] = enabled
        }
    }
}
