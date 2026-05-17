package com.staffflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Datos necesarios para que un usuario autenticado cambie su contraseña.
 * Usado en E03 (PUT /api/v1/auth/password), requiere JWT válido.
 * Se exige la contraseña actual para evitar cambios no autorizados
 * en sesiones robadas (RNF-S01).
 *
 * @author Santiago Castillo
 */
@Data
public class PasswordChangeRequest {

    // Contraseña actual: se verifica contra el hash almacenado antes
    // de permitir el cambio (RNF-S01).
    @NotBlank
    private String passwordActual;

    // Mínimo 8 caracteres por política de seguridad (RNF-S01).
    @NotBlank
    @Size(min = 8)
    private String passwordNueva;
}