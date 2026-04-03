package com.staffflow.android.domain.model

/**
 * Tipo de pausa dentro de una jornada laboral.
 *
 * Se selecciona en P07 (TipoPausaFragment) antes de registrar la pausa
 * desde el terminal (E50) y en P20 (FormFichajeFragment) para pausas
 * manuales (E27).
 *
 * La duracion de la pausa se descuenta de la jornada efectiva al cerrarla (E28).
 */
enum class TipoPausa {
    COMIDA, DESCANSO, AUSENCIA_RETRIBUIDA, OTROS
}
