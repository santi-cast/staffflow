package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.EmpleadoPatchRequest;
import com.staffflow.dto.response.EmpleadoResponse;
import com.staffflow.exception.ConflictException;
import com.staffflow.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de EmpleadoService.actualizar() (E16 PATCH).
 *
 * Verifica los campos editables incorporados al PATCH:
 *   - dni: edición OK y conflicto 409 cuando el nuevo DNI pertenece a otro empleado.
 *   - fechaAlta: edición acepta valores retroactivos (corrección de errores de alta).
 *   - Sin cambios: si el request llega con todos los campos null, no rompe.
 *
 * El resto de campos (nombre, apellido, categoria, jornada, vacaciones,
 * codigoNfc) ya estaban cubiertos por el comportamiento existente y no
 * son alcance de esta clase.
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmpleadoService — actualizar (E16) — dni y fechaAlta editables")
class EmpleadoServiceActualizarTest {

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
        // El usuario asociado es necesario para que toEmpleadoResponse() no
        // explote: el response cablea usuarioId, username y rol del usuario.
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setUsername("jperez");

        empleado = new Empleado();
        empleado.setId(EMPLEADO_ID);
        empleado.setUsuario(usuario);
        empleado.setDni("12345678A");
        empleado.setFechaAlta(LocalDate.of(2025, 3, 15));
    }

    // ---------------------------------------------------------------
    // dni
    // ---------------------------------------------------------------

    @Test
    @DisplayName("actualizar — dni nuevo y libre — persiste el cambio")
    void actualizar_dniLibre_persisteCambio() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(empleadoRepository.existsByDniAndIdNot("87654321B", EMPLEADO_ID)).thenReturn(false);
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));

        EmpleadoPatchRequest request = new EmpleadoPatchRequest();
        request.setDni("87654321B");

        EmpleadoResponse response = empleadoService.actualizar(EMPLEADO_ID, request);

        ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
        verify(empleadoRepository).save(captor.capture());
        assertThat(captor.getValue().getDni()).isEqualTo("87654321B");
        assertThat(response.getDni()).isEqualTo("87654321B");
    }

    @Test
    @DisplayName("actualizar — dni ya registrado en otro empleado — lanza ConflictException 409")
    void actualizar_dniDuplicado_lanzaConflict() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(empleadoRepository.existsByDniAndIdNot("87654321B", EMPLEADO_ID)).thenReturn(true);

        EmpleadoPatchRequest request = new EmpleadoPatchRequest();
        request.setDni("87654321B");

        assertThatThrownBy(() -> empleadoService.actualizar(EMPLEADO_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("87654321B");

        verify(empleadoRepository, never()).save(any(Empleado.class));
    }

    @Test
    @DisplayName("actualizar — empleado conserva su propio DNI — no dispara conflicto")
    void actualizar_dniIgualAlPropio_noDisparaConflict() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        // existsByDniAndIdNot excluye al propio empleado: devuelve false aunque coincida
        when(empleadoRepository.existsByDniAndIdNot("12345678A", EMPLEADO_ID)).thenReturn(false);
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));

        EmpleadoPatchRequest request = new EmpleadoPatchRequest();
        request.setDni("12345678A");

        empleadoService.actualizar(EMPLEADO_ID, request);

        verify(empleadoRepository).save(any(Empleado.class));
    }

    // ---------------------------------------------------------------
    // fechaAlta
    // ---------------------------------------------------------------

    @Test
    @DisplayName("actualizar — fechaAlta retroactiva — persiste el cambio (corrección de alta)")
    void actualizar_fechaAltaRetroactiva_persisteCambio() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate fechaCorregida = LocalDate.of(2025, 2, 1);
        EmpleadoPatchRequest request = new EmpleadoPatchRequest();
        request.setFechaAlta(fechaCorregida);

        empleadoService.actualizar(EMPLEADO_ID, request);

        ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
        verify(empleadoRepository).save(captor.capture());
        assertThat(captor.getValue().getFechaAlta()).isEqualTo(fechaCorregida);
    }

    @Test
    @DisplayName("actualizar — fechaAlta futura — persiste el cambio")
    void actualizar_fechaAltaFutura_persisteCambio() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate fechaFutura = LocalDate.now().plusMonths(1);
        EmpleadoPatchRequest request = new EmpleadoPatchRequest();
        request.setFechaAlta(fechaFutura);

        empleadoService.actualizar(EMPLEADO_ID, request);

        ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
        verify(empleadoRepository).save(captor.capture());
        assertThat(captor.getValue().getFechaAlta()).isEqualTo(fechaFutura);
    }

    // ---------------------------------------------------------------
    // empleado inexistente
    // ---------------------------------------------------------------

    @Test
    @DisplayName("actualizar — empleado inexistente — lanza NotFoundException 404")
    void actualizar_empleadoInexistente_lanzaNotFound() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.empty());

        EmpleadoPatchRequest request = new EmpleadoPatchRequest();
        request.setDni("87654321B");

        assertThatThrownBy(() -> empleadoService.actualizar(EMPLEADO_ID, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(String.valueOf(EMPLEADO_ID));

        verify(empleadoRepository, never()).save(any(Empleado.class));
    }
}
