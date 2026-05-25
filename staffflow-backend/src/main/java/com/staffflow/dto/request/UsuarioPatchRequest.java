package com.staffflow.dto.request;

import com.staffflow.domain.enums.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Campos modificables de un usuario existente mediante actualización parcial.
 * Usado en E11 (PATCH /api/v1/usuarios/{id}), solo accesible por ADMIN.
 * Todos los campos son opcionales: el servicio solo actualiza los que
 * lleguen con valor no null (patrón PATCH).
 *
 * Campos NO modificables por este endpoint:
 *   - username y password: inmutables vía E11. La contraseña se gestiona
 *     por E03 (cambio propio) y E66 (reset por ADMIN).
 *   - activo: la activación/desactivación se realiza exclusivamente por los
 *     endpoints dedicados E12 (DELETE, baja lógica) y E67 (PATCH /reactivar).
 *     Esta separación es intencional: evita que una edición de datos
 *     accidental modifique el estado del usuario.
 *
 * @author Santiago Castillo
 */
@Data
public class UsuarioPatchRequest {

    // @Email valida formato RFC 5322. Opcional en PATCH.
    @Email
    @Size(max = 150)
    private String email;

    // Cambio de rol: solo ADMIN puede modificarlo.
    private Rol rol;
}