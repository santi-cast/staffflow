package com.staffflow.dto.request;

import com.staffflow.domain.enums.CategoriaEmpleado;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Campos modificables de una ficha de empleado mediante actualización parcial.
 * Usado en E21 (PATCH /api/v1/empleados/{id}), solo accesible por ADMIN (decisión nº14).
 * Todos los campos son opcionales: el servicio solo actualiza los que
 * lleguen con valor no null (patrón PATCH, decisión nº3).
 * usuarioId, dni, numeroEmpleado y fechaAlta no son modificables por este endpoint:
 * son datos de identidad y contractuales que no deben cambiar.
 *
 * @author Santiago Castillo
 */
@Data
public class EmpleadoPatchRequest {

    @Size(max = 100)
    private String nombre;

    @Size(max = 100)
    private String apellido1;

    @Size(max = 100)
    private String apellido2;

    // Informativa: no determina permisos ni acceso (decisión nº19).
    private CategoriaEmpleado categoria;

    // Rango 0-40h (decisión nº22).
    @DecimalMin("0.0")
    @DecimalMax("40.0")
    private Double jornadaSemanalHoras;

    @Min(0)
    private Integer jornadaDiariaMinutos;

    @Min(0)
    private Integer diasVacacionesAnuales;

    @Min(0)
    private Integer diasAsuntosPropiosAnuales;

    // Alternativa al PIN para fichaje por NFC (RF-48). Opcional.
    @Size(max = 100)
    private String codigoNfc;

    // Baja lógica: activo=false desactiva al empleado sin borrar su historial
    // (decisión nº4). No confundir con DELETE físico, que no existe.
    private Boolean activo;
}
