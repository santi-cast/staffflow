package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta genérica para operaciones que no devuelven datos,
 * solo confirmación de que la operación se ejecutó correctamente.
 *
 * Se usa en endpoints que devuelven HTTP 200 u HTTP 201 sin cuerpo
 * de datos relevante: altas, bajas lógicas, cambios de estado,
 * envío de email de recuperación, etc.
 *
 * Ejemplos de uso:
 *   E04 (POST /api/v1/auth/password/recovery) → "Email de recuperación enviado"
 *   E07 (PUT /api/v1/empresa) → "Configuración actualizada correctamente"
 *   E12 (PATCH /api/v1/usuarios/{id}) → "Usuario actualizado correctamente"
 *
 * Alternativa descartada: devolver HTTP 204 sin cuerpo. Se prefiere
 * MensajeResponse porque facilita el debugging en Swagger UI y permite
 * al cliente Android mostrar un snackbar con el texto de confirmación
 * sin hardcodear el mensaje en la app.
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MensajeResponse {

    // Texto descriptivo de la operación completada.
    // El cliente Android puede mostrarlo directamente en un snackbar.
    private String mensaje;
}
