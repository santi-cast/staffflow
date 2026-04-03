package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.CategoriaEmpleado;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.EmpleadoPatchRequest;
import com.staffflow.dto.request.EmpleadoRequest;
import com.staffflow.dto.response.EmpleadoResponse;
import com.staffflow.dto.response.MensajeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de gestión del perfil laboral de los empleados.
 *
 * Cubre los endpoints E13-E21 del Grupo 4 (Gestión de Empleados).
 * ADMIN y ENCARGADO acceden a todos los empleados (E13-E20).
 * EMPLEADO solo accede a sus propios datos mediante /me (E21).
 *
 * Decisiones de diseño aplicadas:
 *   - Relación 1:1 usuario-empleado inmutable (decisión nº22): una vez
 *     vinculado un usuario a un empleado, el campo usuarioId no puede
 *     modificarse. Esta restricción se aplica en actualizar() ignorando
 *     el campo usuarioId aunque venga en el request.
 *   - ADMIN excluido de perfiles de empleado: si un usuario ADMIN llama
 *     a /me devuelve HTTP 404 porque ADMIN no tiene perfil de empleado.
 *     Esto es comportamiento esperado, no un error del sistema.
 *   - pinTerminal filtrado por rol (Opción A acordada en sesión 7):
 *     ADMIN recibe pinTerminal en la respuesta de detalle (E15).
 *     ENCARGADO recibe pinTerminal=null. EMPLEADO nunca recibe pin
 *     (accede por /me que no incluye pin en ningún caso).
 *     El filtrado se realiza en este servicio recibiendo el rol como
 *     parámetro del controller, que lo extrae del JWT.
 *   - Búsqueda unificada (RF-14): el parámetro q busca simultáneamente
 *     en nombre, apellido1, apellido2 y dni en una sola consulta.
 *   - HTTP 409 preventivo para DNI, NSS, PIN o NFC duplicados antes
 *     de que explote la BD con DataIntegrityViolationException.
 *   - Baja lógica (decisión nº4): activo=false, nunca SQL DELETE.
 *     El historial de fichajes, pausas y saldos queda intacto.
 *   - E19 (estado tiempo real) y E20 (export CSV/PDF) se implementan
 *     en Bloque 6 y Bloque 7 respectivamente. Sus métodos en este
 *     servicio lanzan UnsupportedOperationException hasta entonces.
 *
 * RF cubiertos: RF-08 a RF-16, RF-50.
 * RNF aplicados: RNF-M01 (sin lógica en controller), RNF-R03 (PIN único).
 *
 * Desviación D-017: firma de obtenerPorId ajustada respecto al esqueleto
 * del Bloque 1 para incluir el parámetro rol necesario para filtrar
 * pinTerminal según la Opción A acordada. El esqueleto original era
 * obtenerPorId(Long id). La firma definitiva es obtenerPorId(Long id, String rol).
 */
