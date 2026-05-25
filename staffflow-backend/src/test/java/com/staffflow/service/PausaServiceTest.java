package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.Pausa;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.enums.TipoPausa;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.PausaPatchRequest;
import com.staffflow.dto.request.PausaRequest;
import com.staffflow.dto.response.PausaResponse;
import com.staffflow.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de PausaService.
 *
 * <p>Cubre los cuatro métodos públicos del servicio (E27 crear, E28 cerrar,
 * E29 listar, E55 listarPropios) con Mockito puro, sin contexto Spring.</p>
 *
 * <p>Las ramas que dependen de la fecha actual (restricción de ENCARGADO,
 * rechazo de fechas futuras) usan un {@code Clock.fixed(...)} inyectado
 * manualmente para ser deterministas. Mismo patrón que
 * {@link com.staffflow.service.scheduled.ProcesoCierreDiarioTest}.</p>
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PausaService — gestión de pausas E27/E28/E29/E55")
class PausaServiceTest {

    @Mock private PausaRepository pausaRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private FichajeRepository fichajeRepository;
    @Mock private UsuarioRepository usuarioRepository;

    // Fecha fija para tests deterministas: jueves 15 de enero de 2026.
    // "ayer" = 14/01, "hoy" = 15/01, "mañana" = 16/01.
    private static final LocalDate HOY = LocalDate.of(2026, 1, 15);
    private static final LocalDate AYER = LocalDate.of(2026, 1, 14);
    private static final LocalDate MANANA = LocalDate.of(2026, 1, 16);
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private Empleado empleado;
    private Usuario usuarioAdmin;
    private Usuario usuarioEncargado;

    /**
     * Construye un {@link PausaService} con el Clock fijado al día indicado.
     *
     * Cada test escoge la fecha de "hoy" que necesita: usar HOY para
     * pruebas normales, o ajustarla si necesita una rama distinta.
     */
    private PausaService nuevoServiceConFecha(LocalDate fechaHoy) {
        Clock clock = Clock.fixed(
                fechaHoy.atStartOfDay(ZONA).toInstant(), ZONA);
        return new PausaService(
                pausaRepository,
                empleadoRepository,
                fichajeRepository,
                usuarioRepository,
                clock);
    }

    @BeforeEach
    void setUp() {
        empleado = new Empleado();
        empleado.setId(10L);
        empleado.setNombre("Carlos");
        empleado.setApellido1("Lopez");

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
    // E27 — crear()
    // =================================================================

    @Nested
    @DisplayName("crear (E27) — registro manual de pausa")
    class CrearTests {

        @Test
        @DisplayName("empleado no existe → EntityNotFoundException (404)")
        void empleadoInexistenteLanzaNotFound() {
            PausaRequest request = nuevoPausaRequest(HOY, LocalTime.of(13, 0), null);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.empty());

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Empleado no encontrado");

            verify(pausaRepository, never()).save(any());
        }

        @Test
        @DisplayName("usuario autenticado no existe → EntityNotFoundException (404)")
        void usuarioInexistenteLanzaNotFound() {
            PausaRequest request = nuevoPausaRequest(HOY, LocalTime.of(13, 0), null);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.empty());

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Usuario autenticado no encontrado");

            verify(pausaRepository, never()).save(any());
        }

        @Test
        @DisplayName("fecha futura → IllegalArgumentException (400)")
        void fechaFuturaRechazada() {
            PausaRequest request = nuevoPausaRequest(MANANA, LocalTime.of(13, 0), null);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fechas futuras");

            verify(pausaRepository, never()).save(any());
        }

        @Test
        @DisplayName("ENCARGADO + fecha pasada → IllegalArgumentException (400)")
        void encargadoConFechaPasadaRechazado() {
            PausaRequest request = nuevoPausaRequest(AYER, LocalTime.of(13, 0), null);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("encargado"))
                    .thenReturn(Optional.of(usuarioEncargado));

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "encargado"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ENCARGADO solo puede gestionar");

            verify(pausaRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN + fecha pasada → permitido (sin restricción)")
        void adminConFechaPasadaPermitido() {
            PausaRequest request = nuevoPausaRequest(AYER, LocalTime.of(13, 0), null);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(10L, AYER))
                    .thenReturn(Optional.empty());
            when(pausaRepository.save(any(Pausa.class))).thenAnswer(inv -> inv.getArgument(0));

            PausaService service = nuevoServiceConFecha(HOY);
            PausaResponse response = service.crear(request, "admin");

            assertThat(response).isNotNull();
            assertThat(response.getFecha()).isEqualTo(AYER);
            verify(pausaRepository).save(any(Pausa.class));
        }

