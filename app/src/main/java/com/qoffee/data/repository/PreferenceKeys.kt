package com.qoffee.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val AUTO_RESTORE_DRAFT = booleanPreferencesKey("auto_restore_draft")
    val SHOW_CONFIDENCE = booleanPreferencesKey("show_insight_confidence")
    val SHOW_LEARN_IN_DOCK = booleanPreferencesKey("show_learn_in_dock")
    val THEME_STYLE = stringPreferencesKey("theme_style")
    val SERVER_ENVIRONMENT = stringPreferencesKey("server_environment")
    val DEFAULT_ANALYSIS_RANGE = stringPreferencesKey("default_analysis_range")
    val DEFAULT_BEAN_ID = longPreferencesKey("default_bean_id")
    val DEFAULT_GRINDER_ID = longPreferencesKey("default_grinder_id")
    val CURRENT_ARCHIVE_ID = longPreferencesKey("current_archive_id")
    val LAST_OPENED_ARCHIVE_ID = longPreferencesKey("last_opened_archive_id")
    val HAS_SEEDED_DEMO_ARCHIVE = booleanPreferencesKey("has_seeded_demo_archive")
    val ENTITLEMENT_TIER = stringPreferencesKey("entitlement_tier")
    val SYNC_EMAIL = stringPreferencesKey("sync_email")
    val SYNC_SIGNED_IN_AT = longPreferencesKey("sync_signed_in_at")
    val SYNC_DEVICE_ID = stringPreferencesKey("sync_device_id")
    val SYNC_ACCESS_TOKEN = stringPreferencesKey("sync_access_token")
    val SYNC_REFRESH_TOKEN = stringPreferencesKey("sync_refresh_token")
    val SYNC_ACCESS_EXPIRES_AT = longPreferencesKey("sync_access_expires_at")
    val SYNC_CHANGE_CURSOR = longPreferencesKey("sync_change_cursor")
    val SYNC_LAST_PUSHED_AT = longPreferencesKey("sync_last_pushed_at")
    val SYNC_LAST_PULLED_AT = longPreferencesKey("sync_last_pulled_at")
    val SYNC_LAST_MESSAGE = stringPreferencesKey("sync_last_message")
    val SYNC_LAST_SNAPSHOT_ID = stringPreferencesKey("sync_last_snapshot_id")
    val SYNC_LAST_SNAPSHOT_AT = longPreferencesKey("sync_last_snapshot_at")
    val SYNC_LAST_SNAPSHOT_CHECKSUM = stringPreferencesKey("sync_last_snapshot_checksum")
    val SYNC_LAST_SNAPSHOT_BYTES = longPreferencesKey("sync_last_snapshot_bytes")
}
