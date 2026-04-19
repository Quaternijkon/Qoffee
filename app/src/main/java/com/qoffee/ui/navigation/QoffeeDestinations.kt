package com.qoffee.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Analytics : TopLevelDestination("analytics", "分析", Icons.Outlined.AutoGraph)
    data object Records : TopLevelDestination("records", "记录", Icons.AutoMirrored.Outlined.MenuBook)
    data object Catalog : TopLevelDestination("catalog", "资料库", Icons.Outlined.Inventory2)
    data object Settings : TopLevelDestination("settings", "设置", Icons.Outlined.Settings)
}

object QoffeeDestinations {
    const val recordIdArg = "recordId"
    const val duplicateFromArg = "duplicateFrom"

    const val recordDetailPattern = "record/{$recordIdArg}"
    const val recordEditorPattern = "editor?$recordIdArg={$recordIdArg}&$duplicateFromArg={$duplicateFromArg}"

    fun recordDetail(recordId: Long) = "record/$recordId"

    fun recordEditor(recordId: Long? = null, duplicateFrom: Long? = null): String {
        val safeRecordId = recordId ?: -1L
        val safeDuplicateFrom = duplicateFrom ?: -1L
        return "editor?$recordIdArg=$safeRecordId&$duplicateFromArg=$safeDuplicateFrom"
    }
}
