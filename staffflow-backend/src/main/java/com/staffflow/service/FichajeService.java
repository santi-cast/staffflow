package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.FichajeRequest;
import com.staffflow.dto.request.FichajePatchRequest;
import com.staffflow.dto.response.FichajeResponse;
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
 * Servicio de gestión de fichajes.
 *
 * Cubre los endpoints E22-E26:
 *   E22 POST   /api/v1/fichajes             → crear()
 *   E23 PATCH  /api/v1/fichajes/{id}        → actualizar()
 *   E24 GET    /api/v1/fichajes             → listar()
 *   E25 GET    /api/v1/fichajes/incompletos → listarIncompletos()
 *   E26 GET    /api/v1/fichajes/me          → listarPropios()
 *
 * Reglas de negocio clave:
 *   - RNF-L01: sin DELETE en fichajes. Nunca se eliminan registros.
 *   - RNF-L02: observaciones obligatorias en crear() y actualizar().
 *   - UNIQUE(empleado_id, fecha): un solo fichaje por empleado por día.
 *   - jornadaEfectivaMinutos se calcula con Math.ceil sobre minutos brutos
 *     menos totalPausasMinutos (ya almacenado en el fichaje).
 *   - ENCARGADO puede gestionar hoy y fechas futuras, no el pasado.
 *     ADMIN no tiene restriccion de fecha. La validacion se aplica en
 *     crear() (E22) y actualizar() (E23).
 *   - Fichajes en fechas futuras no permitidos para ningún rol.
 *
 * Patrón de autenticación:
 *   El controller pasa authentication.getName() (username) a los métodos
 *   que necesitan identificar al usuario autenticado. El service resuelve
 *   el id a partir del username usando UsuarioRepository. Este es el mismo
 *   patrón usado en EmpleadoService.obtenerMiPerfil() (E21).
 *
 * Roles:
 *   ADMIN y ENCARGADO → E22, E23, E24, E25
 *   EMPLEADO          → E26 (solo sus propios fichajes)
 */
@Service
@RequiredArgsConstructor
public class FichajeService {

    // ---------------------------------------------------------------
    // Dependencias inyectadas por constructor (Lombok @RequiredArgsConstructor)
    // ---------------------------------------------------------------

    /** Repositorio de fichajes. Contiene métodos custom para filtros y búsquedas. */
    private final FichajeRepository fichajeRepository;

    /** Repositorio de empleados. Necesario para verificar existencia y resolver /me. */
    private final EmpleadoRepository empleadoRepository;

    /**
     * Repositorio de usuarios. Necesario para resolver username → usuarioId en E22
     * (auditoría) y username → empleadoId en E26 (/me). En E22 y E23 tambien se usa
     * para resolver el rol del usuario autenticado y aplicar la restriccion de fecha
     * del ENCARGADO. Mismo patrón que EmpleadoService.
     */
    private final UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------
    // E22 — POST /api/v1/fichajes
    // ---------------------------------------------------------------

    /**
     * Registra un fichaje manual para un empleado.
     *
     * Flujo:
     *   1. Valida que las observaciones no están vacías (RNF-L02).
     *   2. Verifica que el empleado existe (404 si no).
     *   3. Resuelve el usuario autenticado desde username.
     *   4. Valida restriccion de fecha si el usuario es ENCARGADO:
     *      solo puede gestionar el dia actual. ADMIN sin restriccion.
     *   5. Verifica unicidad empleado+fecha antes de persistir (409 si existe).
     *   6. Calcula jornadaEfectivaMinutos si hay horaEntrada y horaSalida.
     *   7. Persiste el fichaje y devuelve FichajeResponse.
     *
     * Observaciones obligatorias (RNF-L02): todo fichaje manual debe dejar
     * constancia del motivo. Si llegan null o vacías → IllegalArgumentException → 400.
     *
     * La UNIQUE(empleado_id, fecha) de BD también protege la unicidad, pero
     * validamos antes para devolver 409 con mensaje claro en lugar de
     * DataIntegrityViolationException.
     *
     * @param request  datos del fichaje recibidos del controller
     * @param username username del usuario autenticado (de authentication.getName())
     * @return FichajeResponse con los datos del fichaje creado
     */
    @Transactional
    public FichajeResponse crear(FichajeRequest request, String username) {

        // Validación de observaciones obligatorias (RNF-L02)
        // Todo fichaje manual debe dejar constancia del motivo
        if (request.getObservaciones() == null || request.getObservaciones().isBlank()) {
            throw new IllegalArgumentException(
                    "Las observaciones son obligatorias en fichajes manuales (RNF-L02)");
        }

        // Verificar que el empleado existe (404 si no)
        Empleado empleado = empleadoRepository.findById(request.getEmpleadoId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Empleado no encontrado con id: " + request.getEmpleadoId()));

        // Resolver usuario autenticado desde username.
        // Necesario tanto para resolver el rol como para auditoria.
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario autenticado no encontrado: " + username));

        // Fichajes en fechas futuras no permitidos para ningún rol.
        if (request.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "No se pueden registrar fichajes en fechas futuras");
        }

        // ENCARGADO puede gestionar hoy y futuro, no el pasado.
        // ADMIN puede crear fichajes para cualquier fecha sin restriccion.
        if (usuario.getRol() == Rol.ENCARGADO
                && request.getFecha().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "El ENCARGADO solo puede gestionar registros del dia actual y fechas futuras");
        }

