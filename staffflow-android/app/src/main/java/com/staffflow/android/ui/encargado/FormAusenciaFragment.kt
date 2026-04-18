package com.staffflow.android.ui.encargado

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.R
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.SaldoApiService
import com.staffflow.android.data.remote.repository.SaldoRepository
import com.staffflow.android.databinding.FragmentFormAusenciaBinding
import com.staffflow.android.domain.model.TipoAusencia
import kotlinx.coroutines.launch

/**
 * Formulario de ausencia planificada (P24).
 *
 * Modo alta   (ausenciaId = -1): E30 POST /ausencias.
 * Modo edicion (ausenciaId > 0): E31 PATCH /ausencias/{id}.
 * Eliminar    (ausenciaId > 0, procesado=false): E32 DELETE con dialogo previo (Decision 26).
 *
 * Si procesado=true el formulario se muestra en modo solo lectura:
 *   - campos deshabilitados, boton guardar oculto, banner informativo visible.
 *
 * El boton eliminar solo es visible en modo edicion con procesado=false.
 *
 * Argumentos de navegacion esperados (Bundle):
 *   ausenciaId  Long     -1 = alta | >0 = edicion   (obligatorio)
 *   procesado   Boolean  false por defecto           (obligatorio en edicion)
 *   empleadoId  Long     pre-rellena el campo        (opcional)
 *   fecha       String   yyyy-MM-dd                  (opcional, edicion)
 *   tipoAusencia String  nombre TipoAusencia         (opcional, edicion)
 *   observaciones String                             (opcional, edicion)
 */
class FormAusenciaFragment : Fragment() {

    private var _binding: FragmentFormAusenciaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormAusenciaViewModel by viewModels()

    private val saldoRepository by lazy {
        SaldoRepository(NetworkModule.retrofit.create(SaldoApiService::class.java))
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormAusenciaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: Bundle()
        val ausenciaId = args.getLong("ausenciaId", -1L)
        val procesado = args.getBoolean("procesado", false)

        viewModel.init(ausenciaId, procesado)

        configurarDropdown()
        preRellenarCampos(args)
        configurarModo()
        configurarListeners()
        observarViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------

    private fun configurarDropdown() {
        val tipos = TipoAusencia.values().map { tipoAusenciaLabel(it) }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tipos)
        binding.actvTipoAusencia.setAdapter(adapter)
    }

    private fun preRellenarCampos(args: Bundle) {
        args.getLong("empleadoId", -1L).takeIf { it > 0L }?.let {
            binding.etEmpleadoId.setText(it.toString())
        }
        args.getString("fecha")?.let { binding.etFecha.setText(it) }
        args.getString("observaciones")?.let { binding.etObservaciones.setText(it) }
        args.getString("tipoAusencia")?.let { nombre ->
            TipoAusencia.values().find { it.name == nombre }?.let {
                binding.actvTipoAusencia.setText(tipoAusenciaLabel(it), false)
            }
        }
    }

    private fun configurarModo() {
        val procesado = viewModel.procesado
        val modoEdicion = viewModel.modoEdicion

        // Solo lectura si procesado=true
        binding.bannerProcesado.isVisible = procesado
        binding.btnGuardar.isVisible = !procesado
        binding.btnEliminar.isVisible = modoEdicion && !procesado

        if (procesado) {
            binding.etEmpleadoId.isEnabled = false
            binding.etFecha.isEnabled = false
            binding.actvTipoAusencia.isEnabled = false
            binding.etObservaciones.isEnabled = false
        }
    }

    private fun configurarListeners() {
        binding.btnGuardar.setOnClickListener {
            val empleadoId = binding.etEmpleadoId.text.toString().toLongOrNull()
            val fecha = binding.etFecha.text.toString().trim()
            val tipo = TipoAusencia.values().find {
                tipoAusenciaLabel(it) == binding.actvTipoAusencia.text.toString()
            } ?: TipoAusencia.VACACIONES
            val observaciones = binding.etObservaciones.text.toString().trim().ifBlank { null }

            binding.btnGuardar.isEnabled = false
            try {
                viewModel.guardar(empleadoId, fecha, tipo, observaciones)
            } finally {
                binding.btnGuardar.isEnabled = true
            }
        }

        binding.btnEliminar.setOnClickListener {
            mostrarDialogoConfirmacion()
        }
    }

    // ------------------------------------------------------------------
    // Dialogo de confirmacion antes de eliminar (Decision 26)
    // ------------------------------------------------------------------

    private fun mostrarDialogoConfirmacion() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(com.staffflow.android.R.string.form_ausencia_dialogo_eliminar_titulo))
            .setMessage(getString(com.staffflow.android.R.string.form_ausencia_dialogo_eliminar_mensaje))
            .setNegativeButton(getString(com.staffflow.android.R.string.form_ausencia_dialogo_cancelar), null)
            .setPositiveButton(getString(com.staffflow.android.R.string.form_ausencia_dialogo_confirmar)) { _, _ ->
                viewModel.eliminar()
            }
            .show()
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { procesarEstado(it) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    private fun procesarEstado(estado: FormAusenciaViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is FormAusenciaViewModel.UiState.Loading
        binding.btnGuardar.isEnabled = estado !is FormAusenciaViewModel.UiState.Loading && !viewModel.procesado
        binding.btnEliminar.isEnabled = estado !is FormAusenciaViewModel.UiState.Loading

        when (estado) {
            is FormAusenciaViewModel.UiState.Success -> ofrecerRecalcular()
            is FormAusenciaViewModel.UiState.Deleted -> ofrecerRecalcular()
            is FormAusenciaViewModel.UiState.Error -> {
                Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                viewModel.limpiarError()
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Dialogo de recalcular saldo tras guardar/eliminar (E40)
    // ------------------------------------------------------------------

    private fun ofrecerRecalcular() {
        viewModel.resetUiState()
        val empleadoId = binding.etEmpleadoId.text.toString().toLongOrNull() ?: -1L
        val anio = binding.etFecha.text.toString().take(4).toIntOrNull()

        // Festivo global (empleadoId vacío) → no hay saldo que recalcular
        if (empleadoId <= 0L || anio == null) {
            findNavController().popBackStack()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.form_guardado_titulo)
            .setMessage(R.string.form_guardado_recalcular_mensaje)
            .setPositiveButton(R.string.saldo_recalcular) { _, _ ->
                lifecycleScope.launch {
                    saldoRepository.recalcularSaldo(empleadoId, anio)
                    findNavController().popBackStack()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                findNavController().popBackStack()
            }
            .setCancelable(false)
            .show()
    }

    // ------------------------------------------------------------------
    // Etiquetas legibles para el dropdown
    // ------------------------------------------------------------------

    private fun tipoAusenciaLabel(tipo: TipoAusencia): String = when (tipo) {
        TipoAusencia.FESTIVO_NACIONAL        -> "Festivo nacional"
        TipoAusencia.FESTIVO_LOCAL           -> "Festivo local"
        TipoAusencia.VACACIONES              -> "Vacaciones"
        TipoAusencia.ASUNTO_PROPIO           -> "Asunto propio"
        TipoAusencia.PERMISO_RETRIBUIDO      -> "Permiso retribuido"
        TipoAusencia.DIA_LIBRE_COMPENSATORIO -> "Día libre compensatorio"
        TipoAusencia.DIA_LIBRE               -> "Día libre"
    }
}
