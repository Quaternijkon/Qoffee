# Qoffee Record Experience 2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Qoffee into a high-polish, record-centered coffee workbench that reduces the effort to start, complete, reuse, and review brew records without removing existing functionality.

**Architecture:** Keep `CoffeeRecord` as the product center. Add focused presentation models and reusable Compose components for the record workbench, record editor, record report, and review insights; route all start/reuse actions through the existing `RecordPrefillSource` and `RecordEditorEntry` flow. Avoid Room schema changes and preserve current repositories unless a task explicitly identifies a missing projection that must be added.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt, Room-backed repositories, AndroidX Navigation Compose, existing Qoffee image resources, JUnit/Truth unit tests, Android Compose UI tests.

---

## Product Contract

This plan implements the UX proposal as an execution contract for an implementation agent.

The implementation must preserve every existing user capability while simplifying the high-frequency path:

1. Start a record from blank, active draft, bean inventory, recipe, historical record, guide/session, or experiment context.
2. Continue an active draft with low-friction conflict handling.
3. Complete objective parameters and subjective tasting feedback with fewer keyboard-dependent steps.
4. Reuse completed records as recipes, overwrite source recipes, duplicate comparable records, and create guides.
5. Review historical records and analytics as natural follow-up actions from records.
6. Keep profile/assets as management surfaces, not the primary creation path.

## Scope Boundaries

In scope:

- Visual system refinement for record-centered screens.
- Reusable Compose components for action panels, source rails, parameter summaries, smart numeric input, tasting score panels, report headers, and insight action cards.
- Records workbench restructuring.
- Record editor interaction restructuring.
- Record detail report restructuring.
- Analytics/review first-screen restructuring.
- Navigation/test tag updates needed by the new interaction path.
- Unit and UI tests for presentation logic and direct-entry interactions.

Out of scope:

- New cloud sync behavior.
- New AI generation behavior.
- New Room schema fields or migrations.
- Removing existing features.
- Replacing the bottom navigation architecture.
- Building a marketing landing page.
- Adding decorative animation that does not communicate state, hierarchy, or input feedback.

## Current Repo Anchors

Main files to inspect before implementing:

- `app/src/main/java/com/qoffee/feature/records/RecordsScreen.kt`
- `app/src/main/java/com/qoffee/feature/records/RecordEditorScreen.kt`
- `app/src/main/java/com/qoffee/feature/records/RecordDetailScreen.kt`
- `app/src/main/java/com/qoffee/feature/records/RecordPresentation.kt`
- `app/src/main/java/com/qoffee/feature/analytics/AnalyticsScreen.kt`
- `app/src/main/java/com/qoffee/feature/profile/ProfileScreen.kt`
- `app/src/main/java/com/qoffee/ui/QoffeeApp.kt`
- `app/src/main/java/com/qoffee/ui/TestTags.kt`
- `app/src/main/java/com/qoffee/ui/components/V2Components.kt`
- `app/src/main/java/com/qoffee/ui/components/NumericInputComponents.kt`
- `app/src/main/java/com/qoffee/ui/components/InlineNumericControls.kt`
- `app/src/main/java/com/qoffee/ui/components/CommonComponents.kt`
- `app/src/main/java/com/qoffee/ui/components/Charts.kt`
- `app/src/main/java/com/qoffee/ui/theme/Color.kt`
- `app/src/main/java/com/qoffee/ui/theme/Tokens.kt`
- `app/src/main/java/com/qoffee/ui/theme/Theme.kt`
- `app/src/main/java/com/qoffee/ui/theme/Type.kt`
- `app/src/main/java/com/qoffee/ui/navigation/QoffeeDestinations.kt`

Existing image resources to reuse:

- `app/src/main/res/drawable-nodpi/art_record_workbench.jpg`
- `app/src/main/res/drawable-nodpi/art_minimal_record_workbench.jpg`
- `app/src/main/res/drawable-nodpi/art_review_insight.jpg`
- `app/src/main/res/drawable-nodpi/art_minimal_review_insight.jpg`
- `app/src/main/res/drawable-nodpi/art_assets_recipe.jpg`
- `app/src/main/res/drawable-nodpi/art_minimal_assets_recipe.jpg`
- `app/src/main/res/drawable-nodpi/art_experiment_lab.jpg`
- `app/src/main/res/drawable-nodpi/art_minimal_experiment_lab.jpg`

Existing tests to extend:

- `app/src/test/java/com/qoffee/feature/records/RecordPresentationTest.kt`
- `app/src/test/java/com/qoffee/ui/components/NumericInputComponentsTest.kt`
- `app/src/test/java/com/qoffee/ui/components/WaterCurveComponentsTest.kt`
- `app/src/androidTest/java/com/qoffee/NavigationSmokeTest.kt`

Validation commands:

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

If the connected Android test environment is unavailable, run unit tests and `assembleDebug`, then record the Android test blocker with the exact device/emulator state.

---

## File Structure Plan

Create focused presentation and component files instead of growing the largest screens further.

- Create `app/src/main/java/com/qoffee/feature/records/RecordWorkbenchPresentation.kt`
  - Owns workbench action priority, source rail items, pending feedback items, and copy for the first screen.
  - Depends only on domain models and existing presentation types.

- Create `app/src/main/java/com/qoffee/feature/records/RecordEditorPresentation.kt`
  - Owns editor section state, completion progress, parameter summary, missing-field messages, and default quick values.
  - Keeps UI logic out of `RecordEditorScreen.kt`.

- Create `app/src/main/java/com/qoffee/feature/records/RecordReportPresentation.kt`
  - Owns detail-page report header, objective snapshot, subjective summary, comparison summary, and reuse action availability.

- Create `app/src/main/java/com/qoffee/feature/analytics/ReviewInsightPresentation.kt`
  - Owns review insight cards and action labels derived from `ReviewUiState`.

- Create `app/src/main/java/com/qoffee/ui/components/RecordExperienceComponents.kt`
  - Shared high-polish record UX components:
    - `HeroActionPanel`
    - `RecordSourceRail`
    - `ParameterSummaryStrip`
    - `SmartNumberControl`
    - `TastingScorePanel`
    - `RecordReportHeader`
    - `InsightActionCard`
    - `StickyRecordActionBar`

- Modify `app/src/main/java/com/qoffee/ui/components/NumericInputComponents.kt`
  - Add any missing pure helper functions needed by `SmartNumberControl`.
  - Keep numeric normalization and stepping testable.

- Modify `app/src/main/java/com/qoffee/ui/components/V2Components.kt`
  - Keep generic page/card primitives only.
  - Move record-specific primitives into `RecordExperienceComponents.kt`.

- Modify `app/src/main/java/com/qoffee/ui/theme/Color.kt`, `Tokens.kt`, `Theme.kt`, `Type.kt`
  - Add refined neutral/professional palette tokens while preserving Classic and Minimal compatibility.

- Modify `app/src/main/java/com/qoffee/feature/records/RecordsScreen.kt`
  - Use workbench presentation models and new components.
  - Keep existing `handlePrefillRequest` behavior.

- Modify `app/src/main/java/com/qoffee/feature/records/RecordEditorScreen.kt`
  - Use editor presentation models and new input/summary components.
  - Keep existing ViewModel persistence methods.

- Modify `app/src/main/java/com/qoffee/feature/records/RecordDetailScreen.kt`
  - Use report presentation models and new report components.
  - Keep existing reuse and delete actions.

- Modify `app/src/main/java/com/qoffee/feature/analytics/AnalyticsScreen.kt`
  - Use review insight presentation models and action cards.
  - Keep existing filters, export, sample, trend, experiment, and navigation behavior.

- Modify `app/src/main/java/com/qoffee/ui/TestTags.kt`
  - Add stable tags for new high-frequency surfaces.

---

## Implementation Principles

- Treat `CoffeeRecord` as the primary product object.
- Do not add schema fields.
- Do not duplicate draft creation logic; use `RecordPrefillSource`, `DraftReplacePolicy`, and existing repository capabilities.
- Every visual upgrade must improve scanability, tap efficiency, continuity, state feedback, or review quality.
- Keep Chinese UX copy clear and task-serving.
- Do not use oversized landing-page hero sections.
- Do not add decorative gradient orbs or bokeh backgrounds.
- Keep cards at 8dp radius or the nearest existing theme shape that maps to it.
- Avoid cards inside cards; use full-width sections, rails, rows, and unframed layouts.
- Use existing bitmap assets sparingly and only where they support context.
- Do not make the UI a single brown/coffee monochrome palette.
- Ensure text fits on small screens; every button label must remain readable.

