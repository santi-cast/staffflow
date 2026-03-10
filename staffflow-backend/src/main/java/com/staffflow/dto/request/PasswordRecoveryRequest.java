package com.staffflow.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Email del usuario que solicita recuperar su contraseña olvidada.
 * Usado en E04 (POST /api/v1/auth/password/recovery), endpoint público sin JWT.
 * El servidor genera un token opaco de un solo uso con validez de 30 minutos
 * y lo envía al email indicado (RF-44, RNF-S04).
 * Si el email no existe en el sistema la respuesta es igualmente HTTP 200
 * para no revelar qué emails están registrados (RNF-S04).
 *
 * @author Santiago Castillo
 */
@Data
public class PasswordRecoveryRequest {

    // @Email valida formato RFC 5322. @NotBlank descarta null y vacíos.
    @NotBlank
    @Email
    private String email;
}