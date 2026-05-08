package com.staffflow.controller;

import com.staffflow.domain.enums.TipoPausa;
import com.staffflow.dto.request.PausaPatchRequest;
import com.staffflow.dto.request.PausaRequest;
import com.staffflow.dto.response.PausaResponse;
import com.staffflow.service.PausaService;
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
 * Controller de gestión de pausas.
 *
 * Ruta base: /api/v1/pausas
 * Seguridad: JWT requerido en todos los endpoints.
 *
 * Endpoints implementados:
 *   E27 POST  /api/v1/pausas      → crear pausa manual  (ADMIN, ENCARGADO)
 *   E28 PATCH /api/v1/pausas/{id} → cerrar/modificar    (ADMIN, ENCARGADO)
 *   E29 GET   /api/v1/pausas      → listar con filtros  (ADMIN, ENCARGADO)
 *   E55 GET   /api/v1/pausas/me   → pausas propias      (EMPLEADO, ENCARGADO)
 *
 * Patrón de autenticación (igual que FichajeController y EmpleadoController):
 *   authentication.getName() → username → service resuelve usuarioId.
 *
 * El controller no contiene lógica de negocio (RNF-M01).
 *
 * RF cubiertos: RF-22, RF-23, RF-24.
 */
@Tag(name = "Pausas", description = "Registro y consulta de pausas de jornada")
@RestController
@RequestMapping("/api/v1/pausas")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PausaController {

    /** Servicio que contiene toda la lógica de negocio de pausas. */
    private final PausaService pausaService;

    // ---------------------------------------------------------------
    // E27 — POST /api/v1/pausas
    // RF-22: Registrar pausa manual
    // ---------------------------------------------------------------

    /**
     * Registra una pausa manual para un empleado (RF-22).
     *
     * Usado para registrar pausas retroactivamente o cuando el terminal
     * no estaba disponible. horaFin es opcional: si no se proporciona,
     * la pausa queda activa (hora_fin = NULL).
     *
     * El service verifica que no haya ya una pausa activa ese día
     * para ese empleado (409 si la hay).
     *
     * Códigos HTTP:
     *   201 Created → pausa creada correctamente
     *   400         → datos inválidos (@Valid)
     *   403         → rol insuficiente
     *   404         → empleadoId no existe
     *   409         → ya hay pausa activa ese día para ese empleado
     *
     * @param request        datos de la pausa (validados con @Valid)
     * @param authentication objeto de seguridad inyectado por Spring Security
     * @return 201 con PausaResponse de la pausa creada
     */
    @Operation(summary = "Registrar pausa manual",
               description = "Crea una pausa manual para un empleado. horaFin opcional (null = pausa activa).")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<PausaResponse> crear(
            @Valid @RequestBody PausaRequest request,
            Authentication authentication) {

        // authentication.getName() devuelve el username (D-017, Opción B)
        String username = authentication.getName();
        PausaResponse response = pausaService.crear(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ---------------------------------------------------------------
    // E28 — PATCH /api/v1/pausas/{id}
    // RF-23: Cerrar o modificar pausa
    // ---------------------------------------------------------------

    /**
     * Cierra o modifica una pausa existente (RF-23).
     *
     * Caso A — Cerrar pausa activa: se proporciona horaFin en el request.
     *   El service calcula duracionMinutos con Math.floor y actualiza
     *   totalPausasMinutos en el fichaje del día (si la pausa no es
     *   AUSENCIA_RETRIBUIDA).
     *
     * Caso B — Modificar observaciones: solo se envía observaciones.
     *
     * Observaciones obligatorias (RNF-L02).
     *
     * Códigos HTTP:
     *   200 OK → pausa actualizada con duración calculada
     *   400    → observaciones vacías
     *   403    → rol insuficiente
     *   404    → pausa no encontrada
     *
     * @param id      ID de la pausa a modificar (path variable)
     * @param request campos a modificar (observaciones obligatorio, horaFin opcional)
     * @return 200 con PausaResponse actualizada
     */
    @Operation(summary = "Cerrar o modificar pausa",
               description = "Cierra una pausa activa (horaFin) o modifica sus observaciones. Observaciones obligatorias.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<PausaResponse> cerrar(
            @PathVariable Long id,
            @Valid @RequestBody PausaPatchRequest request,
            Authentication authentication) {

        // username necesario para aplicar restriccion D-026 en el service
        String username = authentication.getName();
        PausaResponse response = pausaService.cerrar(id, request, username);
        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------------------------
    // E55 — GET /api/v1/pausas/me
    // RF: Pausas propias del empleado autenticado
    // NOTA: declarado ANTES de GET / para que Spring MVC no trate
    //       "me" como parámetro de query.
    // ---------------------------------------------------------------

    /**
     * Lista las pausas del empleado autenticado en un rango de fechas.
     *
     * Mismo patrón que FichajeController.listarPropios() (E26):
     * username → service resuelve empleadoId.
     *
     * Códigos HTTP:
     *   200 OK → lista de pausas propias (puede ser vacía)
     *   403    → acceso denegado (rol incorrecto)
     *
     * @param desde          filtro opcional fecha inicio
     * @param hasta          filtro opcional fecha fin
     * @param authentication objeto de seguridad para extraer username
     * @return 200 con lista de PausaResponse del empleado autenticado
     */
    @Operation(summary = "Mis pausas",
               description = "Lista las pausas del empleado autenticado. Accesible por EMPLEADO y ENCARGADO.")
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPLEADO','ENCARGADO')")
    public ResponseEntity<List<PausaResponse>> listarPropias(
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        String username = authentication.getName();
        return ResponseEntity.ok(pausaService.listarPropios(username, desde, hasta));
    }

    // ---------------------------------------------------------------
    // E29 — GET /api/v1/pausas
    // RF-24: Listar pausas con filtros
    // ---------------------------------------------------------------

    /**
     * Lista pausas con filtros opcionales y combinables (RF-24).
     *
     * Filtros disponibles (todos opcionales y combinables):
     *   ?empleadoId=number          → filtra por empleado concreto
     *   ?desde=YYYY-MM-DD           → fecha inicio del rango (inclusivo)
     *   ?hasta=YYYY-MM-DD           → fecha fin del rango (inclusivo)
     *   ?tipoPausa=COMIDA|DESCANSO|AUSENCIA_RETRIBUIDA|OTROS
     *
     * Sin filtros → devuelve todas las pausas de todos los empleados.
     *
     * Códigos HTTP:
     *   200 OK → lista de pausas (puede ser vacía)
     *   403    → rol insuficiente
     *
     * @param empleadoId filtro opcional por ID de empleado
     * @param desde      filtro opcional fecha inicio (formato YYYY-MM-DD)
     * @param hasta      filtro opcional fecha fin (formato YYYY-MM-DD)
     * @param tipoPausa  filtro opcional por TipoPausa
     * @return 200 con lista de PausaResponse
     */
    @Operation(summary = "Listar pausas con filtros",
               description = "Lista pausas con filtros opcionales y combinables. Sin filtros devuelve todas.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<List<PausaResponse>> listar(
            @RequestParam(required = false) Long empleadoId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoPausa tipoPausa) {

        return ResponseEntity.ok(pausaService.listar(empleadoId, desde, hasta, tipoPausa));
    }
}
