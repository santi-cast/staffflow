package com.staffflow.dto.response;

import com.staffflow.domain.enums.Rol;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Representación de un usuario del sistema.
 * Usado en E08 (POST), E09 (GET lista), E10 (GET por id),
 * E11 (GET perfil propio) y E12 (PATCH /api/v1/usuarios).
 * Solo ADMIN accede a E08-E10 y E12 (decisión nº14).
 * E11 es accesible por cualquier rol autenticado (perfil propio).
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

    // Determina los permisos de acceso del usuario en la app (decisión nº14).
    private Rol rol;

    // Baja lógica: activo=false. Nunca DELETE físico (decisión nº4).
    private Boolean activo;

    private LocalDateTime fechaCreacion;

    // NUNCA incluir: passwordHash, resetToken, resetTokenExpiry.
    // Son datos sensibles que no se exponen al cliente bajo ningún
    // concepto (RNF-S01, RNF-S04).
}