package com.staffflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Token de recuperación y nueva contraseña para completar el reseteo.
 *
 * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
 * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
 * descrito a continuación pertenece al andamiaje reservado para v2.0
 * (ver memoria TFG, bloque B10 Vías Futuras → Reset password con token UUID).</p>
 *
 * <p>Usado en E05 (POST /api/v1/auth/password/reset), endpoint público sin JWT.
 * En v1, este DTO se acepta sintácticamente (Bean Validation pasa si
 * los campos cumplen las restricciones), pero el endpoint que lo consume
 * siempre devuelve HTTP 400: AuthService busca el token con
 * {@code findByResetToken} y nadie escribe ese campo en la base de datos,
 * por lo que la búsqueda siempre falla.</p>
 *
 * <p>Flujo previsto en v2.0 (contexto, no operativo en v1): el token se
 * recibiría por email tras invocar E04. Sería de un solo uso y se
 * invalidaría inmediatamente tras el reseteo exitoso (RNF-S04).</p>
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