package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.PlanificacionAusencia;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.enums.TipoAusencia;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.response.EmpresaResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del bloque "informes de ausencias" de InformeService.
 *
 * <p>Cubre los tres endpoints publicos relacionados con informes de ausencias:
 * <ul>
 *   <li>E61 informeAusenciasMe: informe HTML de ausencias del empleado
 *       autenticado en un rango.</li>
 *   <li>E62 informeAusenciasEmpleado: informe HTML de ausencias de un
 *       empleado por id (ADMIN/ENCARGADO).</li>
 *   <li>E60 informeAusenciasGlobal: resumen HTML empleado x dia de
 *       ausencias de todos los empleados activos (ADMIN/ENCARGADO).</li>
 * </ul>
 *
 * <p><b>Estrategia sin Clock</b>: E61 y E62 no usan {@code LocalDate.now()}
 * funcionalmente (solo decorativo en cabecera "Generado el ..."). E60 SI
 * usa {@code LocalDate.now()} funcionalmente en L1722 para calcular
 * {@code esPasado}, {@code esFuturo}, {@code esSeleccionable} y las clases
 * CSS {@code td-hoy/td-futuro}. Para evitar inyectar Clock en esta sesion
 * los tests de E60 usan rangos en pasado lejano (2020) de modo que
 * {@code LocalDate.now()} (siempre &gt; 2020) garantiza que todos los dias
 * caigan en {@code esPasado=true}, dando un HTML determinista. Las ramas
 * {@code hoy/futuro/seleccionable=true} de E60 quedan sin cubrir y se
 * trataran en {@code InformeServiceSemanaTest} cuando se inyecte Clock
 * para E59, aprovechando el mismo bean.
 *
 * <p>Patron Mockito puro alineado con {@link InformeServiceHorasTest} y
 * {@link InformeServiceSaldosTest}: sin contexto de Spring, sin
 * SecurityContextHolder. Stubs estrictos (sin {@code lenient()}): solo
 * se stubea lo que cada rama realmente invoca.
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InformeService — informes de ausencias (E61, E62, E60)")
class InformeServiceAusenciasTest {

    @Mock private FichajeRepository fichajeRepository;
    @Mock private PausaRepository pausaRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private SaldoAnualRepository saldoRepository;
    @Mock private SaldoService saldoService;
    @Mock private EmpresaService empresaService;
    @Mock private PlanificacionAusenciaRepository planificacionRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private InformeService informeService;

    // Rango en pasado lejano: garantiza esPasado=true en E60 sin depender de Clock.
    private static final LocalDate DESDE = LocalDate.of(2020, 6, 1);
    private static final LocalDate HASTA = LocalDate.of(2020, 6, 7);

    private Usuario usuario;
    private Empleado empleado;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setUsername("emp.juan");
        usuario.setRol(Rol.ADMIN);

