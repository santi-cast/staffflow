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
 * Controlador de informes de horas, saldos anuales, resumen semanal y
 * resumen de ausencias.
 *
 * <p>Cubre los endpoints E42, E43, E44, E58, E59 y E60. Delega toda la
 * logica en InformeService. Devuelve ResponseEntity con Content-Type
 * dinamico segun el parametro ?formato=json|html.</p>
 *
 * <p>Solo E42, E43 y E44 son dual-format JSON/HTML (defecto JSON). E58,
 * E59 y E60 son HTML-only: la firma del controller no acepta ?formato= y
 * el service siempre genera HTML.</p>
 *
 * <p>Los informes PDF firmables (E45-E47, E57) se gestionan en
 * PdfController, bajo la ruta base /api/v1/informes/pdf.</p>
 *
 * <p>Roles permitidos: ADMIN y ENCARGADO en E42, E43, E44, E59 y E60.
 * E58 lo consumen EMPLEADO y ENCARGADO.</p>
 *
 * @author Santiago Castillo
 */
@RestController
@RequestMapping("/api/v1/informes")
@RequiredArgsConstructor
public class InformeController {

    private final InformeService informeService;

    // E58 — GET /api/v1/informes/me/horas
    // Informe de horas del empleado autenticado en HTML
    // NOTA: declarado ANTES de /horas/{empleadoId} por convención /me primero.

    /**
     * Informe de horas del empleado autenticado en HTML (E58).
     *
     * <p>Devuelve el mismo HTML que E42 pero filtrado por el empleado del
     * token. Accesible por EMPLEADO y ENCARGADO. El service resuelve
     * username → usuario → empleado.</p>
     *
     * <p>Codigos HTTP:</p>
     * <ul>
     *   <li>200: HTML del informe.</li>
     *   <li>404: si el usuario autenticado no tiene perfil de empleado
     *   asociado (caso tipico: ENCARGADO puro sin ficha de empleado).</li>
     * </ul>
     *
     * @param desde          fecha de inicio del periodo (?desde=yyyy-MM-dd)
     * @param hasta          fecha de fin del periodo (?hasta=yyyy-MM-dd)
     * @param authentication objeto de seguridad para extraer username
     * @return HTML del informe de horas del empleado autenticado
     */
    @GetMapping("/me/horas")
    @PreAuthorize("hasAnyRole('EMPLEADO','ENCARGADO')")
    public ResponseEntity<Object> informeHorasMe(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        Object resultado = informeService.informeHorasMe(authentication.getName(), desde, hasta);
        return construirRespuesta(resultado, "html");
    }

    // E42 — GET /api/v1/informes/horas/{empleadoId}
    // Encaja en el bloque RF-32 a RF-40 (informes y saldos);
    // B6 no asigna RF numerico individual a este endpoint.

