package com.staffflow.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.SaldoAnual;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import com.staffflow.dto.response.EmpresaResponse;
import com.staffflow.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Servicio de generacion de informes PDF firmables con iText 7.
 *
 * <p>Cubre los endpoints E45-E47 (Grupo 11) y los endpoints PDF auxiliares
 * E20 (exportación de empleados) y E57 (vacaciones y asuntos propios).
 * Genera documentos PDF binarios que incluyen espacio para firma física del
 * empleado, requisito para la documentación oficial ante la Inspección de
 * Trabajo (RD-ley 8/2019, RF-38, RF-39, RF-40).</p>
 *
 * <p>Estrategia de reutilizacion (Opcion C): PdfService delega la
 * construccion de datos en InformeService llamando a sus metodos publicos
 * existentes. Esto evita duplicar la logica de construirDias() y
 * calcularResumen() ya verificada en E42/E43.</p>
 *
 * <p>Logo: se carga desde la ruta indicada en ConfiguracionEmpresa.logoPath
 * mediante getClass().getResourceAsStream(). Si la ruta es null o el
 * fichero no existe, el PDF se genera sin logo (fallback silencioso).
 * En dev la ruta apunta a src/main/resources/static/logo_empresa.png.</p>
 *
 * <p>E45: cabecera repetida en cada pagina mediante PdfDocumentEvent.START_PAGE,
 * firma en la ultima pagina, numeracion "Pagina X de Y" en pie via segunda pasada.</p>
 *
 * <p>E46: genera cada empleado como un PDF E45 independiente y los concatena
 * con PdfMerger. Cada bloque tiene su propia paginacion, cabecera y firma.</p>
 *
 * <p>E47: genera cada empleado como un PDF independiente con cabecera de empresa,
 * tabla de saldos y firma, y los concatena con PdfMerger. Pie de pagina via
 * PieSaldosHandler (START_PAGE) + segunda pasada sobreescribirNumeracionSaldos().
 * Patron identico a E45.</p>
 *
 * <p>E57: genera el informe de vacaciones y asuntos propios disfrutados por un
 * empleado en un año. Dos tablas (una por tipo) con listado de fechas y total.
 * Mismo estilo que E47. Ruta base: /api/v1/informes/pdf/ausencias.</p>
 *
 * @author Santiago Castillo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    private final InformeService    informeService;
    private final EmpresaService    empresaService;
    private final EmpleadoRepository empleadoRepository;
    private final SaldoAnualRepository saldoRepository;
    private final FichajeRepository fichajeRepository;

    // Colores corporativos alineados con el HTML de E42/E43
    private static final DeviceRgb COLOR_CABECERA  = new DeviceRgb(44, 62, 107);   // #2c3e6b
    private static final DeviceRgb COLOR_AUSENCIA  = new DeviceRgb(255, 248, 225);  // #fff8e1
    private static final DeviceRgb COLOR_LIBRE     = new DeviceRgb(240, 240, 240);  // #f0f0f0
    private static final DeviceRgb COLOR_TOTAL     = new DeviceRgb(44, 62, 107);    // #2c3e6b
    private static final DeviceRgb COLOR_INTERV    = new DeviceRgb(255, 248, 225);  // #fff8e1
    private static final DeviceRgb COLOR_POS       = new DeviceRgb(15, 110, 86);    // verde
    private static final DeviceRgb COLOR_NEG       = new DeviceRgb(192, 57, 43);    // rojo

    private static final float MARGEN              = 40f;
    private static final float FONT_SIZE_NORMAL    = 8f;
    private static final float FONT_SIZE_CABECERA  = 7f;
    private static final float FONT_SIZE_TITULO    = 12f;
    private static final float FONT_SIZE_PIE       = 7f;
    private static final float ALTO_CABECERA_PAG   = 95f;  // espacio para empresa+empleado
    private static final float ALTO_PIE_PAG        = 25f;

    private static final DateTimeFormatter FMT_FECHA  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA   = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FMT_GENERA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");
    private static final DateTimeFormatter FMT_DIA_SEM = DateTimeFormatter.ofPattern("EEE", new java.util.Locale("es", "ES"));

    // E45 — GET /api/v1/informes/horas/{empleadoId}/pdf
    // RF-38: informe de horas de un empleado, firmable

    /**
     * Genera el PDF del informe de horas de un empleado en un periodo (E45).
     *
     * <p>Reutiliza informeHorasEmpleado() de InformeService con formato json
     * para obtener los datos ya procesados. Construye el PDF con iText 7
     * siguiendo el diseno acordado: cabecera repetida por pagina, tabla de
     * detalle, seccion de intervenciones manuales (condicional) y firma.</p>
     *
     * @param empleadoId id del empleado
     * @param desde      fecha de inicio del periodo
     * @param hasta      fecha de fin del periodo
     * @return bytes del PDF generado
     */
    @SuppressWarnings("unchecked")
    public byte[] generarPdfHorasEmpleado(Long empleadoId, LocalDate desde, LocalDate hasta) {

        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new NotFoundException(
                        "Empleado no encontrado con id " + empleadoId));

        // Obtener datos via InformeService (Opcion C — sin duplicar logica)
        Map<String, Object> datos = (Map<String, Object>)
                informeService.informeHorasEmpleado(empleadoId, desde, hasta, "json", null);

        Map<String, Object> resumen = (Map<String, Object>) datos.get("resumen");
        List<Map<String, Object>> detalle = (List<Map<String, Object>>) datos.get("detalle");

        EmpresaResponse empresa = obtenerEmpresa();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            pdfDoc.setDefaultPageSize(PageSize.A4);

            // Manejador de cabecera/pie en cada pagina.
            // totalPaginas se actualiza en segunda pasada (no se conoce hasta cerrar).
            CabeceraPieHandler handler = new CabeceraPieHandler(
                    empresa, empleado, desde, hasta, pdfDoc);
            pdfDoc.addEventHandler(PdfDocumentEvent.START_PAGE, handler);

            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(ALTO_CABECERA_PAG + 35, MARGEN, ALTO_PIE_PAG + 20, MARGEN);

            // Resumen
            agregarResumen(doc, resumen);

            // Tabla de detalle
            agregarTablaDetalle(doc, detalle, empleado);

            // Intervenciones manuales (condicional)
            agregarIntervencionesManuales(doc, detalle);

            // Firma
            agregarSeccionFirma(doc, nombreCompleto(empleado));

            // Capturar total ANTES de cerrar
            int totalPaginas = pdfDoc.getNumberOfPages();
            doc.close();

            // Segunda pasada: sobreescribir "Pagina X" con "Pagina X de Y"
            return sobreescribirNumeracion(baos.toByteArray(), totalPaginas,
                    empresa, empleado, desde, hasta);

        } catch (Exception e) {
            log.error("Error generando PDF E45 para empleadoId={}: {}", empleadoId, e.getMessage(), e);
            throw new IllegalStateException("Error generando el informe PDF: " + e.getMessage(), e);
        }
    }

    // E46 — GET /api/v1/informes/horas/pdf
    // RF-39: informe global de horas de todos los empleados, firmable

    /**
     * Genera el PDF del informe global de horas de todos los empleados (E46).
     *
     * <p>Genera un PDF E45 independiente por cada empleado activo (con su propia
     * cabecera, paginacion "Pagina X de Y" y seccion de firma) y los concatena
     * en un unico documento con PdfMerger. Esto permite imprimir todos los
     * informes de una vez sin perder la paginacion individual por empleado.</p>
     *
     * @param desde fecha de inicio del periodo
     * @param hasta fecha de fin del periodo
     * @return bytes del PDF concatenado
     */
    public byte[] generarPdfHorasGlobal(LocalDate desde, LocalDate hasta) {

        List<Empleado> empleados = empleadoRepository.findByActivo(true);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDocFinal = new PdfDocument(writer);
            PdfMerger merger = new PdfMerger(pdfDocFinal);

            for (Empleado empleado : empleados) {
                // Reutilizar E45 completo: cabecera, numeracion y firma correctas
                byte[] pdfEmpleado = generarPdfHorasEmpleado(empleado.getId(), desde, hasta);
                com.itextpdf.kernel.pdf.PdfReader reader =
                        new com.itextpdf.kernel.pdf.PdfReader(
                                new java.io.ByteArrayInputStream(pdfEmpleado));
                PdfDocument pdfEmpleadoDoc = new PdfDocument(reader);
                merger.merge(pdfEmpleadoDoc, 1, pdfEmpleadoDoc.getNumberOfPages());
                pdfEmpleadoDoc.close();
            }

            pdfDocFinal.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando PDF E46: {}", e.getMessage(), e);
            throw new IllegalStateException("Error generando el informe PDF global: " + e.getMessage(), e);
        }
    }

    // E47 — GET /api/v1/informes/saldos/pdf
    // RF-40: informe de saldos anuales firmable

    /**
     * Genera el PDF del informe de saldos anuales (E47).
     *
     * <p>Genera un PDF independiente por cada empleado con saldo registrado
     * (cabecera de empresa, tabla de vacaciones/asuntos propios, resto de datos
     * y seccion de firma con numeracion "Pagina X de Y" propia) y los concatena
     * en un unico documento con PdfMerger. Si no hay saldos, devuelve un PDF
     * con mensaje informativo.</p>
     *
     * @param anio        año del informe
     * @param empleadoIds lista de ids (null = todos los activos con saldo)
     * @return bytes del PDF concatenado
     */
    public byte[] generarPdfSaldos(Integer anio, List<Long> empleadoIds) {

        List<SaldoAnual> saldos;
        if (empleadoIds == null || empleadoIds.isEmpty()) {
            saldos = saldoRepository.findByAnio(anio).stream()
                    .filter(s -> s.getEmpleado().getActivo())
                    .sorted((a, b) -> nombreCompleto(a.getEmpleado())
                            .compareTo(nombreCompleto(b.getEmpleado())))
                    .toList();
        } else {
            saldos = new java.util.ArrayList<>();
            for (Long id : empleadoIds) {
                saldoRepository.findByEmpleadoIdAndAnio(id, anio).ifPresent(saldos::add);
            }
            saldos.sort((a, b) -> nombreCompleto(a.getEmpleado())
                    .compareTo(nombreCompleto(b.getEmpleado())));
        }

        EmpresaResponse empresa = obtenerEmpresa();

        // Sin saldos: devolver PDF informativo de una sola pagina
        if (saldos.isEmpty()) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                PdfWriter writer = new PdfWriter(baos);
                PdfDocument pdfDoc = new PdfDocument(writer);
                Document doc = new Document(pdfDoc, PageSize.A4);
                doc.setMargins(MARGEN, MARGEN, MARGEN, MARGEN);
                agregarCabeceraInformeSaldos(doc, empresa, anio);
                doc.add(new Paragraph("No hay saldos registrados para el año " + anio + ".")
                        .setFontSize(FONT_SIZE_NORMAL)
                        .setFontColor(ColorConstants.GRAY));
                doc.close();
                return baos.toByteArray();
            } catch (Exception e) {
                log.error("Error generando PDF E47 vacio anio={}: {}", anio, e.getMessage(), e);
                throw new IllegalStateException("Error generando el informe PDF de saldos: " + e.getMessage(), e);
            }
        }

        // Con saldos: un PDF independiente por empleado concatenado con PdfMerger
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDocFinal = new PdfDocument(writer);
            PdfMerger merger = new PdfMerger(pdfDocFinal);

            for (SaldoAnual saldo : saldos) {
                byte[] pdfEmpleado = generarPdfSaldoEmpleado(empresa, saldo, anio);
                com.itextpdf.kernel.pdf.PdfReader reader =
                        new com.itextpdf.kernel.pdf.PdfReader(
                                new java.io.ByteArrayInputStream(pdfEmpleado));
                PdfDocument pdfEmpleadoDoc = new PdfDocument(reader);
                merger.merge(pdfEmpleadoDoc, 1, pdfEmpleadoDoc.getNumberOfPages());
                pdfEmpleadoDoc.close();
            }

            pdfDocFinal.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando PDF E47 anio={}: {}", anio, e.getMessage(), e);
            throw new IllegalStateException("Error generando el informe PDF de saldos: " + e.getMessage(), e);
        }
    }

    // E57 — GET /api/v1/informes/pdf/vacaciones
    // RF-41: informe de vacaciones y asuntos propios disfrutados, firmable

    /**
     * Genera el PDF del informe de vacaciones y asuntos propios disfrutados
     * por un empleado en un año (E57).
     *
     * <p>Dos tablas: una para vacaciones y otra para asuntos propios, cada una
     * con el listado de fechas disfrutadas, fila de total y fila de dias
     * pendientes de disfrutar (verde si quedan, rojo si se han superado,
     * negro si es cero). Mismo estilo que E47: cabecera de empresa, datos del
     * empleado, pie via PieSaldosHandler + segunda pasada
     * sobreescribirNumeracionSaldos().</p>
     *
     * <p>Los fichajes se obtienen de FichajeRepository.findByFiltros() filtrando
     * por empleadoId, rango anual y tipo (VACACIONES / ASUNTO_PROPIO).
     * Los dias disponibles se obtienen de SaldoAnual del empleado para el año.</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año del informe (defecto: año actual)
     * @return bytes del PDF generado
     */
    public byte[] generarPdfAusencias(Long empleadoId, Integer anio) {

        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new NotFoundException(
                        "Empleado no encontrado con id " + empleadoId));

        EmpresaResponse empresa = obtenerEmpresa();

        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta = LocalDate.of(anio, 12, 31);

        List<Fichaje> vacaciones = fichajeRepository.findByFiltros(
                empleadoId, desde, hasta, TipoFichaje.VACACIONES);
        List<Fichaje> asuntosPropios = fichajeRepository.findByFiltros(
                empleadoId, desde, hasta, TipoFichaje.ASUNTO_PROPIO);

        // Obtener dias disponibles del saldo anual (null si aun no existe registro)
        int diasVacDisponibles = 0;
        int diasApDisponibles  = 0;
        java.util.Optional<SaldoAnual> saldoOpt =
                saldoRepository.findByEmpleadoIdAndAnio(empleadoId, anio);
        if (saldoOpt.isPresent()) {
            SaldoAnual s = saldoOpt.get();
            diasVacDisponibles = s.getDiasVacacionesDisponibles() != null
                    ? s.getDiasVacacionesDisponibles() : 0;
            diasApDisponibles  = s.getDiasAsuntosPropiosDisponibles() != null
                    ? s.getDiasAsuntosPropiosDisponibles() : 0;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            pdfDoc.setDefaultPageSize(PageSize.A4);

            PieSaldosHandler handler = new PieSaldosHandler(pdfDoc);
            pdfDoc.addEventHandler(PdfDocumentEvent.START_PAGE, handler);

            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(MARGEN, MARGEN, ALTO_PIE_PAG + 20, MARGEN);

            agregarCabeceraAusencias(doc, empresa, empleado, anio);
            agregarTablaFechasAusencia(doc, "Vacaciones disfrutadas",
                    vacaciones, diasVacDisponibles);
            agregarTablaFechasAusencia(doc, "Asuntos propios disfrutados",
                    asuntosPropios, diasApDisponibles);
            agregarSeccionFirma(doc, nombreCompleto(empleado));

            int totalPaginas = pdfDoc.getNumberOfPages();
            doc.close();

            return sobreescribirNumeracionSaldos(baos.toByteArray(), totalPaginas);

        } catch (Exception e) {
            log.error("Error generando PDF E57 empleadoId={} anio={}: {}",
                    empleadoId, anio, e.getMessage(), e);
            throw new IllegalStateException(
                    "Error generando el informe PDF de ausencias: " + e.getMessage(), e);
        }
    }

    /** Cabecera del informe de vacaciones y asuntos propios (E57): empresa + logo + datos empleado + año. */
    private void agregarCabeceraAusencias(Document doc, EmpresaResponse empresa,
                                           Empleado empleado, Integer anio) throws Exception {
        PdfFont fontBold = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont font = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        Table tabla = new Table(new float[]{300f, 180f})
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(10f);

        Cell izq = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        izq.add(new Paragraph(empresa.getNombreEmpresa())
                .setFont(fontBold).setFontSize(FONT_SIZE_TITULO)
                .setFontColor(COLOR_CABECERA));
        izq.add(new Paragraph("CIF: " + empresa.getCif())
                .setFont(font).setFontSize(FONT_SIZE_NORMAL));
        if (empresa.getDireccion() != null) {
            izq.add(new Paragraph(empresa.getDireccion())
                    .setFont(font).setFontSize(FONT_SIZE_NORMAL));
        }
        tabla.addCell(izq);

        Cell der = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT)
                .setHorizontalAlignment(HorizontalAlignment.RIGHT);
        Image logo = cargarLogo(empresa.getLogoPath());
        if (logo != null) {
            logo.setMaxWidth(100f).setMaxHeight(50f);
            der.add(logo);
        }
        tabla.addCell(der);
        doc.add(tabla);

        doc.add(new Paragraph()
                .setBorderBottom(new SolidBorder(COLOR_CABECERA, 2f))
                .setMarginBottom(8f));

        doc.add(new Paragraph("INFORME DE VACACIONES Y ASUNTOS PROPIOS — " + anio)
                .setFont(fontBold).setFontSize(FONT_SIZE_TITULO)
                .setFontColor(COLOR_CABECERA)
                .setMarginBottom(5f));

        doc.add(new Paragraph(nombreCompleto(empleado))
                .setFont(fontBold).setFontSize(11f)
                .setMarginBottom(3f));
        doc.add(new Paragraph(
                "Nº empleado: " + empleado.getNumeroEmpleado()
                + "   Categoría: " + empleado.getCategoria()
                + "   Jornada: " + empleado.getJornadaSemanalHoras() + "h/semana")
                .setFont(font).setFontSize(FONT_SIZE_NORMAL)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(15f));
    }

    /**
     * Tabla de fechas de un tipo de ausencia (vacaciones o asuntos propios).
     * Muestra una fila por fecha con el dia de la semana, fila de total disfrutado
     * y fila de dias pendientes de disfrutar con color:
     *   verde si diasDisponibles > 0, rojo si < 0, negro si = 0.
     * Si no hay fechas muestra un mensaje informativo pero mantiene la fila
     * de pendientes si el saldo existe.
     *
     * @param doc             documento PDF
     * @param titulo          titulo de la seccion
     * @param fichajes        lista de fichajes del tipo correspondiente
     * @param diasDisponibles dias pendientes de disfrutar segun SaldoAnual
     */
    private void agregarTablaFechasAusencia(Document doc, String titulo,
                                             List<Fichaje> fichajes,
                                             int diasDisponibles) throws Exception {
        PdfFont fontBold = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont font = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        doc.add(new Paragraph(titulo)
                .setFont(fontBold).setFontSize(FONT_SIZE_NORMAL)
                .setFontColor(COLOR_CABECERA)
                .setMarginTop(10f).setMarginBottom(4f));

        if (fichajes.isEmpty()) {
            doc.add(new Paragraph("No hay días registrados.")
                    .setFont(font).setFontSize(FONT_SIZE_NORMAL)
                    .setFontColor(ColorConstants.GRAY)
                    .setMarginBottom(4f));
        } else {
            List<Fichaje> ordenados = fichajes.stream()
                    .sorted(java.util.Comparator.comparing(Fichaje::getFecha))
                    .toList();

            float[] anchos = {80f, 200f};
            Table tabla = new Table(anchos).setMarginBottom(4f);

            tabla.addHeaderCell(new Cell()
                    .add(new Paragraph("Fecha").setFont(fontBold)
                            .setFontSize(FONT_SIZE_CABECERA).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(COLOR_CABECERA).setPadding(4f));
            tabla.addHeaderCell(new Cell()
                    .add(new Paragraph("Día de la semana").setFont(fontBold)
                            .setFontSize(FONT_SIZE_CABECERA).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(COLOR_CABECERA).setPadding(4f));

            for (Fichaje f : ordenados) {
                tabla.addCell(new Cell()
                        .add(new Paragraph(f.getFecha().format(FMT_FECHA))
                                .setFont(font).setFontSize(FONT_SIZE_NORMAL))
                        .setPadding(3f)
                        .setBorder(new SolidBorder(new DeviceRgb(221, 221, 221), 0.5f)));
                String diaSemana = f.getFecha().format(FMT_DIA_SEM);
                diaSemana = diaSemana.substring(0, 1).toUpperCase()
                        + diaSemana.substring(1).toLowerCase();
                tabla.addCell(new Cell()
                        .add(new Paragraph(diaSemana)
                                .setFont(font).setFontSize(FONT_SIZE_NORMAL))
                        .setPadding(3f)
                        .setBorder(new SolidBorder(new DeviceRgb(221, 221, 221), 0.5f)));
            }

            // Fila total disfrutado
            tabla.addCell(new Cell(1, 2)
                    .add(new Paragraph("Total disfrutados: " + ordenados.size() + " día"
                            + (ordenados.size() != 1 ? "s" : ""))
                            .setFont(fontBold).setFontSize(FONT_SIZE_NORMAL)
                            .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(COLOR_TOTAL).setPadding(4f)
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));

            doc.add(tabla);
        }

        // Fila pendientes de disfrutar con color
        DeviceRgb colorDisp = diasDisponibles > 0 ? COLOR_POS
                : (diasDisponibles < 0 ? COLOR_NEG : null);
        String prefijo = diasDisponibles > 0 ? "+" : "";
        String textoDisp = "Pendientes de disfrutar: " + prefijo + diasDisponibles + " día"
                + (Math.abs(diasDisponibles) != 1 ? "s" : "");
        doc.add(new Paragraph(textoDisp)
                .setFont(fontBold).setFontSize(FONT_SIZE_NORMAL)
                .setFontColor(colorDisp != null ? colorDisp : ColorConstants.BLACK)
                .setMarginBottom(10f));
    }

    /**
     * Genera el PDF de saldo anual de un unico empleado (E47).
     *
     * <p>Patron identico a E45: primera pasada con PieSaldosHandler que dibuja
     * el pie provisional ("Pagina X") en cada pagina via START_PAGE, seguida de
     * segunda pasada sobreescribirNumeracionSaldos() que sobreescribe la numeracion
     * con "Pagina X de Y" una vez conocido el total. Usado internamente por
     * generarPdfSaldos() para concatenar con PdfMerger.</p>
     *
     * @param empresa datos de la empresa para la cabecera
     * @param saldo   saldo anual del empleado
     * @param anio    año del informe
     * @return bytes del PDF individual del empleado
     */
    private byte[] generarPdfSaldoEmpleado(EmpresaResponse empresa, SaldoAnual saldo, Integer anio) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            pdfDoc.setDefaultPageSize(PageSize.A4);

            // Handler de pie en cada pagina (identico a CabeceraPieHandler pero sin cabecera)
            PieSaldosHandler handler = new PieSaldosHandler(pdfDoc);
            pdfDoc.addEventHandler(PdfDocumentEvent.START_PAGE, handler);

            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(MARGEN, MARGEN, ALTO_PIE_PAG + 20, MARGEN);

            agregarCabeceraInformeSaldos(doc, empresa, anio);
            agregarBloqueSaldo(doc, empresa, saldo, anio);
            agregarSeccionFirma(doc, nombreCompleto(saldo.getEmpleado()));

            // Capturar total ANTES de cerrar (igual que E45)
            int totalPaginas = pdfDoc.getNumberOfPages();
            doc.close();

            // Segunda pasada: sobreescribir "Pagina X" con "Pagina X de Y"
            return sobreescribirNumeracionSaldos(baos.toByteArray(), totalPaginas);

        } catch (Exception e) {
            log.error("Error generando PDF saldo empleado id={}: {}",
                    saldo.getEmpleado().getId(), e.getMessage(), e);
            throw new IllegalStateException(
                    "Error generando PDF de saldo para empleado: " + e.getMessage(), e);
        }
    }

    /**
     * Segunda pasada para E47: sobreescribe la numeracion "Pagina X" con
     * "Pagina X de Y" una vez conocido el total de paginas del empleado.
     * Patron identico a sobreescribirNumeracion() de E45.
     *
     * @param pdfBytes     bytes del PDF generado en primera pasada
     * @param totalPaginas total de paginas del documento del empleado
     * @return bytes del PDF con numeracion corregida
     */
    private byte[] sobreescribirNumeracionSaldos(byte[] pdfBytes, int totalPaginas) {
        try (ByteArrayOutputStream baos2 = new ByteArrayOutputStream()) {
            com.itextpdf.kernel.pdf.PdfReader reader =
                    new com.itextpdf.kernel.pdf.PdfReader(
                            new java.io.ByteArrayInputStream(pdfBytes));
            PdfWriter writer2 = new PdfWriter(baos2);
            PdfDocument pdfDoc2 = new PdfDocument(reader, writer2);

            PdfFont font = PdfFontFactory.createFont(
                    com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

            for (int i = 1; i <= pdfDoc2.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc2.getPage(i);
                Rectangle pageSize = page.getPageSize();
                PdfCanvas canvas = new PdfCanvas(page);
                Canvas c = new Canvas(canvas, pageSize);

                // Rectangulo blanco para tapar el texto anterior de numeracion
                float pieY = MARGEN / 2f;
                canvas.setFillColor(ColorConstants.WHITE)
                      .rectangle(pageSize.getRight() - MARGEN - 105f, pieY - 5f, 110f, 14f)
                      .fill();

                // Texto correcto "Pagina X de Y"
                c.add(new Paragraph("Página " + i + " de " + totalPaginas)
                        .setFont(font).setFontSize(FONT_SIZE_PIE)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setFixedPosition(pageSize.getRight() - MARGEN - 100f, pieY - 2f, 100f));
                c.close();
            }

            pdfDoc2.close();
            return baos2.toByteArray();
        } catch (Exception e) {
            log.warn("Segunda pasada de numeracion E47 fallida, devolviendo PDF sin numeracion total: {}",
                    e.getMessage());
            return pdfBytes;
        }
    }

    // Manejador de pie por pagina (E47)

    /**
     * Handler de pie de pagina para E47. Patron identico a CabeceraPieHandler
     * de E45 pero sin cabecera: dibuja unicamente la linea horizontal azul,
     * el texto "StaffFlow — Generado el..." y la numeracion provisional
     * "Pagina X" en cada pagina via PdfDocumentEvent.START_PAGE.
     * La segunda pasada sobreescribirNumeracionSaldos() reemplaza "Pagina X"
     * por "Pagina X de Y" una vez conocido el total.
     */
    private class PieSaldosHandler implements IEventHandler {
        private final PdfDocument pdfDoc;

        PieSaldosHandler(PdfDocument pdfDoc) {
            this.pdfDoc = pdfDoc;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfPage page = docEvent.getPage();
            int numPagina = pdfDoc.getPageNumber(page);
            Rectangle pageSize = page.getPageSize();
            PdfCanvas canvas = new PdfCanvas(page);

            try {
                PdfFont font = PdfFontFactory.createFont(
                        com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

                float pieY = MARGEN / 2f;

                // Linea horizontal azul (identica a E45)
                canvas.setStrokeColor(COLOR_CABECERA)
                      .setLineWidth(1f)
                      .moveTo(MARGEN, pieY + 10f)
                      .lineTo(pageSize.getRight() - MARGEN, pieY + 10f)
                      .stroke();

                Canvas c = new Canvas(canvas, pageSize);

                // "StaffFlow — Generado el..." (izquierda)
                c.add(new Paragraph("StaffFlow — Generado el "
                        + LocalDateTime.now().format(FMT_GENERA))
                        .setFont(font).setFontSize(FONT_SIZE_PIE)
                        .setFontColor(ColorConstants.GRAY)
                        .setFixedPosition(MARGEN, pieY - 2f, 300f));

                // Numeracion provisional "Pagina X" (la segunda pasada añade "de Y")
                c.add(new Paragraph("Página " + numPagina)
                        .setFont(font).setFontSize(FONT_SIZE_PIE)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setFixedPosition(pageSize.getRight() - MARGEN - 100f, pieY - 2f, 100f));

                c.close();
            } catch (Exception e) {
                log.error("Error dibujando pie de pagina E47: {}", e.getMessage());
            }
        }
    }

    // Bloques de contenido reutilizables

    /** Agrega el bloque de resumen (dias trabajados, horas, ausencias). */
    private void agregarResumen(Document doc, Map<String, Object> resumen) throws Exception {
        PdfFont fontBold = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont font = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        Table tabla = new Table(new float[]{200f, 150f})
                .setMarginTop(12f)
                .setMarginBottom(10f)
                .setBackgroundColor(new DeviceRgb(244, 246, 251));

        agregarFilaResumen(tabla, "Días trabajados",
                String.valueOf(resumen.getOrDefault("diasTrabajados", 0)), fontBold, font);
        agregarFilaResumen(tabla, "Horas efectivas",
                String.valueOf(resumen.getOrDefault("horasEfectivas", "0h 00min")), fontBold, font);
        agregarFilaResumen(tabla, "Días libres",
                String.valueOf(resumen.getOrDefault("diasLibres", 0)), fontBold, font);

        String[] tiposAusencia = {
            "dia_libre_compensatorio", "festivo_nacional", "festivo_local",
            "vacaciones", "asunto_propio", "permiso_retribuido",
            "baja_medica", "ausencia_injustificada"
        };
        String[] etiquetas = {
            "Día libre compensatorio", "Festivo nacional", "Festivo local",
            "Vacaciones", "Asunto propio", "Permiso retribuido",
            "Baja médica", "Ausencia injustificada"
        };
        for (int i = 0; i < tiposAusencia.length; i++) {
            int count = (int) resumen.getOrDefault(tiposAusencia[i], 0);
            if (count > 0) {
                agregarFilaResumen(tabla, etiquetas[i], String.valueOf(count), fontBold, font);
            }
        }

        doc.add(tabla);
    }

    private void agregarFilaResumen(Table tabla, String label, String valor,
                                     PdfFont fontBold, PdfFont font) {
        tabla.addCell(new Cell()
                .add(new Paragraph(label).setFont(fontBold).setFontSize(FONT_SIZE_NORMAL))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(3f));
        tabla.addCell(new Cell()
                .add(new Paragraph(valor).setFont(font).setFontSize(FONT_SIZE_NORMAL))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(3f));
    }

    /** Agrega la tabla de detalle de jornada. */
    @SuppressWarnings("unchecked")
    private void agregarTablaDetalle(Document doc,
                                      List<Map<String, Object>> detalle,
                                      Empleado empleado) throws Exception {
        // Horas diarias segun contrato (jornada semanal / 5 dias laborables)
        int minutosDiarios = (empleado != null && empleado.getJornadaSemanalHoras() != null)
                ? (int) Math.round(empleado.getJornadaSemanalHoras() * 60 / 5)
                : 480; // fallback 8h
        PdfFont fontBold = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont font = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        // Columnas: Fecha+dia, Tipo, Entrada, Salida, Ini.pausa, Fin pausa, Duracion, Total pausas, Horas efectivas
        float[] anchos = {70f, 90f, 35f, 35f, 38f, 38f, 25f, 38f, 42f};
        Table tabla = new Table(anchos).setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(10f);

        // Cabecera
        String[] cabeceras = {"Fecha", "Tipo", "Entrada", "Salida",
                              "Inicio pausa", "Fin pausa", "Duración", "Total pausas", "Horas efectivas"};
        for (String cab : cabeceras) {
            tabla.addHeaderCell(new Cell()
                    .add(new Paragraph(cab).setFont(fontBold)
                            .setFontSize(FONT_SIZE_CABECERA).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(COLOR_CABECERA)
                    .setPadding(4f)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        // Filas con subtotales semanales
        int semanaActual = -1;
        int minutosSemanales = 0;

        for (Map<String, Object> dia : detalle) {
            String tipo = String.valueOf(dia.getOrDefault("tipo", ""));
            boolean tieneDatos = dia.containsKey("horaEntrada");
            boolean esManual = Boolean.TRUE.equals(dia.get("esManual"));
            boolean esAusencia = !tipo.equals("NORMAL") && !tipo.equals("DIA_LIBRE")
                    && !tipo.equals("SIN_REGISTRO");
            boolean esLibre = tipo.equals("DIA_LIBRE") || tipo.equals("SIN_REGISTRO");

            // Detectar cambio de semana para insertar subtotal
            String fechaStr2 = String.valueOf(dia.getOrDefault("fecha", ""));
            try {
                java.time.LocalDate ld2 = java.time.LocalDate.parse(fechaStr2);
                int semana = ld2.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
                if (semanaActual == -1) {
                    semanaActual = semana;
                } else if (semana != semanaActual) {
                    // Insertar fila subtotal de la semana anterior
                    DeviceRgb colorSub = new DeviceRgb(220, 228, 245);
                    tabla.addCell(new Cell(1, 8)
                            .add(new Paragraph("Subtotal semana " + semanaActual)
                                    .setFont(fontBold).setFontSize(FONT_SIZE_CABECERA)
                                    .setFontColor(COLOR_CABECERA))
                            .setBackgroundColor(colorSub)
                            .setPadding(3f));
                    tabla.addCell(new Cell()
                            .add(new Paragraph(formatearMinutos(minutosSemanales))
                                    .setFont(fontBold).setFontSize(FONT_SIZE_CABECERA)
                                    .setFontColor(COLOR_CABECERA)
                                    .setTextAlignment(TextAlignment.CENTER))
                            .setBackgroundColor(colorSub)
                            .setPadding(3f));
                    semanaActual = semana;
                    minutosSemanales = 0;
                }
            } catch (Exception ignored) {}

            // Acumular minutos de esta fila para el subtotal semanal
            if (tieneDatos) {
                String hef = str(dia.get("horasEfectivas"));
                minutosSemanales += parsearMinutos(hef);
            } else if (tipo.equals("BAJA_MEDICA") || tipo.equals("PERMISO_RETRIBUIDO")) {
                minutosSemanales += minutosDiarios;
            }

            DeviceRgb colorFila = esAusencia ? COLOR_AUSENCIA
                    : (esLibre ? COLOR_LIBRE : null);

            List<Map<String, Object>> pausas =
                    (List<Map<String, Object>>) dia.getOrDefault("pausas", List.of());

            int filasPausa = Math.max(1, pausas.size());
            for (int i = 0; i < filasPausa; i++) {
                if (i == 0) {
                    // Fila principal del dia — orden: Fecha, Tipo, Entrada, Salida,
                    // Ini.pausa, Fin pausa, Duracion, Total pausas, Horas efectivas
                    String fechaStr = String.valueOf(dia.getOrDefault("fecha", ""));
                    // Anadir dia de la semana a la fecha
                    String fechaConDia = fechaStr;
                    try {
                        java.time.LocalDate ld = java.time.LocalDate.parse(fechaStr);
                        String diaSem = ld.format(FMT_DIA_SEM);
                        // Capitalizar primera letra
                        diaSem = diaSem.substring(0,1).toUpperCase() + diaSem.substring(1).replace(".","");
                        fechaConDia = fechaStr + " " + diaSem;
                    } catch (Exception ignored) {}

                    String tipoLeg = tipoLegible(tipo);
                    String entrada = tieneDatos ? str(dia.get("horaEntrada"))
                            + (esManual ? " *" : "") : "—";
                    String salida  = tieneDatos ? str(dia.get("horaSalida"))
                            + (esManual ? " *" : "") : "—";
                    // Para baja medica y permiso retribuido: horas segun contrato aunque no haya fichaje
                    boolean esRetribuida = tipo.equals("BAJA_MEDICA") || tipo.equals("PERMISO_RETRIBUIDO");
                    String horas;
                    if (tieneDatos) {
                        horas = str(dia.get("horasEfectivas"));
                    } else if (esRetribuida) {
                        horas = formatearMinutos(minutosDiarios);
                    } else {
                        horas = "—";
                    }
                    String pausasTot = tieneDatos
                            ? dia.get("totalPausasMinutos") + " min" : "—";

                    agregarCeldaTabla(tabla, fechaConDia, font, colorFila, false);
                    agregarCeldaTabla(tabla, tipoLeg, font, colorFila, esLibre);
                    agregarCeldaTabla(tabla, entrada, font, colorFila, false);
                    agregarCeldaTabla(tabla, salida,  font, colorFila, false);

                    if (!pausas.isEmpty()) {
                        Map<String, Object> p = pausas.get(0);
                        boolean pManual = Boolean.TRUE.equals(p.get("esManual"));
                        agregarCeldaTabla(tabla, str(p.get("inicio"))
                                + (pManual ? " *" : ""), font, colorFila, false);
                        agregarCeldaTabla(tabla, str(p.get("fin"))
                                + (pManual ? " *" : ""), font, colorFila, false);
                        agregarCeldaTabla(tabla, str(p.get("duracion")), font, colorFila, false);
                    } else {
                        agregarCeldaTabla(tabla, "—", font, colorFila, false);
                        agregarCeldaTabla(tabla, "—", font, colorFila, false);
                        agregarCeldaTabla(tabla, "—", font, colorFila, false);
                    }
                    agregarCeldaTabla(tabla, pausasTot, font, colorFila, false);
                    agregarCeldaTabla(tabla, horas,   font, colorFila, false);
                } else {
                    // Filas adicionales de pausa — cols 1-4 vacias, cols 5-7 datos pausa, cols 8-9 vacias
                    Map<String, Object> p = pausas.get(i);
                    boolean pManual = Boolean.TRUE.equals(p.get("esManual"));
                    DeviceRgb colorExtra = new DeviceRgb(248, 249, 251);
                    for (int k = 0; k < 4; k++) {
                        agregarCeldaTabla(tabla, "", font, colorExtra, false);
                    }
                    agregarCeldaTabla(tabla, str(p.get("inicio"))
                            + (pManual ? " *" : ""), font, colorExtra, false);
                    agregarCeldaTabla(tabla, str(p.get("fin"))
                            + (pManual ? " *" : ""), font, colorExtra, false);
                    agregarCeldaTabla(tabla, str(p.get("duracion")), font, colorExtra, false);
                    agregarCeldaTabla(tabla, "", font, colorExtra, false);
                    agregarCeldaTabla(tabla, "", font, colorExtra, false);
                }
            }
        }

        // Fila total
        int totalMinutos = detalle.stream()
                .mapToInt(d -> {
                    if (d.containsKey("horasEfectivas")) {
                        return parsearMinutos(str(d.get("horasEfectivas")));
                    }
                    String t = String.valueOf(d.getOrDefault("tipo", ""));
                    if (t.equals("BAJA_MEDICA") || t.equals("PERMISO_RETRIBUIDO")) {
                        return minutosDiarios;
                    }
                    return 0;
                }).sum();

        // Subtotal de la ultima semana
        if (semanaActual != -1) {
            DeviceRgb colorSub = new DeviceRgb(220, 228, 245);
            tabla.addCell(new Cell(1, 8)
                    .add(new Paragraph("Subtotal semana " + semanaActual)
                            .setFont(fontBold).setFontSize(FONT_SIZE_CABECERA)
                            .setFontColor(COLOR_CABECERA))
                    .setBackgroundColor(colorSub)
                    .setPadding(3f));
            tabla.addCell(new Cell()
                    .add(new Paragraph(formatearMinutos(minutosSemanales))
                            .setFont(fontBold).setFontSize(FONT_SIZE_CABECERA)
                            .setFontColor(COLOR_CABECERA)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(colorSub)
                    .setPadding(3f));
        }

        // Fila total: col 1-8 etiqueta+vacías, col 9 horas efectivas totales
        tabla.addCell(new Cell(1, 4)
                .add(new Paragraph("Total").setFont(fontBold)
                        .setFontSize(FONT_SIZE_NORMAL).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(COLOR_TOTAL)
                .setPadding(4f));
        for (int k = 0; k < 4; k++) {
            tabla.addCell(new Cell()
                    .add(new Paragraph("").setFontSize(FONT_SIZE_NORMAL))
                    .setBackgroundColor(COLOR_TOTAL)
                    .setPadding(4f));
        }
        tabla.addCell(new Cell()
                .add(new Paragraph(formatearMinutos(totalMinutos)).setFont(fontBold)
                        .setFontSize(FONT_SIZE_NORMAL).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(COLOR_TOTAL)
                .setPadding(4f)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(tabla);
    }

    /** Agrega la seccion de intervenciones manuales (solo si existen). */
    @SuppressWarnings("unchecked")
    private void agregarIntervencionesManuales(Document doc,
                                                List<Map<String, Object>> detalle) throws Exception {
        PdfFont fontBold = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont font = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        List<String> fichajesManuales = new java.util.ArrayList<>();
        List<String> pausasManuales   = new java.util.ArrayList<>();

        for (Map<String, Object> dia : detalle) {
            if (Boolean.TRUE.equals(dia.get("esManual"))) {
                String linea = str(dia.get("fecha"));
                Object obs = dia.get("observaciones");
                if (obs != null && !obs.toString().isBlank()) {
                    linea += " — " + obs;
                }
                fichajesManuales.add(linea);
            }
            List<Map<String, Object>> pausas =
                    (List<Map<String, Object>>) dia.getOrDefault("pausas", List.of());
            for (Map<String, Object> p : pausas) {
                if (Boolean.TRUE.equals(p.get("esManual"))) {
                    String linea = str(dia.get("fecha")) + " " + str(p.get("inicio"));
                    Object obs = p.get("observaciones");
                    if (obs != null && !obs.toString().isBlank()) {
                        linea += " — " + obs;
                    }
                    pausasManuales.add(linea);
                }
            }
        }

        if (fichajesManuales.isEmpty() && pausasManuales.isEmpty()) {
            return;
        }

        // Caja amarilla de intervenciones
        Table caja = new Table(1).setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(10f);

        // Titulo de la seccion
        caja.addCell(new Cell()
                .add(new Paragraph("* Intervenciones manuales — Los valores marcados con asterisco fueron modificados manualmente por un encargado o administrador")
                        .setFont(fontBold).setFontSize(FONT_SIZE_CABECERA))
                .setBackgroundColor(COLOR_INTERV)
                .setPadding(5f)
                .setBorder(new SolidBorder(new DeviceRgb(208, 192, 144), 0.5f)));

        // Fichajes manuales
        if (!fichajesManuales.isEmpty()) {
            StringBuilder sb = new StringBuilder("Fichajes:\n");
            for (String item : fichajesManuales) sb.append("  • ").append(item).append("\n");
            caja.addCell(new Cell()
                    .add(new Paragraph(sb.toString()).setFont(font).setFontSize(FONT_SIZE_CABECERA))
                    .setPadding(5f)
                    .setBorder(new SolidBorder(new DeviceRgb(208, 192, 144), 0.5f)));
        }

        // Pausas manuales
        if (!pausasManuales.isEmpty()) {
            StringBuilder sb = new StringBuilder("Pausas:\n");
            for (String item : pausasManuales) sb.append("  • ").append(item).append("\n");
            caja.addCell(new Cell()
                    .add(new Paragraph(sb.toString()).setFont(font).setFontSize(FONT_SIZE_CABECERA))
                    .setPadding(5f)
                    .setBorder(new SolidBorder(new DeviceRgb(208, 192, 144), 0.5f)));
        }

        doc.add(caja);
    }

    /** Agrega la seccion de firma al final del informe. */
    private void agregarSeccionFirma(Document doc, String nombreEmpleado) throws Exception {
        PdfFont fontBold = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont font = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        doc.add(new Paragraph("Conforme con el contenido del presente informe:")
                .setFont(fontBold).setFontSize(FONT_SIZE_NORMAL)
                .setMarginTop(30f).setMarginBottom(60f));  // espacio en blanco para firma fisica

        // Tabla de firma: linea firma | espacio | linea fecha
        Table tablaFirma = new Table(new float[]{300f, 60f, 150f})
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(10f);

        // Columna firma
        Cell celdaFirma = new Cell()
                .setBorderTop(new SolidBorder(ColorConstants.BLACK, 1f))
                .setBorderBottom(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderLeft(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderRight(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPaddingTop(6f);
        celdaFirma.add(new Paragraph("Firma del empleado")
                .setFont(font).setFontSize(FONT_SIZE_CABECERA)
                .setFontColor(ColorConstants.GRAY));
        celdaFirma.add(new Paragraph(nombreEmpleado)
                .setFont(fontBold).setFontSize(FONT_SIZE_NORMAL));
        tablaFirma.addCell(celdaFirma);

        // Espacio separador
        tablaFirma.addCell(new Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));

        // Columna fecha
        Cell celdaFecha = new Cell()
                .setBorderTop(new SolidBorder(ColorConstants.BLACK, 1f))
                .setBorderBottom(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderLeft(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderRight(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPaddingTop(6f);
        celdaFecha.add(new Paragraph("Fecha")
                .setFont(font).setFontSize(FONT_SIZE_CABECERA)
                .setFontColor(ColorConstants.GRAY));
        tablaFirma.addCell(celdaFecha);

        doc.add(tablaFirma);
    }

    /** Cabecera global del informe de saldos (E47). */
    private void agregarCabeceraInformeSaldos(Document doc, EmpresaResponse empresa,
                                               Integer anio) throws Exception {
        PdfFont fontBold = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont font = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        Table tabla = new Table(new float[]{300f, 180f})
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(10f);

        // Datos empresa (izquierda)
        Cell izq = new Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        izq.add(new Paragraph(empresa.getNombreEmpresa())
                .setFont(fontBold).setFontSize(FONT_SIZE_TITULO)
                .setFontColor(new DeviceRgb(44, 62, 107)));
        izq.add(new Paragraph("CIF: " + empresa.getCif())
                .setFont(font).setFontSize(FONT_SIZE_NORMAL));
        if (empresa.getDireccion() != null) {
            izq.add(new Paragraph(empresa.getDireccion())
                    .setFont(font).setFontSize(FONT_SIZE_NORMAL));
        }
        tabla.addCell(izq);

        // Logo (derecha)
        Cell der = new Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT)
                .setHorizontalAlignment(HorizontalAlignment.RIGHT);
        Image logo = cargarLogo(empresa.getLogoPath());
        if (logo != null) {
            logo.setMaxWidth(100f).setMaxHeight(50f);
            der.add(logo);
        }
        tabla.addCell(der);
        doc.add(tabla);

        // Separador
        doc.add(new Paragraph()
                .setBorderBottom(new SolidBorder(COLOR_CABECERA, 2f))
                .setMarginBottom(8f));

        // Titulo del informe
        doc.add(new Paragraph("INFORME DE SALDOS ANUALES — " + anio)
                .setFont(fontBold).setFontSize(FONT_SIZE_TITULO)
                .setFontColor(COLOR_CABECERA)
                .setMarginBottom(5f));

        doc.add(new Paragraph("Generado el " + LocalDate.now().format(FMT_FECHA))
                .setFont(font).setFontSize(FONT_SIZE_NORMAL)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(15f));
    }

    /** Bloque de saldo de un empleado en E47. */
    private void agregarBloqueSaldo(Document doc, EmpresaResponse empresa,
                                     SaldoAnual saldo, Integer anio) throws Exception {
        PdfFont fontBold = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont font = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        Empleado emp = saldo.getEmpleado();

        // Datos del empleado
        doc.add(new Paragraph(nombreCompleto(emp))
                .setFont(fontBold).setFontSize(11f)
                .setMarginBottom(3f));
        doc.add(new Paragraph(
                "Nº empleado: " + emp.getNumeroEmpleado()
                + "   Categoría: " + emp.getCategoria()
                + "   Jornada: " + emp.getJornadaSemanalHoras() + "h/semana")
                .setFont(font).setFontSize(FONT_SIZE_NORMAL)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(10f));

        // Tabla de saldos
        float[] anchos = {140f, 75f, 100f, 100f, 100f};
        Table tabla = new Table(anchos).setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(10f);

        // Cabecera
        String[] cabs = {"Concepto", "Derecho anual", "Pendientes año anterior",
                         "Consumidos año en curso", "Disponibles año en curso"};
        for (String c : cabs) {
            tabla.addHeaderCell(new Cell()
                    .add(new Paragraph(c).setFont(fontBold)
                            .setFontSize(FONT_SIZE_CABECERA).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(COLOR_CABECERA)
                    .setPadding(4f));
        }

        // Vacaciones
        agregarFilaSaldo(tabla, "Días de vacaciones",
                saldo.getDiasVacacionesDerechoAnio(),
                saldo.getDiasVacacionesPendientesAnioAnterior(),
                saldo.getDiasVacacionesConsumidos(),
                saldo.getDiasVacacionesDisponibles(),
                fontBold, font);

        // Asuntos propios
        agregarFilaSaldo(tabla, "Días de asuntos propios",
                saldo.getDiasAsuntosPropiosDerechoAnio(),
                saldo.getDiasAsuntosPropiosPendientesAnterior(),
                saldo.getDiasAsuntosPropiosConsumidos(),
                saldo.getDiasAsuntosPropiosDisponibles(),
                fontBold, font);

        doc.add(tabla);

        // Resto de datos en tabla simple
        float[] anchos2 = {200f, 100f};
        Table tabla2 = new Table(anchos2).setMarginBottom(10f);

        agregarFilaSimple(tabla2, "Días trabajados",
                String.valueOf(saldo.getDiasTrabajados()), font);
        agregarFilaSimple(tabla2, "Días baja médica",
                String.valueOf(saldo.getDiasBajaMedica()), font);
        agregarFilaSimple(tabla2, "Días permiso retribuido",
                String.valueOf(saldo.getDiasPermisoRetribuido()), font);
        agregarFilaSimple(tabla2, "Días ausencia injustificada",
                String.valueOf(saldo.getDiasAusenciaInjustificada()), font);
        agregarFilaSimple(tabla2, "Horas ausencia retribuida",
                formatearDecimal(saldo.getHorasAusenciaRetribuida()), font);

        // Saldo horas con color
        double sh = saldo.getSaldoHoras() != null
                ? saldo.getSaldoHoras().doubleValue() : 0.0;
        DeviceRgb colorSaldo = sh > 0 ? COLOR_POS : (sh < 0 ? COLOR_NEG : null);
        String prefijo = sh > 0 ? "+" : "";
        Cell celdaSaldo = new Cell()
                .add(new Paragraph("Saldo horas").setFont(font).setFontSize(FONT_SIZE_NORMAL))
                .setPadding(3f)
                .setBorder(new SolidBorder(new DeviceRgb(220, 220, 220), 0.5f));
        Cell celdaValSaldo = new Cell()
                .add(new Paragraph(prefijo + formatearDecimal(saldo.getSaldoHoras()))
                        .setFont(font).setFontSize(FONT_SIZE_NORMAL)
                        .setFontColor(colorSaldo != null ? colorSaldo : ColorConstants.BLACK))
                .setPadding(3f)
                .setBorder(new SolidBorder(new DeviceRgb(220, 220, 220), 0.5f));
        tabla2.addCell(celdaSaldo);
        tabla2.addCell(celdaValSaldo);

        if (saldo.getCalculadoHastaFecha() != null) {
            agregarFilaSimple(tabla2, "Calculado hasta",
                    saldo.getCalculadoHastaFecha().format(FMT_FECHA), font);
        }

        doc.add(tabla2);
    }

    private void agregarFilaSaldo(Table tabla, String concepto,
                                   int derecho, int pendiente, int consumido, int disponible,
                                   PdfFont fontBold, PdfFont font) {
        tabla.addCell(new Cell()
                .add(new Paragraph(concepto).setFont(fontBold).setFontSize(FONT_SIZE_NORMAL))
                .setPadding(3f));
        tabla.addCell(new Cell()
                .add(new Paragraph(String.valueOf(derecho)).setFont(font)
                        .setFontSize(FONT_SIZE_NORMAL).setTextAlignment(TextAlignment.CENTER))
                .setPadding(3f));
        tabla.addCell(new Cell()
                .add(new Paragraph(String.valueOf(pendiente)).setFont(font)
                        .setFontSize(FONT_SIZE_NORMAL).setTextAlignment(TextAlignment.CENTER))
                .setPadding(3f));
        tabla.addCell(new Cell()
                .add(new Paragraph(String.valueOf(consumido)).setFont(font)
                        .setFontSize(FONT_SIZE_NORMAL).setTextAlignment(TextAlignment.CENTER))
                .setPadding(3f));
        DeviceRgb color = disponible > 0 ? COLOR_POS : (disponible < 0 ? COLOR_NEG : null);
        tabla.addCell(new Cell()
                .add(new Paragraph(String.valueOf(disponible)).setFont(font)
                        .setFontSize(FONT_SIZE_NORMAL)
                        .setFontColor(color != null ? color : ColorConstants.BLACK)
                        .setTextAlignment(TextAlignment.CENTER))
                .setPadding(3f));
    }

    private void agregarFilaSimple(Table tabla, String label, String valor, PdfFont font) {
        tabla.addCell(new Cell()
                .add(new Paragraph(label).setFont(font).setFontSize(FONT_SIZE_NORMAL))
                .setPadding(3f)
                .setBorder(new SolidBorder(new DeviceRgb(220, 220, 220), 0.5f)));
        tabla.addCell(new Cell()
                .add(new Paragraph(valor).setFont(font).setFontSize(FONT_SIZE_NORMAL))
                .setPadding(3f)
                .setBorder(new SolidBorder(new DeviceRgb(220, 220, 220), 0.5f)));
    }

    private void agregarCeldaTabla(Table tabla, String texto, PdfFont font,
                                    DeviceRgb colorFondo, boolean cursiva) {
        Paragraph p = new Paragraph(texto != null ? texto : "")
                .setFont(font).setFontSize(FONT_SIZE_NORMAL);
        if (cursiva) p.setItalic();
        Cell cell = new Cell().add(p).setPadding(3f)
                .setBorder(new SolidBorder(new DeviceRgb(221, 221, 221), 0.5f));
        if (colorFondo != null) cell.setBackgroundColor(colorFondo);
        tabla.addCell(cell);
    }

    // Manejador de cabecera/pie por pagina (E45)

    private class CabeceraPieHandler implements IEventHandler {
        private final EmpresaResponse empresa;
        private final Empleado        empleado;
        private final LocalDate       desde;
        private final LocalDate       hasta;
        private final PdfDocument     pdfDoc;
        private int totalPaginas = 0;

        CabeceraPieHandler(EmpresaResponse empresa, Empleado empleado,
                           LocalDate desde, LocalDate hasta, PdfDocument pdfDoc) {
            this.empresa  = empresa;
            this.empleado = empleado;
            this.desde    = desde;
            this.hasta    = hasta;
            this.pdfDoc   = pdfDoc;
        }

        void setTotalPaginas(int total) { this.totalPaginas = total; }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfPage page = docEvent.getPage();
            int numPagina = pdfDoc.getPageNumber(page);
            Rectangle pageSize = page.getPageSize();
            PdfCanvas canvas = new PdfCanvas(page);

            try {
                PdfFont fontBold = PdfFontFactory.createFont(
                        com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
                PdfFont font = PdfFontFactory.createFont(
                        com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

                // --- CABECERA ---
                float cabY = pageSize.getTop() - MARGEN;

                // Linea azul inferior de cabecera
                canvas.setStrokeColor(COLOR_CABECERA)
                      .setLineWidth(1.5f)
                      .moveTo(MARGEN, cabY - ALTO_CABECERA_PAG)
                      .lineTo(pageSize.getRight() - MARGEN, cabY - ALTO_CABECERA_PAG)
                      .stroke();

                // Empresa + CIF + direccion en una sola linea (izquierda)
                Canvas c = new Canvas(canvas, page.getPageSize());
                String lineaEmpresa = empresa.getNombreEmpresa()
                        + "   CIF: " + empresa.getCif()
                        + (empresa.getDireccion() != null && !empresa.getDireccion().isBlank()
                           ? "   " + empresa.getDireccion() : "");
                c.add(new Paragraph(lineaEmpresa)
                        .setFont(fontBold).setFontSize(9f).setFontColor(COLOR_CABECERA)
                        .setFixedPosition(MARGEN, cabY - 16f, 400f));

                // Logo (derecha, tope superior de pagina)
                Image logo = cargarLogo(empresa.getLogoPath());
                if (logo != null) {
                    logo.setMaxWidth(90f).setMaxHeight(40f)
                        .setFixedPosition(pageSize.getRight() - MARGEN - 95f,
                                pageSize.getTop() - MARGEN - 42f);
                    c.add(logo);
                }

                // Separador fino entre empresa y datos empleado
                canvas.setStrokeColor(new DeviceRgb(200, 210, 230))
                      .setLineWidth(0.5f)
                      .moveTo(MARGEN, cabY - 22f)
                      .lineTo(pageSize.getRight() - MARGEN - 110f, cabY - 22f)
                      .stroke();

                // Titulo
                c.add(new Paragraph("INFORME DE FICHAJES")
                        .setFont(fontBold).setFontSize(FONT_SIZE_TITULO).setFontColor(COLOR_CABECERA)
                        .setFixedPosition(MARGEN, cabY - 40f, 300f));

                // Nombre empleado
                c.add(new Paragraph(nombreCompleto(empleado))
                        .setFont(fontBold).setFontSize(9f)
                        .setFixedPosition(MARGEN, cabY - 56f, 300f));

                // Datos empleado
                c.add(new Paragraph(
                        "Nº " + empleado.getNumeroEmpleado()
                        + "   " + empleado.getCategoria()
                        + "   " + empleado.getJornadaSemanalHoras() + "h/sem"
                        + "   Período: " + desde.format(FMT_FECHA) + " – " + hasta.format(FMT_FECHA))
                        .setFont(font).setFontSize(FONT_SIZE_CABECERA)
                        .setFontColor(ColorConstants.GRAY)
                        .setFixedPosition(MARGEN, cabY - 68f, 420f));

                // --- PIE ---
                float pieY = MARGEN / 2f;
                canvas.setStrokeColor(COLOR_CABECERA)
                      .setLineWidth(1f)
                      .moveTo(MARGEN, pieY + 10f)
                      .lineTo(pageSize.getRight() - MARGEN, pieY + 10f)
                      .stroke();

                c.add(new Paragraph("StaffFlow — Generado el "
                        + LocalDateTime.now().format(FMT_GENERA))
                        .setFont(font).setFontSize(FONT_SIZE_PIE)
                        .setFontColor(ColorConstants.GRAY)
                        .setFixedPosition(MARGEN, pieY - 2f, 300f));

                String paginaTexto = "Página " + numPagina
                        + (totalPaginas > 0 ? " de " + totalPaginas : "");
                c.add(new Paragraph(paginaTexto)
                        .setFont(font).setFontSize(FONT_SIZE_PIE)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setFixedPosition(pageSize.getRight() - MARGEN - 100f, pieY - 2f, 100f));

                c.close();
            } catch (Exception e) {
                log.error("Error dibujando cabecera/pie de pagina: {}", e.getMessage());
            }
        }
    }

    // Escritura de numeracion final (requiere segunda pasada)

    /**
     * Segunda pasada sobre el PDF ya generado para sobreescribir la numeracion
     * "Pagina X" con "Pagina X de Y" ahora que se conoce el total de paginas.
     * Usa PdfReader + nuevo PdfWriter sobre un segundo ByteArrayOutputStream.
     */
    private byte[] sobreescribirNumeracion(byte[] pdfBytes, int totalPaginas,
                                            EmpresaResponse empresa, Empleado empleado,
                                            LocalDate desde, LocalDate hasta) {
        try (ByteArrayOutputStream baos2 = new ByteArrayOutputStream()) {
            com.itextpdf.kernel.pdf.PdfReader reader =
                    new com.itextpdf.kernel.pdf.PdfReader(
                            new java.io.ByteArrayInputStream(pdfBytes));
            PdfWriter writer2 = new PdfWriter(baos2);
            PdfDocument pdfDoc2 = new PdfDocument(reader, writer2);

            PdfFont font = PdfFontFactory.createFont(
                    com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

            for (int i = 1; i <= pdfDoc2.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc2.getPage(i);
                Rectangle pageSize = page.getPageSize();
                PdfCanvas canvas = new PdfCanvas(page);
                Canvas c = new Canvas(canvas, pageSize);

                // Rectangulo blanco para tapar el texto anterior de numeracion
                float pieY = MARGEN / 2f;
                canvas.setFillColor(ColorConstants.WHITE)
                      .rectangle(pageSize.getRight() - MARGEN - 105f, pieY - 5f, 110f, 14f)
                      .fill();

                // Texto correcto "Pagina X de Y"
                c.add(new Paragraph("Página " + i + " de " + totalPaginas)
                        .setFont(font).setFontSize(FONT_SIZE_PIE)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setFixedPosition(pageSize.getRight() - MARGEN - 100f, pieY - 2f, 100f));
                c.close();
            }

            pdfDoc2.close();
            return baos2.toByteArray();
        } catch (Exception e) {
            log.warn("Segunda pasada de numeracion fallida, devolviendo PDF sin numeracion total: {}",
                    e.getMessage());
            return pdfBytes;  // fallback: devolver el PDF sin "de Y"
        }
    }

    // Utilidades privadas

    /**
     * Carga el logo desde el classpath. Devuelve null si no existe o hay error.
     *
     * La ruta en BD puede ser "src/main/resources/static/logo_empresa.png" (dev)
     * o "/static/logo_empresa.png" (classpath). Se normaliza a /static/...
     */
    private Image cargarLogo(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) return null;
        try {
            // Normalizar: extraer desde "static/" en adelante
            String ruta;
            if (logoPath.contains("static/")) {
                ruta = "/" + logoPath.substring(logoPath.indexOf("static/"));
            } else {
                ruta = logoPath.startsWith("/") ? logoPath : "/" + logoPath;
            }
            InputStream is = getClass().getResourceAsStream(ruta);
            if (is == null) {
                // segundo intento: /static/ + nombre de fichero
                String nombre = ruta.substring(ruta.lastIndexOf('/'));
                is = getClass().getResourceAsStream("/static" + nombre);
            }
            if (is == null) {
                log.warn("Logo no encontrado en classpath para ruta: {}", logoPath);
                return null;
            }
            byte[] bytes = is.readAllBytes();
            return new Image(ImageDataFactory.create(bytes));
        } catch (Exception e) {
            log.warn("No se pudo cargar el logo desde {}: {}", logoPath, e.getMessage());
            return null;
        }
    }

    /** Obtiene los datos de empresa con fallback si no esta configurada. */
    private EmpresaResponse obtenerEmpresa() {
        try {
            return empresaService.obtenerEmpresa();
        } catch (Exception e) {
            return new EmpresaResponse(1L, "StaffFlow", "", null, null, null, null);
        }
    }

    /** Convierte un objeto a String, devolviendo "—" si es null. */
    private String str(Object obj) {
        if (obj == null) return "—";
        String s = obj.toString();
        return s.isBlank() ? "—" : s;
    }

    /** Nombre completo del empleado. */
    private String nombreCompleto(Empleado e) {
        String nombre = e.getNombre() + " " + e.getApellido1();
        if (e.getApellido2() != null && !e.getApellido2().isBlank()) {
            nombre += " " + e.getApellido2();
        }
        return nombre;
    }

    /** Nombre legible del tipo de fichaje. */
    private String tipoLegible(String tipo) {
        return switch (tipo) {
            case "NORMAL"                  -> "Normal";
            case "FESTIVO_NACIONAL"        -> "Festivo nacional";
            case "FESTIVO_LOCAL"           -> "Festivo local";
            case "VACACIONES"              -> "Vacaciones";
            case "ASUNTO_PROPIO"           -> "Asunto propio";
            case "PERMISO_RETRIBUIDO"      -> "Permiso retribuido";
            case "BAJA_MEDICA"             -> "Baja médica";
            case "DIA_LIBRE_COMPENSATORIO" -> "Día libre compensatorio";
            case "AUSENCIA_INJUSTIFICADA"  -> "Ausencia injustificada";
            case "DIA_LIBRE"               -> "Día libre";
            case "SIN_REGISTRO"            -> "Sin registro";
            default                        -> tipo;
        };
    }

    /** Formatea minutos a "Xh YYmin". */
    private String formatearMinutos(int minutos) {
        int h = minutos / 60;
        int m = minutos % 60;
        return h + "h " + String.format("%02d", m) + "min";
    }

    /** Parsea "Xh YYmin" a minutos totales. */
    private int parsearMinutos(String hef) {
        try {
            if (hef == null || hef.equals("—")) return 0;
            hef = hef.trim();
            int h = 0, m = 0;
            if (hef.contains("h")) {
                h = Integer.parseInt(hef.substring(0, hef.indexOf('h')).trim());
                String resto = hef.substring(hef.indexOf('h') + 1).trim();
                if (resto.contains("min")) {
                    m = Integer.parseInt(resto.replace("min", "").trim());
                }
            }
            return h * 60 + m;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Formatea BigDecimal a dos decimales con coma. */
    private String formatearDecimal(BigDecimal bd) {
        if (bd == null) return "0,00";
        return String.format("%.2f", bd).replace('.', ',');
    }

    // E20 — GET /api/v1/empleados/export?formato=pdf
    // RF-16: Exportar listado de empleados en PDF

    /**
     * Genera el PDF del listado de empleados (E20).
     *
     * <p>Tabla con cabecera corporativa y una fila por empleado. Columnas:
     * N° Empleado, Nombre completo, DNI, Categoría, Jornada (h/sem), Fecha Alta.</p>
     *
     * @param empleados lista de empleados a exportar
     * @return bytes del PDF generado
     */
    public byte[] exportarEmpleados(List<Empleado> empleados) {
        EmpresaResponse empresa = obtenerEmpresa();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(MARGEN, MARGEN, MARGEN, MARGEN);

            PdfFont fontBold = PdfFontFactory.createFont(
                    com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
            PdfFont fontNormal = PdfFontFactory.createFont(
                    com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

            // Cabecera del documento
            doc.add(new Paragraph(empresa.getNombreEmpresa() != null ? empresa.getNombreEmpresa() : "StaffFlow")
                    .setFont(fontBold)
                    .setFontSize(FONT_SIZE_TITULO)
                    .setFontColor(COLOR_CABECERA)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("Listado de empleados — generado el "
                    + LocalDateTime.now().format(FMT_GENERA))
                    .setFont(fontNormal)
                    .setFontSize(FONT_SIZE_PIE)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(12f));

            // Tabla: 6 columnas con anchos proporcionales
            float[] anchos = {70f, 140f, 70f, 90f, 60f, 70f};
            Table tabla = new Table(UnitValue.createPointArray(anchos));
            tabla.setWidth(UnitValue.createPercentValue(100));

            String[] cabeceras = {"N° Empleado", "Nombre completo", "DNI",
                    "Categoría", "Jornada h/sem", "Fecha alta"};
            for (String cab : cabeceras) {
                tabla.addHeaderCell(new Cell()
                        .add(new Paragraph(cab).setFont(fontBold).setFontSize(FONT_SIZE_CABECERA))
                        .setBackgroundColor(COLOR_CABECERA)
                        .setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setPadding(4f));
            }

            for (int i = 0; i < empleados.size(); i++) {
                Empleado e = empleados.get(i);
                DeviceRgb fondo = (i % 2 == 0) ? null : new DeviceRgb(245, 245, 245);
                String nombreCompleto = e.getNombre() + " " + e.getApellido1()
                        + (e.getApellido2() != null ? " " + e.getApellido2() : "");

                String[] celdas = {
                        e.getNumeroEmpleado(),
                        nombreCompleto,
                        e.getDni(),
                        e.getCategoria() != null ? e.getCategoria().name() : "—",
                        e.getJornadaSemanalHoras() + "h",
                        e.getFechaAlta() != null ? e.getFechaAlta().format(FMT_FECHA) : "—"
                };

                for (String valor : celdas) {
                    Cell celda = new Cell()
                            .add(new Paragraph(valor).setFont(fontNormal).setFontSize(FONT_SIZE_NORMAL))
                            .setPadding(3f);
                    if (fondo != null) celda.setBackgroundColor(fondo);
                    tabla.addCell(celda);
                }
            }

            doc.add(tabla);

            doc.add(new Paragraph("Total: " + empleados.size() + " empleados")
                    .setFont(fontBold)
                    .setFontSize(FONT_SIZE_NORMAL)
                    .setFontColor(COLOR_CABECERA)
                    .setMarginTop(8f));

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando PDF E20: {}", e.getMessage(), e);
            throw new IllegalStateException("Error generando el PDF de empleados: " + e.getMessage(), e);
        }
    }
}
