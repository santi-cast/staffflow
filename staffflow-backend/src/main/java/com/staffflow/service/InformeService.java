package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.Pausa;
import com.staffflow.domain.entity.SaldoAnual;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de informes de horas, saldos anuales y vacaciones en formato JSON o HTML.
 *
 * <p>Cubre los endpoints E42-E44 (Grupo 10). Devuelve datos estructurados
 * como Map o HTML imprimible según el parámetro ?formato= de la petición.
 * El formato HTML está diseñado para PrintManager + WebView en Android.</p>
 *
 * <p>D-027: E42 y E43 aceptan el parámetro opcional ?tipo= que filtra
 * el detalle por uno o varios tipos de jornada. Valores válidos: cualquier
 * valor del enum TipoFichaje más DIA_LIBRE y SIN_REGISTRO.</p>
 *
 * <p>D-029: E44 renombrado a GET /api/v1/informes/saldos. Acepta parámetros
 * opcionales ?empleadoId= (uno o varios ids separados por coma) y ?campos=
 * (bloques o campos individuales). Sin parámetros devuelve todos los empleados
 * activos con saldo en ese año y todos los campos.</p>
 *
 * <p>Lógica de construcción del detalle de jornada:
 * Para cada día del período se itera LocalDate.from(desde) hasta hasta.
 * Cada día puede ser:
 *   - Con fichaje en BD: se muestran los datos del fichaje y sus pausas.
 *   - Sin fichaje, fin de semana: DIA_LIBRE.
 *   - Sin fichaje, entre semana: SIN_REGISTRO.
 * Los días libres (DIA_LIBRE) y sin registro (SIN_REGISTRO) no tienen
 * entrada/salida ni pausas — todas las columnas muestran guión.</p>
 *
 * <p>Lógica del asterisco de intervención manual (M-007, M-008):
 * Un fichaje es manual si usuario.rol != EMPLEADO y
 * usuario.username != "terminal_service".
 * En v1.0 el asterisco aparece en entrada Y salida cuando el fichaje
 * completo fue creado manualmente (no se puede distinguir qué hora
 * fue tocada — ver L-005). Mismo criterio para pausas (L-006).</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.FichajeRepository
 * @see com.staffflow.domain.repository.PausaRepository
 * @see com.staffflow.domain.repository.SaldoAnualRepository
 */
@Service
@RequiredArgsConstructor
public class InformeService {

    private final FichajeRepository    fichajeRepository;
    private final PausaRepository      pausaRepository;
    private final EmpleadoRepository   empleadoRepository;
    private final SaldoAnualRepository saldoRepository;
    private final EmpresaService       empresaService;
    private final PlanificacionAusenciaRepository planificacionRepository;

    // Formateadores reutilizables
    private static final DateTimeFormatter FMT_FECHA  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA   = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FMT_GENERA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    // Abreviaturas de día de semana en español
    private static final Map<DayOfWeek, String> DIAS_ES = Map.of(
            DayOfWeek.MONDAY,    "lun",
            DayOfWeek.TUESDAY,   "mar",
            DayOfWeek.WEDNESDAY, "mié",
            DayOfWeek.THURSDAY,  "jue",
            DayOfWeek.FRIDAY,    "vie",
            DayOfWeek.SATURDAY,  "sáb",
            DayOfWeek.SUNDAY,    "dom"
    );

    // Nombres legibles de TipoFichaje para mostrar en el informe
    private static final Map<String, String> TIPO_LEGIBLE = new LinkedHashMap<>();
    static {
        TIPO_LEGIBLE.put("NORMAL",                  "Normal");
        TIPO_LEGIBLE.put("FESTIVO_NACIONAL",         "Festivo nacional");
        TIPO_LEGIBLE.put("FESTIVO_LOCAL",            "Festivo local");
        TIPO_LEGIBLE.put("VACACIONES",               "Vacaciones");
        TIPO_LEGIBLE.put("ASUNTO_PROPIO",            "Asunto propio");
        TIPO_LEGIBLE.put("PERMISO_RETRIBUIDO",       "Permiso retribuido");
        TIPO_LEGIBLE.put("BAJA_MEDICA",              "Baja médica");
        TIPO_LEGIBLE.put("DIA_LIBRE_COMPENSATORIO",  "Día libre compensatorio");
        TIPO_LEGIBLE.put("AUSENCIA_INJUSTIFICADA",   "Ausencia injustificada");
        TIPO_LEGIBLE.put("DIA_LIBRE",                "Día libre");
        TIPO_LEGIBLE.put("SIN_REGISTRO",             "Sin registro");
    }

    // Orden de tipos de ausencia en el resumen (sin NORMAL ni DIA_LIBRE)
    private static final List<String> ORDEN_AUSENCIAS = List.of(
            "DIA_LIBRE_COMPENSATORIO",
            "FESTIVO_NACIONAL",
            "FESTIVO_LOCAL",
            "VACACIONES",
            "ASUNTO_PROPIO",
            "PERMISO_RETRIBUIDO",
            "BAJA_MEDICA",
            "AUSENCIA_INJUSTIFICADA"
    );

    // Campos individuales válidos para E44 (?campos=)
    private static final List<String> CAMPOS_VALIDOS = List.of(
            "VAC_PENDIENTE_ANT", "VAC_DERECHO", "VAC_CONSUMIDOS", "VAC_DISPONIBLES",
            "AP_PENDIENTE_ANT",  "AP_DERECHO",  "AP_CONSUMIDOS",  "AP_DISPONIBLES",
            "DIAS_TRABAJADOS", "DIAS_BAJA_MEDICA", "DIAS_PERMISO_RETRIBUIDO",
            "DIAS_AUSENCIA_INJUSTIFICADA",
            "HORAS_AUSENCIA_RETRIBUIDA", "SALDO_HORAS",
            "CALCULADO_HASTA", "ULTIMA_MODIFICACION"
    );

    // Expansión de bloques a campos individuales
    private static final Map<String, List<String>> BLOQUES = new LinkedHashMap<>();
    static {
        BLOQUES.put("DIAS_VACACIONES",     List.of("VAC_PENDIENTE_ANT", "VAC_DERECHO", "VAC_CONSUMIDOS", "VAC_DISPONIBLES"));
        BLOQUES.put("DIAS_ASUNTOS_PROPIOS",List.of("AP_PENDIENTE_ANT",  "AP_DERECHO",  "AP_CONSUMIDOS",  "AP_DISPONIBLES"));
        BLOQUES.put("RESTO_DIAS",          List.of("DIAS_TRABAJADOS", "DIAS_BAJA_MEDICA", "DIAS_PERMISO_RETRIBUIDO", "DIAS_AUSENCIA_INJUSTIFICADA"));
        BLOQUES.put("HORAS",               List.of("HORAS_AUSENCIA_RETRIBUIDA", "SALDO_HORAS"));
        BLOQUES.put("CONTROL",             List.of("CALCULADO_HASTA", "ULTIMA_MODIFICACION"));
    }

    // =========================================================================
    // E42 — GET /api/v1/informes/horas/{empleadoId}
    // RF-32: informe de horas de un empleado en un período
    // =========================================================================

