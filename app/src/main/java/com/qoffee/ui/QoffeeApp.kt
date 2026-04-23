package com.qoffee.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
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
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.qoffee.feature.brew.BrewSessionRoute
import com.qoffee.feature.experiments.ExperimentLabRoute
import com.qoffee.feature.experiments.ExperimentProjectRoute
import com.qoffee.feature.guides.GuideDetailRoute
import com.qoffee.feature.guides.GuideLibraryRoute
import com.qoffee.feature.learn.LearnRoute
import com.qoffee.feature.profile.BeanAssetEditorRoute
import com.qoffee.feature.profile.GrinderEditorRoute
import com.qoffee.feature.profile.MyAssetsRoute
import com.qoffee.feature.profile.MyDataRoute
import com.qoffee.feature.profile.MySubscriptionRoute
import com.qoffee.feature.profile.ProfileRoute
import com.qoffee.feature.profile.RecipeEditorRoute
import com.qoffee.feature.records.RecordDetailRoute
import com.qoffee.feature.records.RecordEditorRoute
import com.qoffee.feature.records.RecordsRoute
import com.qoffee.feature.settings.AnalysisSettingsRoute
import com.qoffee.feature.settings.NavigationSettingsRoute
import com.qoffee.feature.settings.RecordSettingsRoute
import com.qoffee.feature.settings.SettingsMenuRoute
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.navigation.QoffeeDestinations
import com.qoffee.ui.navigation.RecordEditorEntry
import com.qoffee.ui.navigation.TopLevelDestination
import com.qoffee.ui.theme.QoffeeDashboardTheme
import com.qoffee.ui.theme.qoffeeBottomShellBrush
import com.qoffee.ui.theme.qoffeePageBackgroundBrush

