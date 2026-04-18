package com.staffflow.domain.enums;

/**
 * Estado de la jornada de un empleado en el día actual, tal como se
 * muestra en la pantalla de bienvenida del terminal de fichaje (P06).
 *
 *   SIN_ENTRADA     → no hay fichaje de entrada registrado hoy
 *   EN_JORNADA      → hay entrada registrada, sin pausa activa, sin salida
 *   EN_PAUSA        → hay entrada registrada y una pausa actualmente activa
 *   JORNADA_CERRADA → hay entrada Y salida registradas (jornada terminada)
 */
public enum EstadoTerminal {
    SIN_ENTRADA,
    EN_JORNADA,
    EN_PAUSA,
    JORNADA_CERRADA
}
