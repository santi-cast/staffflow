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
import com.staffflow.android.databinding.FragmentFormFichajeBinding
import com.staffflow.android.domain.model.TipoFichaje
import com.staffflow.android.domain.model.TipoPausa
import kotlinx.coroutines.launch

/**
 * Formulario de registro manual de fichaje o pausa (P20).
 *
 * Variante FICHAJE: campos tipo (TipoFichaje), horaEntrada, horaSalida.
 *   Modo alta   -> E22 POST /fichajes.
 *   Modo edicion -> E23 PATCH /fichajes/{id}.
 *
 * Variante PAUSA: campos tipoPausa (TipoPausa), horaInicio, horaFin.
 *   Modo alta   -> E27 POST /pausas.
 *   Modo edicion -> E28 PATCH /pausas/{id}.
 *
 * Argumentos de navegacion esperados (Bundle):
 *   variante    String  "FICHAJE" | "PAUSA"         (obligatorio)
 *   fichajeId   Long    -1 = alta | >0 = edicion    (variante FICHAJE)
 *   pausaId     Long    -1 = alta | >0 = edicion    (variante PAUSA)
 *   empleadoId  Long    pre-rellena el campo         (opcional)
 *   fecha       String  pre-rellena el campo         (opcional)
 *   tipo        String  nombre TipoFichaje           (opcional, edicion)
 *   tipoPausa   String  nombre TipoPausa             (opcional, edicion)
 *   horaEntrada String  HH:mm                        (opcional)
 *   horaSalida  String  HH:mm                        (opcional)
 *   horaInicio  String  HH:mm                        (opcional)
 *   horaFin     String  HH:mm                        (opcional)
 *   observaciones String                             (opcional, edicion)
 *
 * Las observaciones son OBLIGATORIAS al guardar (RNF-L02).
 * El boton GUARDAR se deshabilita durante la llamada al API (Decision 25).
 */
class FormFichajeFragment : Fragment() {

    private var _binding: FragmentFormFichajeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormFichajeViewModel by viewModels()

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
        _binding = FragmentFormFichajeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: Bundle()
        val varianteStr = args.getString("variante", "FICHAJE")
        val variante = if (varianteStr == "PAUSA") FormFichajeViewModel.Variante.PAUSA
                       else FormFichajeViewModel.Variante.FICHAJE
        val fichajeId = args.getLong("fichajeId", -1L)
        val pausaId = args.getLong("pausaId", -1L)

        viewModel.init(variante, fichajeId, pausaId)

        configurarDropdowns()
        configurarVisibilidadVariante(variante)
        preRellenarCampos(args, variante)
        configurarListeners(variante)
        observarViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------

    private fun configurarDropdowns() {
        // Dropdown TipoFichaje
        val tiposFichaje = TipoFichaje.values().map { tipoFichajeLabel(it) }
        val adapterFichaje = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tiposFichaje)
        binding.actvTipoFichaje.setAdapter(adapterFichaje)

