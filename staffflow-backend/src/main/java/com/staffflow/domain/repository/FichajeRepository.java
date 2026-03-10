package com.staffflow.domain.repository;

import com.staffflow.domain.entity.Fichaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para los fichajes.
 *
 * <p>Los fichajes son inmutables por mandato del RD-ley 8/2019 (RNF-L01):
 * no existe DELETE ni modificación de campos de registro. Solo se permite
 * PATCH en observaciones (E24). Este repositorio no expone ningún método
 * de eliminación más allá del heredado de JpaRepository, que nunca
 * debe llamarse desde FichajeService.</p>
 *
 * <p>Restricción de tabla: UNIQUE(empleado_id, fecha). Un empleado no puede
 * tener dos fichajes el mismo día.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.entity.Fichaje
 */
@Repository
public interface FichajeRepository extends JpaRepository<Fichaje, Long> {

    /**
     * Devuelve todos los fichajes de un empleado.
     *
     * <p>Lo usa FichajeService en E23 (GET /fichajes) cuando el filtro
     * incluye solo empleadoId sin rango de fechas.</p>
     *
     * @param empleadoId id del empleado
     * @return lista de fichajes del empleado
     */
    List<Fichaje> findByEmpleadoId(Long empleadoId);

    /**
     * Busca el fichaje de un empleado en una fecha concreta.
     *
     * <p>Lo usan FichajeService y TerminalService para:
     * 1) Validar el UNIQUE(empleado_id, fecha) antes de crear un fichaje nuevo.
     * 2) Detectar si el empleado ya fichó hoy antes de registrar entrada (E48).
     * 3) Completar horaEntrada o horaSalida en los endpoints de terminal.</p>
     *
     * @param empleadoId id del empleado
     * @param fecha      fecha del fichaje
     * @return Optional con el fichaje, vacío si no existe
     */
    Optional<Fichaje> findByEmpleadoIdAndFecha(Long empleadoId, LocalDate fecha);

    /**
     * Devuelve los fichajes de un empleado en un rango de fechas.
     *
     * <p>Lo usa FichajeService en E23 (GET /fichajes) cuando el filtro
     * incluye desde y hasta. También lo usa SaldoService para calcular
     * el saldo anual de un periodo concreto.</p>
     *
     * @param empleadoId id del empleado
     * @param desde      fecha de inicio del rango (inclusive)
     * @param hasta      fecha de fin del rango (inclusive)
     * @return lista de fichajes del empleado en el rango indicado
     */
    List<Fichaje> findByEmpleadoIdAndFechaBetween(Long empleadoId, LocalDate desde, LocalDate hasta);
}