        empleado = new Empleado();
        empleado.setId(100L);
        empleado.setNombre("Juan");
        empleado.setApellido1("Perez");
        empleado.setApellido2("Garcia");
        empleado.setUsuario(usuario);
        empleado.setFechaAlta(LocalDate.of(2019, 1, 1));
        empleado.setActivo(true);
    }

    // ─── E62 informeAusenciasEmpleado ───────────────────────────────────────

    @Nested
    @DisplayName("E62 informeAusenciasEmpleado - GET /api/v1/ausencias/{empleadoId}/informe")
    class InformeAusenciasEmpleado {

        @Test
        @DisplayName("lanza EntityNotFoundException cuando el empleadoId no existe")
        void lanzaCuandoEmpleadoNoExiste() {
            when(empleadoRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> informeService.informeAusenciasEmpleado(
                    999L, DESDE, HASTA, "TODAS"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Empleado con id 999 no encontrado");
        }

        @Test
        @DisplayName("sin fichajes ni planificaciones devuelve HTML con cabecera y nombre del empleado")
        void sinDatosDevuelveHtmlMinimo() {
            mockAusenciasEmpleadoVacias();

            String html = informeService.informeAusenciasEmpleado(
                    100L, DESDE, HASTA, "TODAS");

            assertThat(html).contains("<!DOCTYPE html");
            assertThat(html).contains("Juan Perez Garcia");
            assertThat(html).contains("Informe de ausencias");
            // Tabla resumen presente con Total ausencias = 0.
            assertThat(html).contains("Total ausencias");
            assertThat(html).contains(">0<");
        }

        @Test
        @DisplayName("con una planificacion VACACIONES procesado=false aparece como Planificada")
        void planificacionNoProcesadaSaleComoPlanificada() {
            when(empleadoRepository.findById(100L)).thenReturn(Optional.of(empleado));
            when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
            when(fichajeRepository.findByEmpleadoIdAndFechaBetween(eq(100L), eq(DESDE), eq(HASTA)))
                    .thenReturn(Collections.emptyList());
            when(planificacionRepository.findByEmpleadoIdAndRango(eq(100L), eq(DESDE), eq(HASTA)))
                    .thenReturn(List.of(planificacion(LocalDate.of(2020, 6, 3),
                            TipoAusencia.VACACIONES, false, "Viaje")));

            String html = informeService.informeAusenciasEmpleado(
                    100L, DESDE, HASTA, "TODAS");

            assertThat(html).contains("Vacaciones");
            assertThat(html).contains("Planificada");
            assertThat(html).contains("Viaje");
        }

        @Test
        @DisplayName("con una planificacion VACACIONES procesado=true aparece como Ejecutada")
        void planificacionProcesadaSaleComoEjecutada() {
            when(empleadoRepository.findById(100L)).thenReturn(Optional.of(empleado));
            when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
            when(fichajeRepository.findByEmpleadoIdAndFechaBetween(eq(100L), eq(DESDE), eq(HASTA)))
                    .thenReturn(Collections.emptyList());
            when(planificacionRepository.findByEmpleadoIdAndRango(eq(100L), eq(DESDE), eq(HASTA)))
                    .thenReturn(List.of(planificacion(LocalDate.of(2020, 6, 3),
                            TipoAusencia.VACACIONES, true, null)));

            String html = informeService.informeAusenciasEmpleado(
                    100L, DESDE, HASTA, "TODAS");

            assertThat(html).contains("Vacaciones");
            assertThat(html).contains("Ejecutada");
        }

        @Test
        @DisplayName("filtro VACACIONES_AP descarta PERMISO_RETRIBUIDO y deja pasar VACACIONES")
        void filtroVacApDescarta() {
            when(empleadoRepository.findById(100L)).thenReturn(Optional.of(empleado));
            when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
            when(fichajeRepository.findByEmpleadoIdAndFechaBetween(eq(100L), eq(DESDE), eq(HASTA)))
                    .thenReturn(Collections.emptyList());
            when(planificacionRepository.findByEmpleadoIdAndRango(eq(100L), eq(DESDE), eq(HASTA)))
                    .thenReturn(List.of(
                            planificacion(LocalDate.of(2020, 6, 2),
                                    TipoAusencia.VACACIONES, false, null),
                            planificacion(LocalDate.of(2020, 6, 3),
                                    TipoAusencia.PERMISO_RETRIBUIDO, true, null)));

            String html = informeService.informeAusenciasEmpleado(
                    100L, DESDE, HASTA, "VACACIONES_AP");

            assertThat(html).contains("Vacaciones");
            // La cabecera del filtro debe estar presente.
            assertThat(html).contains("Filtro: Vacaciones y asuntos propios");
            // PERMISO_RETRIBUIDO (TIPO_LEGIBLE = "Permiso retribuido") no debe aparecer
            // en el detalle porque el filtro VACACIONES_AP solo deja pasar VACACIONES
            // y ASUNTO_PROPIO (ver TIPOS_VACACIONES_AP en InformeService L95).
            assertThat(html).doesNotContain("Permiso retribuido");
        }

        @Test
        @DisplayName("fichaje y planificacion en misma fecha: el fichaje pisa a la planificacion")
        void fichajeOverridePlanificacion() {
            // Misma fecha (2020-06-04) con planificacion VACACIONES y fichaje BAJA_MEDICA.
            // El service mete primero la planificacion en el LinkedHashMap y luego el
            // fichaje, que sobreescribe (es la fuente autoritativa por ser ejecutado).
            when(empleadoRepository.findById(100L)).thenReturn(Optional.of(empleado));
            when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
            when(fichajeRepository.findByEmpleadoIdAndFechaBetween(eq(100L), eq(DESDE), eq(HASTA)))
                    .thenReturn(List.of(fichaje(LocalDate.of(2020, 6, 4),
                            TipoFichaje.BAJA_MEDICA, "Doctor")));
            when(planificacionRepository.findByEmpleadoIdAndRango(eq(100L), eq(DESDE), eq(HASTA)))
                    .thenReturn(List.of(planificacion(LocalDate.of(2020, 6, 4),
                            TipoAusencia.VACACIONES, false, "Viaje cancelado")));

            String html = informeService.informeAusenciasEmpleado(
                    100L, DESDE, HASTA, "TODAS");

            // Gana el fichaje: aparece BAJA_MEDICA (Baja médica) como Ejecutada.
            assertThat(html).contains("Baja médica");
            assertThat(html).contains("Doctor");
            assertThat(html).contains("Ejecutada");
            // La planificacion sobreescrita no debe filtrar sus campos al detalle.
            assertThat(html).doesNotContain("Viaje cancelado");
        }
    }

    // ─── E61 informeAusenciasMe ─────────────────────────────────────────────

    @Nested
    @DisplayName("E61 informeAusenciasMe - GET /api/v1/ausencias/me/informe")
    class InformeAusenciasMe {

        @Test
        @DisplayName("lanza EntityNotFoundException cuando el username autenticado no existe")
        void lanzaCuandoUsuarioNoExiste() {
            when(usuarioRepository.findByUsername("emp.fantasma"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> informeService.informeAusenciasMe(
                    "emp.fantasma", DESDE, HASTA, "TODAS"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Usuario autenticado no encontrado");
        }

        @Test
        @DisplayName("lanza EntityNotFoundException cuando el usuario no tiene perfil de empleado")
        void lanzaCuandoUsuarioSinPerfilEmpleado() {
            when(usuarioRepository.findByUsername("emp.juan"))
                    .thenReturn(Optional.of(usuario));
            when(empleadoRepository.findByUsuarioId(10L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> informeService.informeAusenciasMe(
                    "emp.juan", DESDE, HASTA, "TODAS"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("no tiene perfil de empleado");
        }

        @Test
        @DisplayName("camino feliz resuelve empleado por username y delega al mismo motor")
        void caminoFelizGeneraHtml() {
            when(usuarioRepository.findByUsername("emp.juan"))
                    .thenReturn(Optional.of(usuario));
            when(empleadoRepository.findByUsuarioId(10L))
                    .thenReturn(Optional.of(empleado));
            when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
            when(fichajeRepository.findByEmpleadoIdAndFechaBetween(eq(100L), eq(DESDE), eq(HASTA)))
                    .thenReturn(Collections.emptyList());
            when(planificacionRepository.findByEmpleadoIdAndRango(eq(100L), eq(DESDE), eq(HASTA)))
                    .thenReturn(Collections.emptyList());

            String html = informeService.informeAusenciasMe(
                    "emp.juan", DESDE, HASTA, "TODAS");

            assertThat(html).contains("<!DOCTYPE html");
            assertThat(html).contains("Juan Perez Garcia");
            assertThat(html).contains("Informe de ausencias");
        }
    }

    // ─── E60 informeAusenciasGlobal ─────────────────────────────────────────

    @Nested
    @DisplayName("E60 informeAusenciasGlobal - GET /api/v1/informes/ausencias")
    class InformeAusenciasGlobal {

        @Test
        @DisplayName("lanza EntityNotFoundException cuando el username autenticado no existe")
        void lanzaCuandoUsuarioNoExiste() {
            when(usuarioRepository.findByUsername("admin.fantasma"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> informeService.informeAusenciasGlobal(
                    DESDE, HASTA, "admin.fantasma"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Usuario no encontrado");
        }

        @Test
        @DisplayName("sin empleados activos el HTML contiene 'No hay empleados activos'")
        void sinEmpleadosMuestraMensaje() {
            when(usuarioRepository.findByUsername("emp.juan")).thenReturn(Optional.of(usuario));
            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(HASTA))
                    .thenReturn(Collections.emptyList());
            when(fichajeRepository.findByFiltros(eq(null), eq(DESDE), eq(HASTA), eq(null)))
                    .thenReturn(Collections.emptyList());
            when(planificacionRepository.findByFiltros(eq(null), eq(DESDE), eq(HASTA), eq(null)))
                    .thenReturn(Collections.emptyList());

            String html = informeService.informeAusenciasGlobal(DESDE, HASTA, "emp.juan");

            assertThat(html).contains("<!DOCTYPE html");
            assertThat(html).contains("No hay empleados activos");
        }

        @Test
        @DisplayName("con un empleado activo y rango en pasado: fila con nombre, sin celdas seleccionables")
        void unEmpleadoActivoEnPasado() {
            when(usuarioRepository.findByUsername("emp.juan")).thenReturn(Optional.of(usuario));
            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(HASTA))
                    .thenReturn(List.of(empleado));
            when(fichajeRepository.findByFiltros(eq(null), eq(DESDE), eq(HASTA), eq(null)))
                    .thenReturn(Collections.emptyList());
            when(planificacionRepository.findByFiltros(eq(null), eq(DESDE), eq(HASTA), eq(null)))
                    .thenReturn(Collections.emptyList());

            String html = informeService.informeAusenciasGlobal(DESDE, HASTA, "emp.juan");

            assertThat(html).contains("Juan Perez Garcia");
            assertThat(html).contains("Ausencias");
            // Rango en pasado lejano (2020) garantiza esPasado=true para TODOS los dias
            // del rango sin necesidad de Clock. Cuando esSeleccionable=false el SUT no
            // anade los atributos data-emp / data-fecha a las celdas (ver InformeService
            // L1835-1838: solo se anaden dentro del if(esSeleccionable)). La clase CSS
            // .seleccionable aparece SIEMPRE en el bloque <style> aunque ninguna celda
            // la lleve, por lo que asertar "doesNotContain('seleccionable')" daria
            // falso positivo. El test correcto es verificar la AUSENCIA de data-fecha
            // en las celdas <td>.
            assertThat(html).doesNotContain("data-fecha=");
        }

        @Test
        @DisplayName("festivo global (empleado=null) aparece en la celda del dia para todos")
        void festivoGlobalAparece() {
            when(usuarioRepository.findByUsername("emp.juan")).thenReturn(Optional.of(usuario));
            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(HASTA))
                    .thenReturn(List.of(empleado));
            when(fichajeRepository.findByFiltros(eq(null), eq(DESDE), eq(HASTA), eq(null)))
                    .thenReturn(Collections.emptyList());

            // Festivo global: empleado=null, fecha dentro del rango, tipo FESTIVO_NACIONAL.
            PlanificacionAusencia festivo = new PlanificacionAusencia();
            festivo.setId(500L);
            festivo.setEmpleado(null);
            festivo.setFecha(LocalDate.of(2020, 6, 4));
            festivo.setTipoAusencia(TipoAusencia.FESTIVO_NACIONAL);
            festivo.setProcesado(false);
            when(planificacionRepository.findByFiltros(eq(null), eq(DESDE), eq(HASTA), eq(null)))
                    .thenReturn(List.of(festivo));

            String html = informeService.informeAusenciasGlobal(DESDE, HASTA, "emp.juan");

            // El festivo global se renderiza con celdaAusenciaPlanificada igual que una
            // ausencia individual; el TIPO_LEGIBLE de FESTIVO_NACIONAL es "Festivo nacional".
            assertThat(html).contains("Festivo nacional");
        }
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────

    /**
     * Mockea el caso base feliz de E62 sin fichajes ni planificaciones.
     * Sirve para tests que solo verifican la cabecera del HTML.
     */
    private void mockAusenciasEmpleadoVacias() {
        when(empleadoRepository.findById(100L)).thenReturn(Optional.of(empleado));
        when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
        when(fichajeRepository.findByEmpleadoIdAndFechaBetween(
                eq(100L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(planificacionRepository.findByEmpleadoIdAndRango(
                eq(100L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
    }

    private EmpresaResponse buildEmpresaResponse() {
        EmpresaResponse r = new EmpresaResponse();
        r.setId(1L);
        r.setNombreEmpresa("ACME S.L.");
        return r;
    }

    private PlanificacionAusencia planificacion(LocalDate fecha, TipoAusencia tipo,
                                                 boolean procesado, String observaciones) {
        PlanificacionAusencia p = new PlanificacionAusencia();
        p.setId((long) (fecha.getDayOfMonth() + 1000));
        p.setEmpleado(empleado);
        p.setFecha(fecha);
        p.setTipoAusencia(tipo);
        p.setProcesado(procesado);
        p.setObservaciones(observaciones);
        return p;
    }

    private Fichaje fichaje(LocalDate fecha, TipoFichaje tipo, String observaciones) {
        Fichaje f = new Fichaje();
        f.setId((long) (fecha.getDayOfMonth() + 2000));
        f.setEmpleado(empleado);
        f.setFecha(fecha);
        f.setTipo(tipo);
        f.setObservaciones(observaciones);
        return f;
    }
}
