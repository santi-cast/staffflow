package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.SaldoAnual;
import com.staffflow.domain.enums.TipoAusencia;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del bloque "informe de saldos" de InformeService.
 *
 * <p>Cubre el unico endpoint publico del bloque:
 * <ul>
 *   <li>E44 informeSaldos: informe anual de saldos (vacaciones, asuntos propios,
 *       resto de dias, horas, control) de uno o varios empleados activos, con
 *       seleccion de columnas por bloques o campos individuales.</li>
 * </ul>
 *
 * <p>Service con Clock inyectado (sexto service del backend con Clock), pero
 * este bloque no lo consume funcionalmente: E44 no invoca
 * {@code calcularSaldoHastaFecha} (helper privado de E59 con
 * {@code LocalDate.now(clock).minusDays(1)} para el checkpoint del cierre
 * nocturno). El {@code LocalDate.now()} de {@code generarHtmlSaldos} es
 * decorativo (cabecera "Generado el ...") y no afecta los datos calculados.
 * Se declara un {@code Clock.fixed(2026-01-15, Europe/Madrid)} real como
 * campo inicializado por exigencia del constructor de {@code InformeService};
 * Mockito respeta el valor no nulo y {@code @InjectMocks} no lo sobreescribe.
 *
 * <p>Patron Mockito puro alineado con {@link InformeServiceHorasTest},
 * {@link AusenciaServiceTest} y {@link FichajeServiceTest}: sin contexto
 * de Spring, sin SecurityContextHolder. Stubs estrictos (sin
 * {@code lenient()}): solo se stubea lo que cada rama realmente invoca.
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InformeService — informe de saldos (E44)")
class InformeServiceSaldosTest {

    @Mock private FichajeRepository fichajeRepository;
    @Mock private PausaRepository pausaRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private SaldoAnualRepository saldoRepository;
    @Mock private SaldoService saldoService;
    @Mock private EmpresaService empresaService;
    @Mock private PlanificacionAusenciaRepository planificacionRepository;
    @Mock private UsuarioRepository usuarioRepository;

    // Clock real fijado (no @Mock) por exigencia del constructor. E44 no invoca
    // clock.instant(); el valor solo evita NPE si en el futuro algun test cae
    // en las ramas funcionales del service.
    private final Clock clock = Clock.fixed(
            LocalDate.of(2026, 1, 15).atStartOfDay(ZoneId.of("Europe/Madrid")).toInstant(),
            ZoneId.of("Europe/Madrid"));

    private InformeService informeService;

    private Empleado empleadoA;
    private Empleado empleadoZ;
    private SaldoAnual saldoA;
    private SaldoAnual saldoZ;

    @BeforeEach
    void setUp() {
        informeService = new InformeService(
                fichajeRepository,
                pausaRepository,
                empleadoRepository,
                saldoRepository,
                saldoService,
                empresaService,
                planificacionRepository,
                usuarioRepository,
                clock);

        // Empleado "A..." → ordenado primero por nombreCompleto.
        empleadoA = new Empleado();
        empleadoA.setId(1L);
        empleadoA.setNombre("Ana");
        empleadoA.setApellido1("Alvarez");
        empleadoA.setActivo(true);

        // Empleado "Z..." → ordenado ultimo por nombreCompleto.
        empleadoZ = new Empleado();
        empleadoZ.setId(2L);
        empleadoZ.setNombre("Zoe");
        empleadoZ.setApellido1("Zambrano");
        empleadoZ.setActivo(true);

        saldoA = buildSaldo(empleadoA, 2026);
        saldoZ = buildSaldo(empleadoZ, 2026);
    }

    // ─── Errores ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Errores y empty state")
    class Errores {

        @Test
        @DisplayName("sin datos de saldo para el año lanza NotFoundException")
        void sinDatosLanzaNotFound() {
            when(saldoRepository.findByAnio(2026)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> informeService.informeSaldos(2026, "json", null, null))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("No hay datos de saldo para el año 2026");
        }

