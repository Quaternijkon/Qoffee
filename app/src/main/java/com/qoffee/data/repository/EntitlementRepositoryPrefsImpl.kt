package com.qoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.qoffee.core.model.EntitlementTier
import com.qoffee.core.model.UserEntitlements
import com.qoffee.domain.repository.EntitlementRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class EntitlementRepositoryPrefsImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : EntitlementRepository {

    override fun observeEntitlements(): Flow<UserEntitlements> {
        return dataStore.data.map { prefs ->
            val tier = EntitlementTier.entries.firstOrNull {
                it.name == prefs[PreferenceKeys.ENTITLEMENT_TIER]
            } ?: EntitlementTier.FREE
            UserEntitlements(
                tier = tier,
                unlockedFeatures = unlockedFeatures(tier),
                proHighlights = listOf(
                    "高级复盘维度",
                    "完整实验历史",
                    "云同步与自动快照",
                    "批量导出与分享卡",
                    "AI Coach beta",
                ),
            )
        }
    }

    override suspend fun setPreviewTier(tier: EntitlementTier) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.ENTITLEMENT_TIER] = tier.name
        }
    }

    private fun unlockedFeatures(tier: EntitlementTier): List<String> {
        return when (tier) {
            EntitlementTier.FREE -> listOf(
                "basic_records",
                "basic_recipes",
                "basic_review",
                "local_backup",
            )

            EntitlementTier.PRO -> listOf(
                "basic_records",
                "basic_recipes",
                "basic_review",
                "local_backup",
                "advanced_review",
                "experiment_history",
                "cloud_sync",
                "cloud_snapshot",
                "batch_export",
                "ai_coach_beta",
                "share_cards",
            )
        }
    }
}
