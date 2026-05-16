package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.response.EmpleadoResponse;
import com.staffflow.dto.response.RegenerarPinResponse;
import com.staffflow.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de EmpleadoService.
 *
 * Verifica la lógica de regeneración de PIN de terminal (E65):
 *   - Happy path: el PIN se genera, persiste y devuelve correctamente.
 *   - Not found: lanza NotFoundException si el empleado no existe.
 *   - Formato: el PIN devuelto es una cadena de exactamente 4 dígitos numéricos.
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmpleadoService — regenerarPin (E65)")
class EmpleadoServiceTest {

    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PresenciaService presenciaService;
    @Mock private PdfService pdfService;

    @InjectMocks
    private EmpleadoService empleadoService;

    private static final long EMPLEADO_ID = 1L;

    private Empleado empleado;

    @BeforeEach
    void setUp() {
        empleado = new Empleado();
        empleado.setId(EMPLEADO_ID);
        empleado.setPinTerminal("0000");
    }

    // ---------------------------------------------------------------
    // regenerarPin — happy path
    // ---------------------------------------------------------------

    @Test
    @DisplayName("regenerarPin — empleado existente — actualiza PIN y devuelve response con mismo valor")
    void regenerarPin_actualizaYDevuelvePinNuevo() {
        // Arrange: el empleado existe y el PIN generado es único
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(empleadoRepository.existsByPinTerminal(anyString())).thenReturn(false);
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RegenerarPinResponse response = empleadoService.regenerarPin(EMPLEADO_ID);

        // Assert: el PIN del response coincide con el persistido en el empleado capturado
        ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
        verify(empleadoRepository).save(captor.capture());

        Empleado empleadoGuardado = captor.getValue();
        assertThat(response.getPinTerminal())
                .isNotNull()
                .isEqualTo(empleadoGuardado.getPinTerminal());
        assertThat(response.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
    }

    // ---------------------------------------------------------------
    // regenerarPin — not found
    // ---------------------------------------------------------------

    @Test
    @DisplayName("regenerarPin — empleado inexistente — lanza NotFoundException")
    void regenerarPin_idInexistente_lanzaNotFoundException() {
        // Arrange
        when(empleadoRepository.findById(99999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> empleadoService.regenerarPin(99999L))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------
    // regenerarPin — formato del PIN
    // ---------------------------------------------------------------

    @Test
    @DisplayName("regenerarPin — PIN devuelto tiene exactamente 4 dígitos numéricos")
    void regenerarPin_pinDevueltoTieneCuatroDigitos() {
        // Arrange
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(empleadoRepository.existsByPinTerminal(anyString())).thenReturn(false);
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RegenerarPinResponse response = empleadoService.regenerarPin(EMPLEADO_ID);

        // Assert: exactamente 4 dígitos numéricos
        assertThat(response.getPinTerminal())
                .isNotNull()
                .matches("\\d{4}");
    }

    // ---------------------------------------------------------------
    // obtenerPorId (E15) — Opción A: PIN y email solo a ADMIN
    // ---------------------------------------------------------------

    /**
     * Cubre el contrato E15 (GET /api/v1/empleados/{id}) tras aplicar la
     * Opción A: ADMIN recibe pinTerminal y email con valor; ENCARGADO los
     * recibe a null. El UI Android ya asume este comportamiento al
     * renderizar el detalle del empleado.
     */
    @Nested
    @DisplayName("obtenerPorId (E15) — filtrado de PIN y email por rol")
    class ObtenerPorId {

        private static final String PIN = "1234";
        private static final String EMAIL = "empleado@staffflow.local";

        private Empleado empleadoConDatosSensibles() {
            Usuario usuario = new Usuario();
            usuario.setId(10L);
            usuario.setEmail(EMAIL);

            Empleado emp = new Empleado();
            emp.setId(EMPLEADO_ID);
            emp.setPinTerminal(PIN);
            emp.setUsuario(usuario);
            return emp;
        }

        private Authentication authConRol(String rol) {
            return new UsernamePasswordAuthenticationToken(
                    "test-user",
                    "n/a",
                    List.of(new SimpleGrantedAuthority(rol)));
        }

        @Test
        @DisplayName("ADMIN — recibe pinTerminal y email con valor real")
        void obtenerPorId_admin_devuelvePinYEmail() {
            // Arrange
            when(empleadoRepository.findById(EMPLEADO_ID))
                    .thenReturn(Optional.of(empleadoConDatosSensibles()));

            // Act
            EmpleadoResponse response = empleadoService.obtenerPorId(
                    EMPLEADO_ID, authConRol("ROLE_ADMIN"));

            // Assert
            assertThat(response.getPinTerminal()).isEqualTo(PIN);
            assertThat(response.getEmail()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("ENCARGADO — recibe pinTerminal y email a null (Opción A)")
        void obtenerPorId_encargado_devuelvePinYEmailNull() {
            // Arrange
            when(empleadoRepository.findById(EMPLEADO_ID))
                    .thenReturn(Optional.of(empleadoConDatosSensibles()));

            // Act
            EmpleadoResponse response = empleadoService.obtenerPorId(
                    EMPLEADO_ID, authConRol("ROLE_ENCARGADO"));

            // Assert
            assertThat(response.getPinTerminal()).isNull();
            assertThat(response.getEmail()).isNull();
            // El resto del DTO sigue rellenándose con normalidad
            assertThat(response.getId()).isEqualTo(EMPLEADO_ID);
        }

        @Test
        @DisplayName("Empleado inexistente — lanza NotFoundException independientemente del rol")
        void obtenerPorId_idInexistente_lanzaNotFoundException() {
            // Arrange
            when(empleadoRepository.findById(99999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> empleadoService.obtenerPorId(
                    99999L, authConRol("ROLE_ADMIN")))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
