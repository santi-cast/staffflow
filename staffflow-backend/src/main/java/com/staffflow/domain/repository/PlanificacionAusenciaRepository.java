package com.staffflow.domain.repository;

import com.staffflow.domain.entity.PlanificacionAusencia;
import com.staffflow.domain.enums.TipoAusencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio de planificación de ausencias.
 *
 * <p>Cubre las consultas necesarias para E30-E34 (Grupo 7).
 * El método findByFiltros soporta los 4 filtros opcionales de E33
 * con el patrón :param IS NULL OR campo = :param para que la misma
 * query funcione con cualquier combinación de filtros.</p>
 *
 * <p>Nota sobre empleadoId nullable: los festivos globales tienen
 * empleado_id = NULL en BD. La query de findByFiltros incluye
 * ausencias con empleado_id = NULL cuando empleadoId no se pasa,
 * igual que exige el contrato de E33.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.service.AusenciaService
 */
@Repository
public interface PlanificacionAusenciaRepository extends JpaRepository<PlanificacionAusencia, Long> {

    // E30 — validación UNIQUE antes de insertar

    /**
     * Comprueba si ya existe una ausencia para un empleado en una fecha.
     * Usado en E30 para lanzar ConflictException antes de que explote
     * DataIntegrityViolationException por la UNIQUE(empleado_id, fecha).
     *
     * @param empleadoId id del empleado (puede ser null para festivos globales)
     * @param fecha      fecha de la ausencia
     * @return true si ya existe un registro con ese empleado y fecha
     */
    boolean existsByEmpleadoIdAndFecha(Long empleadoId, LocalDate fecha);

    // E30b — crearRango: detectar conflictos en el rango solicitado

    /**
     * Devuelve las ausencias de un empleado en un rango de fechas.
     * Usado en crearRango() para detectar conflictos antes de insertar.
     *
     * @param empleadoId id del empleado
     * @param desde      fecha inicio del rango (inclusive)
     * @param hasta      fecha fin del rango (inclusive)
     * @return ausencias del empleado en el rango
     */
    List<PlanificacionAusencia> findByEmpleadoIdAndFechaBetween(
            Long empleadoId, LocalDate desde, LocalDate hasta);

    // E33 — listar con 4 filtros opcionales

    /**
     * Lista ausencias con filtros opcionales y combinables (E33).
     *
     * <p>Patrón :param IS NULL OR campo = :param — si el parámetro llega
     * null la condición es siempre verdadera y no filtra por ese campo.
     * JOIN FETCH empleado para evitar N+1 al acceder al nombre en el DTO.</p>
     *
     * <p>Los festivos globales (empleado IS NULL) solo aparecen cuando
     * empleadoId no se filtra, igual que exige E33.</p>
     *
     * @param empleadoId filtro por empleado (null = sin filtro)
     * @param desde      fecha inicio del rango (null = sin límite inferior)
     * @param hasta      fecha fin del rango (null = sin límite superior)
     * @param procesado  filtro por estado procesado (null = ambos)
     * @return lista de ausencias que cumplen los filtros
     */
    @Query("""
            SELECT a FROM PlanificacionAusencia a
            LEFT JOIN FETCH a.empleado
            WHERE (:empleadoId IS NULL OR
                   (a.empleado IS NOT NULL AND a.empleado.id = :empleadoId))
              AND (:desde    IS NULL OR a.fecha >= :desde)
              AND (:hasta    IS NULL OR a.fecha <= :hasta)
              AND (:procesado IS NULL OR a.procesado = :procesado)
            ORDER BY a.fecha ASC
            """)
    List<PlanificacionAusencia> findByFiltros(
            @Param("empleadoId") Long empleadoId,
            @Param("desde")      LocalDate desde,
            @Param("hasta")      LocalDate hasta,
            @Param("procesado")  Boolean procesado);

    // E34 — ausencias propias del empleado autenticado (/me)

