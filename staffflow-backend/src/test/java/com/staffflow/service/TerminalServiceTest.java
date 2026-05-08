package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.Pausa;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.TipoPausa;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.TerminalPinRequest;
import com.staffflow.dto.response.TerminalSalidaResponse;
import com.staffflow.exception.ConflictException;
import com.staffflow.exception.PinBloqueadoException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de TerminalService.
 *
 * Verifica la logica de negocio del terminal de fichaje por PIN:
 *   - Bloqueo de dispositivo tras MAX_INTENTOS (5) fallos consecutivos (RNF-S05).
 *   - Incremento y reset del contador de intentos fallidos.
 *   - Validaciones de estado previo a cada operacion (entrada/salida/pausa).
 *
 * Los repositorios se mockean con Mockito: no se necesita ni BD ni contexto Spring.
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TerminalService — logica de PIN y bloqueo de dispositivo")
class TerminalServiceTest {

    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private FichajeRepository fichajeRepository;
    @Mock private PausaRepository pausaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private TerminalService terminalService;

    private static final String DISPOSITIVO = "terminal-01";
    private static final String PIN_VALIDO   = "1234";
    private static final String PIN_INVALIDO = "0000";

    private Empleado empleadoActivo;
    private Usuario usuarioSistema;

    @BeforeEach
    void setUp() {
        empleadoActivo = new Empleado();
        empleadoActivo.setId(1L);
        empleadoActivo.setNombre("Ana");
        empleadoActivo.setApellido1("García");
        empleadoActivo.setActivo(true);
        empleadoActivo.setPinTerminal(PIN_VALIDO);
        empleadoActivo.setJornadaDiariaMinutos(480);

        usuarioSistema = new Usuario();
        usuarioSistema.setId(99L);
        usuarioSistema.setUsername("terminal_service");
    }

    /** Crea un TerminalPinRequest con los campos dados (TerminalPinRequest es @Data, sin @AllArgsConstructor). */
    private TerminalPinRequest req(String pin, String dispositivo) {
        TerminalPinRequest r = new TerminalPinRequest();
        r.setPin(pin);
        r.setDispositivoId(dispositivo);
        return r;
    }

