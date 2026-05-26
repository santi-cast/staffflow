package com.staffflow.service;

import com.staffflow.domain.entity.ConfiguracionEmpresa;
import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.response.EmpresaResponse;
import com.staffflow.exception.NotFoundException;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del bloque "informes de horas" de InformeService.
 *
 * <p>Cubre los tres endpoints publicos relacionados con informes de jornada:
 * <ul>
 *   <li>E58 informeHorasMe: informe de horas del empleado autenticado (delega a E42).</li>
 *   <li>E42 informeHorasEmpleado: informe de horas de un empleado por id.</li>
 *   <li>E43 informeHorasGlobal: informe de horas de todos los empleados activos.</li>
 * </ul>
 *
 * <p>Service sin Clock para este bloque: los tres endpoints no invocan
 * {@code calcularSaldoHastaFecha} (unico punto con logica temporal real,
 * exclusivo de E59). Los {@code LocalDateTime.now()} que aparecen en los
 * helpers HTML son decorativos (cabecera "Generado el ...") y no afectan
 * el calculo, por lo que los tests no necesitan determinismo temporal.
 *
 * <p>Patron Mockito puro alineado con {@link AusenciaServiceTest} y
 * {@link FichajeServiceTest}: sin contexto de Spring, sin SecurityContextHolder.
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InformeService — informes de horas (E58, E42, E43)")
class InformeServiceHorasTest {

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

    private Usuario usuario;
    private Empleado empleado;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setUsername("emp.juan");

