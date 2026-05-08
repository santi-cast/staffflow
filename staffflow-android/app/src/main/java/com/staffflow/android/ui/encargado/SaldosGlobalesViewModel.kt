package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.InformeApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.repository.InformeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel de Saldos globales (P27).
 *
 * Llama a E44 GET /informes/saldos?anio=&formato=html via InformeRepository.
 * Devuelve el HTML generado por InformeService para cargarlo en un WebView.
 * El año por defecto es el año actual.
 *
 * UiState:
 *   Loading -> CircularProgressIndicator centrado
 *   Success -> WebView con el HTML del informe
 *   Error   -> icono nube + mensaje + Reintentar
 */
class SaldosGlobalesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InformeRepository(
        NetworkModule.retrofit.create(InformeApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val html: String) : UiState()
        data class Empty(val anio: Int) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _anio = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val anio: StateFlow<Int> = _anio.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun setAnio(anio: Int) {
        _anio.value = anio
        cargar()
    }

    fun reintentar() = cargar()

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getInformeSaldosHtml(_anio.value).fold(
                onSuccess = { body -> _uiState.value = UiState.Success(body.string()) },
                onFailure = {
                    if (it.message?.startsWith("No hay datos de saldo") == true) {
                        _uiState.value = UiState.Empty(_anio.value)
                    } else {
                        _uiState.value = UiState.Error(it.message ?: "Error al cargar los saldos")
                    }
                }
            )
        }
    }
}
