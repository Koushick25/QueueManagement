package com.example.adaptivequeue.data.repository

import com.example.adaptivequeue.data.local.QueueDao
import com.example.adaptivequeue.data.model.QueueToken
import com.example.adaptivequeue.data.model.ServiceRecord
import com.example.adaptivequeue.data.model.TokenStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRepository @Inject constructor(
    private val queueDao: QueueDao,
    private val estimationEngine: QueueEstimationEngine
) {
    fun getAllTokens() = queueDao.getAllTokens()
    fun getWaitingTokens() = queueDao.getTokensByStatus(TokenStatus.WAITING)
    fun getCurrentServingToken() = queueDao.getCurrentServingToken()
    fun getPeopleAhead(tokenNumber: Int) = queueDao.getPeopleAhead(tokenNumber)
    fun getTokenById(id: Int) = queueDao.getTokenById(id)
    fun getTokensByIds(ids: List<Int>) = queueDao.getTokensByIds(ids)
    fun getActiveTokens() = queueDao.getActiveTokens()

    fun getAverageServiceTimeMinutes(): Flow<Double> {
        return queueDao.getRecentServiceRecords(5).map { records ->
            estimationEngine.calculateAverageServiceTimeMinutes(records)
        }
    }

    suspend fun generateToken(visitorName: String): QueueToken {
        val lastNumber = queueDao.getLastTokenNumber() ?: 0
        val newTokenNumber = lastNumber + 1
        
        // Initial estimate using default if no average yet
        val defaultAvg = estimationEngine.calculateAverageServiceTimeMinutes(emptyList())
        
        val token = QueueToken(
            tokenNumber = newTokenNumber,
            visitorName = visitorName,
            estimatedWaitMinutes = (newTokenNumber * defaultAvg).toInt()
        )
        val id = queueDao.insertToken(token)
        return token.copy(id = id.toInt())
    }

    suspend fun markAsServing(token: QueueToken) {
        queueDao.updateToken(token.copy(
            status = TokenStatus.SERVING,
            serviceStartTime = System.currentTimeMillis()
        ))
    }

    suspend fun markAsCompleted(token: QueueToken) {
        val endTime = System.currentTimeMillis()
        val startTime = token.serviceStartTime ?: token.joinTime // Fallback
        val duration = endTime - startTime

        // Record the actual service
        queueDao.insertServiceRecord(ServiceRecord(
            tokenNumber = token.tokenNumber,
            durationMillis = duration
        ))

        queueDao.updateToken(token.copy(status = TokenStatus.COMPLETED))
    }

    suspend fun addMockServiceRecord(durationMinutes: Long) {
        queueDao.insertServiceRecord(ServiceRecord(
            tokenNumber = 0,
            durationMillis = durationMinutes * 60 * 1000
        ))
    }

    suspend fun updateToken(token: QueueToken) {
        queueDao.updateToken(token)
    }

    suspend fun clearQueue() {
        queueDao.clearQueue()
    }
}