---

### Task 1: Establish Presentation Models for the Record Workbench

**Files:**

- Create: `app/src/main/java/com/qoffee/feature/records/RecordWorkbenchPresentation.kt`
- Modify: `app/src/test/java/com/qoffee/feature/records/RecordPresentationTest.kt`

- [ ] **Step 1: Write failing tests for first-screen action priority**

Add tests to `RecordPresentationTest.kt` that assert these priorities:

```kotlin
@Test
fun buildWorkbenchHeroPrioritizesActiveDraft() {
    val hero = buildRecordWorkbenchHero(
        activeDraft = CoffeeRecord(id = 9L, status = RecordStatus.DRAFT, beanNameSnapshot = "Draft Bean"),
        inventory = emptyList(),
        recipes = emptyList(),
        recentRecords = emptyList(),
        scoredRecords = emptyList(),
    )

    assertThat(hero.title).contains("继续")
    assertThat(hero.primaryAction).isEqualTo(WorkbenchAction.ResumeDraft(9L))
}

@Test
fun buildWorkbenchHeroStartsFirstCupFromInventoryWhenNoDraftExists() {
    val hero = buildRecordWorkbenchHero(
        activeDraft = null,
        inventory = listOf(
            BeanInventory(
                beanId = 20L,
                beanName = "Ethiopia Natural",
                remainingStockG = 120.0,
                remainingPercentage = 80,
            ),
        ),
        recipes = emptyList(),
        recentRecords = emptyList(),
        scoredRecords = emptyList(),
    )

    assertThat(hero.title).contains("第一杯")
    assertThat(hero.primaryAction).isEqualTo(WorkbenchAction.StartBean(20L))
}

@Test
fun buildWorkbenchHeroSuggestsReviewWhenEnoughScoredRecordsExist() {
    val records = listOf(
        record(id = 1L, brewedAt = 1_000L, overall = 4, waterTemp = 90.0),
        record(id = 2L, brewedAt = 2_000L, overall = 5, waterTemp = 91.0),
        record(id = 3L, brewedAt = 3_000L, overall = 3, waterTemp = 92.0),
    )

    val hero = buildRecordWorkbenchHero(
        activeDraft = null,
        inventory = emptyList(),
        recipes = emptyList(),
        recentRecords = records,
        scoredRecords = records,
    )

    assertThat(hero.secondaryActions.map { it.action }).contains(WorkbenchAction.OpenAnalysis)
}
```

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.records.RecordPresentationTest" --no-daemon --console=plain
```

Expected: FAIL with unresolved references for `buildRecordWorkbenchHero`, `WorkbenchAction`, and related models.

- [ ] **Step 2: Implement workbench presentation models**

Create `RecordWorkbenchPresentation.kt` with:

```kotlin
package com.qoffee.feature.records

import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RecipeTemplate

internal data class WorkbenchHero(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val primaryLabel: String,
    val primaryAction: WorkbenchAction,
    val secondaryActions: List<WorkbenchSecondaryAction> = emptyList(),
)

internal data class WorkbenchSecondaryAction(
    val label: String,
    val action: WorkbenchAction,
)

internal data class WorkbenchSourceItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val action: WorkbenchAction,
)

internal data class PendingFeedbackItem(
    val recordId: Long,
    val title: String,
    val subtitle: String,
)

internal sealed interface WorkbenchAction {
    data object StartBlank : WorkbenchAction
    data object OpenAnalysis : WorkbenchAction
    data class ResumeDraft(val recordId: Long) : WorkbenchAction
    data class StartBean(val beanId: Long) : WorkbenchAction
    data class StartRecipe(val recipeId: Long) : WorkbenchAction
    data class DuplicateRecord(val recordId: Long) : WorkbenchAction
    data class OpenRecord(val recordId: Long) : WorkbenchAction
}

internal fun buildRecordWorkbenchHero(
    activeDraft: CoffeeRecord?,
    inventory: List<BeanInventory>,
    recipes: List<RecipeTemplate>,
    recentRecords: List<CoffeeRecord>,
    scoredRecords: List<CoffeeRecord>,
): WorkbenchHero {
    val secondary = buildList {
        recentRecords.firstOrNull { it.status == RecordStatus.COMPLETED }?.let { record ->
            add(WorkbenchSecondaryAction("复刻上一杯", WorkbenchAction.DuplicateRecord(record.id)))
        }
        if (scoredRecords.size >= 3) {
            add(WorkbenchSecondaryAction("查看复盘", WorkbenchAction.OpenAnalysis))
        }
    }.take(2)

    activeDraft?.let { draft ->
        return WorkbenchHero(
            eyebrow = "今日行动",
            title = "继续当前草稿",
            subtitle = draft.beanNameSnapshot?.let { "已记录到 $it，可直接补全参数或感受。" }
                ?: "有一杯还没完成，继续它比新开一杯更省心。",
            primaryLabel = "继续这杯",
            primaryAction = WorkbenchAction.ResumeDraft(draft.id),
            secondaryActions = secondary,
        )
    }

    inventory.firstOrNull { it.remainingStockG > 0.0 }?.let { bean ->
        return WorkbenchHero(
            eyebrow = "今日行动",
            title = if (recentRecords.isEmpty()) "从第一杯开始" else "用库存豆开始一杯",
            subtitle = "${bean.beanName} 还有 ${formatWorkbenchNumber(bean.remainingStockG)}g，点一下直接生成记录草稿。",
            primaryLabel = "用这支豆开始",
            primaryAction = WorkbenchAction.StartBean(bean.beanId),
            secondaryActions = secondary,
        )
    }

    recipes.firstOrNull()?.let { recipe ->
        return WorkbenchHero(
            eyebrow = "今日行动",
            title = "复用常用配方",
            subtitle = "${recipe.name} 会预填客观参数，你只需要按本次冲煮微调。",
            primaryLabel = "从配方开始",
            primaryAction = WorkbenchAction.StartRecipe(recipe.id),
            secondaryActions = secondary,
        )
    }

    recentRecords.firstOrNull { it.status == RecordStatus.COMPLETED }?.let { record ->
        return WorkbenchHero(
            eyebrow = "今日行动",
            title = "复刻最近一杯",
            subtitle = record.beanNameSnapshot?.let { "复制 $it 的参数，再按本次结果微调。" }
                ?: "复制上一杯参数，再按本次结果微调。",
            primaryLabel = "复刻下一杯",
            primaryAction = WorkbenchAction.DuplicateRecord(record.id),
            secondaryActions = secondary,
        )
    }

    return WorkbenchHero(
        eyebrow = "今日行动",
        title = "记录第一杯咖啡",
        subtitle = "先保存豆子、方法和时间，感受和高级参数可以之后补全。",
        primaryLabel = "添加记录",
        primaryAction = WorkbenchAction.StartBlank,
        secondaryActions = secondary,
    )
}

internal fun buildWorkbenchSourceItems(
    activeDraft: CoffeeRecord?,
    inventory: List<BeanInventory>,
    recipes: List<RecipeTemplate>,
    recentRecords: List<CoffeeRecord>,
): List<WorkbenchSourceItem> = buildList {
    activeDraft?.let {
        add(
            WorkbenchSourceItem(
                title = it.beanNameSnapshot ?: "未完成草稿",
                subtitle = "继续填写这杯",
                badge = "草稿",
                action = WorkbenchAction.ResumeDraft(it.id),
            ),
        )
    }
    inventory.filter { it.remainingStockG > 0.0 }.take(4).forEach { bean ->
        add(
            WorkbenchSourceItem(
                title = bean.beanName,
                subtitle = "剩余 ${formatWorkbenchNumber(bean.remainingStockG)}g",
                badge = "豆子",
                action = WorkbenchAction.StartBean(bean.beanId),
            ),
        )
    }
    recipes.take(4).forEach { recipe ->
        add(
            WorkbenchSourceItem(
                title = recipe.name,
                subtitle = recipe.brewMethod?.displayName ?: "预填客观参数",
                badge = "配方",
                action = WorkbenchAction.StartRecipe(recipe.id),
            ),
        )
    }
    recentRecords.filter { it.status == RecordStatus.COMPLETED }.take(4).forEach { record ->
        add(
            WorkbenchSourceItem(
                title = record.beanNameSnapshot ?: record.brewMethod?.displayName ?: "历史记录",
                subtitle = record.subjectiveEvaluation?.overall?.let { "总评 $it/5" } ?: "复制参数微调",
                badge = "历史",
                action = WorkbenchAction.DuplicateRecord(record.id),
            ),
        )
    }
}.distinctBy { "${it.badge}:${it.title}:${it.subtitle}" }.take(10)

