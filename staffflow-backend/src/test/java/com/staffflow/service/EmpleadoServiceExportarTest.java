package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.CategoriaEmpleado;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de EmpleadoService.exportar() (E20 GET /empleados/export).
 *
 * E20 ramifica por formato:
 * <ul>
 *   <li>{@code "csv"} (case-insensitive) → genera bytes con cabecera y una
 *       fila por empleado, codificación UTF-8.</li>
 *   <li>{@code "pdf"} (case-insensitive) → delega en
 *       {@link PdfService#exportarEmpleados(List)}.</li>
 *   <li>cualquier otro valor → {@link IllegalArgumentException} (HTTP 400).</li>
 * </ul>
 *
 * Filtro de empleados:
 * <ul>
 *   <li>{@code activo == null} → solo activos (defecto explícito en el service).</li>
 *   <li>{@code activo == true} → solo activos (mismo resultado).</li>
 *   <li>{@code activo == false} → TODOS los empleados (incluye inactivos).</li>
 * </ul>
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmpleadoService — exportar (E20) — CSV, PDF y filtro activo")
class EmpleadoServiceExportarTest {

    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PresenciaService presenciaService;
    @Mock private PdfService pdfService;

    private EmpleadoService empleadoService;

    /** Reloj fijo (no consumido por E20; exigido por el constructor del SUT). */
    private static final Clock CLOCK_FIJO = Clock.fixed(
            LocalDate.of(2026, 1, 15).atStartOfDay(ZoneId.of("Europe/Madrid")).toInstant(),
            ZoneId.of("Europe/Madrid"));

    @BeforeEach
    void setUp() {
        empleadoService = new EmpleadoService(
                empleadoRepository, usuarioRepository, presenciaService, pdfService, CLOCK_FIJO);
    }

    private Empleado empleado(long id, String nombre, String apellido1, String dni,
                              CategoriaEmpleado cat, double horas, LocalDate fechaAlta) {
        Usuario u = new Usuario();
        u.setId(id);

        Empleado e = new Empleado();
        e.setId(id);
        e.setUsuario(u);
        e.setNumeroEmpleado(String.format("EMP-%03d", id));
        e.setNombre(nombre);
        e.setApellido1(apellido1);
        e.setDni(dni);
        e.setCategoria(cat);
        e.setJornadaSemanalHoras(horas);
        e.setFechaAlta(fechaAlta);
        e.setActivo(true);
        return e;
    }

    // ---------------------------------------------------------------
    // Rama CSV
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Formato CSV")
    class FormatoCsv {

        @Test
        @DisplayName("CSV — cabecera + una fila por empleado, codificación UTF-8")
        void exportar_csv_generaCabeceraYFilas() {
            when(empleadoRepository.findByActivo(true)).thenReturn(List.of(
                    empleado(1L, "Ana", "Pérez", "11111111A",
                            CategoriaEmpleado.OPERARIO, 40.0, LocalDate.of(2025, 1, 1)),
                    empleado(2L, "Bea", "García", "22222222B",
                            CategoriaEmpleado.TECNICO, 20.0, LocalDate.of(2025, 6, 15))));

            byte[] resultado = empleadoService.exportar("csv", null);

            String csv = new String(resultado, StandardCharsets.UTF_8);
            // Cabecera presente y una linea por empleado.
            assertThat(csv.lines().count()).isEqualTo(3L); // 1 cabecera + 2 filas
            assertThat(csv).contains("EMP-001", "Ana", "11111111A");
            assertThat(csv).contains("EMP-002", "Bea", "22222222B");
            // PIN NUNCA en el CSV: la cabecera no contiene la columna PIN
            // (el generador NO la incluye, asi que ni siquiera puede filtrarse).
            assertThat(csv.lines().findFirst().orElseThrow().toUpperCase())
                    .doesNotContain("PIN");
        }

        @Test
        @DisplayName("CSV — case-insensitive (\"CSV\" mayúsculas también se acepta)")
        void exportar_csvMayusculas_funciona() {
            when(empleadoRepository.findByActivo(true)).thenReturn(List.of());

            byte[] resultado = empleadoService.exportar("CSV", null);

            // Solo cabecera (sin filas), sigue siendo un CSV vacio valido.
            assertThat(new String(resultado, StandardCharsets.UTF_8))
                    .startsWith("N");
        }
    }

    // ---------------------------------------------------------------
    // Rama PDF
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Formato PDF — delega en PdfService.exportarEmpleados")
    class FormatoPdf {

        @Test
        @DisplayName("PDF — delega en PdfService y devuelve los bytes producidos por el colaborador")
        void exportar_pdf_delegaEnPdfService() {
            byte[] esperado = new byte[]{1, 2, 3, 4};
            when(empleadoRepository.findByActivo(true)).thenReturn(List.of(
                    empleado(1L, "Ana", "Pérez", "11111111A",
                            CategoriaEmpleado.OPERARIO, 40.0, LocalDate.of(2025, 1, 1))));
            when(pdfService.exportarEmpleados(anyList())).thenReturn(esperado);

            byte[] resultado = empleadoService.exportar("pdf", null);

            assertThat(resultado).isSameAs(esperado);
            verify(pdfService).exportarEmpleados(anyList());
        }
    }

    // ---------------------------------------------------------------
    // Formato inválido
    // ---------------------------------------------------------------

    @Test
    @DisplayName("formato no soportado — IllegalArgumentException con mensaje claro")
    void exportar_formatoInvalido_lanzaIllegalArgument() {
        when(empleadoRepository.findByActivo(true)).thenReturn(List.of());

        assertThatThrownBy(() -> empleadoService.exportar("xml", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xml");

        verifyNoInteractions(pdfService);
    }

    // ---------------------------------------------------------------
    // Filtro activo
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Filtro activo — defecto solo activos; false trae también inactivos")
    class FiltroActivo {

        @Test
        @DisplayName("activo=null — usa findByActivo(true) (defecto solo activos)")
        void exportar_activoNull_usaFindByActivoTrue() {
            when(empleadoRepository.findByActivo(true)).thenReturn(List.of());

            empleadoService.exportar("csv", null);

            verify(empleadoRepository).findByActivo(true);
            verify(empleadoRepository, never()).findAll();
        }

        @Test
        @DisplayName("activo=true — usa findByActivo(true)")
        void exportar_activoTrue_usaFindByActivoTrue() {
            when(empleadoRepository.findByActivo(true)).thenReturn(List.of());

            empleadoService.exportar("csv", true);

            verify(empleadoRepository).findByActivo(true);
        }

        @Test
        @DisplayName("activo=false — usa findAll (incluye inactivos)")
        void exportar_activoFalse_usaFindAll() {
            when(empleadoRepository.findAll()).thenReturn(List.of());

            empleadoService.exportar("csv", false);

            verify(empleadoRepository).findAll();
            verify(empleadoRepository, never()).findByActivo(anyBoolean());
        }
    }
}
