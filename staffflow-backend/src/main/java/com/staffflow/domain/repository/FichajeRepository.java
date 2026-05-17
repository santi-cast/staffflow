package com.staffflow.domain.repository;

import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.enums.TipoFichaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para los fichajes.
 *
 * <p>Los fichajes son inmutables por mandato del RD-ley 8/2019 (RNF-L01):
 * no existe DELETE ni modificación retroactiva de la identidad del registro
 * (empleado_id, fecha). E23 (PATCH /fichajes/{id}) permite actualizar
 * únicamente tipo, horaEntrada, horaSalida y observaciones (esta última
 * obligatoria, RNF-L02). Este repositorio no expone
 * ningún método de eliminación más allá del heredado de JpaRepository,
 * que nunca debe llamarse desde FichajeService.</p>
 *
 * <p>Restricción de tabla: UNIQUE(empleado_id, fecha). Un empleado no puede
 * tener dos fichajes el mismo día.</p>
 *
 * <p>El método existsByEmpleadoIdAndFecha no se añade porque ya existe
 * findByEmpleadoIdAndFecha que cubre la misma necesidad con .isPresent().</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.entity.Fichaje
 */
@Repository
public interface FichajeRepository extends JpaRepository<Fichaje, Long> {

    /**
     * Devuelve todos los fichajes de un empleado, sin filtros adicionales.
     *
     * <p>Actualmente sin consumidores en producción: E24 (GET /fichajes) y
     * E26 (GET /fichajes/me) usan {@link #findByFiltros} para soportar la
     * combinación opcional de empleado, rango y tipo. Se mantiene por
     * simetría con el resto de derivados.</p>
     *
     * @param empleadoId id del empleado
     * @return lista de fichajes del empleado
     */
    List<Fichaje> findByEmpleadoId(Long empleadoId);

    /**
     * Busca el fichaje de un empleado en una fecha concreta.
     *
     * <p>Consumidores principales:
     * <ul>
     *   <li>FichajeService (E22): valida el UNIQUE(empleado_id, fecha) con
     *       .isPresent() antes de crear un fichaje nuevo y lanza
     *       ConflictException si ya existe.</li>
     *   <li>TerminalService (E48 y endpoints de terminal): detecta si el
     *       empleado ya fichó hoy antes de registrar entrada y completa
     *       horaEntrada/horaSalida.</li>
     *   <li>PausaService: localiza el fichaje del día al iniciar o finalizar
     *       una pausa.</li>
     *   <li>PresenciaService (E37): obtiene el fichaje del empleado
     *       autenticado para construir su parte propio.</li>
     *   <li>ProcesoCierreDiario (cron 23:55): recorre los fichajes del día
     *       para auto-cerrar jornadas y generar días libres.</li>
     * </ul></p>
     *
     * @param empleadoId id del empleado
     * @param fecha      fecha del fichaje
     * @return Optional con el fichaje, vacío si no existe
     */
    Optional<Fichaje> findByEmpleadoIdAndFecha(Long empleadoId, LocalDate fecha);

    /**
     * Devuelve los fichajes de un empleado en un rango de fechas.
     *
     * <p>Consumidores:
     * <ul>
     *   <li>SaldoService: calcula el saldo anual de un periodo concreto.</li>
     *   <li>AusenciaService: localiza los fichajes asociados a una ausencia
     *       en su rango de fechas.</li>
     *   <li>InformeService: agrega fichajes por empleado en los informes de
     *       horas trabajadas y resumen anual.</li>
     * </ul>
     * E24 (GET /fichajes) NO usa este método: se apoya en
     * {@link #findByFiltros} para soportar filtros opcionales combinables.</p>
     *
     * @param empleadoId id del empleado
     * @param desde      fecha de inicio del rango (inclusive)
     * @param hasta      fecha de fin del rango (inclusive)
     * @return lista de fichajes del empleado en el rango indicado
     */
    List<Fichaje> findByEmpleadoIdAndFechaBetween(Long empleadoId, LocalDate desde, LocalDate hasta);