internal fun buildPendingFeedbackItems(records: List<CoffeeRecord>): List<PendingFeedbackItem> {
    return records
        .filter { it.status == RecordStatus.COMPLETED && it.subjectiveEvaluation?.overall == null }
        .sortedByDescending { it.brewedAt }
        .take(3)
        .map { record ->
            PendingFeedbackItem(
                recordId = record.id,
                title = record.beanNameSnapshot ?: record.brewMethod?.displayName ?: "待补感受",
                subtitle = "补一个总评，就能进入复盘样本。",
            )
        }
}

private fun formatWorkbenchNumber(value: Double): String {
    return if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.CHINA, "%.1f", value)
    }
}
```

- [ ] **Step 3: Run presentation tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.records.RecordPresentationTest" --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/qoffee/feature/records/RecordWorkbenchPresentation.kt app/src/test/java/com/qoffee/feature/records/RecordPresentationTest.kt
git commit -m "feat: add record workbench presentation models"
```

---

### Task 2: Add Record Experience UI Components

**Files:**

- Create: `app/src/main/java/com/qoffee/ui/components/RecordExperienceComponents.kt`
- Modify: `app/src/main/java/com/qoffee/ui/components/NumericInputComponents.kt`
- Modify: `app/src/test/java/com/qoffee/ui/components/NumericInputComponentsTest.kt`

- [ ] **Step 1: Add failing tests for ratio-linked numeric suggestions**

Add tests to `NumericInputComponentsTest.kt`:

```kotlin
@Test
fun buildWaterQuickValuesUsesDoseAndCommonRatios() {
    assertThat(buildWaterQuickValuesForDose("15", listOf(14, 15, 16)))
        .containsExactly("210", "225", "240")
        .inOrder()
}

@Test
fun buildWaterQuickValuesReturnsEmptyWhenDoseIsMissing() {
    assertThat(buildWaterQuickValuesForDose("", listOf(14, 15, 16))).isEmpty()
    assertThat(buildWaterQuickValuesForDose("abc", listOf(14, 15, 16))).isEmpty()
}
```

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.ui.components.NumericInputComponentsTest" --no-daemon --console=plain
```

Expected: FAIL with unresolved reference `buildWaterQuickValuesForDose`.

- [ ] **Step 2: Implement pure numeric helper**

Add this helper to `NumericInputComponents.kt` near the other internal helpers:

```kotlin
internal fun buildWaterQuickValuesForDose(
    doseText: String,
    ratios: List<Int>,
): List<String> {
    val dose = doseText.toDoubleOrNull() ?: return emptyList()
    if (dose <= 0.0) return emptyList()
    return ratios
        .map { ratio -> dose * ratio.toDouble() }
        .map { value ->
            if (value == value.toInt().toDouble()) {
                value.toInt().toString()
            } else {
                String.format(java.util.Locale.CHINA, "%.1f", value)
            }
        }
        .distinct()
}
```

- [ ] **Step 3: Create shared record experience components**

Create `RecordExperienceComponents.kt` with composables that can be used by the workbench, editor, detail, and analytics screens. The exact visual implementation may adapt to existing imports, but it must expose these signatures:

```kotlin
package com.qoffee.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qoffee.ui.theme.QoffeeDashboardTheme

@Composable
fun HeroActionPanel(
    eyebrow: String,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryActions: List<Pair<String, () -> Unit>> = emptyList(),
    enabled: Boolean = true,
) {
    val colors = QoffeeDashboardTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.panelStrong,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, colors.panelStrokeStrong.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.titleText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onPrimaryClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Text(primaryLabel)
            }
            if (secondaryActions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    secondaryActions.take(2).forEach { (label, action) ->
                        OutlinedButton(onClick = action, modifier = Modifier.weight(1f), enabled = enabled) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordSourceRail(
    items: List<RecordSourceRailItem>,
    onClick: (RecordSourceRailItem) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            PressableSourceCard(
                item = item,
                enabled = enabled,
                onClick = { onClick(item) },
            )
        }
    }
}

data class RecordSourceRailItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val key: String,
)

