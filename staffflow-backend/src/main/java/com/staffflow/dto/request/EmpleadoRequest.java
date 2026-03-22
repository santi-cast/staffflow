package com.staffflow.dto.request;

import com.staffflow.domain.enums.CategoriaEmpleado;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Datos para crear la ficha de un nuevo empleado.
 * Usado en E13 (POST /api/v1/empleados), solo accesible por ADMIN (decisión nº14).
 * El usuario asociado debe existir previamente (E08). La relación es 1:1:
 * un usuario solo puede tener una ficha de empleado (RF-07).
 * pinTerminal es obligatorio: sin él el empleado no puede fichar
 * desde el terminal compartido (decisión nº21).
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

    // Identificador interno de empleado. Renombrado desde nss (v1.0).
    // Se usa en cabeceras de PDFs firmables (RF-40).
    @NotBlank
    @Size(max = 20)
    private String numeroEmpleado;

    @NotNull
    private LocalDate fechaAlta;

    // Informativa: no determina permisos ni acceso (decisión nº19).
    @NotNull
    private CategoriaEmpleado categoria;

    // Horas semanales contractuales. Rango 0-40h (decisión nº22).
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("40.0")
    private Double jornadaSemanalHoras;

    // Minutos de jornada diaria esperada. Base para el cálculo de jornada efectiva.
    @NotNull
    @Min(0)
    private Integer jornadaDiariaMinutos;

    @NotNull
    @Min(0)
    private Integer diasVacacionesAnuales;

    @NotNull
    @Min(0)
    private Integer diasAsuntosPropiosAnuales;

    // Exactamente 4 dígitos numéricos. Único por empleado (decisión nº21).
    // No se devuelve en ningún DTO response por seguridad.
    @NotBlank
    @Size(min = 4, max = 4)
    @Pattern(regexp = "\\d{4}")
    private String pinTerminal;

    // Alternativa al PIN para fichaje por NFC (RF-48). Opcional.
    @Size(max = 100)
    private String codigoNfc;
}
