package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.CategoriaEmpleado;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.EmpleadoRequest;
import com.staffflow.dto.response.EmpleadoResponse;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de EmpleadoService.crear() (E13 POST /api/v1/empleados).
 *
 * E13 es el único endpoint del grupo Empleados que consume el bean Clock
 * inyectado: lo usa para resolver "hoy" tanto al asignar fechaAlta por
 * defecto como al rechazar altas retroactivas (HTTP 400 vía
 * IllegalArgumentException, mapeado por GlobalExceptionHandler).
 *
 * Cobertura:
 * <ul>
 *   <li>Caso feliz: alta sin fechaAlta -> se asigna hoy (clock fijo).</li>
 *   <li>Caso feliz: alta con fechaAlta futura -> se respeta el valor.</li>
 *   <li>Caso feliz: alta con fechaAlta == hoy -> se respeta.</li>
 *   <li>Rechazo: fechaAlta anterior a hoy -> IllegalArgumentException.</li>
 *   <li>404: usuarioId inexistente -> NotFoundException.</li>
 *   <li>409: DNI ya registrado -> ConflictException.</li>
 *   <li>409: codigoNfc ya registrado -> ConflictException.</li>
 *   <li>Autogeneración numeroEmpleado: formato EMP-XXX a partir del conteo.</li>
 *   <li>Autogeneración numeroEmpleado con colisión: incrementa hasta libre.</li>
 *   <li>Cálculo jornadaDiariaMinutos: (horas/5)*60 redondeado.</li>
 *   <li>Response incluye pinTerminal en la respuesta de creación.</li>
 * </ul>
 *
 * Patrón de construcción del SUT: construcción manual con {@code new}
 * en {@code @BeforeEach} pasando los mocks y un {@code Clock.fixed(...)} real
 * al constructor. NO se usa {@code @InjectMocks} (ver Javadoc de
 * EmpleadoServiceTest para el razonamiento detallado).
 *
 * Patrón de stubs: estrictos (sin {@code lenient()}). Cada test stubea solo
 * las llamadas que realmente consume, coherente con el resto de la suite
 * (FichajeServiceTest, AusenciaServiceTest, InformeService*Test).
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmpleadoService — crear (E13) — alta de empleado con Clock")
class EmpleadoServiceCrearTest {

    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PresenciaService presenciaService;
    @Mock private PdfService pdfService;

    private EmpleadoService empleadoService;

    private static final long USUARIO_ID = 10L;
    private static final LocalDate HOY = LocalDate.of(2026, 1, 15);

    /** Reloj fijo Europe/Madrid 15/01/2026 — consumido funcionalmente por E13. */
    private static final Clock CLOCK_FIJO = Clock.fixed(
            HOY.atStartOfDay(ZoneId.of("Europe/Madrid")).toInstant(),
            ZoneId.of("Europe/Madrid"));

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        empleadoService = new EmpleadoService(
                empleadoRepository, usuarioRepository, presenciaService, pdfService, CLOCK_FIJO);

