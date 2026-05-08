package com.staffflow.controller;

import com.staffflow.service.InformeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador de informes de horas y saldos anuales.
 *
 * <p>Cubre los endpoints E42-E44 (Grupo 10). Delega toda la logica
 * en InformeService. Devuelve ResponseEntity con Content-Type dinamico
 * segun el parametro ?formato=json|html.</p>
 *
 * <p>Los informes PDF firmables (E45-E47) se gestionan en PdfController,
 * bajo la ruta base /api/v1/informes/pdf.</p>
 *
 * <p>D-027: E42 y E43 aceptan el parametro opcional ?tipo= con uno o
 * varios valores separados por coma para filtrar el detalle por tipo
 * de jornada.</p>
 *
 * <p>D-029: E44 renombrado a GET /api/v1/informes/saldos. Acepta los
 * parametros opcionales ?empleadoId= y ?campos=.</p>
 *
 * <p>Roles permitidos: ADMIN y ENCARGADO en todos los endpoints.</p>
 *
 * @author Santiago Castillo
 */
@RestController
@RequestMapping("/api/v1/informes")
@RequiredArgsConstructor
public class InformeController {

    private final InformeService informeService;

    // =========================================================================
    // E-me — GET /api/v1/informes/me/horas
    // Informe de horas del empleado autenticado en HTML
    // NOTA: declarado ANTES de /horas/{empleadoId} por convención /me primero.
    // =========================================================================

    /**
     * Informe de horas del empleado autenticado en HTML (E-me).
     *
     * Devuelve el mismo HTML que E42 pero filtrado por el empleado del token.
     * Solo accesible por EMPLEADO. El service resuelve username → empleadoId.
     *
     * @param desde          fecha de inicio del periodo (?desde=yyyy-MM-dd)
     * @param hasta          fecha de fin del periodo (?hasta=yyyy-MM-dd)
     * @param authentication objeto de seguridad para extraer username
     * @return HTML del informe de horas del empleado autenticado
     */
    @GetMapping("/me/horas")
    @PreAuthorize("hasRole('EMPLEADO')")
    public ResponseEntity<Object> informeHorasMe(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        Object resultado = informeService.informeHorasMe(authentication.getName(), desde, hasta);
        return construirRespuesta(resultado, "html");
    }

    // =========================================================================
    // E42 — GET /api/v1/informes/horas/{empleadoId}
    // RF-32: informe de horas de un empleado en un periodo
    // =========================================================================

