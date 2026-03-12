package com.staffflow.service;

import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.LoginRequest;
import com.staffflow.dto.response.LoginResponse;
import com.staffflow.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Servicio de autenticacion y gestion de contrasenas de StaffFlow.
 *
 * <p>Gestiona el ciclo completo de autenticacion: login con JWT, logout stateless,
 * cambio de contrasena autenticado y flujo de recuperacion de contrasena por email.
 *
 * <p>En esta iteracion (Bloque 2) se implementa unicamente el metodo {@code login}.
 * El resto de metodos permanecen como esqueleto hasta el bloque correspondiente.
 *
 * @author Santiago Castillo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // AuthenticationManager: delega la verificacion de credenciales en Spring Security.
    // Internamente llama a UserDetailsServiceImpl.loadUserByUsername() y compara el
    // password con BCrypt. Bean expuesto en SecurityConfig.
    private final AuthenticationManager authenticationManager;

    // Proveedor JWT para generar el token tras autenticacion exitosa
    private final JwtTokenProvider jwtTokenProvider;

    // Repositorio de usuarios para obtener el rol y datos del usuario autenticado
    private final UsuarioRepository usuarioRepository;

    // Repositorio de empleados para obtener el empleadoId si el rol no es ADMIN.
    // Los ADMIN no tienen perfil de empleado (decision de diseno, RF-01 a RF-07).
    private final EmpleadoRepository empleadoRepository;

    /**
     * Autentica un usuario y genera un token JWT.
     *
     * <p>Flujo completo:
     * <ol>
     *   <li>Delegar la verificacion de credenciales en {@code AuthenticationManager}.
     *       Spring Security carga el usuario con {@code UserDetailsServiceImpl},
     *       verifica el password con BCrypt y lanza {@code BadCredentialsException}
     *       si las credenciales son incorrectas.</li>
     *   <li>Recuperar el usuario completo desde BD para obtener el rol y el ID.</li>
     *   <li>Si el rol es ADMIN, el empleadoId es null (sin perfil de empleado).</li>
     *   <li>Si el rol es ENCARGADO o EMPLEADO, buscar el ID del perfil de empleado
     *       vinculado al usuario para incluirlo en el token.</li>
     *   <li>Generar el JWT con username, rol y empleadoId.</li>
     *   <li>Devolver {@link LoginResponse} con el token y los datos del usuario.</li>
     * </ol>
     *
     * <p>Codigos HTTP gestionados por Spring Security automaticamente:
     * <ul>
     *   <li>400: si el body del request no es valido (gestionado por @Valid en el controller).</li>
     *   <li>401: si las credenciales son incorrectas (BadCredentialsException de Spring).</li>
     * </ul>
     *
     * @param request DTO con username y password del login
     * @return {@link LoginResponse} con token JWT, rol, empleadoId y username
     */
    public LoginResponse login(LoginRequest request) {
        log.debug("Intento de login para usuario: {}", request.getUsername());

        // Paso 1: verificar credenciales delegando en Spring Security.
        // UsernamePasswordAuthenticationToken con 2 parametros = token no autenticado.
        // AuthenticationManager lo procesa llamando a UserDetailsServiceImpl y BCrypt.
        // Si las credenciales son incorrectas lanza BadCredentialsException → HTTP 401.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // Paso 2: obtener el usuario completo desde BD.
        // En este punto las credenciales ya son correctas (si no, nunca llegamos aqui).
        // authentication.getName() devuelve el username del UserDetails cargado.
        Usuario usuario = usuarioRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException(
                        "Usuario no encontrado tras autenticacion: " + authentication.getName()));

        // Paso 3: resolver el empleadoId segun el rol.
        // Los ADMIN no tienen perfil de empleado (decision de diseno: ADMIN gestion pura).
        // ENCARGADO y EMPLEADO tienen siempre un perfil de empleado vinculado (1:1 en BD).
        Long empleadoId = null;
        if (usuario.getRol() != Rol.ADMIN) {
            // Buscar el empleado por su usuario_id (FK UNIQUE en tabla empleados)
            empleadoId = empleadoRepository.findByUsuarioId(usuario.getId())
                    .map(empleado -> empleado.getId())
                    .orElse(null);
            // Si es ENCARGADO o EMPLEADO y no tiene perfil de empleado es una incoherencia
            // de datos. Se registra warning pero no se bloquea el login: el usuario puede
            // autenticarse aunque el perfil de empleado aun no este creado.
            if (empleadoId == null) {
                log.warn("Usuario {} con rol {} no tiene perfil de empleado vinculado",
                        usuario.getUsername(), usuario.getRol());
            }
        }

        // Paso 4: generar el JWT con los datos del usuario autenticado.
        // El token incluye username, rol y empleadoId para que los filtros
        // y servicios puedan operar sin consultar la BD en cada peticion.
        String token = jwtTokenProvider.generarToken(
                usuario.getUsername(),
                usuario.getRol().name(),
                empleadoId
        );

        log.info("Login exitoso para usuario: {} [{}]", usuario.getUsername(), usuario.getRol());

        // Paso 5: construir y devolver la respuesta segun el contrato de E01 (Endpoints_v3):
        // { "token": "string", "rol": "ADMIN|ENCARGADO|EMPLEADO",
        //   "empleadoId": null|number, "username": "string" }
        // Orden de parametros segun @AllArgsConstructor de LoginResponse:
        // token, rol (Rol enum), username, empleadoId
        return new LoginResponse(
                token,
                usuario.getRol(),
                usuario.getUsername(),
                empleadoId
        );
    }

    // =========================================================================
    // METODOS PENDIENTES — se implementan en bloques posteriores
    // =========================================================================

    /**
     * Cierra la sesion del usuario autenticado.
     * JWT es stateless: la invalidacion se realiza en el cliente (DataStore Android).
     * El servidor confirma con 200 OK. Bloque 2 — pendiente.
     */
    public void logout() {
        // TODO Bloque 2: confirmar logout (stateless, sin logica server-side adicional)
        throw new UnsupportedOperationException("Pendiente de implementacion en Bloque 2");
    }

    /**
     * Cambia la contrasena del usuario autenticado verificando la actual.
     * Bloque 2 — pendiente.
     */
    public void cambiarPassword(String username, String passwordActual, String passwordNueva) {
        // TODO Bloque 2
        throw new UnsupportedOperationException("Pendiente de implementacion en Bloque 2");
    }

    /**
     * Genera y envia por email el token de recuperacion de contrasena.
     * Requiere spring-boot-starter-mail configurado. Bloque D — pendiente.
     */
    public void solicitarRecuperacion(String email) {
        // TODO Bloque D (trabajo futuro documentado en Wireframes_v5)
        throw new UnsupportedOperationException("Pendiente de implementacion en Bloque D");
    }

    /**
     * Valida el token de recuperacion y establece la nueva contrasena.
     * Bloque D — pendiente.
     */
    public void restablecerPassword(String token, String passwordNueva) {
        // TODO Bloque D
        throw new UnsupportedOperationException("Pendiente de implementacion en Bloque D");
    }
}