    /**
     * Genera el informe de horas de un empleado en un periodo (E42).
     *
     * <p>Detalla los días con jornada NORMAL, días de ausencia por tipo,
     * pausas del día y el total de horas efectivas. Si formato=html devuelve
     * HTML imprimible; si formato=json devuelve Map estructurado.</p>
     *
     * <p>D-027: el parámetro tipos filtra el detalle por tipo de jornada.
     * null o vacío devuelve todos los días del período.</p>
     *
     * @param empleadoId id del empleado
     * @param desde      fecha de inicio del período (inclusive)
     * @param hasta      fecha de fin del período (inclusive)
     * @param formato    "json" o "html" (defecto: json)
     * @param tipos      lista de tipos a incluir (null = todos)
     * @return informe de horas del empleado en el formato solicitado
     */
    public Object informeHorasEmpleado(Long empleadoId, LocalDate desde,
                                        LocalDate hasta, String formato,
                                        List<String> tipos) {

        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Empleado no encontrado con id " + empleadoId));

        String nombreEmpresa = obtenerNombreEmpresa();
        Set<String> filtroTipos = normalizarFiltro(tipos);

        List<DiaInforme> dias = construirDias(empleadoId, desde, hasta, filtroTipos);
        ResumenInforme resumen = calcularResumen(dias, filtroTipos);

        if ("html".equalsIgnoreCase(formato)) {
            return generarHtmlEmpleado(empleado, desde, hasta, dias, resumen, nombreEmpresa);
        }

        return construirJsonEmpleado(empleado, desde, hasta, dias, resumen);
    }

    // =========================================================================
    // E43 — GET /api/v1/informes/horas
    // RF-33: informe global de horas de todos los empleados
    // =========================================================================

    /**
     * Genera el informe global de horas de todos los empleados activos (E43).
     *
     * <p>Resumen por empleado con el total de horas efectivas y desglose
     * de tipos de jornada. Acepta ?formato=html para impresión desde Android.</p>
     *
     * <p>D-027: el parámetro tipos filtra el detalle por tipo de jornada.</p>
     *
     * @param desde   fecha de inicio del período (inclusive)
     * @param hasta   fecha de fin del período (inclusive)
     * @param formato "json" o "html" (defecto: json)
     * @param tipos   lista de tipos a incluir (null = todos)
     * @return informe global de horas en el formato solicitado
     */
    public Object informeHorasGlobal(LocalDate desde, LocalDate hasta,
                                      String formato, List<String> tipos) {

        String nombreEmpresa = obtenerNombreEmpresa();
        Set<String> filtroTipos = normalizarFiltro(tipos);

        List<Empleado> empleados = empleadoRepository.findByActivo(true);

        List<Map<String, Object>> filas = new ArrayList<>();
        for (Empleado empleado : empleados) {
            List<DiaInforme> dias = construirDias(empleado.getId(), desde, hasta, filtroTipos);
            ResumenInforme resumen = calcularResumen(dias, filtroTipos);
            filas.add(construirFilaGlobal(empleado, dias, resumen));
        }

        if ("html".equalsIgnoreCase(formato)) {
            return generarHtmlGlobal(desde, hasta, filas, empleados,
                    filtroTipos, nombreEmpresa);
        }

        return filas;
    }

    // =========================================================================
    // E44 — GET /api/v1/informes/saldos
    // RF-34: informe de saldos anuales (vacaciones, asuntos propios, horas)
    // D-029: renombrado desde /vacaciones, añadidos ?empleadoId= y ?campos=
    // =========================================================================

    /**
     * Genera el informe de saldos anuales por empleado (E44).
     *
     * <p>Devuelve el saldo anual de cada empleado: días vacaciones, días asuntos
     * propios, resto de días (trabajados, baja médica, permiso retribuido,
     * ausencia injustificada), horas de ausencia retribuida, saldo de horas,
     * fecha calculado hasta y fecha de última modificación.</p>
     *
     * <p>D-029: parámetro ?empleadoId= opcional. Sin parámetro devuelve todos
     * los empleados activos con saldo en ese año. Con uno o varios ids separados
     * por coma devuelve solo esos empleados.</p>
     *
     * <p>D-029: parámetro ?campos= opcional. Acepta bloques predefinidos
     * (DIAS_VACACIONES, DIAS_ASUNTOS_PROPIOS, RESTO_DIAS, HORAS, CONTROL)
     * y campos individuales. Sin parámetro se muestran todos los bloques.</p>
     *
     * @param anio        año a consultar
     * @param formato     "json" o "html" (defecto: json)
     * @param empleadoIds lista de ids de empleado (null = todos los activos)
     * @param campos      lista de bloques o campos a incluir (null = todos)
     * @return informe de saldos en el formato solicitado
     */
    public Object informeSaldos(Integer anio, String formato,
                                 List<Long> empleadoIds, List<String> campos) {

        // Resolver campos seleccionados (bloques → campos individuales)
        Set<String> camposActivos = resolverCampos(campos);

        // Obtener saldos según filtro de empleados
        List<SaldoAnual> saldos;
        if (empleadoIds == null || empleadoIds.isEmpty()) {
            // Sin filtro: todos los saldos del año, luego filtrar por empleados activos
            saldos = saldoRepository.findByAnio(anio).stream()
                    .filter(s -> s.getEmpleado().getActivo())
                    .sorted(Comparator.comparing(s -> nombreCompleto(s.getEmpleado())))
                    .collect(Collectors.toList());
        } else {
            // Con filtro: un saldo por cada id solicitado
            saldos = new ArrayList<>();
            for (Long id : empleadoIds) {
                saldoRepository.findByEmpleadoIdAndAnio(id, anio)
                        .ifPresent(saldos::add);
            }
            saldos.sort(Comparator.comparing(s -> nombreCompleto(s.getEmpleado())));
        }

        if ("html".equalsIgnoreCase(formato)) {
            return generarHtmlSaldos(anio, saldos, camposActivos);
        }

        return construirJsonSaldos(anio, saldos, camposActivos);
    }

    // =========================================================================
    // E44 — Construcción JSON de saldos
    // =========================================================================