    // ---------------------------------------------------------------
    // Bloqueo de dispositivo (RNF-S05)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("obtenerEstado — 5 intentos fallidos — el dispositivo queda bloqueado (423)")
    void obtenerEstado_cincoIntentosFallidos_dispositivoBloqueado() {
        // Simular 5 intentos fallidos con PIN incorrecto
        when(empleadoRepository.findByPinTerminal(PIN_INVALIDO)).thenReturn(Optional.empty());

        TerminalPinRequest request = req(PIN_INVALIDO, DISPOSITIVO);

        for (int i = 0; i < 5; i++) {
            // Cada intento debe lanzar 404 (PIN incorrecto) e incrementar contador
            assertThatThrownBy(() -> terminalService.obtenerEstado(request))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        // El sexto intento debe lanzar PinBloqueadoException (el GlobalExceptionHandler la convierte a 423)
        assertThatThrownBy(() -> terminalService.obtenerEstado(request))
                .isInstanceOf(PinBloqueadoException.class)
                .hasMessageContaining("bloqueado");
    }

    @Test
    @DisplayName("obtenerEstado — PIN correcto tras fallos — reinicia contador")
    void obtenerEstado_pinCorrectoTrasFallos_reiniciaContador() {
        // 4 intentos fallidos (justo debajo del limite)
        when(empleadoRepository.findByPinTerminal(PIN_INVALIDO)).thenReturn(Optional.empty());
        TerminalPinRequest requestFallo = req(PIN_INVALIDO, DISPOSITIVO);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> terminalService.obtenerEstado(requestFallo))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        // 1 intento exitoso → reinicia contador
        when(empleadoRepository.findByPinTerminal(PIN_VALIDO)).thenReturn(Optional.of(empleadoActivo));
        when(fichajeRepository.findByEmpleadoIdAndFecha(anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        TerminalPinRequest requestOk = req(PIN_VALIDO, DISPOSITIVO);
        assertThatNoException().isThrownBy(() -> terminalService.obtenerEstado(requestOk));

        // Ahora el contador esta en 0: 5 nuevos fallos deben volver a bloquear
        // (si el contador no se reinicio, habria bloqueado antes)
        when(empleadoRepository.findByPinTerminal(PIN_INVALIDO)).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> terminalService.obtenerEstado(requestFallo))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        // El siguiente debe ser PinBloqueadoException (bloqueado de nuevo), no EntityNotFoundException
        assertThatThrownBy(() -> terminalService.obtenerEstado(requestFallo))
                .isInstanceOf(PinBloqueadoException.class)
                .hasMessageContaining("bloqueado");
    }

    @Test
    @DisplayName("obtenerEstado — PIN incorrecto — lanza EntityNotFoundException (404)")
    void obtenerEstado_pinIncorrecto_lanzaEntityNotFoundException() {
        when(empleadoRepository.findByPinTerminal(PIN_INVALIDO)).thenReturn(Optional.empty());
        TerminalPinRequest request = req(PIN_INVALIDO, DISPOSITIVO);

        assertThatThrownBy(() -> terminalService.obtenerEstado(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("PIN incorrecto");
    }

    // ---------------------------------------------------------------
    // registrarEntrada — validaciones de estado
    // ---------------------------------------------------------------

    @Test
    @DisplayName("registrarEntrada — empleado inactivo — lanza 400")
    void registrarEntrada_empleadoInactivo_lanza400() {
        Empleado inactivo = new Empleado();
        inactivo.setId(2L);
        inactivo.setActivo(false);
        inactivo.setPinTerminal(PIN_VALIDO);

        when(empleadoRepository.findByPinTerminal(PIN_VALIDO)).thenReturn(Optional.of(inactivo));

        TerminalPinRequest request = req(PIN_VALIDO, DISPOSITIVO);

        assertThatThrownBy(() -> terminalService.registrarEntrada(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("registrarEntrada — fichaje ya existe hoy — lanza ConflictException (409)")
    void registrarEntrada_fichajeYaExisteHoy_lanzaConflict409() {
        when(empleadoRepository.findByPinTerminal(PIN_VALIDO)).thenReturn(Optional.of(empleadoActivo));
        when(fichajeRepository.findByEmpleadoIdAndFecha(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(new Fichaje()));

        TerminalPinRequest request = req(PIN_VALIDO, DISPOSITIVO);

        assertThatThrownBy(() -> terminalService.registrarEntrada(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya tiene registrada la entrada");
    }

    @Test
    @DisplayName("registrarEntrada — primer fichaje del dia — persiste y devuelve respuesta")
    void registrarEntrada_primerFichajeDia_persisteYDevuelveRespuesta() {
        when(empleadoRepository.findByPinTerminal(PIN_VALIDO)).thenReturn(Optional.of(empleadoActivo));
        when(fichajeRepository.findByEmpleadoIdAndFecha(anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(usuarioRepository.findByUsername("terminal_service"))
                .thenReturn(Optional.of(usuarioSistema));
        when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(inv -> inv.getArgument(0));

        TerminalPinRequest request = req(PIN_VALIDO, DISPOSITIVO);

        var response = terminalService.registrarEntrada(request);

        assertThat(response).isNotNull();
        assertThat(response.getNombre()).isEqualTo("Ana");
        assertThat(response.getHoraEntrada()).isNotNull();
        verify(fichajeRepository).save(any(Fichaje.class));
    }

    // ---------------------------------------------------------------
    // registrarSalida — validaciones de estado
    // ---------------------------------------------------------------

    @Test
    @DisplayName("registrarSalida — sin entrada previa — lanza 400")
    void registrarSalida_sinEntradaPrevia_lanza400() {
        when(empleadoRepository.findByPinTerminal(PIN_VALIDO)).thenReturn(Optional.of(empleadoActivo));
        when(fichajeRepository.findByEmpleadoIdAndFecha(anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        TerminalPinRequest request = req(PIN_VALIDO, DISPOSITIVO);

        assertThatThrownBy(() -> terminalService.registrarSalida(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("registrarSalida — salida ya registrada — lanza ConflictException (409)")
    void registrarSalida_salidaYaRegistrada_lanzaConflict409() {
        Fichaje fichajeConSalida = new Fichaje();
        fichajeConSalida.setHoraEntrada(LocalDateTime.now().minusHours(8));
        fichajeConSalida.setHoraSalida(LocalDateTime.now().minusHours(1));

        when(empleadoRepository.findByPinTerminal(PIN_VALIDO)).thenReturn(Optional.of(empleadoActivo));
        when(fichajeRepository.findByEmpleadoIdAndFecha(anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.of(fichajeConSalida));

        TerminalPinRequest request = req(PIN_VALIDO, DISPOSITIVO);

        assertThatThrownBy(() -> terminalService.registrarSalida(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya está registrada");
    }

    @Test
    @DisplayName("registrarSalida — pausa activa abierta — lanza ConflictException (409)")
    void registrarSalida_pausaActivaAbierta_lanzaConflict409() {
        Fichaje fichajeAbierto = new Fichaje();
        fichajeAbierto.setHoraEntrada(LocalDateTime.now().minusHours(4));
        fichajeAbierto.setHoraSalida(null);
        fichajeAbierto.setTotalPausasMinutos(0);

        when(empleadoRepository.findByPinTerminal(PIN_VALIDO)).thenReturn(Optional.of(empleadoActivo));
        when(fichajeRepository.findByEmpleadoIdAndFecha(anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.of(fichajeAbierto));
        when(pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.of(new Pausa()));

        TerminalPinRequest request = req(PIN_VALIDO, DISPOSITIVO);

        assertThatThrownBy(() -> terminalService.registrarSalida(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("pausa activa");
    }

    @Test
    @DisplayName("registrarSalida — jornada completa — calcula jornadaEfectivaSegundos correctamente")
    void registrarSalida_jornadaCompleta_calculaJornadaEfectiva() {
        // Entrada hace 480 min, pausa COMIDA de 30 min → jornada efectiva ≈ 450 min = 27000 s
        LocalDateTime inicioPausa = LocalDateTime.now().minusMinutes(240);
        Pausa pausaCompletada = new Pausa();
        pausaCompletada.setHoraInicio(inicioPausa);
        pausaCompletada.setHoraFin(inicioPausa.plusMinutes(30));
        pausaCompletada.setTipoPausa(TipoPausa.COMIDA);

        Fichaje fichajeAbierto = new Fichaje();
        fichajeAbierto.setHoraEntrada(LocalDateTime.now().minusMinutes(480));
        fichajeAbierto.setHoraSalida(null);
        fichajeAbierto.setTotalPausasMinutos(30);

        when(empleadoRepository.findByPinTerminal(PIN_VALIDO)).thenReturn(Optional.of(empleadoActivo));
        when(fichajeRepository.findByEmpleadoIdAndFecha(anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.of(fichajeAbierto));
        when(pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(pausaRepository.findByEmpleadoIdAndFecha(anyLong(), any(LocalDate.class)))
                .thenReturn(List.of(pausaCompletada));
        when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pausaRepository.countByEmpleadoIdAndFecha(anyLong(), any(LocalDate.class))).thenReturn(1);

        TerminalPinRequest request = req(PIN_VALIDO, DISPOSITIVO);
        TerminalSalidaResponse response = terminalService.registrarSalida(request);

        // totalPausasSegundos = 30 min * 60 = 1800 s exactos
        // jornadaEfectivaSegundos ≈ 480 min * 60 - 1800 = 27000 s (tolerancia ±2s por ejecucion)
        assertThat(response.getTotalPausasSegundos()).isEqualTo(1800);
        assertThat(response.getJornadaEfectivaSegundos()).isCloseTo(27000, within(2));
    }
}
