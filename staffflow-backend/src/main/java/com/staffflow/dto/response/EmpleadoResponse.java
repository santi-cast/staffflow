package com.staffflow.dto.response;

import com.staffflow.domain.enums.CategoriaEmpleado;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Representación de la ficha laboral de un empleado.
 *
 * Endpoints que devuelven este DTO (verificado en EmpleadoController):
 *   - E13 (POST  /api/v1/empleados)        → ADMIN, ENCARGADO
 *   - E14 (GET   /api/v1/empleados)        → ADMIN, ENCARGADO
 *   - E15 (GET   /api/v1/empleados/{id})   → ADMIN, ENCARGADO
 *   - E16 (PATCH /api/v1/empleados/{id})   → ADMIN, ENCARGADO
 *   - E21 (GET   /api/v1/empleados/me)     → EMPLEADO, ENCARGADO
 *
 * E17 (baja) y E18 (reactivar) devuelven {@link MensajeResponse}, NO este DTO.
 * E65 (regenerar PIN) devuelve {@link RegenerarPinResponse}.
 *
 * EMPLEADO solo accede al perfil propio vía E21; ningún otro endpoint
 * del grupo de empleados le está abierto.
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

    // Identificador interno de empleado. Renombrado desde nss (v1.0).
    // Se usa en cabeceras de PDFs firmables (RF-40).
    private String numeroEmpleado;

    private LocalDate fechaAlta;

    // Informativa: no determina permisos de acceso.
    private CategoriaEmpleado categoria;

    // Horas semanales contractuales. Double para soportar jornadas parciales.
    private Double jornadaSemanalHoras;

    // Minutos de jornada diaria esperada. Base para el cálculo de jornada efectiva.
    private Integer jornadaDiariaMinutos;

    private Integer diasVacacionesAnuales;

    private Integer diasAsuntosPropiosAnuales;

    // NULL si el empleado no tiene NFC configurado. Campo de soporte para
    // fichaje por NFC (feature futura): no lo consume ningún endpoint en v1.
    private String codigoNfc;

    // Baja lógica: activo=false. Nunca DELETE físico.
    private Boolean activo;

    // PIN del terminal. Se rellena con valor real SOLO en:
    //   - E13 (crear): se devuelve UNA vez al ADMIN/ENCARGADO que crea el
    //     empleado para que entregue el PIN por canal humano seguro.
    //   - E15 (detalle por id) Y SOLO si el llamante tiene rol ADMIN
    //     (Opción A: ENCARGADO recibe pinTerminal = null).
    // Null en E14 (listado), E16 (patch), E21 (/me) y en E15 cuando el
    // llamante es ENCARGADO. E65 lo regenera y lo devuelve en
    // {@link RegenerarPinResponse}, no en este DTO.
    private String pinTerminal;

    // Email del usuario asociado. Solo se devuelve en E15 (detalle por id) para ADMIN.
    private String email;
}
