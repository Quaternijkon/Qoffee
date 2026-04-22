package com.qoffee.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecordPrefillWorkflowTest {

    @Test
    fun beanPrefillCreatesNewDraftWhenNoActiveDraftExists() {
        val behavior = resolveRecordDraftLaunchBehavior(
            activeDraft = null,
            prefillSource = RecordPrefillSource.Bean(beanId = 10L),
        )

        assertThat(behavior).isEqualTo(RecordDraftLaunchBehavior.CREATE_NEW)
    }

    @Test
    fun beanPrefillContinuesCurrentDraftWhenBeanMatches() {
        val behavior = resolveRecordDraftLaunchBehavior(
            activeDraft = activeDraft(beanId = 10L),
            prefillSource = RecordPrefillSource.Bean(beanId = 10L),
        )

        assertThat(behavior).isEqualTo(RecordDraftLaunchBehavior.CONTINUE_CURRENT)
    }

    @Test
    fun beanPrefillRequestsConfirmationWhenBeanDiffers() {
        val behavior = resolveRecordDraftLaunchBehavior(
            activeDraft = activeDraft(beanId = 10L),
            prefillSource = RecordPrefillSource.Bean(beanId = 20L),
        )

        assertThat(behavior).isEqualTo(RecordDraftLaunchBehavior.CONFIRM_REPLACE)
    }

    @Test
    fun blankPrefillRequestsConfirmationWhenDraftExists() {
        val behavior = resolveRecordDraftLaunchBehavior(
            activeDraft = activeDraft(beanId = 10L),
            prefillSource = RecordPrefillSource.Blank,
        )

        assertThat(behavior).isEqualTo(RecordDraftLaunchBehavior.CONFIRM_REPLACE)
    }

    @Test
    fun draftPrefillContinuesCurrentDraft() {
        val behavior = resolveRecordDraftLaunchBehavior(
            activeDraft = activeDraft(beanId = 10L),
            prefillSource = RecordPrefillSource.Draft,
        )

        assertThat(behavior).isEqualTo(RecordDraftLaunchBehavior.CONTINUE_CURRENT)
    }

    private fun activeDraft(beanId: Long) = CoffeeRecord(
        id = 1L,
        status = RecordStatus.DRAFT,
        beanProfileId = beanId,
        beanNameSnapshot = "Kenya AA",
    )
}
