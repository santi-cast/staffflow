package com.staffflow.domain.repository;

import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Usuario.
 *
 * Tabla: usuarios
 * Usado por: AuthService, UsuarioService, UserDetailsServiceImpl
 *
 * Restricciones de tabla relevantes:
 *   - username UNIQUE: dos usuarios no pueden tener el mismo nombre de usuario.
 *   - email UNIQUE: dos usuarios no pueden tener el mismo email.
 *   - Las violaciones de unicidad lanzan DataIntegrityViolationException,
 *     que GlobalExceptionHandler convierte en HTTP 400. Las validaciones
 *     preventivas existsBy* en UsuarioService interceptan los duplicados
 *     antes de llegar a BD y devuelven HTTP 409 con mensaje claro.
 *   - Bajas lógicas: activo=false, nunca DELETE físico.
 *
 * Métodos heredados de JpaRepository usados:
 *   - save()       → crear y actualizar usuario (E08, E11, E12)
 *   - findById()   → obtener detalle (E10)
 *   - findAll()    → listar sin filtros (E09)
 *
 * Métodos custom — usados por AuthService, UserDetailsServiceImpl:
 *   - findByUsername, findByEmail, findByResetToken
 *
 * Métodos custom — usados por UsuarioService:
 *   - existsByUsername, existsByEmail
 *   - existsByUsernameAndIdNot, existsByEmailAndIdNot
 *   - findByRol, findByActivo, findByRolAndActivo
 *
 * Spring Data JPA genera automáticamente la implementación SQL de todos
 * los métodos a partir de su nombre. No se escribe SQL explícito.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // ----------------------------------------------------------------
    // Usados por: AuthService, UserDetailsServiceImpl
    // ----------------------------------------------------------------

    /**
     * Busca un usuario por su nombre de usuario.
     *
     * Usado por UserDetailsServiceImpl durante el login: Spring Security
     * llama a loadUserByUsername() que delega en este método.
     * Si no existe, Spring Security lanza BadCredentialsException → HTTP 401.
     *
     * @param username nombre de usuario (campo UNIQUE en BD)
     * @return Optional con el usuario si existe
     */
    Optional<Usuario> findByUsername(String username);

    /**
     * Busca un usuario por su email.
     *
     * Usado por AuthService en el flujo de recuperación de contraseña (E04).
     * El endpoint devuelve HTTP 200 aunque el email no exista para evitar
     * enumeración de usuarios (RNF-S04, anti-enumeración).
     *
     * @param email email del usuario (campo UNIQUE en BD)
     * @return Optional con el usuario si existe
     */
    Optional<Usuario> findByEmail(String email);

    /**
     * Busca un usuario por su token de recuperación de contraseña.
     *
     * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
     * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
     * descrito a continuación pertenece al andamiaje reservado para v2.0
     * (ver memoria TFG, bloque B10 Vías Futuras → Reset password con token UUID).</p>
     *
     * <p>Estado real en v1: este método siempre devuelve {@code Optional.empty}.
     * Ningún flujo de v1 escribe {@code resetToken} en la base de datos, por
     * lo que la columna está siempre a NULL. Su único consumidor (AuthService
     * en E05) recibe el {@code empty} y lanza IllegalArgumentException, que
     * el GlobalExceptionHandler convierte en HTTP 400.</p>
     *
     * <p>Rol previsto en v2.0: AuthService llamaría a este método en E05
     * (reset con token). El token sería de un solo uso y caducaría a los
     * 30 minutos; si no existiera o hubiera caducado → HTTP 400.</p>
     *
     * @param resetToken token opaco generado en E04 (en v1 no se genera)
     * @return Optional con el usuario si el token existe y es válido
     *         (en v1 siempre vacío)
     */
    Optional<Usuario> findByResetToken(String resetToken);

    // ----------------------------------------------------------------
    // Usados por: UsuarioService (E08, E09, E11)
    // ----------------------------------------------------------------

    /**
     * Comprueba si existe un usuario con el username indicado.
     *
     * Usado en UsuarioService.crear() (E08) para validación preventiva
     * de unicidad antes de insertar. Evita que DataIntegrityViolationException
     * llegue a GlobalExceptionHandler con un mensaje genérico de BD.
     * HTTP 409 con mensaje claro si devuelve true.
     *
     * @param username nombre de usuario a verificar
     * @return true si ya existe un usuario con ese username
     */
    boolean existsByUsername(String username);

    /**
     * Comprueba si existe un usuario con el email indicado.
     *
     * Usado en UsuarioService.crear() (E08) para validación preventiva
     * de unicidad antes de insertar. HTTP 409 con mensaje claro si devuelve true.
     *
     * @param email email a verificar
     * @return true si ya existe un usuario con ese email
     */
    boolean existsByEmail(String email);

    /**
     * Comprueba si existe otro usuario con el username indicado, excluyendo
     * al usuario con el id especificado.
     *
     * Usado en UsuarioService.actualizar() (E11) para validar unicidad
     * de username en edición sin producir falso positivo cuando el usuario
     * mantiene su propio username sin cambiarlo.
     *
     * @param username nombre de usuario a verificar
     * @param id       ID del usuario que se está editando (se excluye de la búsqueda)
     * @return true si otro usuario distinto ya tiene ese username
     */
    boolean existsByUsernameAndIdNot(String username, Long id);

    /**
     * Comprueba si existe otro usuario con el email indicado, excluyendo
     * al usuario con el id especificado.
     *
     * Usado en UsuarioService.actualizar() (E11) para validar unicidad
     * de email en edición sin producir falso positivo cuando el usuario
     * mantiene su propio email sin cambiarlo.
     *
     * @param email email a verificar
     * @param id    ID del usuario que se está editando (se excluye de la búsqueda)
     * @return true si otro usuario distinto ya tiene ese email
     */
    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Lista todos los usuarios con el rol indicado.
     *
     * Usado en UsuarioService.listar() (E09) cuando se filtra solo por rol
     * sin filtro de estado activo.
     *
     * @param rol rol a filtrar (ADMIN, ENCARGADO, EMPLEADO)
     * @return lista de usuarios con ese rol (puede ser vacía)
     */
    List<Usuario> findByRol(Rol rol);

    /**
     * Lista todos los usuarios con el estado activo indicado.
     *
     * Usado en UsuarioService.listar() (E09) cuando se filtra solo por
     * estado sin filtro de rol.
     *
     * @param activo true = usuarios activos, false = usuarios inactivos
     * @return lista de usuarios con ese estado (puede ser vacía)
     */
    List<Usuario> findByActivo(Boolean activo);

    /**
     * Lista todos los usuarios con el rol y estado activo indicados.
     *
     * Usado en UsuarioService.listar() (E09) cuando se aplican ambos
     * filtros simultáneamente (?rol=X&activo=true|false).
     *
     * @param rol    rol a filtrar
     * @param activo estado a filtrar
     * @return lista de usuarios que cumplen ambos filtros (puede ser vacía)
     */
    List<Usuario> findByRolAndActivo(Rol rol, Boolean activo);
}
