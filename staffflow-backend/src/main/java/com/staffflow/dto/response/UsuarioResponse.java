package com.staffflow.dto.response;

import com.staffflow.domain.enums.Rol;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Representación de un usuario del sistema.
 * Usado en E08 (POST crear), E09 (GET lista), E10 (GET por id),
 * E11 (PATCH modificar) y E12 (DELETE lógico desactivar).
 * Solo ADMIN accede a E08-E12.
 * E02 (GET /auth/me) devuelve también UsuarioResponse para cualquier rol autenticado.
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {

    private Long id;

    private String username;

    private String email;

    // Determina los permisos de acceso del usuario en la app.
    private Rol rol;

    // Baja lógica: activo=false. Nunca DELETE físico.
    private Boolean activo;

    private LocalDateTime fechaCreacion;

    // NUNCA incluir: passwordHash, resetToken, resetTokenExpiry.
    // Son datos sensibles que no se exponen al cliente bajo ningún
    // concepto (RNF-S01, RNF-S04).
}