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
import com.staffflow.dto.request.TerminalPausaRequest;
import com.staffflow.dto.request.TerminalPinRequest;
import com.staffflow.dto.response.TerminalEntradaResponse;
import com.staffflow.dto.response.TerminalPausaResponse;
import com.staffflow.dto.response.TerminalSalidaResponse;
import com.staffflow.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio de terminal de fichaje por PIN.
 *
 * Cubre los endpoints E48-E51:
 *   E48 POST /api/v1/terminal/entrada         → registrarEntrada()
 *   E49 POST /api/v1/terminal/salida          → registrarSalida()
 *   E50 POST /api/v1/terminal/pausa/iniciar   → iniciarPausa()
 *   E51 POST /api/v1/terminal/pausa/finalizar → finalizarPausa()
 *
 * Todos los endpoints son PÚBLICOS: no usan JWT. La autenticación se
 * realiza exclusivamente por PIN de 4 dígitos (decisión nº21).
 *
 * Bloqueo por dispositivo (RNF-S05, D-016, Opción A):
 *   Los intentos fallidos de PIN se acumulan en memoria (ConcurrentHashMap).
 *   Tras 5 intentos fallidos consecutivos desde el mismo dispositivoId,
 *   el service lanza HTTP 423 hasta que se reinicia el contador.
 *   El contador se reinicia con un intento exitoso.
 *   Limitación documentada: el contador se pierde al reiniciar el servidor.
 *   Esta implementación es suficiente para el alcance del TFG.
 *
 * @author Santiago Castillo
 */
@Service
@RequiredArgsConstructor
public class TerminalService {

    // ---------------------------------------------------------------
    // Constantes de bloqueo (RNF-S05)
    // ---------------------------------------------------------------

    /** Número máximo de intentos fallidos antes de bloquear el dispositivo. */
    private static final int MAX_INTENTOS = 5;

    // ---------------------------------------------------------------
    // Bloqueo en memoria por dispositivo (Opción A, D-016)
    // ConcurrentHashMap: seguro para acceso concurrente desde múltiples
    // peticiones simultáneas sin sincronización manual.
    // ---------------------------------------------------------------

    /**
     * Mapa de intentos fallidos acumulados por dispositivoId.
     * Clave: dispositivoId (String). Valor: contador de fallos consecutivos.
     * Se reinicia al 0 con un intento de PIN exitoso.
     */
    private final ConcurrentHashMap<String, AtomicInteger> intentosFallidos =
            new ConcurrentHashMap<>();

    // ---------------------------------------------------------------
    // Dependencias
    // ---------------------------------------------------------------

    private final EmpleadoRepository empleadoRepository;
    private final FichajeRepository fichajeRepository;
    private final PausaRepository pausaRepository;
    private final UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------
    // E48 — POST /api/v1/terminal/entrada
    // RF-46: Registrar entrada desde terminal
    // ---------------------------------------------------------------

