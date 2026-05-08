package com.staffflow.controller;

import com.staffflow.dto.request.FichajeRequest;
import com.staffflow.dto.request.FichajePatchRequest;
import com.staffflow.dto.response.FichajeResponse;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.service.FichajeService;
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
 * Controller de gestión de fichajes.
 *
 * Ruta base: /api/v1/fichajes
 * Seguridad: JWT requerido en todos los endpoints.
 *
 * Endpoints implementados:
 *   E22 POST   /api/v1/fichajes             → crear fichaje manual  (ADMIN, ENCARGADO)
 *   E23 PATCH  /api/v1/fichajes/{id}        → modificar fichaje     (ADMIN, ENCARGADO)
 *   E24 GET    /api/v1/fichajes             → listar con filtros    (ADMIN, ENCARGADO)
 *   E25 GET    /api/v1/fichajes/incompletos → jornadas sin salida   (ADMIN, ENCARGADO)
 *   E26 GET    /api/v1/fichajes/me          → historial propio      (EMPLEADO)
 *
 * Patrón de autenticación (igual que EmpleadoController, D-017 Opción B):
 *   authentication.getName() devuelve el username del usuario autenticado.
 *   El service resuelve usuarioId y empleadoId a partir del username.
 *   Sin casting, sin JwtTokenProvider en el controller.
 *
 * El controller no contiene lógica de negocio: recibe la petición,
 * delega en FichajeService y devuelve la respuesta. (RNF-M01)
 *
 * RF cubiertos: RF-17 a RF-21, RF-51.
 */
