package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.SaldoAnual;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import com.staffflow.dto.response.SaldoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de SaldoService.
 *
 * Verifica la logica de recalculo de saldo anual:
 *   - Contadores de dias por tipo de fichaje.
 *   - Calculo de saldo de horas (diferencia jornada efectiva vs esperada).
 *   - Conversion minutos → horas con BigDecimal (precision decimal).
 *   - Patron findOrCreate: crea saldo inicial si no existe.
 *   - Calculo de dias disponibles = derecho + pendientes - consumidos.
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SaldoService — recalculo de saldo anual")
class SaldoServiceTest {

    @Mock private SaldoAnualRepository saldoRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private FichajeRepository fichajeRepository;
    @Mock private PlanificacionAusenciaRepository planificacionRepository;

    private SaldoService saldoService;

    private static final int ANIO = 2026;
    private static final long EMPLEADO_ID = 1L;

    /**
     * Reloj fijado al 15/01/2026 (Europe/Madrid) para hacer deterministas
     * las ramas de SaldoService que dependen del ano actual ({@code Year.now(clock)}
     * en {@code resolverAnio}, {@code obtenerPorEmpleado}, {@code listarTodos},
     * {@code obtenerMiSaldo}) y la marca {@code calculadoHastaFecha} que
     * persiste {@code LocalDate.now(clock)}.
     */
    private static final Clock CLOCK_FIJO =
            Clock.fixed(
                    LocalDate.of(2026, 1, 15).atStartOfDay(ZoneId.of("Europe/Madrid")).toInstant(),
                    ZoneId.of("Europe/Madrid"));

    private Empleado empleado;
    private SaldoAnual saldoExistente;

    @BeforeEach
    void setUp() {
        saldoService = new SaldoService(
                saldoRepository,
                empleadoRepository,
                fichajeRepository,
                planificacionRepository,
                CLOCK_FIJO);

        empleado = new Empleado();
        empleado.setId(EMPLEADO_ID);
        empleado.setNombre("Carlos");
        empleado.setApellido1("López");
        empleado.setJornadaDiariaMinutos(480);     // 8 horas = 480 min
        empleado.setDiasVacacionesAnuales(22);
        empleado.setDiasAsuntosPropiosAnuales(3);

        saldoExistente = new SaldoAnual();
        saldoExistente.setEmpleado(empleado);
        saldoExistente.setAnio(ANIO);
        saldoExistente.setDiasTrabajados(0);
        saldoExistente.setDiasBajaMedica(0);
        saldoExistente.setDiasPermisoRetribuido(0);
        saldoExistente.setDiasAusenciaInjustificada(0);
        saldoExistente.setDiasVacacionesDerechoAnio(22);
        saldoExistente.setDiasVacacionesPendientesAnioAnterior(0);
        saldoExistente.setDiasVacacionesConsumidos(0);
        saldoExistente.setDiasVacacionesDisponibles(22);
        saldoExistente.setDiasAsuntosPropiosDerechoAnio(3);
        saldoExistente.setDiasAsuntosPropiosPendientesAnterior(0);
        saldoExistente.setDiasAsuntosPropiosConsumidos(0);
        saldoExistente.setDiasAsuntosPropiosDisponibles(3);
        saldoExistente.setSaldoHoras(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------
    // Contadores de dias por tipo
    // ---------------------------------------------------------------

    @Test
    @DisplayName("recalcularParaProceso — fichajes NORMAL — incrementa diasTrabajados")
    void recalcularParaProceso_fichajesNormal_incrementaDiasTrabajados() {
        List<Fichaje> fichajes = List.of(
                fichaje(TipoFichaje.NORMAL, 480, 0),    // jornada exacta
                fichaje(TipoFichaje.NORMAL, 480, 0)     // jornada exacta
        );
        configurarMocks(fichajes);

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);

        SaldoAnual guardado = capturarSaldoGuardado();
        assertThat(guardado.getDiasTrabajados()).isEqualTo(2);
        assertThat(guardado.getSaldoHoras()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("recalcularParaProceso — fichajes VACACIONES — incrementa diasVacacionesConsumidos")
    void recalcularParaProceso_fichajesVacaciones_incrementaDiasVacaciones() {
        List<Fichaje> fichajes = List.of(
                fichaje(TipoFichaje.VACACIONES, 0, 0),
                fichaje(TipoFichaje.VACACIONES, 0, 0),
                fichaje(TipoFichaje.VACACIONES, 0, 0)
        );
        configurarMocks(fichajes);

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);

        SaldoAnual guardado = capturarSaldoGuardado();
        assertThat(guardado.getDiasTrabajados()).isZero();
        assertThat(guardado.getDiasVacacionesConsumidos()).isEqualTo(3);
        assertThat(guardado.getDiasVacacionesDisponibles()).isEqualTo(22 - 3); // 19
    }

    @Test
    @DisplayName("recalcularParaProceso — fichaje BAJA_MEDICA — incrementa diasBajaMedica")
    void recalcularParaProceso_fichajeBajaMedica_incrementaDiasBaja() {
        configurarMocks(List.of(fichaje(TipoFichaje.BAJA_MEDICA, 0, 0)));

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);

        assertThat(capturarSaldoGuardado().getDiasBajaMedica()).isEqualTo(1);
    }

    @Test
    @DisplayName("recalcularParaProceso — fichaje AUSENCIA_INJUSTIFICADA — incrementa diasAusenciaInjustificada")
    void recalcularParaProceso_fichajeAusenciaInjustificada_incrementaContador() {
        configurarMocks(List.of(fichaje(TipoFichaje.AUSENCIA_INJUSTIFICADA, 0, 0)));

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);

        assertThat(capturarSaldoGuardado().getDiasAusenciaInjustificada()).isEqualTo(1);
    }

