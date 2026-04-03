package com.staffflow.domain.repository;

import com.staffflow.domain.entity.PlanificacionAusencia;
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

    // ------------------------------------------------------------------
    // E30 — validación UNIQUE antes de insertar
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // E33 — listar con 4 filtros opcionales
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // E34 — ausencias propias del empleado autenticado (/me)
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // ProcesoDiario — usado en Bloque 6 Tarea 4
    // ------------------------------------------------------------------

    /**
     * Busca ausencias pendientes de procesar cuya fecha sea igual o
     * anterior a la fecha indicada. Usado por ProcesoDiario @Scheduled
     * a las 00:01 para materializar las ausencias del día.
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
}