        @Test
        @DisplayName("pausa activa ya existe → ConflictException (409)")
        void pausaActivaExistenteLanzaConflict() {
            PausaRequest request = nuevoPausaRequest(HOY, LocalTime.of(13, 0), null);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(10L, HOY))
                    .thenReturn(Optional.of(new Pausa()));

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("ya tiene una pausa activa");

            verify(pausaRepository, never()).save(any());
        }

        @Test
        @DisplayName("pausa con horaFin → calcula duracionMinutos con floor")
        void pausaCerradaCalculaDuracionConFloor() {
            // 13:00 a 13:30 + 45 segundos = 30.75 min → floor = 30
            PausaRequest request = nuevoPausaRequest(
                    HOY,
                    LocalTime.of(13, 0),
                    LocalTime.of(13, 30, 45));
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(10L, HOY))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<Pausa> captor = ArgumentCaptor.forClass(Pausa.class);
            when(pausaRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            PausaService service = nuevoServiceConFecha(HOY);
            service.crear(request, "admin");

            Pausa guardada = captor.getValue();
            assertThat(guardada.getDuracionMinutos()).isEqualTo(30);
        }

        @Test
        @DisplayName("pausa sin horaFin → queda activa (duracionMinutos = null)")
        void pausaAbiertaSinDuracion() {
            PausaRequest request = nuevoPausaRequest(HOY, LocalTime.of(13, 0), null);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(10L, HOY))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<Pausa> captor = ArgumentCaptor.forClass(Pausa.class);
            when(pausaRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            PausaService service = nuevoServiceConFecha(HOY);
            service.crear(request, "admin");

            Pausa guardada = captor.getValue();
            assertThat(guardada.getDuracionMinutos()).isNull();
            assertThat(guardada.getHoraFin()).isNull();
        }
    }

    // =================================================================
    // E28 — cerrar()
    // =================================================================

    @Nested
    @DisplayName("cerrar (E28) — cierre o modificación de pausa")
    class CerrarTests {

        @Test
        @DisplayName("observaciones null → IllegalArgumentException (RNF-L02)")
        void observacionesNullRechazadas() {
            PausaPatchRequest request = new PausaPatchRequest();
            request.setObservaciones(null);

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.cerrar(1L, request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("observaciones son obligatorias");

            verify(pausaRepository, never()).save(any());
        }

        @Test
        @DisplayName("observaciones vacías → IllegalArgumentException (RNF-L02)")
        void observacionesVaciasRechazadas() {
            PausaPatchRequest request = new PausaPatchRequest();
            request.setObservaciones("   ");

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.cerrar(1L, request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("observaciones son obligatorias");
        }

        @Test
        @DisplayName("pausa no existe → EntityNotFoundException (404)")
        void pausaInexistenteLanzaNotFound() {
            PausaPatchRequest request = nuevoPatchRequest("test", null);
            when(pausaRepository.findById(99L)).thenReturn(Optional.empty());

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.cerrar(99L, request, "admin"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Pausa no encontrada");
        }

        @Test
        @DisplayName("ENCARGADO + pausa con fecha pasada → IllegalArgumentException")
        void encargadoConPausaPasadaRechazado() {
            Pausa pausa = nuevaPausaPersistida(AYER, LocalTime.of(13, 0), null, TipoPausa.COMIDA);
            PausaPatchRequest request = nuevoPatchRequest("cierre tardio", LocalTime.of(13, 30));

            when(pausaRepository.findById(1L)).thenReturn(Optional.of(pausa));
            when(usuarioRepository.findByUsername("encargado"))
                    .thenReturn(Optional.of(usuarioEncargado));

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.cerrar(1L, request, "encargado"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ENCARGADO solo puede gestionar");

            verify(pausaRepository, never()).save(any());
        }

        @Test
        @DisplayName("cerrar pausa NO retribuida → actualiza fichaje (totalPausas + jornadaEfectiva)")
        void cerrarPausaNoRetribuidaActualizaFichaje() {
            // Pausa de 13:00 a 13:30 (30 min). Fichaje 09:00-17:00 (480 min brutos).
            Pausa pausa = nuevaPausaPersistida(HOY, LocalTime.of(13, 0), null, TipoPausa.COMIDA);
            PausaPatchRequest request = nuevoPatchRequest("cierre normal", LocalTime.of(13, 30));

            Fichaje fichaje = new Fichaje();
            fichaje.setHoraEntrada(HOY.atTime(9, 0));
            fichaje.setHoraSalida(HOY.atTime(17, 0));
            fichaje.setTotalPausasMinutos(0);
            fichaje.setJornadaEfectivaMinutos(0);

            when(pausaRepository.findById(1L)).thenReturn(Optional.of(pausa));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(pausaRepository.save(any(Pausa.class))).thenAnswer(inv -> inv.getArgument(0));
            when(fichajeRepository.findByEmpleadoIdAndFecha(10L, HOY))
                    .thenReturn(Optional.of(fichaje));

            PausaService service = nuevoServiceConFecha(HOY);
            service.cerrar(1L, request, "admin");

            // totalPausasMinutos: 0 + 30 = 30
            assertThat(fichaje.getTotalPausasMinutos()).isEqualTo(30);
            // jornadaEfectivaMinutos: ceil(480 - 30) = 450
            assertThat(fichaje.getJornadaEfectivaMinutos()).isEqualTo(450);
            verify(fichajeRepository).save(fichaje);
        }

        @Test
        @DisplayName("cerrar pausa AUSENCIA_RETRIBUIDA → NO toca fichaje")
        void cerrarAusenciaRetribuidaNoTocaFichaje() {
            Pausa pausa = nuevaPausaPersistida(
                    HOY, LocalTime.of(10, 0), null, TipoPausa.AUSENCIA_RETRIBUIDA);
            PausaPatchRequest request = nuevoPatchRequest("gestion medica", LocalTime.of(11, 0));

            when(pausaRepository.findById(1L)).thenReturn(Optional.of(pausa));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(pausaRepository.save(any(Pausa.class))).thenAnswer(inv -> inv.getArgument(0));

            PausaService service = nuevoServiceConFecha(HOY);
            service.cerrar(1L, request, "admin");

            // No se debe consultar ni guardar el fichaje
            verify(fichajeRepository, never()).findByEmpleadoIdAndFecha(any(), any());
            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("cerrar pausa sin fichaje del día → no rompe, solo guarda la pausa")
        void cerrarPausaSinFichajeAsociado() {
            Pausa pausa = nuevaPausaPersistida(HOY, LocalTime.of(13, 0), null, TipoPausa.COMIDA);
            PausaPatchRequest request = nuevoPatchRequest("cierre", LocalTime.of(13, 15));

            when(pausaRepository.findById(1L)).thenReturn(Optional.of(pausa));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(pausaRepository.save(any(Pausa.class))).thenAnswer(inv -> inv.getArgument(0));
            when(fichajeRepository.findByEmpleadoIdAndFecha(10L, HOY))
                    .thenReturn(Optional.empty());

            PausaService service = nuevoServiceConFecha(HOY);
            PausaResponse response = service.cerrar(1L, request, "admin");

            assertThat(response).isNotNull();
            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("solo modifica observaciones (sin horaFin) → no recalcula nada")
        void soloModificaObservacionesNoRecalcula() {
            Pausa pausa = nuevaPausaPersistida(
                    HOY, LocalTime.of(13, 0), LocalTime.of(13, 30), TipoPausa.COMIDA);
            pausa.setDuracionMinutos(30);
            PausaPatchRequest request = nuevoPatchRequest("correcion de texto", null);

            when(pausaRepository.findById(1L)).thenReturn(Optional.of(pausa));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(pausaRepository.save(any(Pausa.class))).thenAnswer(inv -> inv.getArgument(0));

            PausaService service = nuevoServiceConFecha(HOY);
            service.cerrar(1L, request, "admin");

            // Duracion intacta, fichaje no consultado
            assertThat(pausa.getDuracionMinutos()).isEqualTo(30);
            assertThat(pausa.getObservaciones()).isEqualTo("correcion de texto");
            verify(fichajeRepository, never()).findByEmpleadoIdAndFecha(any(), any());
        }
    }

    // =================================================================
    // E29 — listar()
    // =================================================================

    @Nested
    @DisplayName("listar (E29) — filtros opcionales y combinables")
    class ListarTests {

        @Test
        @DisplayName("sin filtros → devuelve todas las pausas")
        void sinFiltrosDevuelveTodas() {
            Pausa p1 = nuevaPausaPersistida(HOY, LocalTime.of(10, 0), null, TipoPausa.DESCANSO);
            Pausa p2 = nuevaPausaPersistida(HOY, LocalTime.of(13, 0), null, TipoPausa.COMIDA);
            when(pausaRepository.findByFiltros(null, null, null, null))
                    .thenReturn(List.of(p1, p2));

            PausaService service = nuevoServiceConFecha(HOY);
            List<PausaResponse> resultado = service.listar(null, null, null, null);

            assertThat(resultado).hasSize(2);
        }

        @Test
        @DisplayName("con filtros combinados → delega al repository sin alterar")
        void filtrosCombinadosDelegadosAlRepository() {
            when(pausaRepository.findByFiltros(10L, AYER, HOY, TipoPausa.COMIDA))
                    .thenReturn(Collections.emptyList());

            PausaService service = nuevoServiceConFecha(HOY);
            List<PausaResponse> resultado = service.listar(10L, AYER, HOY, TipoPausa.COMIDA);

            assertThat(resultado).isEmpty();
            verify(pausaRepository).findByFiltros(10L, AYER, HOY, TipoPausa.COMIDA);
        }
    }

    // =================================================================
    // E55 — listarPropios()
    // =================================================================

    @Nested
    @DisplayName("listarPropios (E55) — pausas del empleado autenticado")
    class ListarPropiosTests {

        @Test
        @DisplayName("usuario no existe → EntityNotFoundException")
        void usuarioInexistenteLanzaNotFound() {
            when(usuarioRepository.findByUsername("desconocido")).thenReturn(Optional.empty());

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.listarPropios("desconocido", null, null))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Usuario autenticado no encontrado");
        }

        @Test
        @DisplayName("usuario sin perfil de empleado → EntityNotFoundException")
        void usuarioSinEmpleadoLanzaNotFound() {
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(empleadoRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());

            PausaService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.listarPropios("admin", null, null))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("no tiene perfil de empleado");
        }

        @Test
        @DisplayName("usuario con empleado → filtra por su empleadoId y delega al repository")
        void filtraPorEmpleadoIdDelUsuarioAutenticado() {
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(empleadoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(empleado));
            when(pausaRepository.findByFiltros(10L, AYER, HOY, null))
                    .thenReturn(Collections.emptyList());

            PausaService service = nuevoServiceConFecha(HOY);
            List<PausaResponse> resultado = service.listarPropios("admin", AYER, HOY);

            assertThat(resultado).isEmpty();
            // tipoPausa siempre null en E55 (no filtra por tipo)
            verify(pausaRepository).findByFiltros(10L, AYER, HOY, null);
        }
    }

    // =================================================================
    // Helpers
    // =================================================================

    private PausaRequest nuevoPausaRequest(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        PausaRequest request = new PausaRequest();
        request.setEmpleadoId(10L);
        request.setFecha(fecha);
        request.setHoraInicio(fecha.atTime(horaInicio));
        if (horaFin != null) {
            request.setHoraFin(fecha.atTime(horaFin));
        }
        request.setTipoPausa(TipoPausa.COMIDA);
        request.setObservaciones("test");
        return request;
    }

    private PausaPatchRequest nuevoPatchRequest(String observaciones, LocalTime horaFin) {
        PausaPatchRequest request = new PausaPatchRequest();
        request.setObservaciones(observaciones);
        if (horaFin != null) {
            request.setHoraFin(HOY.atTime(horaFin));
        }
        return request;
    }

    private Pausa nuevaPausaPersistida(LocalDate fecha, LocalTime horaInicio,
                                       LocalTime horaFin, TipoPausa tipo) {
        Pausa pausa = new Pausa();
        pausa.setId(1L);
        pausa.setEmpleado(empleado);
        pausa.setFecha(fecha);
        pausa.setHoraInicio(fecha.atTime(horaInicio));
        if (horaFin != null) {
            pausa.setHoraFin(fecha.atTime(horaFin));
        }
        pausa.setTipoPausa(tipo);
        pausa.setUsuario(usuarioAdmin);
        pausa.setObservaciones("inicial");
        pausa.setFechaCreacion(LocalDateTime.now());
        return pausa;
    }
}
