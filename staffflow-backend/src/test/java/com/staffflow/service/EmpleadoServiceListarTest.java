package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.CategoriaEmpleado;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.response.EmpleadoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de EmpleadoService.listar() (E14 GET /api/v1/empleados).
 *
 * E14 expone cuatro ramas dependiendo de qué filtros llegan:
 * <ol>
 *   <li>{@code q} no nulo y no en blanco → {@code buscarPorTexto(termino)}
 *       y aplica los filtros {@code activo} y {@code categoria} en memoria.</li>
 *   <li>{@code q} nulo + {@code categoria} no nula → repositorio JPA
 *       {@code findByCategoria} o {@code findByCategoriaAndActivo} según
 *       {@code activo}.</li>
 *   <li>{@code q} nulo + {@code categoria} nula + {@code activo} no nulo →
 *       {@code findByActivo(activo)}.</li>
 *   <li>Sin filtros → {@code findAll()} (incluye inactivos por defecto, para
 *       la pantalla P13 del cliente Android).</li>
 * </ol>
 *
 * Adicional: el listado NUNCA expone {@code pinTerminal} (queda null en
 * todos los elementos, independientemente del rol). Solo E15 lo expone a ADMIN.
 *
 * Patrón de stubs: estrictos (sin {@code lenient()}). Cada test stubea solo
 * el método del repositorio que su rama consume.
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmpleadoService — listar (E14) — ramas de filtros")
class EmpleadoServiceListarTest {

    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PresenciaService presenciaService;
    @Mock private PdfService pdfService;

    private EmpleadoService empleadoService;

    /** Reloj fijo (no consumido por E14; exigido por el constructor del SUT). */
    private static final Clock CLOCK_FIJO = Clock.fixed(
            LocalDate.of(2026, 1, 15).atStartOfDay(ZoneId.of("Europe/Madrid")).toInstant(),
            ZoneId.of("Europe/Madrid"));

    @BeforeEach
    void setUp() {
        empleadoService = new EmpleadoService(
                empleadoRepository, usuarioRepository, presenciaService, pdfService, CLOCK_FIJO);
    }

    /**
     * Construye un empleado mínimo con usuario asociado (toEmpleadoResponse
     * accede a usuario.id, así que no puede ser null).
     */
    private Empleado empleado(long id, String nombre, boolean activo, CategoriaEmpleado cat,
                              String pin) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername("user-" + id);

