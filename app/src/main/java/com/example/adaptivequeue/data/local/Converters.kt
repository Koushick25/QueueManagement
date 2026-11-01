package com.example.adaptivequeue.data.local

import androidx.room.TypeConverter
import com.example.adaptivequeue.data.model.TokenStatus

class Converters {
    @TypeConverter
    fun fromTokenStatus(status: TokenStatus): String {
        return status.name
    }

    @TypeConverter
    fun toTokenStatus(value: String): TokenStatus {
        return TokenStatus.valueOf(value)
    }
}
