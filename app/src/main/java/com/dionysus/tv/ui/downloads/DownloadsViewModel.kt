package com.dionysus.tv.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.data.local.entity.DownloadEntity
import com.dionysus.tv.download.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = repository.downloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pause(download: DownloadEntity) {
        viewModelScope.launch { repository.pause(download) }
    }

    fun resume(download: DownloadEntity) {
        viewModelScope.launch { repository.resume(download) }
    }

    fun delete(download: DownloadEntity) {
        viewModelScope.launch { repository.delete(download) }
    }
}
