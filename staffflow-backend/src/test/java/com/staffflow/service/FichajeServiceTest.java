package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.FichajePatchRequest;
import com.staffflow.dto.request.FichajeRequest;
import com.staffflow.dto.response.FichajeResponse;
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
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de FichajeService — cubre las ramas dependientes de fecha
 * de los endpoints E22 (POST crear) y E23 (PATCH actualizar).
 *
 * <p>Estrategia: Mockito puro sin contexto Spring, inyectando un
 * {@code Clock.fixed(...)} para volver deterministas las comprobaciones de
 * fecha futura y restriccion del ENCARGADO. Mismo patron que
 * {@link PausaServiceTest} y {@link com.staffflow.service.scheduled.ProcesoCierreDiarioTest}.</p>
 *
 * <p>La cobertura de E24/E25/E26 se aborda en un commit posterior para
 * mantener cada PR acotado.</p>
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FichajeService — crear (E22) y actualizar (E23)")
class FichajeServiceTest {

    @Mock private FichajeRepository fichajeRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private UsuarioRepository usuarioRepository;

    // Fecha fija para tests deterministas: jueves 15 de enero de 2026.
    // "ayer" = 14/01, "hoy" = 15/01, "manana" = 16/01.
    private static final LocalDate HOY = LocalDate.of(2026, 1, 15);
    private static final LocalDate AYER = LocalDate.of(2026, 1, 14);
    private static final LocalDate MANANA = LocalDate.of(2026, 1, 16);
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private Empleado empleado;
    private Usuario usuarioAdmin;
    private Usuario usuarioEncargado;

    /**
     * Construye un {@link FichajeService} con el Clock fijado al dia indicado.
     * Cada test pasa la fecha que necesita como "hoy".
     */
    private FichajeService nuevoServiceConFecha(LocalDate fechaHoy) {
        Clock clock = Clock.fixed(
                fechaHoy.atStartOfDay(ZONA).toInstant(), ZONA);
        return new FichajeService(
                fichajeRepository,
                empleadoRepository,
                usuarioRepository,
                clock);
    }

