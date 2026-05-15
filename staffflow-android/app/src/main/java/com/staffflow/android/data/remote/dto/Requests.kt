package com.staffflow.android.data.remote.dto

import com.staffflow.android.domain.model.CategoriaEmpleado
import com.staffflow.android.domain.model.Rol
import com.staffflow.android.domain.model.TipoAusencia
import com.staffflow.android.domain.model.TipoFichaje
import com.staffflow.android.domain.model.TipoPausa

/** Credenciales de acceso para el login (E01 POST /auth/login). */
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * Cambio de contrasena para el usuario autenticado (E03 PUT /auth/password).
 * passwordNueva debe tener minimo 8 caracteres (validacion en backend).
 */
data class PasswordChangeRequest(
    val passwordActual: String,
    val passwordNueva: String
)

/**
 * Solicitud de recuperacion de contrasena por email (E04 POST /auth/password/recovery).
 * El backend siempre responde 200 para evitar enumeracion de usuarios.
 */
data class PasswordRecoveryRequest(val email: String)

/**
 * Restablecimiento de contraseña con token recibido por email
 * (E05 POST /auth/password/reset).
 *
 * **v1.0 — no operativo:** en v1 este flujo entrega una contraseña temporal
 * de 8 caracteres por email (E04). El token UUID de 30 minutos descrito a
 * continuación pertenece al andamiaje reservado para v2.0 (ver memoria TFG,
 * bloque B10 Vías Futuras → Reset password con token UUID).
 *
 * En v1 este DTO se construye técnicamente bien, pero el endpoint que lo
 * consume devuelve siempre HTTP 400: la base de datos nunca contiene tokens
 * válidos porque ningún flujo de producción escribe `resetToken` en la
 * entidad `Usuario`.
 *
 * Comportamiento previsto (v2.0): error 400 si el token ha expirado o es
 * inválido.
 */
data class PasswordResetRequest(
    val token: String,
    val passwordNueva: String
)

/**
 * Creacion de un nuevo usuario del sistema (E08 POST /usuarios).
 * password se almacena como hash BCrypt en el backend.
 * Error 409 si username o email ya existen.
 */
data class UsuarioRequest(
    val username: String,
    val password: String,
    val email: String,
    val rol: Rol
)

/**
 * Actualizacion parcial de un usuario (E11 PATCH /usuarios/{id}).
 * Solo se envian los campos que se quieren modificar (null = sin cambio).
 */
data class UsuarioPatchRequest(
    val email: String? = null,
    val rol: Rol? = null,
    val activo: Boolean? = null
)

/**
 * Creacion de un empleado vinculado a un usuario existente (E13 POST /empleados).
 *
 * El backend auto-genera: numeroEmpleado (EMP-XXX), fechaAlta (hoy),
 * jornadaDiariaMinutos (jornadaSemanal/5*60) y pinTerminal (4 digitos unicos).
 * El PIN generado se devuelve en EmpleadoResponse para que el admin lo entregue al empleado.
 * Error 409 si DNI o codigoNfc ya existen.
 */
data class EmpleadoRequest(
    val usuarioId: Long,
    val nombre: String,
    val apellido1: String,
    val apellido2: String? = null,
    val dni: String,
    val categoria: CategoriaEmpleado,
    val jornadaSemanalHoras: Double,
    val diasVacacionesAnuales: Int,
    val diasAsuntosPropiosAnuales: Int,
    val codigoNfc: String? = null
)

/**
 * Actualizacion parcial de un empleado (E16 PATCH /empleados/{id}).
 * Solo se envian los campos que se quieren modificar (null = sin cambio).
 */
data class EmpleadoPatchRequest(
    val nombre: String? = null,
    val apellido1: String? = null,
    val apellido2: String? = null,
    val categoria: CategoriaEmpleado? = null,
    val jornadaSemanalHoras: Double? = null,
    val jornadaDiariaMinutos: Int? = null,
    val diasVacacionesAnuales: Int? = null,
    val diasAsuntosPropiosAnuales: Int? = null,
    val pinTerminal: String? = null,
    val codigoNfc: String? = null,
    val activo: Boolean? = null
)

/**
 * Actualizacion de la configuracion de empresa (E07 PUT /empresa).
 * Singleton: siempre modifica el registro con id=1.
 * Error 409 si el CIF ya existe en otro registro.
 */
