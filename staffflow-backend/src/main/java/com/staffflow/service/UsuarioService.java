package com.staffflow.service;

import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.UsuarioPatchRequest;
import com.staffflow.dto.request.UsuarioRequest;
import com.staffflow.dto.response.MensajeResponse;
import com.staffflow.dto.response.UsuarioResponse;
import com.staffflow.exception.ConflictException;
import com.staffflow.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de gestión de usuarios del sistema.
 *
 * Cubre los endpoints E08-E12 del Grupo 3 (Gestión de Usuarios).
 * Accesible exclusivamente por el rol ADMIN. Los roles ENCARGADO
 * y EMPLEADO reciben HTTP 403 antes de llegar a este servicio
 * (Spring Security, @PreAuthorize en el controller).
 *
 * Decisiones de diseño aplicadas:
 *   - Baja lógica: E12 ejecuta activo=false, nunca
 *     SQL DELETE. El historial de auditoría en fichajes, pausas y
 *     ausencias queda intacto porque usuario_id sigue siendo válido.
 *   - BCrypt (RNF-S01): la contraseña se cifra con PasswordEncoder
 *     antes de persistir. El campo password_hash nunca aparece en
 *     ningún DTO de respuesta.
 *   - HTTP 409 preventivo: se valida unicidad de username y email
 *     antes de insertar para dar un mensaje de error claro en lugar
 *     de dejar explotar DataIntegrityViolationException con mensaje
 *     genérico de BD. Se lanza ConflictException (HTTP 409) para
 *     duplicados y NotFoundException (HTTP 404) para recursos ausentes.
 *     Ambos casos son distintos semánticamente y requieren códigos HTTP
 *     distintos.
 *   - Filtros combinables en listar(): rol y activo son independientes
 *     y pueden usarse simultáneamente o por separado (RF-04).
 *
 * RF cubiertos: RF-03, RF-04, RF-05, RF-06, RF-07.
 * RNF aplicados: RNF-S01 (BCrypt), RNF-M01 (sin lógica en controller).
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    // ----------------------------------------------------------------
    // E08 — POST /api/v1/usuarios
    // RF-03: Crear usuario
    // ----------------------------------------------------------------

    /**
     * Crea un nuevo usuario en el sistema.
     *
     * Username y email deben ser únicos en la tabla usuarios.
     * La contraseña se cifra con BCrypt antes de persistir (RNF-S01).
     * El usuario se crea siempre con activo=true.
     *
     * Para los roles ENCARGADO y EMPLEADO, tras crear el usuario con
     * este endpoint se debe crear también el perfil de empleado vinculado
     * mediante E13 (POST /api/v1/empleados). El rol ADMIN no tiene
     * perfil de empleado.
     *
     * Códigos HTTP producidos:
     *   201 Created      → usuario creado correctamente
     *   400 Bad Request  → datos de entrada inválidos (@Valid en controller)
     *   409 Conflict     → username o email ya existen en otro usuario
     *
     * @param request datos del nuevo usuario: username, email, password, rol
     * @return UsuarioResponse con los datos del usuario creado (sin password_hash)
     */
    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        // Validación preventiva: username único (HTTP 409 con mensaje claro)
        if (usuarioRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException(
                    "El username '" + request.getUsername() + "' ya está registrado");
        }
        // Validación preventiva: email único (HTTP 409 con mensaje claro)
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException(
                    "El email '" + request.getEmail() + "' ya está registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(request.getUsername());
        usuario.setEmail(request.getEmail());
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setRol(request.getRol());
        usuario.setActivo(true);

        Usuario guardado = usuarioRepository.save(usuario);
        return toUsuarioResponse(guardado);
    }

    // ----------------------------------------------------------------
    // E09 — GET /api/v1/usuarios
    // RF-04: Listar usuarios con filtros opcionales
    // ----------------------------------------------------------------

    /**
     * Lista todos los usuarios del sistema con filtros opcionales y combinables.
     *
     * Sin parámetros devuelve todos los usuarios (activos e inactivos).
     * Los parámetros rol y activo son independientes y combinables:
     * se puede filtrar solo por rol, solo por activo, o por ambos
     * simultáneamente.
     *
     * Códigos HTTP producidos:
     *   200 OK          → lista devuelta correctamente (puede ser lista vacía)
     *   403 Forbidden   → rol insuficiente (gestionado por Spring Security)
     *
     * @param rol    filtro por rol: "ADMIN", "ENCARGADO" o "EMPLEADO" — null = sin filtro
     * @param activo filtro por estado: true = activos, false = inactivos — null = sin filtro
     * @return lista de UsuarioResponse (sin password_hash en ningún elemento)
     */
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(String rol, Boolean activo) {
        List<Usuario> usuarios;

        if (rol != null && activo != null) {
            usuarios = usuarioRepository.findByRolAndActivo(Rol.valueOf(rol), activo);
        } else if (rol != null) {
            usuarios = usuarioRepository.findByRol(Rol.valueOf(rol));
        } else if (activo != null) {
            usuarios = usuarioRepository.findByActivo(activo);
        } else {
            usuarios = usuarioRepository.findAll();
        }

        return usuarios.stream()
                .map(this::toUsuarioResponse)
                .collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // E10 — GET /api/v1/usuarios/{id}
    // RF-05: Consultar detalle de usuario
    // ----------------------------------------------------------------

    /**
     * Devuelve el detalle completo de un usuario concreto.
     *
     * No incluye el campo password_hash por seguridad (RNF-S01).
     *
     * Códigos HTTP producidos:
     *   200 OK          → usuario encontrado y devuelto
     *   403 Forbidden   → rol insuficiente (gestionado por Spring Security)
     *   404 Not Found   → usuario con el id indicado no existe en BD
     *
     * @param id ID del usuario a consultar
     * @return UsuarioResponse con los datos del usuario (sin password_hash)
     */
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Usuario con id " + id + " no encontrado"));
        return toUsuarioResponse(usuario);
    }

    // ----------------------------------------------------------------
    // E11 — PATCH /api/v1/usuarios/{id}
    // RF-06: Editar usuario
    // ----------------------------------------------------------------

    /**
     * Actualiza los datos editables de un usuario: email y rol.
     *
     * La contraseña se gestiona exclusivamente por E03 (cambio con contraseña
     * actual) y E05 (reset con token de recuperación). No se puede modificar
     * por este endpoint.
     *
     * El campo activo tampoco se modifica aquí: la desactivación se realiza
     * exclusivamente por E12 (baja lógica). Esta separación es intencional:
     * evita que una edición de datos accidental modifique el estado del usuario.
     *
     * Solo actualiza los campos enviados con valor no nulo (PATCH semántico).
     * Valida unicidad de email excluyendo al propio usuario que se está
     * editando (puede conservar su propio valor sin conflicto).
     *
     * Códigos HTTP producidos:
     *   200 OK          → usuario actualizado correctamente
     *   400 Bad Request → datos de entrada inválidos
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → usuario con el id indicado no existe
     *   409 Conflict    → email ya existe en otro usuario
     *
     * @param id      ID del usuario a actualizar
     * @param request campos a actualizar (email, rol — ambos opcionales)
     * @return UsuarioResponse con los datos actualizados
     */
    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioPatchRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Usuario con id " + id + " no encontrado"));

        if (request.getEmail() != null) {
            // Verifica que el nuevo email no lo usa otro usuario distinto al actual
            if (usuarioRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
                throw new ConflictException(
                        "El email '" + request.getEmail() + "' ya está registrado");
            }
            usuario.setEmail(request.getEmail());
        }

        if (request.getRol() != null) {
            usuario.setRol(request.getRol());
        }

        return toUsuarioResponse(usuarioRepository.save(usuario));
    }

    // ----------------------------------------------------------------
    // E12 — DELETE /api/v1/usuarios/{id}
    // RF-07: Desactivar usuario (baja lógica)
    // ----------------------------------------------------------------

    /**
     * Desactiva un usuario aplicando baja lógica (activo = false).
     *
     * No ejecuta SQL DELETE sobre la tabla usuarios. El registro permanece
     * en base de datos con activo=false. El historial de auditoría queda
     * intacto: el campo usuario_id en fichajes, pausas y
     * planificacion_ausencias sigue siendo una FK válida que apunta
     * al usuario desactivado.
     *
     * El usuario desactivado no puede hacer login: UserDetailsServiceImpl
     * verifica el campo activo al cargar el usuario para Spring Security
     * y lanza excepción si activo=false, lo que produce HTTP 401.
     *
     * Códigos HTTP producidos:
     *   200 OK          → usuario desactivado correctamente
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → usuario con el id indicado no existe
     *
     * @param id ID del usuario a desactivar
     * @return MensajeResponse confirmando la operación
     */
    @Transactional
    public MensajeResponse desactivar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Usuario con id " + id + " no encontrado"));

        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        return new MensajeResponse("Usuario desactivado correctamente");
    }

    // ----------------------------------------------------------------
    // Conversión entidad → DTO (uso interno)
    // ----------------------------------------------------------------

    /**
     * Convierte una entidad Usuario en su DTO de respuesta.
     *
     * Punto único de conversión entidad → DTO para este servicio.
     * Nunca incluye password_hash en ninguna circunstancia (RNF-S01).
     *
     * @param usuario entidad JPA a convertir
     * @return UsuarioResponse listo para devolver al controller
     */
    private UsuarioResponse toUsuarioResponse(Usuario usuario) {
        UsuarioResponse response = new UsuarioResponse();
        response.setId(usuario.getId());
        response.setUsername(usuario.getUsername());
        response.setEmail(usuario.getEmail());
        response.setRol(usuario.getRol());
        response.setActivo(usuario.getActivo());
        response.setFechaCreacion(usuario.getFechaCreacion());
        return response;
    }
}