@Composable
private fun PressableSourceCard(
    item: RecordSourceRailItem,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = QoffeeDashboardTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        modifier = Modifier
            .width(236.dp)
            .clip(MaterialTheme.shapes.large)
            .graphicsLayer {
                scaleX = if (pressed) 0.985f else 1f
                scaleY = if (pressed) 0.985f else 1f
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        color = colors.panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, colors.panelStroke.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = colors.accentSoft, shape = CircleShape) {
                    Icon(
                        Icons.Outlined.Coffee,
                        contentDescription = null,
                        modifier = Modifier.padding(7.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(item.badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ParameterSummaryStrip(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Surface(
                color = QoffeeDashboardTheme.colors.panelMuted,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedContent(targetState = value, label = "parameter-$label") { animatedValue ->
                        Text(animatedValue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun TastingScorePanel(
    scores: List<TastingScoreItem>,
    onScoreSelected: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        scores.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    item.label,
                    modifier = Modifier.weight(0.28f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                (1..5).forEach { score ->
                    val selected = item.value == score
                    val container by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else QoffeeDashboardTheme.colors.panelMuted,
                        animationSpec = spring(),
                        label = "score-color-${item.key}-$score",
                    )
                    FilterChip(
                        selected = selected,
                        onClick = { onScoreSelected(item.key, score) },
                        label = { Text(score.toString()) },
                        modifier = Modifier.weight(0.14f),
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = container,
                            selectedContainerColor = container,
                        ),
                    )
                }
            }
        }
    }
}

data class TastingScoreItem(
    val key: String,
    val label: String,
    val value: Int?,
)

@Composable
fun RecordReportHeader(
    title: String,
    subtitle: String,
    scoreText: String,
    parameters: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    HeroActionPanel(
        eyebrow = "杯测报告",
        title = title,
        subtitle = subtitle,
        primaryLabel = scoreText,
        onPrimaryClick = {},
        modifier = modifier,
        enabled = false,
    )
    ParameterSummaryStrip(items = parameters)
}

@Composable
fun InsightActionCard(
    title: String,
    evidence: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = QoffeeDashboardTheme.colors.panel,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, QoffeeDashboardTheme.colors.panelStroke.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAction, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (secondaryLabel != null && onSecondaryAction != null) {
                    OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.weight(1f)) {
                        Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
```

If compile errors occur because a Material icon is unavailable, replace only that icon with an icon already used in the repo, such as `Icons.Outlined.PlayArrow`, `Icons.Outlined.Add`, or `Icons.Outlined.Restore`.

- [ ] **Step 4: Run component tests and compile**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.ui.components.NumericInputComponentsTest" --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected: PASS and build success.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/qoffee/ui/components/RecordExperienceComponents.kt app/src/main/java/com/qoffee/ui/components/NumericInputComponents.kt app/src/test/java/com/qoffee/ui/components/NumericInputComponentsTest.kt
git commit -m "feat: add record experience components"
```

---

### Task 3: Refine Theme Tokens for a Premium Utility UI

**Files:**

- Modify: `app/src/main/java/com/qoffee/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/qoffee/ui/theme/Tokens.kt`
- Modify: `app/src/main/java/com/qoffee/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/qoffee/ui/theme/Type.kt`

- [ ] **Step 1: Audit current palette use**

Run:

```powershell
rg -n "Color\\(0x|Espresso|Mocha|Copper|Latte|Crema|GoogleBlue|GoogleGreen|QoffeeDashboardColors" app/src/main/java/com/qoffee/ui app/src/main/java/com/qoffee/feature
```

Expected: Identify all direct palette references that affect the dashboard, navigation bar, and record surfaces.

- [ ] **Step 2: Add refined neutral and semantic palette tokens**

Modify `Color.kt` to add neutral/professional tokens while preserving existing colors:

```kotlin
val Paper = Color(0xFFFAFAF7)
val Porcelain = Color(0xFFFFFFFF)
val Mist = Color(0xFFE9EDF2)
val Zinc = Color(0xFF3F4652)
val Ink = Color(0xFF151922)
val Marine = Color(0xFF1D4E5F)
val MarineSoft = Color(0xFFDDECEF)
val Pine = Color(0xFF2E5C4D)
val PineSoft = Color(0xFFE1EEE8)
val AmberSoft = Color(0xFFFFF0D8)
val BerrySoft = Color(0xFFFFE3E1)
```

- [ ] **Step 3: Update dashboard token values without creating a one-note brown palette**

Modify `Tokens.kt` so the light dashboard colors use `Paper`, `Porcelain`, `Mist`, `Ink`, `Marine`, `MarineSoft`, `Pine`, and warm coffee tones only as supporting accents. Preserve dark and minimal modes unless they need contrast correction.

Required behavior:

- Light page background reads as neutral professional utility, not beige-only.
- Primary panels are mostly neutral.
- Coffee warmth appears in `accentSoft` or chart/status accents, not as the whole page.
- Minimal mode remains Google-like and clean.

- [ ] **Step 4: Run build**

Run:

```powershell
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected: Build success.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/qoffee/ui/theme/Color.kt app/src/main/java/com/qoffee/ui/theme/Tokens.kt app/src/main/java/com/qoffee/ui/theme/Theme.kt app/src/main/java/com/qoffee/ui/theme/Type.kt
git commit -m "style: refine qoffee dashboard theme tokens"
```

---

### Task 4: Rebuild the Records Workbench First Screen

**Files:**

- Modify: `app/src/main/java/com/qoffee/feature/records/RecordsScreen.kt`
- Modify: `app/src/main/java/com/qoffee/ui/TestTags.kt`
- Modify: `app/src/androidTest/java/com/qoffee/NavigationSmokeTest.kt`

- [ ] **Step 1: Add UI test expectations for the new workbench structure**

In `NavigationSmokeTest.kt`, update `brewScreenShowsRecordLoopWorkbench` to assert:

```kotlin
@Test
fun brewScreenShowsRecordLoopWorkbench() {
    composeRule.onNodeWithTag(QoffeeTestTags.BREW_SCREEN).fetchSemanticsNode()
    composeRule.onNodeWithText("记录工作台").fetchSemanticsNode()
    composeRule.onNodeWithText("今日行动").fetchSemanticsNode()
    composeRule.onNodeWithText("快速开始").fetchSemanticsNode()
}
```

Add tags to `TestTags.kt`:

```kotlin
const val BREW_HERO_ACTION = "brew_hero_action"
const val BREW_SOURCE_RAIL = "brew_source_rail"
const val BREW_PENDING_FEEDBACK = "brew_pending_feedback"
```

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
```

Expected: FAIL until the UI exposes the new text and tags. If no device is connected, continue implementation and validate later.

- [ ] **Step 2: Map workbench actions to existing navigation**

In `RecordsScreen.kt`, add a local mapper:

```kotlin
fun handleWorkbenchAction(action: WorkbenchAction) {
    when (action) {
        WorkbenchAction.StartBlank -> handlePrefillRequest(RecordPrefillSource.Blank)
        WorkbenchAction.OpenAnalysis -> onOpenAnalysis()
        is WorkbenchAction.ResumeDraft -> onOpenEditor(action.recordId, null, RecordEditorEntry.DRAFT, null, null)
        is WorkbenchAction.StartBean -> handlePrefillRequest(RecordPrefillSource.Bean(action.beanId))
        is WorkbenchAction.StartRecipe -> handlePrefillRequest(RecordPrefillSource.Recipe(action.recipeId))
        is WorkbenchAction.DuplicateRecord -> handlePrefillRequest(RecordPrefillSource.Record(action.recordId))
        is WorkbenchAction.OpenRecord -> onOpenDetail(action.recordId)
    }
}
```

This must reuse the existing draft conflict behavior.

- [ ] **Step 3: Replace the top of `RecordsScreen` with the hero action panel**

Build:

```kotlin
val completedRecentRecords = uiState.recentRecords.filter { it.status == RecordStatus.COMPLETED }
val workbenchHero = remember(
    uiState.activeDraft,
    uiState.inventory,
    uiState.recipes,
    uiState.recentRecords,
    uiState.subjectiveRecords,
) {
    buildRecordWorkbenchHero(
        activeDraft = uiState.activeDraft,
        inventory = uiState.inventory,
        recipes = uiState.recipes,
        recentRecords = uiState.recentRecords,
        scoredRecords = uiState.subjectiveRecords,
    )
}
```

Render immediately after `PageHeader`:

```kotlin
HeroActionPanel(
    eyebrow = workbenchHero.eyebrow,
    title = workbenchHero.title,
    subtitle = workbenchHero.subtitle,
    primaryLabel = workbenchHero.primaryLabel,
    onPrimaryClick = { handleWorkbenchAction(workbenchHero.primaryAction) },
    secondaryActions = workbenchHero.secondaryActions.map { secondary ->
        secondary.label to { handleWorkbenchAction(secondary.action) }
    },
    enabled = !isReadOnlyArchive || !workbenchHero.primaryAction.requiresWritableArchive(),
    modifier = Modifier.testTag(QoffeeTestTags.BREW_HERO_ACTION),
)
```

If `requiresWritableArchive()` is private in `RecordPresentation.kt`, either move it to a shared internal function or add an equivalent internal extension for `WorkbenchAction`:

```kotlin
private fun WorkbenchAction.requiresWritableArchive(): Boolean = when (this) {
    WorkbenchAction.OpenAnalysis,
    is WorkbenchAction.OpenRecord,
    -> false
    WorkbenchAction.StartBlank,
    is WorkbenchAction.ResumeDraft,
    is WorkbenchAction.StartBean,
    is WorkbenchAction.StartRecipe,
    is WorkbenchAction.DuplicateRecord,
    -> true
}
```

- [ ] **Step 4: Add the quick-start source rail**

Build items:

```kotlin
val sourceItems = remember(uiState.activeDraft, uiState.inventory, uiState.recipes, uiState.recentRecords) {
    buildWorkbenchSourceItems(
        activeDraft = uiState.activeDraft,
        inventory = uiState.inventory,
        recipes = uiState.recipes,
        recentRecords = uiState.recentRecords,
    )
}
```

Render:

```kotlin
SectionCard(
    title = "快速开始",
    subtitle = "从草稿、豆子、配方或历史记录直接生成下一杯。",
) {
    RecordSourceRail(
        items = sourceItems.mapIndexed { index, item ->
            RecordSourceRailItem(
                title = item.title,
                subtitle = item.subtitle,
                badge = item.badge,
                key = "$index:${item.title}:${item.badge}",
            )
        },
        onClick = { clicked ->
            sourceItems.firstOrNull { "${sourceItems.indexOf(it)}:${it.title}:${it.badge}" == clicked.key }
                ?.let { handleWorkbenchAction(it.action) }
        },
        enabled = !isReadOnlyArchive,
        modifier = Modifier.testTag(QoffeeTestTags.BREW_SOURCE_RAIL),
    )
}
```

The implementation may use a direct `key -> action` map instead of the `firstOrNull` lookup if that is clearer.

- [ ] **Step 5: Add pending feedback cards**

Build:

```kotlin
val pendingFeedback = remember(uiState.recentRecords) {
    buildPendingFeedbackItems(uiState.recentRecords)
}
```

Render only when non-empty:

```kotlin
if (pendingFeedback.isNotEmpty()) {
    SectionCard(
        title = "待补感受",
        subtitle = "先补总评，就能让这杯进入复盘样本。",
        modifier = Modifier.testTag(QoffeeTestTags.BREW_PENDING_FEEDBACK),
    ) {
        pendingFeedback.forEach { item ->
            FeatureEntryCard(
                title = item.title,
                hint = item.subtitle,
                icon = Icons.Outlined.Edit,
                onClick = { onOpenEditor(item.recordId, null, RecordEditorEntry.DRAFT, null, null) },
                enabled = !isReadOnlyArchive,
            )
        }
    }
}
```

If `Icons.Outlined.Edit` is unavailable, use `Icons.Outlined.Add`.

- [ ] **Step 6: Keep existing deeper sections but lower their visual priority**

Keep these existing sections available below the first screen:

- Active session.
- Brew Coach suggestions.
- AI Coach suggestions.
- Inventory cards.
- Recipes.
- Recent records.
- Subjective/radar exploration.
- Timeline.
- Experiments/guides entry points.

Move duplicate “quick record” controls below the new hero/rail or remove only duplicated visual entry cards when the same action is available in hero/rail. Do not remove the underlying action.

- [ ] **Step 7: Run tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.records.RecordPresentationTest" --no-daemon --console=plain
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected: PASS. If connected tests are blocked by device availability, record the blocker and verify `assembleDebug`.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/qoffee/feature/records/RecordsScreen.kt app/src/main/java/com/qoffee/ui/TestTags.kt app/src/androidTest/java/com/qoffee/NavigationSmokeTest.kt
git commit -m "feat: redesign records workbench entry"
```

---

### Task 5: Add Editor Presentation Models for Progress and Parameter Summary

**Files:**

- Create: `app/src/main/java/com/qoffee/feature/records/RecordEditorPresentation.kt`
- Create: `app/src/test/java/com/qoffee/feature/records/RecordEditorPresentationTest.kt`

- [ ] **Step 1: Write failing tests**

Create `RecordEditorPresentationTest.kt`:

```kotlin
package com.qoffee.feature.records

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BrewMethod
import org.junit.Test

class RecordEditorPresentationTest {

    @Test
    fun buildEditorProgressCountsCoreObjectiveFields() {
        val progress = buildEditorProgress(
            objective = ObjectiveFormState(
                brewMethod = BrewMethod.POUR_OVER,
                beanProfileId = 10L,
                coffeeDoseG = "15",
                brewWaterMl = "240",
                brewedAtMillis = 1_000L,
            ),
            subjective = SubjectiveFormState(overall = 4),
        )

        assertThat(progress.completedCount).isAtLeast(5)
        assertThat(progress.label).contains("已完成")
    }

    @Test
    fun buildParameterSummaryUsesCompactUnits() {
        val summary = buildEditorParameterSummary(
            ObjectiveFormState(
                coffeeDoseG = "15",
                brewWaterMl = "240",
                waterTempC = "92",
                brewDurationSeconds = "150",
                grindSetting = "18",
            ),
        )

        assertThat(summary).contains("粉量" to "15g")
        assertThat(summary).contains("萃取水" to "240ml")
        assertThat(summary).contains("水温" to "92°C")
        assertThat(summary).contains("时长" to "2:30")
        assertThat(summary).contains("研磨" to "18")
    }

    @Test
    fun buildMissingFieldsAllowsMinimalRecordButReportsHelpfulGaps() {
        val gaps = buildEditorMissingFields(
            objective = ObjectiveFormState(),
            subjective = SubjectiveFormState(),
        )

        assertThat(gaps).contains("记录时间")
        assertThat(gaps).contains("总评")
    }
}
```

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.records.RecordEditorPresentationTest" --no-daemon --console=plain
```

Expected: FAIL with missing file/functions.

- [ ] **Step 2: Implement editor presentation**

Create `RecordEditorPresentation.kt`:

```kotlin
package com.qoffee.feature.records

internal data class EditorProgress(
    val completedCount: Int,
    val totalCount: Int,
    val label: String,
)

internal enum class EditorSection(val label: String) {
    SOURCE("来源"),
    PARAMETERS("参数"),
    WATER("注水"),
    TASTING("感受"),
    REVIEW("检查"),
}

internal fun buildEditorProgress(
    objective: ObjectiveFormState,
    subjective: SubjectiveFormState,
): EditorProgress {
    val checks = listOf(
        objective.brewedAtMillis != null,
        objective.brewMethod != null,
        objective.beanProfileId != null,
        objective.coffeeDoseG.isNotBlank(),
        objective.brewWaterMl.isNotBlank() || objective.waterCurveStages.isNotEmpty(),
        objective.grindSetting.isNotBlank(),
        subjective.overall != null,
    )
    val completed = checks.count { it }
    return EditorProgress(
        completedCount = completed,
        totalCount = checks.size,
        label = "已完成 $completed/${checks.size}",
    )
}

internal fun buildEditorParameterSummary(objective: ObjectiveFormState): List<Pair<String, String>> {
    return listOf(
        "粉量" to objective.coffeeDoseG.withUnit("g"),
        "萃取水" to objective.brewWaterMl.withUnit("ml"),
        "水温" to objective.waterTempC.withUnit("°C"),
        "时长" to objective.brewDurationSeconds.toDurationLabel(),
        "研磨" to objective.grindSetting,
    ).filter { it.second.isNotBlank() }
}

internal fun buildEditorMissingFields(
    objective: ObjectiveFormState,
    subjective: SubjectiveFormState,
): List<String> = buildList {
    if (objective.brewedAtMillis == null) add("记录时间")
    if (objective.brewMethod == null) add("冲煮方式")
    if (objective.beanProfileId == null) add("咖啡豆")
    if (subjective.overall == null) add("总评")
}

internal fun buildTastingScoreItems(subjective: SubjectiveFormState): List<com.qoffee.ui.components.TastingScoreItem> {
    return listOf(
        com.qoffee.ui.components.TastingScoreItem("overall", "总评", subjective.overall),
        com.qoffee.ui.components.TastingScoreItem("aroma", "香气", subjective.aroma),
        com.qoffee.ui.components.TastingScoreItem("acidity", "酸质", subjective.acidity),
        com.qoffee.ui.components.TastingScoreItem("sweetness", "甜感", subjective.sweetness),
        com.qoffee.ui.components.TastingScoreItem("bitterness", "苦感", subjective.bitterness),
        com.qoffee.ui.components.TastingScoreItem("body", "醇厚", subjective.body),
        com.qoffee.ui.components.TastingScoreItem("aftertaste", "余韵", subjective.aftertaste),
    )
}

private fun String.withUnit(unit: String): String {
    return if (isBlank()) "" else "$this$unit"
}

private fun String.toDurationLabel(): String {
    val seconds = toIntOrNull() ?: return ""
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}
```

- [ ] **Step 3: Run editor presentation tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.records.RecordEditorPresentationTest" --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/qoffee/feature/records/RecordEditorPresentation.kt app/src/test/java/com/qoffee/feature/records/RecordEditorPresentationTest.kt
git commit -m "feat: add record editor presentation state"
```

---

### Task 6: Rework Record Editor Layout and Inputs

**Files:**

- Modify: `app/src/main/java/com/qoffee/feature/records/RecordEditorScreen.kt`
- Modify: `app/src/main/java/com/qoffee/ui/TestTags.kt`
- Modify: `app/src/androidTest/java/com/qoffee/NavigationSmokeTest.kt`

- [ ] **Step 1: Add UI test tags**

Add to `TestTags.kt`:

```kotlin
const val RECORD_EDITOR_SUMMARY = "record_editor_summary"
const val RECORD_EDITOR_SECTION_TABS = "record_editor_section_tabs"
const val RECORD_EDITOR_TASTING_PANEL = "record_editor_tasting_panel"
const val RECORD_EDITOR_BOTTOM_ACTION = "record_editor_bottom_action"
```

Extend `addRecordEntryCanOpenEditor`:

```kotlin
composeRule.onNodeWithTag(QoffeeTestTags.RECORD_EDITOR_SUMMARY).fetchSemanticsNode()
composeRule.onNodeWithTag(QoffeeTestTags.RECORD_EDITOR_BOTTOM_ACTION).fetchSemanticsNode()
```

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
```

Expected: FAIL until the editor renders those tags. If no device is connected, continue and validate later.

- [ ] **Step 2: Add editor section state**

Inside `RecordEditorScreen`, add:

```kotlin
var selectedSection by remember { mutableStateOf(EditorSection.PARAMETERS) }
val progress = remember(uiState.objective, uiState.subjective) {
    buildEditorProgress(uiState.objective, uiState.subjective)
}
val parameterSummary = remember(uiState.objective) {
    buildEditorParameterSummary(uiState.objective)
}
val missingFields = remember(uiState.objective, uiState.subjective) {
    buildEditorMissingFields(uiState.objective, uiState.subjective)
}
```

- [ ] **Step 3: Render sticky progress and parameter summary near the top**

After `PageHeader`, render:

```kotlin
SectionCard(
    title = progress.label,
    subtitle = if (missingFields.isEmpty()) "这杯已经可以完成。" else "还差 ${missingFields.joinToString("、")}，也可以先保存草稿。",
    modifier = Modifier.testTag(QoffeeTestTags.RECORD_EDITOR_SUMMARY),
) {
    ParameterSummaryStrip(items = parameterSummary)
}
```

This summary must update when dose, water, temperature, duration, or grind changes.

- [ ] **Step 4: Add section chips for ergonomic navigation**

Render:

```kotlin
FlowRow(
    modifier = Modifier
        .fillMaxWidth()
        .testTag(QoffeeTestTags.RECORD_EDITOR_SECTION_TABS),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    EditorSection.entries.forEach { section ->
        StepChip(
            label = section.label,
            selected = selectedSection == section,
            onClick = { selectedSection = section },
        )
    }
}
```

Keep the existing two-stage objective/subjective flow if it is deeply coupled, but the visible copy and controls should follow these sections:

- `SOURCE`: recipe, bean, method, grinder, timestamp.
- `PARAMETERS`: dose, water, temperature, duration, grind.
- `WATER`: water curve and derived values.
- `TASTING`: subjective score, flavor tags, subjective notes.
- `REVIEW`: validation, comparison, save-as-recipe actions.

- [ ] **Step 5: Replace long numeric text fields with smart controls where already supported**

Use existing `InlineRulerField`, `GrindDialField`, `NumericStepField`, and the new water quick values helper.

Required field behavior:

- Coffee dose: 0.5g step, common quick values `12`, `15`, `18`, `20`.
- Brew water: quick values derived from dose and ratios `14`, `15`, `16`, `17`.
- Water temp: quick values `88`, `90`, `92`, `94`, `96`.
- Duration: retain editable value but expose quick increments where existing components support it.
- Grind: keep `GrindDialField` with reference and normalized values.

Do not remove keyboard access.

- [ ] **Step 6: Replace subjective dimensions with `TastingScorePanel`**

In the tasting section, render:

```kotlin
TastingScorePanel(
    scores = buildTastingScoreItems(uiState.subjective),
    onScoreSelected = { key, score ->
        when (key) {
            "overall" -> onOverallChange(score)
            "aroma" -> onAromaChange(score)
            "acidity" -> onAcidityChange(score)
            "sweetness" -> onSweetnessChange(score)
            "bitterness" -> onBitternessChange(score)
            "body" -> onBodyChange(score)
            "aftertaste" -> onAftertasteChange(score)
        }
    },
    modifier = Modifier.testTag(QoffeeTestTags.RECORD_EDITOR_TASTING_PANEL),
)
```

Keep `TagSelector`, custom tag input, and subjective notes below the panel.

- [ ] **Step 7: Make the bottom action copy reflect the selected state**

The bottom action bar must use:

- `继续记录感受` when objective fields are being edited and no overall score exists.
- `完成记录` when subjective overall is filled.
- `先保存草稿` when required objective validation fails.

Attach `Modifier.testTag(QoffeeTestTags.RECORD_EDITOR_BOTTOM_ACTION)`.

- [ ] **Step 8: Run validation**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.records.RecordEditorPresentationTest" --no-daemon --console=plain
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected: PASS, or connected test blocker documented.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/com/qoffee/feature/records/RecordEditorScreen.kt app/src/main/java/com/qoffee/ui/TestTags.kt app/src/androidTest/java/com/qoffee/NavigationSmokeTest.kt
git commit -m "feat: streamline record editor interactions"
```

---

### Task 7: Add Record Report Presentation Models

**Files:**

- Create: `app/src/main/java/com/qoffee/feature/records/RecordReportPresentation.kt`
- Create: `app/src/test/java/com/qoffee/feature/records/RecordReportPresentationTest.kt`

- [ ] **Step 1: Write failing tests**

Create `RecordReportPresentationTest.kt`:

```kotlin
package com.qoffee.feature.records

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.SubjectiveEvaluation
import org.junit.Test

class RecordReportPresentationTest {

    @Test
    fun buildRecordReportHeaderShowsScoreAndCoreParameters() {
        val record = CoffeeRecord(
            id = 1L,
            status = RecordStatus.COMPLETED,
            brewMethod = BrewMethod.POUR_OVER,
            beanNameSnapshot = "Kenya AB",
            coffeeDoseG = 15.0,
            brewWaterMl = 240.0,
            waterTempC = 92.0,
            brewDurationSeconds = 150,
            grindSetting = 18.0,
            brewedAt = 1_000L,
            subjectiveEvaluation = SubjectiveEvaluation(recordId = 1L, overall = 4),
        )

        val report = buildRecordReport(record)

        assertThat(report.title).isEqualTo("Kenya AB")
        assertThat(report.scoreText).isEqualTo("4/5")
        assertThat(report.parameters).contains("粉量" to "15g")
        assertThat(report.parameters).contains("萃取水" to "240ml")
        assertThat(report.parameters).contains("水温" to "92°C")
        assertThat(report.parameters).contains("时长" to "2:30")
    }

    @Test
    fun buildRecordReportUsesMissingScoreFallback() {
        val report = buildRecordReport(
            CoffeeRecord(id = 1L, status = RecordStatus.COMPLETED, brewedAt = 1_000L),
        )

        assertThat(report.scoreText).isEqualTo("未评分")
    }
}
```

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.records.RecordReportPresentationTest" --no-daemon --console=plain
```

Expected: FAIL with missing file/functions.

- [ ] **Step 2: Implement report presentation**

Create `RecordReportPresentation.kt`:

```kotlin
package com.qoffee.feature.records

import com.qoffee.core.model.CoffeeRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class RecordReport(
    val title: String,
    val subtitle: String,
    val scoreText: String,
    val parameters: List<Pair<String, String>>,
    val reuseActions: List<RecordReuseAction>,
)

internal enum class RecordReuseAction(val label: String) {
    DUPLICATE("复刻下一杯"),
    SAVE_AS_RECIPE("设为配方"),
    OVERWRITE_RECIPE("覆盖原配方"),
    CREATE_GUIDE("设为指导"),
    EDIT("编辑"),
}

internal fun buildRecordReport(record: CoffeeRecord): RecordReport {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    val method = record.brewMethod?.displayName ?: "未指定方式"
    val title = record.beanNameSnapshot ?: method
    val score = record.subjectiveEvaluation?.overall?.let { "$it/5" } ?: "未评分"
    val actions = buildList {
        add(RecordReuseAction.DUPLICATE)
        add(RecordReuseAction.SAVE_AS_RECIPE)
        if (record.recipeTemplateId != null && !record.recipeNameSnapshot.isNullOrBlank()) {
            add(RecordReuseAction.OVERWRITE_RECIPE)
        }
        add(RecordReuseAction.CREATE_GUIDE)
        add(RecordReuseAction.EDIT)
    }
    return RecordReport(
        title = title,
        subtitle = "$method · ${formatter.format(Date(record.brewedAt))}",
        scoreText = score,
        parameters = buildReportParameters(record),
        reuseActions = actions,
    )
}

private fun buildReportParameters(record: CoffeeRecord): List<Pair<String, String>> {
    return listOf(
        "粉量" to record.coffeeDoseG.formatUnit("g"),
        "萃取水" to record.brewWaterMl.formatUnit("ml"),
        "总水量" to record.totalWaterMl.formatUnit("ml"),
        "水温" to record.waterTempC.formatUnit("°C"),
        "时长" to record.brewDurationSeconds.formatDuration(),
        "研磨" to record.grindSetting.formatPlain(),
    ).filter { it.second.isNotBlank() }
}

private fun Double?.formatUnit(unit: String): String {
    return this?.let { "${formatReportNumber(it)}$unit" }.orEmpty()
}

private fun Double?.formatPlain(): String {
    return this?.let(::formatReportNumber).orEmpty()
}

private fun Int?.formatDuration(): String {
    val value = this ?: return ""
    val minutes = value / 60
    val seconds = value % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun formatReportNumber(value: Double): String {
    return String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}
```

- [ ] **Step 3: Run tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.records.RecordReportPresentationTest" --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/qoffee/feature/records/RecordReportPresentation.kt app/src/test/java/com/qoffee/feature/records/RecordReportPresentationTest.kt
git commit -m "feat: add record report presentation"
```

---

### Task 8: Rebuild Record Detail as a Cup Report

**Files:**

- Modify: `app/src/main/java/com/qoffee/feature/records/RecordDetailScreen.kt`
- Modify: `app/src/main/java/com/qoffee/ui/TestTags.kt`

- [ ] **Step 1: Add stable tags**

Add to `TestTags.kt`:

```kotlin
const val RECORD_REPORT_HEADER = "record_report_header"
const val RECORD_REPORT_REUSE_ACTIONS = "record_report_reuse_actions"
```

- [ ] **Step 2: Use report presentation at the top of `RecordDetailScreen`**

Inside `RecordDetailScreen`, after null record handling:

```kotlin
val report = remember(record) { buildRecordReport(record) }
```

Replace the current top `PageHeader`/action row with:

```kotlin
RecordReportHeader(
    title = report.title,
    subtitle = report.subtitle,
    scoreText = report.scoreText,
    parameters = report.parameters,
    modifier = Modifier.testTag(QoffeeTestTags.RECORD_REPORT_HEADER),
)
```

Keep the back button visible near the top, using a compact outlined button.

- [ ] **Step 3: Move reuse actions into a high-priority action section**

Render before low-frequency management:

```kotlin
if (!isReadOnlyArchive) {
    SectionCard(
        title = "下一步",
        subtitle = "把这杯直接变成下一次行动。",
        modifier = Modifier.testTag(QoffeeTestTags.RECORD_REPORT_REUSE_ACTIONS),
    ) {
        Button(
            onClick = { onDuplicate(record.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("复刻下一杯")
        }
        OutlinedButton(
            onClick = { showSaveRecipeDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("设为配方")
        }
        if (record.recipeTemplateId != null && !record.recipeNameSnapshot.isNullOrBlank()) {
            OutlinedButton(
                onClick = onOverwriteSourceRecipe,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("覆盖原配方")
            }
        }
        OutlinedButton(
            onClick = onCreateGuide,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("设为指导")
        }
        OutlinedButton(
            onClick = { onEdit(record.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("编辑")
        }
    }
}
```

Remove or lower any duplicate reuse section below so the page does not repeat identical actions.

- [ ] **Step 4: Preserve all data sections**

Keep existing sections for:

- Review context.
- Objective parameters.
- Water curve.
- Grind normalization.
- Subjective feeling.
- Comparison with previous cup.
- Bean history.
- Record management/delete.

Improve copy to natural Chinese where touched:

- `复盘摘要`
- `客观参数`
- `冲煮曲线`
- `研磨归一化`
- `主观感受`
- `与上一杯相比`
- `同豆历史表现`
- `记录管理`

- [ ] **Step 5: Run tests and build**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.records.RecordReportPresentationTest" --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/qoffee/feature/records/RecordDetailScreen.kt app/src/main/java/com/qoffee/ui/TestTags.kt
git commit -m "feat: present record detail as cup report"
```

---

### Task 9: Add Review Insight Presentation Models

**Files:**

- Create: `app/src/main/java/com/qoffee/feature/analytics/ReviewInsightPresentation.kt`
- Create: `app/src/test/java/com/qoffee/feature/analytics/ReviewInsightPresentationTest.kt`

- [ ] **Step 1: Write failing tests**

Create `ReviewInsightPresentationTest.kt`:

```kotlin
package com.qoffee.feature.analytics

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalyticsDashboard
import org.junit.Test

class ReviewInsightPresentationTest {

    @Test
    fun buildReviewInsightsShowsEmptyStateWhenSampleCountIsZero() {
        val insights = buildReviewInsights(
            dashboard = AnalyticsDashboard(filter = AnalysisFilter()),
            recordsCount = 0,
        )

        assertThat(insights.first().title).contains("先积累")
        assertThat(insights.first().primaryAction).isEqualTo(ReviewInsightAction.StartRecord)
    }

    @Test
    fun buildReviewInsightsOffersSampleReviewWhenRecordsExist() {
        val insights = buildReviewInsights(
            dashboard = AnalyticsDashboard(filter = AnalysisFilter()),
            recordsCount = 4,
        )

        assertThat(insights.map { it.primaryAction }).contains(ReviewInsightAction.OpenSamples)
    }
}
```

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.analytics.ReviewInsightPresentationTest" --no-daemon --console=plain
```

Expected: FAIL with missing file/functions.

- [ ] **Step 2: Implement review insight models**

Create `ReviewInsightPresentation.kt`:

```kotlin
package com.qoffee.feature.analytics

import com.qoffee.core.model.AnalyticsDashboard

internal data class ReviewInsight(
    val title: String,
    val evidence: String,
    val primaryLabel: String,
    val primaryAction: ReviewInsightAction,
    val secondaryLabel: String? = null,
    val secondaryAction: ReviewInsightAction? = null,
)

internal sealed interface ReviewInsightAction {
    data object StartRecord : ReviewInsightAction
    data object OpenSamples : ReviewInsightAction
    data object OpenTrends : ReviewInsightAction
    data object OpenExperiments : ReviewInsightAction
}

internal fun buildReviewInsights(
    dashboard: AnalyticsDashboard,
    recordsCount: Int,
): List<ReviewInsight> {
    if (recordsCount <= 0) {
        return listOf(
            ReviewInsight(
                title = "先积累可复盘样本",
                evidence = "还没有足够记录形成稳定结论。先完成一杯，Qoffee 会从记录里生成复盘线索。",
                primaryLabel = "开始记录",
                primaryAction = ReviewInsightAction.StartRecord,
            ),
        )
    }

    val insights = mutableListOf<ReviewInsight>()
    insights += ReviewInsight(
        title = "查看可复用样本",
        evidence = "当前筛选下有 $recordsCount 条记录，可先从高分或低分样本进入下一杯行动。",
        primaryLabel = "查看样本",
        primaryAction = ReviewInsightAction.OpenSamples,
        secondaryLabel = "看趋势",
        secondaryAction = ReviewInsightAction.OpenTrends,
    )

    if (recordsCount >= 3) {
        insights += ReviewInsight(
            title = "把复盘变成实验",
            evidence = "样本数量已足够形成初步假设，可以创建对照实验验证参数变化。",
            primaryLabel = "进入实验",
            primaryAction = ReviewInsightAction.OpenExperiments,
            secondaryLabel = "查看趋势",
            secondaryAction = ReviewInsightAction.OpenTrends,
        )
    }

    return insights
}
```

- [ ] **Step 3: Run tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.analytics.ReviewInsightPresentationTest" --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/qoffee/feature/analytics/ReviewInsightPresentation.kt app/src/test/java/com/qoffee/feature/analytics/ReviewInsightPresentationTest.kt
git commit -m "feat: add review insight presentation"
```

---

### Task 10: Rework Analytics First Screen into Actionable Review

**Files:**

- Modify: `app/src/main/java/com/qoffee/feature/analytics/AnalyticsScreen.kt`
- Modify: `app/src/main/java/com/qoffee/ui/TestTags.kt`
- Modify: `app/src/androidTest/java/com/qoffee/NavigationSmokeTest.kt`

- [ ] **Step 1: Add tags and test expectation**

Add to `TestTags.kt`:

```kotlin
const val HISTORY_INSIGHTS = "history_insights"
```

Extend `topLevelNavigationShowsExpectedScreens` after opening history:

```kotlin
composeRule.onNodeWithTag(QoffeeTestTags.HISTORY_INSIGHTS).fetchSemanticsNode()
```

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
```

Expected: FAIL until analytics renders the insight section.

- [ ] **Step 2: Render insight cards near the top of `AnalysisScreen`**

Inside `AnalysisScreen`, build:

```kotlin
val reviewInsights = remember(uiState.dashboard, uiState.records.size) {
    buildReviewInsights(
        dashboard = uiState.dashboard,
        recordsCount = uiState.records.size,
    )
}
```

Render near the top, after the title/filter summary and before dense charts:

```kotlin
SectionCard(
    title = "复盘行动",
    subtitle = "先看结论，再进入样本、趋势或实验。",
    modifier = Modifier.testTag(QoffeeTestTags.HISTORY_INSIGHTS),
) {
    reviewInsights.forEach { insight ->
        InsightActionCard(
            title = insight.title,
            evidence = insight.evidence,
            actionLabel = insight.primaryLabel,
            onAction = { handleReviewInsightAction(insight.primaryAction) },
            secondaryLabel = insight.secondaryLabel,
            onSecondaryAction = insight.secondaryAction?.let { action ->
                { handleReviewInsightAction(action) }
            },
        )
    }
}
```

- [ ] **Step 3: Map review insight actions to existing local state**

Add local function in `AnalysisScreen`:

```kotlin
fun handleReviewInsightAction(action: ReviewInsightAction) {
    when (action) {
        ReviewInsightAction.StartRecord -> {
            // Keep analytics read-only. User starts records from the record workbench.
            // Use section switch only if no navigation callback exists in this screen.
            viewModel.updateSelectedSection(HistorySection.SAMPLES)
        }
        ReviewInsightAction.OpenSamples -> viewModel.updateSelectedSection(HistorySection.SAMPLES)
        ReviewInsightAction.OpenTrends -> viewModel.updateSelectedSection(HistorySection.TRENDS)
        ReviewInsightAction.OpenExperiments -> viewModel.updateSelectedSection(HistorySection.EXPERIMENTS)
    }
}
```

If direct navigation to the record workbench is available through parent navigation in the current app structure, wire `StartRecord` to that callback instead. Do not introduce a new top-level navigation abstraction only for this task.

- [ ] **Step 4: Preserve all existing analytics sections**

Keep:

- Filters.
- Report export.
- CSV export.
- Trend charts.
- Sample list and open record behavior.
- Experiment creation from records.
- Existing `HistorySection` state restoration.

- [ ] **Step 5: Run tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.qoffee.feature.analytics.ReviewInsightPresentationTest" --no-daemon --console=plain
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected: PASS, or connected test blocker documented.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/qoffee/feature/analytics/AnalyticsScreen.kt app/src/main/java/com/qoffee/ui/TestTags.kt app/src/androidTest/java/com/qoffee/NavigationSmokeTest.kt
git commit -m "feat: surface actionable review insights"
```

---

### Task 11: Polish Motion, Press Feedback, and Image Usage

**Files:**

- Modify: `app/src/main/java/com/qoffee/ui/components/RecordExperienceComponents.kt`
- Modify: `app/src/main/java/com/qoffee/ui/components/V2Components.kt`
- Modify: `app/src/main/java/com/qoffee/feature/records/RecordsScreen.kt`
- Modify: `app/src/main/java/com/qoffee/feature/analytics/AnalyticsScreen.kt`

- [ ] **Step 1: Audit animation and image use**

Run:

```powershell
rg -n "AnimatedContent|AnimatedVisibility|animate|graphicsLayer|DashboardArtworkBanner|art_" app/src/main/java/com/qoffee
```

Expected: Identify current animation and image call sites.

- [ ] **Step 2: Keep only functional motion**

Ensure these behaviors exist:

- Pressed record source cards scale to about `0.985f`.
- Parameter summary values animate when the displayed value changes.
- Tasting score chip color changes with animation.
- Page navigation remains directional via existing `QoffeeApp.kt` transitions.

Ensure these behaviors do not exist:

- Constant decorative looping animation.
- Large animated background blobs.
- Motion that changes layout size during text entry.

- [ ] **Step 3: Use image banners sparingly**

Records workbench:

- Keep one `DashboardArtworkBanner` only if it does not push the hero action below the first viewport on normal phones.
- Prefer `art_record_workbench` or `art_minimal_record_workbench` based on existing theme style if that logic already exists.

Analytics:

- Use `art_review_insight` only near the report/insight context, not before the actionable cards if it reduces task visibility.

If a banner competes with the primary action, remove the banner from that screen.

- [ ] **Step 4: Run build**

Run:

```powershell
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected: Build success.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/qoffee/ui/components/RecordExperienceComponents.kt app/src/main/java/com/qoffee/ui/components/V2Components.kt app/src/main/java/com/qoffee/feature/records/RecordsScreen.kt app/src/main/java/com/qoffee/feature/analytics/AnalyticsScreen.kt
git commit -m "style: polish motion and artwork usage"
```

---

### Task 12: Final Copy, Ergonomics, and Regression Pass

**Files:**

- Modify: any touched UI files with awkward copy or layout issues.
- Modify: `app/src/androidTest/java/com/qoffee/NavigationSmokeTest.kt` only if final stable tags or labels changed.

- [ ] **Step 1: Search for mojibake or awkward copied text in touched files**

Run:

```powershell
rg -n "璁|鍜|澶|鐑|鈥|�|TODO|TBD" app/src/main/java/com/qoffee/feature/records app/src/main/java/com/qoffee/feature/analytics app/src/main/java/com/qoffee/ui/components
```

Expected: No mojibake or placeholder text in user-facing copy in the touched UI paths. Existing untouched mojibake may remain outside this scope, but do not introduce new garbled copy.

- [ ] **Step 2: Verify high-frequency actions are reachable**

Manually inspect or test:

- Workbench primary action is visible near the top.
- Quick start rail can start from draft, bean, recipe, and history when data exists.
- Active draft conflict still offers only low-friction choices.
- Editor can save/complete records.
- Detail page exposes duplicate, save-as-recipe, overwrite-recipe when applicable, create guide, and edit.
- Analytics can open samples, trends, experiments, and records.

- [ ] **Step 3: Run full verification**

Run:

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
```

Expected:

- Unit tests pass.
- Debug APK builds.
- Connected tests pass when a device/emulator is available.

If `connectedDebugAndroidTest` cannot run:

- Record exact blocker.
- Include `adb devices` output in the final implementation report.
- Do not claim connected UI tests passed.

- [ ] **Step 4: Final visual QA on at least two viewport classes**

Use emulator/device screenshots if available:

- Compact phone portrait.
- Larger phone or tablet-width portrait.

Check:

- No text overlaps.
- Button labels fit.
- The workbench first screen shows the primary action without excessive scrolling.
- Numeric controls remain stable while values change.
- Charts and banners do not occlude controls.
- Dark mode and minimal mode maintain contrast.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java app/src/test/java app/src/androidTest/java
git commit -m "test: verify record experience redesign"
```

---

## Final Acceptance Checklist

The implementation is complete only when all applicable items are true:

- [ ] `feature/records` remains the main workbench for starting, continuing, reusing, and reviewing records.
- [ ] The first screen has one dominant “今日行动” and at most two secondary actions.
- [ ] Users can start records from active draft, bean, recipe, historical record, and blank record without visiting asset management first.
- [ ] Existing draft conflict behavior is preserved.
- [ ] Record editor shows progress, parameter summary, section navigation, ergonomic numeric controls, and a compact tasting score panel.
- [ ] Record detail reads as a cup report and exposes reuse actions before low-frequency management.
- [ ] Analytics first screen surfaces actionable review insights before dense charts.
- [ ] Existing export, filter, sample, trend, experiment, guide, recipe, and delete capabilities still exist.
- [ ] No Room schema change was introduced.
- [ ] No new decorative animation competes with task flow.
- [ ] Existing image assets are used sparingly and do not push key actions out of reach.
- [ ] Unit tests pass.
- [ ] Debug build succeeds.
- [ ] Connected UI tests pass or the exact device blocker is documented.

## Handoff Notes for Implementation Agent

- Use `superpowers:subagent-driven-development` if executing task-by-task with review checkpoints.
- Keep commits task-sized as specified.
- If a task reveals a genuine conflict with existing code, preserve product laws first:
  - Records are central.
  - Assets and recipes reduce record input cost.
  - Analytics follows records.
  - Reuse actions stay directly accessible from records.
- Do not broaden into cloud sync, AI generation, subscription, or schema work while executing this plan.
