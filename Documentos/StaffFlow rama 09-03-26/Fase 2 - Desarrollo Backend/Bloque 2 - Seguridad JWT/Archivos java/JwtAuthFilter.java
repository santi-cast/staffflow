package com.staffflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticacion JWT para StaffFlow.
 *
 * <p>Se ejecuta una vez por cada peticion HTTP (hereda de {@link OncePerRequestFilter}).
 * Su responsabilidad es extraer el token JWT de la cabecera {@code Authorization},
 * validarlo y cargar el contexto de seguridad de Spring para que los controllers
 * puedan acceder al usuario autenticado.
 *
 * <p>Flujo de cada peticion:
 * <ol>
 *   <li>Extraer el token de la cabecera {@code Authorization: Bearer <token>}.</li>
 *   <li>Si no hay token, dejar pasar la peticion sin autenticar
 *       (las rutas publicas no necesitan token).</li>
 *   <li>Si hay token, validarlo con {@link JwtTokenProvider}.</li>
 *   <li>Si es valido, construir el objeto de autenticacion y cargarlo en
 *       {@link SecurityContextHolder} para que Spring Security lo use.</li>
 *   <li>Continuar la cadena de filtros en cualquier caso.</li>
 * </ol>
 *
 * <p>Este filtro se registra en {@code SecurityConfig.apiFilterChain} antes de
 * {@code UsernamePasswordAuthenticationFilter}. NO se registra en
 * {@code terminalFilterChain} porque el terminal usa PIN, no JWT.
 *
 * @author Santiago Castillo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    // Proveedor JWT para validacion y extraccion de claims
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Excluye las rutas publicas del filtro JWT.
     *
     * <p>En Spring Security 6, aunque el filtro deje pasar la peticion sin autenticar
     * cuando no hay token, ejecutarlo en rutas publicas puede causar comportamientos
     * inesperados con el SecurityContext. Excluir explicitamente las rutas publicas
     * garantiza que el filtro solo actua en peticiones que requieren JWT.
     *
     * @param request peticion HTTP entrante
     * @return true si la peticion NO debe pasar por este filtro
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Excluir todas las rutas que no requieren JWT segun SecurityConfig
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/password/recovery")
                || path.equals("/api/v1/auth/password/reset")
                || path.equals("/api/health")
                || path.startsWith("/api/v1/terminal/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/h2-console");
    }

    /**
     * Logica principal del filtro. Se ejecuta una vez por peticion HTTP.
     *
     * @param request     peticion HTTP entrante
     * @param response    respuesta HTTP saliente
     * @param filterChain cadena de filtros restante
     * @throws ServletException si ocurre un error de servlet
     * @throws IOException      si ocurre un error de I/O
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Paso 1: intentar extraer el token de la cabecera Authorization
        String token = extraerTokenDeCabecera(request);

        // Paso 2: si hay token y es valido, cargar el contexto de seguridad
        if (token != null && jwtTokenProvider.validarToken(token)) {

            // Extraer username y rol desde el payload del token (sin consultar BD)
            String username = jwtTokenProvider.getUsernameDesdeToken(token);
            String rol = jwtTokenProvider.getRolDesdeToken(token);

            // Convertir el rol al formato ROLE_XXX que Spring Security entiende
            // (coherente con UserDetailsServiceImpl)
            String authority = "ROLE_" + rol;

            // Construir el objeto de autenticacion con username y rol.
            // Se usa UsernamePasswordAuthenticationToken con 3 parametros
            // (principal, credentials, authorities): la presencia de authorities
            // es la senal que Spring Security usa para marcar la autenticacion como exitosa.
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            username,
                            null, // credenciales null: ya autenticado por JWT, no por password
                            List.of(new SimpleGrantedAuthority(authority))
                    );

            // Adjuntar detalles de la peticion (IP, session ID) al objeto de autenticacion.
            // Util para auditoria y para que Spring Security tenga contexto completo.
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            // Cargar la autenticacion en el SecurityContext de esta peticion.
            // A partir de aqui, cualquier componente puede llamar a
            // SecurityContextHolder.getContext().getAuthentication() para obtener
            // el usuario autenticado sin consultar la BD.
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Usuario autenticado por JWT: {} [{}]", username, rol);
        }

        // Paso 3: continuar la cadena de filtros independientemente del resultado.
        // Si no habia token o era invalido, Spring Security gestionara el acceso
        // segun las reglas de SecurityConfig (401 si la ruta requiere autenticacion).
        filterChain.doFilter(request, response);
    }

    /**
     * Extrae el token JWT de la cabecera {@code Authorization}.
     *
     * <p>El formato esperado es {@code Authorization: Bearer <token>}.
     * Si la cabecera no existe, esta vacia o no empieza por "Bearer ",
     * devuelve {@code null} y el filtro deja pasar la peticion sin autenticar.
     *
     * @param request peticion HTTP de la que extraer la cabecera
     * @return el token JWT sin el prefijo "Bearer ", o {@code null} si no se encuentra
     */
    private String extraerTokenDeCabecera(HttpServletRequest request) {
        String cabecera = request.getHeader("Authorization");

        // Verificar que la cabecera existe y tiene el formato correcto
        if (StringUtils.hasText(cabecera) && cabecera.startsWith("Bearer ")) {
            // Eliminar el prefijo "Bearer " (7 caracteres) para obtener solo el token
            return cabecera.substring(7);
        }

        // Sin cabecera Authorization o formato incorrecto: peticion publica o sin autenticar
        return null;
    }
}
