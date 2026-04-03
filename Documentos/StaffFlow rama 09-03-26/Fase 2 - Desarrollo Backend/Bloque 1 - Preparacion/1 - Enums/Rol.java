package com.staffflow.domain.enums;

/**
 * Rol de acceso del usuario en el sistema StaffFlow.
 *
 * Determina qué endpoints puede invocar cada usuario y qué datos puede ver.
 * Spring Security lee este valor del JWT en cada petición y aplica las
 * restricciones de autorización correspondientes (HTTP 403 si el rol es
 * insuficiente para el endpoint solicitado).
 *
 * Separación de conceptos (decisión de diseño nº19):
 * El Rol es distinto de la CategoriaEmpleado. El Rol define permisos en la app;
 * la CategoriaEmpleado describe el puesto laboral. Un encargado de equipo puede
 * tener rol EMPLEADO, y un técnico puede tener rol ENCARGADO. Son dimensiones
 * independientes.
 *
 * Referencia: RNF-S03, RF-03, decisión de diseño nº10, nº14.
 */
public enum Rol {

    /**
     * Acceso total al sistema. Gestiona la configuración de empresa, usuarios
     * y toda la operativa (fichajes, ausencias, saldos, informes).
     * ADMIN no tiene perfil de empleado y nunca ficha desde el terminal
     * (decisión de diseño nº8): sus credenciales son de gestión, no de presencia.
     */
    ADMIN,

    /**
     * Gestión de la operativa diaria. Hereda todos los RF de ADMIN excepto
     * la gestión de empresa y usuarios (RF-01..RF-07).
     * SÍ tiene perfil de empleado: ficha con PIN desde el terminal (RF-46..RF-49).
     */
    ENCARGADO,

    /**
     * Acceso exclusivo a los propios datos. Solo puede consultar su perfil,
     * historial de fichajes, ausencias planificadas y saldo anual.
     * Spring Security devuelve HTTP 403 si intenta acceder a datos de otro
     * empleado (decisión de diseño nº10 y nº14).
     * SÍ tiene perfil de empleado: ficha con PIN desde el terminal (RF-46..RF-49).
     */
    EMPLEADO
}
