package com.qoffee.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.ui.graphics.vector.ImageVector

sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
) {
    data object Brew : TopLevelDestination("brew", "冲煮", Icons.Outlined.Science, "nav_brew")
    data object Learn : TopLevelDestination("learn", "学习", Icons.AutoMirrored.Outlined.MenuBook, "nav_learn")
    data object History : TopLevelDestination("history", "历史", Icons.Outlined.AutoGraph, "nav_history")
    data object Mine : TopLevelDestination("mine", "我的", Icons.Outlined.Person, "nav_my")
}

enum class RecordEditorEntry(val value: String) {
    NEW("new"),
    DRAFT("draft"),
    DUPLICATE("duplicate"),
    RECIPE("recipe"),
    BEAN("bean"),
}

object QoffeeDestinations {
    const val recordIdArg = "recordId"
    const val duplicateFromArg = "duplicateFrom"
    const val recordEntryArg = "entry"
    const val recipeIdArg = "recipeId"
    const val beanIdArg = "beanId"
    const val grinderIdArg = "grinderId"
    const val sessionMethodArg = "methodCode"

    const val recordDetailPattern = "record/{$recordIdArg}"
    const val recordEditorPattern = "editor?$recordIdArg={$recordIdArg}&$duplicateFromArg={$duplicateFromArg}&$recordEntryArg={$recordEntryArg}&$recipeIdArg={$recipeIdArg}&$beanIdArg={$beanIdArg}"
    const val beanEditorPattern = "bean-editor?$beanIdArg={$beanIdArg}"
    const val grinderEditorPattern = "grinder-editor?$grinderIdArg={$grinderIdArg}"
    const val recipeEditorPattern = "recipe-editor?$recipeIdArg={$recipeIdArg}"
    const val brewSessionPattern = "brew-session?$sessionMethodArg={$sessionMethodArg}"
    const val myAssetsRoute = "mine/assets"
    const val myDataRoute = "mine/data"
    const val mySettingsRoute = "mine/settings"
    const val myLearningRoute = "mine/learning"
    const val mySubscriptionRoute = "mine/subscription"
    const val settingsRecordRoute = "mine/settings/record"
    const val settingsAnalysisRoute = "mine/settings/analysis"
    const val settingsNavigationRoute = "mine/settings/navigation"

    fun recordDetail(recordId: Long) = "record/$recordId"

    fun recordEditor(
        recordId: Long? = null,
        duplicateFrom: Long? = null,
        entry: RecordEditorEntry = RecordEditorEntry.NEW,
        recipeId: Long? = null,
        beanId: Long? = null,
    ): String {
        val safeRecordId = recordId ?: -1L
        val safeDuplicateFrom = duplicateFrom ?: -1L
        val safeRecipeId = recipeId ?: -1L
        val safeBeanId = beanId ?: -1L
        return "editor?$recordIdArg=$safeRecordId&$duplicateFromArg=$safeDuplicateFrom&$recordEntryArg=${entry.value}&$recipeIdArg=$safeRecipeId&$beanIdArg=$safeBeanId"
    }

    fun beanEditor(beanId: Long? = null): String = "bean-editor?$beanIdArg=${beanId ?: -1L}"

    fun grinderEditor(grinderId: Long? = null): String = "grinder-editor?$grinderIdArg=${grinderId ?: -1L}"

    fun recipeEditor(recipeId: Long? = null): String = "recipe-editor?$recipeIdArg=${recipeId ?: -1L}"

    fun brewSession(methodCode: String? = null): String = "brew-session?$sessionMethodArg=${methodCode.orEmpty()}"
}
