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
import com.staffflow.dto.response.ParteDiarioResponse;
import com.staffflow.dto.response.RegenerarPinResponse;
import com.staffflow.exception.ConflictException;
import com.staffflow.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Servicio de gestión del perfil laboral de los empleados.
 *
 * Cubre los endpoints E13-E21 del Grupo 4 (Gestión de Empleados).
 * ADMIN y ENCARGADO acceden a todos los empleados (E13-E20).
 * EMPLEADO y ENCARGADO acceden a sus propios datos mediante /me (E21).
 *
 * Decisiones de diseño aplicadas:
 *   - Relación 1:1 usuario-empleado inmutable: una vez
 *     vinculado un usuario a un empleado, el campo usuarioId no puede
 *     modificarse. Esta restricción se aplica en actualizar() ignorando
 *     el campo usuarioId aunque venga en el request.
 *   - ADMIN excluido de /me a nivel de seguridad:
 *     @PreAuthorize("hasAnyRole('EMPLEADO','ENCARGADO')") en el controller
 *     bloquea a ADMIN con HTTP 403 antes de llegar al service. Si en el
 *     futuro se autorizase ADMIN en method security, el service responderia
 *     HTTP 404 via NotFoundException porque ADMIN no tiene perfil de
 *     empleado. Comportamiento esperado en ambos casos.
 *   - pinTerminal y email se exponen en E15 (GET /empleados/{id}) SOLO
 *     al rol ADMIN. ENCARGADO recibe null en ambos campos (Opción A
 *     acordada con Android). El resto de endpoints de listado y edición
 *     nunca exponen el PIN. E65 (regenerar-pin) sí lo devuelve a ambos
 *     roles porque su propósito es entregarlo al empleado en mano.
 *   - Búsqueda unificada (RF-14): el parámetro q busca simultáneamente
 *     en nombre, apellido1, apellido2 y dni en una sola consulta.
   *   - HTTP 409 preventivo para DNI, numero_empleado o NFC duplicados
 *     antes de que explote la BD con DataIntegrityViolationException
 *     (NFC: validación preventiva, fichaje por NFC reservado para v2).
 *   - Baja lógica: activo=false, nunca SQL DELETE.
 *     El historial de fichajes, pausas y saldos queda intacto.
 *   - E19 (estado tiempo real) y E20 (export CSV/PDF) están fuera del
 *     alcance de v1.0. Sus métodos lanzan UnsupportedOperationException
 *     de forma deliberada y quedan documentados como mejoras en v2.0.
 *
 * RF cubiertos: RF-08 a RF-16, RF-50.
 * RNF aplicados: RNF-M01 (sin lógica en controller), RNF-R03 (PIN único).
 */
