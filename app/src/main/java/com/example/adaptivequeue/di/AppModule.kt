package com.example.adaptivequeue.di

import android.content.Context
import androidx.room.Room
import com.example.adaptivequeue.data.local.AppDatabase
import com.example.adaptivequeue.data.local.QueueDao
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "queue_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideQueueDao(database: AppDatabase): QueueDao {
        return database.queueDao()
    }

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }
}