@Tag(name = "Fichajes", description = "Registro y consulta de fichajes de jornada")
@RestController
@RequestMapping("/api/v1/fichajes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FichajeController {

    // ---------------------------------------------------------------
    // Dependencias
    // ---------------------------------------------------------------

    /** Servicio que contiene toda la lógica de negocio de fichajes. */
    private final FichajeService fichajeService;

    // ---------------------------------------------------------------
    // E22 — POST /api/v1/fichajes
    // RF-17: Registrar fichaje manual
    // ---------------------------------------------------------------

    /**
     * Registra un fichaje manual para un empleado (RF-17).
     *
     * Usado para correcciones o para registrar ausencias retroactivas.
     * Las observaciones son obligatorias (RNF-L02): todo fichaje manual
     * debe dejar constancia del motivo. El service valida esto.
     *
     * El username del autenticado se pasa al service para auditoría:
     * el service resuelve el usuarioId y lo almacena en usuario_id del fichaje.
     *
     * Códigos HTTP:
     *   201 Created → fichaje creado correctamente
     *   400         → observaciones vacías o datos inválidos (@Valid)
     *   403         → rol insuficiente
     *   404         → empleadoId no existe
     *   409         → ya existe fichaje ese día para ese empleado
     *
     * @param request        datos del fichaje (validados con @Valid)
     * @param authentication objeto de seguridad inyectado por Spring Security
     * @return 201 con FichajeResponse del fichaje creado
     */
    @Operation(summary = "Registrar fichaje manual",
               description = "Crea un fichaje manual para un empleado. Observaciones obligatorias (RNF-L02).")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<FichajeResponse> crear(
            @Valid @RequestBody FichajeRequest request,
            Authentication authentication) {

        // authentication.getName() devuelve el username del User estándar de Spring Security.
        // El service resuelve el usuarioId a partir del username (D-017, Opción B).
        String username = authentication.getName();
        FichajeResponse response = fichajeService.crear(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ---------------------------------------------------------------
    // E23 — PATCH /api/v1/fichajes/{id}
    // RF-18: Modificar fichaje
    // ---------------------------------------------------------------

    /**
     * Modifica un fichaje existente (RF-18).
     *
     * Campos modificables: horaEntrada, horaSalida, tipo, observaciones.
     * Todos son opcionales — solo se actualizan los que llegan con valor.
     * Observaciones obligatorias (RNF-L02).
     *
     * Si se modifican las horas, el service recalcula jornadaEfectivaMinutos
     * usando el totalPausasMinutos ya almacenado (E23 no toca pausas).
     *
     * Códigos HTTP:
     *   200 OK → fichaje modificado con jornada recalculada
     *   400    → observaciones vacías
     *   403    → rol insuficiente
     *   404    → fichaje no encontrado
     *
     * @param id      ID del fichaje a modificar (path variable)
     * @param request campos a modificar (observaciones obligatorio, resto opcional)
     * @return 200 con FichajeResponse actualizado
     */
    @Operation(summary = "Modificar fichaje",
               description = "Modifica un fichaje existente. Observaciones obligatorias. Recalcula jornada si se modifican horas.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<FichajeResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody FichajePatchRequest request,
            Authentication authentication) {

        // username necesario para aplicar restriccion D-026 en el service
        String username = authentication.getName();
        FichajeResponse response = fichajeService.actualizar(id, request, username);
        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------------------------
    // E24 — GET /api/v1/fichajes
    // RF-19, RF-20: Listar fichajes con filtros
    // ---------------------------------------------------------------

    /**
     * Lista fichajes con filtros opcionales y combinables (RF-19, RF-20).
     *
     * Filtros disponibles (todos opcionales y combinables):
     *   ?empleadoId=number  → filtra por empleado concreto
     *   ?desde=YYYY-MM-DD   → fecha inicio del rango (inclusivo)
     *   ?hasta=YYYY-MM-DD   → fecha fin del rango (inclusivo)
     *   ?tipo=NORMAL|...    → filtra por tipo de jornada
     *
     * Sin filtros → devuelve todos los fichajes de todos los empleados.
     *
     * Códigos HTTP:
     *   200 OK → lista de fichajes (puede ser vacía)
     *   403    → rol insuficiente
     *
     * @param empleadoId filtro opcional por ID de empleado
     * @param desde      filtro opcional fecha inicio (formato YYYY-MM-DD)
     * @param hasta      filtro opcional fecha fin (formato YYYY-MM-DD)
     * @param tipo       filtro opcional por TipoFichaje
     * @return 200 con lista de FichajeResponse
     */
    @Operation(summary = "Listar fichajes con filtros",
               description = "Lista fichajes con filtros opcionales y combinables. Sin filtros devuelve todos.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<List<FichajeResponse>> listar(
            @RequestParam(required = false) Long empleadoId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoFichaje tipo) {

        return ResponseEntity.ok(fichajeService.listar(empleadoId, desde, hasta, tipo));
    }

    // ---------------------------------------------------------------
    // E25 — GET /api/v1/fichajes/incompletos
    // RF-21: Fichajes sin hora de salida
    // NOTA: declarado antes de /{id} para que Spring MVC no confunda
    //       "incompletos" como valor de la variable {id}.
    // ---------------------------------------------------------------

    /**
     * Lista fichajes con entrada registrada pero sin salida (RF-21).
     *
     * Permite al encargado detectar al final del día qué empleados
     * han olvidado fichar la salida (hora_salida = NULL).
     *
     * El parámetro fecha es opcional: si no se proporciona, usa hoy.
     *
     * Códigos HTTP:
     *   200 OK → lista de fichajes incompletos (puede ser vacía)
     *   403    → rol insuficiente
     *
     * @param fecha fecha a consultar en formato YYYY-MM-DD (defecto: hoy)
     * @return 200 con lista de FichajeResponse donde horaSalida = null
     */
    @Operation(summary = "Listar fichajes incompletos",
               description = "Lista empleados con entrada registrada y sin salida para una fecha. Defecto: hoy.")
    @GetMapping("/incompletos")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<List<FichajeResponse>> listarIncompletos(
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        return ResponseEntity.ok(fichajeService.listarIncompletos(fecha));
    }

    // ---------------------------------------------------------------
    // E26 — GET /api/v1/fichajes/me
    // RF-51: Historial de fichajes del empleado autenticado
    // NOTA: declarado antes de /{id} para que Spring MVC no confunda
    //       "me" como valor de la variable {id}.
    // ---------------------------------------------------------------

    /**
     * Lista los fichajes del empleado autenticado (RF-51).
     *
     * El username se extrae de authentication.getName() — igual que en
     * EmpleadoController.obtenerMiPerfil() (D-017, Opción B).
     * El service resuelve el empleadoId a partir del username.
     *
     * Spring Security garantiza que el rol EMPLEADO solo puede llegar
     * aquí con su propio token: no puede ver fichajes ajenos.
     *
     * Filtros opcionales: desde, hasta, tipo — misma lógica que E24.
     *
     * Códigos HTTP:
     *   200 OK → lista de fichajes propios (puede ser vacía)
     *   403    → acceso denegado (rol incorrecto)
     *
     * @param desde          filtro opcional fecha inicio
     * @param hasta          filtro opcional fecha fin
     * @param tipo           filtro opcional por tipo
     * @param authentication objeto de seguridad para extraer username
     * @return 200 con lista de FichajeResponse del empleado autenticado
     */
    @Operation(summary = "Mis fichajes",
               description = "Lista los fichajes del empleado autenticado. Solo accesible por EMPLEADO.")
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPLEADO','ENCARGADO')")
    public ResponseEntity<List<FichajeResponse>> listarPropios(
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoFichaje tipo,
            Authentication authentication) {

        // authentication.getName() devuelve el username — mismo patrón que E21 (D-017 Opción B)
        String username = authentication.getName();
        return ResponseEntity.ok(fichajeService.listarPropios(username, desde, hasta, tipo));
    }
}
