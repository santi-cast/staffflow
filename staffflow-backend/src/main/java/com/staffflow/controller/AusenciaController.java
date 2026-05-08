package com.staffflow.controller;

import com.staffflow.dto.request.AusenciaPatchRequest;
import com.staffflow.dto.request.AusenciaRangoRequest;
import com.staffflow.dto.request.AusenciaRequest;
import com.staffflow.dto.response.AusenciaResponse;
import com.staffflow.dto.response.PlanificacionVacApResponse;
import com.staffflow.service.AusenciaService;
import com.staffflow.service.InformeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Controller de planificación de ausencias.
 *
 * Ruta base: /api/v1/ausencias
 * Seguridad: JWT requerido en todos los endpoints.
 *
 * Endpoints implementados:
 *   E30 POST   /api/v1/ausencias       → crear ausencia planificada (ADMIN, ENCARGADO)
 *   E31 PATCH  /api/v1/ausencias/{id}  → modificar ausencia         (ADMIN, ENCARGADO)
 *   E32 DELETE /api/v1/ausencias/{id}  → eliminar ausencia          (ADMIN, ENCARGADO)
 *   E33 GET    /api/v1/ausencias       → listar con filtros         (ADMIN, ENCARGADO)
 *   E34 GET    /api/v1/ausencias/me    → ausencias propias          (EMPLEADO)
 *
 * Patrón /me: igual que FichajeController (E26).
 *   authentication.getName() devuelve el username del JWT.
 *   El service resuelve el empleadoId a partir del username.
 *
 * Único DELETE real del sistema (E32): ejecuta SQL DELETE solo si
 * procesado=false. Si procesado=true devuelve 409 (RNF-L01).
 *
 * RF cubiertos: RF-25, RF-26, RF-27, RF-28, RF-29, RF-52.
 */
