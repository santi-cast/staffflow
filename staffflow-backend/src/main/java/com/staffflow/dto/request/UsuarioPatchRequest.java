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
 * El username y la contraseña no son modificables por este endpoint.
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

    // Baja lógica: activo=false desactiva el acceso sin borrar el registro
    // No confundir con DELETE físico, que no existe.
    private Boolean activo;
}