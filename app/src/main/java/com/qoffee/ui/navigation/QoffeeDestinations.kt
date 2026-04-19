package com.qoffee.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.ui.graphics.vector.ImageVector

sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
) {
    data object Records : TopLevelDestination("records", "记录", Icons.AutoMirrored.Outlined.MenuBook, "nav_records")
    data object Analysis : TopLevelDestination("analysis", "分析", Icons.Outlined.AutoGraph, "nav_analysis")
    data object Profile : TopLevelDestination("profile", "我的", Icons.Outlined.Person, "nav_profile")
}

enum class RecordEditorEntry(val value: String) {
    NEW("new"),
    DRAFT("draft"),
    DUPLICATE("duplicate"),
    RECIPE("recipe"),
}

object QoffeeDestinations {
    const val recordIdArg = "recordId"
    const val duplicateFromArg = "duplicateFrom"
    const val recordEntryArg = "entry"
    const val recipeIdArg = "recipeId"
    const val beanIdArg = "beanId"
    const val grinderIdArg = "grinderId"

    const val recordDetailPattern = "record/{$recordIdArg}"
    const val recordEditorPattern = "editor?$recordIdArg={$recordIdArg}&$duplicateFromArg={$duplicateFromArg}&$recordEntryArg={$recordEntryArg}&$recipeIdArg={$recipeIdArg}"
    const val beanEditorPattern = "bean-editor?$beanIdArg={$beanIdArg}"
    const val grinderEditorPattern = "grinder-editor?$grinderIdArg={$grinderIdArg}"
    const val recipeEditorPattern = "recipe-editor?$recipeIdArg={$recipeIdArg}"

    fun recordDetail(recordId: Long) = "record/$recordId"

    fun recordEditor(
        recordId: Long? = null,
        duplicateFrom: Long? = null,
        entry: RecordEditorEntry = RecordEditorEntry.NEW,
        recipeId: Long? = null,
    ): String {
        val safeRecordId = recordId ?: -1L
        val safeDuplicateFrom = duplicateFrom ?: -1L
        val safeRecipeId = recipeId ?: -1L
        return "editor?$recordIdArg=$safeRecordId&$duplicateFromArg=$safeDuplicateFrom&$recordEntryArg=${entry.value}&$recipeIdArg=$safeRecipeId"
    }

    fun beanEditor(beanId: Long? = null): String = "bean-editor?$beanIdArg=${beanId ?: -1L}"

    fun grinderEditor(grinderId: Long? = null): String = "grinder-editor?$grinderIdArg=${grinderId ?: -1L}"

    fun recipeEditor(recipeId: Long? = null): String = "recipe-editor?$recipeIdArg=${recipeId ?: -1L}"
}