@Tag(name = "Ausencias", description = "Planificación de ausencias y festivos")
@RestController
@RequestMapping("/api/v1/ausencias")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AusenciaController {

    /** Servicio que contiene toda la lógica de negocio de ausencias. */
    private final AusenciaService ausenciaService;

    /** Servicio de informes para generación del HTML de ausencias (E-ausencias). */
    private final InformeService informeService;

    // ------------------------------------------------------------------
    // E34 — GET /api/v1/ausencias/me
    // NOTA: declarado ANTES de /{id} para que Spring MVC no confunda
    //       "me" como valor de la variable de ruta {id}.
    // RF-52: ausencias propias del empleado autenticado
    // ------------------------------------------------------------------

    /**
     * Devuelve las ausencias planificadas del empleado autenticado (RF-52).
     *
     * Spring Security garantiza que solo el propio empleado puede llegar
     * aquí con rol EMPLEADO. No puede ver ausencias ajenas.
     *
     * Códigos HTTP:
     *   200 OK → lista de ausencias propias (puede ser vacía)
     *   403    → acceso denegado (rol incorrecto)
     *
     * @param desde          filtro opcional fecha inicio (YYYY-MM-DD)
     * @param hasta          filtro opcional fecha fin (YYYY-MM-DD)
     * @param authentication objeto de seguridad para extraer el username
     * @return 200 con lista de AusenciaResponse del empleado autenticado
     */
    @Operation(summary = "Mis ausencias planificadas",
               description = "Devuelve las ausencias planificadas del empleado autenticado. Accesible por EMPLEADO y ENCARGADO.")
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPLEADO','ENCARGADO')")
    public ResponseEntity<List<AusenciaResponse>> listarPropias(
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        // Mismo patrón que E26 en FichajeController
        String username = authentication.getName();
        return ResponseEntity.ok(ausenciaService.listarMias(username, desde, hasta));
    }

    // ------------------------------------------------------------------
    // E-ausencias — GET /api/v1/ausencias/me/informe
    // Informe HTML de ausencias propias (ejecutadas + planificadas)
    // NOTA: declarado ANTES de /me y /{id} para evitar conflictos de ruta
    // ------------------------------------------------------------------

    /**
     * Devuelve el informe HTML de ausencias del empleado autenticado.
     *
     * Combina planificacion_ausencias y fichajes tipo ausencia.
     * Por defecto muestra el año en curso completo.
     * filtro=VACACIONES_AP muestra solo vacaciones y asuntos propios.
     *
     * @param desde  fecha de inicio (defecto: 1 enero del año actual)
     * @param hasta  fecha de fin (defecto: 31 diciembre del año actual)
     * @param filtro "TODAS" (defecto) o "VACACIONES_AP"
     * @param authentication objeto de seguridad para extraer el username
     * @return 200 con HTML del informe de ausencias
     */
    @Operation(summary = "Informe HTML de mis ausencias",
               description = "Genera el informe HTML de ausencias del empleado autenticado. Combina planificadas y ejecutadas.")
    @GetMapping("/me/informe")
    @PreAuthorize("hasAnyRole('EMPLEADO','ENCARGADO')")
    public ResponseEntity<String> informeAusenciasMe(
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "TODAS") String filtro,
            Authentication authentication) {

        int anio = java.time.LocalDate.now().getYear();
        LocalDate desdeEfectivo = desde != null ? desde : LocalDate.of(anio, 1, 1);
        LocalDate hastaEfectivo = hasta != null ? hasta : LocalDate.of(anio, 12, 31);

        String html = informeService.informeAusenciasMe(
                authentication.getName(), desdeEfectivo, hastaEfectivo, filtro);

        return ResponseEntity.ok()
                .header("Content-Type", "text/html;charset=UTF-8")
                .body(html);
    }

    // ------------------------------------------------------------------
    // E-ausencias-id — GET /api/v1/ausencias/{empleadoId}/informe
    // Informe HTML de ausencias de un empleado por id (ADMIN/ENCARGADO)
    // ------------------------------------------------------------------

    /**
     * Devuelve el informe HTML de ausencias de un empleado concreto.
     * Misma lógica que /me/informe pero accesible por ADMIN y ENCARGADO.
     *
     * @param empleadoId id del empleado
     * @param desde  fecha de inicio (defecto: 1 enero del año actual)
     * @param hasta  fecha de fin (defecto: 31 diciembre del año actual)
     * @param filtro "TODAS" (defecto) o "VACACIONES_AP"
     * @return 200 con HTML del informe de ausencias
     */
    @GetMapping("/{empleadoId}/informe")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<String> informeAusenciasEmpleado(
            @PathVariable Long empleadoId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "TODAS") String filtro) {

        int anio = java.time.LocalDate.now().getYear();
        LocalDate desdeEfectivo = desde != null ? desde : LocalDate.of(anio, 1, 1);
        LocalDate hastaEfectivo = hasta != null ? hasta : LocalDate.of(anio, 12, 31);

        String html = informeService.informeAusenciasEmpleado(
                empleadoId, desdeEfectivo, hastaEfectivo, filtro);

        return ResponseEntity.ok()
                .header("Content-Type", "text/html;charset=UTF-8")
                .body(html);
    }

    // ------------------------------------------------------------------
    // E30 — POST /api/v1/ausencias
    // RF-25: ausencia individual | RF-26: festivo global (empleadoId null)
    // ------------------------------------------------------------------

    /**
     * Planifica una ausencia futura para un empleado (RF-25, RF-26).
     *
     * Si empleadoId es null en el body, crea un festivo global que
     * ProcesoDiario aplicará a todos los empleados activos ese día (RF-26).
     *
     * Códigos HTTP:
     *   201 Created → ausencia creada con procesado=false
     *   400         → datos inválidos (@Valid)
     *   403         → rol insuficiente
     *   404         → empleadoId no existe
     *   409         → ya existe ausencia ese día para ese empleado
     *
     * @param request        datos de la ausencia a planificar
     * @param authentication objeto de seguridad para auditoría
     * @return 201 con AusenciaResponse de la ausencia creada
     */
    @Operation(summary = "Planificar rango de ausencias",
               description = "Crea una ausencia por cada día del rango [fechaDesde, fechaHasta]. " +
                             "Si hay conflictos y sobrescribir=false devuelve 409 con fechasConflictivas.")
    @PostMapping("/rango")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<List<AusenciaResponse>> crearRango(
            @Valid @RequestBody AusenciaRangoRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        List<AusenciaResponse> creadas = ausenciaService.crearRango(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(creadas);
    }

    @Operation(summary = "Planificar ausencia",
               description = "Crea una ausencia planificada. Si empleadoId es null, crea un festivo global (RF-26).")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<AusenciaResponse> crear(
            @Valid @RequestBody AusenciaRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        AusenciaResponse response = ausenciaService.crear(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ------------------------------------------------------------------
    // E33 — GET /api/v1/ausencias
    // RF-29: listar con filtros opcionales
    // ------------------------------------------------------------------

    /**
     * Lista ausencias planificadas con filtros opcionales y combinables (RF-29).
     *
     * Sin filtros devuelve todas las ausencias de todos los empleados,
     * incluyendo festivos globales (empleado_id = null).
     *
     * Códigos HTTP:
     *   200 OK → lista de ausencias (puede ser vacía)
     *   403    → rol insuficiente
     *
     * @param empleadoId filtro opcional por ID de empleado
     * @param desde      filtro opcional fecha inicio (YYYY-MM-DD)
     * @param hasta      filtro opcional fecha fin (YYYY-MM-DD)
     * @param procesado  filtro opcional por estado procesado (true/false)
     * @return 200 con lista de AusenciaResponse
     */
    @Operation(summary = "Listar ausencias con filtros",
               description = "Lista ausencias con filtros opcionales. Sin filtros devuelve todas, incluyendo festivos globales.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<List<AusenciaResponse>> listar(
            @RequestParam(required = false) Long empleadoId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Boolean procesado) {

        return ResponseEntity.ok(ausenciaService.listar(empleadoId, desde, hasta, procesado));
    }

    // ------------------------------------------------------------------
    // E-planificacion-vac-ap — GET /api/v1/ausencias/planificacion-vac-ap
    // Días pendientes de planificar para vacaciones y asuntos propios
    // NOTA: declarado ANTES de /{id} para evitar conflicto de ruta
    // ------------------------------------------------------------------

    /**
     * Devuelve los días pendientes de planificar para vacaciones y asuntos
     * propios de un empleado en un año concreto.
     *
     * Si no existe SaldoAnual para ese año, lo crea on-demand con el derecho
     * del empleado. El flag anioFuturoSinCierre=true indica que los pendientes
     * del año anterior aún no están incluidos (cierre anual no ejecutado).
     *
     * @param empleadoId id del empleado
     * @param anio       año a consultar (defecto: año actual)
     * @return desglose de disponibles, planificados y pendientesPlanificar para vac y AP
     */
    @Operation(summary = "Días pendientes de planificar vacaciones y AP",
               description = "Calcula los días pendientes de planificar para vacaciones y asuntos propios de un empleado.")
    @GetMapping("/planificacion-vac-ap")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<PlanificacionVacApResponse> getPlanificacionVacAp(
            @RequestParam Long empleadoId,
            @RequestParam(required = false) Integer anio) {

        int anioConsulta = anio != null ? anio : java.time.LocalDate.now().getYear();
        return ResponseEntity.ok(ausenciaService.getPlanificacionVacAp(empleadoId, anioConsulta));
    }

    // ------------------------------------------------------------------
    // E31 — PATCH /api/v1/ausencias/{id}
    // RF-27: modificar ausencia planificada
    // ------------------------------------------------------------------

    /**
     * Modifica una ausencia planificada (RF-27).
     *
     * Solo se puede modificar si procesado=false. Si procesado=true
     * la ausencia ya tiene un fichaje asociado y hay que modificar
     * ese fichaje directamente mediante E23.
     *
     * Códigos HTTP:
     *   200 OK → ausencia actualizada
     *   400    → datos inválidos
     *   403    → rol insuficiente
     *   404    → ausencia no encontrada
     *   409    → procesado=true, modifica el fichaje directamente
     *
     * @param id      ID de la ausencia a modificar
     * @param request campos a actualizar (PATCH selectivo)
     * @return 200 con AusenciaResponse actualizado
     */
    @Operation(summary = "Modificar ausencia planificada",
               description = "Modifica una ausencia. Solo posible si procesado=false.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<AusenciaResponse> actualizar(
            @PathVariable Long id,
            @RequestBody AusenciaPatchRequest request,
            Authentication authentication) {

        // username necesario para aplicar restriccion D-026 en el service
        String username = authentication.getName();
        return ResponseEntity.ok(ausenciaService.actualizar(id, request, username));
    }

    // ------------------------------------------------------------------
    // E32 — DELETE /api/v1/ausencias/{id}
    // RF-28: único DELETE real del sistema
    // ------------------------------------------------------------------

    /**
     * Elimina una ausencia planificada (RF-28).
     *
     * Es el único endpoint DELETE real del sistema que ejecuta SQL DELETE.
     * Solo funciona si procesado=false. Si procesado=true devuelve 409:
     * la ausencia ya tiene un fichaje asociado que es inmutable (RNF-L01).
     *
     * Códigos HTTP:
     *   204 No Content → ausencia eliminada
     *   403            → rol insuficiente
     *   404            → ausencia no encontrada
     *   409            → procesado=true, no se puede eliminar
     *
     * @param id ID de la ausencia a eliminar
     * @return 204 sin body
     */
    @Operation(summary = "Eliminar ausencia planificada",
               description = "Elimina una ausencia. Único DELETE real del sistema. Solo posible si procesado=false.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {

        ausenciaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
