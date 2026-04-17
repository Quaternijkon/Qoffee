package com.qoffee.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qoffee.feature.analytics.AnalyticsRoute
import com.qoffee.feature.catalog.CatalogRoute
import com.qoffee.feature.records.RecordDetailRoute
import com.qoffee.feature.records.RecordEditorRoute
import com.qoffee.feature.records.RecordsRoute
import com.qoffee.feature.settings.SettingsRoute
import com.qoffee.ui.navigation.QoffeeDestinations
import com.qoffee.ui.navigation.TopLevelDestination

@Composable
fun QoffeeApp(
    _appViewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val topLevelDestinations = listOf(
        TopLevelDestination.Analytics,
        TopLevelDestination.Records,
        TopLevelDestination.Catalog,
        TopLevelDestination.Settings,
    )
    val topLevelRoutes = topLevelDestinations.map { it.route }
    val showBottomBar = currentDestination?.isTopLevel(topLevelDestinations) == true
    val showFab = currentDestination?.route in (topLevelRoutes - TopLevelDestination.Settings.route)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = { navController.navigate(QoffeeDestinations.recordEditor()) },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "new record",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    ) { paddingValues ->
        QoffeeNavHost(
            paddingValues = paddingValues,
            navController = navController,
        )
    }
}

@Composable
private fun QoffeeNavHost(
    paddingValues: PaddingValues,
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Analytics.route,
    ) {
        composable(TopLevelDestination.Analytics.route) {
            AnalyticsRoute(
                paddingValues = paddingValues,
                onOpenRecord = { recordId ->
                    navController.navigate(QoffeeDestinations.recordDetail(recordId))
                },
            )
        }
        composable(TopLevelDestination.Records.route) {
            RecordsRoute(
                paddingValues = paddingValues,
                onOpenDetail = { recordId ->
                    navController.navigate(QoffeeDestinations.recordDetail(recordId))
                },
                onOpenEditor = { recordId, duplicateFrom ->
                    navController.navigate(QoffeeDestinations.recordEditor(recordId, duplicateFrom))
                },
            )
        }
        composable(TopLevelDestination.Catalog.route) {
            CatalogRoute(paddingValues = paddingValues)
        }
        composable(TopLevelDestination.Settings.route) {
            SettingsRoute(paddingValues = paddingValues)
        }
        composable(
            route = QoffeeDestinations.recordEditorPattern,
            arguments = listOf(
                navArgument(QoffeeDestinations.recordIdArg) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument(QoffeeDestinations.duplicateFromArg) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            RecordEditorRoute(
                paddingValues = paddingValues,
                onBack = { navController.popBackStack() },
                onCompleted = { recordId ->
                    navController.navigate(QoffeeDestinations.recordDetail(recordId)) {
                        popUpTo(QoffeeDestinations.recordEditorPattern) {
                            inclusive = true
                        }
                    }
                },
            )
        }
        composable(
            route = QoffeeDestinations.recordDetailPattern,
            arguments = listOf(
                navArgument(QoffeeDestinations.recordIdArg) {
                    type = NavType.LongType
                },
            ),
        ) {
            RecordDetailRoute(
                paddingValues = paddingValues,
                onBack = { navController.popBackStack() },
                onEdit = { recordId ->
                    navController.navigate(QoffeeDestinations.recordEditor(recordId = recordId))
                },
                onDuplicate = { recordId ->
                    navController.navigate(QoffeeDestinations.recordEditor(duplicateFrom = recordId))
                },
            )
        }
    }
}

private fun NavDestination.isTopLevel(destinations: List<TopLevelDestination>): Boolean {
    return hierarchy.any { destination -> destinations.any { it.route == destination.route } }
}
