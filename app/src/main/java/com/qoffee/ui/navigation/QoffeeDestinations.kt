package com.qoffee.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Analytics : TopLevelDestination("analytics", "Analytics", Icons.Outlined.AutoGraph)
    data object Records : TopLevelDestination("records", "Records", Icons.Outlined.MenuBook)
    data object Catalog : TopLevelDestination("catalog", "Catalog", Icons.Outlined.Inventory2)
    data object Settings : TopLevelDestination("settings", "Settings", Icons.Outlined.Settings)
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
