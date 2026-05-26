package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de AuthService — cubre los cinco endpoints publicos del
 * grupo /api/v1/auth: E01 login, E02 me, E03 cambiar password, E04 solicitar
 * recuperacion y E05 restablecer password.
 *
 * <p>Estrategia: Mockito puro sin contexto Spring, siguiendo el patron
 * establecido en {@link PausaServiceTest}, {@link FichajeServiceTest},
 * {@link AusenciaServiceTest} y {@link com.staffflow.service.scheduled.ProcesoCierreDiarioTest}.</p>
 *
 * <p>Particularidades:</p>
 * <ul>
 *   <li>El SUT lee el usuario autenticado de {@link SecurityContextHolder} en
 *       E02 y E03 (no recibe el username por parametro como otros services).
 *       Se setea un {@link Authentication} mock manualmente antes de cada
 *       test que lo necesite y se limpia en {@code @AfterEach}.</li>
 *   <li>Solo E05 depende del reloj: el bean {@link Clock} se inyecta
 *       fijado a las 12:00 del 15/01/2026 para volver deterministas las
 *       ramas de token vigente y token caducado.</li>
 * </ul>
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — E01/E02/E03/E04/E05")
class AuthServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    // Instante fijo para tests deterministas: 15/01/2026 a las 12:00 hora de
    // Madrid. Solo E05 lo consume al comparar resetTokenExpiry. El resto de
    // tests funcionan con cualquier reloj.
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 1, 15, 12, 0);
    private static final Clock CLOCK_FIJO =
            Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);

    private AuthService authService;

    private Usuario usuarioAdmin;
    private Usuario usuarioEmpleado;
    private Empleado empleado;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                usuarioRepository,
                empleadoRepository,
                authenticationManager,
                jwtTokenProvider,
                passwordEncoder,
                emailService,
                CLOCK_FIJO);

        // Empleado de referencia (id=10, nombre+apellido1 = "Carlos Lopez")
        empleado = new Empleado();
        empleado.setId(10L);
        empleado.setNombre("Carlos");
        empleado.setApellido1("Lopez");

        // Usuario ADMIN sin ficha de empleado asociada
        usuarioAdmin = new Usuario();
        usuarioAdmin.setId(1L);
        usuarioAdmin.setUsername("admin");
        usuarioAdmin.setEmail("admin@staffflow.com");
        usuarioAdmin.setPasswordHash("$2a$10$hashAdmin");
        usuarioAdmin.setRol(Rol.ADMIN);
        usuarioAdmin.setActivo(true);
        usuarioAdmin.setFechaCreacion(LocalDateTime.of(2025, 12, 1, 10, 0));

        // Usuario EMPLEADO con ficha (id=2, vinculado a empleado id=10)
        usuarioEmpleado = new Usuario();
        usuarioEmpleado.setId(2L);
        usuarioEmpleado.setUsername("carlos");
        usuarioEmpleado.setEmail("carlos@staffflow.com");
        usuarioEmpleado.setPasswordHash("$2a$10$hashCarlos");
        usuarioEmpleado.setRol(Rol.EMPLEADO);
        usuarioEmpleado.setActivo(true);
        usuarioEmpleado.setFechaCreacion(LocalDateTime.of(2025, 12, 5, 9, 30));
    }

    @AfterEach
    void clearSecurityContext() {
        // El SUT lee SecurityContextHolder en E02 y E03. Limpiar siempre
        // para que un test no contamine al siguiente.
        SecurityContextHolder.clearContext();
    }

    /**
     * Setea un {@link Authentication} mock en {@link SecurityContextHolder}
     * con el username indicado, simulando el contexto cargado por
     * JwtAuthFilter tras validar el token.
     */
    private void autenticarComo(String username) {
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // =================================================================
    // E01 — login()
    // =================================================================

    @Nested
    @DisplayName("login (E01) — autenticacion y emision de JWT")
    class LoginTests {

        @Test
        @DisplayName("credenciales validas + EMPLEADO con ficha → token + empleadoId + nombre completo")
        void loginEmpleadoConFicha() {
            LoginRequest request = nuevoLoginRequest("carlos", "secret");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(new UsernamePasswordAuthenticationToken("carlos", "secret"));
            when(usuarioRepository.findByUsername("carlos"))
                    .thenReturn(Optional.of(usuarioEmpleado));
            when(empleadoRepository.findByUsuarioId(2L))
                    .thenReturn(Optional.of(empleado));
            when(jwtTokenProvider.generarToken("carlos", "EMPLEADO", 10L))
                    .thenReturn("jwt-carlos");

            LoginResponse response = authService.login(request);

            assertThat(response.getToken()).isEqualTo("jwt-carlos");
            assertThat(response.getRol()).isEqualTo(Rol.EMPLEADO);
            assertThat(response.getUsername()).isEqualTo("carlos");
            assertThat(response.getEmpleadoId()).isEqualTo(10L);
            assertThat(response.getNombre()).isEqualTo("Carlos Lopez");
        }

        @Test
        @DisplayName("credenciales validas + ADMIN sin ficha → empleadoId=null, nombre=username")
        void loginAdminSinFicha() {
            LoginRequest request = nuevoLoginRequest("admin", "secret");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(new UsernamePasswordAuthenticationToken("admin", "secret"));
            when(usuarioRepository.findByUsername("admin"))
                    .thenReturn(Optional.of(usuarioAdmin));
            when(empleadoRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());
            when(jwtTokenProvider.generarToken("admin", "ADMIN", null))
                    .thenReturn("jwt-admin");

            LoginResponse response = authService.login(request);

            assertThat(response.getToken()).isEqualTo("jwt-admin");
            assertThat(response.getRol()).isEqualTo(Rol.ADMIN);
            assertThat(response.getUsername()).isEqualTo("admin");
            assertThat(response.getEmpleadoId()).isNull();
            // Sin ficha → el nombre fallback es el username
            assertThat(response.getNombre()).isEqualTo("admin");
        }

        @Test
        @DisplayName("credenciales validas + ENCARGADO con ficha → token con rol ENCARGADO")
        void loginEncargadoConFicha() {
            Usuario usuarioEncargado = new Usuario();
            usuarioEncargado.setId(3L);
            usuarioEncargado.setUsername("ana");
            usuarioEncargado.setRol(Rol.ENCARGADO);

            Empleado fichaAna = new Empleado();
            fichaAna.setId(20L);
            fichaAna.setNombre("Ana");
            fichaAna.setApellido1("Martinez");

            LoginRequest request = nuevoLoginRequest("ana", "secret");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(new UsernamePasswordAuthenticationToken("ana", "secret"));
            when(usuarioRepository.findByUsername("ana"))
                    .thenReturn(Optional.of(usuarioEncargado));
            when(empleadoRepository.findByUsuarioId(3L)).thenReturn(Optional.of(fichaAna));
            when(jwtTokenProvider.generarToken("ana", "ENCARGADO", 20L))
                    .thenReturn("jwt-ana");

            LoginResponse response = authService.login(request);

            assertThat(response.getRol()).isEqualTo(Rol.ENCARGADO);
            assertThat(response.getEmpleadoId()).isEqualTo(20L);
            assertThat(response.getNombre()).isEqualTo("Ana Martinez");
        }

        @Test
        @DisplayName("AuthenticationManager lanza BadCredentialsException → se propaga (HTTP 401)")
        void credencialesInvalidasSePropagan() {
            LoginRequest request = nuevoLoginRequest("carlos", "wrong");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Credenciales invalidas"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);

            // No se llega a generar token ni a consultar el repositorio
            verifyNoInteractions(jwtTokenProvider);
            verify(usuarioRepository, never()).findByUsername(any());
        }

        @Test
        @DisplayName("usuario autentica pero desaparece de BD entre auth y lookup → NoSuchElementException")
        void usuarioAutenticadoNoEncontradoEnBd() {
            LoginRequest request = nuevoLoginRequest("fantasma", "secret");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(new UsernamePasswordAuthenticationToken("fantasma", "secret"));
            when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

            // El orElseThrow() del codigo lanza NoSuchElementException sin
            // mensaje (caso teorico: solo ocurriria si alguien borra al
            // usuario entre el authenticate() y el findByUsername()).
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(NoSuchElementException.class);

            verifyNoInteractions(jwtTokenProvider);
        }
    }

    // =================================================================
    // E02 — obtenerUsuarioAutenticado()
    // =================================================================

    @Nested
    @DisplayName("obtenerUsuarioAutenticado (E02) — datos del usuario del JWT")
    class ObtenerUsuarioAutenticadoTests {

        @Test
        @DisplayName("usuario existe → UsuarioResponse con todos los campos no sensibles")
        void usuarioExistenteDevuelveResponseCompleto() {
            autenticarComo("admin");
            when(usuarioRepository.findByUsername("admin"))
                    .thenReturn(Optional.of(usuarioAdmin));

            UsuarioResponse response = authService.obtenerUsuarioAutenticado();

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("admin");
            assertThat(response.getEmail()).isEqualTo("admin@staffflow.com");
            assertThat(response.getRol()).isEqualTo(Rol.ADMIN);
            assertThat(response.getActivo()).isTrue();
            assertThat(response.getFechaCreacion())
                    .isEqualTo(LocalDateTime.of(2025, 12, 1, 10, 0));
        }

        @Test
        @DisplayName("usuario del token no existe en BD → EntityNotFoundException (404)")
        void usuarioInexistenteLanzaNotFound() {
            autenticarComo("fantasma");
            when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.obtenerUsuarioAutenticado())
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Usuario no encontrado")
                    .hasMessageContaining("fantasma");
        }

        @Test
        @DisplayName("UsuarioResponse no expone campos sensibles (passwordHash, resetToken, resetTokenExpiry)")
        void responseNoExponeCamposSensibles() {
            // Sanity check del contrato del DTO: el SUT mapea entidad → DTO
            // y los campos sensibles no deben existir en el DTO. Si alguien
            // los agrega por accidente este test lo detecta.
            autenticarComo("carlos");
            usuarioEmpleado.setResetToken("token-sensible-no-exponer");
            usuarioEmpleado.setResetTokenExpiry(LocalDateTime.now(CLOCK_FIJO).plusMinutes(30));
            when(usuarioRepository.findByUsername("carlos"))
                    .thenReturn(Optional.of(usuarioEmpleado));

            UsuarioResponse response = authService.obtenerUsuarioAutenticado();

            // Comprobacion estructural: la clase no debe declarar ninguno
            // de estos campos. Si se agregaran, NoSuchFieldException no se
            // lanzaria y el test fallaria explicitamente.
            assertThat(UsuarioResponse.class.getDeclaredFields())
                    .extracting("name")
                    .doesNotContain("passwordHash", "resetToken", "resetTokenExpiry");
            // Comprobacion de comportamiento: el DTO se construyo igual
            // sin importar los valores sensibles de la entidad origen.
            assertThat(response.getUsername()).isEqualTo("carlos");
        }
    }

    // =================================================================
    // E03 — cambiarPassword()
    // =================================================================

    @Nested
    @DisplayName("cambiarPassword (E03) — cambio con verificacion de password actual")
    class CambiarPasswordTests {

        @Test
        @DisplayName("password actual correcta → se guarda hash nuevo y se devuelve mensaje ok")
        void cambioExitoso() {
            autenticarComo("admin");
            PasswordChangeRequest request = nuevoChangeRequest("oldPass", "newPass123");

            when(usuarioRepository.findByUsername("admin"))
                    .thenReturn(Optional.of(usuarioAdmin));
            when(passwordEncoder.matches("oldPass", "$2a$10$hashAdmin")).thenReturn(true);
            when(passwordEncoder.encode("newPass123")).thenReturn("$2a$10$hashNuevo");

            MensajeResponse response = authService.cambiarPassword(request);

            assertThat(response.getMensaje()).contains("actualizada correctamente");

            // Capturar el Usuario que se guarda y comprobar que lleva el
            // hash nuevo, no la password en claro
            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashNuevo");
            assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("newPass123");
        }

        @Test
        @DisplayName("password actual incorrecta → IllegalArgumentException (HTTP 400), no se guarda")
        void passwordActualIncorrectaRechazada() {
            autenticarComo("admin");
            PasswordChangeRequest request = nuevoChangeRequest("wrongPass", "newPass123");

            when(usuarioRepository.findByUsername("admin"))
                    .thenReturn(Optional.of(usuarioAdmin));
            when(passwordEncoder.matches("wrongPass", "$2a$10$hashAdmin")).thenReturn(false);

            assertThatThrownBy(() -> authService.cambiarPassword(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contraseña actual no es correcta");

            verify(usuarioRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("usuario del token no existe en BD → EntityNotFoundException (404)")
        void usuarioInexistenteLanzaNotFound() {
            autenticarComo("fantasma");
            PasswordChangeRequest request = nuevoChangeRequest("any", "newPass123");

            when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.cambiarPassword(request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Usuario no encontrado")
                    .hasMessageContaining("fantasma");

            verify(usuarioRepository, never()).save(any());
        }
    }

    // =================================================================
    // E04 — solicitarRecuperacion()
    // =================================================================

    @Nested
    @DisplayName("solicitarRecuperacion (E04) — generacion y envio de password temporal")
    class SolicitarRecuperacionTests {

        @Test
        @DisplayName("email existe → genera temporal de 8 chars, hashea, guarda y envia email")
        void emailExistenteGeneraTemporalYEnvia() {
            PasswordRecoveryRequest request = nuevoRecoveryRequest("admin@staffflow.com");

            when(usuarioRepository.findByEmail("admin@staffflow.com"))
                    .thenReturn(Optional.of(usuarioAdmin));
            when(passwordEncoder.encode(any(String.class))).thenReturn("$2a$10$hashTemporal");

            MensajeResponse response = authService.solicitarRecuperacion(request);

            // Mensaje generico de anti-enumeracion (RNF-S04): igual exista o no
            assertThat(response.getMensaje()).contains("Si el email está registrado");

            // Capturar la password temporal en claro que se envia por email
            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> passCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).enviarPasswordTemporal(emailCaptor.capture(), passCaptor.capture());

            // El email destino es el del usuario en BD (RNF-S04), no el
            // del request por si difiere
            assertThat(emailCaptor.getValue()).isEqualTo("admin@staffflow.com");

            // La password temporal tiene exactamente 8 caracteres del
            // alfabeto definido en generarPasswordTemporal()
            String passwordTemporal = passCaptor.getValue();
            assertThat(passwordTemporal).hasSize(8);
            assertThat(passwordTemporal)
                    .matches("[ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789]{8}");

            // Se guarda el hash de la temporal, no la temporal en claro
            ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(usuarioCaptor.capture());
            assertThat(usuarioCaptor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashTemporal");
        }

        @Test
        @DisplayName("email NO existe → mismo mensaje sin save() ni email (anti-enumeracion RNF-S04)")
        void emailInexistenteRespondeIgualSinEfectos() {
            PasswordRecoveryRequest request = nuevoRecoveryRequest("ghost@nowhere.com");

            when(usuarioRepository.findByEmail("ghost@nowhere.com")).thenReturn(Optional.empty());

            MensajeResponse response = authService.solicitarRecuperacion(request);

            // Mismo mensaje exacto que el caso "existe": un atacante no
            // puede distinguir emails registrados de los que no lo estan
            assertThat(response.getMensaje()).contains("Si el email está registrado");

            // No se toca password ni se envia email
            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(emailService);
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("password temporal generada solo contiene caracteres legibles (sin 0/O/1/I/l)")
        void passwordTemporalSinCaracteresAmbiguos() {
            // El alfabeto excluye caracteres ambiguos visualmente: 0, 1, O, I,
            // i, l. Test directo de esa propiedad sobre 50 generaciones para
            // dar señal estadistica solida.
            PasswordRecoveryRequest request = nuevoRecoveryRequest("admin@staffflow.com");
            when(usuarioRepository.findByEmail("admin@staffflow.com"))
                    .thenReturn(Optional.of(usuarioAdmin));
            when(passwordEncoder.encode(any(String.class))).thenReturn("$2a$10$hash");

            for (int i = 0; i < 50; i++) {
                authService.solicitarRecuperacion(request);
            }

            ArgumentCaptor<String> passCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService, org.mockito.Mockito.times(50))
                    .enviarPasswordTemporal(eq("admin@staffflow.com"), passCaptor.capture());

            for (String pwd : passCaptor.getAllValues()) {
                assertThat(pwd)
                        .doesNotContain("0").doesNotContain("1")
                        .doesNotContain("O").doesNotContain("I")
                        .doesNotContain("i").doesNotContain("l");
            }
        }
    }

    // =================================================================
    // E05 — restablecerPassword()
    // =================================================================

    @Nested
    @DisplayName("restablecerPassword (E05) — andamiaje v2.0; v1 siempre 400 sin token")
    class RestablecerPasswordTests {

        @Test
        @DisplayName("token no encontrado → IllegalArgumentException (escenario real v1)")
        void tokenNoEncontradoLanza400() {
            // Este es el caso real en v1: nadie escribe resetToken en BD,
            // por lo que findByResetToken siempre devuelve empty.
            PasswordResetRequest request = nuevoResetRequest("token-x", "newPass123");

            when(usuarioRepository.findByResetToken("token-x")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.restablecerPassword(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("inválido o ya utilizado");

            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("token existe pero resetTokenExpiry es null → IllegalArgumentException")
        void tokenSinExpiryLanzaCaducado() {
            PasswordResetRequest request = nuevoResetRequest("token-x", "newPass123");
            usuarioAdmin.setResetToken("token-x");
            usuarioAdmin.setResetTokenExpiry(null);

            when(usuarioRepository.findByResetToken("token-x"))
                    .thenReturn(Optional.of(usuarioAdmin));

            assertThatThrownBy(() -> authService.restablecerPassword(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ha caducado");

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("token caducado (expiry < ahora) → IllegalArgumentException")
        void tokenCaducadoRechazado() {
            PasswordResetRequest request = nuevoResetRequest("token-x", "newPass123");
            usuarioAdmin.setResetToken("token-x");
            // Expiro a las 11:59, ahora son las 12:00 → caducado
            usuarioAdmin.setResetTokenExpiry(AHORA.minusMinutes(1));

            when(usuarioRepository.findByResetToken("token-x"))
                    .thenReturn(Optional.of(usuarioAdmin));

            assertThatThrownBy(() -> authService.restablecerPassword(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ha caducado");

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("token vigente (expiry > ahora) → hashea, guarda, invalida token, mensaje ok")
        void tokenVigenteRestablecePassword() {
            // Flujo v2.0 cubierto por el codigo aunque en v1 nadie escribe
            // resetToken: si en el futuro se activa, este path tiene que
            // funcionar.
            PasswordResetRequest request = nuevoResetRequest("token-x", "newPass123");
            usuarioAdmin.setResetToken("token-x");
            usuarioAdmin.setResetTokenExpiry(AHORA.plusMinutes(30));

            when(usuarioRepository.findByResetToken("token-x"))
                    .thenReturn(Optional.of(usuarioAdmin));
            when(passwordEncoder.encode("newPass123")).thenReturn("$2a$10$hashNuevo");

            MensajeResponse response = authService.restablecerPassword(request);

            assertThat(response.getMensaje()).contains("restablecida correctamente");

            // Capturar el Usuario que se guarda y verificar las tres
            // invariantes post-reset:
            //   1. passwordHash = hash nuevo
            //   2. resetToken = null (invalidado tras primer uso, RNF-S04)
            //   3. resetTokenExpiry = null
            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            Usuario guardado = captor.getValue();
            assertThat(guardado.getPasswordHash()).isEqualTo("$2a$10$hashNuevo");
            assertThat(guardado.getResetToken()).isNull();
            assertThat(guardado.getResetTokenExpiry()).isNull();
        }

        @Test
        @DisplayName("token con expiry EXACTAMENTE igual a ahora → no caducado (frontera)")
        void tokenExactamenteEnLaFronteraNoEsCaducado() {
            // isAfter() es estricto: ahora == expiry no es "despues de
            // expiry", por lo que el token todavia es valido en ese
            // instante exacto. Esta es la rama de borde (off-by-one).
            PasswordResetRequest request = nuevoResetRequest("token-x", "newPass123");
            usuarioAdmin.setResetToken("token-x");
            usuarioAdmin.setResetTokenExpiry(AHORA);

            when(usuarioRepository.findByResetToken("token-x"))
                    .thenReturn(Optional.of(usuarioAdmin));
            when(passwordEncoder.encode("newPass123")).thenReturn("$2a$10$hashFrontera");

            MensajeResponse response = authService.restablecerPassword(request);

            assertThat(response.getMensaje()).contains("restablecida correctamente");
            verify(usuarioRepository).save(any(Usuario.class));
        }
    }

    // =================================================================
    // Factorias de DTOs de petición
    // =================================================================

    private LoginRequest nuevoLoginRequest(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private PasswordChangeRequest nuevoChangeRequest(String actual, String nueva) {
        PasswordChangeRequest req = new PasswordChangeRequest();
        req.setPasswordActual(actual);
        req.setPasswordNueva(nueva);
        return req;
    }

    private PasswordRecoveryRequest nuevoRecoveryRequest(String email) {
        PasswordRecoveryRequest req = new PasswordRecoveryRequest();
        req.setEmail(email);
        return req;
    }

    private PasswordResetRequest nuevoResetRequest(String token, String passwordNueva) {
        PasswordResetRequest req = new PasswordResetRequest();
        req.setToken(token);
        req.setPasswordNueva(passwordNueva);
        return req;
    }
}
