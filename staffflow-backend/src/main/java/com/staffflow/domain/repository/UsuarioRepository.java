package com.staffflow.domain.repository;

import com.staffflow.domain.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para los usuarios del sistema.
 *
 * <p>Proporciona los métodos de consulta que necesitan AuthService,
 * UsuarioService y UserDetailsServiceImpl. Spring Data genera la
 * implementación SQL de cada findBy automáticamente a partir del
 * nombre del método.</p>
 *
 * <p>Restricciones de la tabla: username UNIQUE, email UNIQUE.
 * Las violaciones de unicidad lanza DataIntegrityViolationException,
 * que GlobalExceptionHandler convierte en HTTP 409 Conflict.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.entity.Usuario
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca un usuario por su nombre de usuario.
     *
     * <p>Lo usa UserDetailsServiceImpl para cargar el usuario durante
     * la autenticación con Spring Security. Si no existe devuelve
     * Optional.empty() y Spring Security lanza BadCredentialsException.</p>
     *
     * @param username nombre de usuario
     * @return Optional con el usuario, vacío si no existe
     */
    Optional<Usuario> findByUsername(String username);

    /**
     * Busca un usuario por su email.
     *
     * <p>Lo usa AuthService en E04 (POST /auth/password/recovery) para
     * localizar al usuario que solicita recuperación de contraseña.
     * El endpoint devuelve HTTP 200 aunque el email no exista, para
     * no revelar qué usuarios están registrados (RNF-S04).</p>
     *
     * @param email email del usuario
     * @return Optional con el usuario, vacío si no existe
     */
    Optional<Usuario> findByEmail(String email);

    /**
     * Busca un usuario por su token de recuperación de contraseña.
     *
     * <p>Lo usa AuthService en E05 (POST /auth/password/reset) para
     * validar el token recibido por email. El token es de un solo uso
     * y caduca a los 30 minutos (RNF-S04, decisión nº9). Si el token
     * no existe o ya fue usado devuelve Optional.empty() → HTTP 400.</p>
     *
     * @param resetToken token opaco de recuperación
     * @return Optional con el usuario, vacío si el token no existe
     */
    Optional<Usuario> findByResetToken(String resetToken);
}