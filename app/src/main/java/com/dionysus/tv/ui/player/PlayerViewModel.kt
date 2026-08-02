package com.dionysus.tv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.data.local.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
) : ViewModel() {

    val url: String = savedStateHandle.get<String>("url").orEmpty()
    val title: String = savedStateHandle.get<String>("title").orEmpty()
    private val progressId: String = savedStateHandle.get<String>("progressId").orEmpty()

    /** Persist the resume point so the title reappears in Continue Watching. */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (progressId.isBlank() || durationMs <= 0) return
        val mediaId = if (progressId.contains(":s")) progressId.substringBefore(":s") else progressId
        val type = if (mediaId.contains(":tv:")) MediaType.TV_SHOW else MediaType.MOVIE
        viewModelScope.launch {
            library.saveProgress(
                id = progressId,
                mediaId = mediaId,
                type = type,
                title = title,
                subtitle = null,
                posterUrl = null,
                backdropUrl = null,
                positionMs = positionMs,
                durationMs = durationMs,
            )
        }
    }
}
