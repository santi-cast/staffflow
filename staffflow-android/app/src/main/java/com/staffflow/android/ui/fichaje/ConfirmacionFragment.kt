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
import com.staffflow.android.databinding.FragmentConfirmacionBinding
import com.staffflow.android.domain.model.TipoPausa
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de bienvenida y confirmacion de fichaje (P06).
 *
 * Recibe el PIN de P01 via Bundle("pin"). Tiene dos fases visuales:
 *
 *   Fase A - Seleccion de accion (estado SeleccionandoAccion / Loading / Error):
 *     Muestra los 4 botones de accion. El usuario elige que quiere registrar.
 *     Para "Iniciar pausa" navega a P07 y recibe el tipo via FragmentResult.
 *
 *   Fase B - Resultado (estado Resultado):
 *     Muestra el icono de exito, los datos del fichaje y una cuenta atras de 5s.
 *     Tras la cuenta atras navega de vuelta a P01.
 *
 * Endpoints gestionados en ConfirmacionViewModel: E48/E49/E50/E51.
 */
class ConfirmacionFragment : Fragment() {

    private var _binding: FragmentConfirmacionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConfirmacionViewModel by viewModels()

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfirmacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        viewModel.init(
            pin            = args.getString("pin", ""),
            nombre         = args.getString("nombre", ""),
            estadoDia      = args.getString("estadoDia", ""),
            horaEntrada    = args.getString("horaEntrada"),
            horaSalida     = args.getString("horaSalida"),
            horaInicioPausa = args.getString("horaInicioPausa"),
            tipoPausa      = args.getString("tipoPausa")
        )