        // Dropdown TipoPausa
        val tiposPausa = TipoPausa.values().map { tipoPausaLabel(it) }
        val adapterPausa = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tiposPausa)
        binding.actvTipoPausa.setAdapter(adapterPausa)
    }

    private fun configurarVisibilidadVariante(variante: FormFichajeViewModel.Variante) {
        val esFichaje = variante == FormFichajeViewModel.Variante.FICHAJE
        binding.grupoFichaje.isVisible = esFichaje
        binding.grupoPausa.isVisible = !esFichaje
    }

    private fun preRellenarCampos(args: Bundle, variante: FormFichajeViewModel.Variante) {
        args.getLong("empleadoId", -1L).takeIf { it > 0L }?.let {
            binding.etEmpleadoId.setText(it.toString())
        }
        args.getString("fecha")?.let { binding.etFecha.setText(it) }
        args.getString("observaciones")?.let { binding.etObservaciones.setText(it) }

        if (variante == FormFichajeViewModel.Variante.FICHAJE) {
            args.getString("tipo")?.let { nombre ->
                TipoFichaje.values().find { it.name == nombre }?.let {
                    binding.actvTipoFichaje.setText(tipoFichajeLabel(it), false)
                }
            }
            args.getString("horaEntrada")?.let { binding.etHoraEntrada.setText(it) }
            args.getString("horaSalida")?.let { binding.etHoraSalida.setText(it) }
        } else {
            args.getString("tipoPausa")?.let { nombre ->
                TipoPausa.values().find { it.name == nombre }?.let {
                    binding.actvTipoPausa.setText(tipoPausaLabel(it), false)
                }
            }
            args.getString("horaInicio")?.let { binding.etHoraInicio.setText(it) }
            args.getString("horaFin")?.let { binding.etHoraFin.setText(it) }
        }
    }

    private fun configurarListeners(variante: FormFichajeViewModel.Variante) {
        binding.btnGuardar.setOnClickListener {
            binding.tilObservaciones.error = null
            val empleadoId = binding.etEmpleadoId.text.toString().toLongOrNull() ?: -1L
            val fecha = binding.etFecha.text.toString().trim()
            val observaciones = binding.etObservaciones.text.toString().trim()

            binding.btnGuardar.isEnabled = false
            try {
                if (variante == FormFichajeViewModel.Variante.FICHAJE) {
                    val tipo = TipoFichaje.values().find {
                        tipoFichajeLabel(it) == binding.actvTipoFichaje.text.toString()
                    } ?: TipoFichaje.NORMAL
                    viewModel.guardarFichaje(
                        empleadoId = empleadoId,
                        fecha = fecha,
                        tipo = tipo,
                        horaEntrada = binding.etHoraEntrada.text.toString().trim().ifBlank { null },
                        horaSalida = binding.etHoraSalida.text.toString().trim().ifBlank { null },
                        observaciones = observaciones
                    )
                } else {
                    val tipoPausa = TipoPausa.values().find {
                        tipoPausaLabel(it) == binding.actvTipoPausa.text.toString()
                    } ?: TipoPausa.OTROS
                    viewModel.guardarPausa(
                        empleadoId = empleadoId,
                        fecha = fecha,
                        tipoPausa = tipoPausa,
                        horaInicio = binding.etHoraInicio.text.toString().trim(),
                        horaFin = binding.etHoraFin.text.toString().trim().ifBlank { null },
                        observaciones = observaciones
                    )
                }
            } finally {
                binding.btnGuardar.isEnabled = true
            }
        }
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

    private fun procesarEstado(estado: FormFichajeViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is FormFichajeViewModel.UiState.Loading
        binding.btnGuardar.isEnabled = estado !is FormFichajeViewModel.UiState.Loading

        when (estado) {
            is FormFichajeViewModel.UiState.Success -> ofrecerRecalcular()
            is FormFichajeViewModel.UiState.Error -> {
                // Observaciones obligatorias: error en ese campo
                if (estado.mensaje.contains("observaciones", ignoreCase = true)) {
                    binding.tilObservaciones.error = estado.mensaje
                } else {
                    Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                }
                viewModel.limpiarError()
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Dialogo de recalcular saldo tras guardar (E40)
    // ------------------------------------------------------------------

    private fun ofrecerRecalcular() {
        viewModel.resetUiState()
        val empleadoId = binding.etEmpleadoId.text.toString().toLongOrNull() ?: -1L
        val anio = binding.etFecha.text.toString().take(4).toIntOrNull()

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
    // Etiquetas legibles para dropdowns
    // ------------------------------------------------------------------

    private fun tipoFichajeLabel(tipo: TipoFichaje): String = when (tipo) {
        TipoFichaje.NORMAL                  -> "Normal"
        TipoFichaje.FESTIVO_NACIONAL        -> "Festivo nacional"
        TipoFichaje.FESTIVO_LOCAL           -> "Festivo local"
        TipoFichaje.VACACIONES              -> "Vacaciones"
        TipoFichaje.ASUNTO_PROPIO           -> "Asunto propio"
        TipoFichaje.PERMISO_RETRIBUIDO      -> "Permiso retribuido"
        TipoFichaje.BAJA_MEDICA             -> "Baja médica"
        TipoFichaje.DIA_LIBRE_COMPENSATORIO -> "Día libre compensatorio"
        TipoFichaje.AUSENCIA_INJUSTIFICADA  -> "Ausencia injustificada"
        TipoFichaje.DIA_LIBRE               -> "Día libre"
    }

    private fun tipoPausaLabel(tipo: TipoPausa): String = when (tipo) {
        TipoPausa.COMIDA              -> "Comida"
        TipoPausa.DESCANSO            -> "Descanso"
        TipoPausa.AUSENCIA_RETRIBUIDA -> "Ausencia retribuida"
        TipoPausa.OTROS               -> "Otros"
    }
}