private const val ANALYSIS_REVIEW_CONTEXT_KEY = "analysisReviewContext"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QoffeeApp(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val topLevelDestinations = remember(appUiState.settings.showLearnInDock) {
        buildList {
            add(TopLevelDestination.Brew)
            if (appUiState.settings.showLearnInDock) {
                add(TopLevelDestination.Learn)
            }
            add(TopLevelDestination.History)
            add(TopLevelDestination.Mine)
        }
    }
    val currentArchive = appUiState.currentArchive
    val showBottomBar = currentDestination?.isTopLevel(topLevelDestinations) == true

    var showArchiveSheet by remember { mutableStateOf(false) }
    var showCreateArchiveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ArchiveSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ArchiveSummary?>(null) }
    val currentTopLevelIndex = topLevelDestinations.indexOfFirst { it.route == currentDestination?.route }
    var navigationAnimationDirection by remember { mutableStateOf(NavigationAnimationDirection.FORWARD) }

    val navigateToTopLevel: (TopLevelDestination) -> Unit = { destination ->
        val targetIndex = topLevelDestinations.indexOfFirst { it.route == destination.route }
        navigationAnimationDirection = when {
            currentTopLevelIndex == -1 || targetIndex == -1 -> NavigationAnimationDirection.FORWARD
            targetIndex > currentTopLevelIndex -> NavigationAnimationDirection.HORIZONTAL_LEFT
            targetIndex < currentTopLevelIndex -> NavigationAnimationDirection.HORIZONTAL_RIGHT
            else -> NavigationAnimationDirection.FORWARD
        }
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    androidx.compose.runtime.LaunchedEffect(appUiState.settings.showLearnInDock, currentDestination?.route) {
        if (!appUiState.settings.showLearnInDock && currentDestination?.route == TopLevelDestination.Learn.route) {
            navigationAnimationDirection = NavigationAnimationDirection.HORIZONTAL_RIGHT
            navController.navigate(TopLevelDestination.Brew.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
                        onNavigate = navigateToTopLevel,
                    )
                }
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentTopLevelIndex, showBottomBar) {
                        if (!showBottomBar || currentTopLevelIndex !in topLevelDestinations.indices) {
                            return@pointerInput
                        }
                        var dragAmountTotal = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                dragAmountTotal += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    dragAmountTotal > 110f && currentTopLevelIndex > 0 -> {
                                        navigateToTopLevel(topLevelDestinations[currentTopLevelIndex - 1])
                                    }
                                    dragAmountTotal < -110f && currentTopLevelIndex < topLevelDestinations.lastIndex -> {
                                        navigateToTopLevel(topLevelDestinations[currentTopLevelIndex + 1])
                                    }
                                }
                            },
                        )
                    },
            ) {
                QoffeeNavHost(
                    paddingValues = paddingValues,
                    navController = navController,
                    currentArchive = currentArchive,
                    onOpenArchiveSheet = { showArchiveSheet = true },
                    navigationAnimationDirection = navigationAnimationDirection,
                    onAnimationDirectionChange = { navigationAnimationDirection = it },
                )
            }
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
    navigationAnimationDirection: NavigationAnimationDirection,
    onAnimationDirectionChange: (NavigationAnimationDirection) -> Unit,
) {
    fun navigateForward(route: String) {
        onAnimationDirectionChange(NavigationAnimationDirection.FORWARD)
        navController.navigate(route)
    }

    fun popBack() {
        onAnimationDirectionChange(NavigationAnimationDirection.BACKWARD)
        navController.popBackStack()
    }

    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Brew.route,
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(durationMillis = 320),
                initialOffsetX = { fullWidth -> navigationAnimationDirection.enterOffset(fullWidth) },
            ) + fadeIn(animationSpec = tween(durationMillis = 220))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(durationMillis = 320),
                targetOffsetX = { fullWidth -> navigationAnimationDirection.exitOffset(fullWidth) },
            ) + fadeOut(animationSpec = tween(durationMillis = 180))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(durationMillis = 320),
                initialOffsetX = { fullWidth -> navigationAnimationDirection.enterOffset(fullWidth) },
            ) + fadeIn(animationSpec = tween(durationMillis = 220))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(durationMillis = 320),
                targetOffsetX = { fullWidth -> navigationAnimationDirection.exitOffset(fullWidth) },
            ) + fadeOut(animationSpec = tween(durationMillis = 180))
        },
    ) {
        composable(TopLevelDestination.Brew.route) {
            RecordsRoute(
                paddingValues = paddingValues,
                currentArchive = currentArchive,
                onOpenDetail = { recordId ->
                    navigateForward(QoffeeDestinations.recordDetail(recordId))
                },
                onOpenSession = { method, guideId ->
                    navigateForward(QoffeeDestinations.brewSession(method.code, guideId))
                },
                onOpenEditor = { recordId, duplicateFrom, entry, recipeId, beanId ->
                    navigateForward(
                        QoffeeDestinations.recordEditor(
                            recordId = recordId,
                            duplicateFrom = duplicateFrom,
                            entry = entry,
                            recipeId = recipeId,
                            beanId = beanId,
                        ),
                    )
                },
                isReadOnlyArchive = currentArchive?.archive?.isReadOnly == true,
                onOpenAnalysis = {
                    onAnimationDirectionChange(NavigationAnimationDirection.HORIZONTAL_LEFT)
                    navController.navigate(TopLevelDestination.History.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenExperiments = { navigateForward(QoffeeDestinations.experimentsRoute) },
                onOpenGuides = { navigateForward(QoffeeDestinations.guidesRoute) },
            )
        }
        composable(TopLevelDestination.Learn.route) {
            LearnRoute(
                paddingValues = paddingValues,
            )
        }
        composable(TopLevelDestination.History.route) {
            AnalysisRoute(
                paddingValues = paddingValues,
                onOpenRecord = { recordId, reviewContext ->
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        ANALYSIS_REVIEW_CONTEXT_KEY,
                        reviewContext,
                    )
                    navigateForward(QoffeeDestinations.recordDetail(recordId))
                },
                onOpenExperimentProject = { projectId ->
                    navigateForward(QoffeeDestinations.experimentDetail(projectId))
                },
            )
        }
        composable(TopLevelDestination.Mine.route) {
            ProfileRoute(
                paddingValues = paddingValues,
                currentArchive = currentArchive,
                onOpenArchiveSheet = onOpenArchiveSheet,
                onOpenAssets = { navigateForward(QoffeeDestinations.myAssetsRoute) },
                onOpenData = { navigateForward(QoffeeDestinations.myDataRoute) },
                onOpenSettings = { navigateForward(QoffeeDestinations.mySettingsRoute) },
                onOpenLearning = { navigateForward(QoffeeDestinations.myLearningRoute) },
                onOpenSubscription = { navigateForward(QoffeeDestinations.mySubscriptionRoute) },
            )
        }
        composable(QoffeeDestinations.myAssetsRoute) {
            MyAssetsRoute(
                paddingValues = paddingValues,
                isReadOnlyArchive = currentArchive?.archive?.isReadOnly == true,
                onBack = ::popBack,
                onOpenBeanEditor = { beanId -> navigateForward(QoffeeDestinations.beanEditor(beanId)) },
                onOpenGrinderEditor = { grinderId -> navigateForward(QoffeeDestinations.grinderEditor(grinderId)) },
                onOpenRecipeEditor = { recipeId -> navigateForward(QoffeeDestinations.recipeEditor(recipeId)) },
                onOpenRecordDraft = { recordId ->
                    navigateForward(
                        QoffeeDestinations.recordEditor(
                            recordId = recordId,
                            entry = RecordEditorEntry.DRAFT,
                        ),
                    )
                },
                onStartBeanRecord = { beanId ->
                    navigateForward(
                        QoffeeDestinations.recordEditor(
                            entry = RecordEditorEntry.BEAN,
                            beanId = beanId,
                        ),
                    )
                },
                onStartRecipeRecord = { recipeId ->
                    navigateForward(
                        QoffeeDestinations.recordEditor(
                            entry = RecordEditorEntry.RECIPE,
                            recipeId = recipeId,
                        ),
                    )
                },
            )
        }
        composable(QoffeeDestinations.myDataRoute) {
            MyDataRoute(
                paddingValues = paddingValues,
                currentArchive = currentArchive,
                isReadOnlyArchive = currentArchive?.archive?.isReadOnly == true,
                onBack = ::popBack,
                onOpenArchiveSheet = onOpenArchiveSheet,
            )
        }
        composable(QoffeeDestinations.mySettingsRoute) {
            SettingsMenuRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
                onOpenRecordSettings = { navigateForward(QoffeeDestinations.settingsRecordRoute) },
                onOpenAnalysisSettings = { navigateForward(QoffeeDestinations.settingsAnalysisRoute) },
                onOpenNavigationSettings = { navigateForward(QoffeeDestinations.settingsNavigationRoute) },
            )
        }
        composable(QoffeeDestinations.myLearningRoute) {
            LearnRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
                eyebrow = "QOFFEE / MINE / LEARN",
                title = "学习",
            )
        }
        composable(QoffeeDestinations.mySubscriptionRoute) {
            MySubscriptionRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
            )
        }
        composable(QoffeeDestinations.settingsRecordRoute) {
            RecordSettingsRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
            )
        }
        composable(QoffeeDestinations.settingsAnalysisRoute) {
            AnalysisSettingsRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
            )
        }
        composable(QoffeeDestinations.settingsNavigationRoute) {
            NavigationSettingsRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
            )
        }
        composable(
            route = QoffeeDestinations.brewSessionPattern,
            arguments = listOf(
                navArgument(QoffeeDestinations.sessionMethodArg) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(QoffeeDestinations.guideIdArg) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            BrewSessionRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
                onOpenRecordEditor = {
                    navigateForward(
                        QoffeeDestinations.recordEditor(
                            entry = RecordEditorEntry.NEW,
                        ),
                    )
                },
            )
        }
        composable(QoffeeDestinations.experimentsRoute) {
            ExperimentLabRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
                onOpenProject = { projectId -> navigateForward(QoffeeDestinations.experimentDetail(projectId)) },
                onProjectCreated = { projectId -> navigateForward(QoffeeDestinations.experimentDetail(projectId)) },
            )
        }
        composable(
            route = QoffeeDestinations.experimentDetailPattern,
            arguments = listOf(
                navArgument(QoffeeDestinations.experimentProjectIdArg) {
                    type = NavType.LongType
                },
            ),
        ) {
            ExperimentProjectRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
                onOpenDraft = { recordId ->
                    navigateForward(
                        QoffeeDestinations.recordEditor(
                            recordId = recordId,
                            entry = RecordEditorEntry.DRAFT,
                        ),
                    )
                },
            )
        }
        composable(QoffeeDestinations.guidesRoute) {
            GuideLibraryRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
                onOpenGuide = { guideId -> navigateForward(QoffeeDestinations.guideDetail(guideId)) },
            )
        }
        composable(
            route = QoffeeDestinations.guideDetailPattern,
            arguments = listOf(
                navArgument(QoffeeDestinations.guideIdArg) {
                    type = NavType.LongType
                },
            ),
        ) {
            GuideDetailRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
                onStart = { guideId ->
                    navigateForward(QoffeeDestinations.brewSession(guideId = guideId))
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
                navArgument(QoffeeDestinations.beanIdArg) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            RecordEditorRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
                onCompleted = { recordId ->
                    onAnimationDirectionChange(NavigationAnimationDirection.FORWARD)
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
            BeanAssetEditorRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
                onSaved = ::popBack,
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
                onBack = ::popBack,
                onSaved = ::popBack,
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
                onBack = ::popBack,
                onSaved = ::popBack,
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
            val reviewContext = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>(ANALYSIS_REVIEW_CONTEXT_KEY)
            RecordDetailRoute(
                paddingValues = paddingValues,
                onBack = ::popBack,
                onEdit = { recordId ->
                    navigateForward(
                        QoffeeDestinations.recordEditor(
                            recordId = recordId,
                            entry = RecordEditorEntry.DRAFT,
                        ),
                    )
                },
                onDuplicate = { recordId ->
                    navigateForward(
                        QoffeeDestinations.recordEditor(
                            duplicateFrom = recordId,
                            entry = RecordEditorEntry.DUPLICATE,
                        ),
                    )
                },
                onDeleted = ::popBack,
                onGuideCreated = { guideId -> navigateForward(QoffeeDestinations.guideDetail(guideId)) },
                isReadOnlyArchive = currentArchive?.archive?.isReadOnly == true,
                reviewContext = reviewContext,
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

private enum class NavigationAnimationDirection {
    FORWARD,
    BACKWARD,
    HORIZONTAL_LEFT,
    HORIZONTAL_RIGHT,
    ;

    fun enterOffset(fullWidth: Int): Int {
        return when (this) {
            FORWARD, HORIZONTAL_LEFT -> fullWidth
            BACKWARD, HORIZONTAL_RIGHT -> -fullWidth
        }
    }

    fun exitOffset(fullWidth: Int): Int {
        return when (this) {
            FORWARD, HORIZONTAL_LEFT -> -fullWidth
            BACKWARD, HORIZONTAL_RIGHT -> fullWidth
        }
    }
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
                val containerColor by animateColorAsState(
                    targetValue = if (selected) {
                        dashboardColors.accentSoft.copy(alpha = 0.56f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = spring(stiffness = 500f),
                    label = "navContainer",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) {
                        dashboardColors.titleText
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = spring(stiffness = 500f),
                    label = "navContent",
                )
                val selectedScale = if (selected) 1.02f else 1f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(containerColor)
                        .graphicsLayer {
                            scaleX = selectedScale
                            scaleY = selectedScale
                        }
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
