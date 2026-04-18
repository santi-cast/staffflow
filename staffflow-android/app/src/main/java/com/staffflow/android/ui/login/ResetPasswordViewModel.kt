package com.staffflow.android.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.AuthApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.PasswordResetRequest
import com.staffflow.android.data.remote.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de restablecimiento de contraseña con token (P05).
 *
 * E05 POST /auth/password/reset. Sin JWT.
 * Error 400 si el token ha expirado o es invalido -> estado Error
 * (el Fragment muestra el mensaje del backend + boton volver a login).
 */
class ResetPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(
        NetworkModule.retrofit.create(AuthApiService::class.java)
    )

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Restablecido : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun restablecer(token: String, passwordNueva: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.restablecerPassword(PasswordResetRequest(token, passwordNueva)).fold(
                onSuccess  = { _uiState.value = UiState.Restablecido },
                onFailure  = { _uiState.value = UiState.Error(it.message ?: "Error al restablecer la contraseña") }
            )
        }
    }

    fun limpiarError() {
        _uiState.value = UiState.Idle
    }
}
