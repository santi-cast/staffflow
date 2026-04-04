package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.PresenciaApiService
import com.staffflow.android.data.remote.dto.ParteDiarioResponse
import com.staffflow.android.data.remote.repository.PresenciaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel del parte diario de presencia (P17). Destino inicial del rol ENCARGADO.
 *
 * Llama a E35 GET /presencia/parte-diario?fecha= via PresenciaRepository.
 * La fecha por defecto es hoy. ParteDiarioFragment actualiza la fecha via setFecha()
 * desde el selector de la toolbar (MaterialDatePicker).
 */
class ParteDiarioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PresenciaRepository(
        NetworkModule.retrofit.create(PresenciaApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val data: ParteDiarioResponse) : UiState()
        object Empty : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _fecha = MutableStateFlow(hoy())
    /**
     * Fecha actualmente seleccionada en formato "yyyy-MM-dd".
     * ParteDiarioFragment la usa para actualizar el titulo del menu de la toolbar.
     */
    val fecha: StateFlow<String> = _fecha.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    /** Estado de la UI. ParteDiarioFragment observa este flow. */
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        cargarParteDiario()
    }

    /**
     * Cambia la fecha y recarga el parte diario.
     * Llamado desde ParteDiarioFragment cuando el usuario selecciona una fecha.
     * @param fecha Fecha en formato "yyyy-MM-dd".
     */
    fun setFecha(fecha: String) {
        _fecha.value = fecha
        cargarParteDiario()
    }

    /** Recarga el parte diario con la fecha actual. Llamado desde el boton Reintentar y pull-to-refresh. */
    fun reintentar() = cargarParteDiario()

    private fun cargarParteDiario() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getParteDiario(_fecha.value).fold(
                onSuccess = { resp ->
                    _uiState.value = if (resp.detalle.isEmpty()) UiState.Empty
                                     else UiState.Success(resp)
                },
                onFailure = {
                    _uiState.value = UiState.Error(it.message ?: "Error al cargar el parte diario")
                }
            )
        }
    }

    companion object {
        fun hoy(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
