package com.staffflow.domain.enums;

/**
 * Categoría laboral del empleado.
 *
 * Propósito: clasificación informativa del puesto. No afecta permisos de acceso
 * (que dependen del campo Rol en la tabla usuarios). Útil para filtros,
 * exportaciones y agrupaciones en informes. Extensible en versiones futuras.
 *
 * Decisión de diseño nº19: CategoriaEmpleado es informativo, no operativo.
 * La alternativa —usar la categoría para determinar permisos— se descartó
 * porque duplicaría la lógica de autorización ya resuelta por Spring Security
 * con Rol. Dos conceptos distintos: qué hace el empleado (categoría)
 * vs qué puede hacer en la app (rol).
 */
public enum CategoriaEmpleado {

    /** Empleado de producción u operaciones. */
    OPERARIO,

    /** Empleado de gestión, administración o soporte administrativo. */
    ADMINISTRATIVO,

    /** Empleado técnico o especialista. */
    TECNICO,

    /** Empleado con responsabilidad de supervisión de equipo.
     *  Nota: este valor es la categoría laboral, independiente del rol ENCARGADO
     *  en la tabla usuarios. Un empleado puede tener categoría ENCARGADO
     *  pero rol EMPLEADO en la app, o viceversa. */
    ENCARGADO,

    /** Categoría no clasificada en las anteriores. */
    OTRO
}
