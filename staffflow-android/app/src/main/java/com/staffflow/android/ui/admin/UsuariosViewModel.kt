package com.staffflow.android.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.UsuarioApiService
import com.staffflow.android.data.remote.dto.UsuarioResponse
import com.staffflow.android.data.remote.repository.UsuarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de la lista de usuarios del sistema (P31). Solo ADMIN.
 *
 * Endpoint: E09 GET /usuarios
 * Carga todos los usuarios sin filtro al inicio.
 * onResume() del fragment llama reintentar() para refrescar tras volver de P32.
 */
class UsuariosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsuarioRepository(
        NetworkModule.retrofit.create(UsuarioApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val usuarios: List<UsuarioResponse>) : UiState()
        object Empty : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun reintentar() = cargar()

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.listarUsuarios().fold(
                onSuccess = { lista ->
                    _uiState.value = if (lista.isEmpty()) UiState.Empty
                                     else UiState.Success(lista)
                },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al cargar usuarios") }
            )
        }
    }
}
