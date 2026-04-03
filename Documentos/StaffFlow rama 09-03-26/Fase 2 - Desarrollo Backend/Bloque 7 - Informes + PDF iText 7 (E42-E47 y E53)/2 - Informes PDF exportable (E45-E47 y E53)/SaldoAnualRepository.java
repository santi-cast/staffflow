package com.staffflow.domain.repository;

import com.staffflow.domain.entity.SaldoAnual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para los saldos anuales de vacaciones, asuntos propios y horas.
 *
 * <p>Restriccion de tabla: UNIQUE(empleado_id, anio). Existe exactamente
 * un registro de saldo por empleado y año. SaldoService usa findByEmpleadoIdAndAnio
 * con un patron findOrCreate: si no existe el registro para el año en curso
 * lo crea con los valores iniciales antes de actualizar.</p>
 *
 * <p>Los saldos los actualiza el proceso nocturno @Scheduled (ProcesoCierreDiario,
 * Tarea 4 del Bloque 6) y manualmente E40 recalcular (solo ADMIN).
 * El cierre anual (boton manual ADMIN, decision n12) crea el registro
 * del año siguiente con los pendientes arrastrados.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.entity.SaldoAnual
 */
@Repository
public interface SaldoAnualRepository extends JpaRepository<SaldoAnual, Long> {

    /**
     * Busca el saldo anual de un empleado para un año concreto.
     *
     * <p>Es la consulta principal de SaldoService. Se usa en:
     * E38 (GET /saldos/{empleadoId}) para devolver el saldo de un empleado,
     * E41 (GET /saldos/me) para devolver el saldo del empleado autenticado,
     * y E40 (POST /saldos/{empleadoId}/recalcular) en el patron findOrCreate
     * para obtener el registro existente o crear uno nuevo si no existe.</p>
     *
     * <p>Si devuelve Optional.empty() en E38 y E41 se lanza
     * IllegalStateException que GlobalExceptionHandler mapea a HTTP 404.
     * En E40 se usa orElseGet() para crear el registro inicial.</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año del saldo (ej: 2026)
     * @return Optional con el saldo, vacio si no existe para ese año
     */
    Optional<SaldoAnual> findByEmpleadoIdAndAnio(Long empleadoId, Integer anio);

    /**
     * Devuelve el historico completo de saldos de un empleado.
     *
     * <p>Lo usa SaldoService para mostrar el historial de años anteriores
     * si el cliente lo solicita. Tambien lo usa CierreAnual para obtener
     * el saldo del año que cierra y calcular los pendientes a arrastrar
     * al año nuevo (decision n12).</p>
     *
     * @param empleadoId id del empleado
     * @return lista de saldos anuales del empleado (puede ser vacia)
     */
    List<SaldoAnual> findByEmpleadoId(Long empleadoId);

    /**
     * Devuelve todos los saldos anuales de un año concreto.
     *
     * <p>Usado por SaldoService.listarTodos() (E39 GET /saldos) para
     * devolver el saldo de todos los empleados con registro en ese año.
     * No filtra por activo porque un empleado inactivo puede tener
     * saldo historico valido consultable por ADMIN y ENCARGADO.</p>
     *
     * <p>Spring Data JPA genera la implementacion SQL automaticamente
     * a partir del nombre del metodo. No requiere @Query explicita.</p>
     *
     * @param anio año a consultar (ej: 2026)
     * @return lista de saldos de ese año (puede ser vacia)
     */
    List<SaldoAnual> findByAnio(Integer anio);
}
