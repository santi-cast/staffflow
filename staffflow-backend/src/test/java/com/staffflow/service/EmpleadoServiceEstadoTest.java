package com.staffflow.service;

import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.response.ParteDiarioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de EmpleadoService.obtenerEstado() (E19 GET /empleados/estado).
 *
 * E19 es un endpoint puramente delegativo: pasa la fecha tal cual a
 * {@link PresenciaService#obtenerParteDiario(LocalDate)}, que ya implementa
 * la lógica completa de clasificación de empleados por EstadoPresencia. La
 * cobertura funcional vive en {@code PresenciaServiceTest}; aquí solo se
 * verifica:
 * <ul>
 *   <li>el service delega en PresenciaService.obtenerParteDiario,</li>
 *   <li>pasa la fecha sin modificarla,</li>
 *   <li>devuelve la misma respuesta que produzca el colaborador.</li>
 * </ul>
 *
 * NO se toca el repositorio en este endpoint (no se invoca).
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmpleadoService — obtenerEstado (E19) — delegación en PresenciaService")
class EmpleadoServiceEstadoTest {

    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PresenciaService presenciaService;
    @Mock private PdfService pdfService;

    private EmpleadoService empleadoService;

    /** Reloj fijo (no consumido por E19; exigido por el constructor del SUT). */
    private static final Clock CLOCK_FIJO = Clock.fixed(
            LocalDate.of(2026, 1, 15).atStartOfDay(ZoneId.of("Europe/Madrid")).toInstant(),
            ZoneId.of("Europe/Madrid"));

    @BeforeEach
    void setUp() {
        empleadoService = new EmpleadoService(
                empleadoRepository, usuarioRepository, presenciaService, pdfService, CLOCK_FIJO);
    }

    @Test
    @DisplayName("obtenerEstado — delega en PresenciaService.obtenerParteDiario con la misma fecha")
    void obtenerEstado_delegaEnPresenciaService() {
        LocalDate fecha = LocalDate.of(2026, 3, 10);
        ParteDiarioResponse esperado = new ParteDiarioResponse();
        esperado.setFecha(fecha);
        esperado.setDetalle(Collections.emptyList());

        when(presenciaService.obtenerParteDiario(fecha)).thenReturn(esperado);

        ParteDiarioResponse resultado = empleadoService.obtenerEstado(fecha);

        // Misma instancia devuelta por el colaborador (sin transformacion intermedia).
        assertThat(resultado).isSameAs(esperado);
        verify(presenciaService).obtenerParteDiario(fecha);
        verifyNoInteractions(empleadoRepository);
    }
}
