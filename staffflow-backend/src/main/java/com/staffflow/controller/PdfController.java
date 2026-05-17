package com.staffflow.controller;

import com.staffflow.service.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controlador de informes PDF firmables generados con iText 7.
 *
 * <p>Cubre los endpoints E45-E47 + E57 (Grupo 11). Delega toda la logica
 * en PdfService. Devuelve siempre ResponseEntity<byte[]> con
 * Content-Type application/pdf y Content-Disposition attachment.</p>
 *
 * <p>Separado de InformeController (E42-E44, E58, E59, E60) por el Principio
 * de Responsabilidad Unica: InformeController devuelve vistas de datos
 * (JSON/HTML); PdfController devuelve documentos binarios firmables
 * (RF-38, RF-39, RF-40, RD-ley 8/2019).</p>
 *
 * <p>Ruta base: /api/v1/informes/pdf — evita conflictos de resolucion
 * de rutas con los path variables {empleadoId} de InformeController.</p>
 *
 * <p>Roles permitidos: ADMIN y ENCARGADO en todos los endpoints.</p>
 *
 * @author Santiago Castillo
 */
@RestController
@RequestMapping("/api/v1/informes/pdf")
@RequiredArgsConstructor
public class PdfController {

    private final PdfService pdfService;

    private static final DateTimeFormatter FMT_NOMBRE =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    // =========================================================================
    // E45 — GET /api/v1/informes/pdf/horas/{empleadoId}
    // RF-38: informe de horas de un empleado en un periodo, firmable
    // =========================================================================

    /**
     * Genera el PDF del informe de horas de un empleado en un periodo (E45).
     *
     * <p>Mismo contenido que E42 (horas, pausas, intervenciones manuales)
     * pero en formato PDF firmable con espacio para firma fisica del empleado
     * (RD-ley 8/2019, RF-38). Cabecera de empresa repetida en cada pagina.
     * Numeracion "Pagina X de Y" en pie de pagina.</p>
     *
     * <p>El fichero se descarga con nombre:
     * informe_horas_{empleadoId}_{desde}_{hasta}.pdf</p>
     *
     * @param empleadoId id del empleado (path variable)
     * @param desde      fecha de inicio del periodo (?desde=yyyy-MM-dd)
     * @param hasta      fecha de fin del periodo (?hasta=yyyy-MM-dd)
     * @return PDF binario descargable
     */
    @GetMapping("/horas/{empleadoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<byte[]> informeHorasEmpleadoPdf(
            @PathVariable Long empleadoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        byte[] pdf = pdfService.generarPdfHorasEmpleado(empleadoId, desde, hasta);

        String nombreFichero = "informe_horas_" + empleadoId
                + "_" + desde.format(FMT_NOMBRE)
                + "_" + hasta.format(FMT_NOMBRE) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreFichero + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // =========================================================================
    // E46 — GET /api/v1/informes/pdf/horas
    // RF-39: informe global de horas de todos los empleados, firmable
    // =========================================================================

    /**
     * Genera el PDF del informe global de horas de todos los empleados (E46).
     *
     * <p>Un bloque por empleado, cada uno con su propia seccion de firma.
     * Cada empleado empieza en pagina nueva para facilitar la distribucion
     * fisica de los documentos firmados.</p>
     *
     * <p>El fichero se descarga con nombre:
     * informe_horas_global_{desde}_{hasta}.pdf</p>
     *
     * @param desde fecha de inicio del periodo (?desde=yyyy-MM-dd)
     * @param hasta fecha de fin del periodo (?hasta=yyyy-MM-dd)
     * @return PDF binario descargable
     */
    @GetMapping("/horas")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<byte[]> informeHorasGlobalPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        byte[] pdf = pdfService.generarPdfHorasGlobal(desde, hasta);

        String nombreFichero = "informe_horas_global_"
                + desde.format(FMT_NOMBRE)
                + "_" + hasta.format(FMT_NOMBRE) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreFichero + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // =========================================================================
    // E47 — GET /api/v1/informes/pdf/saldos
    // RF-40: informe de saldos anuales firmable
    // =========================================================================

    /**
     * Genera el PDF del informe de saldos anuales (E47).
     *
     * <p>Un bloque por empleado con todos los saldos del ano: vacaciones,
     * asuntos propios, resto de dias y horas. Espacio para firma al final
     * de cada bloque.</p>
     *
     * <p>El fichero se descarga con nombre:
     * informe_saldos_{yyyyMMdd}.pdf</p>
     *
     * @param anio       ano del informe — defecto: ano actual
     * @param empleadoId lista de ids de empleado — defecto: todos los activos
     * @return PDF binario descargable
     */
    @GetMapping("/saldos")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<byte[]> informeSaldosPdf(
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) List<Long> empleadoId) {

        if (anio == null) {
            anio = LocalDate.now().getYear();
        }

        byte[] pdf = pdfService.generarPdfSaldos(anio, empleadoId);

        String nombreFichero = "informe_saldos_" + LocalDate.now().format(FMT_NOMBRE) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreFichero + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // =========================================================================
    // E57 — GET /api/v1/informes/pdf/vacaciones
    // RF-41: informe de vacaciones y asuntos propios disfrutados, firmable
    // =========================================================================

    /**
     * Genera el PDF del informe de vacaciones y asuntos propios disfrutados
     * por un empleado en un año (E57).
     *
     * <p>Dos tablas con listado de fechas y total por tipo. Mismo estilo que
     * E47. Documento firmable para acreditar las vacaciones y asuntos propios
     * del empleado en el año consultado (RD-ley 8/2019, RF-41).</p>
     *
     * <p>El fichero se descarga con nombre:
     * informe_vacaciones_{empleadoId}_{yyyyMMdd}.pdf</p>
     *
     * @param empleadoId id del empleado (obligatorio)
     * @param anio       año del informe — defecto: año actual
     * @return PDF binario descargable
     */
    @GetMapping("/vacaciones")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    public ResponseEntity<byte[]> informeAusenciasPdf(
            @RequestParam Long empleadoId,
            @RequestParam(required = false) Integer anio) {

        if (anio == null) {
            anio = LocalDate.now().getYear();
        }

        byte[] pdf = pdfService.generarPdfAusencias(empleadoId, anio);

        String nombreFichero = "informe_vacaciones_" + empleadoId
                + "_" + LocalDate.now().format(FMT_NOMBRE) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreFichero + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
