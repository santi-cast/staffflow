package com.staffflow.dto.response;

import com.staffflow.domain.enums.Rol;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del servidor tras un login exitoso.
 * Usado en E01 (POST /api/v1/auth/login).
 *
 * El cliente Android almacena el token en DataStore y lo incluye
 * en la cabecera Authorization: Bearer {token} de todas las
 * peticiones autenticadas (decisión nº18).
 *
 * El rol determina el dashboard de destino tras el login:
 *   ADMIN     → P13
 *   ENCARGADO → P17
 *   EMPLEADO  → P09
 * (decisión nº23)
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    // JWT de 8 horas. Cubre una jornada laboral completa (decisión nº18).
    // Android lo almacena en DataStore y lo envía en Authorization: Bearer.
    private String token;

    // Determina el dashboard inicial y las opciones visibles en el Drawer.
    // Android infla el Drawer dinámicamente según este valor (decisión nº24).
    private Rol rol;

    // Mostrado en la cabecera del Drawer para identificar la sesión activa.
    private String username;

    // NULL si el usuario es ADMIN: no tiene ficha de empleado ni jornada
    // que registrar (decisión nº2 de arquitectura, separación usuario/empleado).
    // Con valor: Android lo usa para construir peticiones de datos propios
    // del empleado (fichajes, saldos, ausencias).
    private Long empleadoId;

    // Nombre para mostrar en la UI (nombre + apellido1 del empleado, o username si ADMIN).
    private String nombre;
}