package com.staffflow.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Email del usuario que solicita recuperar su contraseña olvidada.
 * Usado en E04 (POST /api/v1/auth/password/recovery), endpoint público sin JWT.
 *
 * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
 * temporal de 8 caracteres alfanuméricos por email. El token UUID de 30
 * minutos descrito a continuación pertenece al andamiaje reservado para v2.0
 * (ver memoria TFG, bloque B10 Vías Futuras → Reset password con token UUID).</p>
 *
 * <p>Comportamiento real en v1: si el email existe en BD, el servidor genera
 * una contraseña temporal con {@code SecureRandom} sobre un alfabeto sin
 * caracteres ambiguos, sobrescribe el {@code passwordHash} del usuario y
 * envía la temporal en claro al email registrado en la entidad Usuario
 * (no al texto tipeado en la pantalla, que solo actúa como identificador
 * para localizar la cuenta). Por anti-enumeración (RNF-S04), si el email
 * no existe la respuesta es exactamente la misma HTTP 200 con mensaje
 * genérico que cuando existe, para no revelar qué emails están registrados.</p>
 *
 * <p>Flujo previsto en v2.0 (contexto, no operativo en v1): el servidor
 * generaría un token UUID de un solo uso con validez de 30 minutos y lo
 * enviaría al email registrado del usuario (RNF-S04). El usuario abriría
 * el enlace recibido y completaría el reseteo desde E05 sin pasar por la
 * contraseña temporal.</p>
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