@Service
@RequiredArgsConstructor
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PresenciaService presenciaService;
    private final PdfService pdfService;

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
     * distintos:
     *   - jornadaSemanalHoras: dato contractual introducido por el ADMIN.
     *   - jornadaDiariaMinutos: referencia de cálculo para el saldo de horas.
     *
     * Códigos HTTP producidos:
     *   201 Created      → perfil creado correctamente
     *   400 Bad Request  → datos de entrada inválidos (@Valid en controller)
     *   404 Not Found    → usuarioId no existe en la tabla usuarios
     *   409 Conflict     → DNI, numero_empleado o NFC ya registrados
     *                      (PIN se autogenera en el service, no proviene del
     *                      request; NFC: feature reservada para v2)
     *
     * @param request datos del perfil laboral del empleado
     * @return EmpleadoResponse con los datos del perfil creado
     */
    @Transactional
    public EmpleadoResponse crear(EmpleadoRequest request) {
        // Verificar que el usuarioId existe (HTTP 404 si no)
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new NotFoundException(
                        "Usuario con id " + request.getUsuarioId() + " no encontrado"));

        // Validación preventiva de unicidad de DNI (HTTP 409 con mensaje claro)
        if (empleadoRepository.existsByDni(request.getDni())) {
            throw new ConflictException(
                    "El DNI '" + request.getDni() + "' ya está registrado");
        }
        if (request.getCodigoNfc() != null
                && empleadoRepository.existsByCodigoNfc(request.getCodigoNfc())) {
            throw new ConflictException(
                    "El código NFC '" + request.getCodigoNfc() + "' ya está registrado");
        }

        // Auto-generar número de empleado: EMP-001, EMP-002, ...
        // Usa count() para incluir empleados dados de baja (evita reutilizar números).
        long total = empleadoRepository.count();
        String numeroEmpleado = String.format("EMP-%03d", total + 1);
        while (empleadoRepository.existsByNumeroEmpleado(numeroEmpleado)) {
            total++;
            numeroEmpleado = String.format("EMP-%03d", total + 1);
        }

        // Auto-generar PIN de 4 dígitos único
        String pin = generarPinUnico();

        // Calcular jornada diaria: (horas/semana / 5 días) * 60 minutos
        int jornadaDiariaMinutos = (int) Math.round(request.getJornadaSemanalHoras() / 5.0 * 60);

        Empleado empleado = new Empleado();
        empleado.setUsuario(usuario);
        empleado.setNombre(request.getNombre());
        empleado.setApellido1(request.getApellido1());
        empleado.setApellido2(request.getApellido2());
        empleado.setDni(request.getDni());
        empleado.setNumeroEmpleado(numeroEmpleado);
        empleado.setFechaAlta(LocalDate.now());
        // categoria ya es CategoriaEmpleado en el DTO — sin valueOf()
        empleado.setCategoria(request.getCategoria());
        empleado.setJornadaSemanalHoras(request.getJornadaSemanalHoras());
        empleado.setJornadaDiariaMinutos(jornadaDiariaMinutos);
        empleado.setDiasVacacionesAnuales(request.getDiasVacacionesAnuales());
        empleado.setDiasAsuntosPropiosAnuales(request.getDiasAsuntosPropiosAnuales());
        empleado.setPinTerminal(pin);
        empleado.setCodigoNfc(request.getCodigoNfc());
        empleado.setActivo(true);

        Empleado guardado = empleadoRepository.save(empleado);
        // En creación se devuelve el PIN para que el ADMIN/ENCARGADO lo entregue al empleado
        EmpleadoResponse response = toEmpleadoResponse(guardado);
        response.setPinTerminal(guardado.getPinTerminal());
        return response;
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
            // Aplicar filtros activo y categoria en memoria sobre el resultado de la búsqueda
            if (activo != null) {
                final Boolean activoFinal = activo;
                empleados = empleados.stream()
                        .filter(e -> activoFinal.equals(e.getActivo()))
                        .collect(Collectors.toList());
            }
            if (categoria != null) {
                final CategoriaEmpleado cat = CategoriaEmpleado.valueOf(categoria);
                empleados = empleados.stream()
                        .filter(e -> cat.equals(e.getCategoria()))
                        .collect(Collectors.toList());
            }
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

        // PIN nunca se devuelve en listados: toEmpleadoResponse() omite
        // pinTerminal por defecto, solo obtenerPorId() lo rellena si ADMIN.
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
     * Aplica la Opción A acordada con Android:
     *   - ADMIN     → pinTerminal y email con valor real.
     *   - ENCARGADO → pinTerminal y email a null (la UI nunca los muestra).
     *
     * Códigos HTTP producidos:
     *   200 OK          → empleado encontrado y devuelto
     *   403 Forbidden   → rol insuficiente (EMPLEADO bloqueado por Spring Security)
     *   404 Not Found   → empleado con el id indicado no existe
     *
     * @param id             ID del empleado a consultar
     * @param authentication objeto Authentication del usuario llamante;
     *                       de él se extrae si tiene el rol ADMIN para
     *                       decidir si se expone el PIN y el email
     * @return EmpleadoResponse con los datos del empleado
     */
    @Transactional(readOnly = true)
    public EmpleadoResponse obtenerPorId(Long id, Authentication authentication) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Empleado con id " + id + " no encontrado"));
        EmpleadoResponse response = toEmpleadoResponse(empleado);
        // Opción A: PIN y email se exponen únicamente al rol ADMIN.
        // ENCARGADO recibe null en ambos campos (la UI Android ya filtra por rol).
        if (esAdmin(authentication)) {
            response.setPinTerminal(empleado.getPinTerminal());
            response.setEmail(empleado.getUsuario().getEmail());
        }
        return response;
    }

    /**
     * Comprueba si el {@link Authentication} dado tiene el rol ADMIN.
     *
     * Spring Security expone los roles como autoridades con prefijo
     * {@code ROLE_}, por eso se compara contra {@code ROLE_ADMIN}.
     *
     * @param authentication objeto de autenticación; puede ser null en
     *                       contextos de test sin SecurityContext
     * @return true si la autenticación existe y contiene ROLE_ADMIN
     */
    private boolean esAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
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
     * es permanente.
     *
     * Valida unicidad de codigoNfc excluyendo al propio empleado
     * (puede conservar su propio valor sin conflicto). PIN, DNI,
     * numeroEmpleado y fechaAlta no se modifican por este endpoint:
     * el PIN se regenera vía E65 (POST /{id}/regenerar-pin) y los
     * otros tres son inmutables (no existen en EmpleadoPatchRequest).
     *
     * Códigos HTTP producidos:
     *   200 OK          → perfil actualizado correctamente
     *   400 Bad Request → datos de entrada inválidos
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → empleado no encontrado
     *   409 Conflict    → NFC duplicado en otro empleado
     *                     (NFC: feature reservada para v2; DNI y
     *                     numeroEmpleado son inmutables, no aplican aquí)
     *
     * @param id      ID del empleado a actualizar
     * @param request campos a actualizar (todos opcionales)
     * @return EmpleadoResponse con los datos actualizados (sin PIN)
     */
    @Transactional
    public EmpleadoResponse actualizar(Long id, EmpleadoPatchRequest request) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
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
        // dni, numeroEmpleado y fechaAlta son inmutables
        // no existen en EmpleadoPatchRequest
        if (request.getCategoria() != null) {
            // categoria ya es CategoriaEmpleado en el DTO — sin valueOf()
            empleado.setCategoria(request.getCategoria());
        }
        if (request.getJornadaSemanalHoras() != null) {
            empleado.setJornadaSemanalHoras(request.getJornadaSemanalHoras());
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
        if (request.getCodigoNfc() != null) {
            if (empleadoRepository.existsByCodigoNfcAndIdNot(request.getCodigoNfc(), id)) {
                throw new ConflictException(
                        "El código NFC '" + request.getCodigoNfc() + "' ya está registrado");
            }
            empleado.setCodigoNfc(request.getCodigoNfc());
        }

        // toEmpleadoResponse() nunca rellena pinTerminal; solo obtenerPorId()
        // lo expone si el llamante es ADMIN (Opción A).
        return toEmpleadoResponse(empleadoRepository.save(empleado));
    }

    // ----------------------------------------------------------------
    // E17 — PATCH /api/v1/empleados/{id}/baja
    // RF-10: Dar de baja empleado
    // ----------------------------------------------------------------

    /**
     * Desactiva un empleado aplicando baja lógica (activo = false).
     *
     * @param id ID del empleado a desactivar
     * @return MensajeResponse confirmando la operación
     */
    @Transactional
    public MensajeResponse darDeBaja(Long id) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
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
     * @param id ID del empleado a reactivar
     * @return MensajeResponse confirmando la operación
     */
    @Transactional
    public MensajeResponse reactivar(Long id) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Empleado con id " + id + " no encontrado"));

        if (empleado.getActivo()) {
            throw new ConflictException(
                    "El empleado con id " + id + " ya está activo");
        }

        empleado.setActivo(true);
        empleadoRepository.save(empleado);

        return new MensajeResponse("Empleado reactivado correctamente");
    }

    // ----------------------------------------------------------------
    // E19 — GET /api/v1/empleados/estado
    // RF-15: Estado en tiempo real de los empleados
    // ----------------------------------------------------------------

    /**
     * Devuelve el estado en tiempo real de todos los empleados activos
     * para la fecha indicada.
     *
     * Delega en PresenciaService.obtenerParteDiario() que ya implementa
     * la lógica completa de clasificación por EstadoPresencia.
     *
     * @param fecha fecha de consulta
     * @return ParteDiarioResponse con contadores globales y detalle por empleado
     */
    @Transactional(readOnly = true)
    public ParteDiarioResponse obtenerEstado(LocalDate fecha) {
        return presenciaService.obtenerParteDiario(fecha);
    }

    // ----------------------------------------------------------------
    // E20 — GET /api/v1/empleados/export
    // RF-16: Exportar listado de empleados
    // ----------------------------------------------------------------

    /**
     * Exporta el listado de empleados activos en formato CSV o PDF.
     *
     * CSV: genera un archivo de texto con cabecera y una fila por empleado.
     * PDF: delega en PdfService para generar un documento con tabla estilizada.
     *
     * @param formato "csv" o "pdf"
     * @param activo  filtro por estado (null = solo activos por defecto)
     * @return bytes del archivo generado
     * @throws IllegalArgumentException si el formato no es "csv" ni "pdf"
     */
    @Transactional(readOnly = true)
    public byte[] exportar(String formato, Boolean activo) {
        boolean soloActivos = (activo == null) || activo;
        List<Empleado> empleados = soloActivos
                ? empleadoRepository.findByActivo(true)
                : empleadoRepository.findAll();

        if ("pdf".equalsIgnoreCase(formato)) {
            return pdfService.exportarEmpleados(empleados);
        } else if ("csv".equalsIgnoreCase(formato)) {
            return generarCsvEmpleados(empleados);
        } else {
            throw new IllegalArgumentException(
                    "Formato no soportado: '" + formato + "'. Use 'csv' o 'pdf'.");
        }
    }

    private byte[] generarCsvEmpleados(List<Empleado> empleados) {
        StringBuilder sb = new StringBuilder();
        sb.append("N\u00BA Empleado,Nombre,Apellido 1,Apellido 2,DNI,Categor\u00EDa,Jornada (h/sem),Fecha Alta\n");
        for (Empleado e : empleados) {
            sb.append(e.getNumeroEmpleado()).append(',')
              .append(escaparCsv(e.getNombre())).append(',')
              .append(escaparCsv(e.getApellido1())).append(',')
              .append(escaparCsv(e.getApellido2() != null ? e.getApellido2() : "")).append(',')
              .append(escaparCsv(e.getDni())).append(',')
              .append(e.getCategoria() != null ? e.getCategoria().name() : "").append(',')
              .append(e.getJornadaSemanalHoras()).append(',')
              .append(e.getFechaAlta() != null ? e.getFechaAlta().toString() : "")
              .append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escaparCsv(String valor) {
        if (valor == null) return "";
        if (valor.contains(",") || valor.contains("\"") || valor.contains("\n")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    // ----------------------------------------------------------------
    // E21 — GET /api/v1/empleados/me
    // RF-50: Perfil propio del empleado autenticado
    // ----------------------------------------------------------------

    /**
     * Devuelve el perfil del empleado autenticado.
     *
     * @param username username del usuario autenticado extraído de Authentication
     * @return EmpleadoResponse con el perfil propio (sin pinTerminal)
     */
    @Transactional(readOnly = true)
    public EmpleadoResponse obtenerMiPerfil(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(
                        "Usuario no encontrado: " + username));

        Empleado empleado = empleadoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NotFoundException(
                        "No existe perfil de empleado para el usuario '" + username + "'"));

        // PIN nunca se devuelve en /me
        return toEmpleadoResponse(empleado);
    }

    // ----------------------------------------------------------------
    // E65 — POST /api/v1/empleados/{id}/regenerar-pin
    // Regenerar PIN de terminal del empleado
    // ----------------------------------------------------------------

    /**
     * Regenera el PIN de terminal de un empleado y lo devuelve UNA sola vez.
     *
     * El nuevo PIN se genera mediante {@code generarPinUnico()}, garantizando
     * unicidad entre todos los empleados del sistema (RNF-R03). Una vez
     * devuelto en la respuesta, el PIN no se puede volver a consultar por API.
     * El ADMIN o ENCARGADO debe entregarlo al empleado en persona.
     *
     * Códigos HTTP producidos:
     *   200 OK        → PIN regenerado y devuelto correctamente
     *   401 Unauthorized → token JWT ausente o inválido
     *   403 Forbidden → rol insuficiente (requiere ADMIN o ENCARGADO)
     *   404 Not Found → empleado con el id indicado no existe
     *
     * @param id ID del empleado cuyo PIN se va a regenerar
     * @return RegenerarPinResponse con el ID del empleado y el nuevo PIN de 4 dígitos
     * @throws NotFoundException si no existe ningún empleado con el id indicado
     */
    @Transactional
    public RegenerarPinResponse regenerarPin(Long id) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Empleado con id " + id + " no encontrado"));

        String nuevoPin = generarPinUnico();
        empleado.setPinTerminal(nuevoPin);
        empleadoRepository.save(empleado);

        return new RegenerarPinResponse(empleado.getId(), nuevoPin);
    }

    // ----------------------------------------------------------------
    // Conversión entidad → DTO (uso interno)
    // ----------------------------------------------------------------

    /**
     * Genera un PIN de 4 dígitos aleatorio que no esté ya asignado a otro empleado.
     * En sistemas con muchos empleados (>9000) la probabilidad de colisión aumenta,
     * pero para el alcance de este proyecto (decenas de empleados) es despreciable.
     */
    private String generarPinUnico() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        String pin;
        do {
            pin = String.format("%04d", random.nextInt(10000));
        } while (empleadoRepository.existsByPinTerminal(pin));
        return pin;
    }

    private EmpleadoResponse toEmpleadoResponse(Empleado empleado) {
        EmpleadoResponse response = new EmpleadoResponse();
        response.setId(empleado.getId());
        response.setUsuarioId(empleado.getUsuario().getId());
        response.setNombre(empleado.getNombre());
        response.setApellido1(empleado.getApellido1());
        response.setApellido2(empleado.getApellido2());
        response.setDni(empleado.getDni());
        response.setNumeroEmpleado(empleado.getNumeroEmpleado());
        response.setFechaAlta(empleado.getFechaAlta());
        response.setCategoria(empleado.getCategoria());
        response.setJornadaSemanalHoras(empleado.getJornadaSemanalHoras());
        response.setJornadaDiariaMinutos(empleado.getJornadaDiariaMinutos());
        response.setDiasVacacionesAnuales(empleado.getDiasVacacionesAnuales());
        response.setDiasAsuntosPropiosAnuales(empleado.getDiasAsuntosPropiosAnuales());
        // pinTerminal y email se rellenan aparte en obtenerPorId() solo si
        // el llamante es ADMIN (Opción A). El resto de consumidores deja
        // ambos campos a null en el DTO base.
        response.setCodigoNfc(empleado.getCodigoNfc());
        response.setActivo(empleado.getActivo());
        return response;
    }
}