    /**
     * Lista las ausencias de un empleado concreto en un rango de fechas
     * opcional (E34 — /me).
     *
     * <p>A diferencia de findByFiltros, aquí empleadoId es siempre
     * obligatorio (viene del JWT del empleado autenticado).</p>
     *
     * @param empleadoId id del empleado autenticado (obligatorio)
     * @param desde      fecha inicio del rango (null = sin límite inferior)
     * @param hasta      fecha fin del rango (null = sin límite superior)
     * @return ausencias del empleado en el rango indicado
     */
    @Query("""
            SELECT a FROM PlanificacionAusencia a
            WHERE a.empleado.id = :empleadoId
              AND (:desde IS NULL OR a.fecha >= :desde)
              AND (:hasta IS NULL OR a.fecha <= :hasta)
            ORDER BY a.fecha ASC
            """)
    List<PlanificacionAusencia> findByEmpleadoIdAndRango(
            @Param("empleadoId") Long empleadoId,
            @Param("desde")      LocalDate desde,
            @Param("hasta")      LocalDate hasta);

    // Usado por ProcesoCierreDiario

    /**
     * Busca ausencias pendientes de procesar cuya fecha sea igual o
     * anterior a la fecha indicada. Usado por ProcesoCierreDiario @Scheduled
     * a las 23:55 para materializar las ausencias del día.
     *
     * @param fecha fecha límite (normalmente LocalDate.now())
     * @return ausencias con procesado=false y fecha <= fecha
     */
    @Query("""
            SELECT a FROM PlanificacionAusencia a
            LEFT JOIN FETCH a.empleado
            WHERE a.procesado = false
              AND a.fecha <= :fecha
            ORDER BY a.fecha ASC
            """)
    List<PlanificacionAusencia> findPendientesByFechaLessThanEqual(
            @Param("fecha") LocalDate fecha);

    // Método para PresenciaService (E35-E37)

    /**
     * Devuelve todas las ausencias planificadas de una fecha concreta
     * que aún no han sido procesadas por el proceso diario.
     *
     * Usado por PresenciaService para determinar qué empleados tienen
     * estado AUSENCIA_PLANIFICADA al construir el parte diario (E35-E37).
     * Una ausencia planificada con procesado=false significa que el proceso
     * nocturno (@Scheduled 23:55) aún no la ha convertido en fichaje,
     * por lo que el empleado no tendrá fichaje para ese día todavía.
     *
     * Incluye ausencias globales (empleado IS NULL — festivos nacionales/locales)
     * porque también determinan el estado de los empleados ese día.
     * El service comprueba si hay un festivo global antes de clasificar
     * a empleados sin fichaje como SIN_JUSTIFICAR.
     *
     * LEFT JOIN FETCH es necesario porque empleado puede ser null
     * en el caso de festivos globales (RF-26).
     *
     * @param fecha fecha a consultar (normalmente hoy)
     * @return lista de ausencias planificadas pendientes para esa fecha,
     *         incluyendo festivos globales (empleado = null)
     */
    @Query("SELECT a FROM PlanificacionAusencia a LEFT JOIN FETCH a.empleado " +
           "WHERE a.fecha = :fecha AND a.procesado = false")
    List<PlanificacionAusencia> findByFechaAndProcesadoFalse(@Param("fecha") LocalDate fecha);

    // E64 — conteo de planificadas por tipo y rango

    /**
     * Cuenta ausencias planificadas (procesado=false) de un empleado
     * para un tipo concreto en un rango de fechas.
     *
     * Usado por AusenciaService.getPlanificacionVacAp() para calcular
     * cuántas vacaciones o asuntos propios ya están planificados ese año.
     *
     * @param empleadoId id del empleado
     * @param tipo       TipoAusencia.VACACIONES o TipoAusencia.ASUNTO_PROPIO
     * @param desde      primer día del año (1 enero)
     * @param hasta      último día del año (31 diciembre)
     * @return número de ausencias planificadas del tipo indicado ese año
     */
    @Query("""
            SELECT COUNT(a) FROM PlanificacionAusencia a
            WHERE a.empleado.id = :empleadoId
              AND a.procesado = false
              AND a.tipoAusencia = :tipo
              AND a.fecha >= :desde
              AND a.fecha <= :hasta
            """)
    int countPlanificadasByEmpleadoAndTipoAndRango(
            @Param("empleadoId") Long empleadoId,
            @Param("tipo")       TipoAusencia tipo,
            @Param("desde")      LocalDate desde,
            @Param("hasta")      LocalDate hasta);

}