        // Verificar unicidad empleado+fecha antes de persistir
        // 409 Conflict si ya existe fichaje ese día para ese empleado.
        // Usamos findByEmpleadoIdAndFecha().isPresent() porque ese método
        // ya existe en el repositorio (no necesitamos existsBy adicional).
        if (fichajeRepository.findByEmpleadoIdAndFecha(
                request.getEmpleadoId(), request.getFecha()).isPresent()) {
            throw new ConflictException(
                    "Ya existe un fichaje para el empleado " + request.getEmpleadoId()
                    + " en la fecha " + request.getFecha());
        }

        // Construir entidad Fichaje con los datos del request
        Fichaje fichaje = new Fichaje();
        fichaje.setEmpleado(empleado);
        fichaje.setFecha(request.getFecha());
        fichaje.setTipo(request.getTipo());
        fichaje.setHoraEntrada(request.getHoraEntrada());   // nullable: ausencias sin horas
        fichaje.setHoraSalida(request.getHoraSalida());     // nullable: jornada no terminada
        fichaje.setObservaciones(request.getObservaciones());
        fichaje.setUsuario(usuario);                         // auditoría: quién creó el fichaje (@ManyToOne)
        fichaje.setTotalPausasMinutos(0);                    // sin pausas al crear manualmente

        // Calcular jornadaEfectivaMinutos según el tipo de fichaje
        if (request.getHoraEntrada() != null && request.getHoraSalida() != null) {
            // Jornada normal con horas: diferencia entrada-salida menos pausas
            long minutosBrutos = ChronoUnit.MINUTES.between(
                    request.getHoraEntrada(), request.getHoraSalida());
            int jornadaEfectiva = (int) Math.ceil((double) minutosBrutos);
            fichaje.setJornadaEfectivaMinutos(jornadaEfectiva);
        } else if (request.getTipo() == TipoFichaje.BAJA_MEDICA
                || request.getTipo() == TipoFichaje.PERMISO_RETRIBUIDO) {
            // Ausencias retribuidas: el empleado no pierde su cuota diaria.
            // Es como si hubiera trabajado su jornada completa.
            fichaje.setJornadaEfectivaMinutos(empleado.getJornadaDiariaMinutos());
        } else {
            // Resto de ausencias (VACACIONES, FESTIVO, DIA_LIBRE, etc.) → 0
            fichaje.setJornadaEfectivaMinutos(0);
        }