        @Test
        @DisplayName("saldos solo de empleados inactivos se filtran y deja lista vacia → NotFound")
        void soloInactivosLanzaNotFound() {
            empleadoA.setActivo(false);
            when(saldoRepository.findByAnio(2026)).thenReturn(List.of(saldoA));

            assertThatThrownBy(() -> informeService.informeSaldos(2026, "json", null, null))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("2026");
        }
    }

    // ─── Completado on-demand y filtros de empleado ─────────────────────────

    @Nested
    @DisplayName("Completado on-demand y filtros de empleado")
    class CompletadoYFiltros {

        @Test
        @DisplayName("completa on-demand solo los empleados activos sin saldo en el año")
        void completaOnDemandSoloLosQueFaltan() {
            // empleadoA ya tiene saldo; empleadoZ activo pero sin saldo → debe recalcularse.
            when(saldoRepository.findByAnio(2026)).thenReturn(List.of(saldoA));
            when(empleadoRepository.findAll()).thenReturn(List.of(empleadoA, empleadoZ));
            when(saldoRepository.findByEmpleadoIdAndAnio(1L, 2026)).thenReturn(Optional.of(saldoA));
            when(saldoRepository.findByEmpleadoIdAndAnio(2L, 2026)).thenReturn(Optional.empty());

            informeService.informeSaldos(2026, "json", null, List.of("VAC_DISPONIBLES"));

            verify(saldoService, times(1)).recalcularParaProceso(2L, 2026);
            verify(saldoService, never()).recalcularParaProceso(eq(1L), anyInt());
        }