    /**
     * Construye la respuesta JSON del informe de saldos (E44).
     * Devuelve lista de mapas, uno por empleado, con solo los campos activos.
     */
    private List<Map<String, Object>> construirJsonSaldos(Integer anio,
                                                           List<SaldoAnual> saldos,
                                                           Set<String> camposActivos) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (SaldoAnual s : saldos) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("empleadoId", s.getEmpleado().getId());
            fila.put("empleado",   nombreCompleto(s.getEmpleado()));
            fila.put("anio",       anio);
            agregarCamposJson(fila, s, camposActivos);
            resultado.add(fila);
        }
        return resultado;
    }

    /**
     * Añade al mapa JSON solo los campos seleccionados del SaldoAnual.
     */
    private void agregarCamposJson(Map<String, Object> fila, SaldoAnual s,
                                    Set<String> camposActivos) {
        if (camposActivos.contains("VAC_PENDIENTE_ANT"))
            fila.put("vacPendientesAnioAnterior", s.getDiasVacacionesPendientesAnioAnterior());
        if (camposActivos.contains("VAC_DERECHO"))
            fila.put("vacDerechoAnual", s.getDiasVacacionesDerechoAnio());
        if (camposActivos.contains("VAC_CONSUMIDOS"))
            fila.put("vacConsumidosAnioEnCurso", s.getDiasVacacionesConsumidos());
        if (camposActivos.contains("VAC_DISPONIBLES"))
            fila.put("vacDisponibles", s.getDiasVacacionesDisponibles());
        if (camposActivos.contains("AP_PENDIENTE_ANT"))
            fila.put("apPendientesAnioAnterior", s.getDiasAsuntosPropiosPendientesAnterior());
        if (camposActivos.contains("AP_DERECHO"))
            fila.put("apDerechoAnual", s.getDiasAsuntosPropiosDerechoAnio());
        if (camposActivos.contains("AP_CONSUMIDOS"))
            fila.put("apConsumidosAnioEnCurso", s.getDiasAsuntosPropiosConsumidos());
        if (camposActivos.contains("AP_DISPONIBLES"))
            fila.put("apDisponibles", s.getDiasAsuntosPropiosDisponibles());
        if (camposActivos.contains("DIAS_TRABAJADOS"))
            fila.put("diasTrabajados", s.getDiasTrabajados());
        if (camposActivos.contains("DIAS_BAJA_MEDICA"))
            fila.put("diasBajaMedica", s.getDiasBajaMedica());
        if (camposActivos.contains("DIAS_PERMISO_RETRIBUIDO"))
            fila.put("diasPermisoRetribuido", s.getDiasPermisoRetribuido());
        if (camposActivos.contains("DIAS_AUSENCIA_INJUSTIFICADA"))
            fila.put("diasAusenciaInjustificada", s.getDiasAusenciaInjustificada());
        if (camposActivos.contains("HORAS_AUSENCIA_RETRIBUIDA"))
            fila.put("horasAusenciaRetribuida", s.getHorasAusenciaRetribuida());
        if (camposActivos.contains("SALDO_HORAS"))
            fila.put("saldoHoras", s.getSaldoHoras());
        if (camposActivos.contains("CALCULADO_HASTA"))
            fila.put("calculadoHasta", s.getCalculadoHastaFecha() != null
                    ? s.getCalculadoHastaFecha().format(FMT_FECHA) : null);
        if (camposActivos.contains("ULTIMA_MODIFICACION"))
            fila.put("ultimaModificacion", s.getFechaUltimaModificacion() != null
                    ? s.getFechaUltimaModificacion().format(FMT_FECHA) : null);
    }

    // =========================================================================
    // E44 — Generación HTML de saldos
    // =========================================================================

    /**
     * Genera el HTML del informe de saldos anuales (E44).
     * Tabla con cabecera de dos niveles, bloques coloreados, scroll horizontal.
     * Colores: disponibles vacaciones/AP y saldo horas en verde/rojo/negro según valor.
     */
    private String generarHtmlSaldos(Integer anio, List<SaldoAnual> saldos,
                                      Set<String> camposActivos) {

        // Determinar qué bloques están activos (al menos un campo del bloque presente)
        boolean bloqVac  = tieneAlgunCampo(camposActivos, "VAC_PENDIENTE_ANT","VAC_DERECHO","VAC_CONSUMIDOS","VAC_DISPONIBLES");
        boolean bloqAp   = tieneAlgunCampo(camposActivos, "AP_PENDIENTE_ANT","AP_DERECHO","AP_CONSUMIDOS","AP_DISPONIBLES");
        boolean bloqDias = tieneAlgunCampo(camposActivos, "DIAS_TRABAJADOS","DIAS_BAJA_MEDICA","DIAS_PERMISO_RETRIBUIDO","DIAS_AUSENCIA_INJUSTIFICADA");
        boolean bloqHoras= tieneAlgunCampo(camposActivos, "HORAS_AUSENCIA_RETRIBUIDA","SALDO_HORAS");
        boolean bloqCtrl = tieneAlgunCampo(camposActivos, "CALCULADO_HASTA","ULTIMA_MODIFICACION");

        String fechaGen = LocalDate.now().format(FMT_FECHA);

        StringBuilder sb = new StringBuilder();
        sb.append(htmlCabeceraInforme());
        sb.append("<body>\n<div class=\"page\">\n");
        sb.append("<div class=\"header\">\n");
        sb.append("  <div class=\"titulo\">INFORME DE SALDOS &mdash; ").append(anio)
          .append(" &nbsp;&middot;&nbsp; Generado el ").append(fechaGen).append("</div>\n");
        sb.append("</div>\n");
        sb.append("<div class=\"tabla-scroll\">\n");
        sb.append("<table class=\"detalle saldos\">\n");

        // --- Fila de cabecera nivel 1: bloques ---
        sb.append("<thead>\n<tr>\n");
        sb.append("  <th rowspan=\"2\" class=\"col-empleado\">Empleado</th>\n");
        if (bloqVac) {
            int cols = contarCamposBloque(camposActivos, "VAC_PENDIENTE_ANT","VAC_DERECHO","VAC_CONSUMIDOS","VAC_DISPONIBLES");
            sb.append("  <th colspan=\"").append(cols).append("\" class=\"bloque bloque-vac\">Dias vacaciones</th>\n");
        }
        if (bloqAp) {
            int cols = contarCamposBloque(camposActivos, "AP_PENDIENTE_ANT","AP_DERECHO","AP_CONSUMIDOS","AP_DISPONIBLES");
            sb.append("  <th colspan=\"").append(cols).append("\" class=\"bloque bloque-ap\">Dias asuntos propios</th>\n");
        }
        if (bloqDias) {
            int cols = contarCamposBloque(camposActivos, "DIAS_TRABAJADOS","DIAS_BAJA_MEDICA","DIAS_PERMISO_RETRIBUIDO","DIAS_AUSENCIA_INJUSTIFICADA");
            sb.append("  <th colspan=\"").append(cols).append("\" class=\"bloque bloque-dias\">Resto de dias</th>\n");
        }
        if (bloqHoras) {
            int cols = contarCamposBloque(camposActivos, "HORAS_AUSENCIA_RETRIBUIDA","SALDO_HORAS");
            sb.append("  <th colspan=\"").append(cols).append("\" class=\"bloque bloque-horas\">Horas</th>\n");
        }
        if (bloqCtrl) {
            int cols = contarCamposBloque(camposActivos, "CALCULADO_HASTA","ULTIMA_MODIFICACION");
            sb.append("  <th colspan=\"").append(cols).append("\" class=\"bloque bloque-ctrl\">Control</th>\n");
        }
        sb.append("</tr>\n");

        // --- Fila de cabecera nivel 2: nombres de columna ---
        sb.append("<tr>\n");
        if (camposActivos.contains("VAC_PENDIENTE_ANT"))
            sb.append("  <th>Pendientes<br>año anterior</th>\n");
        if (camposActivos.contains("VAC_DERECHO"))
            sb.append("  <th>Derecho<br>anual</th>\n");
        if (camposActivos.contains("VAC_CONSUMIDOS"))
            sb.append("  <th>Consumidos<br>año en curso</th>\n");
        if (camposActivos.contains("VAC_DISPONIBLES"))
            sb.append("  <th>Disponibles</th>\n");
        if (camposActivos.contains("AP_PENDIENTE_ANT"))
            sb.append("  <th>Pendientes<br>año anterior</th>\n");
        if (camposActivos.contains("AP_DERECHO"))
            sb.append("  <th>Derecho<br>anual</th>\n");
        if (camposActivos.contains("AP_CONSUMIDOS"))
            sb.append("  <th>Consumidos<br>año en curso</th>\n");
        if (camposActivos.contains("AP_DISPONIBLES"))
            sb.append("  <th>Disponibles</th>\n");
        if (camposActivos.contains("DIAS_TRABAJADOS"))
            sb.append("  <th>Dias<br>trabajados</th>\n");
        if (camposActivos.contains("DIAS_BAJA_MEDICA"))
            sb.append("  <th>Dias baja<br>medica</th>\n");
        if (camposActivos.contains("DIAS_PERMISO_RETRIBUIDO"))
            sb.append("  <th>Dias permiso<br>retribuido</th>\n");
        if (camposActivos.contains("DIAS_AUSENCIA_INJUSTIFICADA"))
            sb.append("  <th>Dias ausencia<br>injustificada</th>\n");
        if (camposActivos.contains("HORAS_AUSENCIA_RETRIBUIDA"))
            sb.append("  <th>Horas ausencia<br>retribuida</th>\n");
        if (camposActivos.contains("SALDO_HORAS"))
            sb.append("  <th>Saldo<br>horas</th>\n");
        if (camposActivos.contains("CALCULADO_HASTA"))
            sb.append("  <th>Calculado<br>hasta</th>\n");
        if (camposActivos.contains("ULTIMA_MODIFICACION"))
            sb.append("  <th>Ultima<br>modificacion</th>\n");
        sb.append("</tr>\n</thead>\n");

        // --- Filas de datos ---
        sb.append("<tbody>\n");
        for (SaldoAnual s : saldos) {
            sb.append("<tr>\n");
            sb.append("  <td class=\"col-empleado\">").append(esc(nombreCompleto(s.getEmpleado()))).append("</td>\n");

            // Bloque vacaciones
            if (camposActivos.contains("VAC_PENDIENTE_ANT"))
                sb.append("  <td>").append(s.getDiasVacacionesPendientesAnioAnterior()).append("</td>\n");
            if (camposActivos.contains("VAC_DERECHO"))
                sb.append("  <td>").append(s.getDiasVacacionesDerechoAnio()).append("</td>\n");
            if (camposActivos.contains("VAC_CONSUMIDOS"))
                sb.append("  <td>").append(s.getDiasVacacionesConsumidos()).append("</td>\n");
            if (camposActivos.contains("VAC_DISPONIBLES")) {
                int disp = s.getDiasVacacionesDisponibles();
                sb.append("  <td class=\"").append(claseCelda(disp)).append("\">").append(disp).append("</td>\n");
            }

            // Bloque asuntos propios
            if (camposActivos.contains("AP_PENDIENTE_ANT"))
                sb.append("  <td>").append(s.getDiasAsuntosPropiosPendientesAnterior()).append("</td>\n");
            if (camposActivos.contains("AP_DERECHO"))
                sb.append("  <td>").append(s.getDiasAsuntosPropiosDerechoAnio()).append("</td>\n");
            if (camposActivos.contains("AP_CONSUMIDOS"))
                sb.append("  <td>").append(s.getDiasAsuntosPropiosConsumidos()).append("</td>\n");
            if (camposActivos.contains("AP_DISPONIBLES")) {
                int disp = s.getDiasAsuntosPropiosDisponibles();
                sb.append("  <td class=\"").append(claseCelda(disp)).append("\">").append(disp).append("</td>\n");
            }

            // Bloque resto días
            if (camposActivos.contains("DIAS_TRABAJADOS"))
                sb.append("  <td>").append(s.getDiasTrabajados()).append("</td>\n");
            if (camposActivos.contains("DIAS_BAJA_MEDICA"))
                sb.append("  <td>").append(s.getDiasBajaMedica()).append("</td>\n");
            if (camposActivos.contains("DIAS_PERMISO_RETRIBUIDO"))
                sb.append("  <td>").append(s.getDiasPermisoRetribuido()).append("</td>\n");
            if (camposActivos.contains("DIAS_AUSENCIA_INJUSTIFICADA"))
                sb.append("  <td>").append(s.getDiasAusenciaInjustificada()).append("</td>\n");

            // Bloque horas
            if (camposActivos.contains("HORAS_AUSENCIA_RETRIBUIDA"))
                sb.append("  <td>").append(formatearDecimal(s.getHorasAusenciaRetribuida())).append("</td>\n");
            if (camposActivos.contains("SALDO_HORAS")) {
                double saldoHoras = s.getSaldoHoras() != null ? s.getSaldoHoras().doubleValue() : 0.0;
                String claseHoras = saldoHoras > 0 ? "valor-pos" : (saldoHoras < 0 ? "valor-neg" : "");
                String prefijo = saldoHoras > 0 ? "+" : "";
                sb.append("  <td class=\"").append(claseHoras).append("\">")
                  .append(prefijo).append(formatearDecimal(s.getSaldoHoras()))
                  .append("</td>\n");
            }

            // Bloque control
            if (camposActivos.contains("CALCULADO_HASTA"))
                sb.append("  <td>").append(s.getCalculadoHastaFecha() != null
                        ? s.getCalculadoHastaFecha().format(FMT_FECHA) : "&mdash;").append("</td>\n");
            if (camposActivos.contains("ULTIMA_MODIFICACION"))
                sb.append("  <td>").append(s.getFechaUltimaModificacion() != null
                        ? s.getFechaUltimaModificacion().format(FMT_FECHA) : "&mdash;").append("</td>\n");

            sb.append("</tr>\n");
        }

        if (saldos.isEmpty()) {
            sb.append("<tr><td colspan=\"20\" style=\"text-align:center;color:#9aa5be;font-style:italic;\">")
              .append("No hay saldos registrados para el año ").append(anio).append("</td></tr>\n");
        }

        sb.append("</tbody>\n</table>\n</div>\n");
        sb.append("<div class=\"pie\">StaffFlow &mdash; Informe generado el ")
          .append(fechaGen).append("</div>\n");
        sb.append("</div>\n</body>\n</html>");

        return sb.toString();
    }

    // =========================================================================
    // Construcción del listado de días del período
    // =========================================================================

    /**
     * Construye la lista de DiaInforme para cada día del período.
     *
     * <p>Para cada día entre desde y hasta (inclusive):
     *   1. Busca si existe fichaje en BD para ese empleado y fecha.
     *   2. Si existe fichaje: carga sus pausas y construye DiaInforme con datos reales.
     *   3. Si no existe fichaje y es fin de semana: DIA_LIBRE.
     *   4. Si no existe fichaje y es entre semana: SIN_REGISTRO.
     * Aplica el filtro de tipos si se especificó.</p>
     *
     * @param empleadoId  id del empleado
     * @param desde       fecha de inicio
     * @param hasta       fecha de fin
     * @param filtroTipos tipos a incluir (vacío = todos)
     * @return lista de días del período con sus datos
     */
    private List<DiaInforme> construirDias(Long empleadoId, LocalDate desde,
                                            LocalDate hasta, Set<String> filtroTipos) {

        // Cargar todos los fichajes del período en una sola query
        List<Fichaje> fichajes = fichajeRepository
                .findByEmpleadoIdAndFechaBetween(empleadoId, desde, hasta);
        Map<LocalDate, Fichaje> fichajesPorFecha = fichajes.stream()
                .collect(Collectors.toMap(Fichaje::getFecha, f -> f));

        // Cargar todas las pausas del período en una sola query
        List<Pausa> pausas = pausaRepository
                .findByEmpleadoIdAndFechaBetween(empleadoId, desde, hasta);
        Map<LocalDate, List<Pausa>> pausasPorFecha = pausas.stream()
                .collect(Collectors.groupingBy(Pausa::getFecha));

        List<DiaInforme> resultado = new ArrayList<>();
        LocalDate cursor = desde;

        while (!cursor.isAfter(hasta)) {
            Fichaje fichaje = fichajesPorFecha.get(cursor);
            DiaInforme dia;

            if (fichaje != null) {
                List<Pausa> pausasDia = pausasPorFecha.getOrDefault(cursor, List.of());
                dia = construirDiaConFichaje(cursor, fichaje, pausasDia);
            } else {
                boolean esFinDeSemana = cursor.getDayOfWeek() == DayOfWeek.SATURDAY
                        || cursor.getDayOfWeek() == DayOfWeek.SUNDAY;
                dia = construirDiaSinFichaje(cursor, esFinDeSemana);
            }

            // Aplicar filtro de tipos si se especificó
            if (filtroTipos.isEmpty() || filtroTipos.contains(dia.tipo)) {
                resultado.add(dia);
            }

            cursor = cursor.plusDays(1);
        }

        return resultado;
    }

    /**
     * Construye un DiaInforme a partir de un fichaje existente en BD.
     * Determina si el fichaje es manual comparando el rol del usuario creador.
     */
    private DiaInforme construirDiaConFichaje(LocalDate fecha, Fichaje fichaje,
                                               List<Pausa> pausas) {
        DiaInforme dia = new DiaInforme();
        dia.fecha       = fecha;
        dia.tipo        = fichaje.getTipo().name();
        dia.tieneFichaje = true;

        // Intervención manual: ni el propio empleado ni terminal_service.
        // D-028: si el fichaje es candidato a manual, se consulta
        // planificacion_ausencias para descartar registros planificados
        // anticipadamente (festivos, vacaciones planificadas, etc.).
        // Si existe planificación para ese empleado y fecha → no es manual.
        // Si no existe planificación → sí es intervención manual real.
        // Solución definitiva en M-012 (DDL v6 con planificacion_id en Fichaje).
        boolean candidatoManual = fichaje.getUsuario() != null
                && fichaje.getUsuario().getRol() != Rol.EMPLEADO
                && !"terminal_service".equals(fichaje.getUsuario().getUsername());
        boolean esManual = candidatoManual
                && !planificacionRepository.existsByEmpleadoIdAndFecha(
                        fichaje.getEmpleado().getId(), fecha);
        dia.esManual = esManual;

        // Horas y pausas solo si jornadaEfectivaMinutos > 0
        if (fichaje.getJornadaEfectivaMinutos() != null
                && fichaje.getJornadaEfectivaMinutos() > 0) {
            dia.horaEntrada         = fichaje.getHoraEntrada();
            dia.horaSalida          = fichaje.getHoraSalida();
            dia.horasEfectivas      = fichaje.getJornadaEfectivaMinutos();
            dia.totalPausasMinutos  = fichaje.getTotalPausasMinutos() != null
                    ? fichaje.getTotalPausasMinutos() : 0;
            dia.tieneDatos          = true;
        }

        dia.observaciones = fichaje.getObservaciones();

        // Construir lista de pausas del día
        for (Pausa p : pausas) {
            PausaInforme pi = new PausaInforme();
            pi.horaInicio     = p.getHoraInicio();
            pi.horaFin        = p.getHoraFin();
            pi.duracionMinutos = p.getDuracionMinutos() != null ? p.getDuracionMinutos() : 0;
            pi.motivo         = p.getTipoPausa() != null ? formatearTipoPausa(p.getTipoPausa().name()) : "";
            pi.esManual       = p.getUsuario() != null
                    && p.getUsuario().getRol() != Rol.EMPLEADO
                    && !"terminal_service".equals(p.getUsuario().getUsername());
            pi.observaciones  = p.getObservaciones();
            dia.pausas.add(pi);
        }

        return dia;
    }

    /**
     * Construye un DiaInforme para un día sin fichaje (DIA_LIBRE o SIN_REGISTRO).
     */
    private DiaInforme construirDiaSinFichaje(LocalDate fecha, boolean esFinDeSemana) {
        DiaInforme dia = new DiaInforme();
        dia.fecha        = fecha;
        dia.tipo         = esFinDeSemana ? "DIA_LIBRE" : "SIN_REGISTRO";
        dia.tieneFichaje = false;
        dia.tieneDatos   = false;
        return dia;
    }

    // =========================================================================
    // Cálculo del resumen
    // =========================================================================

    /**
     * Calcula el resumen del informe a partir de la lista de días construida.
     *
     * <p>diasTrabajados: días con jornadaEfectivaMinutos > 0.
     * horasEfectivas: suma de jornadaEfectivaMinutos de días trabajados.
     * diasLibres: días con tipo DIA_LIBRE.
     * ausencias: conteo por tipo para todos los tipos del enum excepto NORMAL.</p>
     */
    private ResumenInforme calcularResumen(List<DiaInforme> dias, Set<String> filtroTipos) {
        ResumenInforme r = new ResumenInforme();

        for (DiaInforme dia : dias) {
            if (dia.tieneDatos) {
                r.diasTrabajados++;
                r.horasEfectivasMinutos += dia.horasEfectivas;
            }
            if ("DIA_LIBRE".equals(dia.tipo)) {
                r.diasLibres++;
            }
            // Contar ausencias por tipo (excluye NORMAL y DIA_LIBRE y SIN_REGISTRO)
            if (!dia.tipo.equals("NORMAL") && !dia.tipo.equals("DIA_LIBRE")
                    && !dia.tipo.equals("SIN_REGISTRO")) {
                r.ausenciasPorTipo.merge(dia.tipo, 1, Integer::sum);

                // Acumular horas de PERMISO_RETRIBUIDO
                if ("PERMISO_RETRIBUIDO".equals(dia.tipo) && dia.tieneDatos) {
                    r.horasPermisoRetribuidoMinutos += dia.horasEfectivas;
                }
            }
        }

        // Asegurar que todos los tipos de ausencia aparecen en el resumen aunque sean 0
        for (String tipo : ORDEN_AUSENCIAS) {
            r.ausenciasPorTipo.putIfAbsent(tipo, 0);
        }

        return r;
    }

    // =========================================================================
    // Construcción JSON (E42/E43)
    // =========================================================================

    private Map<String, Object> construirJsonEmpleado(Empleado empleado,
                                                       LocalDate desde, LocalDate hasta,
                                                       List<DiaInforme> dias,
                                                       ResumenInforme resumen) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("empleado", Map.of(
                "id", empleado.getId(),
                "nombre", nombreCompleto(empleado)
        ));
        json.put("periodo", Map.of("desde", desde.toString(), "hasta", hasta.toString()));
        json.put("resumen", construirJsonResumen(resumen));
        json.put("detalle", dias.stream().map(this::construirJsonDia).toList());
        return json;
    }

    private Map<String, Object> construirJsonResumen(ResumenInforme r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("diasTrabajados", r.diasTrabajados);
        m.put("horasEfectivas", formatearMinutos(r.horasEfectivasMinutos));
        m.put("diasLibres", r.diasLibres);

        // Ausencias en orden definido
        for (String tipo : ORDEN_AUSENCIAS) {
            int count = r.ausenciasPorTipo.getOrDefault(tipo, 0);
            m.put(tipo.toLowerCase(), count);
        }

        return m;
    }

    private Map<String, Object> construirJsonDia(DiaInforme dia) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fecha", dia.fecha.toString());
        m.put("diaSemana", DIAS_ES.getOrDefault(dia.fecha.getDayOfWeek(), ""));
        m.put("tipo", dia.tipo);
        m.put("esManual", dia.esManual);
        if (dia.tieneDatos) {
            m.put("horaEntrada", dia.horaEntrada != null ? dia.horaEntrada.format(FMT_HORA) : null);
            m.put("horaSalida",  dia.horaSalida  != null ? dia.horaSalida.format(FMT_HORA)  : null);
            m.put("horasEfectivas", formatearMinutos(dia.horasEfectivas));
            m.put("totalPausasMinutos", dia.totalPausasMinutos);
        }
        if (dia.observaciones != null && !dia.observaciones.isBlank()) {
            m.put("observaciones", dia.observaciones);
        }
        if (!dia.pausas.isEmpty()) {
            m.put("pausas", dia.pausas.stream().map(this::construirJsonPausa).toList());
        }
        return m;
    }

    private Map<String, Object> construirJsonPausa(PausaInforme p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inicio",    p.horaInicio != null ? p.horaInicio.format(FMT_HORA) : null);
        m.put("fin",       p.horaFin    != null ? p.horaFin.format(FMT_HORA)    : null);
        m.put("duracion",  p.duracionMinutos + " min");
        m.put("motivo",    p.motivo);
        m.put("esManual",  p.esManual);
        if (p.observaciones != null && !p.observaciones.isBlank()) {
            m.put("observaciones", p.observaciones);
        }
        return m;
    }

    private Map<String, Object> construirFilaGlobal(Empleado empleado,
                                                      List<DiaInforme> dias,
                                                      ResumenInforme resumen) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("empleadoId", empleado.getId());
        m.put("empleado",   nombreCompleto(empleado));
        m.put("diasTrabajados", resumen.diasTrabajados);
        m.put("horasEfectivas", formatearMinutos(resumen.horasEfectivasMinutos));
        for (String tipo : ORDEN_AUSENCIAS) {
            m.put(tipo.toLowerCase(), resumen.ausenciasPorTipo.getOrDefault(tipo, 0));
        }
        return m;
    }

    // =========================================================================
    // Generación HTML (E42/E43) — cabecera y estilos
    // =========================================================================

    private String generarHtmlEmpleado(Empleado empleado, LocalDate desde, LocalDate hasta,
                                        List<DiaInforme> dias, ResumenInforme resumen,
                                        String nombreEmpresa) {
        StringBuilder sb = new StringBuilder();
        sb.append(htmlCabeceraInforme());
        sb.append("<body>\n<div class=\"page\">\n");

        // Cabecera
        sb.append("<div class=\"header\">\n");
        sb.append("  <div class=\"titulo\">").append(esc(nombreEmpresa)).append("</div>\n");
        sb.append("  <div class=\"meta\">\n");
        sb.append("    <span>Informe de jornada</span>\n");
        sb.append("    <span>").append(esc(nombreCompleto(empleado))).append("</span>\n");
        sb.append("    <span>").append(desde.format(FMT_FECHA)).append(" – ")
          .append(hasta.format(FMT_FECHA)).append("</span>\n");
        sb.append("    <span>Generado: ").append(LocalDateTime.now().format(FMT_GENERA)).append("</span>\n");
        sb.append("  </div>\n</div>\n");

        // Tabla resumen
        sb.append("<table class=\"resumen\">\n");
        sb.append(filaResumen("Días trabajados", String.valueOf(resumen.diasTrabajados)));
        sb.append(filaResumen("Horas efectivas", formatearMinutos(resumen.horasEfectivasMinutos)));
        sb.append(filaResumen("Días libres (sáb/dom)", String.valueOf(resumen.diasLibres)));
        for (String tipo : ORDEN_AUSENCIAS) {
            int count = resumen.ausenciasPorTipo.getOrDefault(tipo, 0);
            if (count > 0) {
                sb.append(filaResumen(TIPO_LEGIBLE.getOrDefault(tipo, tipo), String.valueOf(count)));
            }
        }
        sb.append("</table>\n");

        // Tabla detalle
        sb.append("<div class=\"tabla-scroll\">\n");
        sb.append("<table class=\"detalle\">\n<thead>\n<tr>\n");
        sb.append("  <th>Fecha</th><th>Día</th><th>Tipo</th>");
        sb.append("<th>Entrada</th><th>Salida</th>");
        sb.append("<th>Horas ef.</th><th>Pausas</th>");
        sb.append("<th>Inicio pausa</th><th>Fin pausa</th>");
        sb.append("<th>Dur.</th><th>Motivo</th><th>Observaciones</th>\n");
        sb.append("</tr>\n</thead>\n<tbody>\n");

        // Subtotales semanales + total global: mismo patron que PdfService
        int semanaActual    = -1;
        int minutosSemanales = 0;
        int totalMinutos    = 0;
        int minutosDiarios  = empleado.getJornadaDiariaMinutos();

        for (DiaInforme dia : dias) {
            // Detectar cambio de semana e insertar fila subtotal
            int semana = dia.fecha.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            if (semanaActual == -1) {
                semanaActual = semana;
            } else if (semana != semanaActual) {
                sb.append(filaSubtotalSemana(semanaActual, minutosSemanales));
                semanaActual     = semana;
                minutosSemanales = 0;
            }

            // Acumular minutos para el subtotal de esta semana y el total global
            if (dia.tieneDatos) {
                minutosSemanales += dia.horasEfectivas;
                totalMinutos     += dia.horasEfectivas;
            } else if ("BAJA_MEDICA".equals(dia.tipo) || "PERMISO_RETRIBUIDO".equals(dia.tipo)) {
                minutosSemanales += minutosDiarios;
                totalMinutos     += minutosDiarios;
            }

            String claseFila = "";
            if (!dia.tieneFichaje) claseFila = "fila-libre";
            else if (!"NORMAL".equals(dia.tipo)) claseFila = "fila-ausencia";

            if (dia.pausas.isEmpty()) {
                sb.append("<tr class=\"").append(claseFila).append("\">\n");
                sb.append(celdaDia(dia));
                sb.append("  <td>&mdash;</td><td>&mdash;</td><td>&mdash;</td><td>&mdash;</td><td>&mdash;</td>\n");
                sb.append("</tr>\n");
            } else {
                boolean primera = true;
                for (PausaInforme pausa : dia.pausas) {
                    if (primera) {
                        sb.append("<tr class=\"").append(claseFila).append("\">\n");
                        sb.append(celdaDia(dia));
                        primera = false;
                    } else {
                        sb.append("<tr class=\"fila-pausa-extra\">\n");
                        sb.append("  <td></td><td></td><td></td>");
                        sb.append("<td></td><td></td><td></td><td></td>\n");
                    }
                    sb.append("  <td>").append(celdaPausa(pausa.horaInicio, pausa.esManual)).append("</td>\n");
                    sb.append("  <td>").append(celdaPausa(pausa.horaFin,    pausa.esManual)).append("</td>\n");
                    sb.append("  <td>").append(pausa.duracionMinutos > 0 ? pausa.duracionMinutos + " min" : "&mdash;").append("</td>\n");
                    sb.append("  <td>").append(esc(pausa.motivo)).append("</td>\n");
                    sb.append("  <td>").append(pausa.observaciones != null ? esc(pausa.observaciones) : "&mdash;").append("</td>\n");
                    sb.append("</tr>\n");
                }
            }
        }

        // Subtotal de la ultima semana
        if (semanaActual != -1) {
            sb.append(filaSubtotalSemana(semanaActual, minutosSemanales));
        }

        // Fila total global
        sb.append(filaTotalHoras(totalMinutos));

        sb.append("</tbody>\n</table>\n</div>\n");

        // Intervenciones manuales
        sb.append("<h3>Intervenciones manuales</h3>\n");
        List<String> fichajesManuales = new ArrayList<>();
        List<String> pausasManuales   = new ArrayList<>();
        for (DiaInforme dia : dias) {
            if (dia.esManual) {
                fichajesManuales.add(dia.fecha.format(FMT_FECHA)
                        + (dia.observaciones != null && !dia.observaciones.isBlank()
                           ? " – " + dia.observaciones : ""));
            }
            for (PausaInforme p : dia.pausas) {
                if (p.esManual) {
                    pausasManuales.add(dia.fecha.format(FMT_FECHA) + " "
                            + (p.horaInicio != null ? p.horaInicio.format(FMT_HORA) : "")
                            + (p.observaciones != null && !p.observaciones.isBlank()
                               ? " – " + p.observaciones : ""));
                }
            }
        }

        if (fichajesManuales.isEmpty() && pausasManuales.isEmpty()) {
            sb.append("<p class=\"sin-intervenciones\">Sin intervenciones manuales en el período.</p>\n");
        } else {
            if (!fichajesManuales.isEmpty()) {
                sb.append("<p><strong>Fichajes:</strong></p><ul>\n");
                for (String item : fichajesManuales) sb.append("  <li>").append(esc(item)).append("</li>\n");
                sb.append("</ul>\n");
            }
            if (!pausasManuales.isEmpty()) {
                sb.append("<p><strong>Pausas:</strong></p><ul>\n");
                for (String item : pausasManuales) sb.append("  <li>").append(esc(item)).append("</li>\n");
                sb.append("</ul>\n");
            }
        }

        sb.append("<div class=\"pie\">").append(esc(nombreEmpresa))
          .append(" &mdash; Generado el ").append(LocalDateTime.now().format(FMT_GENERA))
          .append("</div>\n</div>\n</body>\n</html>");

        return sb.toString();
    }

    /**
     * Fila de subtotal semanal para la tabla de detalle HTML.
     * 12 columnas: 5 para la etiqueta, 1 para horas efectivas, 6 vacias.
     * Misma logica que PdfService (subtotales semanales con fondo azul claro).
     */
    private String filaSubtotalSemana(int semana, int minutos) {
        return "<tr class=\"subtotal-semana\">\n"
             + "  <td colspan=\"5\">Subtotal semana " + semana + "</td>\n"
             + "  <td>" + formatearMinutos(minutos) + "</td>\n"
             + "  <td colspan=\"6\"></td>\n"
             + "</tr>\n";
    }

    private String filaTotalHoras(int minutos) {
        String estilo = "style=\"background:#2c3e6b;color:#ffffff;font-weight:700;"
                      + "font-size:12px;text-transform:uppercase;letter-spacing:0.5px;"
                      + "border:1px solid #1a2d56;padding:7px 10px;\"";
        return "<tr class=\"total-horas\">\n"
             + "  <td colspan=\"5\" " + estilo + ">TOTAL</td>\n"
             + "  <td " + estilo + ">" + formatearMinutos(minutos) + "</td>\n"
             + "  <td colspan=\"6\" " + estilo + "></td>\n"
             + "</tr>\n";
    }

    private String celdaDia(DiaInforme dia) {
        StringBuilder sb = new StringBuilder();
        sb.append("  <td>").append(dia.fecha.format(FMT_FECHA)).append("</td>\n");
        sb.append("  <td>").append(DIAS_ES.getOrDefault(dia.fecha.getDayOfWeek(), "")).append("</td>\n");
        sb.append("  <td>").append(TIPO_LEGIBLE.getOrDefault(dia.tipo, dia.tipo)).append("</td>\n");
        if (dia.tieneDatos) {
            String entrada = dia.horaEntrada != null ? dia.horaEntrada.format(FMT_HORA) : "&mdash;";
            String salida  = dia.horaSalida  != null ? dia.horaSalida.format(FMT_HORA)  : "&mdash;";
            if (dia.esManual) { entrada += "*"; salida += "*"; }
            sb.append("  <td>").append(entrada).append("</td>\n");
            sb.append("  <td>").append(salida).append("</td>\n");
            sb.append("  <td>").append(formatearMinutos(dia.horasEfectivas)).append("</td>\n");
            sb.append("  <td>").append(dia.totalPausasMinutos > 0
                    ? dia.totalPausasMinutos + " min" : "&mdash;").append("</td>\n");
        } else {
            sb.append("  <td>&mdash;</td><td>&mdash;</td><td>&mdash;</td><td>&mdash;</td>\n");
        }
        return sb.toString();
    }

    private String generarHtmlGlobal(LocalDate desde, LocalDate hasta,
                                      List<Map<String, Object>> filas,
                                      List<Empleado> empleados,
                                      Set<String> filtroTipos,
                                      String nombreEmpresa) {
        StringBuilder sb = new StringBuilder();
        sb.append(htmlCabeceraInforme());
        sb.append("<body>\n<div class=\"page\">\n");
        sb.append("<div class=\"header\">\n");
        sb.append("  <div class=\"titulo\">").append(esc(nombreEmpresa)).append("</div>\n");
        sb.append("  <div class=\"meta\">\n");
        sb.append("    <span>Informe global de jornada</span>\n");
        sb.append("    <span>").append(desde.format(FMT_FECHA)).append(" – ")
          .append(hasta.format(FMT_FECHA)).append("</span>\n");
        sb.append("    <span>Generado: ").append(LocalDateTime.now().format(FMT_GENERA)).append("</span>\n");
        sb.append("  </div>\n</div>\n");

        sb.append("<div class=\"tabla-scroll\">\n");
        sb.append("<table class=\"detalle\">\n<thead>\n<tr>\n");
        sb.append("  <th>Empleado</th><th>Días trab.</th><th>Horas ef.</th>");
        for (String tipo : ORDEN_AUSENCIAS) {
            sb.append("<th>").append(TIPO_LEGIBLE.getOrDefault(tipo, tipo)).append("</th>");
        }
        sb.append("\n</tr>\n</thead>\n<tbody>\n");

        for (Map<String, Object> fila : filas) {
            sb.append("<tr>\n");
            sb.append("  <td>").append(esc(String.valueOf(fila.get("empleado")))).append("</td>\n");
            sb.append("  <td>").append(fila.get("diasTrabajados")).append("</td>\n");
            sb.append("  <td>").append(fila.get("horasEfectivas")).append("</td>\n");
            for (String tipo : ORDEN_AUSENCIAS) {
                sb.append("  <td>").append(fila.getOrDefault(tipo.toLowerCase(), 0)).append("</td>\n");
            }
            sb.append("</tr>\n");
        }

        sb.append("</tbody>\n</table>\n</div>\n");
        sb.append("<div class=\"pie\">").append(esc(nombreEmpresa))
          .append(" &mdash; Generado el ").append(LocalDateTime.now().format(FMT_GENERA))
          .append("</div>\n</div>\n</body>\n</html>");

        return sb.toString();
    }

    // =========================================================================
    // CSS compartido para todos los informes HTML
    // =========================================================================

    private String htmlCabeceraInforme() {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>StaffFlow</title>
                  <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                      font-size: 13px;
                      color: #1a1a2e;
                      background: #ffffff;
                    }
                    .page {
                      max-width: 1400px;
                      margin: 0 auto;
                      padding: 24px 20px;
                    }
                    .header {
                      margin-bottom: 20px;
                      border-bottom: 2px solid #2c3e6b;
                      padding-bottom: 12px;
                    }
                    .titulo {
                      font-size: 16px;
                      font-weight: 600;
                      color: #1a1a2e;
                      margin-bottom: 6px;
                    }
                    .meta {
                      display: flex;
                      gap: 24px;
                      font-size: 13px;
                      color: #3a4a6a;
                    }
                    table.resumen {
                      border-collapse: collapse;
                      width: 340px;
                    }
                    table.resumen td {
                      padding: 5px 10px;
                      border-bottom: 1px solid #e8eaf0;
                      color: #1a1a2e;
                    }
                    table.resumen td:first-child {
                      font-weight: 500;
                      color: #3a4a6a;
                      width: 200px;
                    }
                    .tabla-scroll {
                      overflow-x: auto;
                    }
                    table.detalle {
                      border-collapse: collapse;
                      width: 100%;
                      margin-top: 4px;
                    }
                    table.detalle th {
                      background: #2c3e6b;
                      color: #ffffff;
                      padding: 8px 10px;
                      text-align: center;
                      font-size: 11px;
                      font-weight: 600;
                      text-transform: uppercase;
                      letter-spacing: 0.5px;
                      white-space: nowrap;
                      border: 1px solid #1a2d56;
                    }
                    table.detalle td {
                      padding: 7px 10px;
                      border: 1px solid #e8eaf0;
                      color: #1a1a2e;
                      white-space: nowrap;
                      text-align: center;
                    }
                    table.detalle tbody tr:nth-child(even) td {
                      background: #f7f8fc;
                    }
                    table.detalle tbody tr:hover td {
                      background: #f0f2f8;
                    }
                    table.detalle.saldos th.bloque {
                      font-size: 11px;
                      font-weight: 600;
                      letter-spacing: 0.5px;
                      text-transform: uppercase;
                      padding: 7px 10px;
                    }
                    table.detalle.saldos th.bloque-vac  { background: #185FA5; border: 1px solid #0C447C; }
                    table.detalle.saldos th.bloque-ap   { background: #0F6E56; border: 1px solid #085041; }
                    table.detalle.saldos th.bloque-dias { background: #BA7517; border: 1px solid #854F0B; }
                    table.detalle.saldos th.bloque-horas{ background: #534AB7; border: 1px solid #3C3489; }
                    table.detalle.saldos th.bloque-ctrl { background: #993C1D; border: 1px solid #712B13; }
                    table.detalle.saldos td.col-empleado,
                    table.detalle.saldos th.col-empleado {
                      text-align: center;
                      font-weight: 500;
                      min-width: 160px;
                    }
                    .valor-pos { color: #3B6D11; font-weight: 500; }
                    .valor-neg { color: #A32D2D; font-weight: 500; }
                    .fila-libre td {
                      color: #9aa5be;
                      font-style: italic;
                    }
                    .fila-ausencia td {
                      background: #fef9ec;
                    }
                    .fila-pausa-extra td {
                      background: #fafbfd;
                      border-top: none;
                    }
                    .subtotal-semana td {
                      background: #dce4f5;
                      color: #2c3e6b;
                      font-weight: 600;
                      font-size: 11px;
                      text-transform: uppercase;
                      letter-spacing: 0.4px;
                      border: 1px solid #b8c8e8;
                    }
                    table.detalle tbody tr.total-horas td {
                      background: #2c3e6b;
                      color: #ffffff;
                      font-weight: 700;
                      font-size: 12px;
                      text-transform: uppercase;
                      letter-spacing: 0.5px;
                      border: 1px solid #1a2d56;
                    }
                    .sin-intervenciones {
                      color: #9aa5be;
                      font-style: italic;
                      margin: 4px 0 12px;
                    }
                    ul {
                      padding-left: 20px;
                      margin-bottom: 12px;
                    }
                    ul li {
                      padding: 3px 0;
                      color: #3a4a6a;
                    }
                    .pie {
                      margin-top: 32px;
                      padding-top: 12px;
                      border-top: 1px solid #e8eaf0;
                      font-size: 11px;
                      color: #9aa5be;
                    }
                  </style>
                </head>
                """;
    }

    // =========================================================================
    // Utilidades privadas
    // =========================================================================

    /** Normaliza el filtro de tipos: null o vacío → Set vacío (sin filtro). */
    private Set<String> normalizarFiltro(List<String> tipos) {
        if (tipos == null || tipos.isEmpty()) return Set.of();
        return tipos.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    /**
     * Resuelve la lista de campos activos para E44.
     * Expande bloques a campos individuales. Sin parámetro activa todos.
     */
    private Set<String> resolverCampos(List<String> campos) {
        if (campos == null || campos.isEmpty()) {
            return new LinkedHashSet<>(CAMPOS_VALIDOS);
        }
        Set<String> resultado = new LinkedHashSet<>();
        for (String campo : campos) {
            String cUp = campo.toUpperCase();
            if (BLOQUES.containsKey(cUp)) {
                resultado.addAll(BLOQUES.get(cUp));
            } else if (CAMPOS_VALIDOS.contains(cUp)) {
                resultado.add(cUp);
            }
            // Valores desconocidos se ignoran silenciosamente
        }
        // Si tras resolver no hay ningún campo válido, activar todos
        if (resultado.isEmpty()) {
            resultado.addAll(CAMPOS_VALIDOS);
        }
        return resultado;
    }

    /** Devuelve true si al menos uno de los campos indicados está en camposActivos. */
    private boolean tieneAlgunCampo(Set<String> camposActivos, String... campos) {
        for (String c : campos) {
            if (camposActivos.contains(c)) return true;
        }
        return false;
    }

    /** Cuenta cuántos de los campos indicados están en camposActivos. */
    private int contarCamposBloque(Set<String> camposActivos, String... campos) {
        int count = 0;
        for (String c : campos) {
            if (camposActivos.contains(c)) count++;
        }
        return count;
    }

    /**
     * Devuelve la clase CSS según el valor numérico entero.
     * Positivo → valor-pos (verde). Negativo → valor-neg (rojo). Cero → vacío (negro).
     */
    private String claseCelda(int valor) {
        if (valor > 0) return "valor-pos";
        if (valor < 0) return "valor-neg";
        return "";
    }

    /** Formatea un BigDecimal a dos decimales con coma decimal. */
    private String formatearDecimal(java.math.BigDecimal bd) {
        if (bd == null) return "0,00";
        return String.format("%.2f", bd).replace('.', ',');
    }

    /** Construye nombre completo: nombre + apellido1 + apellido2 (si existe). */
    private String nombreCompleto(Empleado e) {
        String nombre = e.getNombre() + " " + e.getApellido1();
        if (e.getApellido2() != null && !e.getApellido2().isBlank()) {
            nombre += " " + e.getApellido2();
        }
        return nombre;
    }

    /** Convierte minutos a formato "Xh YYmin". */
    private String formatearMinutos(int minutos) {
        int h = minutos / 60;
        int m = minutos % 60;
        return h + "h " + String.format("%02d", m) + "min";
    }

    /** Formatea una LocalDateTime a HH:mm o devuelve "—" si es null. */
    private String celdaHora(LocalDateTime ldt) {
        return ldt != null ? ldt.format(FMT_HORA) : "—";
    }

    /** Formatea hora de pausa con asterisco si es manual. */
    private String celdaPausa(LocalDateTime ldt, boolean esManual) {
        if (ldt == null) return "—";
        return esManual ? ldt.format(FMT_HORA) + "*" : ldt.format(FMT_HORA);
    }

    /** Fila de la tabla resumen en HTML. */
    private String filaResumen(String label, String valor) {
        return "  <tr><td>" + esc(label) + "</td><td>" + esc(valor) + "</td></tr>\n";
    }

    /** Escapa caracteres HTML para evitar XSS en campos de texto libre. */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Formatea nombre legible de tipo de pausa. */
    private String formatearTipoPausa(String tipo) {
        return switch (tipo) {
            case "COMIDA"              -> "Comida";
            case "DESCANSO"            -> "Descanso";
            case "AUSENCIA_RETRIBUIDA" -> "Ausencia retribuida";
            case "OTROS"               -> "Otros";
            default                    -> tipo;
        };
    }

    /** Obtiene el nombre de empresa desde ConfiguracionEmpresa. */
    private String obtenerNombreEmpresa() {
        try {
            return empresaService.obtenerEmpresa().getNombreEmpresa();
        } catch (Exception e) {
            return "StaffFlow";
        }
    }

    // =========================================================================
    // Clases internas de modelo de informe (solo uso interno del service)
    // =========================================================================

    /** Representa un día del período en el informe. */
    private static class DiaInforme {
        LocalDate        fecha;
        String           tipo;
        boolean          tieneFichaje  = false;
        boolean          tieneDatos    = false;
        boolean          esManual      = false;
        LocalDateTime    horaEntrada;
        LocalDateTime    horaSalida;
        int              horasEfectivas    = 0;
        int              totalPausasMinutos = 0;
        String           observaciones;
        List<PausaInforme> pausas = new ArrayList<>();
    }

    /** Representa una pausa dentro de un día del informe. */
    private static class PausaInforme {
        LocalDateTime horaInicio;
        LocalDateTime horaFin;
        int           duracionMinutos;
        String        motivo;
        boolean       esManual;
        String        observaciones;
    }

    /** Acumula los totales del resumen del informe. */
    private static class ResumenInforme {
        int diasTrabajados              = 0;
        int horasEfectivasMinutos       = 0;
        int diasLibres                  = 0;
        int horasPermisoRetribuidoMinutos = 0;
        Map<String, Integer> ausenciasPorTipo = new LinkedHashMap<>();
    }
}
