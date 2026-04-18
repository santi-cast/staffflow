package com.staffflow.android.ui.fichaje

/**
 * Accion a ejecutar en el terminal.
 *
 *   ENTRADA         -> E48 POST /terminal/entrada
 *   SALIDA          -> E49 POST /terminal/salida
 *   INICIAR_PAUSA   -> E50 POST /terminal/pausa/iniciar (requiere tipoPausa)
 *   FINALIZAR_PAUSA -> E51 POST /terminal/pausa/finalizar
 */
enum class AccionTerminal { ENTRADA, SALIDA, INICIAR_PAUSA, FINALIZAR_PAUSA }