        usuario = new Usuario();
        usuario.setId(USUARIO_ID);
        usuario.setUsername("jperez");
    }

    /** Construye un request válido reutilizable; los tests sobreescriben campos puntuales. */
    private EmpleadoRequest requestValido() {
        EmpleadoRequest r = new EmpleadoRequest();
        r.setUsuarioId(USUARIO_ID);
        r.setNombre("Juan");
        r.setApellido1("Pérez");
        r.setApellido2("García");
        r.setDni("12345678A");
        r.setCategoria(CategoriaEmpleado.OPERARIO);
        r.setJornadaSemanalHoras(40.0);
        r.setDiasVacacionesAnuales(22);
        r.setDiasAsuntosPropiosAnuales(2);
        return r;
    }

    /**
     * Stubs MINIMOS para alcanzar el {@code save()} en el camino feliz.
     * No stubea {@code existsByCodigoNfc} (solo se invoca si el request trae
     * codigoNfc; los tests que lo necesiten lo stubean por su cuenta).
     */
    private void stubsCaminoFeliz() {
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(empleadoRepository.existsByDni(anyString())).thenReturn(false);
        when(empleadoRepository.count()).thenReturn(0L);
        when(empleadoRepository.existsByNumeroEmpleado(anyString())).thenReturn(false);
        when(empleadoRepository.existsByPinTerminal(anyString())).thenReturn(false);
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------
    // Resolución de fechaAlta vs Clock inyectado
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("fechaAlta — interacción con Clock inyectado")
    class FechaAlta {

        @Test
        @DisplayName("fechaAlta == null — asigna LocalDate.now(clock) = 15/01/2026")
        void crear_fechaAltaNull_asignaHoyDelClock() {
            stubsCaminoFeliz();
            EmpleadoRequest r = requestValido();
            r.setFechaAlta(null);

            empleadoService.crear(r);

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getFechaAlta()).isEqualTo(HOY);
        }

        @Test
        @DisplayName("fechaAlta futura — se respeta tal cual (alta diferida)")
        void crear_fechaAltaFutura_seRespeta() {
            stubsCaminoFeliz();
            LocalDate futura = HOY.plusDays(30);
            EmpleadoRequest r = requestValido();
            r.setFechaAlta(futura);

            empleadoService.crear(r);

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getFechaAlta()).isEqualTo(futura);
        }

        @Test
        @DisplayName("fechaAlta == hoy — se respeta (límite inclusivo, no es retroactiva)")
        void crear_fechaAltaIgualAHoy_seRespeta() {
            stubsCaminoFeliz();
            EmpleadoRequest r = requestValido();
            r.setFechaAlta(HOY);

            empleadoService.crear(r);

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getFechaAlta()).isEqualTo(HOY);
        }

        @Test
        @DisplayName("fechaAlta anterior a hoy — IllegalArgumentException 400 (no persiste)")
        void crear_fechaAltaRetroactiva_lanzaIllegalArgument() {
            // El SUT verifica usuarioId y existsByDni ANTES de validar la fecha;
            // solo esos dos stubs son necesarios para llegar al punto del error.
            when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
            when(empleadoRepository.existsByDni(anyString())).thenReturn(false);

            LocalDate ayer = HOY.minusDays(1);
            EmpleadoRequest r = requestValido();
            r.setFechaAlta(ayer);

            assertThatThrownBy(() -> empleadoService.crear(r))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("anterior a hoy");

            verify(empleadoRepository, never()).save(any(Empleado.class));
        }
    }

    // ---------------------------------------------------------------
    // Errores de precondición (404, 409)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Errores de precondición")
    class Errores {

        @Test
        @DisplayName("usuarioId inexistente — NotFoundException 404 (no persiste)")
        void crear_usuarioInexistente_lanzaNotFound() {
            when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.crear(requestValido()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(USUARIO_ID));

            verify(empleadoRepository, never()).save(any(Empleado.class));
        }

        @Test
        @DisplayName("DNI ya registrado — ConflictException 409 (no persiste)")
        void crear_dniDuplicado_lanzaConflict() {
            when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
            when(empleadoRepository.existsByDni("12345678A")).thenReturn(true);

            assertThatThrownBy(() -> empleadoService.crear(requestValido()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("12345678A");

            verify(empleadoRepository, never()).save(any(Empleado.class));
        }

        @Test
        @DisplayName("codigoNfc ya registrado — ConflictException 409 (no persiste)")
        void crear_codigoNfcDuplicado_lanzaConflict() {
            when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
            when(empleadoRepository.existsByDni(anyString())).thenReturn(false);
            when(empleadoRepository.existsByCodigoNfc("NFC-123")).thenReturn(true);

            EmpleadoRequest r = requestValido();
            r.setCodigoNfc("NFC-123");

            assertThatThrownBy(() -> empleadoService.crear(r))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("NFC-123");

            verify(empleadoRepository, never()).save(any(Empleado.class));
        }
    }

    // ---------------------------------------------------------------
    // Auto-generación de numeroEmpleado y cálculos derivados
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Auto-generación de campos derivados")
    class Autogeneracion {

        @Test
        @DisplayName("numeroEmpleado — count=0 -> EMP-001")
        void crear_countCero_generaEmp001() {
            stubsCaminoFeliz();

            empleadoService.crear(requestValido());

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getNumeroEmpleado()).isEqualTo("EMP-001");
        }

        @Test
        @DisplayName("numeroEmpleado — count=42 -> EMP-043")
        void crear_countCuarentaYDos_generaEmp043() {
            // Stub explicito de count y existsByNumeroEmpleado para verificar el
            // formato sobre un valor distinto del camino feliz por defecto (0).
            when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
            when(empleadoRepository.existsByDni(anyString())).thenReturn(false);
            when(empleadoRepository.count()).thenReturn(42L);
            when(empleadoRepository.existsByNumeroEmpleado("EMP-043")).thenReturn(false);
            when(empleadoRepository.existsByPinTerminal(anyString())).thenReturn(false);
            when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));

            empleadoService.crear(requestValido());

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getNumeroEmpleado()).isEqualTo("EMP-043");
        }

        @Test
        @DisplayName("numeroEmpleado con colisión — incrementa hasta libre (EMP-005 ocupado -> EMP-006)")
        void crear_numeroOcupado_incrementaHastaLibre() {
            // El bucle while() prueba EMP-005 (ocupado) y luego EMP-006 (libre).
            when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
            when(empleadoRepository.existsByDni(anyString())).thenReturn(false);
            when(empleadoRepository.count()).thenReturn(4L);
            when(empleadoRepository.existsByNumeroEmpleado("EMP-005")).thenReturn(true);
            when(empleadoRepository.existsByNumeroEmpleado("EMP-006")).thenReturn(false);
            when(empleadoRepository.existsByPinTerminal(anyString())).thenReturn(false);
            when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));

            empleadoService.crear(requestValido());

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getNumeroEmpleado()).isEqualTo("EMP-006");
        }

        @Test
        @DisplayName("jornadaDiariaMinutos — 40h/sem -> 480 min/día (40/5*60)")
        void crear_jornada40h_calcula480MinDiarios() {
            stubsCaminoFeliz();

            empleadoService.crear(requestValido()); // jornadaSemanalHoras = 40.0

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getJornadaDiariaMinutos()).isEqualTo(480);
        }

        @Test
        @DisplayName("jornadaDiariaMinutos — 20h/sem -> 240 min/día (media jornada)")
        void crear_jornada20h_calcula240MinDiarios() {
            stubsCaminoFeliz();
            EmpleadoRequest r = requestValido();
            r.setJornadaSemanalHoras(20.0);

            empleadoService.crear(r);

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getJornadaDiariaMinutos()).isEqualTo(240);
        }

        @Test
        @DisplayName("pinTerminal — formato 4 dígitos y se devuelve en EmpleadoResponse")
        void crear_responseIncluyePinTerminal() {
            stubsCaminoFeliz();

            EmpleadoResponse response = empleadoService.crear(requestValido());

            // El PIN debe venir en la respuesta del alta para que el ADMIN/ENCARGADO
            // lo entregue al empleado en persona (no aparece en el resto de endpoints).
            assertThat(response.getPinTerminal())
                    .isNotNull()
                    .matches("\\d{4}");
        }

        @Test
        @DisplayName("empleado nuevo se persiste con activo=true por defecto")
        void crear_persisteEmpleadoActivo() {
            stubsCaminoFeliz();

            empleadoService.crear(requestValido());

            ArgumentCaptor<Empleado> captor = ArgumentCaptor.forClass(Empleado.class);
            verify(empleadoRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isTrue();
        }
    }
}
