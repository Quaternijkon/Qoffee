package com.qoffee.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val AUTO_RESTORE_DRAFT = booleanPreferencesKey("auto_restore_draft")
    val SHOW_CONFIDENCE = booleanPreferencesKey("show_insight_confidence")
    val SHOW_LEARN_IN_DOCK = booleanPreferencesKey("show_learn_in_dock")
    val DEFAULT_ANALYSIS_RANGE = stringPreferencesKey("default_analysis_range")
    val DEFAULT_BEAN_ID = longPreferencesKey("default_bean_id")
    val DEFAULT_GRINDER_ID = longPreferencesKey("default_grinder_id")
    val CURRENT_ARCHIVE_ID = longPreferencesKey("current_archive_id")
    val LAST_OPENED_ARCHIVE_ID = longPreferencesKey("last_opened_archive_id")
    val HAS_SEEDED_DEMO_ARCHIVE = booleanPreferencesKey("has_seeded_demo_archive")
}
