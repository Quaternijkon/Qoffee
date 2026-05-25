package com.qoffee.data.repository

import com.qoffee.core.model.AiCoachSuggestion
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.buildLocalAiCoachSuggestions
import com.qoffee.domain.repository.AiCoachRepository
import com.qoffee.domain.repository.RecordRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AiCoachRepositoryImpl @Inject constructor(
    private val recordRepository: RecordRepository,
) : AiCoachRepository {

    override fun observeSuggestions(): Flow<List<AiCoachSuggestion>> {
        return recordRepository
            .observeRecords(AnalysisFilter(timeRange = AnalysisTimeRange.ALL))
            .map(::buildLocalAiCoachSuggestions)
    }
}
