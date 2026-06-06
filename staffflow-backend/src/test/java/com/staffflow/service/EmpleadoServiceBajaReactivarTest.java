package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.response.MensajeResponse;
import com.staffflow.exception.ConflictException;
import com.staffflow.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de EmpleadoService.darDeBaja() (E17 PATCH /{id}/baja) y
 * EmpleadoService.reactivar() (E18 PATCH /{id}/reactivar).
 *
 * Ambos endpoints aplican baja lógica/reactivación cambiando el flag
 * {@code activo} de la entidad. El historial de fichajes, pausas, saldos
 * y ausencias queda intacto en BD.
 *
 * Cobertura:
 * <ul>
 *   <li>E17 darDeBaja:
 *     <ul>
 *       <li>caso feliz: empleado activo -> {@code activo=false} + mensaje.</li>
 *       <li>404 si el empleado no existe.</li>
 *       <li>idempotencia operativa: empleado ya inactivo -> persiste igual
 *         (no se valida estado previo, comportamiento por diseño).</li>
 *     </ul>
 *   </li>
 *   <li>E18 reactivar:
 *     <ul>
 *       <li>caso feliz: empleado inactivo -> {@code activo=true} + mensaje.</li>
 *       <li>404 si el empleado no existe.</li>
 *       <li>409 ConflictException si ya estaba activo (anti-doble-clic).</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmpleadoService — darDeBaja (E17) y reactivar (E18)")
class EmpleadoServiceBajaReactivarTest {

    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PresenciaService presenciaService;
    @Mock private PdfService pdfService;

    private EmpleadoService empleadoService;

    private static final long EMPLEADO_ID = 1L;

    /** Reloj fijo (no consumido por E17/E18; exigido por el constructor del SUT). */
    private static final Clock CLOCK_FIJO = Clock.fixed(
            LocalDate.of(2026, 1, 15).atStartOfDay(ZoneId.of("Europe/Madrid")).toInstant(),
            ZoneId.of("Europe/Madrid"));

    @BeforeEach
    void setUp() {
        empleadoService = new EmpleadoService(
                empleadoRepository, usuarioRepository, presenciaService, pdfService, CLOCK_FIJO);
    }

    private Empleado empleadoActivo(boolean activo) {
        Empleado e = new Empleado();
        e.setId(EMPLEADO_ID);
        e.setActivo(activo);
        return e;
    }

    // ---------------------------------------------------------------
    // E17 — darDeBaja
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("darDeBaja (E17)")
    class DarDeBaja {

        @Test
        @DisplayName("empleado activo — persiste activo=false y devuelve mensaje confirmando")
        void darDeBaja_empleadoActivo_persisteInactivo() {
            when(empleadoRepository.findById(EMPLEADO_ID))
                    .thenReturn(Optional.of(empleadoActivo(true)));
            when(empleadoRepository.save(any(Empleado.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            MensajeResponse response = empleadoService.darDeBaja(EMPLEADO_ID);

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isFalse();
            assertThat(response.getMensaje()).contains("desactivado");
        }

        @Test
        @DisplayName("empleado inexistente — NotFoundException 404 (no persiste)")
        void darDeBaja_inexistente_lanzaNotFound() {
            when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.darDeBaja(EMPLEADO_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(EMPLEADO_ID));

            verify(empleadoRepository, never()).save(any(Empleado.class));
        }

        @Test
        @DisplayName("empleado ya inactivo — persiste igual (no valida estado previo, comportamiento por diseño)")
        void darDeBaja_yaInactivo_persisteIgual() {
            // El service NO valida si ya estaba inactivo (a diferencia de reactivar()).
            // Lo dejamos documentado como comportamiento idempotente operativo.
            when(empleadoRepository.findById(EMPLEADO_ID))
                    .thenReturn(Optional.of(empleadoActivo(false)));
            when(empleadoRepository.save(any(Empleado.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            empleadoService.darDeBaja(EMPLEADO_ID);

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isFalse();
        }
    }

    // ---------------------------------------------------------------
    // E18 — reactivar
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("reactivar (E18)")
    class Reactivar {

        @Test
        @DisplayName("empleado inactivo — persiste activo=true y devuelve mensaje confirmando")
        void reactivar_empleadoInactivo_persisteActivo() {
            when(empleadoRepository.findById(EMPLEADO_ID))
                    .thenReturn(Optional.of(empleadoActivo(false)));
            when(empleadoRepository.save(any(Empleado.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            MensajeResponse response = empleadoService.reactivar(EMPLEADO_ID);

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isTrue();
            assertThat(response.getMensaje()).contains("reactivado");
        }

        @Test
        @DisplayName("empleado ya activo — ConflictException 409 (anti-doble-clic, no persiste)")
        void reactivar_yaActivo_lanzaConflict() {
            when(empleadoRepository.findById(EMPLEADO_ID))
                    .thenReturn(Optional.of(empleadoActivo(true)));

            assertThatThrownBy(() -> empleadoService.reactivar(EMPLEADO_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(String.valueOf(EMPLEADO_ID));

            verify(empleadoRepository, never()).save(any(Empleado.class));
        }

        @Test
        @DisplayName("empleado inexistente — NotFoundException 404 (no persiste)")
        void reactivar_inexistente_lanzaNotFound() {
            when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.reactivar(EMPLEADO_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(EMPLEADO_ID));

            verify(empleadoRepository, never()).save(any(Empleado.class));
        }
    }
}
