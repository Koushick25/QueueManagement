package com.example.adaptivequeue.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.adaptivequeue.data.model.QueueToken
import com.example.adaptivequeue.data.model.ServiceRecord

@Database(entities = [QueueToken::class, ServiceRecord::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao
}
