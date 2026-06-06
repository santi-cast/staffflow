package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.response.EmpleadoResponse;
import com.staffflow.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de:
 * <ul>
 *   <li>{@code obtenerMiPerfil(String username)} — E21 GET /empleados/me.</li>
 *   <li>{@code obtenerPorUsuarioId(Long usuarioId)} — E68 GET /empleados/by-usuario/{id}.</li>
 * </ul>
 *
 * Ambos endpoints resuelven un empleado a partir del usuario asociado, pero
 * por vías distintas: E21 traduce username -> usuario -> empleado (dos lookups
 * encadenados, ambos pueden devolver 404); E68 recibe el usuarioId
 * directamente y hace un solo lookup. Ninguno expone pinTerminal, email,
 * username ni rol en el response (Opción A solo aplica a E15).
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmpleadoService — obtenerMiPerfil (E21) y obtenerPorUsuarioId (E68)")
class EmpleadoServiceMePerfilTest {

    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PresenciaService presenciaService;
    @Mock private PdfService pdfService;

    private EmpleadoService empleadoService;

    private static final long USUARIO_ID = 10L;
    private static final String USERNAME = "jperez";

    /** Reloj fijo (no consumido por E21/E68; exigido por el constructor del SUT). */
    private static final Clock CLOCK_FIJO = Clock.fixed(
            LocalDate.of(2026, 1, 15).atStartOfDay(ZoneId.of("Europe/Madrid")).toInstant(),
            ZoneId.of("Europe/Madrid"));

    @BeforeEach
    void setUp() {
        empleadoService = new EmpleadoService(
                empleadoRepository, usuarioRepository, presenciaService, pdfService, CLOCK_FIJO);
    }

    private Usuario usuarioConPerfil() {
        Usuario u = new Usuario();
        u.setId(USUARIO_ID);
        u.setUsername(USERNAME);
        u.setEmail("jperez@staffflow.local");
        return u;
    }

    private Empleado empleadoVinculado() {
        Empleado e = new Empleado();
        e.setId(1L);
        e.setUsuario(usuarioConPerfil());
        e.setNombre("Juan");
        e.setApellido1("Pérez");
        e.setPinTerminal("1234");
        return e;
    }

    // ---------------------------------------------------------------
    // E21 — obtenerMiPerfil(username)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("obtenerMiPerfil (E21)")
    class ObtenerMiPerfil {

        @Test
        @DisplayName("username válido — devuelve perfil propio SIN pinTerminal")
        void obtenerMiPerfil_usernameValido_devuelvePerfilSinPin() {
            when(usuarioRepository.findByUsername(USERNAME))
                    .thenReturn(Optional.of(usuarioConPerfil()));
            when(empleadoRepository.findByUsuarioId(USUARIO_ID))
                    .thenReturn(Optional.of(empleadoVinculado()));

            EmpleadoResponse response = empleadoService.obtenerMiPerfil(USERNAME);

            assertThat(response.getNombre()).isEqualTo("Juan");
            // /me NUNCA expone pinTerminal (defensa en profundidad: el empleado
            // ya conoce su PIN, no hay motivo para devolverlo por API).
            assertThat(response.getPinTerminal()).isNull();
        }

        @Test
        @DisplayName("username inexistente — NotFoundException (mensaje menciona username)")
        void obtenerMiPerfil_usernameInexistente_lanzaNotFound() {
            when(usuarioRepository.findByUsername("desconocido")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.obtenerMiPerfil("desconocido"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("desconocido");

            verifyNoInteractions(empleadoRepository);
        }

        @Test
        @DisplayName("usuario sin perfil de empleado — NotFoundException (caso ADMIN puro)")
        void obtenerMiPerfil_usuarioSinPerfil_lanzaNotFound() {
            // Caso típico: rol ADMIN no tiene perfil de empleado vinculado.
            // E21 está bloqueado a ADMIN por @PreAuthorize en el controller,
            // pero si llegase aquí, el service responderia 404 igualmente.
            when(usuarioRepository.findByUsername(USERNAME))
                    .thenReturn(Optional.of(usuarioConPerfil()));
            when(empleadoRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.obtenerMiPerfil(USERNAME))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(USERNAME);
        }
    }

    // ---------------------------------------------------------------
    // E68 — obtenerPorUsuarioId(usuarioId)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("obtenerPorUsuarioId (E68)")
    class ObtenerPorUsuarioId {

        @Test
        @DisplayName("usuarioId válido — devuelve perfil SIN datos sensibles (Opción A solo aplica a E15)")
        void obtenerPorUsuarioId_valido_devuelveSinDatosSensibles() {
            when(empleadoRepository.findByUsuarioId(USUARIO_ID))
                    .thenReturn(Optional.of(empleadoVinculado()));

            EmpleadoResponse response = empleadoService.obtenerPorUsuarioId(USUARIO_ID);

            assertThat(response.getNombre()).isEqualTo("Juan");
            // Verificacion explicita: cabecera de P29 NO necesita los 4 campos
            // sensibles, asi que el response "limpio" los deja todos a null.
            assertThat(response.getPinTerminal()).isNull();
            assertThat(response.getEmail()).isNull();
            assertThat(response.getUsername()).isNull();
            assertThat(response.getRol()).isNull();
            // El usuarioId si se rellena porque viene del Empleado.usuario.
            assertThat(response.getUsuarioId()).isEqualTo(USUARIO_ID);
        }

        @Test
        @DisplayName("usuarioId sin empleado vinculado — NotFoundException")
        void obtenerPorUsuarioId_sinVinculo_lanzaNotFound() {
            when(empleadoRepository.findByUsuarioId(99999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.obtenerPorUsuarioId(99999L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("99999");

            verifyNoInteractions(usuarioRepository);
        }
    }
}