        @Test
        @DisplayName("sin filtro de empleados ordena el JSON por nombre completo")
        void sinFiltroOrdenaPorNombre() {
            // Devolvemos primero Z y luego A para forzar que el orden venga del sort del service.
            when(saldoRepository.findByAnio(2026)).thenReturn(List.of(saldoZ, saldoA));
            when(empleadoRepository.findAll()).thenReturn(List.of(empleadoA, empleadoZ));

            Object resultado = informeService.informeSaldos(
                    2026, "json", null, List.of("VAC_DISPONIBLES"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) resultado;
            assertThat(filas).hasSize(2);
            assertThat(filas.get(0).get("empleadoId")).isEqualTo(1L); // Ana antes que Zoe
            assertThat(filas.get(1).get("empleadoId")).isEqualTo(2L);
        }

        @Test
        @DisplayName("con filtro empleadoIds devuelve solo esos, ordenados por nombre")
        void conFiltroDevuelveSoloEsos() {
            when(saldoRepository.findByAnio(2026)).thenReturn(List.of(saldoA, saldoZ));
            when(empleadoRepository.findAll()).thenReturn(List.of(empleadoA, empleadoZ));
            when(saldoRepository.findByEmpleadoIdAndAnio(2L, 2026)).thenReturn(Optional.of(saldoZ));
            when(saldoRepository.findByEmpleadoIdAndAnio(1L, 2026)).thenReturn(Optional.of(saldoA));

            Object resultado = informeService.informeSaldos(
                    2026, "json", List.of(2L, 1L), List.of("VAC_DISPONIBLES"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) resultado;
            assertThat(filas).hasSize(2);
            // Aunque el filtro pidio [2, 1], la salida se ordena por nombreCompleto (Ana, Zoe).
            assertThat(filas.get(0).get("empleadoId")).isEqualTo(1L);
            assertThat(filas.get(1).get("empleadoId")).isEqualTo(2L);
        }

        @Test
        @DisplayName("con filtro empleadoIds y un id inexistente se ignora silenciosamente")
        void filtroConIdInexistenteSeIgnora() {
            when(saldoRepository.findByAnio(2026)).thenReturn(List.of(saldoA));
            when(empleadoRepository.findAll()).thenReturn(List.of(empleadoA));
            when(saldoRepository.findByEmpleadoIdAndAnio(1L, 2026)).thenReturn(Optional.of(saldoA));
            when(saldoRepository.findByEmpleadoIdAndAnio(999L, 2026)).thenReturn(Optional.empty());

            Object resultado = informeService.informeSaldos(
                    2026, "json", List.of(1L, 999L), List.of("VAC_DISPONIBLES"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) resultado;
            assertThat(filas).hasSize(1);
            assertThat(filas.get(0).get("empleadoId")).isEqualTo(1L);
        }
    }

    // ─── Resolucion de campos ───────────────────────────────────────────────

    @Nested
    @DisplayName("Resolucion de campos (bloques, individuales, invalidos)")
    class ResolucionCampos {

        @Test
        @DisplayName("campos null devuelve todos los campos (CAMPOS_VALIDOS completos)")
        void camposNullDevuelveTodos() {
            mockUnSaldoConPlanificacionVacia();

            Object resultado = informeService.informeSaldos(2026, "json", null, null);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) resultado;
            Map<String, Object> fila = filas.get(0);
            // Una clave representativa de cada bloque (5 bloques).
            assertThat(fila).containsKeys(
                    "vacPendientesAnioAnterior",        // bloque DIAS_VACACIONES
                    "apDerechoAnual",                    // bloque DIAS_ASUNTOS_PROPIOS
                    "diasTrabajados",                    // bloque RESTO_DIAS
                    "saldoHoras",                        // bloque HORAS
                    "calculadoHasta", "ultimaModificacion" // bloque CONTROL
            );
        }

        @Test
        @DisplayName("campo individual VAC_DISPONIBLES devuelve solo esa clave de saldo")
        void campoIndividualDevuelveSoloEsa() {
            when(saldoRepository.findByAnio(2026)).thenReturn(List.of(saldoA));
            when(empleadoRepository.findAll()).thenReturn(List.of(empleadoA));

            Object resultado = informeService.informeSaldos(
                    2026, "json", null, List.of("VAC_DISPONIBLES"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) resultado;
            Map<String, Object> fila = filas.get(0);
            // Siempre presentes: empleadoId, empleado, anio + el campo pedido.
            assertThat(fila).containsKeys("empleadoId", "empleado", "anio", "vacDisponibles");
            // No debe incluir claves de otros campos/bloques.
            assertThat(fila).doesNotContainKeys(
                    "vacDerechoAnual", "apDerechoAnual",
                    "diasTrabajados", "saldoHoras", "calculadoHasta");
        }

        @Test
        @DisplayName("bloque DIAS_VACACIONES expande a los 5 campos del bloque")
        void bloqueExpandeATodosSusCampos() {
            mockUnSaldoConPlanificacionVacia();

            Object resultado = informeService.informeSaldos(
                    2026, "json", null, List.of("DIAS_VACACIONES"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) resultado;
            Map<String, Object> fila = filas.get(0);
            assertThat(fila).containsKeys(
                    "vacPendientesAnioAnterior", "vacDerechoAnual",
                    "vacConsumidosAnioEnCurso", "vacDisponibles",
                    "vacPendientesPlanificar");
            // No debe filtrarse ningun campo de los otros bloques.
            assertThat(fila).doesNotContainKeys(
                    "apDerechoAnual", "diasTrabajados", "saldoHoras");
        }

        @Test
        @DisplayName("campos invalidos se ignoran y al quedar vacio resuelve a todos")
        void camposInvalidosResuelveATodos() {
            mockUnSaldoConPlanificacionVacia();

            Object resultado = informeService.informeSaldos(
                    2026, "json", null, List.of("XXX_INVALIDO", "OTRO_FANTASMA"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) resultado;
            Map<String, Object> fila = filas.get(0);
            // Tras ignorar los invalidos, el resultado queda vacio → activa CAMPOS_VALIDOS completos.
            assertThat(fila).containsKeys(
                    "vacDerechoAnual", "apDerechoAnual",
                    "diasTrabajados", "saldoHoras", "calculadoHasta");
        }
    }

    // ─── Pendientes por planificar ──────────────────────────────────────────

    @Nested
    @DisplayName("Pendientes por planificar (carga agregada)")
    class PendientesPlanificar {

        @Test
        @DisplayName("VAC_PENDIENTE_PLANIF invoca planificacionRepository y resta planificados")
        void vacPendientePlanifRestaPlanificados() {
            // Saldo con 20 dias de vacaciones disponibles.
            saldoA.setDiasVacacionesDisponibles(20);
            when(saldoRepository.findByAnio(2026)).thenReturn(List.of(saldoA));
            when(empleadoRepository.findAll()).thenReturn(List.of(empleadoA));
            // 5 dias planificados de vacaciones para empleadoA en 2026.
            Object[] fila = new Object[] { 1L, TipoAusencia.VACACIONES, 5L };
            when(planificacionRepository.countPlanificadasVacApPorEmpleadoEnRango(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                    .thenReturn(List.<Object[]>of(fila));

            Object resultado = informeService.informeSaldos(
                    2026, "json", null, List.of("VAC_DISPONIBLES", "VAC_PENDIENTE_PLANIF"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) resultado;
            assertThat(filas.get(0).get("vacDisponibles")).isEqualTo(20);
            // 20 disponibles - 5 planificados = 15 pendientes por planificar.
            assertThat(filas.get(0).get("vacPendientesPlanificar")).isEqualTo(15);
            verify(planificacionRepository).countPlanificadasVacApPorEmpleadoEnRango(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        }

        @Test
        @DisplayName("sin campos PENDIENTE_PLANIF no se consulta planificacionRepository")
        void sinCampoPendientePlanifNoConsultaPlanificacion() {
            when(saldoRepository.findByAnio(2026)).thenReturn(List.of(saldoA));
            when(empleadoRepository.findAll()).thenReturn(List.of(empleadoA));

            informeService.informeSaldos(
                    2026, "json", null, List.of("VAC_DISPONIBLES", "DIAS_TRABAJADOS"));

            // Optimizacion del service (L353-360): no carga la query agregada si
            // ningun campo activo la necesita.
            verifyNoInteractions(planificacionRepository);
        }
    }

    // ─── Formato HTML ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Formato HTML")
    class FormatoHtml {

        @Test
        @DisplayName("formato=html devuelve String con la cabecera del informe")
        void formatoHtmlDevuelveString() {
            mockUnSaldoConPlanificacionVacia();

            Object resultado = informeService.informeSaldos(2026, "html", null, null);

            assertThat(resultado).isInstanceOf(String.class);
            String html = (String) resultado;
            assertThat(html).contains("INFORME DE SALDOS");
            assertThat(html).contains("2026");
            assertThat(html).contains("Ana"); // nombreCompleto del empleadoA
        }
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────

    /**
     * Mockea el caso base para tests que activan TODOS los campos (incluidos
     * los PENDIENTE_PLANIF que dispara la carga agregada). Un solo saldo de
     * empleadoA en 2026, sin planificaciones registradas.
     */
    private void mockUnSaldoConPlanificacionVacia() {
        when(saldoRepository.findByAnio(2026)).thenReturn(List.of(saldoA));
        when(empleadoRepository.findAll()).thenReturn(List.of(empleadoA));
        when(planificacionRepository.countPlanificadasVacApPorEmpleadoEnRango(
                any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
    }

    /**
     * Construye un SaldoAnual con valores plausibles para todos los campos
     * del informe. Sirve para tests donde el contenido exacto no importa:
     * lo que se verifica son las claves presentes/ausentes en el JSON.
     */
    private SaldoAnual buildSaldo(Empleado empleado, int anio) {
        SaldoAnual s = new SaldoAnual();
        s.setEmpleado(empleado);
        s.setAnio(anio);
        s.setDiasVacacionesPendientesAnioAnterior(0);
        s.setDiasVacacionesDerechoAnio(22);
        s.setDiasVacacionesConsumidos(0);
        s.setDiasVacacionesDisponibles(22);
        s.setDiasAsuntosPropiosPendientesAnterior(0);
        s.setDiasAsuntosPropiosDerechoAnio(6);
        s.setDiasAsuntosPropiosConsumidos(0);
        s.setDiasAsuntosPropiosDisponibles(6);
        s.setDiasTrabajados(0);
        s.setDiasBajaMedica(0);
        s.setDiasPermisoRetribuido(0);
        s.setDiasAusenciaInjustificada(0);
        s.setHorasAusenciaRetribuida(BigDecimal.ZERO);
        s.setSaldoHoras(BigDecimal.ZERO);
        s.setCalculadoHastaFecha(LocalDate.of(anio, 1, 1));
        return s;
    }
}
