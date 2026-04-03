package com.staffflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Token de recuperación y nueva contraseña para completar el reseteo.
 * Usado en E05 (POST /api/v1/auth/password/reset), endpoint público sin JWT.
 * El token se recibe por email tras invocar E04. Es de un solo uso:
 * se invalida inmediatamente tras el reseteo exitoso (RF-45, RNF-S04).
 *
 * @author Santiago Castillo
 */
@Data
public class PasswordResetRequest {

    // Token opaco generado en E04. Se valida contra reset_token
    // y reset_token_expiry en la entidad Usuario (RNF-S04).
    @NotBlank
    private String token;

    // Mínimo 8 caracteres por política de seguridad (RNF-S01).
    @NotBlank
    @Size(min = 8)
    private String passwordNueva;
}