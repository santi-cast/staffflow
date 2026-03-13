package com.staffflow.controller;

import com.staffflow.dto.request.EmpleadoPatchRequest;
import com.staffflow.dto.request.EmpleadoRequest;
import com.staffflow.dto.response.EmpleadoResponse;
import com.staffflow.dto.response.MensajeResponse;
import com.staffflow.service.EmpleadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller REST para la gestión del perfil laboral de los empleados.
 *
 * Cubre los endpoints E13-E21 del Grupo 4 (Gestión de Empleados).
 * Ruta base: /api/v1/empleados
 *
 * Control de acceso:
 *   - E13-E20: ADMIN y ENCARGADO. EMPLEADO recibe HTTP 403.
 *   - E21 (/me): exclusivo de EMPLEADO. ADMIN y ENCARGADO reciben HTTP 403.
 *
 * El controller extrae del JWT (objeto Authentication de Spring Security)
 * dos piezas de información que necesita el service:
 *   - El rol del usuario autenticado: para filtrar pinTerminal en E15.
 *   - El usuarioId del usuario autenticado: para identificar al empleado en E21.
 *
 * Responsabilidad única (SRP, decisión D del documento de endpoints):
 *   - Recibe la petición HTTP y extrae parámetros.
 *   - Valida el body con @Valid (Bean Validation — HTTP 400 automático).
 *   - Extrae rol y usuarioId del JWT cuando el service los necesita.
 *   - Delega toda la lógica en EmpleadoService.
 *   - Devuelve la respuesta con el código HTTP correcto.
 *   - Sin lógica de negocio en ningún método (RNF-M01).
 *
 * RF cubiertos: RF-08 a RF-16, RF-50.
 *
 * Nota sobre E19 y E20: los métodos están implementados en el controller
 * pero el service lanza UnsupportedOperationException hasta que se
 * implementen en Bloque 6 (E19) y Bloque 7 (E20).
 */