data class EmpresaRequest(
    val nombreEmpresa: String,
    val cif: String,
    val direccion: String? = null,
    val email: String? = null,
    val telefono: String? = null,
    val logoPath: String? = null
)

/**
 * Creacion manual de un fichaje (E22 POST /fichajes).
 *
 * observaciones es obligatoria segun RNF-L02.
 * horaEntrada es null en ausencias planificadas.
 * Error 409 si ya existe fichaje para ese empleado en esa fecha.
 * Solo accesible para ADMIN y ENCARGADO. ENCARGADO solo puede
 * crear fichajes del dia actual (D-026).
 */
data class FichajeRequest(
    val empleadoId: Long,
    val fecha: String,
    val tipo: TipoFichaje,
    val horaEntrada: String? = null,
    val horaSalida: String? = null,
    val observaciones: String? = null
)

/**
 * Actualizacion parcial de un fichaje (E23 PATCH /fichajes/{id}).
 *
 * observaciones es OBLIGATORIA aunque sea PATCH (RNF-L02).
 * ENCARGADO solo puede modificar fichajes del dia actual (D-026).
 */
data class FichajePatchRequest(
    val tipo: TipoFichaje? = null,
    val horaEntrada: String? = null,
    val horaSalida: String? = null,
    val observaciones: String
)

/**
 * Creacion manual de una pausa dentro de una jornada (E27 POST /pausas).
 * horaFin null indica pausa activa en curso.
 * Error 409 si ya hay una pausa activa ese dia para ese empleado.
 */
data class PausaRequest(
    val empleadoId: Long,
    val fecha: String,
    val horaInicio: String,
    val horaFin: String? = null,
    val tipoPausa: TipoPausa,
    val observaciones: String? = null
)

/**
 * Actualizacion parcial de una pausa (E28 PATCH /pausas/{id}).
 * Solo se envian los campos que se quieren modificar (null = sin cambio).
 */
data class PausaPatchRequest(
    val horaInicio: String? = null,
    val horaFin: String? = null,
    val tipoPausa: TipoPausa? = null,
    val observaciones: String? = null
)

/**
 * Planificacion de una ausencia (E30 POST /ausencias).
 *
 * empleadoId null indica festivo global (afecta a todos los empleados).
 * Error 409 si ya existe una ausencia para ese empleado en esa fecha.
 * Las ausencias no procesadas (procesado=false) se pueden modificar y
 * eliminar. Las procesadas (procesado=true) no (409 en E31 y E32).
 */
data class AusenciaRequest(
    val empleadoId: Long? = null,
    val fecha: String,
    val tipoAusencia: TipoAusencia,
    val observaciones: String? = null
)

/**
 * Creacion de ausencias en rango (POST /ausencias/rango).
 * Crea un registro por cada dia entre fechaDesde y fechaHasta.
 * Error 409 si hay conflictos y sobrescribir=false.
 */
data class AusenciaRangoRequest(
    val empleadoId: Long?,
    val fechaDesde: String,
    val fechaHasta: String,
    val tipoAusencia: TipoAusencia,
    val observaciones: String? = null,
    val sobrescribir: Boolean = false
)

/**
 * Actualizacion parcial de una ausencia no procesada (E31 PATCH /ausencias/{id}).
 * Solo se envian los campos que se quieren modificar (null = sin cambio).
 */
data class AusenciaPatchRequest(
    val tipoAusencia: TipoAusencia? = null,
    val observaciones: String? = null
)

/**
 * Identificacion por PIN en el terminal de fichaje (E48, E49, E51).
 *
 * pin debe tener exactamente 4 digitos numericos.
 * dispositivoId es el ID unico del dispositivo Android (usado por el
 * backend para el mecanismo de bloqueo tras 5 intentos fallidos, HTTP 423).
 * No requiere JWT: el terminal es publico.
 */
data class TerminalPinRequest(
    val pin: String,
    val dispositivoId: String
)

/**
 * Inicio de pausa desde el terminal (E50 POST /terminal/pausa/iniciar).
 *
 * Extiende la identificacion por PIN con el tipo de pausa seleccionado
 * en P07 (TipoPausaFragment).
 * No requiere JWT.
 */
data class TerminalPausaRequest(
    val pin: String,
    val tipoPausa: TipoPausa,
    val dispositivoId: String
)
