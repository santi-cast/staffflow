package com.staffflow.android.data.remote.dto

import com.staffflow.android.domain.model.CategoriaEmpleado
import com.staffflow.android.domain.model.EstadoPresencia
import com.staffflow.android.domain.model.Rol
import com.staffflow.android.domain.model.TipoAusencia
import com.staffflow.android.domain.model.TipoFichaje
import com.staffflow.android.domain.model.TipoPausa

/**
 * Respuesta del login (E01 POST /auth/login).
 *
 * El token JWT se almacena en Preferences DataStore y se incluye en
 * el header Authorization de todas las llamadas autenticadas.
 * empleadoId es null cuando el rol es ADMIN (no tiene perfil empleado).
 */
data class LoginResponse(
    val token: String,
    val rol: Rol,
    val username: String,
    val empleadoId: Long?,
    val nombre: String?
)

/**
 * Datos de un usuario del sistema (E02, E09, E10).
 *
 * fechaCreacion llega como String ISO-8601 desde el backend.
 * No incluye password ni datos sensibles.
 */
data class UsuarioResponse(
    val id: Long,
    val username: String,
    val email: String,
    val rol: Rol,
    val activo: Boolean,
    val fechaCreacion: String
)

/**
 * Datos de un empleado (E14, E15, E21).
 *
 * numeroEmpleado tiene formato EMP-001 (renombrado desde nss en D-030).
 * apellido2 y codigoNfc son opcionales (pueden ser null).
 *
 * pinTerminal y email son nullables y dependen del endpoint y del rol (D-017 Opción A):
 *   - E13 POST /empleados              -> pinTerminal con valor (creación), email segun lo enviado
 *   - E15 GET /empleados/{id} ADMIN    -> pinTerminal con valor, email con valor
 *   - E15 GET /empleados/{id} ENCARGADO-> pinTerminal = null, email = null
 *   - E14 GET /empleados (lista)       -> pinTerminal = null, email = null
 *   - E21 GET /empleados/me            -> pinTerminal = null, email del usuario autenticado
 */
data class EmpleadoResponse(
    val id: Long,
    val usuarioId: Long,
    val nombre: String,
    val apellido1: String,
    val apellido2: String?,
    val dni: String,
    val numeroEmpleado: String,
    val fechaAlta: String,
    val categoria: CategoriaEmpleado,
    val jornadaSemanalHoras: Int,
    val jornadaDiariaMinutos: Int,
    val diasVacacionesAnuales: Int,
    val diasAsuntosPropiosAnuales: Int,
    val codigoNfc: String?,
    val activo: Boolean,
    val pinTerminal: String? = null,  // E13 (crear) y E15 (detalle por id) si rol = ADMIN
    val email: String? = null         // E15 (detalle por id) si rol = ADMIN, o E21 (perfil propio)
)

/**
 * Configuracion de la empresa (E06 GET /empresa).
 *
 * Singleton: siempre id=1. Los campos opcionales pueden ser null
 * si el admin no los ha configurado todavia.
 */
data class EmpresaResponse(
    val id: Long,
    val nombreEmpresa: String,
    val cif: String,
    val direccion: String?,
    val email: String?,
    val telefono: String?,
    val logoPath: String?
)

/**
 * Registro de jornada laboral de un empleado (E22-E26).
 *
 * horaEntrada es null en ausencias planificadas generadas por el sistema.
 * horaSalida es null mientras la jornada esta en curso (fichaje abierto).
 * jornadaEfectivaMinutos = (horaSalida - horaEntrada) - totalPausasMinutos.
 */
data class FichajeResponse(
    val id: Long,
    val empleadoId: Long,
    val fecha: String,
    val tipo: TipoFichaje,
    val horaEntrada: String?,
    val horaSalida: String?,
    val totalPausasMinutos: Int,
    val jornadaEfectivaMinutos: Int,
    val usuarioId: Long,
    val observaciones: String?,
    val fechaCreacion: String,
    val nombreCompleto: String? = null
)

/**
 * Registro de una pausa dentro de una jornada (E27-E29).
 *
 * horaFin es null mientras la pausa esta activa.
 * duracionMinutos es null hasta que se cierra la pausa (E28).
 */
