package com.staffflow.dto.request;

import com.staffflow.domain.enums.CategoriaEmpleado;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Datos para crear la ficha de un nuevo empleado.
 * Usado en E13 (POST /api/v1/empleados), solo accesible por ADMIN.
 * El usuario asociado debe existir previamente (E08). La relación es 1:1:
 * un usuario solo puede tener una ficha de empleado (RF-07).
 *
 * Campos auto-generados por el backend (no presentes en este request):
 *   - numeroEmpleado: generado como EMP-XXX a partir del conteo total de empleados.
 *   - fechaAlta:      fecha actual del sistema (LocalDate.now()).
 *   - jornadaDiariaMinutos: calculado como (jornadaSemanalHoras / 5) * 60.
 *   - pinTerminal:    PIN de 4 dígitos único generado aleatoriamente.
 *                     Se devuelve en EmpleadoResponse solo en E13 y E15 para
 *                     que el ADMIN/ENCARGADO pueda entregárselo al empleado.
 *
 * @author Santiago Castillo
 */
@Data
public class EmpleadoRequest {

    // El usuario debe existir en la BD antes de crear el empleado (RF-07).
    @NotNull
    private Long usuarioId;

    @NotBlank
    @Size(max = 100)
    private String nombre;

    @NotBlank
    @Size(max = 100)
    private String apellido1;

    // Opcional: no todos los empleados tienen segundo apellido.
    @Size(max = 100)
    private String apellido2;

    @NotBlank
    @Size(max = 9)
    private String dni;

    // Informativa: no determina permisos ni acceso.
    @NotNull
    private CategoriaEmpleado categoria;

    // Horas semanales contractuales. Rango 0-40h.
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("40.0")
    private Double jornadaSemanalHoras;

    @NotNull
    @Min(0)
    private Integer diasVacacionesAnuales;

    @NotNull
    @Min(0)
    private Integer diasAsuntosPropiosAnuales;

    // Campo de soporte para fichaje por NFC (feature futura). Se persiste
    // y se valida, pero ningún endpoint de fichaje lo consume en v1.
    @Size(max = 100)
    private String codigoNfc;
}