    /**
     * Informe de horas trabajadas de un empleado en un periodo (E42).
     *
     * <p>Detalla los dias con jornada NORMAL, dias de ausencia por tipo,
     * pausas del dia, intervenciones manuales y el total de horas efectivas.
     * Con ?formato=html devuelve HTML imprimible para PrintManager + WebView.
     * Con ?formato=json (defecto) devuelve estructura JSON.</p>
     *
     * <p>D-027: el parametro ?tipo= acepta uno o varios valores del enum
     * TipoFichaje separados por coma, mas DIA_LIBRE y SIN_REGISTRO.
     * Sin ?tipo= se devuelven todos los dias del periodo.</p>
     *
     * @param empleadoId id del empleado (path variable)
     * @param desde      fecha de inicio del periodo (?desde=yyyy-MM-dd)
     * @param hasta      fecha de fin del periodo (?hasta=yyyy-MM-dd)
     * @param formato    "json" o "html" — defecto: "json"
     * @param tipo       lista de tipos a incluir — defecto: todos
     * @return informe en el formato solicitado
     */
    @GetMapping("/horas/{empleadoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<Object> informeHorasEmpleado(
            @PathVariable Long empleadoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "json") String formato,
            @RequestParam(required = false) List<String> tipo) {

        Object resultado = informeService.informeHorasEmpleado(
                empleadoId, desde, hasta, formato, tipo);

        return construirRespuesta(resultado, formato);
    }

    // =========================================================================
    // E43 — GET /api/v1/informes/horas
    // RF-33: informe global de horas de todos los empleados
    // =========================================================================

    /**
     * Informe global de horas de todos los empleados activos en un periodo (E43).
     *
     * <p>Devuelve un resumen por empleado con el total de horas efectivas
     * y desglose de tipos de jornada. Con ?formato=html devuelve HTML
     * para impresion desde Android.</p>
     *
     * <p>D-027: el parametro ?tipo= funciona igual que en E42.</p>
     *
     * @param desde   fecha de inicio del periodo (?desde=yyyy-MM-dd)
     * @param hasta   fecha de fin del periodo (?hasta=yyyy-MM-dd)
     * @param formato "json" o "html" — defecto: "json"
     * @param tipo    lista de tipos a incluir — defecto: todos
     * @return informe global en el formato solicitado
     */
    @GetMapping("/horas")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<Object> informeHorasGlobal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "json") String formato,
            @RequestParam(required = false) List<String> tipo) {

        Object resultado = informeService.informeHorasGlobal(
                desde, hasta, formato, tipo);

        return construirRespuesta(resultado, formato);
    }

    // =========================================================================
    // E-Semana — GET /api/v1/informes/semana
    // Tabla HTML semanal con fichajes, pausas y ausencias de todos los empleados
    // =========================================================================

    /**
     * Tabla HTML semanal de presencia de todos los empleados activos (E-Semana).
     *
     * <p>Devuelve un HTML con una tabla empleado × dia (lunes–domingo) donde
     * cada celda muestra el fichaje, pausas y ausencias del dia. Los datos
     * son clicables con URLs staffflow:// para editar desde el WebView Android.</p>
     *
     * @param desde          primer dia del rango (?desde=yyyy-MM-dd)
     * @param hasta          ultimo dia del rango (?hasta=yyyy-MM-dd)
     * @param authentication objeto de seguridad para extraer username y rol
     * @return HTML de la tabla semanal
     */
    @GetMapping("/semana")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<Object> informeSemana(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        String html = informeService.informeSemana(desde, hasta, authentication.getName());
        return construirRespuesta(html, "html");
    }

    // =========================================================================
    // E44 — GET /api/v1/informes/saldos
    // RF-34: informe de saldos anuales (vacaciones, asuntos propios, horas)
    // D-029: renombrado desde /vacaciones, anadidos ?empleadoId= y ?campos=
    // =========================================================================

    /**
     * Informe de saldos anuales de empleados (E44).
     *
     * <p>D-029: parametro ?empleadoId= opcional. Sin parametro devuelve todos
     * los empleados activos con saldo en ese ano. Con uno o varios ids separados
     * por coma devuelve solo esos empleados.</p>
     *
     * <p>D-029: parametro ?campos= opcional. Acepta bloques predefinidos y
     * campos individuales separados por coma. Sin parametro se muestran todos.</p>
     *
     * @param anio        ano a consultar — defecto: ano actual
     * @param formato     "json" o "html" — defecto: "json"
     * @param empleadoId  lista de ids de empleado — defecto: todos los activos
     * @param campos      lista de bloques o campos — defecto: todos
     * @return informe de saldos en el formato solicitado
     */
    @GetMapping("/saldos")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<Object> informeSaldos(
            @RequestParam(required = false) Integer anio,
            @RequestParam(defaultValue = "json") String formato,
            @RequestParam(required = false) List<Long> empleadoId,
            @RequestParam(required = false) List<String> campos) {

        if (anio == null) {
            anio = LocalDate.now().getYear();
        }

        Object resultado = informeService.informeSaldos(anio, formato, empleadoId, campos);
        return construirRespuesta(resultado, formato);
    }

    // =========================================================================
    // E-ausencias-global — GET /api/v1/informes/ausencias
    // Resumen HTML de ausencias de todos los empleados activos en un rango
    // =========================================================================

    /**
     * Resumen de ausencias globales de todos los empleados activos (E-ausencias-global).
     *
     * <p>Devuelve una tabla HTML empleado × día para el rango solicitado.
     * Muestra ausencias ejecutadas (fichajes tipo != NORMAL/DIA_LIBRE) y
     * ausencias planificadas (planificacion_ausencias). Sin columnas de saldo ni totales.</p>
     *
     * @param desde          primer día del rango (?desde=yyyy-MM-dd)
     * @param hasta          último día del rango (?hasta=yyyy-MM-dd)
     * @param authentication objeto de seguridad para extraer username y rol
     * @return HTML del resumen de ausencias
     */
    @GetMapping("/ausencias")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<Object> informeAusenciasGlobal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        String html = informeService.informeAusenciasGlobal(desde, hasta, authentication.getName());
        return construirRespuesta(html, "html");
    }

    // =========================================================================
    // Utilidad privada: construir ResponseEntity con Content-Type correcto
    // =========================================================================

    /**
     * Construye la ResponseEntity con el Content-Type adecuado segun el formato.
     * HTML → text/html;charset=UTF-8
     * JSON → application/json (Spring lo gestiona automaticamente con Object)
     */
    private ResponseEntity<Object> construirRespuesta(Object resultado, String formato) {
        if ("html".equalsIgnoreCase(formato)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("text/html;charset=UTF-8"))
                    .body(resultado);
        }
        return ResponseEntity.ok(resultado);
    }
}
