package com.staffflow.security;

import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementacion de {@link UserDetailsService} para StaffFlow.
 *
 * <p>Spring Security llama a este servicio durante el proceso de autenticacion
 * para obtener los datos del usuario almacenados en base de datos. El flujo es:
 * <ol>
 *   <li>AuthService llama a {@code AuthenticationManager.authenticate()}.</li>
 *   <li>Spring Security llama internamente a {@code loadUserByUsername()}.</li>
 *   <li>Este metodo busca el usuario en BD y devuelve un {@link UserDetails}.</li>
 *   <li>Spring Security compara el password del UserDetails con el de la peticion usando BCrypt.</li>
 * </ol>
 *
 * <p>El rol se convierte al formato que Spring Security entiende: prefijo {@code ROLE_}.
 * Por ejemplo, {@code Rol.ADMIN} se convierte en {@code ROLE_ADMIN}.
 * Esto permite usar {@code hasRole("ADMIN")} en SecurityConfig, que internamente
 * busca {@code ROLE_ADMIN}.
 *
 * @author Santiago Castillo
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    // Repositorio JPA para buscar el usuario por username en la tabla 'usuarios'
    private final UsuarioRepository usuarioRepository;

    /**
     * Carga un usuario por su nombre de usuario para el proceso de autenticacion.
     *
     * <p>Solo los usuarios con {@code activo = true} pueden autenticarse.
     * Un usuario desactivado (baja logica, decision de diseno 4) recibe
     * {@code UsernameNotFoundException} con el mismo mensaje que un usuario
     * inexistente para evitar enumeracion de cuentas activas/inactivas.
     *
     * @param username nombre de usuario tal como fue introducido en el login
     * @return {@link UserDetails} con username, password hasheado y rol
     * @throws UsernameNotFoundException si el usuario no existe o esta inactivo
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Buscar usuario en BD por username (campo UNIQUE en tabla usuarios)
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + username));

        // Verificar que el usuario esta activo (baja logica: activo=false no puede autenticarse)
        // Se usa el mismo mensaje que "no encontrado" para evitar enumeracion de usuarios
        if (!usuario.getActivo()) {
            throw new UsernameNotFoundException("Usuario no encontrado: " + username);
        }

        // Convertir el rol del enum de StaffFlow al formato ROLE_XXX que Spring Security espera.
        // hasRole("ADMIN") en SecurityConfig busca internamente "ROLE_ADMIN".
        // El prefijo ROLE_ es una convencion de Spring Security, no una decision nuestra.
        String authority = "ROLE_" + usuario.getRol().name();

        // Construir y devolver el UserDetails con:
        //   - username: identificador del usuario
        //   - passwordHash: hash BCrypt almacenado en BD (Spring lo comparara con el input)
        //   - authorities: lista con el unico rol del usuario
        return new User(
                usuario.getUsername(),
                usuario.getPasswordHash(),
                List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
