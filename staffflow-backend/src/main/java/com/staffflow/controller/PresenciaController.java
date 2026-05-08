package com.staffflow.controller;

import com.staffflow.dto.response.DetallePresenciaResponse;
import com.staffflow.dto.response.ParteDiarioResponse;
import com.staffflow.dto.response.SinJustificarResponse;
import com.staffflow.service.PresenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador de control de presencia en tiempo real.
 *
 * Expone vistas calculadas del estado de los empleados para una fecha dada.
 * No crea ni modifica ningún dato: todos los endpoints son GET de solo lectura
 * que agregan información de fichajes, pausas y ausencias planificadas.
 *
 * Separado de FichajeController por responsabilidad única (decisión de diseño
 * Bloque 6): los fichajes son el registro histórico inmutable (RD-ley 8/2019),
 * la presencia es una vista calculada en tiempo real. Mezclarlos en el mismo
 * controller confundiría las responsabilidades y dificultaría el mantenimiento.
 *
 * Ruta base: /api/v1/presencia
 *
 * ORDEN DE DECLARACIÓN CRÍTICO:
 *   E37 GET /parte-diario/me debe declararse ANTES de cualquier ruta con
 *   variable de path /{id} para evitar ambigüedad en Spring MVC.
 *   En este controller no hay rutas con /{id} pero se mantiene la convención
 *   del proyecto: /me siempre antes que rutas parametrizadas.
 *
 * @author Santiago Castillo
 */
@RestController
@RequestMapping("/api/v1/presencia")
@Tag(name = "Presencia", description = "Control de presencia en tiempo real (E35-E37)")
@SecurityRequirement(name = "bearerAuth")
public class PresenciaController {

    private final PresenciaService presenciaService;

    /**
     * Constructor con inyección de dependencias.
     * Inyección por constructor: práctica recomendada en Spring
     * para testabilidad y claridad de dependencias.
     */
    public PresenciaController(PresenciaService presenciaService) {
        this.presenciaService = presenciaService;
    }

    // ----------------------------------------------------------------
    // E37 — GET /api/v1/presencia/parte-diario/me
    // Declarado PRIMERO por convención /me antes de rutas parametrizadas
    // ----------------------------------------------------------------

    /**
     * E37 — Devuelve el estado de presencia del empleado autenticado.
     *
     * El empleado puede consultar su propia situación del día desde la app:
     * si está fichado, en pausa, tiene ausencia registrada o planificada.
     * Por defecto devuelve el estado de hoy; ?fecha permite consultar otro día.
     *
     * El username se extrae del JWT mediante el objeto Authentication de Spring
     * Security, que lo inyecta automáticamente en el parámetro del método.
     * El service resuelve internamente el empleadoId a partir del username.
     *
     * RF-54. Rol: EMPLEADO.
     *
     * @param fecha          fecha a consultar (por defecto hoy)
     * @param authentication objeto de Spring Security con el JWT del empleado
     * @return estado de presencia del empleado para la fecha indicada
     */
    @GetMapping("/parte-diario/me")
    @PreAuthorize("hasAnyRole('EMPLEADO','ENCARGADO')")
    @Operation(summary = "Estado propio del día",
               description = "Devuelve el estado de presencia del empleado autenticado para la fecha indicada. " +
                             "Por defecto hoy. Solo accesible por el propio empleado (RF-54).")
    public ResponseEntity<DetallePresenciaResponse> obtenerMiPresencia(
            @Parameter(description = "Fecha a consultar en formato YYYY-MM-DD. Por defecto hoy.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication authentication) {

        LocalDate fechaConsulta = fecha != null ? fecha : LocalDate.now();
        String username = authentication.getName();
        return ResponseEntity.ok(presenciaService.obtenerMiPresencia(username, fechaConsulta));
    }

    // ----------------------------------------------------------------
    // E35 — GET /api/v1/presencia/parte-diario
    // ----------------------------------------------------------------

    /**
     * E35 — Devuelve el parte diario completo de una fecha.
     *
     * Muestra el estado de presencia de todos los empleados activos:
     * contadores globales (fichados, enPausa, ausencias, sinJustificar)
     * más el detalle individual de cada empleado ordenado alfabéticamente.
     * Es el panel principal de control de presencia del encargado.
     *
     * Por defecto devuelve el parte de hoy; ?fecha permite consultar
     * partes de fechas anteriores para revisión histórica.
     *
     * RF-30. Roles: ADMIN, ENCARGADO.
     *
     * @param fecha fecha del parte en formato YYYY-MM-DD (por defecto hoy)
     * @return parte diario con contadores globales y detalle por empleado
     */
    @GetMapping("/parte-diario")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    @Operation(summary = "Parte diario completo",
               description = "Devuelve el estado de presencia de todos los empleados activos para la fecha " +
                             "indicada. Incluye contadores globales y detalle individual. Por defecto hoy (RF-30).")
    public ResponseEntity<ParteDiarioResponse> obtenerParteDiario(
            @Parameter(description = "Fecha del parte en formato YYYY-MM-DD. Por defecto hoy.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        LocalDate fechaConsulta = fecha != null ? fecha : LocalDate.now();
        return ResponseEntity.ok(presenciaService.obtenerParteDiario(fechaConsulta));
    }

    // ----------------------------------------------------------------
    // E36 — GET /api/v1/presencia/sin-justificar
    // ----------------------------------------------------------------

    /**
     * E36 — Devuelve los empleados sin justificación de presencia para una fecha.
     *
     * Lista los empleados que no tienen ningún registro para el día indicado:
     * ni fichaje, ni ausencia registrada, ni ausencia planificada. Son los
     * empleados que requieren atención inmediata del encargado.
     *
     * Es un subconjunto del parte diario filtrado por SIN_JUSTIFICAR,
     * expuesto como endpoint independiente para facilitar la vista rápida
     * en el panel del encargado sin cargar el detalle completo de todos
     * los empleados.
     *
     * Si existe un festivo global para la fecha devuelve lista vacía:
     * todos los empleados tienen AUSENCIA_PLANIFICADA ese día.
     *
     * RF-31. Roles: ADMIN, ENCARGADO.
     *
     * @param fecha fecha a consultar en formato YYYY-MM-DD (por defecto hoy)
     * @return lista de empleados sin justificación (puede ser vacía)
     */
    @GetMapping("/sin-justificar")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    @Operation(summary = "Empleados sin justificar",
               description = "Lista los empleados sin fichaje ni ausencia registrada para la fecha indicada. " +
                             "Requieren atención del encargado. Lista vacía si hay festivo global (RF-31).")
    public ResponseEntity<List<SinJustificarResponse>> obtenerSinJustificar(
            @Parameter(description = "Fecha a consultar en formato YYYY-MM-DD. Por defecto hoy.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        LocalDate fechaConsulta = fecha != null ? fecha : LocalDate.now();
        return ResponseEntity.ok(presenciaService.obtenerSinJustificar(fechaConsulta));
    }
}
