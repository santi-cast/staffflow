package com.staffflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Credenciales de acceso enviadas por el cliente en el login.
 * Usado en E01 (POST /api/v1/auth/login), endpoint público sin JWT.
 * Si las credenciales son válidas el servidor devuelve {@link com.staffflow.dto.response.LoginResponse}
 * con el token JWT de 8 horas (decisión nº18).
 *
 * @author Santiago Castillo
 */
@Data
public class LoginRequest {

    // @NotBlank valida que no sea null, vacío ni solo espacios.
    @NotBlank
    private String username;

    @NotBlank
    private String password;
}