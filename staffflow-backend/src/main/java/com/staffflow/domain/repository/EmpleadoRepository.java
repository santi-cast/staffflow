package com.staffflow.domain.repository;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.enums.CategoriaEmpleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Empleado.
 *
 * Tabla: empleados
 * Usado por: AusenciaService, AuthService, EmpleadoService, FichajeService,
 *            InformeService, PausaService, PdfService, PresenciaService,
 *            SaldoService, TerminalService, ProcesoCierreDiario.
 *
 * Restricciones de tabla relevantes:
 *   - usuario_id UNIQUE: un usuario solo puede tener un perfil de empleado.
 *     La relación usuario-empleado es 1:1 e inmutable.
 *   - pin_terminal UNIQUE CHAR(4): el PIN identifica al empleado en el
 *     terminal físico. Único en toda la tabla (RNF-R03: búsqueda < 100ms).
 *   - codigo_nfc UNIQUE nullable: alternativa al PIN. NULL si no se usa.
 *   - Bajas lógicas: activo=false impide el acceso al terminal pero
 *     conserva el historial de fichajes y saldos.
 *   - Por convención el rol ADMIN no tiene perfil de empleado (no ficha
 *     ni tiene jornada que registrar). NO está impuesto a nivel de BD
 *     ni de validación en EmpleadoService.crear(): es una invariante
 *     operativa que respeta la seed (data.sql) pero el modelo permite
 *     la combinación.
 *
 * Métodos heredados de JpaRepository más usados:
 *   - save()       → crear y actualizar empleado (E13, E16, E17, E18, E65)
 *   - findById()   → obtener detalle (E15) y consumido por múltiples
 *                    services para resolver referencias por id.
 *
 * Métodos custom (consumidos transversalmente, no solo en EmpleadoService):
 *   - findByUsuarioId, findByUsuarioUsername, findByActivo, findByPinTerminal
 *   - findByActivoTrueAndFechaAltaLessThanEqual (empleados operativos a una fecha)
 *   - existsByDni, existsByNumeroEmpleado, existsByPinTerminal, existsByCodigoNfc
 *   - existsByDniAndIdNot, existsByNumeroEmpleadoAndIdNot,
 *     existsByPinTerminalAndIdNot, existsByCodigoNfcAndIdNot
 *   - findByCategoria, findByCategoriaAndActivo
 *   - buscarPorTexto (@Query manual — búsqueda unificada RF-14)
 *
 * Spring Data JPA genera automáticamente la implementación SQL de todos
 * los métodos convencionales. Solo buscarPorTexto requiere @Query explícita.
 */