        Empleado e = new Empleado();
        e.setId(id);
        e.setUsuario(u);
        e.setNombre(nombre);
        e.setApellido1("Apellido");
        e.setActivo(activo);
        e.setCategoria(cat);
        e.setPinTerminal(pin);
        return e;
    }

    // ---------------------------------------------------------------
    // Rama 4 — sin filtros (findAll)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Sin filtros — devuelve TODOS los empleados (activos e inactivos)")
    class SinFiltros {

        @Test
        @DisplayName("listar(null, null, null) — invoca findAll y devuelve la lista mapeada")
        void listar_sinFiltros_invocaFindAll() {
            when(empleadoRepository.findAll()).thenReturn(List.of(
                    empleado(1L, "Activo", true, CategoriaEmpleado.OPERARIO, "1111"),
                    empleado(2L, "Inactivo", false, CategoriaEmpleado.TECNICO, "2222")));

            List<EmpleadoResponse> resultado = empleadoService.listar(null, null, null);

            assertThat(resultado).hasSize(2);
            verify(empleadoRepository).findAll();
            verify(empleadoRepository, never()).findByActivo(anyBoolean());
        }

        @Test
        @DisplayName("listar(null, null, null) — empty state cuando no hay empleados")
        void listar_sinFiltrosSinDatos_devuelveListaVacia() {
            when(empleadoRepository.findAll()).thenReturn(Collections.emptyList());

            List<EmpleadoResponse> resultado = empleadoService.listar(null, null, null);

            assertThat(resultado).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // Rama 3 — solo activo (findByActivo)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Filtro activo aislado — invoca findByActivo")
    class SoloActivo {

        @Test
        @DisplayName("listar(true, null, null) — invoca findByActivo(true)")
        void listar_activoTrue_invocaFindByActivo() {
            when(empleadoRepository.findByActivo(true)).thenReturn(List.of(
                    empleado(1L, "Ana", true, CategoriaEmpleado.OPERARIO, "1111")));

            List<EmpleadoResponse> resultado = empleadoService.listar(true, null, null);

            assertThat(resultado).hasSize(1);
            verify(empleadoRepository).findByActivo(true);
            verify(empleadoRepository, never()).findAll();
        }

        @Test
        @DisplayName("listar(false, null, null) — invoca findByActivo(false) para mostrar inactivos")
        void listar_activoFalse_invocaFindByActivoFalse() {
            when(empleadoRepository.findByActivo(false)).thenReturn(List.of(
                    empleado(2L, "Inactivo", false, CategoriaEmpleado.TECNICO, "2222")));

            List<EmpleadoResponse> resultado = empleadoService.listar(false, null, null);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getActivo()).isFalse();
        }
    }

    // ---------------------------------------------------------------
    // Rama 2 — categoria (con o sin activo)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Filtro categoria — invoca findByCategoria o findByCategoriaAndActivo")
    class PorCategoria {

        @Test
        @DisplayName("listar(null, null, 'OPERARIO') — invoca findByCategoria(OPERARIO)")
        void listar_soloCategoria_invocaFindByCategoria() {
            when(empleadoRepository.findByCategoria(CategoriaEmpleado.OPERARIO))
                    .thenReturn(List.of(
                            empleado(1L, "Ana", true, CategoriaEmpleado.OPERARIO, "1111")));

            List<EmpleadoResponse> resultado = empleadoService.listar(null, null, "OPERARIO");

            assertThat(resultado).hasSize(1);
            verify(empleadoRepository).findByCategoria(CategoriaEmpleado.OPERARIO);
            verify(empleadoRepository, never()).findByCategoriaAndActivo(any(), anyBoolean());
        }

        @Test
        @DisplayName("listar(true, null, 'TECNICO') — combina categoria + activo")
        void listar_categoriaYActivo_invocaFindByCategoriaAndActivo() {
            when(empleadoRepository.findByCategoriaAndActivo(CategoriaEmpleado.TECNICO, true))
                    .thenReturn(List.of(
                            empleado(3L, "Carlos", true, CategoriaEmpleado.TECNICO, "3333")));

            List<EmpleadoResponse> resultado = empleadoService.listar(true, null, "TECNICO");

            assertThat(resultado).hasSize(1);
            verify(empleadoRepository).findByCategoriaAndActivo(CategoriaEmpleado.TECNICO, true);
            verify(empleadoRepository, never()).findByCategoria(any());
        }
    }

    // ---------------------------------------------------------------
    // Rama 1 — busqueda por texto (q)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Búsqueda por texto (q) — invoca buscarPorTexto y filtra en memoria")
    class BusquedaPorTexto {

        @Test
        @DisplayName("listar(null, 'ana', null) — invoca buscarPorTexto con termino normalizado a minusculas y trim")
        void listar_qConTexto_invocaBuscarPorTexto() {
            // El service hace trim() + toLowerCase() antes de llamar al repo.
            when(empleadoRepository.buscarPorTexto("ana")).thenReturn(List.of(
                    empleado(1L, "Ana", true, CategoriaEmpleado.OPERARIO, "1111")));

            List<EmpleadoResponse> resultado = empleadoService.listar(null, "  ANA  ", null);

            assertThat(resultado).hasSize(1);
            verify(empleadoRepository).buscarPorTexto("ana");
            verify(empleadoRepository, never()).findAll();
        }

        @Test
        @DisplayName("listar con q + activo=true — filtra en memoria los activos del resultado")
        void listar_qMasActivo_filtraEnMemoria() {
            when(empleadoRepository.buscarPorTexto("perez")).thenReturn(List.of(
                    empleado(1L, "Pérez", true, CategoriaEmpleado.OPERARIO, "1111"),
                    empleado(2L, "Pérez", false, CategoriaEmpleado.OPERARIO, "2222")));

            List<EmpleadoResponse> resultado = empleadoService.listar(true, "perez", null);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getActivo()).isTrue();
        }

        @Test
        @DisplayName("listar con q + categoria — filtra en memoria por categoria del resultado")
        void listar_qMasCategoria_filtraEnMemoria() {
            when(empleadoRepository.buscarPorTexto("apellido")).thenReturn(List.of(
                    empleado(1L, "Ana", true, CategoriaEmpleado.OPERARIO, "1111"),
                    empleado(2L, "Bea", true, CategoriaEmpleado.TECNICO, "2222")));

            List<EmpleadoResponse> resultado = empleadoService.listar(null, "apellido", "TECNICO");

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getCategoria()).isEqualTo(CategoriaEmpleado.TECNICO);
        }

        @Test
        @DisplayName("listar con q en blanco — se trata como sin q (rama findAll/findByActivo según resto)")
        void listar_qEnBlanco_seTrataComoSinQ() {
            // q = "   " entra en la rama "sin q": como no hay categoria ni activo,
            // termina en findAll().
            when(empleadoRepository.findAll()).thenReturn(Collections.emptyList());

            empleadoService.listar(null, "   ", null);

            verify(empleadoRepository).findAll();
            verify(empleadoRepository, never()).buscarPorTexto(anyString());
        }
    }

    // ---------------------------------------------------------------
    // Seguridad — pinTerminal nunca aparece en listados
    // ---------------------------------------------------------------

    @Test
    @DisplayName("listar — pinTerminal siempre null en el response (nunca se expone en listados)")
    void listar_pinTerminalNuncaEnResponse() {
        when(empleadoRepository.findAll()).thenReturn(List.of(
                empleado(1L, "Ana", true, CategoriaEmpleado.OPERARIO, "1111"),
                empleado(2L, "Bea", true, CategoriaEmpleado.TECNICO, "2222")));

        List<EmpleadoResponse> resultado = empleadoService.listar(null, null, null);

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allSatisfy(r -> assertThat(r.getPinTerminal()).isNull());
    }
}
