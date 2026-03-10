package com.staffflow.dto.request;

import com.staffflow.domain.enums.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Datos para crear un nuevo usuario del sistema.
 * Usado en E08 (POST /api/v1/usuarios), solo accesible por ADMIN (decisión nº14).
 * La contraseña se recibe en claro y se hashea con BCrypt en el servicio
 * antes de persistir. Nunca se almacena ni se devuelve en claro (RNF-S01).
 *
 * @author Santiago Castillo
 */
@Data
public class UsuarioRequest {

    @NotBlank
    @Size(max = 50)
    private String username;

    // Se recibe en claro. El servicio aplica BCrypt antes de persistir (RNF-S01).
    @NotBlank
    @Size(min = 8)
    private String password;

    // @Email valida formato RFC 5322.
    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    // @NotNull permite enviar cualquier valor del enum Rol pero no null.
    // El rol determina los permisos de acceso (decisión nº14).
    @NotNull
    private Rol rol;
}