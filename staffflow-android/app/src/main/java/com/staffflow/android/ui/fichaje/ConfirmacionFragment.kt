package com.staffflow.android.ui.fichaje

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentConfirmacionBinding
import com.staffflow.android.domain.model.TipoPausa
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de confirmacion de fichaje (P06).
 *
 * Feedback visual de 3 segundos tras una operacion exitosa en el terminal (P01).
 * No tiene interaccion de usuario ni ViewModel propio (sin red, sin estado complejo).
 *
 * Recibe via Bundle (nav args de action_terminal_to_confirmacion):
 *   "accion"                 -> AccionTerminal.name (ENTRADA/SALIDA/INICIAR_PAUSA/FINALIZAR_PAUSA)
 *   "nombre"                 -> nombre del empleado
 *   "horaEntrada"            -> String HH:mm (solo ENTRADA)
 *   "horaSalida"             -> String HH:mm (solo SALIDA)
 *   "jornadaEfectivaMinutos" -> Int (solo SALIDA)
 *   "horaInicioPausa"        -> String HH:mm (solo INICIAR_PAUSA)
 *   "duracionPausaMinutos"   -> Int (solo FINALIZAR_PAUSA)
 *   "tipoPausa"              -> TipoPausa.name (solo INICIAR_PAUSA)
 *
 * Mensajes mostrados:
 *   ENTRADA         -> "Bienvenido/a, {nombre}" / "Entrada registrada a las {HH:mm}"
 *   SALIDA          -> "Hasta luego, {nombre}" / "Jornada: {Xh YYm}"
 *   INICIAR_PAUSA   -> "Pausa iniciada, {nombre}" / "{HH:mm} · {tipo de pausa}"
 *   FINALIZAR_PAUSA -> "Pausa finalizada, {nombre}" / "Duracion: {X} minutos"
 *
 * Tras 3 segundos navega a action_confirmacion_to_terminal (popUpTo P01 inclusive).
 */
class ConfirmacionFragment : Fragment() {

    private var _binding: FragmentConfirmacionBinding? = null
    private val binding get() = _binding!!

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
        mostrarMensaje()
        iniciarCuentaAtras()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Construccion del mensaje
    // ------------------------------------------------------------------

    private fun mostrarMensaje() {
        val args = requireArguments()
        val accion = AccionTerminal.valueOf(args.getString("accion", AccionTerminal.ENTRADA.name))
        val nombre = args.getString("nombre", "")

        val (principal, secundario) = when (accion) {
            AccionTerminal.ENTRADA -> {
                val hora = args.getString("horaEntrada", "")
                "Bienvenido/a, $nombre" to "Entrada registrada a las $hora"
            }
            AccionTerminal.SALIDA -> {
                val hora = args.getString("horaSalida", "")
                val minutos = args.getInt("jornadaEfectivaMinutos", 0)
                "Hasta luego, $nombre" to "Hora de salida: $hora · Jornada: ${formatearJornada(minutos)}"
            }
            AccionTerminal.INICIAR_PAUSA -> {
                val hora = args.getString("horaInicioPausa", "")
                val tipo = args.getString("tipoPausa")
                    ?.let { TipoPausa.valueOf(it) }
                    ?.let { tipoPausaNombre(it) }
                    ?: ""
                "Pausa iniciada, $nombre" to "$hora · $tipo"
            }
            AccionTerminal.FINALIZAR_PAUSA -> {
                val duracion = args.getInt("duracionPausaMinutos", 0)
                "Pausa finalizada, $nombre" to "Duracion: $duracion minutos"
            }
        }

        binding.tvMensajePrincipal.text = principal
        binding.tvMensajeSecundario.text = secundario
    }

    // ------------------------------------------------------------------
    // Cuenta atras de 3 segundos
    // ------------------------------------------------------------------

    private fun iniciarCuentaAtras() {
        viewLifecycleOwner.lifecycleScope.launch {
            for (i in 3 downTo 1) {
                binding.tvCuentaAtras.text = getString(R.string.confirmacion_volviendo, i)
                delay(1000)
            }
            findNavController().navigate(R.id.action_confirmacion_to_terminal)
        }
    }

    // ------------------------------------------------------------------
    // Helpers de formato
    // ------------------------------------------------------------------

    /** Convierte minutos totales a "Xh YYm". Ejemplo: 485 -> "8h 05m". */
    private fun formatearJornada(minutos: Int): String {
        val h = minutos / 60
        val m = minutos % 60
        return "${h}h ${m.toString().padStart(2, '0')}m"
    }

    private fun tipoPausaNombre(tipo: TipoPausa): String = when (tipo) {
        TipoPausa.COMIDA             -> "Comida"
        TipoPausa.DESCANSO           -> "Descanso"
        TipoPausa.AUSENCIA_RETRIBUIDA -> "Ausencia retribuida"
        TipoPausa.OTROS              -> "Otros"
    }
}
