package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.PlanificacionAusencia;
import com.staffflow.domain.entity.SaldoAnual;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.enums.TipoAusencia;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.AusenciaPatchRequest;
import com.staffflow.dto.request.AusenciaRangoRequest;
import com.staffflow.dto.request.AusenciaRequest;
import com.staffflow.dto.response.AusenciaResponse;
import com.staffflow.dto.response.PlanificacionVacApResponse;
import com.staffflow.exception.ConflictException;
import com.staffflow.exception.NotFoundException;
import com.staffflow.exception.RangoConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio de planificación de ausencias.
 *
 * <p>Cubre los endpoints E30-E34, E63 y E64 (Grupo 7). Gestiona las
 * ausencias planificadas con antelación. El único DELETE real del sistema
 * (E32) solo opera sobre ausencias con procesado=false.</p>
 *
 * <p>Si empleadoId es null en la creación, la ausencia es un festivo global
 * que ProcesoCierreDiario aplicará a todos los empleados activos ese día (RF-26).</p>
 *
 * <p>Una ausencia con procesado=true ya fue convertida en fichaje por
 * ProcesoCierreDiario y no puede modificarse ni eliminarse.</p>
 *
 * <p>ENCARGADO solo puede gestionar ausencias del dia actual y
 * fechas futuras. La restriccion se aplica en crear() (E30) y actualizar() (E31).
 * E32 no necesita restriccion de fecha: se protege solo con procesado=false.
 * En condiciones normales, si la fecha ya paso, ProcesoCierreDiario ya
 * habra marcado procesado=true y E32 devuelve 409 automaticamente.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.PlanificacionAusenciaRepository
 */
@Service
@RequiredArgsConstructor
public class AusenciaService {

    private final PlanificacionAusenciaRepository ausenciaRepository;
    private final EmpleadoRepository              empleadoRepository;
    private final UsuarioRepository               usuarioRepository;
    private final SaldoAnualRepository            saldoAnualRepository;

    // ------------------------------------------------------------------
    // E30 — POST /api/v1/ausencias
    // RF-25 ausencia individual | RF-26 festivo global (empleadoId null)
    // ------------------------------------------------------------------

    /**
     * Planifica una ausencia futura para un empleado (E30).
     *
     * <p>Si empleadoId es null crea un festivo global (RF-26).
     * Valida el UNIQUE(empleado_id, fecha) antes de insertar para
     * devolver HTTP 409 con mensaje claro en lugar de dejar explotar
     * DataIntegrityViolationException.</p>
     *
     * <p>Restriccion: ENCARGADO solo puede planificar ausencias
     * para el dia actual y fechas futuras. ADMIN puede planificar para
     * cualquier fecha. La validacion se aplica sobre request.getFecha().</p>
     *
     * @param request   datos de la ausencia a planificar
     * @param username  username del usuario autenticado (para auditoría)
     * @return ausencia planificada creada con procesado=false
     */
    @Transactional
    public AusenciaResponse crear(AusenciaRequest request, String username) {

        // --- Resolver usuario autenticado para restriccion de rol y auditoria ---
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(
                        "Usuario no encontrado: " + username));

