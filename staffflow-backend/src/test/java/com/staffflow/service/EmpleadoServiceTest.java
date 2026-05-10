package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.response.RegenerarPinResponse;
import com.staffflow.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
