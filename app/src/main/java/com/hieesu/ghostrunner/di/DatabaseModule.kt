package com.hieesu.ghostrunner.di

import android.content.Context
import androidx.room.Room
import com.hieesu.ghostrunner.data.local.GhostRunnerDatabase
import com.hieesu.ghostrunner.data.local.RouteHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Room database and DAO instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GhostRunnerDatabase {
        return Room.databaseBuilder(
            context,
            GhostRunnerDatabase::class.java,
            "ghost_runner_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideRouteHistoryDao(database: GhostRunnerDatabase): RouteHistoryDao {
        return database.routeHistoryDao()
    }
}
