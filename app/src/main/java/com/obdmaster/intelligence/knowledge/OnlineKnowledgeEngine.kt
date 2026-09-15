package com.obdmaster.intelligence.knowledge

import com.obdmaster.intelligence.data.local.dao.KnowledgeCacheDao
import com.obdmaster.intelligence.data.local.entity.KnowledgeCacheEntity
import com.obdmaster.intelligence.data.remote.KnowledgeApi
import com.obdmaster.intelligence.domain.model.KnowledgeResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineKnowledgeEngine @Inject constructor(
    private val api: KnowledgeApi,
    private val cache: KnowledgeCacheDao
) {
    suspend fun query(query: String): KnowledgeResult {
        val key = query.trim().lowercase()
        cache.get(key)?.let {
            return KnowledgeResult(query, it.answer, it.source, cached = true)
        }
        val remote = api.fetch(query)
        cache.upsert(
            KnowledgeCacheEntity(
                queryKey = key,
                answer = remote.second,
                source = remote.first,
                cachedAt = System.currentTimeMillis()
            )
        )
        return KnowledgeResult(query, remote.second, remote.first, cached = false)
    }

    suspend fun sampleVehicleEcuProtocol(brand: String, model: String): KnowledgeResult =
        query("vehicle ECU diagnostic protocol $brand $model")
}
