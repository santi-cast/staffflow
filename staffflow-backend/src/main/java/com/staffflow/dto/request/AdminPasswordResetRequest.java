package com.staffflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Datos necesarios para que un ADMIN restablezca la contraseña de otro usuario.
 * Usado en E14 (PATCH /api/v1/usuarios/{id}/password), requiere JWT con rol ADMIN.
 *
 * A diferencia de E03 (cambio de contraseña propia) no se exige la contraseña
 * actual: el ADMIN actúa como helpdesk y puede no conocerla. La nueva contraseña
 * se hashea con BCrypt y se persiste directamente sin enviar ningún correo.
 *
 * @author Santiago Castillo
 */
@Data
public class AdminPasswordResetRequest {

    // Mínimo 8 caracteres por política de seguridad (RNF-S01).
    @NotBlank
    @Size(min = 8)
    private String nuevaPassword;
}
