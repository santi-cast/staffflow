package com.staffflow.android.ui.fichaje

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.staffflow.android.R

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
 *   Al 4o digito -> llamada al endpoint segun boton activo.
 *   OK    -> navegar a ConfirmacionFragment (P06), volver tras 3s.
 *   Error -> mensaje de error en pantalla (PIN incorrecto, ya fichado, etc.)
 *   HTTP 423 -> "Dispositivo bloqueado" (5 intentos fallidos).
 *
 * Implementacion completa: Bloque 2, Prioridad 1.
 */
class TerminalFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_terminal, container, false)
}
