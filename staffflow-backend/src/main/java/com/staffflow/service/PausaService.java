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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de gestión de pausas.
 *
 * Cubre los endpoints E27-E29:
 *   E27 POST  /api/v1/pausas       → crear()
 *   E28 PATCH /api/v1/pausas/{id}  → cerrar()
 *   E29 GET   /api/v1/pausas       → listar()
 *
 * Reglas de negocio clave:
 *   - RNF-L01: sin DELETE en pausas. Nunca se eliminan registros.
 *   - Una pausa activa = horaFin IS NULL. Solo puede haber una por empleado/día.
 *   - duracionMinutos se calcula con Math.floor al cerrar (beneficia al empleado).
 *   - Al cerrar una pausa no retribuida se actualiza totalPausasMinutos
 *     en el fichaje del día y se recalcula jornadaEfectivaMinutos.
 *   - Pausas AUSENCIA_RETRIBUIDA NO descuentan de la jornada efectiva.
 *   - D-026: ENCARGADO solo puede gestionar pausas del dia actual.
 *     ADMIN no tiene restriccion de fecha. La validacion se aplica en
 *     crear() (E27) y cerrar() (E28).
 *
 * Patrón de autenticación:
 *   El controller pasa authentication.getName() (username). El service
 *   resuelve el usuarioId a partir del username (D-017, Opción B).
 *
 * Roles:
 *   ADMIN y ENCARGADO → E27, E28, E29
 */
@Service
@RequiredArgsConstructor
public class PausaService {

    // ---------------------------------------------------------------
    // Dependencias
    // ---------------------------------------------------------------

    /** Repositorio de pausas. */
    private final PausaRepository pausaRepository;

    /** Repositorio de empleados. Necesario para verificar existencia. */
    private final EmpleadoRepository empleadoRepository;

    /**
     * Repositorio de fichajes. Necesario en E28 (cerrar pausa): al cerrar
     * una pausa no retribuida hay que actualizar totalPausasMinutos del
     * fichaje del día y recalcular jornadaEfectivaMinutos.
     */
    private final FichajeRepository fichajeRepository;

    /**
     * Repositorio de usuarios. Para resolver username → usuario (auditoría
     * y restriccion D-026). Mismo patrón que FichajeService y EmpleadoService
     * (D-017, Opción B).
     */
    private final UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------
    // E27 — POST /api/v1/pausas
    // ---------------------------------------------------------------

    /**
     * Registra una pausa manual para un empleado.
     *
     * Flujo:
     *   1. Verifica que el empleado existe (404 si no).
     *   2. Resuelve el usuario autenticado desde username.
     *   3. Valida restriccion de fecha si el usuario es ENCARGADO (D-026):
     *      solo puede gestionar pausas del dia actual. ADMIN sin restriccion.
     *   4. Verifica que no hay ya una pausa activa ese día (409 si hay).
     *      Solo puede haber una pausa con horaFin=null por empleado/día.
     *   5. Si llega horaFin, calcula duracionMinutos con Math.floor.
     *   6. Persiste la pausa y devuelve PausaResponse.
     *
     * horaFin es nullable: si no se proporciona, la pausa queda activa.
     * Las observaciones son opcionales en pausas (a diferencia de fichajes).
     *
     * @param request  datos de la pausa recibidos del controller
     * @param username username del usuario autenticado (de authentication.getName())
     * @return PausaResponse con los datos de la pausa creada
     */
    @Transactional
    public PausaResponse crear(PausaRequest request, String username) {

        // Verificar que el empleado existe (404 si no)
        Empleado empleado = empleadoRepository.findById(request.getEmpleadoId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Empleado no encontrado con id: " + request.getEmpleadoId()));

        // Resolver usuario autenticado para restriccion D-026 y auditoria
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario autenticado no encontrado: " + username));

        // Pausas en fechas futuras no permitidas para ningún rol.
        if (request.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "No se pueden registrar pausas en fechas futuras");
        }

        // D-026: ENCARGADO puede gestionar hoy y futuro, no el pasado.
        // ADMIN puede crear pausas para cualquier fecha sin restriccion.
        if (usuario.getRol() == Rol.ENCARGADO
                && request.getFecha().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "El ENCARGADO solo puede gestionar registros del dia actual y fechas futuras");
        }

