package com.dionysus.tv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dionysus.tv.data.local.dao.DownloadDao
import com.dionysus.tv.data.local.dao.FavoriteDao
import com.dionysus.tv.data.local.dao.HomeRowDao
import com.dionysus.tv.data.local.dao.WatchProgressDao
import com.dionysus.tv.data.local.entity.DownloadEntity
import com.dionysus.tv.data.local.entity.FavoriteEntity
import com.dionysus.tv.data.local.entity.HomeRowConfigEntity
import com.dionysus.tv.data.local.entity.WatchProgressEntity

@Database(
    entities = [
        FavoriteEntity::class,
        WatchProgressEntity::class,
        DownloadEntity::class,
        HomeRowConfigEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class DionysusDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun downloadDao(): DownloadDao
    abstract fun homeRowDao(): HomeRowDao

    companion object {
        const val NAME = "dionysus.db"
    }
}
