package com.staffflow.android.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.EmpresaApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.EmpresaRequest
import com.staffflow.android.data.remote.dto.EmpresaResponse
import com.staffflow.android.data.remote.repository.EmpresaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de la configuracion de empresa (P34). Solo ADMIN.
 *
 * Carga los datos actuales con E06 GET /empresa.
 * Guarda los cambios con E07 PUT /empresa (objeto completo).
 *
 * UiState:
 *   Loading  -> cargando datos iniciales (progressIndicator)
 *   Success  -> datos cargados, formulario rellenable
 *   Saving   -> PUT en curso (botones deshabilitados)
 *   Saved    -> PUT correcto (Fragment muestra Snackbar + recarga)
 *   Error    -> error de carga o guardado
 */
class EmpresaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmpresaRepository(
        NetworkModule.retrofit.create(EmpresaApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val empresa: EmpresaResponse) : UiState()
        object Saving : UiState()
        data class Saved(val empresa: EmpresaResponse) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun reintentar() = cargar()

    /**
     * Guarda la configuracion de empresa (E07 PUT /empresa).
     * Envia el objeto completo. Error 409 si el CIF ya existe.
     */
    fun guardar(
        nombreEmpresa: String,
        cif: String,
        direccion: String?,
        email: String?,
        telefono: String?,
        logoPath: String?
    ) {
        if (nombreEmpresa.isBlank() || cif.isBlank()) {
            _uiState.value = UiState.Error("Nombre y CIF son obligatorios")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Saving
            val request = EmpresaRequest(
                nombreEmpresa = nombreEmpresa,
                cif = cif,
                direccion = direccion?.ifBlank { null },
                email = email?.ifBlank { null },
                telefono = telefono?.ifBlank { null },
                logoPath = logoPath?.ifBlank { null }
            )
            repository.actualizarEmpresa(request).fold(
                onSuccess = { _uiState.value = UiState.Saved(it) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al guardar") }
            )
        }
    }

    fun limpiarError() {
        if (_uiState.value is UiState.Error) cargar()
    }

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getEmpresa().fold(
                onSuccess = { _uiState.value = UiState.Success(it) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al cargar empresa") }
            )
        }
    }
}
