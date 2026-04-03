package com.staffflow.controller;

import com.staffflow.service.InformeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador de informes de horas y saldos anuales.
 *
 * <p>Cubre los endpoints E42-E44 (Grupo 10). Delega toda la lógica
 * en InformeService. Devuelve ResponseEntity con Content-Type dinámico
 * según el parámetro ?formato=json|html.</p>
 *
 * <p>D-027: E42 y E43 aceptan el parámetro opcional ?tipo= con uno o
 * varios valores separados por coma para filtrar el detalle por tipo
 * de jornada. Sin ?tipo= se devuelven todos los días del período.</p>
 *
 * <p>D-029: E44 renombrado a GET /api/v1/informes/saldos. Acepta los
 * parámetros opcionales ?empleadoId= (uno o varios ids separados por
 * coma) y ?campos= (bloques o campos individuales separados por coma).
 * Sin parámetros devuelve todos los empleados activos con saldo en ese
 * año y todos los campos.</p>
 *
 * <p>Roles permitidos: ADMIN y ENCARGADO en todos los endpoints de
 * este grupo. Sin restricción de fecha: los informes son solo lectura
 * y pueden consultar cualquier período pasado, presente o futuro.</p>
 *
 * @author Santiago Castillo
 */
@RestController
@RequestMapping("/api/v1/informes")
@RequiredArgsConstructor
public class InformeController {

    private final InformeService informeService;

    // =========================================================================
    // E42 — GET /api/v1/informes/horas/{empleadoId}
    // RF-32: informe de horas de un empleado en un período
    // =========================================================================

    /**
     * Informe de horas trabajadas de un empleado en un período (E42).
     *
     * <p>Detalla los días con jornada NORMAL, días de ausencia por tipo,
     * pausas del día, intervenciones manuales y el total de horas efectivas.
     * Con ?formato=html devuelve HTML imprimible para PrintManager + WebView.
     * Con ?formato=json (defecto) devuelve estructura JSON.</p>
     *
     * <p>D-027: el parámetro ?tipo= acepta uno o varios valores del enum
     * TipoFichaje separados por coma, más DIA_LIBRE y SIN_REGISTRO.
     * Sin ?tipo= se devuelven todos los días del período.</p>
     *
     * @param empleadoId id del empleado (path variable)
     * @param desde      fecha de inicio del período (?desde=yyyy-MM-dd)
     * @param hasta      fecha de fin del período (?hasta=yyyy-MM-dd)
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
     * Informe global de horas de todos los empleados activos en un período (E43).
     *
     * <p>Devuelve un resumen por empleado con el total de horas efectivas
     * y desglose de tipos de jornada. Con ?formato=html devuelve HTML
     * para impresión desde Android.</p>
     *
     * <p>D-027: el parámetro ?tipo= funciona igual que en E42.</p>
     *
     * @param desde   fecha de inicio del período (?desde=yyyy-MM-dd)
     * @param hasta   fecha de fin del período (?hasta=yyyy-MM-dd)
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
    // E44 — GET /api/v1/informes/saldos
    // RF-34: informe de saldos anuales (vacaciones, asuntos propios, horas)
    // D-029: renombrado desde /vacaciones, añadidos ?empleadoId= y ?campos=
    // =========================================================================

    /**
     * Informe de saldos anuales de empleados (E44).
     *
     * <p>Devuelve el saldo anual por empleado: días vacaciones (pendientes año
     * anterior, derecho anual, consumidos año en curso, disponibles), días
     * asuntos propios (mismo desglose), resto de días (trabajados, baja médica,
     * permiso retribuido, ausencia injustificada), horas de ausencia retribuida,
     * saldo de horas, fecha calculado hasta y fecha de última modificación.</p>
     *
     * <p>D-029: parámetro ?empleadoId= opcional. Sin parámetro devuelve todos
     * los empleados activos con saldo en ese año. Con uno o varios ids separados
     * por coma devuelve solo esos empleados.
     * Ejemplo: ?empleadoId=1,2</p>
     *
     * <p>D-029: parámetro ?campos= opcional. Acepta bloques predefinidos y
     * campos individuales separados por coma. Sin parámetro se muestran todos.
     * Bloques: DIAS_VACACIONES, DIAS_ASUNTOS_PROPIOS, RESTO_DIAS, HORAS, CONTROL.
     * Campos: VAC_PENDIENTE_ANT, VAC_DERECHO, VAC_CONSUMIDOS, VAC_DISPONIBLES,
     *   AP_PENDIENTE_ANT, AP_DERECHO, AP_CONSUMIDOS, AP_DISPONIBLES,
     *   DIAS_TRABAJADOS, DIAS_BAJA_MEDICA, DIAS_PERMISO_RETRIBUIDO,
     *   DIAS_AUSENCIA_INJUSTIFICADA, HORAS_AUSENCIA_RETRIBUIDA, SALDO_HORAS,
     *   CALCULADO_HASTA, ULTIMA_MODIFICACION.
     * Ejemplo: ?campos=DIAS_VACACIONES,SALDO_HORAS</p>
     *
     * @param anio        año a consultar — defecto: año actual
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
    // Utilidad privada: construir ResponseEntity con Content-Type correcto
    // =========================================================================

    /**
     * Construye la ResponseEntity con el Content-Type adecuado según el formato.
     * HTML → text/html;charset=UTF-8
     * JSON → application/json (Spring lo gestiona automáticamente con Object)
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