        // Persistir y convertir a DTO de respuesta
        Fichaje guardado = fichajeRepository.save(fichaje);
        return toFichajeResponse(guardado, empleado);
    }

    // ---------------------------------------------------------------
    // E23 — PATCH /api/v1/fichajes/{id}
    // ---------------------------------------------------------------

    /**
     * Modifica un fichaje existente.
     *
     * Campos modificables: horaEntrada, horaSalida, tipo, observaciones.
     * Todos son opcionales en el request — solo se actualizan los que
     * llegan con valor no nulo (patrón PATCH del proyecto).
     *
     * Observaciones obligatorias (RNF-L02): si llegan null o vacías → 400.
     *
     * Restriccion de fecha: ENCARGADO solo puede modificar fichajes del dia
     * actual y fechas futuras. La fecha a validar es la del fichaje existente
     * en BD, no un campo del request. Si es ENCARGADO y el fichaje es de
     * fecha pasada → HTTP 400. ADMIN puede modificar fichajes de cualquier fecha.
     *
     * Recálculo de jornadaEfectivaMinutos:
     *   Si tras el PATCH el fichaje tiene horaEntrada y horaSalida,
     *   se recalcula usando totalPausasMinutos ya almacenado en BD.
     *   E23 no toca las pausas — solo recalcula jornada con lo que hay.
     *
     * @param id       ID del fichaje a modificar
     * @param request  campos a modificar (observaciones obligatorio)
     * @param username username del usuario autenticado (de authentication.getName())
     * @return FichajeResponse con el fichaje actualizado
     */
    @Transactional
    public FichajeResponse actualizar(Long id, FichajePatchRequest request, String username) {

        // Validación de observaciones obligatorias (RNF-L02)
        if (request.getObservaciones() == null || request.getObservaciones().isBlank()) {
            throw new IllegalArgumentException(
                    "Las observaciones son obligatorias al modificar un fichaje (RNF-L02)");
        }

        // Cargar fichaje — 404 si no existe
        Fichaje fichaje = fichajeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Fichaje no encontrado con id: " + id));

        // Resolver usuario autenticado para aplicar la restriccion de fecha del ENCARGADO
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario autenticado no encontrado: " + username));

        // Fichajes en fechas futuras no modificables para ningún rol.
        if (fichaje.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "No se pueden modificar fichajes en fechas futuras");
        }

        // ENCARGADO puede modificar hoy y futuro, no el pasado.
        // La fecha a validar es la del fichaje cargado de BD, no del request.
        // ADMIN puede modificar fichajes de cualquier fecha sin restriccion.
        if (usuario.getRol() == Rol.ENCARGADO
                && fichaje.getFecha().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "El ENCARGADO solo puede gestionar registros del dia actual y fechas futuras");
        }

        // Aplicar solo los campos que llegan con valor (patrón PATCH)
        // Si el campo es null en el request, el valor actual en BD se mantiene
        if (request.getTipo() != null) {
            fichaje.setTipo(request.getTipo());
        }
        if (request.getHoraEntrada() != null) {
            fichaje.setHoraEntrada(request.getHoraEntrada());
        }
        if (request.getHoraSalida() != null) {
            fichaje.setHoraSalida(request.getHoraSalida());
        }

        // Actualizar observaciones (ya validadas como no vacías arriba)
        fichaje.setObservaciones(request.getObservaciones());

        // Recalcular jornadaEfectivaMinutos si el fichaje tiene entrada y salida
        // Usamos totalPausasMinutos ya almacenado en BD — E23 no toca pausas
        if (fichaje.getHoraEntrada() != null && fichaje.getHoraSalida() != null) {
            long minutosBrutos = ChronoUnit.MINUTES.between(
                    fichaje.getHoraEntrada(), fichaje.getHoraSalida());
            int jornadaEfectiva = (int) Math.ceil(
                    (double)(minutosBrutos - fichaje.getTotalPausasMinutos()));
            fichaje.setJornadaEfectivaMinutos(jornadaEfectiva);
        }

        // Persistir cambios y devolver respuesta
        Fichaje actualizado = fichajeRepository.save(fichaje);
        return toFichajeResponse(actualizado, actualizado.getEmpleado());
    }

    // ---------------------------------------------------------------
    // E24 — GET /api/v1/fichajes
    // ---------------------------------------------------------------

    /**
     * Lista fichajes con filtros opcionales y combinables.
     *
     * Filtros disponibles (todos opcionales y combinables):
     *   - empleadoId → filtra por empleado concreto (RF-19)
     *   - desde/hasta → rango de fechas inclusivo en ambos extremos (RF-20)
     *   - tipo → filtra por tipo de jornada
     *
     * Sin filtros → devuelve todos los fichajes de todos los empleados.
     *
     * @param empleadoId filtro opcional por ID de empleado
     * @param desde      filtro opcional fecha inicio del rango
     * @param hasta      filtro opcional fecha fin del rango
     * @param tipo       filtro opcional por tipo de fichaje
     * @return lista de FichajeResponse (puede ser vacía)
     */
    @Transactional(readOnly = true)
    public List<FichajeResponse> listar(Long empleadoId, LocalDate desde,
                                        LocalDate hasta, TipoFichaje tipo) {
        return fichajeRepository
                .findByFiltros(empleadoId, desde, hasta, tipo)
                .stream()
                .map(f -> toFichajeResponse(f, f.getEmpleado()))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // E25 — GET /api/v1/fichajes/incompletos
    // ---------------------------------------------------------------

    /**
     * Lista fichajes con hora_entrada registrada pero sin hora_salida (RF-21).
     *
     * Útil para que el encargado detecte al final del día qué empleados
     * han olvidado fichar la salida.
     *
     * Si no se proporciona fecha, usa hoy (LocalDate.now()).
     *
     * @param fecha fecha a consultar (puede ser null → usa hoy)
     * @return lista de FichajeResponse donde horaSalida = null
     */
    @Transactional(readOnly = true)
    public List<FichajeResponse> listarIncompletos(LocalDate fecha) {

        // Si no se proporciona fecha, usar hoy
        LocalDate fechaConsulta = (fecha != null) ? fecha : LocalDate.now();

        return fichajeRepository
                .findByFechaAndHoraSalidaIsNull(fechaConsulta)
                .stream()
                .map(f -> toFichajeResponse(f, f.getEmpleado()))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // E26 — GET /api/v1/fichajes/me
    // ---------------------------------------------------------------

    /**
     * Lista los fichajes del empleado autenticado (RF-51).
     *
     * Recibe el username del controller (authentication.getName()) y
     * resuelve el empleadoId a partir de él. Mismo patrón que
     * EmpleadoService.obtenerMiPerfil().
     *
     * Spring Security garantiza que el rol EMPLEADO solo puede llegar
     * aquí con su propio token — no puede ver fichajes ajenos.
     *
     * Filtros opcionales: desde, hasta, tipo — misma lógica que E24.
     *
     * @param username username del empleado autenticado (de authentication.getName())
     * @param desde    filtro opcional fecha inicio
     * @param hasta    filtro opcional fecha fin
     * @param tipo     filtro opcional por tipo
     * @return lista de FichajeResponse del empleado autenticado
     */
    @Transactional(readOnly = true)
    public List<FichajeResponse> listarPropios(String username, LocalDate desde,
                                               LocalDate hasta, TipoFichaje tipo) {

        // Resolver username → usuario → empleado
        // Mismo patrón que obtenerMiPerfil() en EmpleadoService
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario autenticado no encontrado: " + username));

        Empleado empleado = empleadoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "El usuario autenticado no tiene perfil de empleado"));

        // Listar fichajes del empleado con los filtros opcionales
        return fichajeRepository
                .findByFiltros(empleado.getId(), desde, hasta, tipo)
                .stream()
                .map(f -> toFichajeResponse(f, f.getEmpleado()))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Método auxiliar de conversión entidad → DTO
    // ---------------------------------------------------------------

    /**
     * Convierte una entidad Fichaje en FichajeResponse.
     *
     * Separado en método privado para evitar duplicación de código.
     * El parámetro empleado se pasa explícitamente para evitar lazy loading
     * fuera de transacción cuando ya fue cargado previamente en el mismo método.
     *
     * @param fichaje  entidad a convertir
     * @param empleado entidad empleado ya cargada (evita consulta adicional)
     * @return FichajeResponse listo para serializar como JSON
     */
    private FichajeResponse toFichajeResponse(Fichaje fichaje, Empleado empleado) {
        // FichajeResponse usa @AllArgsConstructor sin @Builder.
        // Orden de campos según declaración en FichajeResponse.java:
        // id, empleadoId, fecha, tipo, horaEntrada, horaSalida,
        // totalPausasMinutos, jornadaEfectivaMinutos, usuarioId, observaciones, fechaCreacion
        String nombreCompleto = empleado.getNombre() + " " + empleado.getApellido1()
                + (empleado.getApellido2() != null ? " " + empleado.getApellido2() : "");

        return new FichajeResponse(
                fichaje.getId(),
                empleado.getId(),
                fichaje.getFecha(),
                fichaje.getTipo(),
                fichaje.getHoraEntrada(),
                fichaje.getHoraSalida(),
                fichaje.getTotalPausasMinutos(),
                fichaje.getJornadaEfectivaMinutos(),
                fichaje.getUsuario() != null ? fichaje.getUsuario().getId() : null,
                fichaje.getObservaciones(),
                fichaje.getFechaCreacion(),
                nombreCompleto
        );
    }
}
