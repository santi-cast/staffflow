package com.staffflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Proveedor de tokens JWT para StaffFlow.
 *
 * <p>Gestiona la generacion y validacion de tokens JWT usando la libreria
 * jjwt 0.12.6, declarada manualmente en pom.xml (no es transitiva de Spring Boot).
 *
 * <p>Estructura del token generado:
 * <ul>
 *   <li>Header: algoritmo HMAC-SHA seleccionado por jjwt segun la longitud
 *       de la clave (HS256 para 32-47 bytes, HS384 para 48-63 bytes,
 *       HS512 para 64 o mas bytes). En este proyecto no se fija el
 *       algoritmo explicitamente en {@code signWith}: depende del valor
 *       real de {@code staffflow.jwt.secret} en cada entorno.</li>
 *   <li>Claims estandar: {@code sub} (username), {@code iat} (emision), {@code exp} (expiracion).</li>
 *   <li>Claims personalizados: {@code rol} (ADMIN|ENCARGADO|EMPLEADO),
 *       {@code empleadoId} (null para ADMIN, Long para ENCARGADO/EMPLEADO).</li>
 * </ul>
 *
 * <p>Decisiones activas:
 * <ul>
 *   <li>Sin refresh token en v1.0. Validez de 12h cubre una jornada laboral
 *       con descanso de comida. Refresh token queda como mejora para v2.0.</li>
 *   <li>Firma simetrica HMAC-SHA con clave secreta. Suficiente para una API
 *       monolitica donde el mismo servidor genera y valida los tokens.
 *       Alternativa descartada: RS256 (asimetrico) — solo necesario si otros
 *       servicios externos validan el token.</li>
 * </ul>
 *
 * @author Santiago Castillo
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /**
     * Clave secreta leida de application.yml (propiedad {@code staffflow.jwt.secret}).
     * Debe tener al menos 32 caracteres (256 bits): es el minimo que exige
     * {@code Keys.hmacShaKeyFor}. Con 32-47 caracteres jjwt firma con HS256;
     * con 48-63 caracteres con HS384; con 64 o mas con HS512.
     * En produccion se inyecta como variable de entorno, nunca en texto plano en el repo.
     */
    @Value("${staffflow.jwt.secret}")
    private String jwtSecret;

    /**
     * Validez del token en milisegundos, leida de application.yml.
     * Valor por defecto: 43200000 ms = 12 horas.
     * 12h cubre una jornada laboral con descanso de comida.
     * Refresh token queda como mejora para v2.0.
     */
    @Value("${staffflow.jwt.expiration-ms:43200000}")
    private long jwtExpirationMs;

    // Clave criptografica derivada de jwtSecret, inicializada en @PostConstruct
    private SecretKey secretKey;

    /**
     * Inicializa la clave criptografica tras la inyeccion de propiedades.
     *
     * <p>Se usa {@code @PostConstruct} en lugar del constructor porque las propiedades
     * {@code @Value} se inyectan despues de la construccion del bean. Si se intentara
     * inicializar secretKey en el constructor, jwtSecret todavia seria null.
     *
     * <p>{@link Keys#hmacShaKeyFor} deriva una {@link SecretKey} valida para HMAC-SHA
     * a partir de los bytes de la cadena configurada. El algoritmo concreto
     * (HS256/HS384/HS512) lo decide jjwt en tiempo de firma segun la longitud.
     */
    @PostConstruct
    public void init() {
        // Derivar la SecretKey desde los bytes UTF-8 del secreto configurado.
        // Keys.hmacShaKeyFor lanza excepcion si la clave tiene menos de 32 bytes (256 bits).
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("JwtTokenProvider inicializado. Expiracion configurada: {}ms", jwtExpirationMs);
    }

    /**
     * Genera un token JWT firmado para el usuario autenticado.
     *
     * <p>El token incluye el rol y el empleadoId como claims personalizados para que
     * los filtros y servicios puedan extraer esta informacion sin consultar la BD
     * en cada peticion. Esto es coherente con la naturaleza stateless de JWT.
     *
     * @param username   nombre de usuario (se almacena en el claim {@code sub})
     * @param rol        rol del usuario en formato string (ADMIN, ENCARGADO o EMPLEADO)
     * @param empleadoId ID del perfil de empleado vinculado, o {@code null} si el usuario
     *                   es ADMIN (los ADMIN no tienen perfil de empleado)
     * @return token JWT firmado como String en formato Base64url compacto
     */
    public String generarToken(String username, String rol, Long empleadoId) {
        Date ahora = new Date();
        // Calcular la fecha de expiracion sumando los milisegundos configurados
        Date expiracion = new Date(ahora.getTime() + jwtExpirationMs);

        return Jwts.builder()
                // sub: identificador principal del token (username unico en el sistema)
                .subject(username)
                // Claim personalizado: rol del usuario para control de acceso en SecurityConfig
                .claim("rol", rol)
                // Claim personalizado: empleadoId para que los endpoints /me puedan
                // identificar al empleado autenticado sin consultar la BD
                .claim("empleadoId", empleadoId)
                // iat: fecha de emision del token
                .issuedAt(ahora)
                // exp: fecha de expiracion (12h por defecto)
                .expiration(expiracion)
                // Firmar con la clave simetrica inicializada en @PostConstruct.
                // jjwt selecciona HS256/HS384/HS512 segun la longitud de la clave.
                .signWith(secretKey)
                // Serializar a formato compacto: header.payload.signature en Base64url
                .compact();
    }

    /**
     * Extrae el username del claim {@code sub} de un token JWT.
     *
     * <p>Llamar a este metodo sin haber validado el token primero puede lanzar
     * {@link JwtException}. El flujo correcto es: validar primero con
     * {@link #validarToken(String)}, luego extraer el username.
     *
     * @param token token JWT en formato compacto
     * @return username almacenado en el claim {@code sub}
     */
    public String getUsernameDesdeToken(String token) {
        return extraerClaims(token).getSubject();
    }

    /**
     * Extrae el rol del claim personalizado {@code rol} de un token JWT.
     *
     * @param token token JWT en formato compacto
     * @return rol del usuario como String (ADMIN, ENCARGADO o EMPLEADO)
     */
    public String getRolDesdeToken(String token) {
        return extraerClaims(token).get("rol", String.class);
    }

    /**
     * Extrae el empleadoId del claim personalizado {@code empleadoId} de un token JWT.
     *
     * @param token token JWT en formato compacto
     * @return ID del empleado como Long, o {@code null} si el usuario es ADMIN
     */
    public Long getEmpleadoIdDesdeToken(String token) {
        // jjwt deserializa numeros como Integer si caben en 32 bits.
        // Hay que convertir a Long explicitamente para evitar ClassCastException.
        Object value = extraerClaims(token).get("empleadoId");
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        return Long.valueOf(value.toString());
    }

    /**
     * Valida un token JWT verificando firma y fecha de expiracion.
     *
     * <p>jjwt 0.12.6 lanza {@link JwtException} (o subclases) si:
     * <ul>
     *   <li>La firma no coincide con la clave secreta (token manipulado).</li>
     *   <li>El token ha expirado ({@code exp} anterior a la fecha actual).</li>
     *   <li>El formato del token no es valido.</li>
     * </ul>
     *
     * @param token token JWT a validar
     * @return {@code true} si el token es valido y no ha expirado, {@code false} en caso contrario
     */
    public boolean validarToken(String token) {
        try {
            // Intentar parsear y verificar la firma. Si no lanza excepcion, el token es valido.
            extraerClaims(token);
            return true;
        } catch (JwtException e) {
            // Token invalido: firma incorrecta, expirado o malformado
            log.warn("Token JWT invalido: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            // Token nulo o vacio
            log.warn("Token JWT vacio o nulo");
            return false;
        }
    }

    /**
     * Parsea el token y extrae todos los claims.
     *
     * <p>Metodo privado de uso interno. Verifica la firma con la clave simetrica.
     * Cualquier problema (firma incorrecta, token expirado, formato invalido)
     * lanza una subclase de {@link JwtException}.
     *
     * @param token token JWT en formato compacto
     * @return objeto {@link Claims} con todos los claims del payload
     * @throws JwtException si el token no es valido
     */
    private Claims extraerClaims(String token) {
        return Jwts.parser()
                // Configurar la clave de verificacion (misma que se uso para firmar)
                .verifyWith(secretKey)
                .build()
                // Parsear y verificar. Lanza JwtException si algo falla.
                .parseSignedClaims(token)
                // Extraer el payload (claims)
                .getPayload();
    }
}