data class PausaResponse(
    val id: Long,
    val empleadoId: Long,
    val fecha: String,
    val horaInicio: String,
    val horaFin: String?,
    val duracionMinutos: Int?,
    val tipoPausa: TipoPausa,
    val usuarioId: Long,
    val observaciones: String?,
    val fechaCreacion: String
)

/**
 * Ausencia planificada de un empleado (E30-E34).
 *
 * empleadoId es null cuando es un festivo global (afecta a todos).
 * procesado=true indica que el cierre diario ya genero el fichaje
 * correspondiente. Una ausencia procesada no se puede modificar ni
 * eliminar (E31, E32 devuelven 409).
 */
data class AusenciaResponse(
    val id: Long,
    val empleadoId: Long?,
    val fecha: String,
    val tipoAusencia: TipoAusencia,
    val procesado: Boolean,
    val usuarioId: Long,
    val observaciones: String?,
    val fechaCreacion: String
)

/**
 * Saldo anual de vacaciones, asuntos propios y horas de un empleado
 * (E38, E39, E41).
 *
 * horas.saldoHoras: positivo = horas extra | negativo = deficit de horas.
 * Se muestra en verde/rojo segun signo en P09 y P25.
 * calculadoHastaFecha indica hasta que fecha se han computado los datos.
 */
data class SaldoResponse(
    val empleadoId: Long,
    val nombreCompleto: String,
    val anio: Int,
    val vacaciones: VacacionesDesglose,
    val asuntosPropios: AsuntosPropiosDesglose,
    val horas: HorasDesglose,
    val calculadoHastaFecha: String?
) {
    data class VacacionesDesglose(
        val derechoAnio: Int,
        val pendientesAnterior: Int,
        val consumidos: Int,
        val disponibles: Int,
        val pendientesPlanificar: Int
    )
    data class AsuntosPropiosDesglose(
        val derechoAnio: Int,
        val pendientesAnterior: Int,
        val consumidos: Int,
        val disponibles: Int,
        val pendientesPlanificar: Int
    )
    data class HorasDesglose(
        val esperadas: Double,
        val trabajadas: Double,
        val saldoHoras: Double,
        val diasTrabajados: Int,
        val diasBajaMedica: Int,
        val diasPermisoRetribuido: Int,
        val diasAusenciaInjustificada: Int
    )
}

/**
 * Parte diario de presencia de toda la empresa (E35 GET /presencia/parte-diario).
 *
 * Usado en P17 (ParteDiarioFragment). Los chips del resumen muestran
 * fichados, enPausa, ausencias y sinJustificar.
 * El chip sinJustificar navega a P18 (SinJustificarFragment).
 */
data class ParteDiarioResponse(
    val fecha: String,
    val totalEmpleados: Int,
    val trabajando: Int,
    val enPausa: Int,
    val ausencias: Int,
    val sinJustificar: Int,
    val jornadaCompletada: Int = 0,
    val detalle: List<DetallePresenciaResponse>
)

/**
 * Estado de presencia individual de un empleado en el parte diario.
 *
 * Tambien devuelto por E37 GET /presencia/parte-diario/me para el
 * empleado autenticado (P12 MiHoyFragment).
 * El color de la fila en P17 se determina segun el campo estado.
 * El campo pausas solo viene relleno en E37 (nunca en E35).
 */
data class DetallePresenciaResponse(
    val empleadoId: Long,
    val nombre: String,
    val apellido1: String,
    val apellido2: String?,
    val estado: EstadoPresencia,
    val horaEntrada: String?,
    val horaSalida: String?,
    val pausaActiva: Boolean,
    val fichajeTipo: TipoFichaje?,
    val pausas: List<PausaResumen>? = null,
    val fichajeId: Long? = null,
    val ausenciaId: Long? = null,
    val jornadaEfectivaMinutos: Int? = null
) {
    /**
     * Resumen de una pausa del dia (solo en E37).
     * horaFin y duracionMinutos son null si la pausa sigue activa.
     */
    data class PausaResumen(
        val id: Long? = null,
        val horaInicio: String?,
        val horaFin: String?,
        val tipoPausa: String?,
        val duracionMinutos: Int?
    )
}

/**
 * Empleado sin justificar del dia (E36 GET /presencia/sin-justificar).
 * Listado en P18 (SinJustificarFragment).
 */