public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {


    /**
     * Busca el perfil de empleado vinculado a un usuario.
     *
     * @param usuarioId ID del usuario cuyo perfil de empleado se busca
     * @return Optional con el empleado si existe
     */
    Optional<Empleado> findByUsuarioId(Long usuarioId);

    /**
     * Lista todos los empleados con el estado activo indicado.
     *
     * <p>NOTA: este método NO filtra por {@code fechaAlta}. Para procesos
     * operativos diarios (parte diario, cierre nocturno, informes por
     * rango) hay que usar {@link #findByActivoTrueAndFechaAltaLessThanEqual}
     * con la fecha relevante. Este método se reserva para listados
     * administrativos (gestión P13, exportaciones CSV/PDF) en los que el
     * ADMIN debe poder ver a empleados aún no operativos para administrarlos.</p>
     *
     * @param activo true = empleados activos, false = empleados inactivos
     * @return lista de empleados con ese estado (puede ser vacía)
     */
    List<Empleado> findByActivo(boolean activo);

    /**
     * Lista los empleados operativos a una fecha dada: estado activo Y
     * fecha de alta menor o igual a la fecha indicada.
     *
     * <p>Pensado para procesos que reflejan la realidad laboral del día:
     * parte diario (E35), cierre nocturno (ProcesoCierreDiario) e informes
     * por rango (filtrar con {@code hasta} para incluir solo empleados que
     * ya habían comenzado dentro del rango). Un empleado con {@code fechaAlta}
     * futura existe en la base de datos pero todavía no contribuye a los
     * fichajes, ausencias o saldos del día y debe quedar fuera de estos flujos.</p>
     *
     * @param fecha fecha de referencia (inclusive)
     * @return empleados activos cuya fecha de alta es {@code <= fecha}
     */
    List<Empleado> findByActivoTrueAndFechaAltaLessThanEqual(LocalDate fecha);

    /**
     * Busca un empleado por su PIN de terminal.
     *
     * @param pinTerminal PIN de 4 dígitos introducido en el terminal
     * @return Optional con el empleado si el PIN existe
     */
    Optional<Empleado> findByPinTerminal(String pinTerminal);

    /**
     * Comprueba si existe un empleado con el DNI indicado.
     *
     * @param dni DNI a verificar
     * @return true si ya existe un empleado con ese DNI
     */
    boolean existsByDni(String dni);

    /**
     * Comprueba si existe un empleado con el número de empleado indicado.
     *
     * Usado en EmpleadoService.crear() (E13) para validación preventiva
     * de unicidad antes de insertar. HTTP 409 con mensaje claro si devuelve true.
     * Renombrado desde existsByNss (v1.0 → campo numero_empleado).
     *
     * @param numeroEmpleado número de empleado a verificar
     * @return true si ya existe un empleado con ese número
     */
    boolean existsByNumeroEmpleado(String numeroEmpleado);

    /**
     * Comprueba si existe un empleado con el PIN de terminal indicado.
     *
     * @param pinTerminal PIN de 4 dígitos a verificar
     * @return true si ya existe un empleado con ese PIN
     */
    boolean existsByPinTerminal(String pinTerminal);

    /**
     * Comprueba si existe un empleado con el código NFC indicado.
     *
     * @param codigoNfc código NFC a verificar
     * @return true si ya existe un empleado con ese código NFC
     */
    boolean existsByCodigoNfc(String codigoNfc);

    /**
     * Comprueba si existe otro empleado con el DNI indicado, excluyendo
     * al empleado con el id especificado.
     *
     * <p>Usado en EmpleadoService.actualizar() (E16) para validar la unicidad
     * del DNI cuando el ADMIN lo corrige. Excluir al propio empleado permite
     * que conserve su DNI actual sin disparar el conflicto.</p>
     *
     * @param dni DNI a verificar
     * @param id  ID del empleado que se está editando (se excluye)
     * @return true si otro empleado distinto ya tiene ese DNI
     */
    boolean existsByDniAndIdNot(String dni, Long id);

    /**
     * Comprueba si existe otro empleado con el número de empleado indicado,
     * excluyendo al empleado con el id especificado.
     *
     * <p>NO CONSUMIDO en v1: numeroEmpleado se autogenera EMP-XXX en
     * EmpleadoService.crear() y no se edita por E16. Método mantenido como
     * andamiaje por simetría con el bloque de validaciones de unicidad.</p>
     *
     * @param numeroEmpleado número de empleado a verificar
     * @param id             ID del empleado que se está editando (se excluye)
     * @return true si otro empleado distinto ya tiene ese número
     */
    boolean existsByNumeroEmpleadoAndIdNot(String numeroEmpleado, Long id);

    /**
     * Comprueba si existe otro empleado con el PIN indicado, excluyendo
     * al empleado con el id especificado.
     *
     * <p>NO CONSUMIDO en v1: el PIN se regenera aleatoriamente en
     * EmpleadoService.regenerarPin() (E65) usando existsByPinTerminal sin
     * exclusión (la unicidad global incluye al propio empleado, que ya tiene
     * un PIN distinto al candidato). Método mantenido como andamiaje.</p>
     *
     * @param pinTerminal PIN a verificar
     * @param id          ID del empleado que se está editando (se excluye)
     * @return true si otro empleado distinto ya tiene ese PIN
     */
    boolean existsByPinTerminalAndIdNot(String pinTerminal, Long id);

    /**
     * Comprueba si existe otro empleado con el código NFC indicado, excluyendo
     * al empleado con el id especificado.
     *
     * @param codigoNfc código NFC a verificar
     * @param id        ID del empleado que se está editando (se excluye)
     * @return true si otro empleado distinto ya tiene ese código NFC
     */
    boolean existsByCodigoNfcAndIdNot(String codigoNfc, Long id);

    /**
     * Lista todos los empleados con la categoría laboral indicada.
     *
     * @param categoria categoría laboral a filtrar
     * @return lista de empleados con esa categoría (puede ser vacía)
     */
    List<Empleado> findByCategoria(CategoriaEmpleado categoria);

    /**
     * Lista todos los empleados con la categoría y estado activo indicados.
     *
     * @param categoria categoría laboral a filtrar
     * @param activo    estado a filtrar
     * @return lista de empleados que cumplen ambos filtros (puede ser vacía)
     */
    List<Empleado> findByCategoriaAndActivo(CategoriaEmpleado categoria, boolean activo);

    /**
     * Búsqueda unificada de empleados por texto libre (RF-14).
     *
     * @param termino texto de búsqueda en minúsculas (sin wildcards — los añade la query)
     * @return lista de empleados que coinciden en alguno de los cuatro campos
     */
    @Query("SELECT e FROM Empleado e WHERE " +
           "LOWER(e.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "LOWER(e.apellido1) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "LOWER(e.apellido2) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "LOWER(e.dni) LIKE LOWER(CONCAT('%', :termino, '%'))")
    List<Empleado> buscarPorTexto(@Param("termino") String termino);

    /**
     * Busca el perfil de empleado a partir del username de su usuario vinculado.
     *
     * @param username username del usuario vinculado al empleado
     * @return Optional con el empleado si existe
     */
    Optional<Empleado> findByUsuarioUsername(String username);
}
