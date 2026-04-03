package com.staffflow.android.domain.model

/**
 * Categoria profesional del empleado.
 *
 * Se muestra en P08 (mi perfil), P14 (detalle empleado) y se usa
 * como filtro en P13 (lista empleados, E14).
 * Se asigna al crear o editar un empleado (E13, E16).
 */
enum class CategoriaEmpleado {
    OPERARIO, ADMINISTRATIVO, TECNICO, ENCARGADO, OTRO
}
