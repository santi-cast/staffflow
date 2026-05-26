package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.PlanificacionAusencia;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.enums.TipoAusencia;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.AusenciaPatchRequest;
import com.staffflow.dto.request.AusenciaRangoRequest;
import com.staffflow.dto.request.AusenciaRequest;
import com.staffflow.dto.response.AusenciaResponse;
import com.staffflow.exception.ConflictException;
import com.staffflow.exception.NotFoundException;
import com.staffflow.exception.RangoConflictException;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de AusenciaService — cubre las ramas dependientes de
 * fecha y rol de los endpoints CRUD del servicio (E30, E31, E32, E33, E63).
 *
 * <p>Estrategia: Mockito puro sin contexto Spring, inyectando un
 * {@code Clock.fixed(...)} para volver deterministas las comprobaciones
 * de fecha y restriccion del ENCARGADO. Mismo patron que
 * {@link FichajeServiceTest}, {@link PausaServiceTest} y
 * {@link com.staffflow.service.scheduled.ProcesoCierreDiarioTest}.</p>
 *
 * <p>Los tests de lectura E34 (listarMias) y E64 (getPlanificacionVacAp)
 * viven en un commit posterior para mantener este archivo enfocado en
 * el CRUD.</p>
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AusenciaService — E30/E31/E32/E33/E63 (CRUD)")
class AusenciaServiceTest {

    @Mock private PlanificacionAusenciaRepository ausenciaRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SaldoAnualRepository saldoAnualRepository;

    // Fecha fija para tests deterministas: jueves 15 de enero de 2026.
    // "ayer" = 14/01, "hoy" = 15/01, "manana" = 16/01.
    private static final LocalDate HOY = LocalDate.of(2026, 1, 15);
    private static final LocalDate AYER = LocalDate.of(2026, 1, 14);
    private static final LocalDate MANANA = LocalDate.of(2026, 1, 16);
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private Empleado empleado;
    private Usuario usuarioAdmin;
    private Usuario usuarioEncargado;

    /**
     * Construye un {@link AusenciaService} con el Clock fijado al dia indicado.
     * Cada test pasa la fecha que necesita como "hoy".
     */
    private AusenciaService nuevoServiceConFecha(LocalDate fechaHoy) {
        Clock clock = Clock.fixed(
                fechaHoy.atStartOfDay(ZONA).toInstant(), ZONA);
        return new AusenciaService(
                ausenciaRepository,
                empleadoRepository,
                usuarioRepository,
                saldoAnualRepository,
                clock);
    }

    @BeforeEach
    void setUp() {
        empleado = new Empleado();
        empleado.setId(10L);
        empleado.setNombre("Carlos");
        empleado.setApellido1("Lopez");
        empleado.setApellido2("Garcia");

        usuarioAdmin = new Usuario();
        usuarioAdmin.setId(1L);
        usuarioAdmin.setUsername("admin");
        usuarioAdmin.setRol(Rol.ADMIN);

        usuarioEncargado = new Usuario();
        usuarioEncargado.setId(2L);
        usuarioEncargado.setUsername("encargado");
        usuarioEncargado.setRol(Rol.ENCARGADO);
    }

    // =================================================================
    // E30 — crear()
    // =================================================================

    @Nested
    @DisplayName("crear (E30) — planificar ausencia individual o festivo global")
    class CrearTests {

