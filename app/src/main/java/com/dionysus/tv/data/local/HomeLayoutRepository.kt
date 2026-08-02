package com.dionysus.tv.data.local

import com.dionysus.tv.core.model.HomeRow
import com.dionysus.tv.core.model.HomeRowKind
import com.dionysus.tv.data.local.dao.HomeRowDao
import com.dionysus.tv.data.local.entity.HomeRowConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the user-customizable home layout. On first launch it seeds a sensible
 * default set of rows; thereafter the user can reorder, rename, toggle, and
 * remove them from Settings → Customize Home.
 */
@Singleton
class HomeLayoutRepository @Inject constructor(
    private val dao: HomeRowDao,
) {
    val rows: Flow<List<HomeRow>> = dao.observeAll().map { list ->
        list.mapNotNull { it.toDomain() }
    }

    suspend fun ensureSeeded() {
        if (dao.count() == 0) dao.upsertAll(DEFAULT_ROWS)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val existing = DEFAULT_ROWS.firstOrNull { it.id == id } ?: return
        dao.upsert(existing.copy(enabled = enabled))
    }

    suspend fun reorder(orderedIds: List<String>) {
        val current = orderedIds.mapIndexedNotNull { index, id ->
            DEFAULT_ROWS.firstOrNull { it.id == id }?.copy(position = index)
        }
        dao.upsertAll(current)
    }

    suspend fun upsert(row: HomeRow) = dao.upsert(row.toEntity())

    suspend fun remove(id: String) = dao.remove(id)

    private fun HomeRowConfigEntity.toDomain(): HomeRow? {
        val k = HomeRowKind.fromName(kind) ?: return null
        return HomeRow(id, k, title, position, enabled, param)
    }

    private fun HomeRow.toEntity() =
        HomeRowConfigEntity(id, kind.name, title, position, enabled, param)

    companion object {
        val DEFAULT_ROWS: List<HomeRowConfigEntity> = listOf(
            row("continue_watching", HomeRowKind.CONTINUE_WATCHING, "Continue Watching", 0),
            row("trending", HomeRowKind.TRENDING, "Trending Now", 1),
            row("my_list", HomeRowKind.MY_LIST, "My List", 2),
            row("popular_movies", HomeRowKind.POPULAR_MOVIES, "Popular Movies", 3),
            row("popular_shows", HomeRowKind.POPULAR_SHOWS, "Popular Shows", 4),
            row("top_rated", HomeRowKind.TOP_RATED_MOVIES, "Top Rated Movies", 5),
            row("downloads", HomeRowKind.DOWNLOADS, "Downloads", 6),
        )

        private fun row(id: String, kind: HomeRowKind, title: String, position: Int) =
            HomeRowConfigEntity(id, kind.name, title, position, enabled = true, param = null)
    }
}
