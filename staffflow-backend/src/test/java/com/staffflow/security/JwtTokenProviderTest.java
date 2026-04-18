package com.staffflow.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios de JwtTokenProvider.
 *
 * No necesita contexto de Spring. Se usan ReflectionTestUtils para inyectar
 * las propiedades @Value y se invoca init() manualmente para derivar la SecretKey.
 *
 * Cubre:
 *   - Generacion de tokens con los claims correctos (sub, rol, empleadoId).
 *   - Validacion de tokens: valido, manipulado, expirado.
 *   - Conversion Integer→Long del claim empleadoId (gotcha de jjwt 0.12.x).
 *
 * @author Santiago Castillo
 */
@DisplayName("JwtTokenProvider — generacion y validacion de tokens")
class JwtTokenProviderTest {

    // Secreto de prueba — minimo 48 caracteres para HS384 (384 bits)
    private static final String SECRET =
            "staffflow-test-secret-key-desarrollo-2026-minimo48chars-ok";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 3_600_000L); // 1 hora
        provider.init(); // deriva la SecretKey desde el secreto inyectado
    }

    // ---------------------------------------------------------------
    // Claims generados correctamente
    // ---------------------------------------------------------------

    @Test
    @DisplayName("generarToken — admin sin empleadoId — sub y rol correctos, empleadoId null")
    void generarToken_adminSinEmpleadoId_claimsCorrectos() {
        String token = provider.generarToken("admin", "ADMIN", null);

        assertThat(provider.getUsernameDesdeToken(token)).isEqualTo("admin");
        assertThat(provider.getRolDesdeToken(token)).isEqualTo("ADMIN");
        assertThat(provider.getEmpleadoIdDesdeToken(token)).isNull();
    }

    @Test
    @DisplayName("generarToken — encargado con empleadoId — todos los claims correctos")
    void generarToken_encargadoConEmpleadoId_claimsCorrectos() {
        String token = provider.generarToken("encargado", "ENCARGADO", 7L);

        assertThat(provider.getUsernameDesdeToken(token)).isEqualTo("encargado");
        assertThat(provider.getRolDesdeToken(token)).isEqualTo("ENCARGADO");
        assertThat(provider.getEmpleadoIdDesdeToken(token)).isEqualTo(7L);
    }

    @Test
    @DisplayName("generarToken — empleado con empleadoId pequeño — devuelve Long, no Integer")
    void generarToken_empleadoConEmpleadoIdPequenio_devuelveLongNoInteger() {
        // jjwt deserializa numeros < 2^31 como Integer — el provider debe convertir a Long
        String token = provider.generarToken("empleado1", "EMPLEADO", 1L);

        Long empleadoId = provider.getEmpleadoIdDesdeToken(token);

        assertThat(empleadoId).isNotNull();
        assertThat(empleadoId).isInstanceOf(Long.class);
        assertThat(empleadoId).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // Validacion de tokens
    // ---------------------------------------------------------------

    @Test
    @DisplayName("validarToken — token recien generado — devuelve true")
    void validarToken_tokenValido_devuelveTrue() {
        String token = provider.generarToken("user", "EMPLEADO", 2L);

        assertThat(provider.validarToken(token)).isTrue();
    }

    @Test
    @DisplayName("validarToken — firma alterada — devuelve false")
    void validarToken_firmaAlterada_devuelveFalse() {
        String token = provider.generarToken("user", "EMPLEADO", 2L);
        // Modificar el ultimo caracter de la firma invalida el token
        String tokenManipulado = token.substring(0, token.length() - 1) + "X";

        assertThat(provider.validarToken(tokenManipulado)).isFalse();
    }

    @Test
    @DisplayName("validarToken — token malformado — devuelve false")
    void validarToken_tokenMalformado_devuelveFalse() {
        assertThat(provider.validarToken("esto.no.es.un.jwt")).isFalse();
    }

    @Test
    @DisplayName("validarToken — token expirado — devuelve false")
    void validarToken_tokenExpirado_devuelveFalse() {
        // Crear un provider con expiracion de -1ms (ya expirado al generarse)
        JwtTokenProvider providerExpirado = new JwtTokenProvider();
        ReflectionTestUtils.setField(providerExpirado, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(providerExpirado, "jwtExpirationMs", -1L);
        providerExpirado.init();

        String tokenExpirado = providerExpirado.generarToken("user", "EMPLEADO", 1L);

        assertThat(provider.validarToken(tokenExpirado)).isFalse();
    }

    @Test
    @DisplayName("validarToken — token nulo — devuelve false sin excepcion")
    void validarToken_tokenNulo_devuelveFalseSinExcepcion() {
        assertThat(provider.validarToken(null)).isFalse();
    }

    @Test
    @DisplayName("validarToken — token vacio — devuelve false sin excepcion")
    void validarToken_tokenVacio_devuelveFalseSinExcepcion() {
        assertThat(provider.validarToken("")).isFalse();
    }

    // ---------------------------------------------------------------
    // Tokens generados por claves distintas no se validan entre si
    // ---------------------------------------------------------------

    @Test
    @DisplayName("validarToken — token firmado con otra clave — devuelve false")
    void validarToken_otraClave_devuelveFalse() {
        JwtTokenProvider otroProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(otroProvider, "jwtSecret",
                "otra-clave-secreta-completamente-diferente-48chars!!");
        ReflectionTestUtils.setField(otroProvider, "jwtExpirationMs", 3_600_000L);
        otroProvider.init();

        String tokenDeOtraApp = otroProvider.generarToken("atacante", "ADMIN", null);

        assertThat(provider.validarToken(tokenDeOtraApp)).isFalse();
    }
}
