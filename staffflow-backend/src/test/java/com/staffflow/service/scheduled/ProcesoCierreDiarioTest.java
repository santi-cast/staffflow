package com.staffflow.service.scheduled;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.PlanificacionAusencia;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.TipoAusencia;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.exception.NotFoundException;
import com.staffflow.service.SaldoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de ProcesoCierreDiario.
 *
 * <p>Cubre las tres tareas del proceso nocturno (Tarea A: cierre ausencias,
 * Tarea B: materializacion de planificaciones, Tarea C: recalculo saldos)
 * y el bloque adicional de DIA_LIBRE para el dia siguiente cuando ese
 * dia es sabado o domingo.</p>
 *
 * <p>Cada test construye un {@link Clock#fixed(java.time.Instant, ZoneId)}
 * con la fecha objetivo (laborable, viernes o sabado segun convenga) y
 * lo inyecta manualmente al instanciar el SUT. De esta forma las
 * ramas dependientes del dia de la semana son deterministas y la suite
 * funciona igual cualquier dia del año.</p>
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProcesoCierreDiario — proceso nocturno @Scheduled 23:55")
class ProcesoCierreDiarioTest {

    @Mock private PlanificacionAusenciaRepository ausenciaRepository;
    @Mock private FichajeRepository fichajeRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SaldoService saldoService;

    private Usuario usuarioSistema;
    private Empleado empleado1;
    private Empleado empleado2;

    // Fechas fijas escogidas para cubrir las tres ramas:
    //   LUNES_FIJO  = laborable, manana es martes (laborable).
    //   VIERNES_FIJO = laborable, manana es sabado (fin de semana).
    //   SABADO_FIJO  = fin de semana, manana es domingo.
    private static final LocalDate LUNES_FIJO   = LocalDate.of(2026, 1, 5);   // lunes
    private static final LocalDate VIERNES_FIJO = LocalDate.of(2026, 1, 9);   // viernes
    private static final LocalDate SABADO_FIJO  = LocalDate.of(2026, 1, 10);  // sabado

    @BeforeEach
    void setUp() {
        usuarioSistema = new Usuario();
        usuarioSistema.setId(99L);
        usuarioSistema.setUsername("terminal_service");

        empleado1 = new Empleado();
        empleado1.setId(1L);
        empleado1.setNombre("Carlos");
        empleado1.setApellido1("Lopez");

        empleado2 = new Empleado();
        empleado2.setId(2L);
        empleado2.setNombre("Ana");
        empleado2.setApellido1("Garcia");
    }

    // -------------------------------------------------------------------
    // Tarea A — Cierre ausencias injustificadas / dias libres de fin de semana
    // -------------------------------------------------------------------

    @Test
    @DisplayName("Tarea A: dia laborable sin fichaje crea AUSENCIA_INJUSTIFICADA")
    void tareaA_laborableSinFichaje_creaAusenciaInjustificada() {
        ProcesoCierreDiario proceso = nuevoProceso(LUNES_FIJO);
        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(LUNES_FIJO))
                .thenReturn(List.of(empleado1));
        when(fichajeRepository.findByEmpleadoIdAndFecha(1L, LUNES_FIJO))
                .thenReturn(Optional.empty());
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(LUNES_FIJO.plusDays(1)))
                .thenReturn(Collections.emptyList());

        proceso.ejecutar();

        ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
        verify(fichajeRepository, times(1)).save(captor.capture());
        Fichaje creado = captor.getValue();
        assertThat(creado.getTipo()).isEqualTo(TipoFichaje.AUSENCIA_INJUSTIFICADA);
        assertThat(creado.getEmpleado()).isSameAs(empleado1);
        assertThat(creado.getFecha()).isEqualTo(LUNES_FIJO);
        assertThat(creado.getUsuario()).isSameAs(usuarioSistema);
        assertThat(creado.getJornadaEfectivaMinutos()).isZero();
    }

    @Test
    @DisplayName("Tarea A: empleado con fichaje previo no se sobreescribe")
    void tareaA_conFichajePrevio_noCreaNuevo() {
        ProcesoCierreDiario proceso = nuevoProceso(LUNES_FIJO);
        Fichaje existente = new Fichaje();
        existente.setEmpleado(empleado1);
        existente.setFecha(LUNES_FIJO);
        existente.setTipo(TipoFichaje.NORMAL);

        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(LUNES_FIJO))
                .thenReturn(List.of(empleado1));
        when(fichajeRepository.findByEmpleadoIdAndFecha(1L, LUNES_FIJO))
                .thenReturn(Optional.of(existente));
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(LUNES_FIJO.plusDays(1)))
                .thenReturn(Collections.emptyList());

        proceso.ejecutar();

        verify(fichajeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tarea A: fin de semana sin fichaje crea DIA_LIBRE en lugar de AUSENCIA_INJUSTIFICADA")
    void tareaA_finDeSemanaSinFichaje_creaDiaLibre() {
        ProcesoCierreDiario proceso = nuevoProceso(SABADO_FIJO);
        LocalDate domingo = SABADO_FIJO.plusDays(1);

        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(SABADO_FIJO))
                .thenReturn(List.of(empleado1));
        when(fichajeRepository.findByEmpleadoIdAndFecha(1L, SABADO_FIJO))
                .thenReturn(Optional.empty());
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(domingo))
                .thenReturn(Collections.emptyList());
        // Bloque DIA_LIBRE del dia siguiente (domingo): tampoco hay fichaje.
        when(fichajeRepository.findByEmpleadoIdAndFecha(1L, domingo))
                .thenReturn(Optional.empty());

        proceso.ejecutar();

        ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
        verify(fichajeRepository, times(2)).save(captor.capture()); // sabado (Tarea A) + domingo (bloque DIA_LIBRE)
        List<Fichaje> guardados = captor.getAllValues();
        assertThat(guardados).allSatisfy(f -> assertThat(f.getTipo()).isEqualTo(TipoFichaje.DIA_LIBRE));
        assertThat(guardados).extracting(Fichaje::getFecha)
                .containsExactlyInAnyOrder(SABADO_FIJO, domingo);
    }

    @Test
    @DisplayName("Tarea A: solo procesa empleados con fechaAlta <= hoy (alta diferida queda fuera)")
    void tareaA_filtraAltaDiferida() {
        // El repositorio ya filtra por fechaAlta: simulamos lista vacia
        // y verificamos que no se crean fichajes pese a que Tarea B este
        // configurada para no devolver nada tampoco.
        ProcesoCierreDiario proceso = nuevoProceso(LUNES_FIJO);
        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(LUNES_FIJO))
                .thenReturn(Collections.emptyList());
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(LUNES_FIJO.plusDays(1)))
                .thenReturn(Collections.emptyList());

        proceso.ejecutar();

        verify(fichajeRepository, never()).save(any());
        verify(saldoService, never()).recalcularParaProceso(anyLong(), anyInt());
    }

    // -------------------------------------------------------------------
    // Tarea B — Procesado planificaciones pendientes
    // -------------------------------------------------------------------

    @Test
    @DisplayName("Tarea B: planificacion individual VACACIONES genera fichaje y marca procesado=true")
    void tareaB_planificacionIndividual_generaFichajeYMarcaProcesado() {
        ProcesoCierreDiario proceso = nuevoProceso(LUNES_FIJO);
        PlanificacionAusencia pendiente = nuevaPlanificacion(
                empleado1, LUNES_FIJO, TipoAusencia.VACACIONES);

        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(LUNES_FIJO))
                .thenReturn(List.of(empleado1));
        // Tarea A consulta primero (devuelve fichaje, no crea), Tarea B
        // consulta despues (devuelve empty, si crea). El stub encadenado
        // refleja ese orden.
        when(fichajeRepository.findByEmpleadoIdAndFecha(1L, LUNES_FIJO))
                .thenReturn(Optional.of(new Fichaje()))     // Tarea A: ya hay algo
                .thenReturn(Optional.empty());              // Tarea B: no hay aun
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(LUNES_FIJO.plusDays(1)))
                .thenReturn(List.of(pendiente));

        proceso.ejecutar();

        ArgumentCaptor<Fichaje> fichajeCaptor = ArgumentCaptor.forClass(Fichaje.class);
        verify(fichajeRepository).save(fichajeCaptor.capture());
        Fichaje creado = fichajeCaptor.getValue();
        assertThat(creado.getTipo()).isEqualTo(TipoFichaje.VACACIONES);
        assertThat(creado.getEmpleado()).isSameAs(empleado1);
        assertThat(creado.getFecha()).isEqualTo(LUNES_FIJO);

        // La planificacion queda marcada como procesada
        assertThat(pendiente.getProcesado()).isTrue();
        verify(ausenciaRepository).save(pendiente);
    }

    @Test
    @DisplayName("Tarea B: planificacion global (empleado=null) crea fichaje para todos los activos")
    void tareaB_planificacionGlobal_creaFichajeParaTodos() {
        ProcesoCierreDiario proceso = nuevoProceso(LUNES_FIJO);
        PlanificacionAusencia festivoGlobal = nuevaPlanificacion(
                null, LUNES_FIJO, TipoAusencia.FESTIVO_NACIONAL);

        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(LUNES_FIJO))
                .thenReturn(List.of(empleado1, empleado2));
        // Tarea A: ambos ya tienen fichaje (no nos interesa aqui).
        // Tarea B: ninguno tiene fichaje todavia.
        when(fichajeRepository.findByEmpleadoIdAndFecha(1L, LUNES_FIJO))
                .thenReturn(Optional.of(new Fichaje()))
                .thenReturn(Optional.empty());
        when(fichajeRepository.findByEmpleadoIdAndFecha(2L, LUNES_FIJO))
                .thenReturn(Optional.of(new Fichaje()))
                .thenReturn(Optional.empty());
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(LUNES_FIJO.plusDays(1)))
                .thenReturn(List.of(festivoGlobal));

        proceso.ejecutar();

        ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
        verify(fichajeRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Fichaje::getTipo)
                .containsOnly(TipoFichaje.FESTIVO_NACIONAL);
        assertThat(captor.getAllValues())
                .extracting(Fichaje::getEmpleado)
                .containsExactlyInAnyOrder(empleado1, empleado2);
        assertThat(festivoGlobal.getProcesado()).isTrue();
    }

    @Test
    @DisplayName("Tarea B: planificacion individual con fichaje preexistente no sobreescribe pero se marca procesada")
    void tareaB_conFichajePreexistente_noSobreescribePeroMarcaProcesado() {
        ProcesoCierreDiario proceso = nuevoProceso(LUNES_FIJO);
        PlanificacionAusencia pendiente = nuevaPlanificacion(
                empleado1, LUNES_FIJO, TipoAusencia.ASUNTO_PROPIO);

        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(LUNES_FIJO))
                .thenReturn(List.of(empleado1));
        // Tanto Tarea A como Tarea B ven fichaje existente.
        when(fichajeRepository.findByEmpleadoIdAndFecha(1L, LUNES_FIJO))
                .thenReturn(Optional.of(new Fichaje()));
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(LUNES_FIJO.plusDays(1)))
                .thenReturn(List.of(pendiente));

        proceso.ejecutar();

        verify(fichajeRepository, never()).save(any());
        // La planificacion se marca como procesada de todas formas:
        // el proceso considera que ya esta cubierta por el fichaje
        // existente y no debe volver a evaluarla.
        assertThat(pendiente.getProcesado()).isTrue();
        verify(ausenciaRepository).save(pendiente);
    }

    @Test
    @DisplayName("Tarea B: mapeo TipoAusencia → TipoFichaje cubre todos los valores")
    void tareaB_mapeoTipoAusenciaTipoFichajeCubreTodosLosValores() {
        ProcesoCierreDiario proceso = nuevoProceso(LUNES_FIJO);
        // Una planificacion por cada TipoAusencia, todas para empleado1.
        List<PlanificacionAusencia> todas = List.of(
                nuevaPlanificacion(empleado1, LUNES_FIJO, TipoAusencia.FESTIVO_NACIONAL),
                nuevaPlanificacion(empleado1, LUNES_FIJO, TipoAusencia.FESTIVO_LOCAL),
                nuevaPlanificacion(empleado1, LUNES_FIJO, TipoAusencia.VACACIONES),
                nuevaPlanificacion(empleado1, LUNES_FIJO, TipoAusencia.ASUNTO_PROPIO),
                nuevaPlanificacion(empleado1, LUNES_FIJO, TipoAusencia.PERMISO_RETRIBUIDO),
                nuevaPlanificacion(empleado1, LUNES_FIJO, TipoAusencia.DIA_LIBRE_COMPENSATORIO),
                nuevaPlanificacion(empleado1, LUNES_FIJO, TipoAusencia.DIA_LIBRE)
        );

        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(LUNES_FIJO))
                .thenReturn(List.of(empleado1));
        // Tarea A: hay fichaje (no crear). Tarea B: no hay (crea uno por planificacion).
        when(fichajeRepository.findByEmpleadoIdAndFecha(eq(1L), eq(LUNES_FIJO)))
                .thenReturn(Optional.of(new Fichaje()))
                .thenReturn(Optional.empty());
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(LUNES_FIJO.plusDays(1)))
                .thenReturn(todas);

        proceso.ejecutar();

        ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
        verify(fichajeRepository, times(7)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Fichaje::getTipo)
                .containsExactlyInAnyOrder(
                        TipoFichaje.FESTIVO_NACIONAL,
                        TipoFichaje.FESTIVO_LOCAL,
                        TipoFichaje.VACACIONES,
                        TipoFichaje.ASUNTO_PROPIO,
                        TipoFichaje.PERMISO_RETRIBUIDO,
                        TipoFichaje.DIA_LIBRE_COMPENSATORIO,
                        TipoFichaje.DIA_LIBRE);
    }

    // -------------------------------------------------------------------
    // Bloque adicional: DIA_LIBRE del dia siguiente si es fin de semana
    // -------------------------------------------------------------------

    @Test
    @DisplayName("Bloque DIA_LIBRE manana: viernes -> sabado siguiente crea DIA_LIBRE para activos")
    void diaLibreSiguiente_viernes_creaDiaLibreSabado() {
        ProcesoCierreDiario proceso = nuevoProceso(VIERNES_FIJO);
        LocalDate sabado = VIERNES_FIJO.plusDays(1);

        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(VIERNES_FIJO))
                .thenReturn(List.of(empleado1));
        // Tarea A: ya tiene fichaje el viernes.
        when(fichajeRepository.findByEmpleadoIdAndFecha(1L, VIERNES_FIJO))
                .thenReturn(Optional.of(new Fichaje()));
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(sabado))
                .thenReturn(Collections.emptyList());
        // Bloque DIA_LIBRE: empleado1 no tiene fichaje el sabado.
        when(fichajeRepository.findByEmpleadoIdAndFecha(1L, sabado))
                .thenReturn(Optional.empty());

        proceso.ejecutar();

        ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
        verify(fichajeRepository, times(1)).save(captor.capture());
        Fichaje creado = captor.getValue();
        assertThat(creado.getTipo()).isEqualTo(TipoFichaje.DIA_LIBRE);
        assertThat(creado.getFecha()).isEqualTo(sabado);
    }

    @Test
    @DisplayName("Bloque DIA_LIBRE manana: dia laborable siguiente NO crea DIA_LIBRE")
    void diaLibreSiguiente_diaLaborable_noCrea() {
        ProcesoCierreDiario proceso = nuevoProceso(LUNES_FIJO);   // manana es martes
        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(LUNES_FIJO))
                .thenReturn(List.of(empleado1));
        when(fichajeRepository.findByEmpleadoIdAndFecha(1L, LUNES_FIJO))
                .thenReturn(Optional.of(new Fichaje()));   // Tarea A: ya tiene
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(LUNES_FIJO.plusDays(1)))
                .thenReturn(Collections.emptyList());

        proceso.ejecutar();

        // No debe haberse guardado ningun fichaje: ni Tarea A (ya tenia),
        // ni Tarea B (no habia pendientes), ni bloque DIA_LIBRE (manana es martes).
        verify(fichajeRepository, never()).save(any());
    }

    // -------------------------------------------------------------------
    // Tarea C — Recalculo saldos
    // -------------------------------------------------------------------

    @Test
    @DisplayName("Tarea C: invoca recalcularParaProceso para cada empleado activo con anio del clock")
    void tareaC_recalculaSaldoParaCadaActivo() {
        ProcesoCierreDiario proceso = nuevoProceso(LUNES_FIJO);
        configurarUsuarioSistemaPresente();
        when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(LUNES_FIJO))
                .thenReturn(List.of(empleado1, empleado2));
        // Suponer que ambos tienen fichaje (Tarea A no crea, simplificamos).
        when(fichajeRepository.findByEmpleadoIdAndFecha(anyLong(), eq(LUNES_FIJO)))
                .thenReturn(Optional.of(new Fichaje()));
        when(ausenciaRepository.findPendientesByFechaLessThanEqual(LUNES_FIJO.plusDays(1)))
                .thenReturn(Collections.emptyList());

        proceso.ejecutar();

        int anioEsperado = LUNES_FIJO.getYear();
        verify(saldoService).recalcularParaProceso(1L, anioEsperado);
        verify(saldoService).recalcularParaProceso(2L, anioEsperado);
    }

    // -------------------------------------------------------------------
    // Error: terminal_service ausente
    // -------------------------------------------------------------------

    @Test
    @DisplayName("Falla si terminal_service no existe en BD")
    void faltaUsuarioSistema_lanzaNotFoundException() {
        ProcesoCierreDiario proceso = nuevoProceso(LUNES_FIJO);
        when(usuarioRepository.findByUsername("terminal_service"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(proceso::ejecutar)
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("terminal_service");

        // Ningun fichaje ni recalculo debe haberse intentado.
        verifyNoInteractions(fichajeRepository);
        verifyNoInteractions(ausenciaRepository);
        verifyNoInteractions(saldoService);
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /**
     * Construye una instancia de {@link ProcesoCierreDiario} con un
     * {@link Clock#fixed(java.time.Instant, ZoneId)} apuntando a la
     * fecha indicada (a las 23:55 UTC, hora aproximada del @Scheduled).
     *
     * @param fecha fecha que {@code LocalDate.now(clock)} devolvera
     * @return SUT con todos los mocks y el reloj fijado
     */
    private ProcesoCierreDiario nuevoProceso(LocalDate fecha) {
        Clock clockFijo = Clock.fixed(
                fecha.atTime(23, 55).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC);
        return new ProcesoCierreDiario(
                ausenciaRepository,
                fichajeRepository,
                empleadoRepository,
                usuarioRepository,
                saldoService,
                clockFijo);
    }

    /**
     * Configura el stub del usuario sistema (presente en BD). Se invoca
     * al inicio de cada test que necesita pasar la carga inicial.
     */
    private void configurarUsuarioSistemaPresente() {
        when(usuarioRepository.findByUsername("terminal_service"))
                .thenReturn(Optional.of(usuarioSistema));
    }

    private PlanificacionAusencia nuevaPlanificacion(Empleado empleado, LocalDate fecha,
                                                      TipoAusencia tipo) {
        PlanificacionAusencia p = new PlanificacionAusencia();
        p.setEmpleado(empleado);
        p.setFecha(fecha);
        p.setTipoAusencia(tipo);
        p.setProcesado(false);
        p.setUsuario(usuarioSistema);
        return p;
    }
}
