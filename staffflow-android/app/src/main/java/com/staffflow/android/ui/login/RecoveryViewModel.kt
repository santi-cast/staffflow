package com.staffflow.android.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.AuthApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.PasswordRecoveryRequest
import com.staffflow.android.data.remote.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de recuperacion de contraseña (P03).
 *
 * E04 POST /auth/password/recovery.
 * El backend siempre responde 200 independientemente de si el email existe
 * (anti-enumeracion). La UI siempre muestra el mensaje informativo en Enviado.
 */
class RecoveryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(
        NetworkModule.retrofit.create(AuthApiService::class.java)
    )

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Enviado : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun enviar(email: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.recuperarPassword(PasswordRecoveryRequest(email)).fold(
                onSuccess  = { _uiState.value = UiState.Enviado },
                onFailure  = { _uiState.value = UiState.Error(it.message ?: "Error de conexion") }
            )
        }
    }

    fun limpiarError() {
        _uiState.value = UiState.Idle
    }
}
