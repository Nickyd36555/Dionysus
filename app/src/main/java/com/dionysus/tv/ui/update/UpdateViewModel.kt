package com.dionysus.tv.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.data.update.ApkInstaller
import com.dionysus.tv.data.update.UpdateInfo
import com.dionysus.tv.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val info: UpdateInfo? = null,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val dismissed: Boolean = false,
) {
    val isAvailable: Boolean get() = info != null && !dismissed
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
    private val installer: ApkInstaller,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init { check() }

    fun check() {
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            val info = updates.checkForUpdate()
            _state.update {
                it.copy(
                    checking = false,
                    info = info,
                    message = if (info == null) "You're on the latest version." else null,
                )
            }
        }
    }

    fun update() {
        val info = _state.value.info ?: return
        if (!installer.canInstall()) {
            installer.openInstallPermissionSettings()
            _state.update { it.copy(message = "Allow installing unknown apps, then press Update again.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(downloading = true, progress = 0f, message = "Downloading update…") }
            try {
                val file = installer.download(info.downloadUrl) { p ->
                    _state.update { it.copy(progress = p) }
                }
                installer.install(file)
                _state.update { it.copy(downloading = false, message = "Launching installer…") }
            } catch (t: Throwable) {
                _state.update { it.copy(downloading = false, message = "Update failed: ${t.message}") }
            }
        }
    }

    fun dismiss() {
        _state.update { it.copy(dismissed = true) }
    }
}
