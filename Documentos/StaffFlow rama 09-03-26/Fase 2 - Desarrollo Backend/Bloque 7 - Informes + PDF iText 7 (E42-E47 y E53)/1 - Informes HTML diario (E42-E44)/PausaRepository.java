package com.staffflow.domain.repository;

import com.staffflow.domain.entity.Pausa;
import com.staffflow.domain.enums.TipoPausa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para las pausas dentro de la jornada laboral.
 *
 * <p>Las pausas son inmutables igual que los fichajes (RNF-L01):
 * no existe DELETE ni modificación de campos de registro. Solo se
 * permite PATCH en observaciones (E28).</p>
 *
 * <p>Una pausa con horaFin=null es una pausa activa en curso. Este
 * estado es imprescindible para los endpoints de terminal E50 y E51,
 * que detectan si hay pausa activa antes de registrar inicio o fin.</p>
 *
 * <p>Métodos añadidos en Bloque 5 (sesión 10):
 *   - findByFiltros → E29 filtros combinables</p>
 *
 * <p>Métodos añadidos en Bloque 7 (sesión 18):
 *   - findByEmpleadoIdAndFechaBetween → InformeService E42/E43</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.entity.Pausa
 */
@Repository
public interface PausaRepository extends JpaRepository<Pausa, Long> {

    // ---------------------------------------------------------------
    // Métodos existentes (Bloque 1)
    // ---------------------------------------------------------------

    /**
     * Devuelve todas las pausas de un empleado.
     *
     * <p>Lo usa PausaService en E29 (GET /pausas) cuando el filtro
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

    // ---------------------------------------------------------------
    // Métodos añadidos en Bloque 5 (sesión 10)
    // ---------------------------------------------------------------

    /**
     * Busca pausas aplicando filtros opcionales y combinables.
     *
     * <p>Lo usa PausaService en E29 (GET /pausas, RF-24): ADMIN y ENCARGADO
     * con cualquier combinación de filtros, incluyendo sin filtros para
     * obtener todas las pausas.</p>
     *
     * <p>El patrón (:param IS NULL OR campo = :param) hace que cada filtro
     * sea opcional: si llega null, la condición siempre es verdadera.
     * Cuando todos los parámetros son null, devuelve todas las pausas.</p>
     *
     * <p>JOIN FETCH empleado evita el problema N+1: carga el empleado en
     * la misma query para que toPausaResponse() acceda a empleado.getId()
     * sin consulta adicional por cada pausa.</p>
     *
     * @param empleadoId filtro opcional por ID de empleado (null = todos)
     * @param desde      filtro opcional fecha inicio, inclusive (null = sin límite)
     * @param hasta      filtro opcional fecha fin, inclusive (null = sin límite)
     * @param tipoPausa  filtro opcional por tipo de pausa (null = todos los tipos)
     * @return lista de pausas que cumplen todos los filtros activos
     */
    @Query("SELECT p FROM Pausa p JOIN FETCH p.empleado e WHERE " +
           "(:empleadoId IS NULL OR e.id = :empleadoId) AND " +
           "(:desde IS NULL OR p.fecha >= :desde) AND " +
           "(:hasta IS NULL OR p.fecha <= :hasta) AND " +
           "(:tipoPausa IS NULL OR p.tipoPausa = :tipoPausa)")
    List<Pausa> findByFiltros(
            @Param("empleadoId") Long empleadoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("tipoPausa") TipoPausa tipoPausa);

    // ---------------------------------------------------------------
    // Métodos añadidos en Bloque 6 Tarea 2 (PresenciaService E35-E37)
    // ---------------------------------------------------------------

    /**
     * Devuelve todas las pausas activas de una fecha concreta con su empleado cargado.
     *
     * Una pausa activa es aquella con horaFin = null: el empleado está en pausa
     * en este momento. Usado por PresenciaService para determinar qué empleados
     * tienen estado EN_PAUSA al construir el parte diario (E35-E37).
     *
     * Carga todos los registros activos del día en una sola query para evitar N+1:
     * el service construye un Set de empleadoIds en pausa y clasifica en memoria.
     *
     * JOIN FETCH p.empleado garantiza acceso a empleado.id sin lazy loading.
     *
     * @param fecha fecha a consultar (normalmente hoy)
     * @return lista de pausas activas con empleado cargado para esa fecha
     */
    @Query("SELECT p FROM Pausa p JOIN FETCH p.empleado WHERE p.fecha = :fecha AND p.horaFin IS NULL")
    List<Pausa> findPausasActivasByFecha(@Param("fecha") LocalDate fecha);

    // ---------------------------------------------------------------
    // Métodos añadidos en Bloque 7 (sesión 18 — InformeService E42/E43)
    // ---------------------------------------------------------------

    /**
     * Devuelve todas las pausas de un empleado en un rango de fechas.
     *
     * <p>Usado por InformeService para cargar el detalle de pausas de
     * cada día del período en E42 y E43. A diferencia de findByFiltros,
     * no hace JOIN FETCH (el empleado no se necesita aquí porque ya está
     * resuelto en el contexto del informe) y el empleadoId es siempre
     * obligatorio. Más limpio y eficiente para este caso de uso.</p>
     *
     * <p>Spring Data JPA genera la implementación automáticamente a partir
     * del nombre del método. No requiere @Query explícita.</p>
     *
     * @param empleadoId id del empleado (obligatorio)
     * @param desde      fecha de inicio del rango (inclusive)
     * @param hasta      fecha de fin del rango (inclusive)
     * @return lista de pausas del empleado en el rango indicado, ordenadas
     *         por fecha y hora de inicio de forma natural por BD
     */
    List<Pausa> findByEmpleadoIdAndFechaBetween(Long empleadoId, LocalDate desde, LocalDate hasta);
}
