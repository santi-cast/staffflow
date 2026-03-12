package com.staffflow.controller;

import com.staffflow.dto.request.LoginRequest;
import com.staffflow.dto.response.LoginResponse;
import com.staffflow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de autenticacion y gestion de contrasenas de StaffFlow.
 *
 * <p>Gestiona los endpoints E01-E05 del grupo Autenticacion (Endpoints_v3):
 * <ul>
 *   <li>E01 POST /api/v1/auth/login — PUBLICO, implementado en Bloque 2.</li>
 *   <li>E02 POST /api/v1/auth/logout — TODOS, pendiente Bloque 2.</li>
 *   <li>E03 PUT  /api/v1/auth/password — TODOS, pendiente Bloque 2.</li>
 *   <li>E04 POST /api/v1/auth/password/recovery — PUBLICO, pendiente Bloque D.</li>
 *   <li>E05 POST /api/v1/auth/password/reset — PUBLICO, pendiente Bloque D.</li>
 * </ul>
 *
 * <p>Regla de arquitectura: este controller no contiene logica de negocio.
 * Recibe la peticion, valida el body con {@code @Valid} y delega en {@link AuthService}.
 *
 * @author Santiago Castillo
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticacion", description = "Login, logout y gestion de contrasenas")
public class AuthController {

    // Servicio de autenticacion que contiene toda la logica de negocio
    private final AuthService authService;

    /**
     * E01 — Autentica al usuario y devuelve un token JWT valido 8 horas.
     *
     * <p>Endpoint publico: no requiere JWT. Es el primer endpoint que debe
     * llamarse; sin un token valido el resto de endpoints protegidos devuelven 401.
     *
     * <p>El token devuelto debe incluirse en la cabecera de todas las peticiones
     * protegidas: {@code Authorization: Bearer <token>}.
     *
     * @param request body con username y password del usuario
     * @return 200 OK con token JWT, rol, empleadoId y username
     */
    @Operation(
        summary = "Login de usuario",
        description = "Autentica con username y password. Devuelve JWT valido 8 horas. " +
                      "El token debe enviarse en Authorization: Bearer <token> en el resto de peticiones."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos",
            content = @Content),
        @ApiResponse(responseCode = "401", description = "Credenciales incorrectas",
            content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // Delegar completamente en AuthService: este controller no toma decisiones de negocio
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // ENDPOINTS PENDIENTES — esqueleto para compilacion
    // =========================================================================

    /**
     * E02 — Cierra la sesion del usuario autenticado.
     * JWT stateless: la invalidacion es en cliente (DataStore Android).
     * Pendiente de implementacion en Bloque 2.
     */
    @Operation(summary = "Logout", description = "Pendiente de implementacion en Bloque 2")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // TODO Bloque 2
        return ResponseEntity.ok().build();
    }

    /**
     * E03 — Cambia la contrasena del usuario autenticado.
     * Requiere la contrasena actual como verificacion.
     * Pendiente de implementacion en Bloque 2.
     */
    @Operation(summary = "Cambiar contrasena", description = "Pendiente de implementacion en Bloque 2")
    @org.springframework.web.bind.annotation.PutMapping("/password")
    public ResponseEntity<Void> cambiarPassword() {
        // TODO Bloque 2
        return ResponseEntity.ok().build();
    }

    /**
     * E04 — Solicita el token de recuperacion de contrasena por email.
     * Devuelve siempre 200 OK para evitar enumeracion de usuarios.
     * Pendiente de implementacion en Bloque D (trabajo futuro).
     */
    @Operation(summary = "Solicitar recuperacion de contrasena",
               description = "Pendiente de implementacion en Bloque D")
    @PostMapping("/password/recovery")
    public ResponseEntity<Void> solicitarRecuperacion() {
        // TODO Bloque D
        return ResponseEntity.ok().build();
    }

    /**
     * E05 — Restablece la contrasena usando el token recibido por email.
     * Token de un solo uso, caduca en 30 minutos (RNF-S04).
     * Pendiente de implementacion en Bloque D (trabajo futuro).
     */
    @Operation(summary = "Restablecer contrasena con token",
               description = "Pendiente de implementacion en Bloque D")
    @PostMapping("/password/reset")
    public ResponseEntity<Void> restablecerPassword() {
        // TODO Bloque D
        return ResponseEntity.ok().build();
    }
}
