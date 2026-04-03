package com.staffflow.controller;

import com.staffflow.dto.request.AusenciaPatchRequest;
import com.staffflow.dto.request.AusenciaRequest;
import com.staffflow.dto.response.AusenciaResponse;
import com.staffflow.service.AusenciaService;
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
               description = "Devuelve las ausencias planificadas del empleado autenticado. Solo accesible por EMPLEADO.")
    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLEADO')")
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
