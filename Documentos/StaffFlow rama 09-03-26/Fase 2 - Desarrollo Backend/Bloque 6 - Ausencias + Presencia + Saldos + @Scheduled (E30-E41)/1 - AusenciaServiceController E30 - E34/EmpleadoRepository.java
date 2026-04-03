package com.staffflow.domain.repository;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.enums.CategoriaEmpleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Empleado.
 *
 * Tabla: empleados
 * Usado por: EmpleadoService, PresenciaService, TerminalService
 *
 * Restricciones de tabla relevantes:
 *   - usuario_id UNIQUE: un usuario solo puede tener un perfil de empleado.
 *     La relación usuario-empleado es 1:1 e inmutable (decisión nº22).
 *   - pin_terminal UNIQUE CHAR(4): el PIN identifica al empleado en el
 *     terminal físico. Único en toda la tabla (RNF-R03: búsqueda < 100ms).
 *   - codigo_nfc UNIQUE nullable: alternativa al PIN. NULL si no se usa.
 *   - Bajas lógicas: activo=false impide el acceso al terminal pero
 *     conserva el historial de fichajes y saldos (decisión nº4).
 *   - El rol ADMIN nunca tiene perfil de empleado: no ficha ni tiene
 *     jornada que registrar.
 *
 * Métodos heredados de JpaRepository usados:
 *   - save()       → crear y actualizar empleado (E13, E16, E17, E18)
 *   - findById()   → obtener detalle (E15)
 *
 * Métodos custom — Bloque 1 (PresenciaService, TerminalService):
 *   - findByUsuarioId, findByActivo, findByPinTerminal
 *
 * Métodos custom añadidos — Bloque 4 (EmpleadoService):
 *   - existsByDni, existsByNss, existsByPinTerminal, existsByCodigoNfc
 *   - existsByDniAndIdNot, existsByNssAndIdNot,
 *     existsByPinTerminalAndIdNot, existsByCodigoNfcAndIdNot
 *   - findByCategoria, findByCategoriaAndActivo
 *   - buscarPorTexto (@Query manual — búsqueda unificada RF-14)
 *
 * Spring Data JPA genera automáticamente la implementación SQL de todos
 * los métodos convencionales. Solo buscarPorTexto requiere @Query explícita.
 */
