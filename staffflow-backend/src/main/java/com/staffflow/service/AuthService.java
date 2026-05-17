package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.LoginRequest;
import com.staffflow.dto.request.PasswordChangeRequest;
import com.staffflow.dto.request.PasswordRecoveryRequest;
import com.staffflow.dto.request.PasswordResetRequest;
import com.staffflow.dto.response.LoginResponse;
import com.staffflow.dto.response.MensajeResponse;
import com.staffflow.dto.response.UsuarioResponse;
import com.staffflow.security.JwtTokenProvider;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Servicio de autenticación y gestión de credenciales.
 *
 * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
 * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
 * descrito a continuación pertenece al andamiaje reservado para v2.0.</p>
 *
 * <p>Cubre los cinco endpoints del grupo /api/v1/auth:</p>
 * <ul>
 *   <li>E01 — login</li>
 *   <li>E02 — obtener datos del usuario autenticado</li>
 *   <li>E03 — cambiar contraseña (usuario conoce la contraseña actual)</li>
 *   <li>E04 — solicitar recuperación por email (en v1 entrega contraseña
 *       temporal real vía Gmail SMTP)</li>
 *   <li>E05 — restablecer contraseña con token de un solo uso (andamiaje
 *       v2.0; en v1 siempre HTTP 400 porque nadie escribe el token)</li>
 * </ul>
 *
 * <p>RNF-S04 en v1 aplica a la contraseña temporal entregada por E04
 * (longitud, anti-enumeración y respuesta genérica), no al token UUID,
 * cuyo flujo de 30 minutos queda reservado para v2.0.</p>
 *
 * @author Santiago Castillo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // --- Dependencias inyectadas por Lombok @RequiredArgsConstructor ---

    /** Repositorio de usuarios: acceso a BD para leer y actualizar credenciales. */
    private final UsuarioRepository usuarioRepository;

    /** Repositorio de empleados: necesario para resolver empleadoId en el login. */
    private final EmpleadoRepository empleadoRepository;

    /**
     * Gestor de autenticación de Spring Security.
     * Se usa en login() para delegar la verificación de credenciales.
     * Bean expuesto en SecurityConfig.
     */
    private final AuthenticationManager authenticationManager;

    /**
     * Proveedor JWT: genera y valida tokens HS384.
     * Se usa en login() para crear el token tras autenticar.
     */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Codificador BCrypt (factor 10).
     * Se usa en E03 para verificar la contraseña actual antes de cambiarla,
     * y en E03/E05 para hashear la nueva contraseña antes de guardarla.
     * Bean expuesto en SecurityConfig.
     */
    private final PasswordEncoder passwordEncoder;

    /** Servicio de email: envía el correo de recuperación de contraseña (E04). */
    private final EmailService emailService;

    // E01 — POST /api/v1/auth/login

    /**
     * Autentica al usuario y devuelve un JWT válido.
     *
     * <p>Flujo: AuthenticationManager valida credenciales → JwtTokenProvider
     * genera token → se devuelve LoginResponse con token, rol, username y
     * empleadoId (null si ADMIN).</p>
     *
     * @param request DTO con username y password
     * @return LoginResponse con el JWT y metadatos del usuario
     */
    public LoginResponse login(LoginRequest request) {
        // 1. Spring Security verifica username + password contra la BD
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // 2. Cargar la entidad Usuario para extraer rol y empleadoId
        Usuario usuario = usuarioRepository.findByUsername(request.getUsername())
                .orElseThrow(); // No puede fallar: Authentication acaba de verificar que existe

        // 3. Determinar empleadoId y nombre para mostrar en la UI.
        // ADMIN no tiene perfil de empleado → empleadoId=null, nombre=username.
        // ENCARGADO/EMPLEADO: nombre = nombre + apellido1 del empleado.
        var optEmpleado = empleadoRepository.findByUsuarioId(usuario.getId());
        Long empleadoId = optEmpleado.map(Empleado::getId).orElse(null);
        String nombre = optEmpleado
                .map(e -> e.getNombre() + " " + e.getApellido1())
                .orElse(usuario.getUsername());

        String token = jwtTokenProvider.generarToken(
                usuario.getUsername(),
                usuario.getRol().name(),
                empleadoId
        );

        return new LoginResponse(token, usuario.getRol(), usuario.getUsername(), empleadoId, nombre);
    }

    // E02 — GET /api/v1/auth/me

    /**
     * Devuelve los datos del usuario autenticado a partir del JWT.
     *
     * <p>El username se extrae del SecurityContext, que JwtAuthFilter ha
     * cargado previamente al validar el token. No requiere parámetros en
     * la petición: toda la información necesaria viene del token.</p>
     *
     * <p>Roles permitidos: ADMIN, ENCARGADO, EMPLEADO (cualquier usuario
     * autenticado puede consultar sus propios datos).</p>
     *
     * @return UsuarioResponse con los datos del usuario (sin password_hash)
     * @throws jakarta.persistence.EntityNotFoundException si el username del
     *         token no existe en BD (no debería ocurrir en condiciones normales)
     */
    public UsuarioResponse obtenerUsuarioAutenticado() {
        // Extraer el username del SecurityContext cargado por JwtAuthFilter
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // Buscar el usuario en BD por username
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + username));

        // Construir el DTO de respuesta sin exponer password_hash
        return toUsuarioResponse(usuario);
    }

    // E03 — PUT /api/v1/auth/password

    /**
     * Cambia la contraseña del usuario autenticado.
     *
     * <p>Flujo:
     * <ol>
     *   <li>Extraer username del SecurityContext (token ya validado).</li>
     *   <li>Verificar que currentPassword coincide con el hash almacenado.</li>
     *   <li>Hashear newPassword con BCrypt y guardar en BD.</li>
     * </ol>
     * </p>
     *
     * <p>Si currentPassword es incorrecto se lanza una excepción que el
     * GlobalExceptionHandler convertirá en HTTP 400.</p>
     *
     * @param request DTO con currentPassword y newPassword
     * @return MensajeResponse confirmando el cambio
     * @throws IllegalArgumentException si la contraseña actual no coincide
     * @throws jakarta.persistence.EntityNotFoundException si el username del
     *         token no existe en BD (no debería ocurrir en condiciones normales)
     */
    @Transactional
    public MensajeResponse cambiarPassword(PasswordChangeRequest request) {
        // 1. Obtener el usuario autenticado
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + username));

        // 2. Verificar que la contraseña actual es correcta
        // passwordEncoder.matches() compara en texto plano con el hash almacenado
        if (!passwordEncoder.matches(request.getPasswordActual(), usuario.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }

        // 3. Hashear la nueva contraseña y guardar
        usuario.setPasswordHash(passwordEncoder.encode(request.getPasswordNueva()));
        usuarioRepository.save(usuario);

        return new MensajeResponse("Contraseña actualizada correctamente");
    }

    // E04 — POST /api/v1/auth/password/recovery

    /**
     * Solicita la recuperación de contraseña por email (E04).
     *
     * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
     * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
     * descrito a continuación pertenece al andamiaje reservado para v2.0.</p>
     *
     * <p>Comportamiento real en v1:</p>
     * <ol>
     *   <li>Si el email existe en BD, se genera una contraseña temporal de
     *       8 caracteres alfanuméricos mediante {@code generarPasswordTemporal()}
     *       (SecureRandom sobre un alfabeto sin caracteres ambiguos).</li>
     *   <li>Se sobrescribe {@code passwordHash} del usuario con el hash BCrypt
     *       de esa contraseña temporal.</li>
     *   <li>Se envía la contraseña en claro al email del usuario a través de
     *       {@link EmailService#enviarPasswordTemporal(String, String)} (Gmail
     *       SMTP, envío asíncrono).</li>
     * </ol>
     *
     * <p>Este flujo NO toca {@code resetToken} ni {@code resetTokenExpiry}
     * en la entidad Usuario; ambos campos quedan reservados para v2.0.</p>
     *
     * <p>Decisión de seguridad (RNF-S04): si el email no existe en BD se
     * devuelve exactamente la misma respuesta 200 que si existe. Esto evita
     * que un atacante pueda enumerar qué emails están registrados en el
     * sistema mediante las respuestas de este endpoint.</p>
     *
     * @param request DTO con el email del usuario
     * @return MensajeResponse con mensaje genérico (mismo tanto si existe como si no)
     */
    @Transactional
    public MensajeResponse solicitarRecuperacion(PasswordRecoveryRequest request) {
        // Si no existe, devolvemos la misma respuesta sin hacer nada (RNF-S04)
        usuarioRepository.findByEmail(request.getEmail()).ifPresent(usuario -> {
            // Generar contraseña temporal legible (8 chars: letras + dígitos)
            String passwordTemporal = generarPasswordTemporal();

            // Reemplazar la contraseña del usuario con la temporal (hasheada)
            usuario.setPasswordHash(passwordEncoder.encode(passwordTemporal));
            usuarioRepository.save(usuario);

            emailService.enviarPasswordTemporal(usuario.getEmail(), passwordTemporal);
        });

        return new MensajeResponse("Si el email está registrado recibirás tu contraseña temporal");
    }

    private String generarPasswordTemporal() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // E05 — POST /api/v1/auth/password/reset

    /**
     * Restablece la contraseña usando el token de recuperación (E05).
     *
     * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
     * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
     * descrito a continuación pertenece al andamiaje reservado para v2.0.</p>
     *
     * <p>Estado real en v1: este método siempre falla con
     * IllegalArgumentException (HTTP 400). El motivo es que E04 no escribe
     * {@code resetToken} en la base de datos en v1; por lo tanto la búsqueda
     * {@code findByResetToken} en el paso 1 siempre devuelve
     * {@code Optional.empty} y se lanza la excepción del orElseThrow.</p>
     *
     * <p>Flujo previsto en v2.0 (contexto, no operativo en v1):</p>
     * <ol>
     *   <li>Buscar usuario por resetToken.</li>
     *   <li>Verificar que el token no ha caducado (resetTokenExpiry &gt; ahora).</li>
     *   <li>Hashear newPassword con BCrypt y guardar.</li>
     *   <li>Invalidar el token: resetToken = null, resetTokenExpiry = null.</li>
     * </ol>
     *
     * <p>En v2.0, si el token no existe o ha caducado se lanza
     * IllegalArgumentException que el GlobalExceptionHandler convertirá en
     * HTTP 400. RNF-S04 (v2.0): token de un solo uso; una vez usado,
     * resetToken se pone a null y un segundo intento con el mismo token
     * devuelve 400.</p>
     *
     * @param request DTO con token y newPassword
     * @return MensajeResponse confirmando el restablecimiento
     *         (en v1 nunca se alcanza: siempre se lanza la excepción)
     * @throws IllegalArgumentException siempre en v1; en v2.0, si el token no
     *         existe o ha caducado
     */
    @Transactional
    public MensajeResponse restablecerPassword(PasswordResetRequest request) {
        // 1. Buscar usuario por token
        // Si no existe: el token es inválido o ya fue usado (resetToken = null)
        Usuario usuario = usuarioRepository.findByResetToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Token de recuperación inválido o ya utilizado"));

        // 2. Verificar que el token no ha caducado
        // resetTokenExpiry se guardó como ahora + 30 minutos en E04
        if (usuario.getResetTokenExpiry() == null ||
                LocalDateTime.now().isAfter(usuario.getResetTokenExpiry())) {
            throw new IllegalArgumentException("El token de recuperación ha caducado");
        }

        // 3. Hashear la nueva contraseña y guardar
        usuario.setPasswordHash(passwordEncoder.encode(request.getPasswordNueva()));

        // 4. Invalidar el token para que no pueda reutilizarse (RNF-S04)
        usuario.setResetToken(null);
        usuario.setResetTokenExpiry(null);

        usuarioRepository.save(usuario);

        return new MensajeResponse("Contraseña restablecida correctamente");
    }

    // Métodos privados de apoyo

    /**
     * Convierte la entidad Usuario en UsuarioResponse para la capa de presentación.
     *
     * <p>Regla de oro: ningún Service devuelve entidades JPA al Controller,
     * siempre usa DTOs. Este método es el punto único de conversión en AuthService.</p>
     *
     * @param usuario entidad JPA
     * @return UsuarioResponse sin password_hash ni campos sensibles
     */
    private UsuarioResponse toUsuarioResponse(Usuario usuario) {
        // Construir el DTO en el mismo orden que el constructor de UsuarioResponse:
        // (id, username, email, rol, activo, fechaCreacion)
        // Si el orden del constructor cambia, ajustar aquí.
        return new UsuarioResponse(
                usuario.getId(),          // Long id
                usuario.getUsername(),    // String username
                usuario.getEmail(),       // String email
                usuario.getRol(),         // Rol rol
                usuario.getActivo(),      // Boolean activo
                usuario.getFechaCreacion() // LocalDateTime fechaCreacion
        );
    }
}
