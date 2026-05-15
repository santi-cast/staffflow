package com.staffflow.controller;

import com.staffflow.dto.request.LoginRequest;
import com.staffflow.dto.request.PasswordChangeRequest;
import com.staffflow.dto.request.PasswordRecoveryRequest;
import com.staffflow.dto.request.PasswordResetRequest;
import com.staffflow.dto.response.LoginResponse;
import com.staffflow.dto.response.MensajeResponse;
import com.staffflow.dto.response.UsuarioResponse;
import com.staffflow.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para autenticación y gestión de credenciales.
 *
 * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
 * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
 * descrito a continuación pertenece al andamiaje reservado para v2.0
 * (ver memoria TFG, bloque B10 Vías Futuras → Reset password con token UUID).</p>
 *
 * <p>Cubre el grupo /api/v1/auth con cinco endpoints:</p>
 * <ul>
 *   <li>E01 POST /login         — login público, devuelve JWT</li>
 *   <li>E02 GET  /me            — datos del usuario autenticado</li>
 *   <li>E03 PUT  /password      — cambio de contraseña (requiere actual)</li>
 *   <li>E04 POST /password/recovery — envío de contraseña temporal por email</li>
 *   <li>E05 POST /password/reset    — andamiaje v2.0; en v1 siempre HTTP 400</li>
 * </ul>
 *
 * <p>E01, E04 y E05 son rutas públicas (declaradas en SecurityConfig y en
 * shouldNotFilter de JwtAuthFilter). E02 y E03 requieren JWT válido.</p>
 *
 * <p>Este controlador no accede directamente a repositorios ni a entidades JPA.
 * Toda la lógica está en AuthService (regla de oro de la arquitectura).</p>
 *
 * @author Santiago Castillo
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /** Servicio que contiene toda la lógica de autenticación. */
    private final AuthService authService;

    // =========================================================================
    // E01 — POST /api/v1/auth/login
    // =========================================================================

    /**
     * Autentica al usuario y devuelve un JWT válido.
     *
     * <p>Ruta pública: no requiere token.</p>
     *
     * <p>Spring valida el DTO con @Valid antes de llamar al servicio.
     * Si username o password están ausentes, Bean Validation devuelve 400
     * antes de llegar a AuthService.</p>
     *
     * @param request DTO con username y password
     * @return 200 OK con LoginResponse (token, rol, username, empleadoId)
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // =========================================================================
    // E02 — GET /api/v1/auth/me
    // =========================================================================

    /**
     * Devuelve los datos del usuario que realiza la petición.
     *
     * <p>Requiere JWT válido en el header Authorization: Bearer {token}.
     * El username se extrae del SecurityContext cargado por JwtAuthFilter,
     * no de la URL ni del body: el usuario solo puede ver sus propios datos.</p>
     *
     * <p>Roles: ADMIN, ENCARGADO, EMPLEADO (cualquier usuario autenticado).</p>
     *
     * @return 200 OK con UsuarioResponse (sin password_hash)
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UsuarioResponse> me() {
        return ResponseEntity.ok(authService.obtenerUsuarioAutenticado());
    }

    // =========================================================================
    // E03 — PUT /api/v1/auth/password
    // =========================================================================

    /**
     * Cambia la contraseña del usuario autenticado.
     *
     * <p>Requiere JWT válido. El usuario debe proporcionar su contraseña
     * actual para confirmar la identidad antes del cambio (doble verificación).
     * Si currentPassword no coincide, AuthService lanza IllegalArgumentException
     * que se convierte en 400 (GlobalExceptionHandler).</p>
     *
     * <p>Usa PUT porque el contrato establece que /auth/password es un
     * formulario completo que sustituye el valor existente.</p>
     *
     * <p>Roles: ADMIN, ENCARGADO, EMPLEADO.</p>
     *
     * @param request DTO con currentPassword y newPassword
     * @return 200 OK con MensajeResponse confirmando el cambio
     */
    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MensajeResponse> cambiarPassword(
            @Valid @RequestBody PasswordChangeRequest request) {
        return ResponseEntity.ok(authService.cambiarPassword(request));
    }

    // =========================================================================
    // E04 — POST /api/v1/auth/password/recovery
    // =========================================================================

    /**
     * Solicita la recuperación de contraseña por email.
     *
     * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
     * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
     * descrito a continuación pertenece al andamiaje reservado para v2.0
     * (ver memoria TFG, bloque B10 Vías Futuras → Reset password con token UUID).</p>
     *
     * <p>Ruta pública: no requiere token (el usuario no puede autenticarse
     * si ha olvidado su contraseña).</p>
     *
     * <p>Siempre devuelve 200 con el mismo mensaje, exista o no el email en
     * la BD. Esto impide la enumeración de usuarios registrados (RNF-S04).</p>
     *
     * <p>Comportamiento real en v1: si el email existe, AuthService genera
     * una contraseña temporal de 8 caracteres alfanuméricos, sobrescribe el
     * passwordHash del usuario y envía la contraseña en claro por correo
     * a través de EmailService (Gmail SMTP). Los campos resetToken y
     * resetTokenExpiry de la entidad Usuario no se tocan; quedan reservados
     * para el flujo de token UUID previsto en v2.0.</p>
     *
     * @param request DTO con el email del usuario
     * @return 200 OK con MensajeResponse (mensaje genérico)
     */
    @PostMapping("/password/recovery")
    public ResponseEntity<MensajeResponse> solicitarRecuperacion(
            @Valid @RequestBody PasswordRecoveryRequest request) {
        return ResponseEntity.ok(authService.solicitarRecuperacion(request));
    }

    // =========================================================================
    // E05 — POST /api/v1/auth/password/reset
    // =========================================================================

    /**
     * Restablece la contraseña usando el token de recuperación.
     *
     * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
     * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
     * descrito a continuación pertenece al andamiaje reservado para v2.0
     * (ver memoria TFG, bloque B10 Vías Futuras → Reset password con token UUID).</p>
     *
     * <p>Estado real en v1: el endpoint está expuesto y acepta el DTO, pero
     * siempre devuelve HTTP 400. La razón es que E04 nunca escribe
     * {@code resetToken} en la base de datos, por lo que la búsqueda
     * {@code findByResetToken} siempre devuelve {@code Optional.empty} y
     * AuthService lanza IllegalArgumentException. El flujo está operativo
     * solo nominalmente; queda reservado para activarse en v2.0.</p>
     *
     * <p>Descripción del flujo previsto en v2.0 (contexto, no operativo en v1):
     * el usuario llegaría aquí desde el enlace del email. Si el token no
     * existe, ya fue usado o ha caducado (más de 30 minutos desde E04),
     * AuthService lanzaría IllegalArgumentException que se convierte en 400.
     * El token se invalida tras el primer uso exitoso (RNF-S04).</p>
     *
     * @param request DTO con token y newPassword
     * @return 200 OK con MensajeResponse confirmando el restablecimiento
     *         (en v1 siempre 400 por el motivo arriba indicado)
     */
    @PostMapping("/password/reset")
    public ResponseEntity<MensajeResponse> restablecerPassword(
            @Valid @RequestBody PasswordResetRequest request) {
        return ResponseEntity.ok(authService.restablecerPassword(request));
    }
}
