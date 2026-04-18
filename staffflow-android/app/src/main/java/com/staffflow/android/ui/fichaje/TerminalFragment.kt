package com.staffflow.android.ui.fichaje

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentTerminalBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Terminal de fichaje por PIN (P01).
 *
 * Pantalla raiz de la zona publica. Solo gestiona la entrada del PIN.
 * La accion de fichaje se elige en P06 (ConfirmacionFragment) tras introducir el PIN.
 *
 * Diseno kiosk premium (inspirado en la imagen de referencia):
 *   - Logo: simbolo + "Staff" (bold) + "Flow" (thin).
 *   - Reloj en sans-serif-thin 88sp como elemento hero.
 *   - Fecha corta sin año con letterSpacing amplio en mayusculas.
 *   - 4 circulos PIN: grises de placeholder → oscuros al introducir digito.
 *   - Numpad flat 3x3 + 0 centrado, sin ⌫ en el grid.
 *   - LIMPIAR (outlined): borra el PIN completo.
 *   - CONFIRMAR (filled dark): solo habilitado con 4 digitos, navega a P06.
 *   - Modo kiosk: barras del sistema ocultas en esta pantalla.
 *
 * Flujo (distinto al diseno anterior):
 *   Antes: 4o digito -> auto-navega a P06.
 *   Ahora: 4o digito -> habilita CONFIRMAR -> usuario confirma -> navega a P06.
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
        configurarFecha()
        configurarNumpad()
        observarViewModel()
        binding.btnIrALogin.setOnClickListener {
            findNavController().navigate(R.id.action_terminal_to_login)
        }
    }

    override fun onResume() {
        super.onResume()
        ocultarBarrasSistema()
    }

    override fun onPause() {
        super.onPause()
        mostrarBarrasSistema()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Modo kiosk
    // ------------------------------------------------------------------

    /**
     * Oculta la barra de estado y la barra de navegacion del sistema.
     * El usuario puede recuperarlas deslizando desde el borde (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE).
     * Se restauran en onPause para que el resto de fragmentos las vea correctamente.
     */
    private fun ocultarBarrasSistema() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun mostrarBarrasSistema() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, binding.root)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------

    /**
     * Formato corto sin año, mayusculas y letterSpacing amplio: "MIÉ., 4 ABR"
     * (coincide con el estilo de la imagen de referencia "WED, OCT 25").
     */
    private fun configurarFecha() {
        val fmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale("es", "ES"))
        binding.tvFecha.text = LocalDate.now().format(fmt).uppercase(Locale("es", "ES"))
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
        binding.btnLimpiar.setOnClickListener { viewModel.borrarDigito() }
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.pin.collect { actualizarDisplayPin(it) } }
                launch { viewModel.uiState.collect { procesarEstado(it) } }
            }
        }
    }

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    /**
     * Actualiza los 4 circulos del indicador de PIN.
     * Posicion con digito -> bg_pin_lleno (circulo oscuro).
     * Posicion vacia      -> bg_pin_vacio (circulo gris placeholder).
     */
    private fun actualizarDisplayPin(pin: String) {
        val circles = listOf(binding.vPin1, binding.vPin2, binding.vPin3, binding.vPin4)
        circles.forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (index < pin.length) R.drawable.bg_pin_lleno
                else R.drawable.bg_pin_vacio
            )
        }
    }

    private fun procesarEstado(estado: TerminalUiState) {
        binding.progressIndicator.isVisible = estado is TerminalUiState.VerificandoPin
        binding.tvError.isVisible = estado is TerminalUiState.Error

        when (estado) {
            is TerminalUiState.EsperandoPin -> {
                setNumpadHabilitado(true)
            }
            is TerminalUiState.VerificandoPin -> {
                setNumpadHabilitado(false)
            }
            is TerminalUiState.Error -> {
                binding.tvError.text = estado.mensaje
                setNumpadHabilitado(false)
            }
            is TerminalUiState.PinVerificado -> {
                setNumpadHabilitado(false)
                findNavController().navigate(
                    R.id.action_terminal_to_confirmacion,
                    Bundle().apply {
                        putString("pin", estado.pin)
                        putString("nombre", estado.nombre)
                        putString("estadoDia", estado.estadoDia)
                        estado.horaEntrada?.let { putString("horaEntrada", it) }
                        estado.horaSalida?.let { putString("horaSalida", it) }
                        estado.horaInicioPausa?.let { putString("horaInicioPausa", it) }
                        estado.tipoPausa?.let { putString("tipoPausa", it) }
                    }
                )
                viewModel.resetEstado()
            }
        }
    }

    private fun setNumpadHabilitado(habilitado: Boolean) {
        with(binding) {
            btn0.isEnabled = habilitado
            btn1.isEnabled = habilitado
            btn2.isEnabled = habilitado
            btn3.isEnabled = habilitado
            btn4.isEnabled = habilitado
            btn5.isEnabled = habilitado
            btn6.isEnabled = habilitado
            btn7.isEnabled = habilitado
            btn8.isEnabled = habilitado
            btn9.isEnabled = habilitado
            btnLimpiar.isEnabled = habilitado
            btnIrALogin.isEnabled = habilitado
        }
    }
}