        // Verificar que no hay pausa activa ese día para ese empleado
        // 409 Conflict si hay pausa con horaFin=null — no se pueden solapar pausas
        pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(
                request.getEmpleadoId(), request.getFecha())
                .ifPresent(p -> {
                    throw new ConflictException(
                            "El empleado " + request.getEmpleadoId()
                            + " ya tiene una pausa activa el " + request.getFecha());
                });

        // Construir entidad Pausa
        Pausa pausa = new Pausa();
        pausa.setEmpleado(empleado);
        pausa.setFecha(request.getFecha());
        pausa.setHoraInicio(request.getHoraInicio());
        pausa.setHoraFin(request.getHoraFin());         // nullable: pausa puede crearse abierta
        pausa.setTipoPausa(request.getTipoPausa());
        pausa.setObservaciones(request.getObservaciones());
        pausa.setUsuario(usuario);

        // Calcular duracionMinutos si llega horaFin
        // Math.floor: redondeo a la baja — beneficia al empleado (decisión de diseño)
        if (request.getHoraFin() != null) {
            long minutos = ChronoUnit.MINUTES.between(
                    request.getHoraInicio(), request.getHoraFin());
            pausa.setDuracionMinutos((int) Math.floor((double) minutos));
        } else {
            // Pausa activa — duracion null hasta que se cierre (E28)
            pausa.setDuracionMinutos(null);
        }