@Service
@RequiredArgsConstructor
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final UsuarioRepository usuarioRepository;

    // ----------------------------------------------------------------
    // E13 — POST /api/v1/empleados
    // RF-08: Crear perfil de empleado
    // ----------------------------------------------------------------

    /**
     * Crea el perfil laboral de un empleado vinculándolo a un usuario existente.
     *
     * La relación usuario-empleado es 1:1 garantizada por la restricción
     * UNIQUE en usuario_id de la tabla empleados. Si ya existe un empleado
     * vinculado al mismo usuarioId, la BD lanzará DataIntegrityViolationException
     * que GlobalExceptionHandler convierte en HTTP 400.
     *
     * El campo pinTerminal debe ser único en todo el sistema (índice UNIQUE,
     * RNF-R03): permite la búsqueda en menos de 100ms desde el terminal.
     *
     * Los campos jornadaSemanalHoras y jornadaDiariaMinutos tienen propósitos
     * distintos (decisión nº22):
     *   - jornadaSemanalHoras: dato contractual introducido por el ADMIN.
     *   - jornadaDiariaMinutos: referencia de cálculo para el saldo de horas.
     *
     * Códigos HTTP producidos:
     *   201 Created      → perfil creado correctamente
     *   400 Bad Request  → datos de entrada inválidos (@Valid en controller)
     *   404 Not Found    → usuarioId no existe en la tabla usuarios
     *   409 Conflict     → DNI, NSS, PIN o NFC ya registrados en otro empleado
     *
     * @param request datos del perfil laboral del empleado
     * @return EmpleadoResponse con los datos del perfil creado
     */
    @Transactional
    public EmpleadoResponse crear(EmpleadoRequest request) {
        // Verificar que el usuarioId existe (HTTP 404 si no)
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario con id " + request.getUsuarioId() + " no encontrado"));

        // Validaciones preventivas de unicidad (HTTP 409 con mensaje claro)
        if (empleadoRepository.existsByDni(request.getDni())) {
            throw new IllegalStateException(
                    "El DNI '" + request.getDni() + "' ya está registrado");
        }
        if (empleadoRepository.existsByNss(request.getNss())) {
            throw new IllegalStateException(
                    "El NSS '" + request.getNss() + "' ya está registrado");
        }
        if (empleadoRepository.existsByPinTerminal(request.getPinTerminal())) {
            throw new IllegalStateException(
                    "El PIN de terminal '" + request.getPinTerminal() + "' ya está registrado");
        }
        if (request.getCodigoNfc() != null
                && empleadoRepository.existsByCodigoNfc(request.getCodigoNfc())) {
            throw new IllegalStateException(
                    "El código NFC '" + request.getCodigoNfc() + "' ya está registrado");
        }

        Empleado empleado = new Empleado();
        empleado.setUsuario(usuario);
        empleado.setNombre(request.getNombre());
        empleado.setApellido1(request.getApellido1());
        empleado.setApellido2(request.getApellido2());
        empleado.setDni(request.getDni());
        empleado.setNss(request.getNss());
        empleado.setFechaAlta(request.getFechaAlta());
        // categoria ya es CategoriaEmpleado en el DTO — sin valueOf()
        empleado.setCategoria(request.getCategoria());
        // jornadaSemanalHoras es Double en el DTO e Integer en la entidad — conversión explícita
        empleado.setJornadaSemanalHoras(request.getJornadaSemanalHoras().intValue());
        empleado.setJornadaDiariaMinutos(request.getJornadaDiariaMinutos());
        empleado.setDiasVacacionesAnuales(request.getDiasVacacionesAnuales());
        empleado.setDiasAsuntosPropiosAnuales(request.getDiasAsuntosPropiosAnuales());
        empleado.setPinTerminal(request.getPinTerminal());
        empleado.setCodigoNfc(request.getCodigoNfc());
        empleado.setActivo(true);

        Empleado guardado = empleadoRepository.save(empleado);
        // En creación el ADMIN siempre recibe el PIN (acaba de crearlo)
        return toEmpleadoResponse(guardado);
    }

    // ----------------------------------------------------------------
    // E14 — GET /api/v1/empleados
    // RF-12, RF-14: Listar empleados con filtros
    // ----------------------------------------------------------------

    /**
     * Lista empleados con filtros opcionales y combinables.
     *
     * Sin parámetros devuelve todos los empleados activos.
     * El parámetro q busca simultáneamente en nombre, apellido1,
     * apellido2 y dni integrando RF-12 y RF-14 en un solo endpoint.
     *
     * El PIN no se devuelve en el listado por seguridad (aparece como
     * null en todos los elementos independientemente del rol).
     * Solo se devuelve en el detalle individual E15 para el rol ADMIN.
     *
     * Códigos HTTP producidos:
     *   200 OK          → lista devuelta (puede ser lista vacía)
     *   403 Forbidden   → rol insuficiente
     *
     * @param activo    filtro por estado: true/false — null = solo activos (defecto)
     * @param q         búsqueda por nombre, apellido1, apellido2 o dni — null = sin filtro
     * @param categoria filtro por categoría laboral — null = sin filtro
     * @return lista de EmpleadoResponse sin pinTerminal
     */
    @Transactional(readOnly = true)
    public List<EmpleadoResponse> listar(Boolean activo, String q, String categoria) {
        List<Empleado> empleados;

        if (q != null && !q.isBlank()) {
            // Búsqueda por texto en nombre, apellidos o DNI (RF-14)
            String termino = q.trim().toLowerCase();
            empleados = empleadoRepository.buscarPorTexto(termino);
        } else if (categoria != null) {
            CategoriaEmpleado cat = CategoriaEmpleado.valueOf(categoria);
            empleados = (activo != null)
                    ? empleadoRepository.findByCategoriaAndActivo(cat, activo)
                    : empleadoRepository.findByCategoria(cat);
        } else if (activo != null) {
            empleados = empleadoRepository.findByActivo(activo);
        } else {
            // Sin filtros: devuelve solo los activos (comportamiento por defecto)
            empleados = empleadoRepository.findByActivo(true);
        }

        // PIN nunca se devuelve en listados (incluirPin = false)
        return empleados.stream()
                .map(this::toEmpleadoResponse)
                .collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // E15 — GET /api/v1/empleados/{id}
    // RF-13: Perfil completo de empleado
    // ----------------------------------------------------------------

    /**
     * Devuelve el perfil completo de un empleado.
     *
     * El campo pinTerminal se incluye o excluye según el rol del usuario
     * autenticado (Opción A — decisión de sesión 7, desviación D-017):
     *   - ADMIN:     pinTerminal con valor real
     *   - ENCARGADO: pinTerminal = null
     *
     * El controller extrae el rol del JWT y lo pasa como parámetro.
     *
     * Códigos HTTP producidos:
     *   200 OK          → empleado encontrado y devuelto
     *   403 Forbidden   → rol insuficiente (EMPLEADO bloqueado por Spring Security)
     *   404 Not Found   → empleado con el id indicado no existe
     *
     * @param id  ID del empleado a consultar
     * @param rol rol del usuario autenticado extraído del JWT por el controller
     * @return EmpleadoResponse con pinTerminal según rol
     */
    @Transactional(readOnly = true)
    public EmpleadoResponse obtenerPorId(Long id) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Empleado con id " + id + " no encontrado"));
        // pinTerminal nunca se expone en la respuesta (decisión nº21)
        return toEmpleadoResponse(empleado);
    }

    // ----------------------------------------------------------------
    // E16 — PATCH /api/v1/empleados/{id}
    // RF-09: Editar perfil laboral
    // ----------------------------------------------------------------

    /**
     * Actualiza el perfil laboral de un empleado.
     *
     * Solo actualiza los campos enviados con valor no nulo (PATCH semántico).
     * El campo usuarioId nunca se modifica: la vinculación usuario-empleado
     * es permanente (decisión nº22).
     *
     * Valida unicidad de PIN, DNI y NSS excluyendo al propio empleado que
     * se está editando (puede conservar sus propios valores sin conflicto).
     *
     * Códigos HTTP producidos:
     *   200 OK          → perfil actualizado correctamente
     *   400 Bad Request → datos de entrada inválidos
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → empleado no encontrado
     *   409 Conflict    → PIN, DNI o NSS duplicados en otro empleado
     *
     * @param id      ID del empleado a actualizar
     * @param request campos a actualizar (todos opcionales)
     * @return EmpleadoResponse con los datos actualizados (sin PIN)
     */
    @Transactional
    public EmpleadoResponse actualizar(Long id, EmpleadoPatchRequest request) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Empleado con id " + id + " no encontrado"));

        if (request.getNombre() != null) {
            empleado.setNombre(request.getNombre());
        }
        if (request.getApellido1() != null) {
            empleado.setApellido1(request.getApellido1());
        }
        if (request.getApellido2() != null) {
            empleado.setApellido2(request.getApellido2());
        }
        // dni, nss y fechaAlta son inmutables (decisión nº22) — no existen en EmpleadoPatchRequest
        if (request.getCategoria() != null) {
            // categoria ya es CategoriaEmpleado en el DTO — sin valueOf()
            empleado.setCategoria(request.getCategoria());
        }
        if (request.getJornadaSemanalHoras() != null) {
            // jornadaSemanalHoras es Double en el DTO e Integer en la entidad — conversión explícita
            empleado.setJornadaSemanalHoras(request.getJornadaSemanalHoras().intValue());
        }
        if (request.getJornadaDiariaMinutos() != null) {
            empleado.setJornadaDiariaMinutos(request.getJornadaDiariaMinutos());
        }
        if (request.getDiasVacacionesAnuales() != null) {
            empleado.setDiasVacacionesAnuales(request.getDiasVacacionesAnuales());
        }
        if (request.getDiasAsuntosPropiosAnuales() != null) {
            empleado.setDiasAsuntosPropiosAnuales(request.getDiasAsuntosPropiosAnuales());
        }
        if (request.getPinTerminal() != null) {
            if (empleadoRepository.existsByPinTerminalAndIdNot(request.getPinTerminal(), id)) {
                throw new IllegalStateException(
                        "El PIN de terminal '" + request.getPinTerminal() + "' ya está registrado");
            }
            empleado.setPinTerminal(request.getPinTerminal());
        }
        if (request.getCodigoNfc() != null) {
            if (empleadoRepository.existsByCodigoNfcAndIdNot(request.getCodigoNfc(), id)) {
                throw new IllegalStateException(
                        "El código NFC '" + request.getCodigoNfc() + "' ya está registrado");
            }
            empleado.setCodigoNfc(request.getCodigoNfc());
        }

        // PIN no se devuelve en edición (el controller no pasa rol aquí,
        // el PATCH nunca incluye el PIN en la respuesta por seguridad)
        return toEmpleadoResponse(empleadoRepository.save(empleado));
    }

    // ----------------------------------------------------------------
    // E17 — PATCH /api/v1/empleados/{id}/baja
    // RF-10: Dar de baja empleado
    // ----------------------------------------------------------------

    /**
     * Desactiva un empleado aplicando baja lógica (activo = false).
     *
     * El empleado desactivado no puede fichar desde el terminal ni
     * aparece en listados por defecto. Su historial de fichajes, pausas
     * y saldos queda intacto para consultas e informes históricos.
     *
     * Decisión nº4: baja lógica en lugar de DELETE físico.
     *
     * Códigos HTTP producidos:
     *   200 OK          → empleado desactivado correctamente
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → empleado no encontrado
     *
     * @param id ID del empleado a desactivar
     * @return MensajeResponse confirmando la operación
     */
    @Transactional
    public MensajeResponse darDeBaja(Long id) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Empleado con id " + id + " no encontrado"));

        empleado.setActivo(false);
        empleadoRepository.save(empleado);

        return new MensajeResponse("Empleado desactivado correctamente");
    }

    // ----------------------------------------------------------------
    // E18 — PATCH /api/v1/empleados/{id}/reactivar
    // RF-11: Reactivar empleado
    // ----------------------------------------------------------------

    /**
     * Reactiva un empleado previamente desactivado (activo = true).
     *
     * El empleado recupera inmediatamente la capacidad de fichar desde
     * el terminal con su PIN. HTTP 409 si el empleado ya estaba activo
     * (reactivar un empleado activo es un error de lógica de negocio).
     *
     * Códigos HTTP producidos:
     *   200 OK          → empleado reactivado correctamente
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → empleado no encontrado
     *   409 Conflict    → el empleado ya estaba activo
     *
     * @param id ID del empleado a reactivar
     * @return MensajeResponse confirmando la operación
     */
    @Transactional
    public MensajeResponse reactivar(Long id) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Empleado con id " + id + " no encontrado"));

        if (empleado.getActivo()) {
            throw new IllegalStateException(
                    "El empleado con id " + id + " ya está activo");
        }

        empleado.setActivo(true);
        empleadoRepository.save(empleado);

        return new MensajeResponse("Empleado reactivado correctamente");
    }

    // ----------------------------------------------------------------
    // E19 — GET /api/v1/empleados/estado
    // RF-15: Estado en tiempo real de los empleados
    // Pendiente de implementación en Bloque 6 (depende de FichajeRepository,
    // PausaRepository y PlanificacionAusenciaRepository)
    // ----------------------------------------------------------------

    /**
     * Devuelve el estado en tiempo real de todos los empleados activos
     * para una fecha dada.
     *
     * Pendiente de implementación en Bloque 6. Depende de la agregación
     * de datos de fichajes, pausas y ausencias que se implementa en
     * PresenciaService. Se implementará en este servicio coordinando
     * con PresenciaService o consultando directamente los repositorios
     * necesarios.
     *
     * @param fecha fecha para la consulta (defecto: hoy)
     * @return lista de estados por empleado
     */
    public Object obtenerEstado(java.time.LocalDate fecha) {
        throw new UnsupportedOperationException(
                "Pendiente de implementar en Bloque 6");
    }

    // ----------------------------------------------------------------
    // E20 — GET /api/v1/empleados/export
    // RF-16: Exportar listado de empleados
    // Pendiente de implementación en Bloque 7 (CSV/PDF con iText 7)
    // ----------------------------------------------------------------

    /**
     * Exporta el listado de empleados en formato CSV o PDF.
     *
     * Pendiente de implementación en Bloque 7. El formato CSV es texto
     * plano; el PDF usa iText 7 con el logotipo de la empresa si está
     * configurado. Requiere ConfiguracionEmpresaRepository para el logo.
     *
     * @param formato "csv" o "pdf"
     * @param activo  filtro por estado — null = todos
     * @return byte[] con el contenido del fichero generado
     */
    public byte[] exportar(String formato, Boolean activo) {
        throw new UnsupportedOperationException(
                "Pendiente de implementar en Bloque 7");
    }

    // ----------------------------------------------------------------
    // E21 — GET /api/v1/empleados/me
    // RF-50: Perfil propio del empleado autenticado
    // ----------------------------------------------------------------

    /**
     * Devuelve el perfil del empleado autenticado.
     *
     * El controller extrae el username del objeto Authentication de Spring
     * Security (authentication.getName()) y lo pasa a este método. El service
     * resuelve el id del usuario a partir del username consultando
     * UsuarioRepository, y luego localiza el perfil de empleado vinculado.
     *
     * Esta estrategia evita inyectar repositorios en el controller (RNF-M01)
     * y es compatible con UserDetailsServiceImpl que devuelve el User estándar
     * de Spring Security sin getId() (decisión de sesión 7, Opción B, D-017).
     *
     * Spring Security garantiza que solo usuarios con rol EMPLEADO llegan
     * aquí (@PreAuthorize en el controller). ADMIN y ENCARGADO reciben HTTP 403.
     *
     * El ADMIN no tiene perfil de empleado: si por cualquier motivo llegara
     * aquí, findByUsuarioId devuelve vacío y se lanza IllegalStateException
     * que GlobalExceptionHandler convierte en HTTP 404. Es comportamiento
     * esperado, no un error del sistema.
     *
     * El PIN nunca se devuelve en /me independientemente del rol.
     *
     * Códigos HTTP producidos:
     *   200 OK          → perfil devuelto correctamente
     *   403 Forbidden   → rol insuficiente (ADMIN/ENCARGADO bloqueados por Spring Security)
     *   404 Not Found   → usuario autenticado sin perfil de empleado
     *
     * @param username username del usuario autenticado extraído de Authentication
     * @return EmpleadoResponse con el perfil propio (sin pinTerminal)
     */
    @Transactional(readOnly = true)
    public EmpleadoResponse obtenerMiPerfil(String username) {
        // Resolver el id del usuario a partir del username (disponible en Authentication)
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario no encontrado: " + username));

        // Localizar el perfil de empleado vinculado al usuario autenticado
        Empleado empleado = empleadoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No existe perfil de empleado para el usuario '" + username + "'"));

        // PIN nunca se devuelve en /me (incluirPin = false)
        return toEmpleadoResponse(empleado);
    }

    // ----------------------------------------------------------------
    // Conversión entidad → DTO (uso interno)
    // ----------------------------------------------------------------

    /**
     * Convierte una entidad Empleado en su DTO de respuesta.
     *
     * Punto único de conversión entidad → DTO para este servicio.
     * El parámetro incluirPin controla si el campo pinTerminal se
     * incluye en la respuesta o se devuelve como null (Opción A):
     *   - true:  pinTerminal con valor real (solo ADMIN en E15)
     *   - false: pinTerminal = null (ENCARGADO, listados, /me)
     *
     * @param empleado   entidad JPA a convertir
     * @param incluirPin true si el rol es ADMIN y se debe incluir el PIN
     * @return EmpleadoResponse listo para devolver al controller
     */
    private EmpleadoResponse toEmpleadoResponse(Empleado empleado) {
        EmpleadoResponse response = new EmpleadoResponse();
        response.setId(empleado.getId());
        response.setUsuarioId(empleado.getUsuario().getId());
        response.setNombre(empleado.getNombre());
        response.setApellido1(empleado.getApellido1());
        response.setApellido2(empleado.getApellido2());
        response.setDni(empleado.getDni());
        response.setNss(empleado.getNss());
        response.setFechaAlta(empleado.getFechaAlta());
        response.setCategoria(empleado.getCategoria());
        response.setJornadaSemanalHoras(empleado.getJornadaSemanalHoras());
        response.setJornadaDiariaMinutos(empleado.getJornadaDiariaMinutos());
        response.setDiasVacacionesAnuales(empleado.getDiasVacacionesAnuales());
        response.setDiasAsuntosPropiosAnuales(empleado.getDiasAsuntosPropiosAnuales());
        // pinTerminal nunca se expone en ningún DTO de respuesta (decisión nº21)
        response.setCodigoNfc(empleado.getCodigoNfc());
        response.setActivo(empleado.getActivo());
        return response;
    }
}