        @Test
        @DisplayName("usuario autenticado no existe → NotFoundException (404)")
        void usuarioInexistenteLanzaNotFound() {
            AusenciaRequest request = nuevoRequest(10L, HOY, TipoAusencia.VACACIONES);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AusenciaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Usuario no encontrado");

            verify(ausenciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("ENCARGADO + fecha pasada → IllegalArgumentException (400)")
        void encargadoConFechaPasadaRechazado() {
            AusenciaRequest request = nuevoRequest(10L, AYER, TipoAusencia.VACACIONES);
            when(usuarioRepository.findByUsername("encargado"))
                    .thenReturn(Optional.of(usuarioEncargado));

            AusenciaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "encargado"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ENCARGADO solo puede gestionar");

            verify(ausenciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("ENCARGADO + HOY → permitido")
        void encargadoConHoyPermitido() {
            AusenciaRequest request = nuevoRequest(10L, HOY, TipoAusencia.VACACIONES);
            when(usuarioRepository.findByUsername("encargado"))
                    .thenReturn(Optional.of(usuarioEncargado));
            when(ausenciaRepository.existsByEmpleadoIdAndFecha(10L, HOY))
                    .thenReturn(false);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(ausenciaRepository.save(any(PlanificacionAusencia.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaResponse response = service.crear(request, "encargado");

            assertThat(response).isNotNull();
            assertThat(response.getFecha()).isEqualTo(HOY);
            assertThat(response.getEmpleadoId()).isEqualTo(10L);
            verify(ausenciaRepository).save(any(PlanificacionAusencia.class));
        }

        @Test
        @DisplayName("ENCARGADO + fecha futura → permitido")
        void encargadoConFechaFuturaPermitido() {
            AusenciaRequest request = nuevoRequest(10L, MANANA, TipoAusencia.VACACIONES);
            when(usuarioRepository.findByUsername("encargado"))
                    .thenReturn(Optional.of(usuarioEncargado));
            when(ausenciaRepository.existsByEmpleadoIdAndFecha(10L, MANANA))
                    .thenReturn(false);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(ausenciaRepository.save(any(PlanificacionAusencia.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaResponse response = service.crear(request, "encargado");

            assertThat(response.getFecha()).isEqualTo(MANANA);
            verify(ausenciaRepository).save(any(PlanificacionAusencia.class));
        }

        @Test
        @DisplayName("ADMIN + fecha pasada → permitido (sin restriccion de fecha)")
        void adminConFechaPasadaPermitido() {
            AusenciaRequest request = nuevoRequest(10L, AYER, TipoAusencia.PERMISO_RETRIBUIDO);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(ausenciaRepository.existsByEmpleadoIdAndFecha(10L, AYER))
                    .thenReturn(false);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(ausenciaRepository.save(any(PlanificacionAusencia.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaResponse response = service.crear(request, "admin");

            assertThat(response.getFecha()).isEqualTo(AYER);
            verify(ausenciaRepository).save(any(PlanificacionAusencia.class));
        }

        @Test
        @DisplayName("ya existe ausencia empleado+fecha → ConflictException (409)")
        void conflictoUnicidadEmpleadoFecha() {
            AusenciaRequest request = nuevoRequest(10L, HOY, TipoAusencia.VACACIONES);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(ausenciaRepository.existsByEmpleadoIdAndFecha(10L, HOY))
                    .thenReturn(true);

            AusenciaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Ya existe una ausencia planificada");

            verify(ausenciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("empleado inexistente → NotFoundException (404)")
        void empleadoInexistenteLanzaNotFound() {
            AusenciaRequest request = nuevoRequest(99L, HOY, TipoAusencia.VACACIONES);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(ausenciaRepository.existsByEmpleadoIdAndFecha(99L, HOY)).thenReturn(false);
            when(empleadoRepository.findById(99L)).thenReturn(Optional.empty());

            AusenciaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Empleado no encontrado");

            verify(ausenciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("empleadoId null → festivo global (RF-26), no se consulta empleadoRepository")
        void festivoGlobalNoConsultaEmpleado() {
            AusenciaRequest request = nuevoRequest(null, HOY, TipoAusencia.FESTIVO_LOCAL);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(ausenciaRepository.existsByEmpleadoIdAndFecha(null, HOY)).thenReturn(false);

            ArgumentCaptor<PlanificacionAusencia> captor =
                    ArgumentCaptor.forClass(PlanificacionAusencia.class);
            when(ausenciaRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaResponse response = service.crear(request, "admin");

            assertThat(response.getEmpleadoId()).isNull();
            assertThat(captor.getValue().getEmpleado()).isNull();
            assertThat(captor.getValue().getProcesado()).isFalse();
            verify(empleadoRepository, never()).findById(any());
        }
    }

    // =================================================================
    // E63 — crearRango()
    // =================================================================

    @Nested
    @DisplayName("crearRango (E63) — planificacion de rango consecutivo")
    class CrearRangoTests {

        @Test
        @DisplayName("fechaDesde posterior a fechaHasta → IllegalArgumentException (400)")
        void rangoInvertidoRechazado() {
            AusenciaRangoRequest request = nuevoRangoRequest(
                    10L, MANANA, HOY, TipoAusencia.VACACIONES, false);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));

            AusenciaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crearRango(request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fechaDesde no puede ser posterior");

            verify(ausenciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("ENCARGADO + fechaDesde en el pasado → IllegalArgumentException (400)")
        void encargadoRangoEnPasadoRechazado() {
            AusenciaRangoRequest request = nuevoRangoRequest(
                    10L, AYER, MANANA, TipoAusencia.VACACIONES, false);
            when(usuarioRepository.findByUsername("encargado"))
                    .thenReturn(Optional.of(usuarioEncargado));

            AusenciaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crearRango(request, "encargado"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ENCARGADO solo puede gestionar");

            verify(ausenciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("algun dia con procesado=true → IllegalArgumentException (no se sobrescribe fichaje)")
        void diaProcesadoEnRangoRechazado() {
            AusenciaRangoRequest request = nuevoRangoRequest(
                    10L, HOY, MANANA, TipoAusencia.VACACIONES, false);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));

            PlanificacionAusencia procesada = nuevaAusenciaPersistida(1L, HOY, true);
            when(ausenciaRepository.findByEmpleadoIdAndFechaBetween(10L, HOY, MANANA))
                    .thenReturn(List.of(procesada));

            AusenciaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crearRango(request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ya tienen un fichaje generado");

            verify(ausenciaRepository, never()).save(any());
            verify(ausenciaRepository, never()).deleteAll(anyList());
        }

        @Test
        @DisplayName("conflicto procesado=false + sobrescribir=false → RangoConflictException con fechas")
        void conflictoSinSobrescribirLanzaRangoConflict() {
            AusenciaRangoRequest request = nuevoRangoRequest(
                    10L, HOY, MANANA, TipoAusencia.VACACIONES, false);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));

            PlanificacionAusencia existente = nuevaAusenciaPersistida(1L, HOY, false);
            when(ausenciaRepository.findByEmpleadoIdAndFechaBetween(10L, HOY, MANANA))
                    .thenReturn(List.of(existente));

            AusenciaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crearRango(request, "admin"))
                    .isInstanceOf(RangoConflictException.class)
                    .satisfies(ex -> assertThat(
                            ((RangoConflictException) ex).getFechasConflictivas())
                            .containsExactly(HOY));

            verify(ausenciaRepository, never()).save(any());
            verify(ausenciaRepository, never()).deleteAll(anyList());
        }

        @Test
        @DisplayName("conflicto procesado=false + sobrescribir=true → deleteAll + save por dia")
        void conflictoConSobrescribirEliminaYRecrea() {
            AusenciaRangoRequest request = nuevoRangoRequest(
                    10L, HOY, MANANA, TipoAusencia.VACACIONES, true);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));

            PlanificacionAusencia existente = nuevaAusenciaPersistida(1L, HOY, false);
            when(ausenciaRepository.findByEmpleadoIdAndFechaBetween(10L, HOY, MANANA))
                    .thenReturn(List.of(existente));
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(ausenciaRepository.save(any(PlanificacionAusencia.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AusenciaService service = nuevoServiceConFecha(HOY);
            List<AusenciaResponse> resultado = service.crearRango(request, "admin");

            // 2 dias (HOY y MANANA) → 2 saves nuevos
            assertThat(resultado).hasSize(2);
            verify(ausenciaRepository).deleteAll(List.of(existente));
            verify(ausenciaRepository, times(2)).save(any(PlanificacionAusencia.class));
        }

        @Test
        @DisplayName("rango limpio de 3 dias → 3 saves consecutivos")
        void rangoLimpioCreaUnRegistroPorDia() {
            LocalDate hasta = HOY.plusDays(2); // HOY, +1, +2 → 3 dias
            AusenciaRangoRequest request = nuevoRangoRequest(
                    10L, HOY, hasta, TipoAusencia.VACACIONES, false);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(ausenciaRepository.findByEmpleadoIdAndFechaBetween(10L, HOY, hasta))
                    .thenReturn(Collections.emptyList());
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));

            ArgumentCaptor<PlanificacionAusencia> captor =
                    ArgumentCaptor.forClass(PlanificacionAusencia.class);
            when(ausenciaRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            AusenciaService service = nuevoServiceConFecha(HOY);
            List<AusenciaResponse> resultado = service.crearRango(request, "admin");

            assertThat(resultado).hasSize(3);
            assertThat(captor.getAllValues())
                    .extracting(PlanificacionAusencia::getFecha)
                    .containsExactly(HOY, HOY.plusDays(1), HOY.plusDays(2));
            verify(ausenciaRepository, never()).deleteAll(anyList());
        }

        @Test
        @DisplayName("empleadoId null → festivo global, no se consulta empleadoRepository")
        void rangoFestivoGlobalNoConsultaEmpleado() {
            AusenciaRangoRequest request = nuevoRangoRequest(
                    null, HOY, MANANA, TipoAusencia.FESTIVO_LOCAL, false);
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(ausenciaRepository.findByEmpleadoIdAndFechaBetween(null, HOY, MANANA))
                    .thenReturn(Collections.emptyList());

            ArgumentCaptor<PlanificacionAusencia> captor =
                    ArgumentCaptor.forClass(PlanificacionAusencia.class);
            when(ausenciaRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            AusenciaService service = nuevoServiceConFecha(HOY);
            List<AusenciaResponse> resultado = service.crearRango(request, "admin");

            assertThat(resultado).hasSize(2);
            assertThat(captor.getAllValues())
                    .allMatch(a -> a.getEmpleado() == null);
            verify(empleadoRepository, never()).findById(any());
        }
    }

    // =================================================================
    // E31 — actualizar()
    // =================================================================

    @Nested
    @DisplayName("actualizar (E31) — modificacion parcial de ausencia planificada")
    class ActualizarTests {

        @Test
        @DisplayName("id inexistente → NotFoundException (404)")
        void idInexistenteLanzaNotFound() {
            when(ausenciaRepository.findById(99L)).thenReturn(Optional.empty());

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaPatchRequest patch = nuevoPatchRequest(TipoAusencia.VACACIONES, null);

            assertThatThrownBy(() -> service.actualizar(99L, patch, "admin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Ausencia no encontrada");

            verify(ausenciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("usuario autenticado no existe → NotFoundException (404)")
        void usuarioInexistenteLanzaNotFound() {
            PlanificacionAusencia ausencia = nuevaAusenciaPersistida(1L, HOY, false);
            when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausencia));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaPatchRequest patch = nuevoPatchRequest(TipoAusencia.VACACIONES, null);

            assertThatThrownBy(() -> service.actualizar(1L, patch, "admin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Usuario no encontrado");

            verify(ausenciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("ENCARGADO + ausencia fecha pasada → IllegalArgumentException (400)")
        void encargadoConAusenciaPasadaRechazado() {
            PlanificacionAusencia ausencia = nuevaAusenciaPersistida(1L, AYER, false);
            when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausencia));
            when(usuarioRepository.findByUsername("encargado"))
                    .thenReturn(Optional.of(usuarioEncargado));

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaPatchRequest patch = nuevoPatchRequest(TipoAusencia.VACACIONES, null);

            assertThatThrownBy(() -> service.actualizar(1L, patch, "encargado"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ENCARGADO solo puede gestionar");

            verify(ausenciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN + ausencia fecha pasada → permitido")
        void adminConAusenciaPasadaPermitido() {
            PlanificacionAusencia ausencia = nuevaAusenciaPersistida(1L, AYER, false);
            ausencia.setTipoAusencia(TipoAusencia.ASUNTO_PROPIO);
            when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausencia));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(ausenciaRepository.save(any(PlanificacionAusencia.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaPatchRequest patch = nuevoPatchRequest(TipoAusencia.VACACIONES, null);

            AusenciaResponse response = service.actualizar(1L, patch, "admin");

            assertThat(response.getTipoAusencia()).isEqualTo(TipoAusencia.VACACIONES);
            verify(ausenciaRepository).save(ausencia);
        }

        @Test
        @DisplayName("procesado=true → ConflictException (409)")
        void ausenciaProcesadaRechazada() {
            PlanificacionAusencia ausencia = nuevaAusenciaPersistida(1L, HOY, true);
            when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausencia));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaPatchRequest patch = nuevoPatchRequest(TipoAusencia.VACACIONES, null);

            assertThatThrownBy(() -> service.actualizar(1L, patch, "admin"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("ya fue procesada");

            verify(ausenciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("PATCH selectivo solo tipoAusencia → observaciones se conservan")
        void patchSoloTipoConservaObservaciones() {
            PlanificacionAusencia ausencia = nuevaAusenciaPersistida(1L, HOY, false);
            ausencia.setTipoAusencia(TipoAusencia.ASUNTO_PROPIO);
            ausencia.setObservaciones("original");

            when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausencia));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(ausenciaRepository.save(any(PlanificacionAusencia.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaPatchRequest patch = nuevoPatchRequest(TipoAusencia.VACACIONES, null);

            service.actualizar(1L, patch, "admin");

            assertThat(ausencia.getTipoAusencia()).isEqualTo(TipoAusencia.VACACIONES);
            assertThat(ausencia.getObservaciones()).isEqualTo("original");
        }

        @Test
        @DisplayName("PATCH selectivo solo observaciones → tipoAusencia se conserva")
        void patchSoloObservacionesConservaTipo() {
            PlanificacionAusencia ausencia = nuevaAusenciaPersistida(1L, HOY, false);
            ausencia.setTipoAusencia(TipoAusencia.ASUNTO_PROPIO);
            ausencia.setObservaciones("original");

            when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausencia));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(ausenciaRepository.save(any(PlanificacionAusencia.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AusenciaService service = nuevoServiceConFecha(HOY);
            AusenciaPatchRequest patch = nuevoPatchRequest(null, "nueva nota");

            service.actualizar(1L, patch, "admin");

            assertThat(ausencia.getTipoAusencia()).isEqualTo(TipoAusencia.ASUNTO_PROPIO);
            assertThat(ausencia.getObservaciones()).isEqualTo("nueva nota");
        }
    }

    // =================================================================
    // E32 — eliminar()
    // =================================================================

    @Nested
    @DisplayName("eliminar (E32) — unico DELETE real del sistema")
    class EliminarTests {

        @Test
        @DisplayName("id inexistente → NotFoundException (404)")
        void idInexistenteLanzaNotFound() {
            when(ausenciaRepository.findById(99L)).thenReturn(Optional.empty());

            AusenciaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.eliminar(99L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Ausencia no encontrada");

            verify(ausenciaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("procesado=true → ConflictException (409, RNF-L01)")
        void ausenciaProcesadaNoSeElimina() {
            PlanificacionAusencia ausencia = nuevaAusenciaPersistida(1L, AYER, true);
            when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausencia));

            AusenciaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.eliminar(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("RNF-L01");

            verify(ausenciaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("procesado=false → delete invocado")
        void ausenciaNoProcesadaSeElimina() {
            PlanificacionAusencia ausencia = nuevaAusenciaPersistida(1L, HOY, false);
            when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausencia));

            AusenciaService service = nuevoServiceConFecha(HOY);
            service.eliminar(1L);

            verify(ausenciaRepository).delete(ausencia);
        }
    }

    // =================================================================
    // E33 — listar()
    // =================================================================

    @Nested
    @DisplayName("listar (E33) — filtros opcionales combinables")
    class ListarTests {

        @Test
        @DisplayName("pasa los 4 filtros al repositorio y mapea cada entidad a Response")
        void pasaFiltrosYMapeaResultados() {
            PlanificacionAusencia a1 = nuevaAusenciaPersistida(1L, HOY, false);
            PlanificacionAusencia a2 = nuevaAusenciaPersistida(2L, MANANA, false);
            when(ausenciaRepository.findByFiltros(10L, HOY, MANANA, false))
                    .thenReturn(List.of(a1, a2));

            AusenciaService service = nuevoServiceConFecha(HOY);
            List<AusenciaResponse> resultado =
                    service.listar(10L, HOY, MANANA, false);

            assertThat(resultado).hasSize(2);
            assertThat(resultado)
                    .extracting(AusenciaResponse::getId)
                    .containsExactly(1L, 2L);
            assertThat(resultado)
                    .extracting(AusenciaResponse::getEmpleadoId)
                    .containsExactly(10L, 10L);
        }

        @Test
        @DisplayName("sin filtros (todos null) → delega al repo con nulls y devuelve lista vacia")
        void sinFiltrosDevuelveVacio() {
            when(ausenciaRepository.findByFiltros(null, null, null, null))
                    .thenReturn(Collections.emptyList());

            AusenciaService service = nuevoServiceConFecha(HOY);
            List<AusenciaResponse> resultado =
                    service.listar(null, null, null, null);

            assertThat(resultado).isEmpty();
        }
    }

    // =================================================================
    // Helpers
    // =================================================================

    private AusenciaRequest nuevoRequest(Long empleadoId, LocalDate fecha, TipoAusencia tipo) {
        AusenciaRequest request = new AusenciaRequest();
        request.setEmpleadoId(empleadoId);
        request.setFecha(fecha);
        request.setTipoAusencia(tipo);
        request.setObservaciones("test");
        return request;
    }

    private AusenciaRangoRequest nuevoRangoRequest(Long empleadoId, LocalDate desde,
                                                    LocalDate hasta, TipoAusencia tipo,
                                                    boolean sobrescribir) {
        AusenciaRangoRequest request = new AusenciaRangoRequest();
        request.setEmpleadoId(empleadoId);
        request.setFechaDesde(desde);
        request.setFechaHasta(hasta);
        request.setTipoAusencia(tipo);
        request.setSobrescribir(sobrescribir);
        request.setObservaciones("test");
        return request;
    }

    private AusenciaPatchRequest nuevoPatchRequest(TipoAusencia tipo, String observaciones) {
        AusenciaPatchRequest patch = new AusenciaPatchRequest();
        patch.setTipoAusencia(tipo);
        patch.setObservaciones(observaciones);
        return patch;
    }

    private PlanificacionAusencia nuevaAusenciaPersistida(Long id, LocalDate fecha,
                                                           boolean procesado) {
        PlanificacionAusencia ausencia = new PlanificacionAusencia();
        ausencia.setId(id);
        ausencia.setEmpleado(empleado);
        ausencia.setFecha(fecha);
        ausencia.setTipoAusencia(TipoAusencia.VACACIONES);
        ausencia.setProcesado(procesado);
        ausencia.setUsuario(usuarioAdmin);
        ausencia.setObservaciones("inicial");
        ausencia.setFechaCreacion(LocalDateTime.now());
        return ausencia;
    }
}
