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
 * ViewModel de restablecimiento de contraseña (P05).
 *
 * **v1.0 — no operativo:** en v1 este flujo entrega una contraseña temporal
 * de 8 caracteres por email (E04). El token UUID de 30 minutos descrito a
 * continuación pertenece al andamiaje reservado para v2.0 (ver memoria TFG,
 * bloque B10 Vías Futuras → Reset password con token UUID).
 *
 * En v1 este ViewModel forma parte del andamiaje v2.0 y no se invoca desde
 * ningún flujo de producción. Si en pruebas se forzase su uso, la llamada a
 * E05 `POST /auth/password/reset` devolvería HTTP 400 de forma determinista
 * porque la base de datos nunca contiene tokens válidos (en v1 nadie escribe
 * `resetToken` en la entidad `Usuario`).
 *
 * Comportamiento previsto (v2.0): E05 POST /auth/password/reset, sin JWT.
 * Error 400 si el token ha expirado o es inválido -> estado Error
 * (el Fragment muestra el mensaje del backend + botón volver a login).
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
