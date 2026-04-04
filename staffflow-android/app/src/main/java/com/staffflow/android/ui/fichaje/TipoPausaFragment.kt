package com.staffflow.android.ui.fichaje

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentTipoPausaBinding
import com.staffflow.android.domain.model.TipoPausa

/**
 * Seleccion de tipo de pausa (P07).
 *
 * Pantalla publica sin JWT. Se navega aqui desde P01 al pulsar "Iniciar pausa".
 * El usuario elige uno de los 4 tipos definidos en TipoPausa y la pantalla
 * vuelve automaticamente a P01 enviando el tipo via FragmentResult.
 *
 * Sin ViewModel propio: no hay red ni estado complejo.
 *
 * Flujo de vuelta:
 *   setFragmentResult("tipoPausa", bundleOf("tipo" to TipoPausa.X))
 *   navigate(action_tipo_pausa_to_terminal)  <- popUpTo P01 inclusive
 *
 * P01 recibe el resultado en setFragmentResultListener("tipoPausa")
 * registrado en onViewCreated de TerminalFragment.
 */
class TipoPausaFragment : Fragment() {

    private var _binding: FragmentTipoPausaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTipoPausaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnComida.setOnClickListener { seleccionar(TipoPausa.COMIDA) }
        binding.btnDescanso.setOnClickListener { seleccionar(TipoPausa.DESCANSO) }
        binding.btnAusenciaRetribuida.setOnClickListener { seleccionar(TipoPausa.AUSENCIA_RETRIBUIDA) }
        binding.btnOtros.setOnClickListener { seleccionar(TipoPausa.OTROS) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Envia el tipo de pausa seleccionado a P01 via FragmentResult y navega de vuelta.
     * TerminalFragment lo recibe en setFragmentResultListener("tipoPausa").
     */
    private fun seleccionar(tipo: TipoPausa) {
        setFragmentResult("tipoPausa", bundleOf("tipo" to tipo))
        findNavController().navigate(R.id.action_tipo_pausa_to_terminal)
    }
}
