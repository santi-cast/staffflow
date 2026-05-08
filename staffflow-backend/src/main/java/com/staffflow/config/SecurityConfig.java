package com.staffflow.config;

import com.staffflow.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuracion central de Spring Security para StaffFlow.
 *
 * <p>Define dos cadenas de seguridad independientes con prioridades distintas:
 * <ul>
 *   <li>{@code terminalFilterChain} (Order=1): cubre los 4 endpoints de terminal PIN
 *       ({@code /api/v1/terminal/**}). Sin JWT, sin sesion, totalmente publica.
 *       Tiene su propia cadena para que el bloqueo por dispositivo (RNF-S05)
 *       y la autenticacion por PIN no interfieran con el resto de la API.</li>
 *   <li>{@code apiFilterChain} (Order=2): cubre el resto de la API. Stateless con JWT.
 *       Las rutas publicas (login, recuperacion de contrasena, health) no requieren token.
 *       El resto exige JWT valido y rol suficiente segun Endpoints_v3.</li>
 * </ul>
 *
 * <p>Decisiones de diseno aplicadas:
 * <ul>
 *   <li>Decision 21: PIN para terminal compartido, JWT para acceso a datos personales.</li>
 *   <li>Decision 18: sin refresh token en v1.0 (JWT de 8h cubre jornada completa).</li>
 *   <li>RNF-S05: bloqueo por dispositivo gestionado en TerminalController, no aqui.</li>
 * </ul>
 *
 * @author Santiago Castillo
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Filtro JWT inyectado — se aplica solo en apiFilterChain, nunca en terminalFilterChain
    private final JwtAuthFilter jwtAuthFilter;

    // =========================================================================
    // CADENA 1 — TERMINAL PIN (Order=1, mayor prioridad)
    // Cubre exclusivamente /api/v1/terminal/**
    // Sin JWT. Los 4 endpoints (E48-E51) se autentican por PIN de 4 digitos.
    // =========================================================================

    /**
     * Cadena de seguridad para los endpoints de terminal de fichaje PIN.
     *
     * <p>Por que Order=1: Spring evalua las cadenas en orden ascendente.
     * Al asignar prioridad 1 a esta cadena, las peticiones a /api/v1/terminal/**
     * son interceptadas aqui antes de llegar a la cadena general (Order=2).
     * Sin este aislamiento, el JwtAuthFilter de la cadena general rechazaria
     * con 401 las peticiones del terminal que llegan sin Bearer token.
     *
     * @param http constructor de la cadena proporcionado por Spring Security
     * @return cadena configurada para el terminal PIN
     * @throws Exception si falla la configuracion interna de Spring Security
     */
    @Bean
    @Order(1)
    public SecurityFilterChain terminalFilterChain(HttpSecurity http) throws Exception {
        http
            // Limita esta cadena a los endpoints PIN publicos (E48-E52).
            // /api/v1/terminal/bloqueo (E53-E54) queda FUERA — requiere JWT y cae
            // a apiFilterChain (Order=2) donde se valida rol ENCARGADO/ADMIN.
            .securityMatcher(
                "/api/v1/terminal/entrada",
                "/api/v1/terminal/salida",
                "/api/v1/terminal/pausa/iniciar",
                "/api/v1/terminal/pausa/finalizar",
                "/api/v1/terminal/estado"
            )

            // CSRF desactivado: API REST stateless, no hay formularios HTML ni cookies de sesion
            .csrf(csrf -> csrf.disable())

            // Sin sesion: cada peticion del terminal es independiente
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Todos los endpoints de terminal son publicos — la autenticacion es por PIN,
            // gestionada dentro de TerminalController y TerminalService
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );

        return http.build();
    }

    // =========================================================================
    // CADENA 2 — API REST GENERAL (Order=2)
    // Cubre toda la API excepto /api/v1/terminal/**
    // Stateless con JWT. Rutas publicas explicitamente declaradas.
    // =========================================================================

    /**
     * Cadena de seguridad principal para la API REST de StaffFlow.
     *
     * <p>Configuracion de rutas segun Endpoints_v3:
     * <ul>
     *   <li>PUBLICAS (sin JWT): login, recuperacion/reset de contrasena, health check.</li>
     *   <li>SOLO ADMIN: gestion de empresa (/empresa/**) y usuarios (/usuarios/**).</li>
     *   <li>ADMIN o ENCARGADO: empleados, fichajes, pausas, ausencias, presencia,
     *       saldos (sin /me), informes y PDFs.</li>
     *   <li>TODOS los roles autenticados: logout y cambio de contrasena.</li>
     *   <li>SOLO EMPLEADO: endpoints /me de cada recurso.</li>
     * </ul>
     *
     * <p>El {@link JwtAuthFilter} se registra antes de {@link UsernamePasswordAuthenticationFilter}
     * para que cada peticion protegida sea validada por JWT antes de llegar al controller.
     *
     * @param http constructor de la cadena proporcionado por Spring Security
     * @return cadena configurada para la API REST general
     * @throws Exception si falla la configuracion interna de Spring Security
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF desactivado: API REST stateless
            .csrf(csrf -> csrf.disable())

            // Deshabilitar frameOptions para H2 Console en perfil dev
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())
            )

            // Sin sesion: el estado de autenticacion viaja en el JWT, no en la sesion HTTP
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth

                // --- RUTAS PUBLICAS (sin JWT) ---
                // E01: login
                .requestMatchers("/api/v1/auth/login").permitAll()
                // E04: solicitar token de recuperacion de contrasena
                .requestMatchers("/api/v1/auth/password/recovery").permitAll()
                // E05: restablecer contrasena con token
                .requestMatchers("/api/v1/auth/password/reset").permitAll()
                // E52: health check (creado en Fase 0, fuera de /api/v1)
                .requestMatchers("/api/health").permitAll()
                // H2 Console: accesible en perfil dev sin autenticacion
                .requestMatchers("/h2-console/**").permitAll()
                // Swagger UI y OpenAPI spec: accesibles sin autenticacion para desarrollo
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                // --- ADMIN o ENCARGADO: bloqueo del terminal (E53-E54) ---
                .requestMatchers("/api/v1/terminal/bloqueo").hasAnyRole("ADMIN", "ENCARGADO")

                // --- SOLO ADMIN ---
                // E06-E07: configuracion de empresa
                .requestMatchers("/api/v1/empresa/**").hasRole("ADMIN")
                // E08-E12: gestion de usuarios
                .requestMatchers("/api/v1/usuarios/**").hasRole("ADMIN")
                // E40: recalcular saldo (solo ADMIN segun Endpoints_v3)
                .requestMatchers("/api/v1/saldos/*/recalcular").hasRole("ADMIN")

                // --- EMPLEADO y ENCARGADO: endpoints /me (datos propios) ---
                // E21: perfil propio
                .requestMatchers("/api/v1/empleados/me").hasAnyRole("EMPLEADO", "ENCARGADO")
                // E26: fichajes propios
                .requestMatchers("/api/v1/fichajes/me").hasAnyRole("EMPLEADO", "ENCARGADO")
                // E34: ausencias propias | E-ausencias: informe HTML de ausencias
                .requestMatchers("/api/v1/ausencias/me/informe").hasAnyRole("EMPLEADO", "ENCARGADO")
                .requestMatchers("/api/v1/ausencias/me").hasAnyRole("EMPLEADO", "ENCARGADO")
                // E41: saldo propio
                .requestMatchers("/api/v1/saldos/me").hasAnyRole("EMPLEADO", "ENCARGADO")
                // E37: parte diario propio
                .requestMatchers("/api/v1/presencia/parte-diario/me").hasAnyRole("EMPLEADO", "ENCARGADO")
                // E35: pausas propias
                .requestMatchers("/api/v1/pausas/me").hasAnyRole("EMPLEADO", "ENCARGADO")
                // E-me: informe de horas propio
                .requestMatchers("/api/v1/informes/me/**").hasAnyRole("EMPLEADO", "ENCARGADO")

                // --- ADMIN o ENCARGADO ---
                // E13-E20: gestion de empleados (sin /me, ya cubierto arriba)
                .requestMatchers("/api/v1/empleados/**").hasAnyRole("ADMIN", "ENCARGADO")
                // E22-E25: fichajes (sin /me)
                .requestMatchers("/api/v1/fichajes/**").hasAnyRole("ADMIN", "ENCARGADO")
                // E27-E29: pausas (sin /me, ya cubierto arriba)
                .requestMatchers("/api/v1/pausas/**").hasAnyRole("ADMIN", "ENCARGADO")
                // E30-E33: ausencias (sin /me)
                .requestMatchers("/api/v1/ausencias/**").hasAnyRole("ADMIN", "ENCARGADO")
                // E35-E36: presencia (sin /me)
                .requestMatchers("/api/v1/presencia/**").hasAnyRole("ADMIN", "ENCARGADO")
                // E38-E39: saldos (sin /me ni /recalcular, ya cubiertos)
                .requestMatchers("/api/v1/saldos/**").hasAnyRole("ADMIN", "ENCARGADO")
                // E42-E47: informes JSON/HTML y PDFs
                .requestMatchers("/api/v1/informes/**").hasAnyRole("ADMIN", "ENCARGADO")

                // --- TODOS LOS ROLES AUTENTICADOS ---
                // E02: logout
                // E03: cambiar contrasena propia
                .requestMatchers("/api/v1/auth/**").authenticated()

                // Cualquier otra ruta no declarada requiere autenticacion (politica restrictiva)
                .anyRequest().authenticated()
            )

            // Registrar el filtro JWT antes del filtro estandar de usuario/contrasena de Spring.
            // Esto garantiza que cada peticion protegida sea evaluada por JWT antes de
            // que Spring intente autenticacion por formulario (que no usamos).
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // =========================================================================
    // BEANS DE INFRAESTRUCTURA DE SEGURIDAD
    // =========================================================================

    /**
     * Encoder de contrasenas con BCrypt.
     *
     * <p>BCrypt aplica un factor de coste (por defecto 10 rondas) que hace que
     * cada hash tarde ~100ms en generarse. Esto hace inviables los ataques
     * de fuerza bruta masivos aunque el atacante obtenga el hash (RNF-S01).
     * Alternativa descartada: MD5/SHA sin sal — vulnerables a rainbow tables.
     *
     * @return instancia de BCryptPasswordEncoder lista para ser inyectada
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Factor de coste por defecto: 10. Suficiente para produccion en PYME.
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager expuesto como bean para ser inyectado en AuthService.
     *
     * <p>Spring Security crea internamente el AuthenticationManager a partir de
     * {@link AuthenticationConfiguration}. Al exponerlo como bean, AuthService
     * puede llamar a {@code authenticationManager.authenticate()} para verificar
     * las credenciales durante el login sin necesidad de reimplementar la logica
     * de verificacion de usuario y contrasena.
     *
     * @param authenticationConfiguration configuracion de autenticacion de Spring Security
     * @return el AuthenticationManager configurado
     * @throws Exception si Spring Security no puede construir el manager
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
