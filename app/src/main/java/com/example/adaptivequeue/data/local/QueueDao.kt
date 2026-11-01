package com.example.adaptivequeue.data.local

import androidx.room.*
import com.example.adaptivequeue.data.model.QueueToken
import com.example.adaptivequeue.data.model.TokenStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_tokens ORDER BY tokenNumber ASC")
    fun getAllTokens(): Flow<List<QueueToken>>

    @Query("SELECT * FROM queue_tokens WHERE status = :status ORDER BY tokenNumber ASC")
    fun getTokensByStatus(status: TokenStatus): Flow<List<QueueToken>>

    @Query("SELECT * FROM queue_tokens WHERE status = 'SERVING' LIMIT 1")
    fun getCurrentServingToken(): Flow<QueueToken?>

    @Query("SELECT COUNT(*) FROM queue_tokens WHERE status = 'WAITING' AND tokenNumber < :tokenNumber")
    fun getPeopleAhead(tokenNumber: Int): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: QueueToken): Long

    @Update
    suspend fun updateToken(token: QueueToken)

    @Query("SELECT * FROM queue_tokens WHERE id = :id")
    fun getTokenById(id: Int): Flow<QueueToken?>

    @Query("SELECT * FROM queue_tokens WHERE id IN (:ids) ORDER BY tokenNumber ASC")
    fun getTokensByIds(ids: List<Int>): Flow<List<QueueToken>>

    @Query("SELECT * FROM queue_tokens WHERE status != 'COMPLETED' ORDER BY tokenNumber ASC")
    fun getActiveTokens(): Flow<List<QueueToken>>

    @Query("DELETE FROM queue_tokens")
    suspend fun clearQueue()

    @Query("SELECT MAX(tokenNumber) FROM queue_tokens")
    suspend fun getLastTokenNumber(): Int?

    // Service Record Operations
    @Insert
    suspend fun insertServiceRecord(record: com.example.adaptivequeue.data.model.ServiceRecord)

    @Query("SELECT * FROM service_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentServiceRecords(limit: Int): Flow<List<com.example.adaptivequeue.data.model.ServiceRecord>>
}
