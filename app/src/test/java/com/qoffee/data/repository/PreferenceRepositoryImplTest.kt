package com.qoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.ServerEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PreferenceRepositoryImplTest {

    @Test
    fun settingsDefaultToTestServerEnvironment() = runTest {
        val repository = PreferenceRepositoryImpl(InMemoryPreferencesDataStore())

        val settings = repository.observeSettings().first()

        assertThat(settings.serverEnvironment).isEqualTo(ServerEnvironment.TEST)
    }

    @Test
    fun changingServerEnvironmentClearsSyncSession() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val repository = PreferenceRepositoryImpl(dataStore)
        dataStore.updateData {
            mutablePreferencesOf(
                PreferenceKeys.SYNC_EMAIL to "tester@example.com",
                PreferenceKeys.SYNC_DEVICE_ID to "device-1",
                PreferenceKeys.SYNC_ACCESS_TOKEN to "access-token",
                PreferenceKeys.SYNC_REFRESH_TOKEN to "refresh-token",
                PreferenceKeys.SYNC_CHANGE_CURSOR to 10L,
            )
        }

        repository.setServerEnvironment(ServerEnvironment.PRODUCTION)

        val preferences = dataStore.data.first()
        assertThat(preferences[PreferenceKeys.SERVER_ENVIRONMENT]).isEqualTo(ServerEnvironment.PRODUCTION.name)
        assertThat(preferences[PreferenceKeys.SYNC_EMAIL]).isNull()
        assertThat(preferences[PreferenceKeys.SYNC_DEVICE_ID]).isNull()
        assertThat(preferences[PreferenceKeys.SYNC_ACCESS_TOKEN]).isNull()
        assertThat(preferences[PreferenceKeys.SYNC_REFRESH_TOKEN]).isNull()
        assertThat(preferences[PreferenceKeys.SYNC_CHANGE_CURSOR]).isNull()
        assertThat(preferences[PreferenceKeys.SYNC_LAST_MESSAGE]).contains("正式")
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
