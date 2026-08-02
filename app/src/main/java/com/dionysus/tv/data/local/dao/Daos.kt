package com.dionysus.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.dionysus.tv.data.local.entity.DownloadEntity
import com.dionysus.tv.data.local.entity.FavoriteEntity
import com.dionysus.tv.data.local.entity.HomeRowConfigEntity
import com.dionysus.tv.data.local.entity.InstalledAddonEntity
import com.dionysus.tv.data.local.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId)")
    fun observeIsFavorite(mediaId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun remove(mediaId: String)
}

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE id = :id")
    suspend fun get(id: String): WatchProgressEntity?

    @Upsert
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE id = :id")
    suspend fun remove(id: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: String): DownloadEntity?

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, bytesDownloaded = :bytes, totalBytes = :total WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, bytes: Long, total: Long)

    @Query("UPDATE downloads SET status = :status, localPath = :localPath WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, localPath: String?)

    @Delete
    suspend fun delete(download: DownloadEntity)
}

@Dao
interface AddonDao {
    @Query("SELECT * FROM addons ORDER BY position ASC")
    fun observeAll(): Flow<List<InstalledAddonEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM addons")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(addon: InstalledAddonEntity)

    @Query("DELETE FROM addons WHERE transportUrl = :url")
    suspend fun remove(url: String)
}

@Dao
interface HomeRowDao {
    @Query("SELECT * FROM home_rows ORDER BY position ASC")
    fun observeAll(): Flow<List<HomeRowConfigEntity>>

    @Query("SELECT COUNT(*) FROM home_rows")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(rows: List<HomeRowConfigEntity>)

    @Upsert
    suspend fun upsert(row: HomeRowConfigEntity)

    @Query("DELETE FROM home_rows WHERE id = :id")
    suspend fun remove(id: String)
}
