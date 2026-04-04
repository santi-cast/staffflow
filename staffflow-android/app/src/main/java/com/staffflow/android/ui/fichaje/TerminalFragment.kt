package com.staffflow.android.ui.fichaje

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentTerminalBinding
import com.staffflow.android.domain.model.TipoPausa
import kotlinx.coroutines.launch

/**
 * Terminal de fichaje por PIN (P01).
 *
 * Pantalla raiz de la zona publica. Permite a los empleados registrar
 * entrada, salida e inicio/fin de pausa mediante PIN de 4 digitos,
 * sin necesidad de autenticacion JWT.
 *
 * Endpoints: E48 POST /terminal/entrada  | E49 POST /terminal/salida
 *            E50 POST /terminal/pausa/iniciar | E51 POST /terminal/pausa/finalizar
 *
 * Flujo:
 *   1. Usuario selecciona accion (Entrada | Salida | Iniciar pausa | Finalizar pausa).
 *   2. Para Iniciar pausa: navega a P07 (TipoPausaFragment), recibe FragmentResult.
 *   3. Usuario introduce 4 digitos -> llamada automatica al endpoint.
 *   4. Exito  -> navega a P06 (ConfirmacionFragment) con datos del resultado.
 *   5. Error  -> muestra mensaje en pantalla, el terminal permanece activo.
 *   6. HTTP 423 -> "Dispositivo bloqueado" (5 intentos fallidos).
 *
 * Decision 25: todos los botones se deshabilitan durante la llamada de red.
 */
class TerminalFragment : Fragment() {

    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TerminalViewModel by viewModels()

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarBotonesAccion()
        configurarNumpad()
        configurarFragmentResult()
        observarViewModel()
        binding.btnIrALogin.setOnClickListener {
            findNavController().navigate(R.id.action_terminal_to_login)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------

    private fun configurarBotonesAccion() {
        binding.btnEntrada.setOnClickListener {
            viewModel.setAccion(AccionTerminal.ENTRADA)
        }
        binding.btnSalida.setOnClickListener {
            viewModel.setAccion(AccionTerminal.SALIDA)
        }
        binding.btnIniciarPausa.setOnClickListener {
            // Navega a P07 para seleccionar el tipo de pausa.
            // Al volver, FragmentResult("tipoPausa") activa INICIAR_PAUSA en el ViewModel.
            findNavController().navigate(R.id.action_terminal_to_tipo_pausa)
        }
        binding.btnFinalizarPausa.setOnClickListener {
            viewModel.setAccion(AccionTerminal.FINALIZAR_PAUSA)
        }
    }

    private fun configurarNumpad() {
        listOf(
            binding.btn1 to 1, binding.btn2 to 2, binding.btn3 to 3,
            binding.btn4 to 4, binding.btn5 to 5, binding.btn6 to 6,
            binding.btn7 to 7, binding.btn8 to 8, binding.btn9 to 9,
            binding.btn0 to 0
        ).forEach { (btn, digito) ->
            btn.setOnClickListener { viewModel.appendDigito(digito) }
        }
        binding.btnBorrar.setOnClickListener { viewModel.borrarDigito() }
    }

    /**
     * Escucha el resultado de P07 (TipoPausaFragment).
     * Cuando el usuario selecciona un tipo de pausa, el ViewModel activa
     * INICIAR_PAUSA y guarda el tipo para la llamada a E50.
     */
    private fun configurarFragmentResult() {
        setFragmentResultListener("tipoPausa") { _, bundle ->
            @Suppress("DEPRECATION")
            val tipo = bundle.getSerializable("tipo") as TipoPausa
            viewModel.setAccionConPausa(tipo)
        }
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.pin.collect { actualizarDisplayPin(it) } }
                launch { viewModel.accionActiva.collect { actualizarBotonesAccion(it) } }
                launch { viewModel.uiState.collect { procesarEstado(it) } }
            }
        }
    }

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    /**
     * Actualiza los 4 circulos del display de PIN.
     * Digito introducido -> "●", posicion vacia -> "○".
     */
    private fun actualizarDisplayPin(pin: String) {
        binding.tvPin.text = (0..3).joinToString("  ") { i ->
            if (i < pin.length) "●" else "○"
        }
    }

    /**
     * Marca el boton de la accion activa con alpha=1.0.
     * Los botones inactivos quedan con alpha=0.5.
     * El boton "Iniciar pausa" no se marca directamente desde aqui:
     * se activa al volver de P07 via FragmentResult.
     */
    private fun actualizarBotonesAccion(accionActiva: AccionTerminal) {
        listOf(
            AccionTerminal.ENTRADA to binding.btnEntrada,
            AccionTerminal.SALIDA to binding.btnSalida,
            AccionTerminal.INICIAR_PAUSA to binding.btnIniciarPausa,
            AccionTerminal.FINALIZAR_PAUSA to binding.btnFinalizarPausa
        ).forEach { (accion, btn) ->
            btn.alpha = if (accion == accionActiva) 1f else 0.5f
        }
    }

    private fun procesarEstado(estado: TerminalUiState) {
        val cargando = estado is TerminalUiState.Loading
        setInteraccionHabilitada(!cargando)
        binding.progressIndicator.isVisible = cargando

        when (estado) {
            is TerminalUiState.Idle -> {
                binding.tvError.isVisible = false
            }
            is TerminalUiState.Loading -> {
                binding.tvError.isVisible = false
            }
            is TerminalUiState.Error -> {
                binding.tvError.text = estado.mensaje
                binding.tvError.isVisible = true
            }
            is TerminalUiState.Exito -> {
                // Resetear antes de navegar para que al volver el terminal quede limpio
                viewModel.resetEstado()
                findNavController().navigate(
                    R.id.action_terminal_to_confirmacion,
                    Bundle().apply {
                        putString("accion", estado.accion.name)
                        putString("nombre", estado.nombre)
                        estado.horaEntrada?.let { putString("horaEntrada", it) }
                        estado.horaSalida?.let { putString("horaSalida", it) }
                        estado.jornadaEfectivaMinutos?.let { putInt("jornadaEfectivaMinutos", it) }
                        estado.horaInicioPausa?.let { putString("horaInicioPausa", it) }
                        estado.duracionPausaMinutos?.let { putInt("duracionPausaMinutos", it) }
                        estado.tipoPausa?.let { putString("tipoPausa", it.name) }
                    }
                )
            }
        }
    }

    /** Habilita o deshabilita todos los botones interactivos. Decision 25. */
    private fun setInteraccionHabilitada(habilitada: Boolean) {
        with(binding) {
            btnEntrada.isEnabled = habilitada
            btnSalida.isEnabled = habilitada
            btnIniciarPausa.isEnabled = habilitada
            btnFinalizarPausa.isEnabled = habilitada
            btn0.isEnabled = habilitada
            btn1.isEnabled = habilitada
            btn2.isEnabled = habilitada
            btn3.isEnabled = habilitada
            btn4.isEnabled = habilitada
            btn5.isEnabled = habilitada
            btn6.isEnabled = habilitada
            btn7.isEnabled = habilitada
            btn8.isEnabled = habilitada
            btn9.isEnabled = habilitada
            btnBorrar.isEnabled = habilitada
            btnIrALogin.isEnabled = habilitada
        }
    }
}