    /**
     * Informe de horas trabajadas de un empleado en un periodo (E42).
     *
     * <p>Detalla los dias con jornada NORMAL, dias de ausencia por tipo,
     * pausas del dia, intervenciones manuales y el total de horas efectivas.
     * Con ?formato=html devuelve HTML imprimible para PrintManager + WebView.
     * Con ?formato=json (defecto) devuelve estructura JSON.</p>
     *
     * <p>El parametro ?tipo= acepta uno o varios valores del enum
     * TipoFichaje separados por coma, mas DIA_LIBRE y SIN_REGISTRO.
     * Sin ?tipo= se devuelven todos los dias del periodo.</p>
     *
     * <p>Codigos HTTP:</p>
     * <ul>
     *   <li>200: informe en el formato solicitado.</li>
     *   <li>404: si el empleado con id {@code empleadoId} no existe.</li>
     * </ul>
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

    // E43 — GET /api/v1/informes/horas
    // Encaja en el bloque RF-32 a RF-40 (informes y saldos);
    // B6 no asigna RF numerico individual a este endpoint.

    /**
     * Informe global de horas de todos los empleados activos en un periodo (E43).
     *
     * <p>Devuelve un resumen por empleado con el total de horas efectivas
     * y desglose de tipos de jornada. Con ?formato=html devuelve HTML
     * para impresion desde Android.</p>
     *
     * <p>El parametro ?tipo= funciona igual que en E42.</p>
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

    // E59 — GET /api/v1/informes/semana
    // Tabla HTML semanal con fichajes, pausas y ausencias de todos los empleados.
    // Solo HTML (sin ?formato=). Operativos en el rango: empleados activos con
    // fechaAlta <= hasta.

    /**
     * Tabla HTML semanal de presencia de todos los empleados activos (E59).
     *
     * <p>Devuelve un HTML con una tabla empleado × dia (lunes–domingo) donde
     * cada celda muestra el fichaje, pausas y ausencias del dia. Los datos
     * son clicables con URLs staffflow:// para editar desde el WebView Android.</p>
     *
     * <p>Los enlaces de edicion se generan condicionalmente segun rol y
     * fecha de cada celda:</p>
     * <ul>
     *   <li>Fichajes y pausas: editables si la fecha no es futura y
     *   (rol = ADMIN o fecha = hoy). ENCARGADO no puede editar pasado.</li>
     *   <li>Ausencias planificadas: editables si rol = ADMIN o fecha
     *   &gt;= hoy. ENCARGADO no puede editar ausencias pasadas.</li>
     * </ul>
     *
     * <p>Codigos HTTP:</p>
     * <ul>
     *   <li>200: HTML de la tabla semanal.</li>
     *   <li>404: si el usuario autenticado no existe (caso teorico, el
     *   JWT ya valida la existencia del usuario).</li>
     * </ul>
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

    // E44 — GET /api/v1/informes/saldos
    // Encaja en el bloque RF-32 a RF-40 (informes y saldos);
    // B6 no asigna RF numerico individual a este endpoint.

    /**
     * Informe de saldos anuales de empleados (E44).
     *
     * <p>Parametro ?empleadoId= opcional. Sin parametro devuelve todos
     * los empleados activos con saldo en ese ano. Con uno o varios ids separados
     * por coma devuelve solo esos empleados.</p>
     *
     * <p>Parametro ?campos= opcional. Acepta grupos predefinidos y
     * campos individuales separados por coma. Sin parametro se muestran todos.</p>
     *
     * <p><b>Efecto colateral (find-or-create):</b> si el ano consultado
     * ya tiene al menos un SaldoAnual en BD, el endpoint completa
     * on-demand los empleados activos que aun no tengan registro para
     * ese ano invocando SaldoService.recalcularParaProceso(empleadoId,
     * anio). Esto evita huecos cuando se incorpora un empleado a mitad
     * de ano sin que haya corrido el cierre nocturno.</p>
     *
     * <p>Codigos HTTP:</p>
     * <ul>
     *   <li>200: informe en el formato solicitado.</li>
     *   <li>404: si no existe ningun SaldoAnual de empleados activos
     *   para el ano consultado (empty state: el ano no tiene datos
     *   reales y no se autocrea desde cero).</li>
     * </ul>
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

    // E60 — GET /api/v1/informes/ausencias
    // Resumen HTML de ausencias de todos los empleados activos en un rango.
    // Solo HTML (sin ?formato=). Operativos en el rango: empleados activos
    // con fechaAlta <= hasta.

    /**
     * Resumen de ausencias globales de todos los empleados activos (E60).
     *
     * <p>Devuelve una tabla HTML empleado × dia para el rango solicitado.
     * Muestra ausencias ejecutadas (fichajes con tipo != NORMAL y
     * != DIA_LIBRE) y ausencias planificadas (planificacion_ausencias).
     * Incluye tambien festivos globales (planificacion con empleado=null)
     * resaltados como columna comun. Sin columnas de saldo ni totales.</p>
     *
     * <p>Los enlaces de edicion se generan condicionalmente segun rol y
     * fecha de cada celda:</p>
     * <ul>
     *   <li>Fichajes de ausencia: editables solo si rol = ADMIN y la
     *   fecha no es futura. ENCARGADO nunca edita fichajes desde este
     *   informe.</li>
     *   <li>Ausencias planificadas: editables si rol = ADMIN o fecha
     *   &gt;= hoy. ENCARGADO no puede editar ausencias pasadas.</li>
     * </ul>
     *
     * <p>Codigos HTTP:</p>
     * <ul>
     *   <li>200: HTML del resumen de ausencias.</li>
     *   <li>404: si el usuario autenticado no existe (caso teorico, el
     *   JWT ya valida la existencia del usuario).</li>
     * </ul>
     *
     * @param desde          primer dia del rango (?desde=yyyy-MM-dd)
     * @param hasta          ultimo dia del rango (?hasta=yyyy-MM-dd)
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

    // Utilidad privada: construir ResponseEntity con Content-Type correcto

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
