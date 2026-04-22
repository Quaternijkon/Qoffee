---
name: qoffee-record-centered
description: Record-centered product and engineering rules for the Qoffee app. Use when working on record flow, analytics/review loop, recipe or asset semantics, inventory entry behavior, record editor/detail UX, navigation context, repository modeling, testing, or Android build stability in this repo. Apply whenever a task could change how users create, reuse, review, or analyze brew records.
---

# Qoffee Record Centered

## Overview

Treat records as the only first-class product object in Qoffee. Design assets, recipes, inventory, analytics, navigation, and engineering decisions to shorten the path from recording a brew to learning from it and acting on that learning.

## Core Product Law

- Treat `CoffeeRecord` as the center of the product.
- Treat analytics as the natural follow-up to records, not a parallel feature silo.
- Treat assets as structured reuse layers that reduce record input cost.
- Treat recipes as reusable objective parameters projected from records.
- Treat inventory as a usage view over bean assets and a direct entry into records.
- Reject features that add management burden without strengthening the record-to-review loop.

## Modeling Rules

- Use record objective parameters as the single source of truth.
- Keep recipe objective fields isomorphic with record objective fields. When adding a new objective field to records, add it to recipes in the same change.
- Keep subjective score, flavor tags, and subjective notes out of recipes unless the product direction explicitly changes.
- Prefer data models that answer: does this reduce record input cost or improve record review quality?
- Prefer repository-level projections and shared models over page-level manual mapping.
- Default to no Room schema change. Add a migration only when the record-centered model cannot express the requirement safely.
- Prefer typed result models for export, restore, and other high-risk flows instead of raw status strings.

## Workflow Priorities

- Optimize these chains first:
  `inventory/bean -> record`
  `record -> recipe`
  `recipe -> new record`
  `record -> analytics/review`
- In the current phase, prioritize record creation, continuation, duplication, prefill, and review over expanding analytics surface area.
- If a high-frequency action requires visiting an asset management page first, reconsider the interaction.

## Information Architecture

- Make `feature/records` the main workbench for starting, continuing, reusing, and reviewing records.
- Keep `feature/profile` focused on managing reuse objects, not on being the main creation path.
- Let record editor and record detail expose reuse actions directly, such as `设为配方`, `覆盖原配方`, `再冲一杯`, and `复制可比记录`.
- Preserve context when moving between records and analytics/review.

## Interaction Principles

- Make entry points reflect user intent:
  click a bean in inventory -> start a record for that bean
  click a recipe -> start a prefilled record
  click a historical record -> review it and reuse it directly
- When an active draft conflicts with a new intent, offer only low-friction choices:
  `继续当前草稿`
  `替换当前草稿并继续`
- Minimize jumps into management UI during high-frequency tasks.
- Keep copy clear and task-serving.
- Weaken the feeling that assets and recipes are separate systems; present them as helpers for recording and reviewing brews.

## Engineering Rules

- Reuse shared entry models for record launches. Prefer one prefill model such as `RecordPrefillSource` plus one conflict policy such as `DraftReplacePolicy`.
- Route all new record-entry variants through one repository capability instead of duplicating business logic in screens.
- Keep recipe creation from records in the record repository or another central projection layer so field parity is enforced in one place.
- Preserve navigation context for editor/detail/review round-trips.
- Fix project-level causes for build issues instead of relying only on ephemeral shell state.

## UI Direction

- Prefer simple, direct, ergonomic UI over decorative complexity.
- Optimize for scanability, tap efficiency, and continuity of task flow.
- Reduce the sense that assets and recipes are separate feature islands.
- Default to Chinese copy for project-facing UX unless a task explicitly requires another language.

## Validation Rules

- After record-flow changes, verify:
  record creation, resume, reuse, and replace behavior
  recipe projection consistency with record objective fields
  analytics still aggregate naturally around records
- When fixing regressions, distinguish:
  environment or configuration failures
  interface or contract breakage
  product-logic regressions
- Add tests where the rule lives:
  model tests for workflow resolution
  repository tests for projection and draft behavior
  UI tests for direct-entry interactions

## Build Hygiene

- Require a full JDK with `jlink` for Android builds. Do not rely on a stripped JRE.
- Prefer pinning Gradle to a valid JDK via project configuration when local environments are noisy.
- If `androidJdkImage` or `jlink` failures appear, check:
  `org.gradle.java.home`
  `JAVA_HOME`
  Gradle daemon state after JDK changes

## Repo Anchors

- Treat these areas as the main touchpoints for record-centered work:
  `app/src/main/java/com/qoffee/feature/records`
  `app/src/main/java/com/qoffee/feature/profile`
  `app/src/main/java/com/qoffee/feature/analytics`
  `app/src/main/java/com/qoffee/data/repository`
  `app/src/main/java/com/qoffee/ui/navigation`

## Agent Working Rules

- Understand the record-centered product logic before editing assets, recipes, inventory, or analytics.
- Ask of each proposed change: does this strengthen the record-to-review loop?
- Prefer convergence:
  one shared model
  one shared navigation path
  one shared repository capability
- Avoid designs where one new field requires several manual catch-up mappings in other silos.
- Favor stable product laws over temporary tactical tweaks when updating this skill.
