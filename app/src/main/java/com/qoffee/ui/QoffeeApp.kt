package com.qoffee.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import com.qoffee.feature.analytics.AnalysisRoute
import com.qoffee.feature.profile.BeanEditorRoute
import com.qoffee.feature.profile.GrinderEditorRoute
import com.qoffee.feature.profile.ProfileRoute
import com.qoffee.feature.profile.RecipeEditorRoute
import com.qoffee.feature.records.RecordDetailRoute
import com.qoffee.feature.records.RecordEditorRoute
import com.qoffee.feature.records.RecordsRoute
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.navigation.QoffeeDestinations
import com.qoffee.ui.navigation.RecordEditorEntry
import com.qoffee.ui.navigation.TopLevelDestination
import com.qoffee.ui.theme.QoffeeDashboardTheme
import com.qoffee.ui.theme.qoffeeBottomShellBrush
import com.qoffee.ui.theme.qoffeePageBackgroundBrush

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
        TopLevelDestination.Records,
        TopLevelDestination.Analysis,
        TopLevelDestination.Profile,
    )
    val currentArchive = appUiState.currentArchive
    val showBottomBar = currentDestination?.isTopLevel(topLevelDestinations) == true

    var showArchiveSheet by remember { mutableStateOf(false) }
    var showCreateArchiveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ArchiveSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ArchiveSummary?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = qoffeePageBackgroundBrush()),
    ) {
        DashboardBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = {
                if (showBottomBar && currentDestination != null) {
                    DashboardNavigationBar(
                        currentDestination = currentDestination,
                        destinations = topLevelDestinations,
                        onNavigate = { destination ->
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { paddingValues ->
            QoffeeNavHost(
                paddingValues = paddingValues,
                navController = navController,
                currentArchive = currentArchive,
                onOpenArchiveSheet = { showArchiveSheet = true },
            )
        }
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
    onOpenArchiveSheet: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Records.route,
    ) {
        composable(TopLevelDestination.Records.route) {
            RecordsRoute(
                paddingValues = paddingValues,
                currentArchive = currentArchive,
                onOpenDetail = { recordId ->
                    navController.navigate(QoffeeDestinations.recordDetail(recordId))
                },
                onOpenEditor = { recordId, duplicateFrom, entry, recipeId ->
                    navController.navigate(
                        QoffeeDestinations.recordEditor(
                            recordId = recordId,
                            duplicateFrom = duplicateFrom,
                            entry = entry,
                            recipeId = recipeId,
                        ),
                    )
                },
                isReadOnlyArchive = currentArchive?.archive?.isReadOnly == true,
            )
        }
        composable(TopLevelDestination.Analysis.route) {
            AnalysisRoute(
                paddingValues = paddingValues,
                onOpenRecord = { recordId ->
                    navController.navigate(QoffeeDestinations.recordDetail(recordId))
                },
            )
        }
        composable(TopLevelDestination.Profile.route) {
            ProfileRoute(
                paddingValues = paddingValues,
                currentArchive = currentArchive,
                isReadOnlyArchive = currentArchive?.archive?.isReadOnly == true,
                onOpenArchiveSheet = onOpenArchiveSheet,
                onOpenBeanEditor = { beanId ->
                    navController.navigate(QoffeeDestinations.beanEditor(beanId))
                },
                onOpenGrinderEditor = { grinderId ->
                    navController.navigate(QoffeeDestinations.grinderEditor(grinderId))
                },
                onOpenRecipeEditor = { recipeId ->
                    navController.navigate(QoffeeDestinations.recipeEditor(recipeId))
                },
            )
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
                navArgument(QoffeeDestinations.recordEntryArg) {
                    type = NavType.StringType
                    defaultValue = RecordEditorEntry.NEW.value
                },
                navArgument(QoffeeDestinations.recipeIdArg) {
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
            route = QoffeeDestinations.beanEditorPattern,
            arguments = listOf(
                navArgument(QoffeeDestinations.beanIdArg) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            BeanEditorRoute(
                paddingValues = paddingValues,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = QoffeeDestinations.grinderEditorPattern,
            arguments = listOf(
                navArgument(QoffeeDestinations.grinderIdArg) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            GrinderEditorRoute(
                paddingValues = paddingValues,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = QoffeeDestinations.recipeEditorPattern,
            arguments = listOf(
                navArgument(QoffeeDestinations.recipeIdArg) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            RecipeEditorRoute(
                paddingValues = paddingValues,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
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
                    navController.navigate(
                        QoffeeDestinations.recordEditor(
                            recordId = recordId,
                            entry = RecordEditorEntry.DRAFT,
                        ),
                    )
                },
                onDuplicate = { recordId ->
                    navController.navigate(
                        QoffeeDestinations.recordEditor(
                            duplicateFrom = recordId,
                            entry = RecordEditorEntry.DUPLICATE,
                        ),
                    )
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = QoffeeDashboardTheme.colors.panel,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = QoffeeDashboardTheme.colors.accentSoft,
                    shape = CircleShape,
                    modifier = Modifier.size(12.dp),
                ) {}
                Text("切换存档", style = MaterialTheme.typography.headlineMedium)
            }
            currentArchive?.let { archive ->
                SectionCard(
                    title = "当前会话",
                    subtitle = "所有记录、配方和设备都会绑定到当前存档。",
                ) {
                    Text(
                        text = archive.archive.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip(text = archive.archive.type.displayName)
                        StatChip(text = "记录 ${archive.recordCount}")
                        StatChip(text = "豆子 ${archive.beanCount}")
                    }
                }
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
                SectionCard(
                    title = archive.archive.name,
                    subtitle = "${archive.archive.type.displayName} · 豆 ${archive.beanCount} · 磨豆机 ${archive.grinderCount} · 记录 ${archive.recordCount}",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (archive.archive.id == currentArchive?.archive?.id) {
                            StatChip(text = "当前存档")
                        }
                        archive.lastRecordAt?.let {
                            StatChip(text = "最近活跃")
                        }
                    }
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

@Composable
private fun DashboardBackdrop() {
    val dashboardColors = QoffeeDashboardTheme.colors
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = dashboardColors.ambientGlow,
            radius = size.width * 0.48f,
            center = Offset(size.width * 0.82f, size.height * 0.12f),
        )
        drawCircle(
            color = dashboardColors.ambientGlowSecondary,
            radius = size.width * 0.38f,
            center = Offset(size.width * 0.18f, size.height * 0.46f),
        )
        drawCircle(
            color = dashboardColors.accentGlow,
            radius = size.width * 0.30f,
            center = Offset(size.width * 0.55f, size.height * 0.88f),
        )
    }
}

@Composable
private fun DashboardNavigationBar(
    currentDestination: NavDestination,
    destinations: List<TopLevelDestination>,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = qoffeeBottomShellBrush()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dashboardColors.shellDivider.copy(alpha = 0.72f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(dashboardColors.shellElevated)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            destinations.forEach { destination ->
                val selected = currentDestination.hierarchy.any { it.route == destination.route }
                val containerColor = if (selected) {
                    dashboardColors.accentSoft.copy(alpha = 0.56f)
                } else {
                    Color.Transparent
                }
                val contentColor = if (selected) {
                    dashboardColors.titleText
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(containerColor)
                            .testTag(destination.testTag),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(destination) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary else contentColor,
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}