    /**
     * Lista los fichajes de una fecha concreta que tienen horaEntrada
     * registrada pero hora_salida = NULL (jornada incompleta).
     *
     * <p>Lo usa FichajeService en E25 (GET /fichajes/incompletos, RF-21).
     * Permite al encargado detectar al final del día qué empleados han
     * olvidado fichar la salida.</p>
     *
     * <p>Spring Data JPA genera la query automáticamente a partir del nombre
     * del método: fecha = :fecha AND hora_salida IS NULL.</p>
     *
     * @param fecha fecha a consultar (normalmente hoy)
     * @return lista de fichajes sin hora_salida para esa fecha
     */
    List<Fichaje> findByFechaAndHoraSalidaIsNull(LocalDate fecha);

    /**
     * Busca fichajes aplicando filtros opcionales y combinables.
     *
     * <p>Consumidores:
     * <ul>
     *   <li>FichajeService (E24, RF-19/RF-20): ADMIN y ENCARGADO con cualquier
     *       combinación de filtros, incluyendo sin filtros para obtener todos.</li>
     *   <li>FichajeService (E26, RF-51): empleadoId obligatorio (el del token JWT),
     *       filtros de fecha y tipo opcionales.</li>
     *   <li>PausaService: listado de pausas con filtros equivalentes.</li>
     *   <li>PdfService: extracción de vacaciones y asuntos propios para los PDF anuales.</li>
     *   <li>InformeService: agregaciones por rango y tipo para los informes (E29-E33).</li>
     *   <li>AusenciaService: detección de fichajes con procesado=false o true en un rango.</li>
     * </ul></p>
     *
     * <p>El patrón (:param IS NULL OR campo = :param) hace que cada filtro sea
     * opcional: si llega null, la condición siempre es verdadera y no filtra.
     * Cuando todos los parámetros son null, devuelve todos los fichajes.</p>
     *
     * <p>JOIN FETCH empleado evita el problema N+1: carga el empleado en la
     * misma query para que toFichajeResponse() pueda acceder a empleado.getId()
     * sin lanzar una consulta adicional por cada fichaje.</p>
     *
     * @param empleadoId filtro opcional por ID de empleado (null = todos)
     * @param desde      filtro opcional fecha inicio, inclusive (null = sin límite)
     * @param hasta      filtro opcional fecha fin, inclusive (null = sin límite)
     * @param tipo       filtro opcional por tipo de jornada (null = todos los tipos)
     * @return lista de fichajes que cumplen todos los filtros activos
     */
    @Query("SELECT f FROM Fichaje f JOIN FETCH f.empleado e WHERE " +
           "(:empleadoId IS NULL OR e.id = :empleadoId) AND " +
           "(:desde IS NULL OR f.fecha >= :desde) AND " +
           "(:hasta IS NULL OR f.fecha <= :hasta) AND " +
           "(:tipo IS NULL OR f.tipo = :tipo)")
    List<Fichaje> findByFiltros(
            @Param("empleadoId") Long empleadoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("tipo") TipoFichaje tipo);

    // Métodos para PresenciaService (E35 directo, E36 transitivo)

    /**
     * Devuelve todos los fichajes de una fecha concreta con su empleado cargado.
     *
     * Usado por PresenciaService para construir el parte diario (E35) y la
     * lista de empleados sin justificar (E36). Carga todos los fichajes del
     * día en una sola query para evitar el problema N+1: el service clasifica
     * a cada empleado en memoria sin lanzar queries adicionales por cada uno.
     *
     * JOIN FETCH f.empleado garantiza que empleado.id, empleado.nombre y
     * empleado.apellido1 están disponibles en el DTO sin lazy loading.
     *
     * Se usa @Query explícita porque el método convencional findByFecha()
     * no haría el JOIN FETCH y provocaría N+1 al acceder a empleado.
     *
     * @param fecha fecha a consultar (normalmente hoy)
     * @return lista de fichajes con empleado cargado para esa fecha
     */
    @Query("SELECT f FROM Fichaje f JOIN FETCH f.empleado WHERE f.fecha = :fecha")
    List<Fichaje> findByFechaWithEmpleado(@Param("fecha") LocalDate fecha);
}