        empleado = new Empleado();
        empleado.setId(100L);
        empleado.setNombre("Juan");
        empleado.setApellido1("Perez");
        empleado.setApellido2("Garcia");
        empleado.setUsuario(usuario);
        empleado.setFechaAlta(LocalDate.of(2025, 1, 1));
        empleado.setActivo(true);
    }

    // ─── E58 informeHorasMe ─────────────────────────────────────────────────

    @Nested
    @DisplayName("E58 informeHorasMe - GET /api/v1/informes/me/horas")
    class InformeHorasMe {

        @Test
        @DisplayName("lanza EntityNotFoundException cuando el username autenticado no existe")
        void lanzaCuandoUsuarioNoExiste() {
            when(usuarioRepository.findByUsername("emp.fantasma"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> informeService.informeHorasMe(
                    "emp.fantasma", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
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

            assertThatThrownBy(() -> informeService.informeHorasMe(
                    "emp.juan", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("no tiene perfil de empleado");
        }

        @Test
        @DisplayName("delega a informeHorasEmpleado con formato=html y devuelve String HTML")
        void delegaAEmpleadoConHtml() {
            when(usuarioRepository.findByUsername("emp.juan"))
                    .thenReturn(Optional.of(usuario));
            when(empleadoRepository.findByUsuarioId(10L))
                    .thenReturn(Optional.of(empleado));
            mockHorasVacias();

            Object resultado = informeService.informeHorasMe(
                    "emp.juan", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5));

            // E58 fija formato="html" al delegar a E42 → debe devolver String HTML.
            assertThat(resultado).isInstanceOf(String.class);
            assertThat((String) resultado).contains("<!DOCTYPE html");
        }
    }

    // ─── E42 informeHorasEmpleado ───────────────────────────────────────────

    @Nested
    @DisplayName("E42 informeHorasEmpleado - GET /api/v1/informes/horas/{empleadoId}")
    class InformeHorasEmpleado {

        @Test
        @DisplayName("lanza NotFoundException cuando el empleadoId no existe")
        void lanzaCuandoEmpleadoNoExiste() {
            when(empleadoRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> informeService.informeHorasEmpleado(
                    999L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                    "json", null))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Empleado no encontrado con id 999");
        }

        @Test
        @DisplayName("formato=html devuelve String que contiene la cabecera del documento")
        void formatoHtmlDevuelveString() {
            mockHorasVacias();

            Object resultado = informeService.informeHorasEmpleado(
                    100L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                    "html", null);

            assertThat(resultado).isInstanceOf(String.class);
            String html = (String) resultado;
            assertThat(html).contains("<!DOCTYPE html");
            assertThat(html).contains("Juan");
        }

        @Test
        @DisplayName("formato=json devuelve Map con empleado, dias y resumen")
        void formatoJsonDevuelveMap() {
            mockHorasVacias();

            Object resultado = informeService.informeHorasEmpleado(
                    100L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                    "json", null);

            assertThat(resultado).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) resultado;
            // Claves reales del JSON de E42: empleado, periodo, resumen, detalle.
            assertThat(map).containsKeys("empleado", "periodo", "resumen", "detalle");
        }

        @Test
        @DisplayName("formato null o desconocido se trata como json (no como html)")
        void formatoDistintoDeHtmlEsJson() {
            mockHorasVacias();

            Object conNull = informeService.informeHorasEmpleado(
                    100L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                    null, null);
            Object conRaro = informeService.informeHorasEmpleado(
                    100L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                    "xml-no-soportado", null);

            assertThat(conNull).isInstanceOf(Map.class);
            assertThat(conRaro).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("incluye los dias del rango en la respuesta JSON aunque no haya fichajes")
        void incluyeDiasDelRangoSinFichajes() {
            mockHorasVacias();

            Object resultado = informeService.informeHorasEmpleado(
                    100L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7),
                    "json", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) resultado;
            @SuppressWarnings("unchecked")
            List<Object> detalle = (List<Object>) map.get("detalle");
            // Rango de 3 dias [5,6,7] → la lista "detalle" del informe tiene 3 entradas.
            assertThat(detalle).hasSize(3);
        }

        @Test
        @DisplayName("con un fichaje NORMAL el JSON refleja la jornada del dia")
        void conFichajeNormalJsonRefleja() {
            when(empleadoRepository.findById(100L)).thenReturn(Optional.of(empleado));
            when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());

            Fichaje fichaje = new Fichaje();
            fichaje.setId(500L);
            fichaje.setEmpleado(empleado);
            fichaje.setFecha(LocalDate.of(2026, 1, 5));
            fichaje.setTipo(TipoFichaje.NORMAL);
            fichaje.setHoraEntrada(LocalDateTime.of(2026, 1, 5, 9, 0));
            fichaje.setHoraSalida(LocalDateTime.of(2026, 1, 5, 17, 0));
            fichaje.setJornadaEfectivaMinutos(480);

            when(fichajeRepository.findByEmpleadoIdAndFechaBetween(
                    eq(100L), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of(fichaje));
            when(pausaRepository.findByEmpleadoIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
                    eq(100L), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            Object resultado = informeService.informeHorasEmpleado(
                    100L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                    "json", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) resultado;
            @SuppressWarnings("unchecked")
            Map<String, Object> resumen = (Map<String, Object>) map.get("resumen");
            // 480 minutos = 8h 00min (formato real de formatearMinutos en
            // InformeService L2181-2185: "Nh MMmin").
            assertThat(resumen.get("diasTrabajados")).isEqualTo(1);
            assertThat(resumen.get("horasEfectivas")).isEqualTo("8h 00min");
        }
    }

    // ─── E43 informeHorasGlobal ─────────────────────────────────────────────

    @Nested
    @DisplayName("E43 informeHorasGlobal - GET /api/v1/informes/horas")
    class InformeHorasGlobal {

        @Test
        @DisplayName("sin empleados activos devuelve lista vacia en JSON")
        void sinEmpleadosListaVacia() {
            when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            Object resultado = informeService.informeHorasGlobal(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                    "json", null);

            assertThat(resultado).isInstanceOf(List.class);
            assertThat((List<?>) resultado).isEmpty();
        }

        @Test
        @DisplayName("con un empleado activo construye una fila en el JSON global")
        void conUnEmpleadoUnaFila() {
            when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(any(LocalDate.class)))
                    .thenReturn(List.of(empleado));
            when(fichajeRepository.findByEmpleadoIdAndFechaBetween(
                    eq(100L), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(pausaRepository.findByEmpleadoIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
                    eq(100L), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            Object resultado = informeService.informeHorasGlobal(
                    LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                    "json", null);

            assertThat(resultado).isInstanceOf(List.class);
            assertThat((List<?>) resultado).hasSize(1);
        }

        @Test
        @DisplayName("formato=html devuelve String HTML")
        void formatoHtmlDevuelveString() {
            when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            Object resultado = informeService.informeHorasGlobal(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                    "html", null);

            assertThat(resultado).isInstanceOf(String.class);
            assertThat((String) resultado).contains("<!DOCTYPE html");
        }

        @Test
        @DisplayName("formato null o desconocido se trata como json (no como html)")
        void formatoDistintoDeHtmlEsJson() {
            when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            Object conNull = informeService.informeHorasGlobal(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                    null, null);

            assertThat(conNull).isInstanceOf(List.class);
        }
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────

    /**
     * Mockea las dependencias minimas de E42 para un caso feliz sin fichajes
     * ni pausas en el periodo. El SUT solo consulta
     * {@code planificacionRepository.existsByEmpleadoIdAndFecha} cuando el
     * dia TIENE fichaje y el creador del fichaje NO es EMPLEADO ni el usuario
     * tecnico {@code terminal_service} (ver InformeService.construirDiaConFichaje
     * L767-772). En tests sin fichajes ese stub no aparece para mantener
     * stubs estrictos (sin {@code lenient()}) coherentes con la suite
     * (FichajeServiceTest, AusenciaServiceTest).
     */
    private void mockHorasVacias() {
        when(empleadoRepository.findById(100L)).thenReturn(Optional.of(empleado));
        when(empresaService.obtenerEmpresa()).thenReturn(buildEmpresaResponse());
        when(fichajeRepository.findByEmpleadoIdAndFechaBetween(
                eq(100L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(pausaRepository.findByEmpleadoIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
                eq(100L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
    }

    private EmpresaResponse buildEmpresaResponse() {
        EmpresaResponse r = new EmpresaResponse();
        r.setId(1L);
        r.setNombreEmpresa("ACME S.L.");
        return r;
    }

    /**
     * Atajo para que las llamadas a {@code any(LocalDate.class)} convivan con
     * {@code eq(...)} en el mismo {@code when(...)} sin tener que importar
     * el {@code eq} de Mockito en cada test. Localmente delega al matcher
     * estatico estandar.
     */
    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
