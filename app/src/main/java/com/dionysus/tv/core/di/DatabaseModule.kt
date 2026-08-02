package com.dionysus.tv.core.di

import android.content.Context
import androidx.room.Room
import com.dionysus.tv.data.local.DionysusDatabase
import com.dionysus.tv.data.local.dao.AddonDao
import com.dionysus.tv.data.local.dao.DownloadDao
import com.dionysus.tv.data.local.dao.FavoriteDao
import com.dionysus.tv.data.local.dao.HomeRowDao
import com.dionysus.tv.data.local.dao.WatchProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DionysusDatabase =
        Room.databaseBuilder(context, DionysusDatabase::class.java, DionysusDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideFavoriteDao(db: DionysusDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideWatchProgressDao(db: DionysusDatabase): WatchProgressDao = db.watchProgressDao()
    @Provides fun provideDownloadDao(db: DionysusDatabase): DownloadDao = db.downloadDao()
    @Provides fun provideHomeRowDao(db: DionysusDatabase): HomeRowDao = db.homeRowDao()
    @Provides fun provideAddonDao(db: DionysusDatabase): AddonDao = db.addonDao()
}
