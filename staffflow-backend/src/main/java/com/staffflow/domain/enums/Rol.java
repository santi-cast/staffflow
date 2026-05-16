package com.staffflow.domain.enums;

/**
 * Rol de acceso del usuario en el sistema StaffFlow.
 *
 * Determina qué endpoints puede invocar cada usuario y qué datos puede ver.
 * Spring Security lee este valor del JWT en cada petición y aplica las
 * restricciones de autorización correspondientes (HTTP 403 si el rol es
 * insuficiente para el endpoint solicitado).
 *
 * Separación de conceptos:
 * El Rol es distinto de la CategoriaEmpleado. El Rol define permisos en la app;
 * la CategoriaEmpleado describe el puesto laboral. Un encargado de equipo puede
 * tener rol EMPLEADO, y un técnico puede tener rol ENCARGADO. Son dimensiones
 * independientes.
 *
 * El reparto exacto de permisos por endpoint vive en {@code SecurityConfig}
 * (capa URL) y en las anotaciones {@code @PreAuthorize} de cada controller
 * (capa método). Cualquier divergencia entre este Javadoc y esos dos sitios
 * es un bug del Javadoc, no de la configuración.
 *
 * Referencia: RNF-S03, RF-03.
 */
public enum Rol {

    /**
     * Acceso total a la gestión del sistema. Es el único rol con acceso
     * a la configuración de empresa ({@literal /api/v1/empresa/**}, E06-E07),
     * a la gestión de usuarios ({@literal /api/v1/usuarios/**}, E08-E12) y
     * al recálculo forzado de saldos (E40, POST sobre
     * /api/v1/saldos/{empleadoId}/recalcular). Comparte con ENCARGADO
     * el resto de módulos operativos
     * (empleados, fichajes, pausas, ausencias, presencia, saldos, informes
     * y desbloqueo del terminal E53-E54).
     *
     * ADMIN no tiene perfil de empleado y, por tanto, no puede invocar
     * los endpoints {@code /me} ni fichar desde el terminal por PIN:
     * sus credenciales son de gestión, no de presencia.
     */
    ADMIN,

    /**
     * Rol operativo del día a día. Tiene los mismos permisos que ADMIN
     * sobre los módulos operativos (empleados, fichajes, pausas,
     * ausencias, presencia, saldos —sin recálculo—, informes y
     * desbloqueo del terminal), pero NO accede a la configuración de
     * empresa, a la gestión de usuarios ni al recálculo forzado de
     * saldos. La separación es matricial por módulo, no una jerarquía
     * estricta.
     *
     * SÍ tiene perfil de empleado: puede consultar sus propios datos
     * vía endpoints {@code /me} y fichar con PIN desde el terminal
     * (RF-46..RF-49).
     */
    ENCARGADO,

    /**
     * Acceso exclusivo a sus propios datos a través de los endpoints
     * {@code /me} (perfil E21, fichajes E26, pausas E55, ausencias E34
     * + informe E61, parte diario E37, saldo E41 e informes propios
     * E58). No tiene acceso a ningún endpoint de gestión: Spring
     * Security devuelve HTTP 403 ante cualquier intento de consultar
     * datos de otro empleado.
     *
     * SÍ tiene perfil de empleado: ficha con PIN desde el terminal
     * (RF-46..RF-49).
     */
    EMPLEADO
}
