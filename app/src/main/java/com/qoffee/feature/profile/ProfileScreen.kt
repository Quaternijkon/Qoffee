package com.qoffee.feature.profile

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.R
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.ArchiveSummary
import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.EntitlementTier
import com.qoffee.core.model.FileExportPayload
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RecordDraftLaunchBehavior
import com.qoffee.core.model.RecordPrefillSource
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.RestoreOutcome
import com.qoffee.core.model.ShareCard
import com.qoffee.core.model.SyncConflictResolution
import com.qoffee.core.model.SyncState
import com.qoffee.core.model.UserEntitlements
import com.qoffee.core.model.UserSettings
import com.qoffee.core.model.resolveRecordDraftLaunchBehavior
import com.qoffee.domain.repository.BackupRepository
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.EntitlementRepository
import com.qoffee.domain.repository.ExperimentRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.domain.repository.ShareRepository
import com.qoffee.domain.repository.SyncRepository
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.DashboardArtworkBanner
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val inventory: List<BeanInventory> = emptyList(),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
    val recipes: List<RecipeTemplate> = emptyList(),
    val shareCards: List<ShareCard> = emptyList(),
    val activeDraft: CoffeeRecord? = null,
    val settings: UserSettings = UserSettings(),
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    recipeRepository: RecipeRepository,
    experimentRepository: ExperimentRepository,
    recordRepository: RecordRepository,
    shareRepository: ShareRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        experimentRepository.observeBeanInventory(),
        catalogRepository.observeBeanProfiles(),
        catalogRepository.observeGrinderProfiles(),
        recipeRepository.observeRecipes(),
        recordRepository.observeRecords(),
    ) { inventory, beans, grinders, recipes, records ->
        ProfileUiState(
            inventory = inventory,
            beans = beans,
            grinders = grinders,
            recipes = recipes,
            activeDraft = records.firstOrNull { it.status == RecordStatus.DRAFT },
        )
    }.combine(preferenceRepository.observeSettings()) { state, settings ->
        ProfileUiState(
            inventory = state.inventory,
            beans = state.beans,
            grinders = state.grinders,
            recipes = state.recipes,
            shareCards = state.shareCards,
            activeDraft = state.activeDraft,
            settings = settings,
        )
    }.combine(shareRepository.observeShareCards()) { state, shareCards ->
        state.copy(shareCards = shareCards)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    fun setDefaultAnalysisRange(range: AnalysisTimeRange) {
        viewModelScope.launch {
            preferenceRepository.setDefaultAnalysisTimeRange(range)
        }
    }
}

@HiltViewModel
class MyDataViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    val syncState: StateFlow<SyncState> = syncRepository.observeSyncState().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncState(),
    )

    suspend fun exportBackup(): FileExportPayload = backupRepository.exportBackup()

    suspend fun restoreBackup(json: String): RestoreOutcome = backupRepository.restoreBackup(json)

    suspend fun registerWithEmail(email: String, password: String): String =
        syncRepository.registerWithEmail(email, password).message

    suspend fun signInWithEmail(email: String, password: String): String =
        syncRepository.signInWithEmail(email, password).message

    suspend fun signOut(): String = syncRepository.signOut().message

    suspend fun pushChanges(): String = syncRepository.pushChanges().message

    suspend fun pullChanges(): String = syncRepository.pullChanges().message

    suspend fun createSnapshot(): String = syncRepository.createSnapshot().message

    suspend fun keepRemote(conflictId: String): String =
        syncRepository.resolveConflict(conflictId, SyncConflictResolution.KEEP_REMOTE).message

    suspend fun keepLocal(conflictId: String): String =
        syncRepository.resolveConflict(conflictId, SyncConflictResolution.KEEP_LOCAL).message
}

data class SubscriptionUiState(
    val entitlements: UserEntitlements = UserEntitlements(),
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val entitlementRepository: EntitlementRepository,
) : ViewModel() {

    val uiState: StateFlow<SubscriptionUiState> = entitlementRepository.observeEntitlements()
        .combine(kotlinx.coroutines.flow.flowOf(Unit)) { entitlements, _ ->
            SubscriptionUiState(entitlements = entitlements)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SubscriptionUiState(),
        )

    fun setPreviewTier(tier: EntitlementTier) {
        viewModelScope.launch {
            entitlementRepository.setPreviewTier(tier)
        }
    }
}

