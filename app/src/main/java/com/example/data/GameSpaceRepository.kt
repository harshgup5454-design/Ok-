package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameSpaceRepository(context: Context) {
    private val db = GameBoostDatabase.getDatabase(context)
    private val dao = db.gameSpaceDao()
    private val networkDao = db.networkHistoryDao()

    val profilesFlow: Flow<List<GameProfile>> = dao.getAllGameProfilesFlow()
        .map { entities ->
            if (entities.isEmpty()) {
                GameProfilesRepository.getDefaultProfiles()
            } else {
                entities.map { it.toDomain() }
            }
        }

    val sessionsFlow: Flow<List<BenchmarkSessionReport>> = dao.getAllSessionReportsFlow()
        .map { entities ->
            if (entities.isEmpty()) {
                BenchmarkRepository.getAllSessions()
            } else {
                entities.map { it.toDomain() }
            }
        }

    val benchmarkLabResultsFlow: Flow<List<BenchmarkLabRunResult>> = dao.getAllBenchmarkLabResultsFlow()
        .map { entities -> entities.map { BenchmarkLabRunResult.fromEntity(it) } }

    val advisorRecommendationsFlow: Flow<List<AdvisorRecommendation>> = dao.getAllAdvisorRecommendationsFlow()
        .map { entities -> entities.map { AdvisorRecommendation.fromEntity(it) } }

    val networkHistoryFlow: Flow<List<NetworkHistoryRecord>> = networkDao.getHistoryFlow()
        .map { entities ->
            val domain = entities.map { it.toDomain() }
            NetworkMonitor.setHistory(domain)
            domain
        }

    suspend fun initializeDefaultsIfNeeded() {
        val existing = dao.getAllGameProfiles()
        if (existing.isEmpty()) {
            for (profile in GameProfilesRepository.getDefaultProfiles()) {
                dao.insertGameProfile(GameProfileEntity.fromDomain(profile))
            }
        }
        val existingSessions = dao.getAllSessionReports()
        if (existingSessions.isEmpty()) {
            for (session in BenchmarkRepository.getAllSessions()) {
                dao.insertSessionReport(SessionReportEntity.fromDomain(session))
            }
        }
        val persistedNet = networkDao.getHistory()
        if (persistedNet.isNotEmpty()) {
            NetworkMonitor.setHistory(persistedNet.map { it.toDomain() })
        }
    }

    suspend fun saveProfile(profile: GameProfile) {
        dao.insertGameProfile(GameProfileEntity.fromDomain(profile))
    }

    suspend fun deleteProfile(id: String) {
        dao.deleteGameProfile(id)
    }

    suspend fun duplicateProfile(profile: GameProfile) {
        val duplicated = profile.copy(
            id = "game_dup_${System.currentTimeMillis()}",
            name = "${profile.name} (Copy)",
            isFavorite = false
        )
        dao.insertGameProfile(GameProfileEntity.fromDomain(duplicated))
    }

    suspend fun saveSessionReport(report: BenchmarkSessionReport) {
        dao.insertSessionReport(SessionReportEntity.fromDomain(report))
        BenchmarkRepository.addSession(report)
    }

    suspend fun clearSessions() {
        dao.clearSessionReports()
        BenchmarkRepository.clearHistory()
    }

    suspend fun saveBenchmarkLabResult(result: BenchmarkLabRunResult) {
        dao.insertBenchmarkLabResult(result.toEntity())
    }

    suspend fun clearBenchmarkLabResults() {
        dao.clearBenchmarkLabResults()
    }

    suspend fun saveAdvisorRecommendation(rec: AdvisorRecommendation) {
        dao.insertAdvisorRecommendation(rec.toEntity())
    }

    suspend fun dismissAdvisorRecommendationByRuleId(ruleId: String) {
        dao.dismissRecommendationByRuleId(ruleId)
    }

    suspend fun clearAdvisorRecommendations() {
        dao.clearAdvisorRecommendations()
    }

    suspend fun saveNetworkRecord(record: NetworkHistoryRecord) {
        NetworkMonitor.addHistoryRecord(record)
        networkDao.insertHistory(NetworkHistoryEntity.fromDomain(record))
    }

    suspend fun clearNetworkHistory() {
        NetworkMonitor.setHistory(emptyList())
        networkDao.clearHistory()
    }
}