        // ENCARGADO puede gestionar hoy y futuro, no el pasado.
        // ADMIN puede planificar ausencias para cualquier fecha sin restriccion.
        // Ausencias futuras están permitidas para ambos roles (a diferencia de fichajes/pausas).
        if (usuario.getRol() == Rol.ENCARGADO
                && request.getFecha().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "El ENCARGADO solo puede gestionar registros del dia actual y fechas futuras");
        }

        // --- Validación UNIQUE(empleado_id, fecha) ---
        if (ausenciaRepository.existsByEmpleadoIdAndFecha(
                request.getEmpleadoId(), request.getFecha())) {
            throw new ConflictException(
                    "Ya existe una ausencia planificada para ese empleado en la fecha "
                    + request.getFecha());
        }

        // --- Resolver entidad Empleado (null = festivo global RF-26) ---
        Empleado empleado = null;
        if (request.getEmpleadoId() != null) {
            empleado = empleadoRepository.findById(request.getEmpleadoId())
                    .orElseThrow(() -> new NotFoundException(
                            "Empleado no encontrado con id " + request.getEmpleadoId()));
        }

        // --- Construir y persistir la entidad ---
        PlanificacionAusencia ausencia = new PlanificacionAusencia();
        ausencia.setEmpleado(empleado);
        ausencia.setFecha(request.getFecha());
        ausencia.setTipoAusencia(request.getTipoAusencia());
        ausencia.setProcesado(false);          // siempre false al crear
        ausencia.setUsuario(usuario);
        ausencia.setObservaciones(request.getObservaciones());

        PlanificacionAusencia guardada = ausenciaRepository.save(ausencia);
        return toAusenciaResponse(guardada);
    }

    // ------------------------------------------------------------------
    // E63 — POST /api/v1/ausencias/rango
    // Conveniencia: crea un registro por cada día del rango [desde, hasta]
    // ------------------------------------------------------------------

    /**
     * Planifica un rango de ausencias consecutivas para un empleado (E63).
     *
     * <p>Crea un registro de PlanificacionAusencia por cada día del rango
     * [fechaDesde, fechaHasta] inclusive. El modelo de datos no cambia
     * respecto a E30 — sigue siendo un registro por día.</p>
     *
     * <p>Detección de conflictos: si algún día del rango ya tiene una
     * ausencia con procesado=false y sobrescribir=false, lanza
     * RangoConflictException (HTTP 409) con la lista de fechas. Si
     * sobrescribir=true, elimina los registros conflictivos primero.</p>
     *
     * <p>Si algún día tiene procesado=true (ya materializado en fichaje),
     * devuelve HTTP 400 — no se puede sobrescribir un fichaje generado.</p>
     *
     * <p>Restriccion: ENCARGADO solo puede planificar desde hoy
     * en adelante. ADMIN sin restricción de fecha.</p>
     *
     * @param request   datos del rango a planificar
     * @param username  username del usuario autenticado
     * @return lista de ausencias creadas
     */
    @Transactional
    public List<AusenciaResponse> crearRango(AusenciaRangoRequest request, String username) {

        // --- Resolver usuario autenticado para restriccion de rol y auditoría ---
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(
                        "Usuario no encontrado: " + username));

        // --- Validar que fechaDesde <= fechaHasta ---
        if (request.getFechaDesde().isAfter(request.getFechaHasta())) {
            throw new IllegalArgumentException(
                    "fechaDesde no puede ser posterior a fechaHasta");
        }

        // ENCARGADO solo puede gestionar hoy y fechas futuras
        if (usuario.getRol() == Rol.ENCARGADO
                && request.getFechaDesde().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "El ENCARGADO solo puede gestionar registros del dia actual y fechas futuras");
        }

        // --- Detectar conflictos en el rango ---
        List<PlanificacionAusencia> existentes = ausenciaRepository
                .findByEmpleadoIdAndFechaBetween(
                        request.getEmpleadoId(),
                        request.getFechaDesde(),
                        request.getFechaHasta());

        // procesado=true: ya materializado en fichaje — no se puede sobrescribir
        List<LocalDate> procesadas = existentes.stream()
                .filter(a -> Boolean.TRUE.equals(a.getProcesado()))
                .map(PlanificacionAusencia::getFecha)
                .collect(Collectors.toList());
        if (!procesadas.isEmpty()) {
            throw new IllegalArgumentException(
                    "Las siguientes fechas ya tienen un fichaje generado y no pueden modificarse: "
                    + procesadas);
        }

        // procesado=false: conflicto sobrescribible
        List<PlanificacionAusencia> conflictos = existentes.stream()
                .filter(a -> Boolean.FALSE.equals(a.getProcesado()))
                .collect(Collectors.toList());

        if (!conflictos.isEmpty() && !request.isSobrescribir()) {
            List<LocalDate> fechas = conflictos.stream()
                    .map(PlanificacionAusencia::getFecha)
                    .collect(Collectors.toList());
            throw new RangoConflictException(fechas);
        }

        // Sobrescribir: eliminar conflictos procesado=false antes de crear
        if (!conflictos.isEmpty()) {
            ausenciaRepository.deleteAll(conflictos);
        }

        // --- Resolver empleado (null = festivo global RF-26) ---
        Empleado empleado = null;
        if (request.getEmpleadoId() != null) {
            empleado = empleadoRepository.findById(request.getEmpleadoId())
                    .orElseThrow(() -> new NotFoundException(
                            "Empleado no encontrado con id " + request.getEmpleadoId()));
        }

        // --- Crear un registro por cada día del rango ---
        final Empleado empleadoFinal = empleado;
        List<AusenciaResponse> creadas = new ArrayList<>();
        LocalDate fecha = request.getFechaDesde();
        while (!fecha.isAfter(request.getFechaHasta())) {
            PlanificacionAusencia ausencia = new PlanificacionAusencia();
            ausencia.setEmpleado(empleadoFinal);
            ausencia.setFecha(fecha);
            ausencia.setTipoAusencia(request.getTipoAusencia());
            ausencia.setProcesado(false);
            ausencia.setUsuario(usuario);
            ausencia.setObservaciones(request.getObservaciones());
            creadas.add(toAusenciaResponse(ausenciaRepository.save(ausencia)));
            fecha = fecha.plusDays(1);
        }

        return creadas;
    }

    // ------------------------------------------------------------------
    // E31 — PATCH /api/v1/ausencias/{id}
    // RF-27: modificar ausencia planificada
    // ------------------------------------------------------------------

    /**
     * Modifica una ausencia planificada (E31).
     *
     * <p>Solo se puede modificar si procesado=false. Si procesado=true
     * devuelve HTTP 409: hay que modificar el fichaje generado (E23).</p>
     *
     * <p>Restriccion: ENCARGADO solo puede modificar ausencias del
     * dia actual y fechas futuras. La fecha a validar es ausencia.getFecha(),
     * obtenida tras cargar la entidad con findById. ADMIN sin restriccion de fecha.</p>
     *
     * @param id      id de la ausencia a modificar
     * @param request campos a actualizar (PATCH selectivo)
     * @param username username del usuario autenticado (para restriccion de rol)
     * @return ausencia actualizada
     */
    @Transactional
    public AusenciaResponse actualizar(Long id, AusenciaPatchRequest request, String username) {

        PlanificacionAusencia ausencia = ausenciaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Ausencia no encontrada con id " + id));

        // Resolver usuario autenticado para restriccion de rol
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(
                        "Usuario no encontrado: " + username));

        // ENCARGADO puede modificar hoy y futuro, no el pasado.
        // La fecha a validar es la de la ausencia cargada de BD, no del request.
        // ADMIN puede modificar ausencias de cualquier fecha sin restriccion.
        if (usuario.getRol() == Rol.ENCARGADO
                && ausencia.getFecha().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "El ENCARGADO solo puede gestionar registros del dia actual y fechas futuras");
        }

        // procesado=true → ya materializada en fichaje → no se puede modificar
        if (Boolean.TRUE.equals(ausencia.getProcesado())) {
            throw new ConflictException(
                    "La ausencia ya fue procesada. Modifica el fichaje generado mediante E23.");
        }

        // PATCH selectivo: solo actualiza campos no nulos
        if (request.getTipoAusencia() != null) {
            ausencia.setTipoAusencia(request.getTipoAusencia());
        }
        if (request.getObservaciones() != null) {
            ausencia.setObservaciones(request.getObservaciones());
        }

        return toAusenciaResponse(ausenciaRepository.save(ausencia));
    }

    // ------------------------------------------------------------------
    // E32 — DELETE /api/v1/ausencias/{id}
    // RF-28: único DELETE real del sistema
    // ------------------------------------------------------------------

    /**
     * Elimina una ausencia planificada (E32 — único DELETE real del sistema).
     *
     * <p>Solo funciona si procesado=false. Si procesado=true devuelve
     * HTTP 409: el fichaje generado es inmutable (RNF-L01).</p>
     *
     * <p>E32 no necesita restriccion de fecha: la validacion
     * procesado=false la cubre. En condiciones normales, si la fecha
     * ya paso, ProcesoCierreDiario ya habrá marcado procesado=true y
     * este metodo devuelve 409 automaticamente.</p>
     *
     * @param id id de la ausencia a eliminar
     */
    @Transactional
    public void eliminar(Long id) {

        PlanificacionAusencia ausencia = ausenciaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Ausencia no encontrada con id " + id));

        // procesado=true → fichaje ya generado e inmutable → HTTP 409
        if (Boolean.TRUE.equals(ausencia.getProcesado())) {
            throw new ConflictException(
                    "La ausencia ya fue procesada y tiene un fichaje asociado. "
                    + "No se puede eliminar (RNF-L01).");
        }

        ausenciaRepository.delete(ausencia);
    }

    // ------------------------------------------------------------------
    // E33 — GET /api/v1/ausencias
    // RF-29: listar con filtros opcionales
    // ------------------------------------------------------------------

    /**
     * Lista ausencias planificadas con filtros opcionales (E33).
     *
     * <p>Sin empleadoId devuelve las ausencias de todos los empleados.
     * Incluye festivos globales (empleadoId = null).</p>
     *
     * @param empleadoId filtro por empleado (nullable)
     * @param desde      fecha de inicio del rango (nullable)
     * @param hasta      fecha de fin del rango (nullable)
     * @param procesado  filtro por estado procesado (nullable)
     * @return lista de ausencias que cumplen los filtros
     */
    @Transactional(readOnly = true)
    public List<AusenciaResponse> listar(Long empleadoId, LocalDate desde,
                                         LocalDate hasta, Boolean procesado) {
        return ausenciaRepository
                .findByFiltros(empleadoId, desde, hasta, procesado)
                .stream()
                .map(this::toAusenciaResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // E34 — GET /api/v1/ausencias/me
    // RF-52: ausencias propias del empleado autenticado
    // ------------------------------------------------------------------

    /**
     * Lista las ausencias planificadas del empleado autenticado (E34 — /me).
     *
     * <p>Spring Security garantiza que el empleado solo ve las suyas propias.
     * Incluye vacaciones y permisos planificados futuros.</p>
     *
     * @param username username del empleado autenticado (extraído del JWT)
     * @param desde    fecha de inicio del rango (nullable)
     * @param hasta    fecha de fin del rango (nullable)
     * @return lista de ausencias propias del empleado
     */
    @Transactional(readOnly = true)
    public List<AusenciaResponse> listarMias(String username,
                                              LocalDate desde, LocalDate hasta) {

        // Resolver empleadoId a partir del username del JWT
        Empleado empleado = empleadoRepository.findByUsuarioUsername(username)
                .orElseThrow(() -> new NotFoundException(
                        "Perfil de empleado no encontrado para el usuario: " + username));

        return ausenciaRepository
                .findByEmpleadoIdAndRango(empleado.getId(), desde, hasta)
                .stream()
                .map(this::toAusenciaResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // E64 — GET /api/v1/ausencias/planificacion-vac-ap
    // Días pendientes de planificar para vacaciones y asuntos propios
    // ------------------------------------------------------------------

    /**
     * Calcula los días pendientes de planificar para vacaciones y asuntos
     * propios de un empleado en un año concreto.
     *
     * <p>Si no existe SaldoAnual para ese año, lo crea on-demand con
     * derechoAnio del empleado y pendientesAnterior=0. Esto permite
     * planificar el año siguiente antes del cierre anual. El flag
     * anioFuturoSinCierre=true avisa al cliente de que los pendientes
     * del año actual aún no están incluidos.</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año a consultar
     * @return desglose de disponibles, planificados y pendientes para vac y AP
     */
    @Transactional
    public PlanificacionVacApResponse getPlanificacionVacAp(Long empleadoId, int anio) {

        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new NotFoundException(
                        "Empleado no encontrado con id: " + empleadoId));

        Optional<SaldoAnual> existente = saldoAnualRepository
                .findByEmpleadoIdAndAnio(empleadoId, anio);
        boolean anioFuturoSinCierre = anio > LocalDate.now().getYear();

        SaldoAnual saldo = existente.orElseGet(() -> {
            SaldoAnual nuevo = new SaldoAnual();
            nuevo.setEmpleado(empleado);
            nuevo.setAnio(anio);
            nuevo.setDiasVacacionesDerechoAnio(empleado.getDiasVacacionesAnuales());
            nuevo.setDiasVacacionesPendientesAnioAnterior(0);
            nuevo.setDiasVacacionesConsumidos(0);
            nuevo.setDiasVacacionesDisponibles(empleado.getDiasVacacionesAnuales());
            nuevo.setDiasAsuntosPropiosDerechoAnio(empleado.getDiasAsuntosPropiosAnuales());
            nuevo.setDiasAsuntosPropiosPendientesAnterior(0);
            nuevo.setDiasAsuntosPropiosConsumidos(0);
            nuevo.setDiasAsuntosPropiosDisponibles(empleado.getDiasAsuntosPropiosAnuales());
            nuevo.setHorasAusenciaRetribuida(BigDecimal.ZERO);
            nuevo.setSaldoHoras(BigDecimal.ZERO);
            nuevo.setCalculadoHastaFecha(null);
            return saldoAnualRepository.save(nuevo);
        });

        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta  = LocalDate.of(anio, 12, 31);

        int planVac = ausenciaRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                empleadoId, TipoAusencia.VACACIONES, desde, hasta);
        int planAP  = ausenciaRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                empleadoId, TipoAusencia.ASUNTO_PROPIO, desde, hasta);

        int pendVac = Math.max(0, saldo.getDiasVacacionesDisponibles() - planVac);
        int pendAP  = Math.max(0, saldo.getDiasAsuntosPropiosDisponibles() - planAP);

        return new PlanificacionVacApResponse(
                new PlanificacionVacApResponse.VacAp(
                        saldo.getDiasVacacionesDisponibles(), planVac, pendVac),
                new PlanificacionVacApResponse.VacAp(
                        saldo.getDiasAsuntosPropiosDisponibles(), planAP, pendAP),
                anioFuturoSinCierre
        );
    }

    // ------------------------------------------------------------------
    // Mapeo entidad → DTO
    // ------------------------------------------------------------------

    /**
     * Convierte una entidad PlanificacionAusencia en su DTO response.
     *
     * <p>empleadoId puede ser null si la ausencia es un festivo global.
     * La entidad ya tiene el empleado cargado por JOIN FETCH en las queries,
     * así que no hay riesgo de LazyInitializationException.</p>
     *
     * @param ausencia entidad a convertir
     * @return DTO con los datos de la ausencia
     */
    private AusenciaResponse toAusenciaResponse(PlanificacionAusencia ausencia) {
        return new AusenciaResponse(
                ausencia.getId(),
                ausencia.getEmpleado() != null ? ausencia.getEmpleado().getId() : null,
                ausencia.getFecha(),
                ausencia.getTipoAusencia(),
                ausencia.getProcesado(),
                ausencia.getUsuario() != null ? ausencia.getUsuario().getId() : null,
                ausencia.getObservaciones(),
                ausencia.getFechaCreacion()
        );
    }
}