@Composable
fun ProfileRoute(
    paddingValues: PaddingValues,
    currentArchive: ArchiveSummary?,
    onOpenArchiveSheet: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLearning: () -> Unit,
    onOpenSubscription: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    syncViewModel: MyDataViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncState by syncViewModel.syncState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var isSyncWorking by remember { mutableStateOf(false) }
    var syncEmail by remember(syncState.account?.email) { mutableStateOf(syncState.account?.email.orEmpty()) }
    var syncPassword by remember { mutableStateOf("") }
    var syncOperationMessage by remember { mutableStateOf<String?>(null) }

    fun runSyncOperation(block: suspend () -> String) {
        coroutineScope.launch {
            isSyncWorking = true
            syncOperationMessage = runCatching { block() }
                .getOrElse { error -> error.message ?: "同步操作失败，请稍后重试。" }
            isSyncWorking = false
        }
    }

    ProfileScreen(
        paddingValues = paddingValues,
        currentArchive = currentArchive,
        uiState = uiState,
        syncState = syncState,
        syncEmail = syncEmail,
        syncPassword = syncPassword,
        isSyncWorking = isSyncWorking,
        syncOperationMessage = syncOperationMessage,
        onSyncEmailChange = { syncEmail = it },
        onSyncPasswordChange = { syncPassword = it },
        onRegister = { runSyncOperation { syncViewModel.registerWithEmail(syncEmail, syncPassword) } },
        onSignIn = { runSyncOperation { syncViewModel.signInWithEmail(syncEmail, syncPassword) } },
        onSignOut = { runSyncOperation { syncViewModel.signOut() } },
        onPush = { runSyncOperation { syncViewModel.pushChanges() } },
        onPull = { runSyncOperation { syncViewModel.pullChanges() } },
        onSnapshot = { runSyncOperation { syncViewModel.createSnapshot() } },
        onKeepRemote = { conflictId -> runSyncOperation { syncViewModel.keepRemote(conflictId) } },
        onKeepLocal = { conflictId -> runSyncOperation { syncViewModel.keepLocal(conflictId) } },
        onOpenArchiveSheet = onOpenArchiveSheet,
        onOpenAssets = onOpenAssets,
        onOpenData = onOpenData,
        onOpenSettings = onOpenSettings,
        onOpenLearning = onOpenLearning,
        onOpenSubscription = onOpenSubscription,
    )
}

@Composable
fun MyAssetsRoute(
    paddingValues: PaddingValues,
    isReadOnlyArchive: Boolean,
    onBack: () -> Unit,
    onOpenBeanEditor: (Long?) -> Unit,
    onOpenGrinderEditor: (Long?) -> Unit,
    onOpenRecipeEditor: (Long?) -> Unit,
    onOpenRecordDraft: (Long) -> Unit,
    onStartBeanRecord: (Long) -> Unit,
    onStartRecipeRecord: (Long) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MyAssetsScreen(
        paddingValues = paddingValues,
        isReadOnlyArchive = isReadOnlyArchive,
        uiState = uiState,
        onBack = onBack,
        onOpenBeanEditor = onOpenBeanEditor,
        onOpenGrinderEditor = onOpenGrinderEditor,
        onOpenRecipeEditor = onOpenRecipeEditor,
        onOpenRecordDraft = onOpenRecordDraft,
        onStartBeanRecord = onStartBeanRecord,
        onStartRecipeRecord = onStartRecipeRecord,
    )
}

