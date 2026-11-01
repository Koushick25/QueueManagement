package com.example.adaptivequeue.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_records")
data class ServiceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tokenNumber: Int,
    val durationMillis: Long,
    val timestamp: Long = System.currentTimeMillis()
)
