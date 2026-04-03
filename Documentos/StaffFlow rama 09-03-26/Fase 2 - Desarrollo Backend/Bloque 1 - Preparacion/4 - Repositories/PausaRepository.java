package com.staffflow.domain.repository;

import com.staffflow.domain.entity.Pausa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para las pausas dentro de la jornada laboral.
 *
 * <p>Las pausas son inmutables igual que los fichajes (RNF-L01):
 * no existe DELETE ni modificación de campos de registro. Solo se
 * permite PATCH en observaciones (E29).</p>
 *
 * <p>Una pausa con horaFin=null es una pausa activa en curso. Este
 * estado es imprescindible para los endpoints de terminal E50 y E51,
 * que detectan si hay pausa activa antes de registrar inicio o fin.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.entity.Pausa
 */
@Repository
public interface PausaRepository extends JpaRepository<Pausa, Long> {

    /**
     * Devuelve todas las pausas de un empleado.
     *
     * <p>Lo usa PausaService en E28 (GET /pausas) cuando el filtro
     * incluye solo empleadoId sin filtro de fecha.</p>
     *
     * @param empleadoId id del empleado
     * @return lista de todas las pausas del empleado
     */
    List<Pausa> findByEmpleadoId(Long empleadoId);

    /**
     * Devuelve todas las pausas de un empleado en una fecha concreta.
     *
     * <p>Lo usan PausaService y FichajeService para calcular
     * totalPausasMinutos al cerrar la jornada. También lo usa
     * PresenciaService para incluir el detalle de pausas del día
     * en ParteDiarioResponse.</p>
     *
     * @param empleadoId id del empleado
     * @param fecha      fecha de las pausas
     * @return lista de pausas del empleado en esa fecha
     */
    List<Pausa> findByEmpleadoIdAndFecha(Long empleadoId, LocalDate fecha);

    /**
     * Busca la pausa activa de un empleado en una fecha concreta.
     *
     * <p>Una pausa activa es aquella con horaFin=null. Lo usa TerminalService
     * en E49 (salida) para verificar que no hay pausa activa antes de
     * registrar la salida, y en E50 (inicio pausa) para evitar que
     * un empleado abra dos pausas simultáneas. Si existe pausa activa
     * → HTTP 409 Conflict.</p>
     *
     * @param empleadoId id del empleado
     * @param fecha      fecha de la pausa
     * @return Optional con la pausa activa, vacío si no hay ninguna
     */
    Optional<Pausa> findByEmpleadoIdAndFechaAndHoraFinIsNull(Long empleadoId, LocalDate fecha);
}