    @BeforeEach
    void setUp() {
        empleado = new Empleado();
        empleado.setId(10L);
        empleado.setNombre("Carlos");
        empleado.setApellido1("Lopez");
        empleado.setApellido2("Garcia");
        empleado.setJornadaDiariaMinutos(480); // 8h

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
    // E22 — crear()
    // =================================================================

    @Nested
    @DisplayName("crear (E22) — registro manual de fichaje")
    class CrearTests {

        @Test
        @DisplayName("observaciones null → IllegalArgumentException (RNF-L02)")
        void observacionesNullRechazadas() {
            FichajeRequest request = nuevoFichajeRequest(HOY, TipoFichaje.NORMAL);
            request.setObservaciones(null);

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("observaciones son obligatorias");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("observaciones blank → IllegalArgumentException (RNF-L02)")
        void observacionesBlankRechazadas() {
            FichajeRequest request = nuevoFichajeRequest(HOY, TipoFichaje.NORMAL);
            request.setObservaciones("   ");

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("observaciones son obligatorias");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("empleado no existe → EntityNotFoundException (404)")
        void empleadoInexistenteLanzaNotFound() {
            FichajeRequest request = nuevoFichajeRequest(HOY, TipoFichaje.NORMAL);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.empty());

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Empleado no encontrado");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("usuario autenticado no existe → EntityNotFoundException (404)")
        void usuarioInexistenteLanzaNotFound() {
            FichajeRequest request = nuevoFichajeRequest(HOY, TipoFichaje.NORMAL);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.empty());

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Usuario autenticado no encontrado");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("fecha futura → IllegalArgumentException (400) para cualquier rol")
        void fechaFuturaRechazada() {
            FichajeRequest request = nuevoFichajeRequest(MANANA, TipoFichaje.NORMAL);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fechas futuras");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("ENCARGADO + fecha pasada → IllegalArgumentException (400)")
        void encargadoConFechaPasadaRechazado() {
            FichajeRequest request = nuevoFichajeRequest(AYER, TipoFichaje.NORMAL);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("encargado"))
                    .thenReturn(Optional.of(usuarioEncargado));

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "encargado"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ENCARGADO solo puede gestionar");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN + fecha pasada → permitido (sin restriccion)")
        void adminConFechaPasadaPermitido() {
            FichajeRequest request = nuevoFichajeRequest(AYER, TipoFichaje.NORMAL);
            request.setHoraEntrada(AYER.atTime(8, 0));
            request.setHoraSalida(AYER.atTime(16, 0));

            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(fichajeRepository.findByEmpleadoIdAndFecha(10L, AYER)).thenReturn(Optional.empty());
            when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(inv -> inv.getArgument(0));

            FichajeService service = nuevoServiceConFecha(HOY);
            FichajeResponse response = service.crear(request, "admin");

            assertThat(response).isNotNull();
            assertThat(response.getFecha()).isEqualTo(AYER);
            verify(fichajeRepository).save(any(Fichaje.class));
        }

        @Test
        @DisplayName("ENCARGADO + HOY → permitido")
        void encargadoConHoyPermitido() {
            FichajeRequest request = nuevoFichajeRequest(HOY, TipoFichaje.NORMAL);
            request.setHoraEntrada(HOY.atTime(9, 0));
            request.setHoraSalida(HOY.atTime(17, 0));

            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("encargado"))
                    .thenReturn(Optional.of(usuarioEncargado));
            when(fichajeRepository.findByEmpleadoIdAndFecha(10L, HOY)).thenReturn(Optional.empty());
            when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(inv -> inv.getArgument(0));

            FichajeService service = nuevoServiceConFecha(HOY);
            FichajeResponse response = service.crear(request, "encargado");

            assertThat(response).isNotNull();
            assertThat(response.getFecha()).isEqualTo(HOY);
            verify(fichajeRepository).save(any(Fichaje.class));
        }

        @Test
        @DisplayName("ya existe fichaje empleado+fecha → ConflictException (409)")
        void conflictoUnicidadEmpleadoFecha() {
            FichajeRequest request = nuevoFichajeRequest(HOY, TipoFichaje.NORMAL);
            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(fichajeRepository.findByEmpleadoIdAndFecha(10L, HOY))
                    .thenReturn(Optional.of(new Fichaje()));

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.crear(request, "admin"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Ya existe un fichaje");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("NORMAL con horas → jornadaEfectivaMinutos = (salida - entrada)")
        void normalConHorasCalculaJornada() {
            // 09:00 a 17:30 = 510 minutos
            FichajeRequest request = nuevoFichajeRequest(HOY, TipoFichaje.NORMAL);
            request.setHoraEntrada(HOY.atTime(9, 0));
            request.setHoraSalida(HOY.atTime(17, 30));

            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(fichajeRepository.findByEmpleadoIdAndFecha(10L, HOY)).thenReturn(Optional.empty());

            ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
            when(fichajeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            FichajeService service = nuevoServiceConFecha(HOY);
            service.crear(request, "admin");

            Fichaje guardado = captor.getValue();
            assertThat(guardado.getJornadaEfectivaMinutos()).isEqualTo(510);
            assertThat(guardado.getTotalPausasMinutos()).isZero();
        }

        @Test
        @DisplayName("BAJA_MEDICA sin horas → jornadaEfectivaMinutos = jornadaDiariaMinutos")
        void bajaMedicaSinHorasUsaJornadaDiaria() {
            FichajeRequest request = nuevoFichajeRequest(HOY, TipoFichaje.BAJA_MEDICA);
            // sin horaEntrada ni horaSalida

            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(fichajeRepository.findByEmpleadoIdAndFecha(10L, HOY)).thenReturn(Optional.empty());

            ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
            when(fichajeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            FichajeService service = nuevoServiceConFecha(HOY);
            service.crear(request, "admin");

            Fichaje guardado = captor.getValue();
            assertThat(guardado.getJornadaEfectivaMinutos()).isEqualTo(480);
        }

        @Test
        @DisplayName("PERMISO_RETRIBUIDO sin horas → jornadaEfectivaMinutos = jornadaDiariaMinutos")
        void permisoRetribuidoSinHorasUsaJornadaDiaria() {
            FichajeRequest request = nuevoFichajeRequest(HOY, TipoFichaje.PERMISO_RETRIBUIDO);

            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(fichajeRepository.findByEmpleadoIdAndFecha(10L, HOY)).thenReturn(Optional.empty());

            ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
            when(fichajeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            FichajeService service = nuevoServiceConFecha(HOY);
            service.crear(request, "admin");

            Fichaje guardado = captor.getValue();
            assertThat(guardado.getJornadaEfectivaMinutos()).isEqualTo(480);
        }

        @Test
        @DisplayName("VACACIONES sin horas → jornadaEfectivaMinutos = 0")
        void vacacionesSinHorasJornadaCero() {
            FichajeRequest request = nuevoFichajeRequest(HOY, TipoFichaje.VACACIONES);

            when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(fichajeRepository.findByEmpleadoIdAndFecha(10L, HOY)).thenReturn(Optional.empty());

            ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
            when(fichajeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            FichajeService service = nuevoServiceConFecha(HOY);
            service.crear(request, "admin");

            Fichaje guardado = captor.getValue();
            assertThat(guardado.getJornadaEfectivaMinutos()).isZero();
        }
    }

    // =================================================================
    // E23 — actualizar()
    // =================================================================

    @Nested
    @DisplayName("actualizar (E23) — modificacion manual de fichaje")
    class ActualizarTests {

        @Test
        @DisplayName("observaciones null → IllegalArgumentException (RNF-L02)")
        void observacionesNullRechazadas() {
            FichajePatchRequest request = new FichajePatchRequest();
            request.setObservaciones(null);

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.actualizar(1L, request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("observaciones son obligatorias");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("observaciones blank → IllegalArgumentException (RNF-L02)")
        void observacionesBlankRechazadas() {
            FichajePatchRequest request = new FichajePatchRequest();
            request.setObservaciones("   ");

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.actualizar(1L, request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("observaciones son obligatorias");
        }

        @Test
        @DisplayName("fichaje no existe → EntityNotFoundException (404)")
        void fichajeInexistenteLanzaNotFound() {
            FichajePatchRequest request = nuevoPatchRequest("Correccion");
            when(fichajeRepository.findById(99L)).thenReturn(Optional.empty());

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.actualizar(99L, request, "admin"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Fichaje no encontrado");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("usuario autenticado no existe → EntityNotFoundException (404)")
        void usuarioInexistenteLanzaNotFound() {
            FichajePatchRequest request = nuevoPatchRequest("Correccion");
            Fichaje fichaje = nuevoFichajePersistido(1L, HOY);
            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichaje));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.empty());

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.actualizar(1L, request, "admin"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Usuario autenticado no encontrado");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("fichaje en fecha futura → IllegalArgumentException (400)")
        void fichajeFechaFuturaRechazado() {
            FichajePatchRequest request = nuevoPatchRequest("Correccion");
            Fichaje fichaje = nuevoFichajePersistido(1L, MANANA);
            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichaje));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.actualizar(1L, request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fechas futuras");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("ENCARGADO + fichaje fecha pasada → IllegalArgumentException (400)")
        void encargadoConFichajePasadoRechazado() {
            FichajePatchRequest request = nuevoPatchRequest("Correccion");
            Fichaje fichaje = nuevoFichajePersistido(1L, AYER);
            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichaje));
            when(usuarioRepository.findByUsername("encargado"))
                    .thenReturn(Optional.of(usuarioEncargado));

            FichajeService service = nuevoServiceConFecha(HOY);

            assertThatThrownBy(() -> service.actualizar(1L, request, "encargado"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ENCARGADO solo puede gestionar");

            verify(fichajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN + fichaje fecha pasada → permitido")
        void adminConFichajePasadoPermitido() {
            FichajePatchRequest request = nuevoPatchRequest("Correccion historica");
            Fichaje fichaje = nuevoFichajePersistido(1L, AYER);
            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichaje));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));
            when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(inv -> inv.getArgument(0));

            FichajeService service = nuevoServiceConFecha(HOY);
            FichajeResponse response = service.actualizar(1L, request, "admin");

            assertThat(response).isNotNull();
            assertThat(response.getFecha()).isEqualTo(AYER);
            assertThat(response.getObservaciones()).isEqualTo("Correccion historica");
            verify(fichajeRepository).save(any(Fichaje.class));
        }

        @Test
        @DisplayName("recalculo de jornada descuenta totalPausasMinutos existente")
        void recalculoJornadaDescuentaPausas() {
            // Fichaje en HOY con pausas previas: 60 min.
            // PATCH con horaEntrada 09:00 y horaSalida 17:00 → 480 brutos.
            // jornadaEfectiva = ceil(480 - 60) = 420.
            Fichaje fichaje = nuevoFichajePersistido(1L, HOY);
            fichaje.setTotalPausasMinutos(60);

            FichajePatchRequest request = new FichajePatchRequest();
            request.setObservaciones("Corrige horario");
            request.setHoraEntrada(HOY.atTime(9, 0));
            request.setHoraSalida(HOY.atTime(17, 0));

            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichaje));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));

            ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
            when(fichajeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            FichajeService service = nuevoServiceConFecha(HOY);
            service.actualizar(1L, request, "admin");

            Fichaje actualizado = captor.getValue();
            assertThat(actualizado.getJornadaEfectivaMinutos()).isEqualTo(420);
            // El PATCH no toca totalPausasMinutos: sigue siendo 60.
            assertThat(actualizado.getTotalPausasMinutos()).isEqualTo(60);
        }

        @Test
        @DisplayName("PATCH solo con observaciones no modifica tipo ni horas existentes")
        void patchParcialSoloObservaciones() {
            Fichaje fichaje = nuevoFichajePersistido(1L, HOY);
            fichaje.setTipo(TipoFichaje.NORMAL);
            fichaje.setHoraEntrada(HOY.atTime(8, 0));
            fichaje.setHoraSalida(HOY.atTime(16, 0));
            fichaje.setObservaciones("inicial");

            FichajePatchRequest request = new FichajePatchRequest();
            request.setObservaciones("Solo cambia el motivo");
            // tipo, horaEntrada, horaSalida nulos → no se tocan

            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichaje));
            when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuarioAdmin));

            ArgumentCaptor<Fichaje> captor = ArgumentCaptor.forClass(Fichaje.class);
            when(fichajeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            FichajeService service = nuevoServiceConFecha(HOY);
            service.actualizar(1L, request, "admin");

            Fichaje actualizado = captor.getValue();
            assertThat(actualizado.getTipo()).isEqualTo(TipoFichaje.NORMAL);
            assertThat(actualizado.getHoraEntrada()).isEqualTo(HOY.atTime(8, 0));
            assertThat(actualizado.getHoraSalida()).isEqualTo(HOY.atTime(16, 0));
            assertThat(actualizado.getObservaciones()).isEqualTo("Solo cambia el motivo");
        }
    }

    // =================================================================
    // Helpers
    // =================================================================

    private FichajeRequest nuevoFichajeRequest(LocalDate fecha, TipoFichaje tipo) {
        FichajeRequest request = new FichajeRequest();
        request.setEmpleadoId(10L);
        request.setFecha(fecha);
        request.setTipo(tipo);
        request.setObservaciones("test");
        return request;
    }

    private FichajePatchRequest nuevoPatchRequest(String observaciones) {
        FichajePatchRequest request = new FichajePatchRequest();
        request.setObservaciones(observaciones);
        return request;
    }

    private Fichaje nuevoFichajePersistido(Long id, LocalDate fecha) {
        Fichaje fichaje = new Fichaje();
        fichaje.setId(id);
        fichaje.setEmpleado(empleado);
        fichaje.setFecha(fecha);
        fichaje.setTipo(TipoFichaje.NORMAL);
        fichaje.setTotalPausasMinutos(0);
        fichaje.setJornadaEfectivaMinutos(0);
        fichaje.setUsuario(usuarioAdmin);
        fichaje.setObservaciones("inicial");
        fichaje.setFechaCreacion(LocalDateTime.now());
        return fichaje;
    }
}
