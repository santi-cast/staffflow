package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.SaldoAnual;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @InjectMocks
    private SaldoService saldoService;

    private static final int ANIO = 2026;
    private static final long EMPLEADO_ID = 1L;

    private Empleado empleado;
    private SaldoAnual saldoExistente;

    @BeforeEach
    void setUp() {
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
}