public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    // ----------------------------------------------------------------
    // Métodos del Bloque 1
    // Usados por: EmpleadoService (/me), PresenciaService, TerminalService
    // ----------------------------------------------------------------

    /**
     * Busca el perfil de empleado vinculado a un usuario.
     *
     * Usado por EmpleadoService.obtenerMiPerfil() (E21 /me) para resolver
     * el perfil del empleado autenticado a partir del username → id del usuario.
     * Si el usuario es ADMIN devuelve Optional.empty() → HTTP 404 (comportamiento
     * esperado: ADMIN no tiene perfil de empleado).
     * También usado por AuthService en login() para incluir empleadoId en el JWT
     * (pendiente Bloque 4, TODO documentado en prompt).
     *
     * @param usuarioId ID del usuario cuyo perfil de empleado se busca
     * @return Optional con el empleado si existe
     */
    Optional<Empleado> findByUsuarioId(Long usuarioId);

    /**
     * Lista todos los empleados con el estado activo indicado.
     *
     * Usado por PresenciaService (E35) para obtener todos los empleados
     * activos al calcular el parte diario de presencia en tiempo real.
     * También usado por EmpleadoService.listar() (E14) cuando se filtra
     * solo por estado.
     *
     * @param activo true = empleados activos, false = empleados inactivos
     * @return lista de empleados con ese estado (puede ser vacía)
     */
    List<Empleado> findByActivo(boolean activo);

    /**
     * Busca un empleado por su PIN de terminal.
     *
     * Usado por TerminalService (E48-E51) para identificar al empleado
     * que introduce el PIN en el terminal físico. El índice UNIQUE en
     * pin_terminal garantiza la búsqueda en menos de 100ms (RNF-R03).
     * Si no existe → HTTP 404. El bloqueo por 5 intentos fallidos lo
     * gestiona TerminalService, no este repositorio (decisión nº16).
     *
     * @param pinTerminal PIN de 4 dígitos introducido en el terminal
     * @return Optional con el empleado si el PIN existe
     */
    Optional<Empleado> findByPinTerminal(String pinTerminal);

    // ----------------------------------------------------------------
    // Métodos añadidos en Bloque 4
    // Usados por: EmpleadoService (E13, E14, E16)
    // ----------------------------------------------------------------

    /**
     * Comprueba si existe un empleado con el DNI indicado.
     *
     * Usado en EmpleadoService.crear() (E13) para validación preventiva
     * de unicidad antes de insertar. HTTP 409 con mensaje claro si devuelve true.
     *
     * @param dni DNI a verificar
     * @return true si ya existe un empleado con ese DNI
     */
    boolean existsByDni(String dni);

    /**
     * Comprueba si existe un empleado con el NSS indicado.
     *
     * Usado en EmpleadoService.crear() (E13) para validación preventiva
     * de unicidad. HTTP 409 con mensaje claro si devuelve true.
     *
     * @param nss número de seguridad social a verificar
     * @return true si ya existe un empleado con ese NSS
     */
    boolean existsByNss(String nss);

    /**
     * Comprueba si existe un empleado con el PIN de terminal indicado.
     *
     * Usado en EmpleadoService.crear() (E13) para validación preventiva
     * de unicidad del PIN antes de insertar. HTTP 409 con mensaje claro
     * si devuelve true. El PIN debe ser único en todo el sistema (RNF-R03).
     *
     * Nota: difiere de findByPinTerminal (Bloque 1) en que devuelve boolean
     * en lugar de Optional, optimizando la consulta de existencia.
     *
     * @param pinTerminal PIN de 4 dígitos a verificar
     * @return true si ya existe un empleado con ese PIN
     */
    boolean existsByPinTerminal(String pinTerminal);

    /**
     * Comprueba si existe un empleado con el código NFC indicado.
     *
     * Usado en EmpleadoService.crear() (E13) para validación preventiva
     * de unicidad del NFC. Solo se verifica si el campo no es null en el
     * request (NFC es opcional). HTTP 409 con mensaje claro si devuelve true.
     *
     * @param codigoNfc código NFC a verificar
     * @return true si ya existe un empleado con ese código NFC
     */
    boolean existsByCodigoNfc(String codigoNfc);

    /**
     * Comprueba si existe otro empleado con el DNI indicado, excluyendo
     * al empleado con el id especificado.
     *
     * Usado en EmpleadoService.actualizar() (E16) para validar unicidad
     * de DNI en edición sin producir falso positivo cuando el empleado
     * mantiene su propio DNI sin cambiarlo.
     *
     * @param dni DNI a verificar
     * @param id  ID del empleado que se está editando (se excluye)
     * @return true si otro empleado distinto ya tiene ese DNI
     */
    boolean existsByDniAndIdNot(String dni, Long id);

    /**
     * Comprueba si existe otro empleado con el NSS indicado, excluyendo
     * al empleado con el id especificado.
     *
     * Usado en EmpleadoService.actualizar() (E16) para validar unicidad
     * de NSS en edición.
     *
     * @param nss NSS a verificar
     * @param id  ID del empleado que se está editando (se excluye)
     * @return true si otro empleado distinto ya tiene ese NSS
     */
    boolean existsByNssAndIdNot(String nss, Long id);

    /**
     * Comprueba si existe otro empleado con el PIN indicado, excluyendo
     * al empleado con el id especificado.
     *
     * Usado en EmpleadoService.actualizar() (E16) para validar unicidad
     * de PIN en edición. El PIN debe seguir siendo único en el sistema
     * tras la edición (RNF-R03).
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
     * Usado en EmpleadoService.actualizar() (E16) para validar unicidad
     * de NFC en edición.
     *
     * @param codigoNfc código NFC a verificar
     * @param id        ID del empleado que se está editando (se excluye)
     * @return true si otro empleado distinto ya tiene ese código NFC
     */
    boolean existsByCodigoNfcAndIdNot(String codigoNfc, Long id);

    /**
     * Lista todos los empleados con la categoría laboral indicada.
     *
     * Usado en EmpleadoService.listar() (E14) cuando se filtra por
     * categoría sin filtro de estado activo.
     *
     * @param categoria categoría laboral a filtrar
     * @return lista de empleados con esa categoría (puede ser vacía)
     */
    List<Empleado> findByCategoria(CategoriaEmpleado categoria);

    /**
     * Lista todos los empleados con la categoría y estado activo indicados.
     *
     * Usado en EmpleadoService.listar() (E14) cuando se aplican ambos
     * filtros simultáneamente (?categoria=X&activo=true|false).
     *
     * @param categoria categoría laboral a filtrar
     * @param activo    estado a filtrar
     * @return lista de empleados que cumplen ambos filtros (puede ser vacía)
     */
    List<Empleado> findByCategoriaAndActivo(CategoriaEmpleado categoria, boolean activo);

    /**
     * Búsqueda unificada de empleados por texto libre (RF-14).
     *
     * Busca simultáneamente en nombre, apellido1, apellido2 y dni.
     * Integra RF-12 (listar empleados) y RF-14 (buscar por nombre/DNI)
     * en un solo endpoint (E14, parámetro ?q=texto).
     *
     * La búsqueda es case-insensitive mediante LOWER() en JPQL.
     * El parámetro termino debe llegar en minúsculas desde el service
     * (EmpleadoService.listar() aplica q.trim().toLowerCase()).
     *
     * Se usa @Query explícita porque Spring Data no puede inferir
     * automáticamente una condición OR entre cuatro campos distintos
     * a partir del nombre del método.
     *
     * Alternativa descartada: Specification<Empleado> con JpaSpecificationExecutor.
     * Añadiría complejidad innecesaria para una sola consulta fija.
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
     * Usado por AusenciaService.listarMias() (E34 /me) para resolver el
     * empleadoId del empleado autenticado a partir del username extraído del JWT.
     * Navega la relación empleado → usuario → username en una sola query,
     * evitando el doble lookup (findByUsername + findByUsuarioId).
     *
     * Si el usuario autenticado es ADMIN devuelve Optional.empty()
     * porque ADMIN no tiene perfil de empleado (comportamiento esperado).
     *
     * @param username username del usuario vinculado al empleado
     * @return Optional con el empleado si existe
     */
    Optional<Empleado> findByUsuarioUsername(String username);
}