        Pausa guardada = pausaRepository.save(pausa);
        return toPausaResponse(guardada, empleado);
    }

    // ---------------------------------------------------------------
    // E28 — PATCH /api/v1/pausas/{id}
    // ---------------------------------------------------------------

    /**
     * Cierra o modifica una pausa existente.
     *
     * Casos de uso principales:
     *   A) Cerrar pausa activa: se proporciona horaFin → calcula duracionMinutos.
     *   B) Modificar observaciones de una pausa ya cerrada.
     *
     * Observaciones obligatorias (RNF-L02): si llegan null o vacías → 400.
     *
     * Restriccion D-026: ENCARGADO solo puede modificar pausas del dia actual.
     * La fecha a validar es la de la pausa cargada de BD, no del request.
     * ADMIN puede modificar pausas de cualquier fecha sin restriccion.
     *
     * Efecto en el fichaje del día (solo si la pausa NO es AUSENCIA_RETRIBUIDA):
     *   Al cerrar una pausa (horaFin llega en el request) se actualiza
     *   totalPausasMinutos del fichaje del día sumando la nueva duración,
     *   y se recalcula jornadaEfectivaMinutos.
     *   Las pausas AUSENCIA_RETRIBUIDA no descuentan de la jornada efectiva:
     *   no se suman a totalPausasMinutos del fichaje.
     *
     * @param id       ID de la pausa a modificar
     * @param request  campos a modificar (observaciones obligatorio, horaFin opcional)
     * @param username username del usuario autenticado (de authentication.getName())
     * @return PausaResponse con la pausa actualizada
     */
    @Transactional
    public PausaResponse cerrar(Long id, PausaPatchRequest request, String username) {

        // Validación de observaciones obligatorias (RNF-L02)
        if (request.getObservaciones() == null || request.getObservaciones().isBlank()) {
            throw new IllegalArgumentException(
                    "Las observaciones son obligatorias al modificar una pausa (RNF-L02)");
        }

        // Buscar pausa — 404 si no existe
        Pausa pausa = pausaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Pausa no encontrada con id: " + id));

        // Resolver usuario autenticado para restriccion D-026
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario autenticado no encontrado: " + username));

        // Pausas en fechas futuras no modificables para ningún rol.
        if (pausa.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "No se pueden modificar pausas en fechas futuras");
        }

        // D-026: ENCARGADO puede modificar hoy y futuro, no el pasado.
        // La fecha a validar es la de la pausa cargada de BD.
        // ADMIN puede modificar pausas de cualquier fecha sin restriccion.
        if (usuario.getRol() == Rol.ENCARGADO
                && pausa.getFecha().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "El ENCARGADO solo puede gestionar registros del dia actual y fechas futuras");
        }

        // Actualizar observaciones (ya validadas)
        pausa.setObservaciones(request.getObservaciones());

        // Si llega horaFin en el request, cerrar la pausa y calcular duración
        if (request.getHoraFin() != null) {
            pausa.setHoraFin(request.getHoraFin());

            // Calcular duracionMinutos con Math.floor (beneficia al empleado)
            long minutos = ChronoUnit.MINUTES.between(
                    pausa.getHoraInicio(), request.getHoraFin());
            int duracion = (int) Math.floor((double) minutos);
            pausa.setDuracionMinutos(duracion);

            // Actualizar totalPausasMinutos en el fichaje del día
            // Solo para pausas que NO son AUSENCIA_RETRIBUIDA (no descuentan jornada)
            if (pausa.getTipoPausa() != TipoPausa.AUSENCIA_RETRIBUIDA) {
                actualizarFichajePorPausa(pausa.getEmpleado().getId(),
                        pausa.getFecha(), duracion);
            }
            // AUSENCIA_RETRIBUIDA: se acumula en horas_ausencia_retribuida del saldo anual.
            // Ese cálculo lo hace SaldoService — PausaService no toca saldos_anuales.
        }

        Pausa actualizada = pausaRepository.save(pausa);
        return toPausaResponse(actualizada, actualizada.getEmpleado());
    }

    // ---------------------------------------------------------------
    // E29 — GET /api/v1/pausas
    // ---------------------------------------------------------------

    /**
     * Lista pausas con filtros opcionales y combinables (RF-24).
     *
     * Filtros disponibles (todos opcionales y combinables):
     *   - empleadoId → filtra por empleado concreto
     *   - desde/hasta → rango de fechas inclusivo en ambos extremos
     *   - tipoPausa → filtra por tipo de pausa
     *
     * Sin filtros → devuelve todas las pausas de todos los empleados.
     *
     * @param empleadoId filtro opcional por ID de empleado
     * @param desde      filtro opcional fecha inicio del rango
     * @param hasta      filtro opcional fecha fin del rango
     * @param tipoPausa  filtro opcional por tipo de pausa
     * @return lista de PausaResponse (puede ser vacía)
     */
    @Transactional(readOnly = true)
    public List<PausaResponse> listar(Long empleadoId, LocalDate desde,
                                      LocalDate hasta, TipoPausa tipoPausa) {

        return pausaRepository
                .findByFiltros(empleadoId, desde, hasta, tipoPausa)
                .stream()
                .map(p -> toPausaResponse(p, p.getEmpleado()))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // E35 — GET /api/v1/pausas/me
    // ---------------------------------------------------------------

    /**
     * Lista las pausas del empleado autenticado en un rango de fechas (E35).
     *
     * Mismo patrón que FichajeService.listarPropios() (D-017, Opción B):
     * recibe el username del controller, resuelve usuario → empleado,
     * y filtra las pausas por empleadoId + rango de fechas.
     *
     * Spring Security garantiza que el rol EMPLEADO solo puede llegar
     * aquí con su propio token — no puede ver pausas ajenas.
     *
     * @param username username del empleado autenticado (de authentication.getName())
     * @param desde    filtro opcional fecha inicio
     * @param hasta    filtro opcional fecha fin
     * @return lista de PausaResponse del empleado autenticado
     */
    @Transactional(readOnly = true)
    public List<PausaResponse> listarPropios(String username, LocalDate desde, LocalDate hasta) {

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario autenticado no encontrado: " + username));

        Empleado empleado = empleadoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "El usuario autenticado no tiene perfil de empleado"));

        return pausaRepository
                .findByFiltros(empleado.getId(), desde, hasta, null)
                .stream()
                .map(p -> toPausaResponse(p, p.getEmpleado()))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Método auxiliar: actualizar fichaje al cerrar pausa
    // ---------------------------------------------------------------

    /**
     * Actualiza totalPausasMinutos y recalcula jornadaEfectivaMinutos
     * en el fichaje del día cuando se cierra una pausa no retribuida.
     *
     * Flujo:
     *   1. Busca el fichaje del empleado para esa fecha.
     *   2. Suma la duración de la pausa a totalPausasMinutos.
     *   3. Recalcula jornadaEfectivaMinutos si el fichaje tiene entrada y salida.
     *      Fórmula: Math.ceil(minutos brutos - totalPausasMinutos)
     *   4. Persiste el fichaje actualizado.
     *
     * Si no existe fichaje para esa fecha, se ignora silenciosamente:
     * puede ser una pausa manual registrada retroactivamente sin fichaje asociado.
     *
     * @param empleadoId ID del empleado
     * @param fecha      fecha del fichaje a actualizar
     * @param duracion   duración en minutos de la pausa que se acaba de cerrar
     */
    private void actualizarFichajePorPausa(Long empleadoId, LocalDate fecha, int duracion) {

        // Buscar fichaje del día — si no existe, no hay nada que actualizar
        fichajeRepository.findByEmpleadoIdAndFecha(empleadoId, fecha)
                .ifPresent(fichaje -> {

                    // Sumar duración de esta pausa al acumulado del día
                    int totalPausas = fichaje.getTotalPausasMinutos() + duracion;
                    fichaje.setTotalPausasMinutos(totalPausas);

                    // Recalcular jornadaEfectivaMinutos si hay entrada y salida
                    // Fórmula: Math.ceil(minutos brutos - totalPausasMinutos)
                    if (fichaje.getHoraEntrada() != null && fichaje.getHoraSalida() != null) {
                        long minutosBrutos = ChronoUnit.MINUTES.between(
                                fichaje.getHoraEntrada(), fichaje.getHoraSalida());
                        int jornadaEfectiva = (int) Math.ceil(
                                (double)(minutosBrutos - totalPausas));
                        fichaje.setJornadaEfectivaMinutos(jornadaEfectiva);
                    }

                    fichajeRepository.save(fichaje);
                });
    }

    // ---------------------------------------------------------------
    // Método auxiliar de conversión entidad → DTO
    // ---------------------------------------------------------------

    /**
     * Convierte una entidad Pausa en PausaResponse.
     *
     * PausaResponse usa @AllArgsConstructor sin @Builder.
     * Orden de campos según declaración en PausaResponse.java:
     * id, empleadoId, fecha, horaInicio, horaFin, duracionMinutos,
     * tipoPausa, usuarioId, observaciones, fechaCreacion
     *
     * @param pausa    entidad a convertir
     * @param empleado entidad empleado ya cargada (evita N+1)
     * @return PausaResponse listo para serializar como JSON
     */
    private PausaResponse toPausaResponse(Pausa pausa, Empleado empleado) {
        return new PausaResponse(
                pausa.getId(),
                empleado.getId(),
                pausa.getFecha(),
                pausa.getHoraInicio(),
                pausa.getHoraFin(),
                pausa.getDuracionMinutos(),
                pausa.getTipoPausa(),
                pausa.getUsuario() != null ? pausa.getUsuario().getId() : null,
                pausa.getObservaciones(),
                pausa.getFechaCreacion()
        );
    }
}