    @Test
    @DisplayName("recalcularParaProceso — fichaje ASUNTO_PROPIO — incrementa diasAsuntosPropiosConsumidos")
    void recalcularParaProceso_fichajeAsuntoPropio_incrementaDiasAsuntosPropios() {
        configurarMocks(List.of(fichaje(TipoFichaje.ASUNTO_PROPIO, 0, 0)));

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);

        SaldoAnual guardado = capturarSaldoGuardado();
        assertThat(guardado.getDiasAsuntosPropiosConsumidos()).isEqualTo(1);
        assertThat(guardado.getDiasAsuntosPropiosDisponibles()).isEqualTo(3 - 1); // 2
    }

    // ---------------------------------------------------------------
    // Calculo de saldo de horas
    // ---------------------------------------------------------------

    @Test
    @DisplayName("recalcularParaProceso — jornada con hora extra (30 min) — saldo positivo")
    void recalcularParaProceso_jornadaConHoraExtra_saldoHorasPositivo() {
        // 510 min trabajados con 0 pausas → 30 min extra sobre los 480 esperados
        configurarMocks(List.of(fichaje(TipoFichaje.NORMAL, 510, 0)));

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);

        // 30 min / 60 = 0.50 horas
        assertThat(capturarSaldoGuardado().getSaldoHoras())
                .isEqualByComparingTo(new BigDecimal("0.50"));
    }

    @Test
    @DisplayName("recalcularParaProceso — jornada corta (60 min menos) — saldo negativo")
    void recalcularParaProceso_jornadaCorta_saldoHorasNegativo() {
        // 420 min trabajados (7h) → -60 min respecto a los 480 esperados
        configurarMocks(List.of(fichaje(TipoFichaje.NORMAL, 420, 0)));

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);

        // -60 min / 60 = -1.00 hora
        assertThat(capturarSaldoGuardado().getSaldoHoras())
                .isEqualByComparingTo(new BigDecimal("-1.00"));
    }

    @Test
    @DisplayName("recalcularParaProceso — varios dias normales con saldo acumulado — suma correcta")
    void recalcularParaProceso_variosDiasConSaldo_acumulaCorrectamente() {
        // Dia 1: +30 min extra | Dia 2: -30 min falta | Dia 3: jornada exacta
        List<Fichaje> fichajes = List.of(
                fichaje(TipoFichaje.NORMAL, 510, 0),   // +30 min → +0.50h
                fichaje(TipoFichaje.NORMAL, 450, 0),   // -30 min → -0.50h
                fichaje(TipoFichaje.NORMAL, 480, 0)    // exacto  →  0.00h
        );
        configurarMocks(fichajes);

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);

        SaldoAnual guardado = capturarSaldoGuardado();
        assertThat(guardado.getDiasTrabajados()).isEqualTo(3);
        assertThat(guardado.getSaldoHoras()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("recalcularParaProceso — mezcla de tipos — contadores independientes correctos")
    void recalcularParaProceso_mezclaDeTipos_contadoresCorrectosIndependientes() {
        // BAJA_MEDICA y PERMISO_RETRIBUIDO cuentan como dia trabajado (jornada
        // consumida con justificacion legal), neutros en saldoHoras. Por eso
        // diasTrabajados = NORMAL (1) + BAJA_MEDICA (1) = 2. Vacaciones y
        // asuntos propios NO suman diasTrabajados — van a sus propios contadores.
        List<Fichaje> fichajes = List.of(
                fichaje(TipoFichaje.NORMAL, 480, 0),
                fichaje(TipoFichaje.VACACIONES, 0, 0),
                fichaje(TipoFichaje.VACACIONES, 0, 0),
                fichaje(TipoFichaje.ASUNTO_PROPIO, 0, 0),
                fichaje(TipoFichaje.BAJA_MEDICA, 0, 0)
        );
        configurarMocks(fichajes);

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);

        SaldoAnual guardado = capturarSaldoGuardado();
        assertThat(guardado.getDiasTrabajados()).isEqualTo(2); // NORMAL + BAJA_MEDICA
        assertThat(guardado.getDiasVacacionesConsumidos()).isEqualTo(2);
        assertThat(guardado.getDiasAsuntosPropiosConsumidos()).isEqualTo(1);
        assertThat(guardado.getDiasBajaMedica()).isEqualTo(1);
        assertThat(guardado.getDiasVacacionesDisponibles()).isEqualTo(22 - 2); // 20
        assertThat(guardado.getDiasAsuntosPropiosDisponibles()).isEqualTo(3 - 1); // 2
    }

    // ---------------------------------------------------------------
    // Idempotencia: el recalculo parte siempre de cero
    // ---------------------------------------------------------------

    @Test
    @DisplayName("recalcularParaProceso — ejecutado dos veces — resultado identico (idempotente)")
    void recalcularParaProceso_ejecutadoDosveces_resultadoIdempotente() {
        List<Fichaje> fichajes = List.of(fichaje(TipoFichaje.NORMAL, 480, 0));
        configurarMocks(fichajes);

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);
        // Simular que la segunda ejecucion recupera el saldo ya guardado
        when(saldoRepository.findByEmpleadoIdAndAnio(EMPLEADO_ID, ANIO))
                .thenReturn(Optional.of(saldoExistente));

        saldoService.recalcularParaProceso(EMPLEADO_ID, ANIO);

        // El saldo guardado en la segunda llamada debe ser el mismo que en la primera
        SaldoAnual guardado = capturarSaldoGuardado();
        assertThat(guardado.getDiasTrabajados()).isEqualTo(1);
        assertThat(guardado.getSaldoHoras()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------
    // E38 — GET /api/v1/saldos/{empleadoId}
    // ---------------------------------------------------------------

    @Test
    @DisplayName("obtenerPorEmpleado — empleado inexistente — lanza NotFoundException")
    void obtenerPorEmpleado_empleadoInexistente_lanzaNotFound() {
        when(empleadoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saldoService.obtenerPorEmpleado(99L, ANIO))
                .isInstanceOf(com.staffflow.exception.NotFoundException.class)
                .hasMessageContaining("Empleado no encontrado");
    }

    @Test
    @DisplayName("obtenerPorEmpleado — anio pasado sin saldo — lanza NotFoundException sin crear on-demand")
    void obtenerPorEmpleado_anioPasadoSinSaldo_lanzaNotFound() {
        // Reloj fijado en 2026; pedimos 2025 → NO se aplica el on-demand
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(saldoRepository.findByEmpleadoIdAndAnio(EMPLEADO_ID, 2025))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> saldoService.obtenerPorEmpleado(EMPLEADO_ID, 2025))
                .isInstanceOf(com.staffflow.exception.NotFoundException.class)
                .hasMessageContaining("No existe saldo");

        // Verificar que NO se llamo a fichajeRepository (no hubo recalculo on-demand)
        verify(fichajeRepository, never())
                .findByEmpleadoIdAndFechaBetween(anyLong(), any(), any());
    }

    @Test
    @DisplayName("obtenerPorEmpleado — anio actual sin saldo — recalcula on-demand y devuelve respuesta")
    void obtenerPorEmpleado_anioActualSinSaldo_recalculaOnDemand() {
        // Reloj fijado en 2026; pedimos null → resolverAnio devuelve 2026 (anio actual)
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        // Primera busqueda: vacio (dispara on-demand). Segunda: el saldo recien creado.
        when(saldoRepository.findByEmpleadoIdAndAnio(EMPLEADO_ID, ANIO))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(saldoExistente));
        when(fichajeRepository.findByEmpleadoIdAndFechaBetween(eq(EMPLEADO_ID), any(), any()))
                .thenReturn(List.of());
        when(saldoRepository.save(any(SaldoAnual.class))).thenAnswer(inv -> inv.getArgument(0));
        // Mocks de las dos queries de planificadas que dispara toSaldoResponse
        when(planificacionRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                eq(EMPLEADO_ID), any(), any(), any())).thenReturn(0);

        SaldoResponse respuesta = saldoService.obtenerPorEmpleado(EMPLEADO_ID, null);

        assertThat(respuesta).isNotNull();
        assertThat(respuesta.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
        assertThat(respuesta.getAnio()).isEqualTo(ANIO);
        // Se invoco crearSaldoInicial dentro de recalcularParaProceso → save al menos 1 vez
        verify(saldoRepository, atLeastOnce()).save(any(SaldoAnual.class));
    }

    @Test
    @DisplayName("obtenerPorEmpleado — saldo existente — devuelve respuesta sin recalcular")
    void obtenerPorEmpleado_saldoExistente_devuelveRespuesta() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(saldoRepository.findByEmpleadoIdAndAnio(EMPLEADO_ID, ANIO))
                .thenReturn(Optional.of(saldoExistente));
        when(planificacionRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                eq(EMPLEADO_ID), any(), any(), any())).thenReturn(0);

        SaldoResponse respuesta = saldoService.obtenerPorEmpleado(EMPLEADO_ID, ANIO);

        assertThat(respuesta).isNotNull();
        assertThat(respuesta.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
        assertThat(respuesta.getVacaciones().getDisponibles()).isEqualTo(22);
        // No se llamo a save (no hubo on-demand)
        verify(saldoRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // E39 — GET /api/v1/saldos
    // ---------------------------------------------------------------

    @Test
    @DisplayName("listarTodos — anio sin registros — devuelve lista vacia")
    void listarTodos_anioPasadoSinSaldos_devuelveListaVacia() {
        // Anio 2025 (pasado, NO dispara on-demand) sin saldos
        when(saldoRepository.findByAnio(2025)).thenReturn(List.of());

        List<SaldoResponse> respuesta = saldoService.listarTodos(2025);

        assertThat(respuesta).isEmpty();
        // No se intento on-demand porque no es anio actual
        verify(empleadoRepository, never()).findAll();
    }

    @Test
    @DisplayName("listarTodos — anio actual — crea on-demand solo para activos sin registro")
    void listarTodos_anioActual_creaOnDemandSoloActivosSinRegistro() {
        Empleado activoConSaldo = empleadoConId(1L, true);
        Empleado activoSinSaldo = empleadoConId(2L, true);
        Empleado inactivoSinSaldo = empleadoConId(3L, false);

        when(empleadoRepository.findAll())
                .thenReturn(List.of(activoConSaldo, activoSinSaldo, inactivoSinSaldo));
        // Activo 1: ya tiene saldo (no recalcula)
        when(saldoRepository.findByEmpleadoIdAndAnio(1L, ANIO))
                .thenReturn(Optional.of(saldoExistente));
        // Activo 2: no tiene saldo (dispara recalculo). Segunda llamada devuelve lo recien creado.
        when(saldoRepository.findByEmpleadoIdAndAnio(2L, ANIO))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(saldoExistente));
        // Inactivo: no se consulta porque el filtro activo lo descarta
        when(empleadoRepository.findById(2L)).thenReturn(Optional.of(activoSinSaldo));
        when(fichajeRepository.findByEmpleadoIdAndFechaBetween(eq(2L), any(), any()))
                .thenReturn(List.of());
        when(saldoRepository.save(any(SaldoAnual.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saldoRepository.findByAnio(ANIO)).thenReturn(List.of(saldoExistente));
        when(planificacionRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                anyLong(), any(), any(), any())).thenReturn(0);

        List<SaldoResponse> respuesta = saldoService.listarTodos(null);

        assertThat(respuesta).hasSize(1);
        // Solo el activo sin registro disparo save (el inactivo NO entro al recalculo)
        verify(empleadoRepository, never()).findById(3L);
    }

    @Test
    @DisplayName("listarTodos — anio pasado con saldos — mapea sin tocar empleados")
    void listarTodos_anioPasadoConSaldos_mapeaSinTocarEmpleados() {
        SaldoAnual saldo2024 = new SaldoAnual();
        saldo2024.setEmpleado(empleado);
        saldo2024.setAnio(2024);
        saldo2024.setDiasTrabajados(220);
        saldo2024.setDiasBajaMedica(0);
        saldo2024.setDiasPermisoRetribuido(0);
        saldo2024.setDiasAusenciaInjustificada(0);
        saldo2024.setDiasVacacionesDerechoAnio(22);
        saldo2024.setDiasVacacionesPendientesAnioAnterior(0);
        saldo2024.setDiasVacacionesConsumidos(22);
        saldo2024.setDiasVacacionesDisponibles(0);
        saldo2024.setDiasAsuntosPropiosDerechoAnio(3);
        saldo2024.setDiasAsuntosPropiosPendientesAnterior(0);
        saldo2024.setDiasAsuntosPropiosConsumidos(3);
        saldo2024.setDiasAsuntosPropiosDisponibles(0);
        saldo2024.setSaldoHoras(BigDecimal.ZERO);
        when(saldoRepository.findByAnio(2024)).thenReturn(List.of(saldo2024));
        when(planificacionRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                anyLong(), any(), any(), any())).thenReturn(0);

        List<SaldoResponse> respuesta = saldoService.listarTodos(2024);

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.get(0).getAnio()).isEqualTo(2024);
        verify(empleadoRepository, never()).findAll();
    }

    // ---------------------------------------------------------------
    // E40 — POST /api/v1/saldos/{empleadoId}/recalcular
    // ---------------------------------------------------------------

    @Test
    @DisplayName("recalcular — empleado inexistente — lanza NotFoundException")
    void recalcular_empleadoInexistente_lanzaNotFound() {
        when(empleadoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saldoService.recalcular(99L, ANIO))
                .isInstanceOf(com.staffflow.exception.NotFoundException.class)
                .hasMessageContaining("Empleado no encontrado");
    }

    @Test
    @DisplayName("recalcular — anio null — usa anio actual del Clock fijado")
    void recalcular_anioNull_usaAnioActualDelClock() {
        // Reloj fijado en 2026-01-15 → resolverAnio(null) devuelve 2026
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(saldoRepository.findByEmpleadoIdAndAnio(EMPLEADO_ID, ANIO))
                .thenReturn(Optional.of(saldoExistente));
        when(fichajeRepository.findByEmpleadoIdAndFechaBetween(eq(EMPLEADO_ID), any(), any()))
                .thenReturn(List.of(fichaje(TipoFichaje.NORMAL, 480, 0)));
        when(saldoRepository.save(any(SaldoAnual.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planificacionRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                eq(EMPLEADO_ID), any(), any(), any())).thenReturn(0);

        SaldoResponse respuesta = saldoService.recalcular(EMPLEADO_ID, null);

        assertThat(respuesta.getAnio()).isEqualTo(ANIO);
        assertThat(respuesta.getHoras().getDiasTrabajados()).isEqualTo(1);
    }

    @Test
    @DisplayName("recalcular — caso feliz — delega en recalcularParaProceso y devuelve respuesta")
    void recalcular_casoFeliz_devuelveRespuesta() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(saldoRepository.findByEmpleadoIdAndAnio(EMPLEADO_ID, ANIO))
                .thenReturn(Optional.of(saldoExistente));
        when(fichajeRepository.findByEmpleadoIdAndFechaBetween(eq(EMPLEADO_ID), any(), any()))
                .thenReturn(List.of(
                        fichaje(TipoFichaje.NORMAL, 510, 0),  // +30 min extra → +0.50 h
                        fichaje(TipoFichaje.VACACIONES, 0, 0)
                ));
        when(saldoRepository.save(any(SaldoAnual.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planificacionRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                eq(EMPLEADO_ID), any(), any(), any())).thenReturn(0);

        SaldoResponse respuesta = saldoService.recalcular(EMPLEADO_ID, ANIO);

        assertThat(respuesta.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
        assertThat(respuesta.getHoras().getDiasTrabajados()).isEqualTo(1);
        assertThat(respuesta.getHoras().getSaldoHoras()).isEqualByComparingTo(new BigDecimal("0.50"));
        assertThat(respuesta.getVacaciones().getConsumidos()).isEqualTo(1);
        assertThat(respuesta.getVacaciones().getDisponibles()).isEqualTo(21);
    }

    // ---------------------------------------------------------------
    // E41 — GET /api/v1/saldos/me
    // ---------------------------------------------------------------

    @Test
    @DisplayName("obtenerMiSaldo — username sin empleado — lanza NotFoundException")
    void obtenerMiSaldo_usernameSinEmpleado_lanzaNotFound() {
        when(empleadoRepository.findByUsuarioUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saldoService.obtenerMiSaldo("fantasma", ANIO))
                .isInstanceOf(com.staffflow.exception.NotFoundException.class)
                .hasMessageContaining("Empleado no encontrado para el usuario");
    }

    @Test
    @DisplayName("obtenerMiSaldo — anio futuro — lanza NotFoundException sin tocar repositorios de saldo")
    void obtenerMiSaldo_anioFuturo_lanzaNotFound() {
        empleado.setFechaAlta(LocalDate.of(2024, 6, 1));
        when(empleadoRepository.findByUsuarioUsername("carlos")).thenReturn(Optional.of(empleado));

        // Reloj fijado en 2026 → 2027 es futuro
        assertThatThrownBy(() -> saldoService.obtenerMiSaldo("carlos", 2027))
                .isInstanceOf(com.staffflow.exception.NotFoundException.class)
                .hasMessageContaining("No hay datos de saldo");

        verify(saldoRepository, never()).findByEmpleadoIdAndAnio(anyLong(), anyInt());
    }

    @Test
    @DisplayName("obtenerMiSaldo — anio anterior al alta — lanza NotFoundException")
    void obtenerMiSaldo_anioAnteriorAlAlta_lanzaNotFound() {
        empleado.setFechaAlta(LocalDate.of(2025, 6, 1));
        when(empleadoRepository.findByUsuarioUsername("carlos")).thenReturn(Optional.of(empleado));

        // fechaAlta=2025 y consulta=2024 → anterior al alta
        assertThatThrownBy(() -> saldoService.obtenerMiSaldo("carlos", 2024))
                .isInstanceOf(com.staffflow.exception.NotFoundException.class)
                .hasMessageContaining("No hay datos de saldo");

        verify(saldoRepository, never()).findByEmpleadoIdAndAnio(anyLong(), anyInt());
    }

    @Test
    @DisplayName("obtenerMiSaldo — anio actual sin saldo — calcula on-demand y devuelve respuesta")
    void obtenerMiSaldo_anioActualSinSaldo_recalculaOnDemand() {
        empleado.setFechaAlta(LocalDate.of(2024, 6, 1));
        when(empleadoRepository.findByUsuarioUsername("carlos")).thenReturn(Optional.of(empleado));
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(saldoRepository.findByEmpleadoIdAndAnio(EMPLEADO_ID, ANIO))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(saldoExistente));
        when(fichajeRepository.findByEmpleadoIdAndFechaBetween(eq(EMPLEADO_ID), any(), any()))
                .thenReturn(List.of());
        when(saldoRepository.save(any(SaldoAnual.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planificacionRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                eq(EMPLEADO_ID), any(), any(), any())).thenReturn(0);

        SaldoResponse respuesta = saldoService.obtenerMiSaldo("carlos", null);

        assertThat(respuesta).isNotNull();
        assertThat(respuesta.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
        assertThat(respuesta.getAnio()).isEqualTo(ANIO);
        verify(saldoRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("obtenerMiSaldo — saldo existente — devuelve respuesta sin recalcular")
    void obtenerMiSaldo_saldoExistente_devuelveRespuesta() {
        empleado.setFechaAlta(LocalDate.of(2024, 6, 1));
        when(empleadoRepository.findByUsuarioUsername("carlos")).thenReturn(Optional.of(empleado));
        when(saldoRepository.findByEmpleadoIdAndAnio(EMPLEADO_ID, ANIO))
                .thenReturn(Optional.of(saldoExistente));
        when(planificacionRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                eq(EMPLEADO_ID), any(), any(), any())).thenReturn(0);

        SaldoResponse respuesta = saldoService.obtenerMiSaldo("carlos", ANIO);

        assertThat(respuesta).isNotNull();
        assertThat(respuesta.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
        assertThat(respuesta.getAnio()).isEqualTo(ANIO);
        verify(saldoRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Helpers de test
    // ---------------------------------------------------------------

    /** Configura los mocks con una lista de fichajes predefinida. */
    private void configurarMocks(List<Fichaje> fichajes) {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(saldoRepository.findByEmpleadoIdAndAnio(EMPLEADO_ID, ANIO))
                .thenReturn(Optional.of(saldoExistente));
        when(fichajeRepository.findByEmpleadoIdAndFechaBetween(
                eq(EMPLEADO_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(fichajes);
        when(saldoRepository.save(any(SaldoAnual.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Crea un fichaje de prueba con el tipo y los minutos indicados. */
    private Fichaje fichaje(TipoFichaje tipo, int jornadaEfectivaMinutos, int totalPausasMinutos) {
        Fichaje f = new Fichaje();
        f.setTipo(tipo);
        f.setJornadaEfectivaMinutos(jornadaEfectivaMinutos);
        f.setTotalPausasMinutos(totalPausasMinutos);
        return f;
    }

    /** Captura el argumento pasado a saldoRepository.save(). */
    private SaldoAnual capturarSaldoGuardado() {
        ArgumentCaptor<SaldoAnual> captor = ArgumentCaptor.forClass(SaldoAnual.class);
        verify(saldoRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    /** Crea un empleado minimo con id, nombre y flag activo para los tests de E39. */
    private Empleado empleadoConId(Long id, boolean activo) {
        Empleado e = new Empleado();
        e.setId(id);
        e.setNombre("Empleado");
        e.setApellido1("Test" + id);
        e.setJornadaDiariaMinutos(480);
        e.setDiasVacacionesAnuales(22);
        e.setDiasAsuntosPropiosAnuales(3);
        e.setActivo(activo);
        return e;
    }
}
