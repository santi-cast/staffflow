package com.staffflow.dto.request;

import com.staffflow.domain.enums.CategoriaEmpleado;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Campos modificables de una ficha de empleado mediante actualización parcial.
 * Usado en E16 (PATCH /api/v1/empleados/{id}), accesible por ADMIN y ENCARGADO.
 * Todos los campos son opcionales: el servicio solo actualiza los que
 * lleguen con valor no null (patrón PATCH).
 * usuarioId y numeroEmpleado no son modificables por este endpoint:
 * usuarioId es el vínculo permanente con Usuario y numeroEmpleado se autogenera
 * como EMP-XXX al crear el empleado. PIN se regenera aparte vía E65.
 * dni y fechaAlta SÍ son modificables: el ADMIN puede corregir errores de
 * registro o cambios contractuales. La unicidad del DNI se valida en el
 * servicio. fechaAlta admite valores retroactivos para corregir altas mal
 * registradas; impacta a informes históricos (avisado al usuario en cliente).
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

    // Documento de identidad. Editable para corregir errores tipográficos
    // detectados después del alta. La unicidad se valida en el servicio
    // (409 Conflict si ya pertenece a otro empleado).
    @Size(max = 9)
    private String dni;

    // Fecha en la que el empleado empieza a contar laboralmente. Editable
    // para corregir errores de registro. Admite valores retroactivos (a
    // diferencia del alta, que solo permite fechas presentes o futuras).
    // El cliente avisa al usuario porque afecta a los informes históricos
    // y al cálculo de empleados operativos en fechas anteriores.
    private LocalDate fechaAlta;

    // Informativa: no determina permisos ni acceso.
    private CategoriaEmpleado categoria;

    // Rango 0-40h.
    @DecimalMin("0.0")
    @DecimalMax("40.0")
    private Double jornadaSemanalHoras;

    @Min(0)
    private Integer jornadaDiariaMinutos;

    @Min(0)
    private Integer diasVacacionesAnuales;

    @Min(0)
    private Integer diasAsuntosPropiosAnuales;

    // Campo de soporte para fichaje por NFC (feature futura). Se persiste
    // y se valida, pero ningún endpoint de fichaje lo consume en v1.
    @Size(max = 100)
    private String codigoNfc;

    // Baja lógica: activo=false desactiva al empleado sin borrar su historial
    // No confundir con DELETE físico, que no existe.
    private Boolean activo;
}