data class SinJustificarResponse(
    val empleadoId: Long,
    val nombre: String,
    val apellido1: String,
    val apellido2: String?
)

/** Respuesta generica de confirmacion para operaciones sin datos de retorno. */
data class MensajeResponse(val mensaje: String)

/**
 * Respuesta de error estandar del backend.
 *
 * Todos los errores (4xx, 5xx) devuelven esta estructura.
 * El campo mensaje es el texto legible para mostrar al usuario.
 * Codigos relevantes: 400 datos invalidos | 401 no autenticado |
 * 403 rol insuficiente | 404 no encontrado | 409 conflicto | 423 bloqueado.
 */
data class ErrorResponse(
    val status: Int,
    val error: String,
    val mensaje: String,
    val timestamp: String
)

/**
 * Cuerpo del 409 de POST /ausencias/rango cuando hay conflictos y sobrescribir=false.
 */
data class RangoConflictResponse(
    val error: String?,
    val fechasConflictivas: List<String>?
)

/**
 * Respuesta de GET /ausencias/planificacion-vac-ap.
 *
 * Muestra cuántos días de vacaciones y asuntos propios quedan por planificar.
 * anioFuturoSinCierre=true indica que el saldo fue creado on-demand (sin cierre
 * anual previo) y los pendientes del año anterior no están incluidos aún.
 */
data class PlanificacionVacApResponse(
    val vacaciones: VacAp,
    val asuntosPropios: VacAp,
    val anioFuturoSinCierre: Boolean
) {
    data class VacAp(
        val disponibles: Int,
        val planificados: Int,
        val pendientesPlanificar: Int
    )
}

/**
 * Respuesta al consultar el estado del dia por PIN (E52 POST /terminal/estado).
 * Devuelta por P06 (ConfirmacionFragment) antes de seleccionar la accion.
 * Las horas vienen formateadas como "HH:mm" desde el backend.
 */
data class TerminalEstadoResponse(
    val nombre: String,
    val estado: String,         // EstadoTerminal.name(): SIN_ENTRADA | EN_JORNADA | EN_PAUSA | JORNADA_CERRADA
    val horaEntrada: String?,
    val horaSalida: String?,
    val horaInicioPausa: String?,
    val tipoPausa: String?      // TipoPausa.name() o null
)

/**
 * Respuesta al registrar la entrada desde el terminal (E48 POST /terminal/entrada).
 * Mostrada en P06 (ConfirmacionFragment) durante 5 segundos.
 */
data class TerminalEntradaResponse(
    val nombre: String,
    val horaEntrada: String,
    val mensaje: String
)

/**
 * Respuesta al registrar la salida desde el terminal (E49 POST /terminal/salida).
 * Mostrada en P06 (ConfirmacionFragment) durante 3 segundos.
 */
data class TerminalSalidaResponse(
    val nombre: String,
    val horaEntrada: String,
    val horaSalida: String,
    val totalPausasSegundos: Int,
    val numeroPausas: Int,
    val jornadaEfectivaSegundos: Int,
    val mensaje: String
)

/**
 * Respuesta al iniciar (E50) o finalizar (E51) una pausa desde el terminal.
 *
 * horaInicioPausa es null en E51 (finalizar pausa).
 * duracionPausaMinutos es null en E50 (iniciar pausa).
 * Mostrada en P06 (ConfirmacionFragment) durante 3 segundos.
 */
data class TerminalPausaResponse(
    val nombre: String,
    val horaInicioPausa: String?,
    val horaFinPausa: String?,
    val duracionPausaSegundos: Int?,
    val mensaje: String
)

/**
 * Respuesta de E53 GET /terminal/bloqueo y E54 DELETE /terminal/bloqueo.
 * Requiere JWT (ENCARGADO o ADMIN). Usado desde ParteDiarioFragment.
 */
data class TerminalBloqueoResponse(
    val bloqueado: Boolean
)

/**
 * Respuesta del endpoint E65 — POST /empleados/{id}/regenerar-pin.
 * Contiene el PIN nuevo en claro, devuelto una sola vez.
 */
data class RegenerarPinResponse(
    val empleadoId: Long,
    val pinTerminal: String,
)
