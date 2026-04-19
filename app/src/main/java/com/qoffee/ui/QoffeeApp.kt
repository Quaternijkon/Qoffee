package com.qoffee.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.qoffee.core.model.ArchiveSummary
import com.qoffee.feature.analytics.AnalyticsRoute
import com.qoffee.feature.catalog.CatalogRoute
import com.qoffee.feature.records.RecordDetailRoute
import com.qoffee.feature.records.RecordEditorRoute
import com.qoffee.feature.records.RecordsRoute
import com.qoffee.feature.settings.SettingsRoute
import com.qoffee.ui.navigation.QoffeeDestinations
import com.qoffee.ui.navigation.TopLevelDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QoffeeApp(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val topLevelDestinations = listOf(
        TopLevelDestination.Analytics,
        TopLevelDestination.Records,
        TopLevelDestination.Catalog,
        TopLevelDestination.Settings,
    )
    val currentArchive = appUiState.currentArchive
    val currentTopLevel = topLevelDestinations.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }
    val showBottomBar = currentDestination?.isTopLevel(topLevelDestinations) == true
    val showFab = showBottomBar &&
        currentTopLevel != TopLevelDestination.Settings &&
        currentArchive?.archive?.isReadOnly != true

    var showArchiveSheet by remember { mutableStateOf(false) }
    var showCreateArchiveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ArchiveSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ArchiveSummary?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Text(text = currentTopLevel?.label ?: "Qoffee")
                        Text(
                            text = currentArchive?.archive?.name ?: "正在准备存档",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showArchiveSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Inventory2,
                            contentDescription = "切换存档",
                        )
                    }
                },
            )
        },
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
                        contentDescription = "新建记录",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    ) { paddingValues ->
        QoffeeNavHost(
            paddingValues = paddingValues,
            navController = navController,
            currentArchive = currentArchive,
        )
    }

    if (showArchiveSheet) {
        ArchiveSheet(
            archives = appUiState.archives,
            currentArchive = currentArchive,
            onDismiss = { showArchiveSheet = false },
            onSwitch = {
                appViewModel.switchArchive(it)
                showArchiveSheet = false
            },
            onCreateArchive = {
                showArchiveSheet = false
                showCreateArchiveDialog = true
            },
            onDuplicateArchive = { archive ->
                appViewModel.duplicateArchive(archive.archive.id, "${archive.archive.name} 副本")
                showArchiveSheet = false
            },
            onRenameArchive = { archive -> renameTarget = archive },
            onDeleteArchive = { archive -> deleteTarget = archive },
            onCopyDemo = {
                appViewModel.copyDemoArchiveAsEditable("我的咖啡实验")
                showArchiveSheet = false
            },
            onResetDemo = {
                appViewModel.resetDemoArchive()
                showArchiveSheet = false
            },
        )
    }

    if (showCreateArchiveDialog) {
        ArchiveNameDialog(
            title = "新建空白存档",
            initialValue = "",
            confirmLabel = "创建",
            onDismiss = { showCreateArchiveDialog = false },
            onConfirm = { name ->
                appViewModel.createArchive(name.ifBlank { "新的咖啡实验" })
                showCreateArchiveDialog = false
            },
        )
    }

    renameTarget?.let { archive ->
        ArchiveNameDialog(
            title = "重命名存档",
            initialValue = archive.archive.name,
            confirmLabel = "保存",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                appViewModel.renameArchive(archive.archive.id, name)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { archive ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除存档") },
            text = { Text("确认删除“${archive.archive.name}”吗？该操作会清空该存档下所有资料和记录。") },
            confirmButton = {
                Button(onClick = {
                    appViewModel.deleteArchive(archive.archive.id)
                    deleteTarget = null
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun QoffeeNavHost(
    paddingValues: PaddingValues,
    navController: NavHostController,
    currentArchive: ArchiveSummary?,
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
                isReadOnlyArchive = currentArchive?.archive?.isReadOnly == true,
            )
        }
        composable(TopLevelDestination.Catalog.route) {
            CatalogRoute(
                paddingValues = paddingValues,
                isReadOnlyArchive = currentArchive?.archive?.isReadOnly == true,
            )
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
                isReadOnlyArchive = currentArchive?.archive?.isReadOnly == true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveSheet(
    archives: List<ArchiveSummary>,
    currentArchive: ArchiveSummary?,
    onDismiss: () -> Unit,
    onSwitch: (Long) -> Unit,
    onCreateArchive: () -> Unit,
    onDuplicateArchive: (ArchiveSummary) -> Unit,
    onRenameArchive: (ArchiveSummary) -> Unit,
    onDeleteArchive: (ArchiveSummary) -> Unit,
    onCopyDemo: () -> Unit,
    onResetDemo: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("切换存档", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            currentArchive?.let { archive ->
                Text(
                    text = "当前：${archive.archive.name}",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                if (archive.isDemo) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCopyDemo) {
                            Icon(Icons.Outlined.FolderCopy, contentDescription = null)
                            Text("复制为我的存档")
                        }
                        OutlinedButton(onClick = onResetDemo) {
                            Icon(Icons.Outlined.Restore, contentDescription = null)
                            Text("重置示范数据")
                        }
                    }
                }
            }
            archives.forEach { archive ->
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (archive.archive.id == currentArchive?.archive?.id) {
                            androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = archive.archive.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${archive.archive.type.displayName} | 豆 ${archive.beanCount} | 磨豆机 ${archive.grinderCount} | 记录 ${archive.recordCount}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onSwitch(archive.archive.id) }) { Text("切换") }
                            OutlinedButton(onClick = { onDuplicateArchive(archive) }) { Text("复制") }
                            if (!archive.archive.isReadOnly) {
                                OutlinedButton(onClick = { onRenameArchive(archive) }) { Text("重命名") }
                                TextButton(onClick = { onDeleteArchive(archive) }) { Text("删除") }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onCreateArchive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("新建空白存档")
            }
        }
    }
}

@Composable
private fun ArchiveNameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("存档名称") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun NavDestination.isTopLevel(destinations: List<TopLevelDestination>): Boolean {
    return hierarchy.any { destination -> destinations.any { it.route == destination.route } }
}
