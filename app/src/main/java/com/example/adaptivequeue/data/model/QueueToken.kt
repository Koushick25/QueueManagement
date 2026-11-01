package com.example.adaptivequeue.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TokenStatus {
    WAITING,
    SERVING,
    COMPLETED,
    MISSED
}

@Entity(tableName = "queue_tokens")
data class QueueToken(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tokenNumber: Int,
    val status: TokenStatus = TokenStatus.WAITING,
    val joinTime: Long = System.currentTimeMillis(),
    val serviceStartTime: Long? = null,
    val estimatedWaitMinutes: Int = 0,
    val visitorName: String = ""
)
