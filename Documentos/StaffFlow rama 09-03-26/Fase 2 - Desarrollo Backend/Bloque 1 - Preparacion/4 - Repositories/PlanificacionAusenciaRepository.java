package com.staffflow.domain.repository;

import com.staffflow.domain.entity.PlanificacionAusencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para las ausencias planificadas.
 *
 * <p>Una ausencia planificada con procesado=false está pendiente de que
 * ProcesoDiario (@Scheduled 00:01) la convierta en fichaje. Una vez
 * procesada (procesado=true) no puede modificarse ni eliminarse.</p>
 *
 * <p>Restricción de tabla: UNIQUE(empleado_id, fecha). Una violación
 * lanza DataIntegrityViolationException → HTTP 409 Conflict.</p>
 *
 * <p>Si empleado_id es NULL el registro es un festivo global que
 * ProcesoDiario aplica a todos los empleados activos ese día.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.entity.PlanificacionAusencia
 */
@Repository
public interface PlanificacionAusenciaRepository extends JpaRepository<PlanificacionAusencia, Long> {

    /**
     * Devuelve todas las ausencias planificadas de un empleado.
     *
     * <p>Lo usa AusenciaService en E31 (GET /ausencias) cuando el filtro
     * incluye empleadoId. El EMPLEADO solo puede ver las suyas propias;
     * ADMIN y ENCARGADO pueden consultar cualquier empleado (decisión nº14).</p>
     *
     * @param empleadoId id del empleado
     * @return lista de ausencias del empleado ordenadas por fecha
     */
    List<PlanificacionAusencia> findByEmpleadoId(Long empleadoId);

    /**
     * Devuelve todas las ausencias pendientes de procesar para una fecha concreta.
     *
     * <p>Es la consulta principal de ProcesoDiario (@Scheduled 00:01).
     * Obtiene los registros con fecha=HOY y procesado=false para convertirlos
     * en fichajes. Incluye festivos globales (empleado_id IS NULL).</p>
     *
     * @param fecha fecha a procesar (normalmente LocalDate.now())
     * @return lista de ausencias pendientes para esa fecha
     */
    List<PlanificacionAusencia> findByFechaAndProcesadoFalse(LocalDate fecha);

    /**
     * Busca la ausencia planificada de un empleado en una fecha concreta.
     *
     * <p>Lo usa AusenciaService antes de crear una nueva ausencia (E30)
     * para validar el UNIQUE(empleado_id, fecha) en la capa de servicio
     * antes de llegar a la BD, devolviendo un mensaje de error claro
     * en lugar de dejar que explote DataIntegrityViolationException.</p>
     *
     * @param empleadoId id del empleado
     * @param fecha      fecha de la ausencia
     * @return Optional con la ausencia, vacío si no existe
     */
    Optional<PlanificacionAusencia> findByEmpleadoIdAndFecha(Long empleadoId, LocalDate fecha);
}