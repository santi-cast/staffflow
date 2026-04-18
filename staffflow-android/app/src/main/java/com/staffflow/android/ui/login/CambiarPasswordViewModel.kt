package com.staffflow.android.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.AuthApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.PasswordChangeRequest
import com.staffflow.android.data.remote.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de cambio de contraseña (P04).
 *
 * E03 PUT /auth/password. Requiere JWT.
 * Error 401 si passwordActual es incorrecto.
 * La validacion local nueva == repetir se hace en el Fragment antes de llamar aqui.
 */
class CambiarPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(
        NetworkModule.retrofit.create(AuthApiService::class.java)
    )

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Guardado : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun guardar(passwordActual: String, passwordNueva: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.cambiarPassword(PasswordChangeRequest(passwordActual, passwordNueva)).fold(
                onSuccess  = { _uiState.value = UiState.Guardado },
                onFailure  = { _uiState.value = UiState.Error(it.message ?: "Error al cambiar la contraseña") }
            )
        }
    }

    fun limpiarError() {
        _uiState.value = UiState.Idle
    }
}