@Composable
fun MyDataRoute(
    paddingValues: PaddingValues,
    currentArchive: ArchiveSummary?,
    isReadOnlyArchive: Boolean,
    onBack: () -> Unit,
    onOpenArchiveSheet: () -> Unit,
    viewModel: MyDataViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingBackupPayload by remember { mutableStateOf<FileExportPayload?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = pendingBackupPayload
        pendingBackupPayload = null
        isWorking = false
        if (uri == null || payload == null) {
            operationMessage = "已取消导出。"
            return@rememberLauncherForActivityResult
        }
        operationMessage = context.writeTextToUri(uri, payload.content)
            .fold(
                onSuccess = { "备份已导出。" },
                onFailure = { error -> "备份导出失败：${error.message ?: "未知错误"}" },
            )
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isWorking = true
            operationMessage = context.readTextFromUri(uri)
                .fold(
                    onSuccess = { content ->
                        viewModel.restoreBackup(content).message
                    },
                    onFailure = { error ->
                        "恢复失败：${error.message ?: "未知错误"}"
                    },
                )
            isWorking = false
        }
    }
    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = "数据",
            subtitle = currentArchive?.archive?.name,
            eyebrow = "QOFFEE / MINE / DATA",
        )
        OutlinedButton(onClick = onBack) {
            Text("返回")
        }
        SectionCard(title = "当前存档") {
            Text(
                text = currentArchive?.archive?.name ?: "当前无存档",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(text = currentArchive?.archive?.type?.displayName ?: "普通存档")
                StatChip(text = "记录 ${currentArchive?.recordCount ?: 0}")
                StatChip(text = "豆子 ${currentArchive?.beanCount ?: 0}")
            }
            if (isReadOnlyArchive) {
                StatChip(text = "只读示范存档")
            }
            OutlinedButton(onClick = onOpenArchiveSheet) {
                Text("管理存档")
            }
        }
        SectionCard(title = "Backup & Restore") {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        isWorking = true
                        runCatching {
                            operationMessage = "正在准备备份文件…"
                            val payload = viewModel.exportBackup()
                            pendingBackupPayload = payload
                            backupLauncher.launch(payload.fileName)
                        }.onFailure { error ->
                            pendingBackupPayload = null
                            isWorking = false
                            operationMessage = "备份导出失败：${error.message ?: "未知错误"}"
                        }
                    }
                },
                enabled = !isWorking,
            ) {
                Text(if (isWorking) "处理中…" else "导出备份 JSON")
            }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*")) },
                enabled = !isWorking,
            ) {
                Text("从备份恢复")
            }
            operationMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MySubscriptionRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val entitlements = uiState.entitlements
    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = "订阅",
            subtitle = null,
            eyebrow = "QOFFEE / MINE / SUBSCRIPTION",
        )
        OutlinedButton(onClick = onBack) {
            Text("返回")
        }
        SectionCard(title = "当前计划") {
            StatChip(text = entitlements.tier.displayName)
            Text(
                text = "基础记录、学习和图表功能可用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (entitlements.tier == EntitlementTier.PRO) {
                    "Pro 预览已开启：高级复盘、实验历史、云同步和分享卡会显示为已解锁。"
                } else {
                    "Free 保留完整本地记录、配方、基础复盘和本地备份。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entitlements.unlockedFeatures.forEach { feature ->
                    StatChip(text = feature)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.setPreviewTier(EntitlementTier.FREE) }) {
                    Text("Free")
                }
                Button(onClick = { viewModel.setPreviewTier(EntitlementTier.PRO) }) {
                    Text("Pro 预览")
                }
            }
        }
        SectionCard(title = "Pro 预览") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entitlements.proHighlights.forEach { feature ->
                    StatChip(text = feature)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountSyncSection(
    syncState: SyncState,
    email: String,
    password: String,
    isWorking: Boolean,
    isReadOnlyArchive: Boolean,
    operationMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegister: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onSnapshot: () -> Unit,
    onKeepRemote: (String) -> Unit,
    onKeepLocal: (String) -> Unit,
) {
    SectionCard(
        title = "账号与云同步",
        subtitle = "登录后可同步本机记录、配方、豆子和快照。",
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(text = syncState.backendLabel)
            StatChip(text = syncState.archiveScope)
            StatChip(text = syncState.phase.displayName)
        }

        if (syncState.account == null) {
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("邮箱") },
                singleLine = true,
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = { Text("至少 8 位。登录和注册按钮会一直可点，具体校验由后端返回。") },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onSignIn,
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("登录")
                }
                OutlinedButton(
                    onClick = onRegister,
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("注册")
                }
            }
        } else {
            Text(
                text = "账号：${syncState.account.email}",
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                syncState.lastPushedAt?.let { StatChip(text = "上传 ${formatTimestamp(it)}") }
                syncState.lastPulledAt?.let { StatChip(text = "拉取 ${formatTimestamp(it)}") }
                syncState.lastSnapshot?.let { snapshot ->
                    StatChip(text = "快照 ${formatTimestamp(snapshot.createdAt)}")
                    StatChip(text = "${snapshot.byteSize} bytes")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPush,
                    enabled = !isWorking && !isReadOnlyArchive,
                ) {
                    Text("上传本机")
                }
                OutlinedButton(
                    onClick = onPull,
                    enabled = !isWorking,
                ) {
                    Text("拉取远端")
                }
                OutlinedButton(
                    onClick = onSnapshot,
                    enabled = !isWorking && !isReadOnlyArchive,
                ) {
                    Text("创建快照")
                }
                OutlinedButton(
                    onClick = onSignOut,
                    enabled = !isWorking,
                ) {
                    Text("退出")
                }
            }
        }

        (operationMessage ?: syncState.lastMessage)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (syncState.pendingConflicts.isEmpty()) {
            Text(
                text = "当前没有待处理冲突。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            syncState.pendingConflicts.forEach { conflict ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(conflict.summary, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onKeepRemote(conflict.id) }, enabled = !isWorking) {
                                Text("保留远端")
                            }
                            OutlinedButton(onClick = { onKeepLocal(conflict.id) }, enabled = !isWorking) {
                                Text("保留本机")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    paddingValues: PaddingValues,
    currentArchive: ArchiveSummary?,
    uiState: ProfileUiState,
    syncState: SyncState,
    syncEmail: String,
    syncPassword: String,
    isSyncWorking: Boolean,
    syncOperationMessage: String?,
    onSyncEmailChange: (String) -> Unit,
    onSyncPasswordChange: (String) -> Unit,
    onRegister: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onSnapshot: () -> Unit,
    onKeepRemote: (String) -> Unit,
    onKeepLocal: (String) -> Unit,
    onOpenArchiveSheet: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLearning: () -> Unit,
    onOpenSubscription: () -> Unit,
) {
    DashboardPage(
        paddingValues = paddingValues,
        testTag = QoffeeTestTags.MY_SCREEN,
    ) {
        PageHeader(
            title = "我的",
            subtitle = currentArchive?.archive?.name,
            eyebrow = "QOFFEE / MINE",
            trailing = {
                OutlinedButton(onClick = onOpenArchiveSheet) {
                    Text("存档管理")
                }
            },
        )
        DashboardArtworkBanner(
            imageRes = R.drawable.art_cloud_sync,
            height = 118.dp,
        )

        AccountSyncSection(
            syncState = syncState,
            email = syncEmail,
            password = syncPassword,
            isWorking = isSyncWorking || syncState.isBusy,
            isReadOnlyArchive = currentArchive?.archive?.isReadOnly == true,
            operationMessage = syncOperationMessage,
            onEmailChange = onSyncEmailChange,
            onPasswordChange = onSyncPasswordChange,
            onRegister = onRegister,
            onSignIn = onSignIn,
            onSignOut = onSignOut,
            onPush = onPush,
            onPull = onPull,
            onSnapshot = onSnapshot,
            onKeepRemote = onKeepRemote,
            onKeepLocal = onKeepLocal,
        )

        SectionCard(title = "目录") {
            MenuListItem(
                title = "资产",
                subtitle = "豆子 / 磨豆机 / 配方",
                icon = Icons.Outlined.FolderCopy,
                onClick = onOpenAssets,
            )
            MenuListItem(
                title = "数据",
                subtitle = "存档 / 统计概览",
                icon = Icons.Outlined.AutoGraph,
                onClick = onOpenData,
            )
            MenuListItem(
                title = "设置",
                subtitle = "记录 / 分析 / 导航",
                icon = Icons.Outlined.Settings,
                onClick = onOpenSettings,
                testTag = QoffeeTestTags.PROFILE_SETTINGS,
            )
            MenuListItem(
                title = "学习",
                subtitle = "课程 / 术语 / 排查",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                onClick = onOpenLearning,
            )
            MenuListItem(
                title = "订阅",
                subtitle = "Free / Pro",
                icon = Icons.Outlined.Person,
                onClick = onOpenSubscription,
            )
        }

        SectionCard(title = "概览") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(text = "豆子 ${uiState.beans.size}")
                StatChip(text = "磨豆机 ${uiState.grinders.size}")
                StatChip(text = "配方 ${uiState.recipes.size}")
            }
            if (uiState.inventory.isNotEmpty()) {
                Text(
                    text = "已启用库存的豆子 ${uiState.inventory.size} 个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.shareCards.isNotEmpty()) {
            SectionCard(
                title = "分享验证",
                subtitle = "先用分享卡和配方导入链接验证轻量社区能力，所有公开内容都应可撤回。",
            ) {
                uiState.shareCards.take(3).forEach { card ->
                    ShareCardPreview(card = card)
                }
            }
        }
    }
}

@Composable
private fun ShareCardPreview(card: ShareCard) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = card.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                card.badge?.let { StatChip(text = it) }
            }
            if (card.body.isNotBlank()) {
                Text(
                    text = card.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            card.importHint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyAssetsScreen(
    paddingValues: PaddingValues,
    isReadOnlyArchive: Boolean,
    uiState: ProfileUiState,
    onBack: () -> Unit,
    onOpenBeanEditor: (Long?) -> Unit,
    onOpenGrinderEditor: (Long?) -> Unit,
    onOpenRecipeEditor: (Long?) -> Unit,
    onOpenRecordDraft: (Long) -> Unit,
    onStartBeanRecord: (Long) -> Unit,
    onStartRecipeRecord: (Long) -> Unit,
) {
    var pendingAction by remember { mutableStateOf<AssetRecordAction?>(null) }

    fun openRecordSource(source: RecordPrefillSource) {
        when (source) {
            is RecordPrefillSource.Bean -> onStartBeanRecord(source.beanId)
            is RecordPrefillSource.Recipe -> onStartRecipeRecord(source.recipeId)
            RecordPrefillSource.Draft -> uiState.activeDraft?.let { onOpenRecordDraft(it.id) }
            else -> Unit
        }
    }

    fun handleRecordSource(source: RecordPrefillSource) {
        when (resolveRecordDraftLaunchBehavior(uiState.activeDraft, source)) {
            RecordDraftLaunchBehavior.CREATE_NEW -> openRecordSource(source)
            RecordDraftLaunchBehavior.CONTINUE_CURRENT -> openRecordSource(RecordPrefillSource.Draft)
            RecordDraftLaunchBehavior.CONFIRM_REPLACE -> {
                pendingAction = when (source) {
                    is RecordPrefillSource.Bean -> AssetRecordAction.Bean(source.beanId)
                    is RecordPrefillSource.Recipe -> AssetRecordAction.Recipe(source.recipeId)
                    else -> null
                }
            }
        }
    }

    DashboardPage(
        paddingValues = paddingValues,
        testTag = QoffeeTestTags.PROFILE_ASSETS,
    ) {
        PageHeader(
            title = "资产",
            subtitle = null,
            eyebrow = "QOFFEE / MINE / ASSETS",
        )
        OutlinedButton(onClick = onBack) {
            Text("返回")
        }
        DashboardArtworkBanner(
            imageRes = R.drawable.art_assets_recipe,
            height = 118.dp,
        )

        SectionCard(title = "快捷操作", subtitle = "资产仍可显式管理，但真正的使用动作都应该回到记录。") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenBeanEditor(null) }, enabled = !isReadOnlyArchive) {
                    Text("新增豆子")
                }
                OutlinedButton(onClick = { onOpenGrinderEditor(null) }, enabled = !isReadOnlyArchive) {
                    Text("新增磨豆机")
                }
                OutlinedButton(onClick = { onOpenRecipeEditor(null) }, enabled = !isReadOnlyArchive) {
                    Text("新增配方")
                }
            }
        }

        SectionCard(title = "豆子", subtitle = "点击条目编辑资产，点击“记一杯”直接回到记录流。") {
            if (uiState.beans.isEmpty()) {
                EmptyStateCard(
                    title = "还没有豆子资产",
                    subtitle = "先创建一个豆子资产，后面记录和库存都会直接复用。",
                )
            } else {
                uiState.beans.forEach { bean ->
                    AssetListItem(
                        title = bean.name,
                        subtitle = buildString {
                            append(bean.processMethod.displayName)
                            append(" · ")
                            append(bean.roastLevel.displayName)
                            bean.initialStockG?.let { stock ->
                                append(" · ")
                                append(formatNumber(stock))
                                append("g 库存")
                            }
                        },
                        enabled = !isReadOnlyArchive,
                        onClick = { onOpenBeanEditor(bean.id) },
                        secondaryActionLabel = "记一杯",
                        onSecondaryAction = {
                            if (!isReadOnlyArchive) {
                                handleRecordSource(RecordPrefillSource.Bean(bean.id))
                            }
                        },
                    )
                }
            }
        }

        SectionCard(title = "磨豆机") {
            if (uiState.grinders.isEmpty()) {
                EmptyStateCard(
                    title = "还没有磨豆机资产",
                    subtitle = "把设备参数维护好，记录时输入会更顺手。",
                )
            } else {
                uiState.grinders.forEach { grinder ->
                    AssetListItem(
                        title = grinder.name,
                        subtitle = "${formatNumber(grinder.minSetting)}-${formatNumber(grinder.maxSetting)} ${grinder.unitLabel}",
                        enabled = !isReadOnlyArchive,
                        onClick = { onOpenGrinderEditor(grinder.id) },
                    )
                }
            }
        }

        SectionCard(title = "配方", subtitle = "配方来自记录客观参数复用，点击“记一杯”会直接开一条预填记录。") {
            if (uiState.recipes.isEmpty()) {
                EmptyStateCard(
                    title = "还没有配方",
                    subtitle = "先在记录页或记录详情里把一条真实记录设为配方，再回来管理它。",
                )
            } else {
                uiState.recipes.forEach { recipe ->
                    AssetListItem(
                        title = recipe.name,
                        subtitle = buildString {
                            append(recipe.brewMethod?.displayName ?: "未指定方式")
                            recipe.beanNameSnapshot?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        enabled = !isReadOnlyArchive,
                        onClick = { onOpenRecipeEditor(recipe.id) },
                        secondaryActionLabel = "记一杯",
                        onSecondaryAction = {
                            if (!isReadOnlyArchive) {
                                handleRecordSource(RecordPrefillSource.Recipe(recipe.id))
                            }
                        },
                    )
                }
            }
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("当前有未完成草稿") },
            text = {
                Text(
                    when (action) {
                        is AssetRecordAction.Bean -> "你可以继续当前草稿，或放弃它并改为记录这颗豆子。"
                        is AssetRecordAction.Recipe -> "你可以继续当前草稿，或放弃它并从这个配方开始。"
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    when (action) {
                        is AssetRecordAction.Bean -> onStartBeanRecord(action.beanId)
                        is AssetRecordAction.Recipe -> onStartRecipeRecord(action.recipeId)
                    }
                    pendingAction = null
                }) {
                    Text("替换草稿并继续")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    uiState.activeDraft?.let { onOpenRecordDraft(it.id) }
                    pendingAction = null
                }) {
                    Text("继续当前草稿")
                }
            },
        )
    }
}

@Composable
private fun MenuListItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = icon, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssetListItem(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (enabled && secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(onClick = onSecondaryAction) {
                        Text(secondaryActionLabel)
                    }
                }
                if (enabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private sealed interface AssetRecordAction {
    data class Bean(val beanId: Long) : AssetRecordAction
    data class Recipe(val recipeId: Long) : AssetRecordAction
}

private fun formatNumber(value: Double): String {
    return String.format(java.util.Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}

private fun formatTimestamp(value: Long): String {
    return SimpleDateFormat("M/d HH:mm", java.util.Locale.CHINA).format(Date(value))
}

private fun Context.writeTextToUri(uri: android.net.Uri, content: String): Result<Unit> {
    return runCatching {
        val outputStream = contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法打开目标文件。")
        outputStream.bufferedWriter().use { writer ->
            writer.write(content)
        }
    }
}

private fun Context.readTextFromUri(uri: android.net.Uri): Result<String> {
    return runCatching {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取备份文件。")
        inputStream.bufferedReader().use { reader ->
            reader.readText()
        }
    }
}
