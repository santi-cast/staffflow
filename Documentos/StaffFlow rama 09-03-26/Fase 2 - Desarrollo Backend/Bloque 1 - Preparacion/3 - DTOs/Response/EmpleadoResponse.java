package com.staffflow.dto.response;

import com.staffflow.domain.enums.CategoriaEmpleado;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Representación de la ficha laboral de un empleado.
 * Usado en E13 (POST), E14 (GET lista), E15 (GET por id),
 * E16 (GET perfil propio), E17-E20 (activar/desactivar/buscar)
 * y E21 (PATCH /api/v1/empleados).
 * Solo ADMIN accede a E13, E14, E21 (decisión nº14).
 * EMPLEADO solo puede consultar su propio perfil (E16, decisión nº10).
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmpleadoResponse {

    private Long id;

    // ID del usuario asociado. Se devuelve el ID, no el objeto completo,
    // para evitar referencias circulares y mantener los DTOs planos.
    private Long usuarioId;

    private String nombre;

    private String apellido1;

    // NULL si el empleado no tiene segundo apellido.
    private String apellido2;

    private String dni;

    private String nss;

    private LocalDate fechaAlta;

    // Informativa: no determina permisos de acceso (decisión nº19).
    private CategoriaEmpleado categoria;

    // Horas semanales contractuales (decisión nº22).
    private Integer jornadaSemanalHoras;

    // Minutos de jornada diaria esperada. Base para el cálculo de jornada efectiva.
    private Integer jornadaDiariaMinutos;

    private Integer diasVacacionesAnuales;

    private Integer diasAsuntosPropiosAnuales;

    // NULL si el empleado no tiene NFC configurado (RF-48).
    private String codigoNfc;

    // Baja lógica: activo=false. Nunca DELETE físico (decisión nº4).
    private Boolean activo;

    // NUNCA incluir: pinTerminal.
    // El PIN es un dato de seguridad del terminal que no se expone
    // al cliente bajo ningún concepto (decisión nº21).
}