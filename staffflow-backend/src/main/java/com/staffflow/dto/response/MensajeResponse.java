package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta genérica para operaciones que no devuelven datos,
 * solo confirmación de que la operación se ejecutó correctamente.
 *
 * Se usa en endpoints que devuelven HTTP 200 sin cuerpo de datos relevante:
 * bajas lógicas, reactivaciones, cambios de contraseña, envío de email
 * de recuperación, etc. Las altas (POST de creación) NO usan este DTO:
 * devuelven la entidad recién creada.
 *
 * Endpoints que devuelven MensajeResponse:
 *   E03 (PUT  /api/v1/auth/password)           → "Contraseña actualizada correctamente"
 *   E04 (POST /api/v1/auth/password/recovery)  → "Si el email está registrado recibirás tu contraseña temporal"
 *   E05 (POST /api/v1/auth/password/reset)     → andamiaje v2.0; en v1 siempre HTTP 400
 *   E12 (DELETE /api/v1/usuarios/{id})         → "Usuario desactivado correctamente"
 *   E17 (PATCH  /api/v1/empleados/{id}/baja)       → "Empleado desactivado correctamente"
 *   E18 (PATCH  /api/v1/empleados/{id}/reactivar)  → "Empleado reactivado correctamente"
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
