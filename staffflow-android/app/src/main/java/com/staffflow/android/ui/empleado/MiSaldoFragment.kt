package com.staffflow.android.ui.empleado

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentMiSaldoBinding
import com.staffflow.android.databinding.ItemSaldoFilaBinding
import com.staffflow.android.data.remote.dto.SaldoResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Mi saldo anual (P09). Destino inicial del rol EMPLEADO.
 *
 * Patron C - dato unico solo lectura. Endpoint: E41 GET /saldos/me?anio=
 *
 * Tres estados:
 *   Loading -> CircularProgressIndicator centrado
 *   Error   -> icono nube + mensaje + boton Reintentar
 *   Success -> 3 cards (vacaciones, asuntos propios, horas)
 *
 * Selector de año en la toolbar: item de menu que muestra el año actual.
 * Al pulsarlo abre un dialogo con los ultimos 3 años + año siguiente.
 * saldoHoras: verde si >= 0, rojo si < 0.
 */
class MiSaldoFragment : Fragment() {

    private var _binding: FragmentMiSaldoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MiSaldoViewModel by viewModels()

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiSaldoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarMenu()
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
        observarViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.reintentar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Menu selector de año en toolbar
    // ------------------------------------------------------------------

    private fun configurarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_mi_saldo, menu)
                val chip = menu.findItem(R.id.action_anio)
                    ?.actionView?.findViewById<Chip>(R.id.chipAnio)
                chip?.text = "${viewModel.anio.value} ▾"
                chip?.setOnClickListener { mostrarSelectorAnio() }
            }
            override fun onMenuItemSelected(item: MenuItem) = false
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun mostrarSelectorAnio() {
        val anioActual = Calendar.getInstance().get(Calendar.YEAR)
        val anios = ((anioActual - 2)..(anioActual + 1)).map { it.toString() }.toTypedArray()
        val seleccionado = anios.indexOf(viewModel.anio.value.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.saldo_selector_anio_titulo)
            .setSingleChoiceItems(anios, seleccionado) { dialogo, indice ->
                val anioSeleccionado = anios[indice].toInt()
                viewModel.setAnio(anioSeleccionado)
                requireActivity().invalidateOptionsMenu()
                dialogo.dismiss()
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

    private fun procesarEstado(estado: MiSaldoViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is MiSaldoViewModel.UiState.Loading
        binding.layoutError.isVisible = estado is MiSaldoViewModel.UiState.Error
                                     || estado is MiSaldoViewModel.UiState.Empty
        binding.scrollContenido.isVisible = estado is MiSaldoViewModel.UiState.Success

        when (estado) {
            is MiSaldoViewModel.UiState.Error -> {
                binding.tvErrorMensaje.text = estado.mensaje
                binding.btnReintentar.isVisible = true
            }
            is MiSaldoViewModel.UiState.Empty -> {
                binding.tvErrorMensaje.text = "No hay datos de saldo para el año ${estado.anio}."
                binding.btnReintentar.isVisible = false
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(2500)
                    val anioActual = Calendar.getInstance().get(Calendar.YEAR)
                    viewModel.setAnio(anioActual)
                    requireActivity().invalidateOptionsMenu()
                }
            }
            is MiSaldoViewModel.UiState.Success -> {
                mostrarSaldo(estado.saldo)
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Relleno de datos
    // ------------------------------------------------------------------

    private fun mostrarSaldo(saldo: SaldoResponse) {
        val anio = saldo.anio
        binding.tvTituloVacaciones.text = "Vacaciones · $anio"
        binding.tvTituloAsuntos.text    = "Asuntos propios · $anio"
        binding.tvTituloHoras.text      = "Horas · $anio"

        val vac = saldo.vacaciones
        setFila(binding.filaVacacionesAnterior,   "Disponibles año anterior",    "${vac.pendientesAnterior} días")
        setFila(binding.filaVacacionesAnual,      "Derecho anual",               "${vac.derechoAnio} días")
        setFila(binding.filaVacacionesAnoCurso,   "Derecho total año en curso",  "${vac.pendientesAnterior + vac.derechoAnio} días")
        setFila(binding.filaVacacionesConsumidos, "Consumidos año en curso",     "${vac.consumidos} días")
        setFila(binding.filaVacacionesDisponibles,"Disponibles año en curso",    "${vac.disponibles} días")
        setFila(binding.filaVacacionesPendientesPlanificar, "Pendientes por planificar", "${vac.pendientesPlanificar} días")

        val ap = saldo.asuntosPropios
        setFila(binding.filaAsuntosAnterior,   "Disponibles año anterior",      "${ap.pendientesAnterior} días")
        setFila(binding.filaAsuntosAnual,      "Derecho anual",                 "${ap.derechoAnio} días")
        setFila(binding.filaAsuntosAnoCurso,   "Derecho total año en curso",    "${ap.pendientesAnterior + ap.derechoAnio} días")
        setFila(binding.filaAsuntosConsumidos, "Consumidos año en curso",       "${ap.consumidos} días")
        setFila(binding.filaAsuntosDisponibles,"Disponibles año en curso",       "${ap.disponibles} días")
        setFila(binding.filaAsuntosPendientesPlanificar, "Pendientes por planificar", "${ap.pendientesPlanificar} días")

        val h = saldo.horas
        setFila(binding.filaHorasEsperadas,  getString(R.string.saldo_esperadas),  formatHoras(h.esperadas))
        setFila(binding.filaHorasTrabajadas, getString(R.string.saldo_trabajadas), formatHoras(h.trabajadas))

        val saldoTexto = formatSaldoHoras(h.saldoHoras)
        val saldoColor = if (h.saldoHoras >= 0)
            MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorTertiary, 0)
        else
            MaterialColors.getColor(requireView(), androidx.appcompat.R.attr.colorError, 0)
        setFila(binding.filaSaldoHoras, getString(R.string.saldo_saldo_horas), saldoTexto)
        binding.filaSaldoHoras.tvValor.setTextColor(saldoColor)
    }

    private fun setFila(fila: ItemSaldoFilaBinding, label: String, valor: String) {
        fila.tvLabel.text = label
        fila.tvValor.text = valor
    }

    // ------------------------------------------------------------------
    // Helpers de formato
    // ------------------------------------------------------------------

    private fun formatHoras(horas: Double): String = String.format("%.1f h", horas)

    private fun formatSaldoHoras(horas: Double): String {
        val signo = if (horas >= 0) "+" else ""
        return "$signo${String.format("%.2f", horas)} h"
    }
}