@RestController
@RequestMapping("/api/v1/empleados")
@RequiredArgsConstructor
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    // ----------------------------------------------------------------
    // E13 — POST /api/v1/empleados
    // RF-08: Crear perfil de empleado
    // ----------------------------------------------------------------

    /**
     * Crea el perfil laboral de un empleado vinculándolo a un usuario existente.
     *
     * Códigos HTTP:
     *   201 Created      → perfil creado correctamente
     *   400 Bad Request  → datos de entrada inválidos (Bean Validation)
     *   403 Forbidden    → rol insuficiente
     *   404 Not Found    → usuarioId no existe
     *   409 Conflict     → DNI, NSS, PIN o NFC duplicados
     *
     * @param request body JSON con los datos del perfil laboral
     * @return 201 Created con EmpleadoResponse
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<EmpleadoResponse> crear(@Valid @RequestBody EmpleadoRequest request) {
        EmpleadoResponse response = empleadoService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ----------------------------------------------------------------
    // E14 — GET /api/v1/empleados
    // RF-12, RF-14: Listar empleados con filtros
    // ----------------------------------------------------------------

    /**
     * Lista empleados con filtros opcionales y combinables.
     *
     * Sin query params devuelve todos los empleados activos.
     * El PIN no se incluye en el listado independientemente del rol.
     *   ?activo=true|false
     *   ?q=texto (busca en nombre, apellido1, apellido2, dni)
     *   ?categoria=OPERARIO|ADMINISTRATIVO|TECNICO|ENCARGADO|OTRO
     *
     * Códigos HTTP:
     *   200 OK          → lista devuelta (puede ser lista vacía)
     *   403 Forbidden   → rol insuficiente
     *
     * @param activo    filtro por estado — opcional
     * @param q         búsqueda por texto — opcional
     * @param categoria filtro por categoría — opcional
     * @return 200 OK con lista de EmpleadoResponse (sin pinTerminal)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<List<EmpleadoResponse>> listar(
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoria) {
        return ResponseEntity.ok(empleadoService.listar(activo, q, categoria));
    }

    // ----------------------------------------------------------------
    // E15 — GET /api/v1/empleados/{id}
    // RF-13: Perfil completo de empleado
    // IMPORTANTE: esta ruta debe declararse ANTES de /estado, /export y /me
    // para evitar que Spring confunda "{id}" con esos literales.
    // ----------------------------------------------------------------

    /**
     * Devuelve el perfil completo de un empleado.
     *
     * Extrae el rol del JWT y lo pasa al service para que filtre
     * pinTerminal según la Opción A acordada (D-017):
     *   - ADMIN     → pinTerminal con valor real
     *   - ENCARGADO → pinTerminal = null
     *
     * Códigos HTTP:
     *   200 OK          → empleado encontrado y devuelto
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → empleado no encontrado
     *
     * @param id             ID del empleado (path variable)
     * @param authentication objeto Authentication inyectado por Spring Security
     * @return 200 OK con EmpleadoResponse (pinTerminal según rol)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<EmpleadoResponse> obtenerPorId(
            @PathVariable Long id) {
        return ResponseEntity.ok(empleadoService.obtenerPorId(id));
    }

    // ----------------------------------------------------------------
    // E16 — PATCH /api/v1/empleados/{id}
    // RF-09: Editar perfil laboral
    // ----------------------------------------------------------------

    /**
     * Actualiza el perfil laboral de un empleado.
     *
     * Solo actualiza los campos enviados con valor no nulo (PATCH semántico).
     * El usuarioId no puede modificarse.
     *
     * Códigos HTTP:
     *   200 OK          → perfil actualizado correctamente
     *   400 Bad Request → datos inválidos
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → empleado no encontrado
     *   409 Conflict    → PIN, DNI o NSS duplicados
     *
     * @param id      ID del empleado (path variable)
     * @param request body JSON con los campos a actualizar (todos opcionales)
     * @return 200 OK con EmpleadoResponse actualizado (sin pinTerminal)
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<EmpleadoResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody EmpleadoPatchRequest request) {
        return ResponseEntity.ok(empleadoService.actualizar(id, request));
    }

    // ----------------------------------------------------------------
    // E17 — PATCH /api/v1/empleados/{id}/baja
    // RF-10: Dar de baja empleado
    // ----------------------------------------------------------------

    /**
     * Desactiva un empleado (baja lógica: activo = false).
     *
     * Códigos HTTP:
     *   200 OK          → empleado desactivado correctamente
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → empleado no encontrado
     *
     * @param id ID del empleado (path variable)
     * @return 200 OK con MensajeResponse
     */
    @PatchMapping("/{id}/baja")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<MensajeResponse> darDeBaja(@PathVariable Long id) {
        return ResponseEntity.ok(empleadoService.darDeBaja(id));
    }

    // ----------------------------------------------------------------
    // E18 — PATCH /api/v1/empleados/{id}/reactivar
    // RF-11: Reactivar empleado
    // ----------------------------------------------------------------

    /**
     * Reactiva un empleado previamente desactivado (activo = true).
     *
     * Códigos HTTP:
     *   200 OK          → empleado reactivado correctamente
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → empleado no encontrado
     *   409 Conflict    → el empleado ya estaba activo
     *
     * @param id ID del empleado (path variable)
     * @return 200 OK con MensajeResponse
     */
    @PatchMapping("/{id}/reactivar")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<MensajeResponse> reactivar(@PathVariable Long id) {
        return ResponseEntity.ok(empleadoService.reactivar(id));
    }

    // ----------------------------------------------------------------
    // E19 — GET /api/v1/empleados/estado
    // RF-15: Estado en tiempo real de los empleados
    // Pendiente de implementación en Bloque 6
    // ----------------------------------------------------------------

    /**
     * Devuelve el estado en tiempo real de todos los empleados activos
     * para una fecha dada.
     *
     * Pendiente de implementación en Bloque 6. El service lanza
     * UnsupportedOperationException hasta entonces, que GlobalExceptionHandler
     * convierte en HTTP 500 con mensaje descriptivo.
     *
     * @param fecha fecha para la consulta (defecto: hoy)
     * @return estado de empleados por fecha
     */
    @GetMapping("/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<Object> obtenerEstado(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        LocalDate fechaConsulta = (fecha != null) ? fecha : LocalDate.now();
        return ResponseEntity.ok(empleadoService.obtenerEstado(fechaConsulta));
    }

    // ----------------------------------------------------------------
    // E20 — GET /api/v1/empleados/export
    // RF-16: Exportar listado de empleados
    // Pendiente de implementación en Bloque 7
    // ----------------------------------------------------------------

    /**
     * Exporta el listado de empleados en formato CSV o PDF.
     *
     * Pendiente de implementación en Bloque 7. El service lanza
     * UnsupportedOperationException hasta entonces.
     *
     * @param formato "csv" o "pdf"
     * @param activo  filtro por estado — opcional
     * @return fichero binario (text/csv o application/pdf)
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<byte[]> exportar(
            @RequestParam String formato,
            @RequestParam(required = false) Boolean activo) {
        byte[] contenido = empleadoService.exportar(formato, activo);
        String contentType = "pdf".equalsIgnoreCase(formato)
                ? "application/pdf"
                : "text/csv";
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .body(contenido);
    }

    // ----------------------------------------------------------------
    // E21 — GET /api/v1/empleados/me
    // RF-50: Perfil propio del empleado autenticado
    // ----------------------------------------------------------------

    /**
     * Devuelve el perfil del empleado autenticado.
     *
     * Extrae el username del objeto Authentication de Spring Security
     * mediante authentication.getName() — disponible en el User estándar
     * de Spring sin necesidad de cast ni implementación propia (Opción B,
     * decisión de sesión 7, D-017). El service resuelve el id del usuario
     * a partir del username y localiza el perfil de empleado vinculado.
     *
     * El PIN nunca se incluye en /me independientemente del rol.
     *
     * Códigos HTTP:
     *   200 OK          → perfil devuelto correctamente
     *   403 Forbidden   → rol insuficiente (ADMIN/ENCARGADO bloqueados)
     *   404 Not Found   → usuario autenticado sin perfil de empleado
     *
     * @param authentication objeto Authentication inyectado por Spring Security
     * @return 200 OK con EmpleadoResponse (sin pinTerminal)
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLEADO')")
    public ResponseEntity<EmpleadoResponse> obtenerMiPerfil(Authentication authentication) {
        // authentication.getName() devuelve el username del User estándar de Spring Security.
        // Compatible con UserDetailsServiceImpl que no implementa getId() (Opción B, D-017).
        String username = authentication.getName();
        return ResponseEntity.ok(empleadoService.obtenerMiPerfil(username));
    }
}