        configurarBotonesAccion()
        configurarFragmentResult()
        observarViewModel()
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
            viewModel.ejecutarAccion(AccionTerminal.ENTRADA)
        }
        binding.btnSalida.setOnClickListener {
            viewModel.ejecutarAccion(AccionTerminal.SALIDA)
        }
        binding.btnIniciarPausa.setOnClickListener {
            // Navega a P07 para seleccionar el tipo de pausa.
            // Al volver, FragmentResult("tipoPausa") ejecuta INICIAR_PAUSA.
            findNavController().navigate(R.id.action_confirmacion_to_tipo_pausa)
        }
        binding.btnFinalizarPausa.setOnClickListener {
            viewModel.ejecutarAccion(AccionTerminal.FINALIZAR_PAUSA)
        }
        binding.btnCancelar.setOnClickListener {
            findNavController().navigate(R.id.action_confirmacion_to_terminal)
        }
    }

    /**
     * Escucha el resultado de P07 (TipoPausaFragment).
     * Cuando el usuario selecciona un tipo de pausa, ejecuta INICIAR_PAUSA.
     */
    private fun configurarFragmentResult() {
        setFragmentResultListener("tipoPausa") { _, bundle ->
            @Suppress("DEPRECATION")
            val tipo = bundle.getSerializable("tipo") as TipoPausa
            viewModel.ejecutarAccion(AccionTerminal.INICIAR_PAUSA, tipo)
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

    private fun procesarEstado(estado: ConfirmacionUiState) {
        binding.progressIndicator.isVisible =
            estado is ConfirmacionUiState.CargandoEstado || estado is ConfirmacionUiState.Loading

        when (estado) {
            is ConfirmacionUiState.CargandoEstado -> {
                binding.layoutSeleccion.isVisible = false
                binding.layoutResultado.isVisible = false
            }
            is ConfirmacionUiState.BienvenidaConOpciones -> {
                binding.layoutSeleccion.isVisible = true
                binding.layoutResultado.isVisible = false
                binding.tvError.isVisible = false
                mostrarBienvenida(estado)
                configurarBotonesSegunEstado(estado.estadoDia)
                setBotonesHabilitados(true)
            }
            is ConfirmacionUiState.Loading -> {
                binding.layoutSeleccion.isVisible = true
                binding.layoutResultado.isVisible = false
                binding.tvError.isVisible = false
                setBotonesHabilitados(false)
            }
            is ConfirmacionUiState.Error -> {
                binding.layoutSeleccion.isVisible = true
                binding.layoutResultado.isVisible = false
                binding.tvError.text = estado.mensaje
                binding.tvError.isVisible = true
                setBotonesHabilitados(true)
            }
            is ConfirmacionUiState.Resultado -> {
                binding.layoutSeleccion.isVisible = false
                binding.layoutResultado.isVisible = true
                mostrarResultado(estado)
                iniciarCuentaAtras()
            }
        }
    }

    private fun mostrarBienvenida(estado: ConfirmacionUiState.BienvenidaConOpciones) {
        binding.tvBienvenida.text = getString(R.string.confirmacion_bienvenida, estado.nombre)
        binding.tvEstadoDia.text = when (estado.estadoDia) {
            "SIN_ENTRADA" -> getString(R.string.confirmacion_estado_sin_entrada)
            "EN_JORNADA" -> getString(R.string.confirmacion_estado_en_jornada, estado.horaEntrada ?: "–")
            "EN_PAUSA" -> getString(
                R.string.confirmacion_estado_en_pausa,
                estado.horaEntrada ?: "–",
                estado.tipoPausa?.let { tipoPausaNombre(it) } ?: "",
                estado.horaInicioPausa ?: "–"
            )
            "JORNADA_CERRADA" -> getString(
                R.string.confirmacion_estado_jornada_cerrada,
                estado.horaEntrada ?: "–",
                estado.horaSalida ?: "–"
            )
            else -> ""
        }
    }

    /**
     * Muestra solo los botones de accion validos segun el estado de la jornada.
     *
     *   SIN_ENTRADA     -> solo Entrada
     *   EN_JORNADA      -> Salida + Iniciar pausa
     *   EN_PAUSA        -> Finalizar pausa
     *   JORNADA_CERRADA -> ningun boton de accion (jornada ya cerrada)
     */
    private fun configurarBotonesSegunEstado(estadoDia: String) {
        val sinEntrada = estadoDia == "SIN_ENTRADA"
        val enJornada = estadoDia == "EN_JORNADA"
        val enPausa = estadoDia == "EN_PAUSA"
        val cerrada = estadoDia == "JORNADA_CERRADA"

        binding.btnEntrada.isVisible = sinEntrada
        binding.btnSalida.isVisible = enJornada
        binding.btnIniciarPausa.isVisible = enJornada
        binding.btnFinalizarPausa.isVisible = enPausa

        // Si la jornada esta cerrada, ocultar titulo de acciones y mostrar mensaje
        binding.tvQueRegistrar.isVisible = !cerrada
        if (cerrada) {
            binding.tvEstadoDia.append("\n" + getString(R.string.confirmacion_jornada_completada))
        }
    }

    private fun setBotonesHabilitados(habilitado: Boolean) {
        with(binding) {
            btnEntrada.isEnabled = habilitado
            btnSalida.isEnabled = habilitado
            btnIniciarPausa.isEnabled = habilitado
            btnFinalizarPausa.isEnabled = habilitado
            btnCancelar.isEnabled = habilitado
        }
    }

    private fun mostrarResultado(estado: ConfirmacionUiState.Resultado) {
        val (principal, secundario) = when (estado.accion) {
            AccionTerminal.ENTRADA -> {
                "Bienvenido/a, ${estado.nombre}" to
                        "Entrada registrada a las ${soloHora(estado.horaEntrada)}"
            }
            AccionTerminal.SALIDA -> {
                val resumen = buildString {
                    append("Entrada: ${soloHora(estado.horaEntrada)} · Salida: ${soloHora(estado.horaSalida)}")
                    val pausas = estado.numeroPausas ?: 0
                    if (pausas > 0) {
                        val totalPausas = estado.totalPausasMinutos ?: 0
                        append("\nPausas: $pausas (${totalPausas}min)")
                    }
                    append("\nTiempo trabajado: ${formatearJornada(estado.jornadaEfectivaMinutos ?: 0)}")
                }
                "Hasta luego, ${estado.nombre}" to resumen
            }
            AccionTerminal.INICIAR_PAUSA -> {
                val tipo = estado.tipoPausa?.let { tipoPausaNombre(it) } ?: ""
                "Pausa iniciada, ${estado.nombre}" to "${soloHora(estado.horaInicioPausa)} · $tipo"
            }
            AccionTerminal.FINALIZAR_PAUSA -> {
                "Pausa finalizada, ${estado.nombre}" to
                        "Inicio: ${soloHora(estado.horaInicioPausa)} · Fin: ${soloHora(estado.horaFinPausa)}\nDuración: ${estado.duracionPausaMinutos} minutos"
            }
        }
        binding.tvMensajePrincipal.text = principal
        binding.tvMensajeSecundario.text = secundario
    }

    // ------------------------------------------------------------------
    // Cuenta atras de 5 segundos
    // ------------------------------------------------------------------

    private fun iniciarCuentaAtras() {
        viewLifecycleOwner.lifecycleScope.launch {
            for (i in 5 downTo 1) {
                binding.tvCuentaAtras.text = getString(R.string.confirmacion_volviendo, i)
                delay(1000)
            }
            findNavController().navigate(R.id.action_confirmacion_to_terminal)
        }
    }

    // ------------------------------------------------------------------
    // Helpers de formato
    // ------------------------------------------------------------------

    /**
     * Extrae solo HH:mm de un ISO datetime ("2026-04-04T08:30:00" -> "08:30")
     * o devuelve tal cual si ya viene como "HH:mm" (E52).
     */
    private fun soloHora(valor: String?): String {
        if (valor == null) return "–"
        return if (valor.contains("T")) valor.substringAfter("T").take(5) else valor
    }

    /** Convierte minutos totales a "Xh YYm". Ejemplo: 485 -> "8h 05m". */
    private fun formatearJornada(minutos: Int): String {
        val h = minutos / 60
        val m = minutos % 60
        return "${h}h ${m.toString().padStart(2, '0')}m"
    }

    private fun tipoPausaNombre(tipo: TipoPausa): String = tipoPausaNombre(tipo.name)

    private fun tipoPausaNombre(tipo: String): String = when (tipo) {
        "COMIDA"              -> getString(R.string.tipo_pausa_comida)
        "DESCANSO"            -> getString(R.string.tipo_pausa_descanso)
        "AUSENCIA_RETRIBUIDA" -> getString(R.string.tipo_pausa_ausencia_retribuida)
        "OTROS"               -> getString(R.string.tipo_pausa_otros)
        else                  -> tipo
    }
}