    /**
     * Registra la entrada del empleado desde el terminal físico (RF-46).
     *
     * Flujo:
     *   1. Verificar bloqueo del dispositivo (423 si bloqueado).
     *   2. Buscar empleado por PIN → 404 si no existe, incrementar fallos.
     *   3. Verificar que el empleado está activo → 400 si está de baja.
     *   4. Verificar que no existe ya fichaje hoy → 409 si existe.
     *   5. Crear fichaje con horaEntrada = now(), horaSalida null.
     *   6. Reiniciar contador de fallos del dispositivo.
     *   7. Devolver TerminalEntradaResponse.
     *
     * No se usa JWT. El empleado se identifica exclusivamente por PIN.
     * El fichaje queda abierto (horaSalida null) hasta que se registre
     * la salida en E49.
     *
     * @param request pin + dispositivoId
     * @return nombre del empleado, hora de entrada y mensaje de confirmación
     */
    @Transactional
    public TerminalEntradaResponse registrarEntrada(TerminalPinRequest request) {

        // Verificar bloqueo del dispositivo antes de cualquier consulta
        verificarBloqueo(request.getDispositivoId());

        // Buscar empleado por PIN (índice UNIQUE, RNF-R03 < 100ms)
        Empleado empleado = buscarEmpleadoPorPin(request.getPin(), request.getDispositivoId());

        // Verificar que el empleado está activo
        // Baja lógica: activo=false impide fichar (decisión nº4)
        if (!Boolean.TRUE.equals(empleado.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El empleado está de baja y no puede fichar");
        }

        // Verificar que no existe ya fichaje hoy → 409
        LocalDate hoy = LocalDate.now();
        if (fichajeRepository.findByEmpleadoIdAndFecha(empleado.getId(), hoy).isPresent()) {
            throw new ConflictException(
                    "El empleado ya tiene registrada la entrada hoy " + hoy);
        }

        // Crear fichaje con horaEntrada = ahora, horaSalida = null
        // Auditoría: usuario sistema 'terminal_service' (D-021, Opción B).
        // El terminal no tiene sesión JWT — se usa usuario de servicio predefinido
        // para cumplir NOT NULL de usuario_id y mantener trazabilidad (RNF-L01).
        LocalDateTime ahora = LocalDateTime.now();
        Usuario usuarioSistema = obtenerUsuarioSistema();
        Fichaje fichaje = new Fichaje();
        fichaje.setEmpleado(empleado);
        fichaje.setFecha(hoy);
        fichaje.setHoraEntrada(ahora);
        fichaje.setHoraSalida(null);           // jornada abierta
        fichaje.setTotalPausasMinutos(0);
        fichaje.setJornadaEfectivaMinutos(0);
        fichaje.setObservaciones(null);
        fichaje.setUsuario(usuarioSistema);

        fichajeRepository.save(fichaje);

        // PIN correcto → reiniciar contador de fallos del dispositivo
        reiniciarFallos(request.getDispositivoId());

        return new TerminalEntradaResponse(
                empleado.getNombre(),
                ahora,
                "\u2714 Entrada registrada"
        );
    }

    // ---------------------------------------------------------------
    // E49 — POST /api/v1/terminal/salida
    // RF-47: Registrar salida desde terminal
    // ---------------------------------------------------------------

    /**
     * Registra la salida del empleado y calcula la jornada efectiva (RF-47).
     *
     * Flujo:
     *   1. Verificar bloqueo del dispositivo (423 si bloqueado).
     *   2. Buscar empleado por PIN → 404 si no existe.
     *   3. Buscar fichaje de hoy → 400 si no hay entrada registrada.
     *   4. Verificar que no hay ya salida → 409 si horaSalida ya tiene valor.
     *   5. Verificar que no hay pausa activa → 409 si hay pausa con horaFin null.
     *   6. Registrar horaSalida = now().
     *   7. Calcular jornadaEfectivaMinutos con Math.ceil.
     *   8. Reiniciar contador de fallos y devolver TerminalSalidaResponse.
     *
     * Fórmula jornada efectiva:
     *   minutosBrutos = horaSalida - horaEntrada (en minutos, ChronoUnit)
     *   jornadaEfectiva = Math.ceil(minutosBrutos - totalPausasMinutos)
     *
     * @param request pin + dispositivoId
     * @return nombre, hora de salida, jornada efectiva y mensaje
     */
    @Transactional
    public TerminalSalidaResponse registrarSalida(TerminalPinRequest request) {

        verificarBloqueo(request.getDispositivoId());

        Empleado empleado = buscarEmpleadoPorPin(request.getPin(), request.getDispositivoId());

        LocalDate hoy = LocalDate.now();

        // Verificar que hay entrada registrada hoy → 400 si no
        Fichaje fichaje = fichajeRepository.findByEmpleadoIdAndFecha(empleado.getId(), hoy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No hay entrada registrada hoy para este empleado"));

        // Verificar que no hay ya salida registrada → 409
        if (fichaje.getHoraSalida() != null) {
            throw new ConflictException("La salida ya está registrada para hoy");
        }

        // Verificar que no hay pausa activa → 409
        // No se puede registrar salida con pausa abierta (hora_fin = null)
        pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(empleado.getId(), hoy)
                .ifPresent(p -> {
                    throw new ConflictException(
                            "Hay una pausa activa pendiente de cerrar. Finaliza la pausa antes de fichar la salida");
                });

        // Registrar horaSalida
        LocalDateTime ahora = LocalDateTime.now();
        fichaje.setHoraSalida(ahora);

        // Calcular jornadaEfectivaMinutos
        // Math.ceil: redondeo al alza — beneficia al empleado en el cómputo final
        int jornadaEfectiva = 0;
        if (fichaje.getHoraEntrada() != null) {
            long minutosBrutos = ChronoUnit.MINUTES.between(fichaje.getHoraEntrada(), ahora);
            jornadaEfectiva = (int) Math.ceil(
                    (double)(minutosBrutos - fichaje.getTotalPausasMinutos()));
        }
        fichaje.setJornadaEfectivaMinutos(jornadaEfectiva);

        fichajeRepository.save(fichaje);
        reiniciarFallos(request.getDispositivoId());

        return new TerminalSalidaResponse(
                empleado.getNombre(),
                ahora,
                jornadaEfectiva,
                "\u2714 Salida registrada"
        );
    }

    // ---------------------------------------------------------------
    // E50 — POST /api/v1/terminal/pausa/iniciar
    // RF-48: Iniciar pausa desde terminal
    // ---------------------------------------------------------------

    /**
     * Inicia una pausa desde el terminal físico (RF-48).
     *
     * Flujo:
     *   1. Verificar bloqueo del dispositivo.
     *   2. Buscar empleado por PIN.
     *   3. Verificar que hay fichaje de entrada hoy → 400 si no.
     *   4. Verificar que no hay ya pausa activa → 409 si hay.
     *   5. Crear pausa con horaInicio = now(), horaFin = null.
     *   6. Reiniciar fallos y devolver TerminalPausaResponse.
     *
     * El empleado selecciona el tipo de pausa en el teclado táctil antes
     * de introducir el PIN. El tipo determina si la pausa descuenta de
     * la jornada efectiva (AUSENCIA_RETRIBUIDA no descuenta).
     *
     * @param request pin + tipoPausa + dispositivoId
     * @return nombre, hora inicio de pausa y mensaje
     */
    @Transactional
    public TerminalPausaResponse iniciarPausa(TerminalPausaRequest request) {

        verificarBloqueo(request.getDispositivoId());

        Empleado empleado = buscarEmpleadoPorPin(request.getPin(), request.getDispositivoId());

        LocalDate hoy = LocalDate.now();

        // Verificar que hay entrada registrada hoy → 400
        fichajeRepository.findByEmpleadoIdAndFecha(empleado.getId(), hoy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No hay entrada registrada hoy. Ficha la entrada antes de iniciar una pausa"));

        // Verificar que no hay pausa activa → 409
        pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(empleado.getId(), hoy)
                .ifPresent(p -> {
                    throw new ConflictException(
                            "Ya hay una pausa activa. Finaliza la pausa actual antes de iniciar otra");
                });

        // Crear pausa activa (horaFin = null)
        // Auditoría: usuario sistema 'terminal_service' (misma decisión que en E48)
        LocalDateTime ahora = LocalDateTime.now();
        Usuario usuarioSistema = obtenerUsuarioSistema();
        Pausa pausa = new Pausa();
        pausa.setEmpleado(empleado);
        pausa.setFecha(hoy);
        pausa.setHoraInicio(ahora);
        pausa.setHoraFin(null);              // pausa activa en curso
        pausa.setDuracionMinutos(null);      // se calcula al finalizar (E51)
        pausa.setTipoPausa(request.getTipoPausa());
        pausa.setObservaciones(null);
        pausa.setUsuario(usuarioSistema);

        pausaRepository.save(pausa);
        reiniciarFallos(request.getDispositivoId());

        return new TerminalPausaResponse(
                empleado.getNombre(),
                ahora,
                null,
                "\u2714 Pausa iniciada"
        );
    }

    // ---------------------------------------------------------------
    // E51 — POST /api/v1/terminal/pausa/finalizar
    // RF-49: Finalizar pausa desde terminal
    // ---------------------------------------------------------------

    /**
     * Finaliza la pausa activa del empleado desde el terminal (RF-49).
     *
     * Flujo:
     *   1. Verificar bloqueo del dispositivo.
     *   2. Buscar empleado por PIN.
     *   3. Buscar pausa activa (horaFin null) hoy → 400 si no hay.
     *   4. Registrar horaFin = now().
     *   5. Calcular duracionMinutos con Math.floor.
     *   6. Si la pausa NO es AUSENCIA_RETRIBUIDA → actualizar
     *      totalPausasMinutos en el fichaje del día.
     *   7. Reiniciar fallos y devolver TerminalPausaResponse.
     *
     * @param request pin + dispositivoId
     * @return nombre, duración de la pausa y mensaje
     */
    @Transactional
    public TerminalPausaResponse finalizarPausa(TerminalPinRequest request) {

        verificarBloqueo(request.getDispositivoId());

        Empleado empleado = buscarEmpleadoPorPin(request.getPin(), request.getDispositivoId());

        LocalDate hoy = LocalDate.now();

        // Buscar pausa activa hoy → 400 si no hay ninguna
        Pausa pausa = pausaRepository
                .findByEmpleadoIdAndFechaAndHoraFinIsNull(empleado.getId(), hoy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No hay pausa activa para finalizar"));

        // Registrar horaFin y calcular duración
        LocalDateTime ahora = LocalDateTime.now();
        pausa.setHoraFin(ahora);

        // Math.floor: redondeo a la baja — beneficia al empleado en pausas
        long minutos = ChronoUnit.MINUTES.between(pausa.getHoraInicio(), ahora);
        int duracion = (int) Math.floor((double) minutos);
        pausa.setDuracionMinutos(duracion);

        pausaRepository.save(pausa);

        // Actualizar totalPausasMinutos en el fichaje si la pausa no es retribuida
        // AUSENCIA_RETRIBUIDA: no descuenta de jornada efectiva (RF-35)
        if (pausa.getTipoPausa() != TipoPausa.AUSENCIA_RETRIBUIDA) {
            fichajeRepository.findByEmpleadoIdAndFecha(empleado.getId(), hoy)
                    .ifPresent(fichaje -> {
                        fichaje.setTotalPausasMinutos(
                                fichaje.getTotalPausasMinutos() + duracion);
                        fichajeRepository.save(fichaje);
                    });
        }

        reiniciarFallos(request.getDispositivoId());

        return new TerminalPausaResponse(
                empleado.getNombre(),
                null,
                duracion,
                "\u2714 Pausa finalizada"
        );
    }

    // ---------------------------------------------------------------
    // Métodos auxiliares privados
    // ---------------------------------------------------------------

    /**
     * Obtiene el usuario de servicio predefinido para auditoría de terminal.
     *
     * El terminal no tiene sesión JWT, pero la columna usuario_id en fichajes
     * y pausas es NOT NULL (RNF-L01 exige trazabilidad). Se usa el usuario
     * 'terminal_service' creado en el INSERT inicial de datos del sistema.
     *
     * Si el usuario no existe en BD (por ejemplo en un entorno nuevo sin datos
     * iniciales), lanza EntityNotFoundException con mensaje claro para facilitar
     * el diagnóstico durante el despliegue.
     *
     * @return entidad Usuario del usuario de servicio de terminal
     */
    private Usuario obtenerUsuarioSistema() {
        return usuarioRepository.findByUsername("terminal_service")
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario de servicio 'terminal_service' no encontrado. " +
                        "Ejecuta el INSERT de datos iniciales del sistema."));
    }

    /**
     * Verifica si el dispositivo está bloqueado por exceso de intentos fallidos.
     * Lanza HTTP 423 Locked si el dispositivo ha superado MAX_INTENTOS (5).
     *
     * @param dispositivoId identificador del terminal físico
     */
    private void verificarBloqueo(String dispositivoId) {
        AtomicInteger intentos = intentosFallidos.get(dispositivoId);
        if (intentos != null && intentos.get() >= MAX_INTENTOS) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "Dispositivo bloqueado por " + MAX_INTENTOS
                    + " intentos fallidos de PIN. Contacta con el encargado");
        }
    }

    /**
     * Busca un empleado por su PIN de terminal.
     * Si el PIN no existe → incrementa el contador de fallos del dispositivo
     * y lanza EntityNotFoundException (HTTP 404).
     *
     * @param pin          PIN de 4 dígitos introducido en el terminal
     * @param dispositivoId identificador del terminal para el contador de fallos
     * @return empleado encontrado
     */
    private Empleado buscarEmpleadoPorPin(String pin, String dispositivoId) {
        return empleadoRepository.findByPinTerminal(pin)
                .orElseGet(() -> {
                    // PIN incorrecto → incrementar contador de fallos del dispositivo
                    intentosFallidos
                            .computeIfAbsent(dispositivoId, k -> new AtomicInteger(0))
                            .incrementAndGet();
                    throw new EntityNotFoundException(
                            "PIN incorrecto o no registrado");
                });
    }

    /**
     * Reinicia el contador de intentos fallidos de un dispositivo.
     * Se llama después de cualquier operación exitosa con PIN correcto.
     *
     * @param dispositivoId identificador del terminal a desbloquear
     */
    private void reiniciarFallos(String dispositivoId) {
        intentosFallidos.remove(dispositivoId);
    }
}
