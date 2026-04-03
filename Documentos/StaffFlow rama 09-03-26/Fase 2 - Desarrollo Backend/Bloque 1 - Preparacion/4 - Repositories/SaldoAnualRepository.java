package com.staffflow.domain.repository;

import com.staffflow.domain.entity.SaldoAnual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para los saldos anuales de vacaciones, asuntos propios y horas.
 *
 * <p>Restricción de tabla: UNIQUE(empleado_id, anio). Existe exactamente
 * un registro de saldo por empleado y año. SaldoService usa findByEmpleadoIdAndAnio
 * con un patrón findOrCreate: si no existe el registro para el año en curso
 * lo crea con los valores iniciales antes de actualizar.</p>
 *
 * <p>Los saldos los actualiza CierreDiario (botón manual ENCARGADO/ADMIN, E40)
 * y los inicializa para el año siguiente CierreAnual (botón manual ADMIN, E41,
 * decisiones nº11 y nº12).</p>
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
     * E38 (GET /saldos/{empleadoId}) y E39 (GET /saldos/me) para devolver
     * el saldo actual, y en el proceso de CierreDiario para actualizarlo.
     * Si devuelve Optional.empty() SaldoService crea el registro con
     * los valores iniciales del contrato del empleado.</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año del saldo (ej: 2026)
     * @return Optional con el saldo, vacío si no existe para ese año
     */
    Optional<SaldoAnual> findByEmpleadoIdAndAnio(Long empleadoId, Integer anio);

    /**
     * Devuelve el histórico completo de saldos de un empleado.
     *
     * <p>Lo usa SaldoService para mostrar el historial de años anteriores
     * si el cliente lo solicita. También lo usa CierreAnual para obtener
     * el saldo del año que cierra y calcular los pendientes a arrastrar
     * al año nuevo.</p>
     *
     * @param empleadoId id del empleado
     * @return lista de saldos anuales del empleado ordenados por año
     */
    List<SaldoAnual> findByEmpleadoId(Long empleadoId);
}