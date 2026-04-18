package com.staffflow.android.ui.empleado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.EmpleadoApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.data.remote.repository.EmpleadoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del perfil del empleado autenticado (P08).
 *
 * Endpoint: E21 GET /empleados/me
 * Solo lectura. El empleado no puede editar su propio perfil.
 * El boton "Cambiar contrasena" navega a P04 (gestionado en el Fragment).
 */
class MiPerfilViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmpleadoRepository(
        NetworkModule.retrofit.create(EmpleadoApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val empleado: EmpleadoResponse) : UiState()
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
            repository.getMiPerfil().fold(
                onSuccess = { _uiState.value = UiState.Success(it) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al cargar perfil") }
            )
        }
    }
}
