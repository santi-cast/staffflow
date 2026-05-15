package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.Pausa;
import com.staffflow.domain.entity.PlanificacionAusencia;
import com.staffflow.domain.entity.SaldoAnual;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.exception.NotFoundException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * <p>E42 y E43 aceptan el parámetro opcional ?tipo= que filtra
 * el detalle por uno o varios tipos de jornada. Valores válidos: cualquier
 * valor del enum TipoFichaje más DIA_LIBRE y SIN_REGISTRO.</p>
 *
 * <p>E44 (GET /api/v1/informes/saldos) acepta parámetros opcionales
 * ?empleadoId= (uno o varios ids separados por coma) y ?campos= (bloques
 * o campos individuales). Sin parámetros devuelve todos los empleados
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
    private final SaldoService         saldoService;
    private final EmpresaService       empresaService;
    private final PlanificacionAusenciaRepository planificacionRepository;
    private final UsuarioRepository    usuarioRepository;

    // Tipos excluidos siempre del informe de ausencias (NORMAL y festivos)
    private static final Set<String> TIPOS_EXCLUIDOS_AUSENCIAS = Set.of(
            "NORMAL", "FESTIVO_NACIONAL", "FESTIVO_LOCAL", "DIA_LIBRE", "SIN_REGISTRO"
    );

    // Tipos incluidos en el filtro VACACIONES_AP
    private static final Set<String> TIPOS_VACACIONES_AP = Set.of("VACACIONES", "ASUNTO_PROPIO");

    // Formateadores reutilizables
    private static final DateTimeFormatter FMT_FECHA    = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA     = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FMT_HORA_SEG = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FMT_GENERA   = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

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
     * <p>El parámetro tipos filtra el detalle por tipo de jornada.
     * null o vacío devuelve todos los días del período.</p>
     *
     * @param empleadoId id del empleado
     * @param desde      fecha de inicio del período (inclusive)
     * @param hasta      fecha de fin del período (inclusive)
     * @param formato    "json" o "html" (defecto: json)
     * @param tipos      lista de tipos a incluir (null = todos)
     * @return informe de horas del empleado en el formato solicitado
     */
    /**
     * Informe de horas del empleado autenticado (E58).
     *
     * Resuelve username → usuario → empleado y delega en informeHorasEmpleado()
     * con formato=html fijo.
     *
     * @param username username del empleado autenticado (de authentication.getName())
     * @param desde    fecha de inicio del periodo
     * @param hasta    fecha de fin del periodo
     * @return HTML del informe de horas del empleado autenticado
     */
    @Transactional(readOnly = true)
    public Object informeHorasMe(String username, LocalDate desde, LocalDate hasta) {

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario autenticado no encontrado: " + username));

        Empleado empleado = empleadoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "El usuario autenticado no tiene perfil de empleado"));

        return informeHorasEmpleado(empleado.getId(), desde, hasta, "html", null);
    }

    @Transactional(readOnly = true)
    public Object informeHorasEmpleado(Long empleadoId, LocalDate desde,
                                        LocalDate hasta, String formato,
                                        List<String> tipos) {

        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new NotFoundException(
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
     * <p>El parámetro tipos filtra el detalle por tipo de jornada.</p>
     *
     * @param desde   fecha de inicio del período (inclusive)
     * @param hasta   fecha de fin del período (inclusive)
     * @param formato "json" o "html" (defecto: json)
     * @param tipos   lista de tipos a incluir (null = todos)
     * @return informe global de horas en el formato solicitado
     */
    @Transactional(readOnly = true)
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
    // =========================================================================

    /**
     * Genera el informe de saldos anuales por empleado (E44).
     *
     * <p>Devuelve el saldo anual de cada empleado: días vacaciones, días asuntos
     * propios, resto de días (trabajados, baja médica, permiso retribuido,
     * ausencia injustificada), horas de ausencia retribuida, saldo de horas,
     * fecha calculado hasta y fecha de última modificación.</p>
     *
     * <p>Parámetro ?empleadoId= opcional. Sin parámetro devuelve todos
     * los empleados activos con saldo en ese año. Con uno o varios ids separados
     * por coma devuelve solo esos empleados.</p>
     *
     * <p>Parámetro ?campos= opcional. Acepta bloques predefinidos
     * (DIAS_VACACIONES, DIAS_ASUNTOS_PROPIOS, RESTO_DIAS, HORAS, CONTROL)
     * y campos individuales. Sin parámetro se muestran todos los bloques.</p>
     *
     * @param anio        año a consultar
     * @param formato     "json" o "html" (defecto: json)
     * @param empleadoIds lista de ids de empleado (null = todos los activos)
     * @param campos      lista de bloques o campos a incluir (null = todos)
     * @return informe de saldos en el formato solicitado
     */
    @Transactional(readOnly = true)
    public Object informeSaldos(Integer anio, String formato,
                                 List<Long> empleadoIds, List<String> campos) {

        // Resolver campos seleccionados (bloques → campos individuales)
        Set<String> camposActivos = resolverCampos(campos);

        // Comprobar si existe algún saldo en BD para ese año antes de crear on-demand.
        // Si no hay ningún registro previo → el año no tiene datos reales → Empty state.
        List<SaldoAnual> existentes = saldoRepository.findByAnio(anio).stream()
                .filter(s -> Boolean.TRUE.equals(s.getEmpleado().getActivo()))
                .collect(Collectors.toList());

        if (existentes.isEmpty()) {
            throw new NotFoundException("No hay datos de saldo para el año " + anio);
        }

        // El año tiene datos: completar on-demand los empleados activos que aún no tengan registro.
        empleadoRepository.findAll().stream()
                .filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .filter(e -> saldoRepository.findByEmpleadoIdAndAnio(e.getId(), anio).isEmpty())
                .forEach(e -> saldoService.recalcularParaProceso(e.getId(), anio));

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
                sb.append("  <td class=\"col-vac\">").append(s.getDiasVacacionesPendientesAnioAnterior()).append("</td>\n");
            if (camposActivos.contains("VAC_DERECHO"))
                sb.append("  <td class=\"col-vac\">").append(s.getDiasVacacionesDerechoAnio()).append("</td>\n");
            if (camposActivos.contains("VAC_CONSUMIDOS"))
                sb.append("  <td class=\"col-vac\">").append(s.getDiasVacacionesConsumidos()).append("</td>\n");
            if (camposActivos.contains("VAC_DISPONIBLES")) {
                int disp = s.getDiasVacacionesDisponibles();
                String extra = claseCelda(disp);
                sb.append("  <td class=\"col-vac").append(extra.isEmpty() ? "" : " " + extra).append("\">").append(disp).append("</td>\n");
            }

            // Bloque asuntos propios
            if (camposActivos.contains("AP_PENDIENTE_ANT"))
                sb.append("  <td class=\"col-ap\">").append(s.getDiasAsuntosPropiosPendientesAnterior()).append("</td>\n");
            if (camposActivos.contains("AP_DERECHO"))
                sb.append("  <td class=\"col-ap\">").append(s.getDiasAsuntosPropiosDerechoAnio()).append("</td>\n");
            if (camposActivos.contains("AP_CONSUMIDOS"))
                sb.append("  <td class=\"col-ap\">").append(s.getDiasAsuntosPropiosConsumidos()).append("</td>\n");
            if (camposActivos.contains("AP_DISPONIBLES")) {
                int disp = s.getDiasAsuntosPropiosDisponibles();
                String extra = claseCelda(disp);
                sb.append("  <td class=\"col-ap").append(extra.isEmpty() ? "" : " " + extra).append("\">").append(disp).append("</td>\n");
            }

            // Bloque resto días
            if (camposActivos.contains("DIAS_TRABAJADOS"))
                sb.append("  <td class=\"col-dias\">").append(s.getDiasTrabajados()).append("</td>\n");
            if (camposActivos.contains("DIAS_BAJA_MEDICA"))
                sb.append("  <td class=\"col-dias\">").append(s.getDiasBajaMedica()).append("</td>\n");
            if (camposActivos.contains("DIAS_PERMISO_RETRIBUIDO"))
                sb.append("  <td class=\"col-dias\">").append(s.getDiasPermisoRetribuido()).append("</td>\n");
            if (camposActivos.contains("DIAS_AUSENCIA_INJUSTIFICADA"))
                sb.append("  <td class=\"col-dias\">").append(s.getDiasAusenciaInjustificada()).append("</td>\n");

            // Bloque horas
            if (camposActivos.contains("HORAS_AUSENCIA_RETRIBUIDA"))
                sb.append("  <td class=\"col-horas\">").append(formatearDecimal(s.getHorasAusenciaRetribuida())).append("</td>\n");
            if (camposActivos.contains("SALDO_HORAS")) {
                double saldoHoras = s.getSaldoHoras() != null ? s.getSaldoHoras().doubleValue() : 0.0;
                String claseHoras = saldoHoras > 0 ? "valor-pos" : (saldoHoras < 0 ? "valor-neg" : "");
                String prefijo = saldoHoras > 0 ? "+" : "";
                sb.append("  <td class=\"col-horas").append(claseHoras.isEmpty() ? "" : " " + claseHoras).append("\">")
                  .append(prefijo).append(formatearDecimal(s.getSaldoHoras()))
                  .append("</td>\n");
            }

            // Bloque control
            if (camposActivos.contains("CALCULADO_HASTA"))
                sb.append("  <td class=\"col-ctrl\">").append(s.getCalculadoHastaFecha() != null
                        ? s.getCalculadoHastaFecha().format(FMT_FECHA) : "&mdash;").append("</td>\n");
            if (camposActivos.contains("ULTIMA_MODIFICACION"))
                sb.append("  <td class=\"col-ctrl\">").append(s.getFechaUltimaModificacion() != null
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
        sb.append("</div>\n");
        sb.append("</body>\n</html>");

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
                .findByEmpleadoIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(empleadoId, desde, hasta);
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
        // Si el fichaje es candidato a manual, se consulta planificacion_ausencias
        // para descartar registros planificados anticipadamente (festivos,
        // vacaciones planificadas, etc.).
        // Si existe planificación para ese empleado y fecha → no es manual.
        // Si no existe planificación → sí es intervención manual real.
        // Solución definitiva en M-012 (planificacion_id en Fichaje).
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
                            + (p.horaInicio != null ? p.horaInicio.format(FMT_HORA_SEG) : "")
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
            String entrada = dia.horaEntrada != null ? dia.horaEntrada.format(FMT_HORA_SEG) : "&mdash;";
            String salida  = dia.horaSalida  != null ? dia.horaSalida.format(FMT_HORA_SEG)  : "&mdash;";
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
    // E-ausencias — GET /api/v1/ausencias/me/informe
    // Informe HTML de ausencias del empleado autenticado (ejecutadas + planificadas)
    // =========================================================================

    /**
     * Genera el informe HTML de ausencias del empleado autenticado.
     *
     * Combina dos fuentes:
     *   - planificacion_ausencias: ausencias planificadas (procesado=false) y ejecutadas vía flujo normal (procesado=true)
     *   - fichajes tipo != NORMAL: ausencias registradas directamente sin planificación
     * Para una misma fecha, el fichaje tiene prioridad (es el dato ejecutado real).
     *
     * Excluye siempre NORMAL, FESTIVO_NACIONAL, FESTIVO_LOCAL, DIA_LIBRE.
     * filtro=VACACIONES_AP muestra solo VACACIONES y ASUNTO_PROPIO.
     *
     * @param username username del empleado autenticado
     * @param desde    fecha de inicio del periodo
     * @param hasta    fecha de fin del periodo
     * @param filtro   "TODAS" o "VACACIONES_AP"
     * @return HTML del informe de ausencias
     */
    /**
     * Informe HTML de ausencias de un empleado por id (ADMIN/ENCARGADO).
     * Misma lógica que informeAusenciasMe pero resolviendo por empleadoId.
     *
     * @param empleadoId id del empleado
     * @param desde      fecha de inicio del periodo
     * @param hasta      fecha de fin del periodo
     * @param filtro     "TODAS" o "VACACIONES_AP"
     * @return HTML del informe de ausencias
     */
    @Transactional(readOnly = true)
    public String informeAusenciasEmpleado(Long empleadoId, LocalDate desde, LocalDate hasta, String filtro) {

        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Empleado con id " + empleadoId + " no encontrado"));

        String nombreEmpresa = obtenerNombreEmpresa();
        boolean soloVacAp = "VACACIONES_AP".equalsIgnoreCase(filtro);

        List<Fichaje> fichajes = fichajeRepository
                .findByEmpleadoIdAndFechaBetween(empleado.getId(), desde, hasta)
                .stream()
                .filter(f -> !TIPOS_EXCLUIDOS_AUSENCIAS.contains(f.getTipo().name()))
                .collect(Collectors.toList());

        List<PlanificacionAusencia> planificaciones = planificacionRepository
                .findByEmpleadoIdAndRango(empleado.getId(), desde, hasta)
                .stream()
                .filter(p -> !TIPOS_EXCLUIDOS_AUSENCIAS.contains(p.getTipoAusencia().name()))
                .collect(Collectors.toList());

        Map<LocalDate, AusenciaInformeRow> porFecha = new LinkedHashMap<>();
        for (PlanificacionAusencia p : planificaciones) {
            String estado = Boolean.TRUE.equals(p.getProcesado()) ? "Ejecutada" : "Planificada";
            porFecha.put(p.getFecha(), new AusenciaInformeRow(
                    p.getFecha(), p.getTipoAusencia().name(), estado, p.getObservaciones()));
        }
        for (Fichaje f : fichajes) {
            porFecha.put(f.getFecha(), new AusenciaInformeRow(
                    f.getFecha(), f.getTipo().name(), "Ejecutada", f.getObservaciones()));
        }

        List<AusenciaInformeRow> filas = porFecha.values().stream()
                .filter(r -> !soloVacAp || TIPOS_VACACIONES_AP.contains(r.tipo))
                .sorted(Comparator.comparing(r -> r.fecha))
                .collect(Collectors.toList());

        return generarHtmlAusencias(empleado, desde, hasta, filas, nombreEmpresa, soloVacAp);
    }

    @Transactional(readOnly = true)
    public String informeAusenciasMe(String username, LocalDate desde, LocalDate hasta, String filtro) {

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario autenticado no encontrado: " + username));
        Empleado empleado = empleadoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "El usuario autenticado no tiene perfil de empleado"));

        String nombreEmpresa = obtenerNombreEmpresa();
        boolean soloVacAp = "VACACIONES_AP".equalsIgnoreCase(filtro);

        // 1. Fichajes con tipo ausencia (excluir NORMAL y festivos)
        List<Fichaje> fichajes = fichajeRepository
                .findByEmpleadoIdAndFechaBetween(empleado.getId(), desde, hasta)
                .stream()
                .filter(f -> !TIPOS_EXCLUIDOS_AUSENCIAS.contains(f.getTipo().name()))
                .collect(Collectors.toList());

        // 2. Planificaciones (excluir festivos)
        List<PlanificacionAusencia> planificaciones = planificacionRepository
                .findByEmpleadoIdAndRango(empleado.getId(), desde, hasta)
                .stream()
                .filter(p -> !TIPOS_EXCLUIDOS_AUSENCIAS.contains(p.getTipoAusencia().name()))
                .collect(Collectors.toList());

        // 3. Merge: planificaciones primero, fichajes sobreescriben (son la fuente autoritativa)
        Map<LocalDate, AusenciaInformeRow> porFecha = new LinkedHashMap<>();
        for (PlanificacionAusencia p : planificaciones) {
            String estado = Boolean.TRUE.equals(p.getProcesado()) ? "Ejecutada" : "Planificada";
            porFecha.put(p.getFecha(), new AusenciaInformeRow(
                    p.getFecha(), p.getTipoAusencia().name(), estado, p.getObservaciones()));
        }
        for (Fichaje f : fichajes) {
            porFecha.put(f.getFecha(), new AusenciaInformeRow(
                    f.getFecha(), f.getTipo().name(), "Ejecutada", f.getObservaciones()));
        }

        // 4. Aplicar filtro y ordenar por fecha
        List<AusenciaInformeRow> filas = porFecha.values().stream()
                .filter(r -> !soloVacAp || TIPOS_VACACIONES_AP.contains(r.tipo))
                .sorted(Comparator.comparing(r -> r.fecha))
                .collect(Collectors.toList());

        return generarHtmlAusencias(empleado, desde, hasta, filas, nombreEmpresa, soloVacAp);
    }

    private String generarHtmlAusencias(Empleado empleado, LocalDate desde, LocalDate hasta,
                                         List<AusenciaInformeRow> filas,
                                         String nombreEmpresa, boolean soloVacAp) {

        // Resumen por tipo
        Map<String, Integer> conteoTipos = new LinkedHashMap<>();
        for (AusenciaInformeRow fila : filas) {
            conteoTipos.merge(fila.tipo, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(htmlCabeceraInforme());
        sb.append("<body>\n<div class=\"page\">\n");

        sb.append("<div class=\"header\">\n");
        sb.append("  <div class=\"titulo\">").append(esc(nombreEmpresa)).append("</div>\n");
        sb.append("  <div class=\"meta\">\n");
        sb.append("    <span>Informe de ausencias</span>\n");
        sb.append("    <span>").append(esc(nombreCompleto(empleado))).append("</span>\n");
        sb.append("    <span>").append(desde.format(FMT_FECHA)).append(" – ")
          .append(hasta.format(FMT_FECHA)).append("</span>\n");
        if (soloVacAp) {
            sb.append("    <span>Filtro: Vacaciones y asuntos propios</span>\n");
        }
        sb.append("    <span>Generado: ").append(LocalDateTime.now().format(FMT_GENERA)).append("</span>\n");
        sb.append("  </div>\n</div>\n");

        // Tabla resumen
        sb.append("<table class=\"resumen\">\n");
        sb.append(filaResumen("Total ausencias", String.valueOf(filas.size())));
        for (Map.Entry<String, Integer> entry : conteoTipos.entrySet()) {
            sb.append(filaResumen(
                    TIPO_LEGIBLE.getOrDefault(entry.getKey(), entry.getKey()),
                    String.valueOf(entry.getValue())));
        }
        sb.append("</table>\n");

        // Tabla detalle
        sb.append("<div class=\"tabla-scroll\">\n");
        sb.append("<table class=\"detalle\">\n<thead>\n<tr>\n");
        sb.append("  <th>Fecha</th><th>Dia</th><th>Tipo</th><th>Estado</th><th>Observaciones</th>\n");
        sb.append("</tr>\n</thead>\n<tbody>\n");

        for (AusenciaInformeRow fila : filas) {
            String claseFilaEstado = "Planificada".equals(fila.estado) ? "fila-ausencia" : "";
            sb.append("<tr class=\"").append(claseFilaEstado).append("\">\n");
            sb.append("  <td>").append(fila.fecha.format(FMT_FECHA)).append("</td>\n");
            sb.append("  <td>").append(DIAS_ES.getOrDefault(fila.fecha.getDayOfWeek(), "")).append("</td>\n");
            sb.append("  <td>").append(TIPO_LEGIBLE.getOrDefault(fila.tipo, fila.tipo)).append("</td>\n");
            String claseEstado = "Planificada".equals(fila.estado) ? "estado-planificada" : "estado-ejecutada";
            sb.append("  <td class=\"").append(claseEstado).append("\">").append(fila.estado).append("</td>\n");
            sb.append("  <td>").append(
                    fila.observaciones != null && !fila.observaciones.isBlank()
                            ? esc(fila.observaciones) : "&mdash;").append("</td>\n");
            sb.append("</tr>\n");
        }

        if (filas.isEmpty()) {
            sb.append("<tr><td colspan=\"5\" style=\"text-align:center;color:#9aa5be;font-style:italic;\">")
              .append("No hay ausencias en el periodo seleccionado").append("</td></tr>\n");
        }

        sb.append("</tbody>\n</table>\n</div>\n");
        sb.append("<div class=\"pie\">").append(esc(nombreEmpresa))
          .append(" &mdash; Generado el ").append(LocalDateTime.now().format(FMT_GENERA))
          .append("</div>\n</div>\n</body>\n</html>");

        return sb.toString();
    }

    // =========================================================================
    // E-semana — GET /api/v1/informes/semana
    // Resumen semanal de todos los empleados activos (fichajes + pausas + ausencias)
    // =========================================================================

    /**
     * Genera el HTML del resumen semanal de todos los empleados activos.
     *
     * <p>Tabla: filas = empleados activos, columnas = días (desde–hasta).
     * Cada celda muestra el fichaje, pausas y/o ausencia planificada del día.
     * Los enlaces de edición se muestran condicionalmente según rol y fecha:</p>
     * <ul>
     *   <li>Fichajes/pausas: ADMIN en cualquier fecha no futura; ENCARGADO solo hoy.</li>
     *   <li>Ausencias: ADMIN en cualquier fecha; ENCARGADO en hoy y futuro.</li>
     *   <li>Futuro: solo ausencias planificadas son visibles y editables.</li>
     * </ul>
     *
     * @param desde    primer día de la semana (lunes)
     * @param hasta    último día de la semana (domingo)
     * @param username username del usuario autenticado
     * @return HTML del resumen semanal
     */
    @Transactional(readOnly = true)
    public String informeSemana(LocalDate desde, LocalDate hasta, String username) {

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario no encontrado: " + username));
        Rol rol = usuario.getRol();
        LocalDate hoy = LocalDate.now();

        // Empleados activos ordenados por nombre
        List<Empleado> empleados = empleadoRepository.findByActivo(true).stream()
                .sorted(Comparator.comparing(this::nombreCompleto))
                .collect(Collectors.toList());

        // Días del rango
        List<LocalDate> dias = new ArrayList<>();
        LocalDate cursor = desde;
        while (!cursor.isAfter(hasta)) { dias.add(cursor); cursor = cursor.plusDays(1); }

        // Fichajes: empleadoId -> fecha -> Fichaje
        Map<Long, Map<LocalDate, Fichaje>> fichajesMap = fichajeRepository
                .findByFiltros(null, desde, hasta, null).stream()
                .collect(Collectors.groupingBy(
                        f -> f.getEmpleado().getId(),
                        Collectors.toMap(Fichaje::getFecha, f -> f)));

        // Pausas: empleadoId -> fecha -> List<Pausa>
        Map<Long, Map<LocalDate, List<Pausa>>> pausasMap = pausaRepository
                .findByFiltros(null, desde, hasta, null).stream()
                .collect(Collectors.groupingBy(
                        p -> p.getEmpleado().getId(),
                        Collectors.groupingBy(Pausa::getFecha)));

        // Ausencias planificadas: empleadoId -> fecha -> PlanificacionAusencia
        // Se excluyen festivos globales (empleado null)
        Map<Long, Map<LocalDate, PlanificacionAusencia>> ausenciasMap = planificacionRepository
                .findByFiltros(null, desde, hasta, null).stream()
                .filter(a -> a.getEmpleado() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getEmpleado().getId(),
                        Collectors.toMap(PlanificacionAusencia::getFecha, a -> a,
                                (a1, a2) -> a1)));

        // Saldo inicial por empleado: suma de contribuciones de fichajes del año
        // ANTES de 'desde'. Calculado desde fichajes reales, no desde SaldoAnual,
        // para que sea correcto al navegar a semanas pasadas o futuras.
        int anio = desde.getYear();
        Map<Long, BigDecimal> saldoInicialMap = calcularSaldoHastaFecha(
                empleados, LocalDate.of(anio, 1, 1), desde.minusDays(1));

        return generarHtmlSemana(empleados, dias, fichajesMap, pausasMap, ausenciasMap,
                saldoInicialMap, rol, hoy, desde, hasta);
    }

    private String generarHtmlSemana(
            List<Empleado> empleados, List<LocalDate> dias,
            Map<Long, Map<LocalDate, Fichaje>> fichajesMap,
            Map<Long, Map<LocalDate, List<Pausa>>> pausasMap,
            Map<Long, Map<LocalDate, PlanificacionAusencia>> ausenciasMap,
            Map<Long, BigDecimal> saldoInicialMap,
            Rol rol, LocalDate hoy, LocalDate desde, LocalDate hasta) {

        DateTimeFormatter fmtDia = DateTimeFormatter.ofPattern("dd/MM");

        StringBuilder sb = new StringBuilder();
        sb.append(htmlCabeceraInforme());
        // Estilos adicionales para la tabla semanal (dentro del body para no tocar el head)
        sb.append("<body>\n<style>\n");
        sb.append(".tabla-semana{border-collapse:collapse;min-width:100%;font-size:12px;}\n");
        sb.append(".tabla-semana th{background:#2c3e6b;color:#fff;padding:7px 8px;text-align:center;white-space:nowrap;border:1px solid #1a2d56;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.4px;}\n");
        sb.append(".tabla-semana td{padding:6px 8px;border:1px solid #e8eaf0;vertical-align:top;min-width:90px;}\n");
        sb.append(".tabla-semana tbody tr:nth-child(even) td{background:#f7f8fc;}\n");
        sb.append("@media(hover:hover){.tabla-semana tbody tr:hover td{background:#f0f2f8;}}\n");
        sb.append(".th-hoy{background:#1a6b3c!important;border-color:#0e4727!important;}\n");
        sb.append(".td-hoy{background:#f0faf4!important;}\n");
        sb.append(".td-futuro{color:#777;}\n");
        sb.append(".col-empleado{min-width:140px;font-weight:500;text-align:left!important;white-space:nowrap;}\n");
        sb.append(".num-empleado{font-size:10px;color:#9aa5be;font-weight:400;}\n");
        sb.append(".jornada-contrato{font-size:10px;color:#9aa5be;font-weight:400;}\n");
        sb.append(".link-celda{color:#2c3e6b;text-decoration:none;font-weight:500;}\n");
        sb.append(".link-pausa{color:#534AB7;text-decoration:none;font-size:11px;}\n");
        sb.append(".link-ausencia{color:#534AB7;text-decoration:none;}\n");
        sb.append(".link-planif{color:#BA7517;text-decoration:none;}\n");
        sb.append(".link-celda:hover,.link-pausa:hover,.link-ausencia:hover,.link-planif:hover{text-decoration:underline;}\n");
        sb.append(".horas{color:#555;font-size:11px;}\n");
        sb.append(".pausa-info{color:#534AB7;font-size:11px;}\n");
        sb.append(".ausencia-label{color:#534AB7;}\n");
        sb.append(".planif-label{color:#BA7517;}\n");
        sb.append(".vacio{color:#ccc;}\n");
        sb.append(".libre{color:#aaa;font-size:11px;font-style:italic;}\n");
        sb.append(".td-finde{background:#f4f4f6!important;}\n");
        sb.append(".col-saldo{background:#1a2d56!important;color:#fff;text-align:center;white-space:nowrap;min-width:48px;width:52px;}\n");
        sb.append(".col-saldo-val{text-align:center;font-weight:600;white-space:nowrap;font-size:11px;}\n");
        sb.append(".saldo-pos{color:#1a6b3c;}\n");
        sb.append(".saldo-neg{color:#b71c1c;}\n");
        sb.append(".saldo-cero{color:#777;}\n");
        sb.append(".col-total{background:#1a2d56!important;color:#fff;text-align:center;white-space:nowrap;min-width:70px;}\n");
        sb.append(".col-total-val{text-align:center;font-weight:600;color:#2c3e6b;white-space:nowrap;}\n");
        sb.append(".dif-inline{font-size:11px;font-weight:600;}\n");
        sb.append(".col-saldo-fin{background:#1a2d56!important;color:#fff;text-align:center;white-space:nowrap;min-width:48px;width:52px;}\n");
        sb.append(".col-saldo-fin-val{text-align:center;font-weight:700;white-space:nowrap;font-size:11px;}\n");
        sb.append(".sin-datos{text-align:center;color:#9aa5be;font-style:italic;padding:20px;}\n");
        sb.append("</style>\n");
        sb.append("<div class=\"page\">\n");
        sb.append("<div class=\"header\">\n  <div class=\"titulo\">Resumen semanal &mdash; ")
          .append(desde.format(FMT_FECHA)).append(" &ndash; ").append(hasta.format(FMT_FECHA))
          .append("</div>\n</div>\n");

        sb.append("<div class=\"tabla-scroll\">\n<table class=\"tabla-semana\">\n<thead>\n<tr>\n");
        sb.append("  <th class=\"col-empleado\">Empleado</th>\n");
        sb.append("  <th class=\"col-saldo\">Saldo<br>horas</th>\n");
        for (LocalDate dia : dias) {
            String clsTh = dia.isEqual(hoy) ? " th-hoy" : "";
            sb.append("  <th class=\"th-dia").append(clsTh).append("\">")
              .append(DIAS_ES.getOrDefault(dia.getDayOfWeek(), ""))
              .append("<br>").append(dia.format(fmtDia)).append("</th>\n");
        }
        sb.append("  <th class=\"col-total\">Total<br>semana</th>\n");
        sb.append("  <th class=\"col-saldo-fin\">Saldo<br>al cierre</th>\n");
        sb.append("</tr>\n</thead>\n<tbody>\n");

        for (Empleado emp : empleados) {
            sb.append("<tr>\n  <td class=\"col-empleado\">")
              .append(esc(nombreCompleto(emp)))
              .append("<br><span class=\"num-empleado\">").append(esc(emp.getNumeroEmpleado())).append("</span>")
              .append("<br><span class=\"jornada-contrato\">").append(String.format("%.0f", emp.getJornadaSemanalHoras())).append("h/sem</span>")
              .append("</td>\n");

            // Celda de saldo inicial (acumulado antes de esta semana)
            BigDecimal saldoInicial = saldoInicialMap.getOrDefault(emp.getId(), BigDecimal.ZERO);
            {
                double saldoD = saldoInicial.doubleValue();
                String clsSaldo = saldoD > 0 ? "saldo-pos" : (saldoD < 0 ? "saldo-neg" : "saldo-cero");
                String prefijo  = saldoD > 0 ? "+" : "";
                String saldoStr = prefijo + String.format("%.2f", saldoD) + "h";
                sb.append("  <td class=\"col-saldo-val ").append(clsSaldo).append("\">")
                  .append(saldoStr).append("</td>\n");
            }

            Map<LocalDate, Fichaje> fichajes = fichajesMap.getOrDefault(emp.getId(), Map.of());
            Map<LocalDate, List<Pausa>> pausas  = pausasMap.getOrDefault(emp.getId(), Map.of());
            Map<LocalDate, PlanificacionAusencia> ausencias = ausenciasMap.getOrDefault(emp.getId(), Map.of());

            int totalMinutosSemana = 0;
            int weekSaldoMinutos   = 0;  // contribución real al saldo de esta semana

            for (LocalDate dia : dias) {
                boolean esPasado = dia.isBefore(hoy);
                boolean esFuturo = dia.isAfter(hoy);
                boolean puedeEditarFichajePausa = !esFuturo && (rol == Rol.ADMIN || !esPasado);
                boolean puedeEditarAusencia     = rol == Rol.ADMIN || !esPasado;
                boolean esFinde = dia.getDayOfWeek() == DayOfWeek.SATURDAY
                        || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
                String clsTd = dia.isEqual(hoy) ? " td-hoy"
                        : (esFuturo ? " td-futuro" : (esFinde ? " td-finde" : ""));

                sb.append("  <td class=\"celda").append(clsTd).append("\">\n");

                Fichaje fichaje = fichajes.get(dia);
                List<Pausa> pausasDia = pausas.getOrDefault(dia, List.of());
                PlanificacionAusencia ausencia = ausencias.get(dia);

                if (fichaje != null) {
                    sb.append(celdaConFichaje(fichaje, pausasDia, emp.getId(), dia, puedeEditarFichajePausa));
                    int minFichaje = fichaje.getJornadaEfectivaMinutos() != null
                            ? fichaje.getJornadaEfectivaMinutos() : 0;
                    // BAJA y PERMISO: crédito de jornada teórica para total y saldo
                    if (fichaje.getTipo() == TipoFichaje.BAJA_MEDICA
                            || fichaje.getTipo() == TipoFichaje.PERMISO_RETRIBUIDO) {
                        minFichaje = emp.getJornadaDiariaMinutos();
                    }
                    totalMinutosSemana += minFichaje;
                    // Contribución al saldo: NORMAL=(efectiva-diaria), BAJA/PERMISO=+diaria, resto=0
                    weekSaldoMinutos += saldoContribMinutos(fichaje.getTipo(), minFichaje, emp.getJornadaDiariaMinutos());
                } else if (ausencia != null) {
                    sb.append(celdaAusenciaPlanificada(ausencia, emp.getId(), dia, puedeEditarAusencia));
                } else {
                    if (esFinde) {
                        sb.append("    <span class=\"libre\">D&iacute;a libre</span>\n");
                    } else {
                        sb.append("    <span class=\"vacio\">&mdash;</span>\n");
                    }
                }
                sb.append("  </td>\n");
            }

            // Contribución al saldo de esta semana (día a día, no vs jornada semanal)
            double weekSaldoHoras = weekSaldoMinutos / 60.0;
            String clsDif = weekSaldoMinutos > 0 ? "saldo-pos" : (weekSaldoMinutos < 0 ? "saldo-neg" : "saldo-cero");
            String prefDif = weekSaldoMinutos >= 0 ? "+" : "";
            String difStr  = prefDif + String.format("%.2f", weekSaldoHoras) + "h";

            // Celda total semana: horas totales + contribución al saldo debajo
            String totalStr = totalMinutosSemana > 0 ? formatearMinutos(totalMinutosSemana) : "&mdash;";
            sb.append("  <td class=\"col-total-val\">");
            sb.append(totalStr);
            sb.append("<br><span class=\"dif-inline ").append(clsDif).append("\">")
              .append(difStr).append("</span>");
            sb.append("</td>\n");

            // Saldo al cierre: saldo inicial + contribución de la semana
            double saldoCierre = saldoInicial.doubleValue() + weekSaldoHoras;
            String clsFin = saldoCierre > 0 ? "saldo-pos" : (saldoCierre < 0 ? "saldo-neg" : "saldo-cero");
            String prefFin = saldoCierre > 0 ? "+" : "";
            String finStr  = prefFin + String.format("%.2f", saldoCierre) + "h";
            sb.append("  <td class=\"col-saldo-fin-val ").append(clsFin).append("\">")
              .append(finStr).append("</td>\n");

            sb.append("</tr>\n");
        }

        if (empleados.isEmpty()) {
            sb.append("<tr><td colspan=\"11\" class=\"sin-datos\">No hay empleados activos</td></tr>\n");
        }

        sb.append("</tbody>\n</table>\n</div>\n");
        sb.append("<div class=\"pie\">StaffFlow &mdash; Generado el ")
          .append(LocalDate.now().format(FMT_FECHA)).append("</div>\n");
        sb.append("</div>\n");
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private String celdaConFichaje(Fichaje f, List<Pausa> pausas, Long empleadoId,
                                    LocalDate dia, boolean puedeEditar) {
        StringBuilder sb = new StringBuilder();
        boolean esNormal = f.getTipo() == TipoFichaje.NORMAL;
        String urlF = urlFichaje(f.getId(), empleadoId, dia, f);

        if (esNormal) {
            String entrada = f.getHoraEntrada() != null ? f.getHoraEntrada().format(FMT_HORA) : "&mdash;";
            String salida  = f.getHoraSalida()  != null ? f.getHoraSalida().format(FMT_HORA)  : "&mdash;";
            String horas   = (f.getJornadaEfectivaMinutos() != null
                    && f.getJornadaEfectivaMinutos() > 0)
                    ? formatearMinutos(f.getJornadaEfectivaMinutos()) : "";
            if (puedeEditar) {
                sb.append("    <a href=\"").append(urlF).append("\" class=\"link-celda\">")
                  .append(entrada).append("&ndash;").append(salida).append(" &#9998;</a>");
            } else {
                sb.append("    <span>").append(entrada).append("&ndash;").append(salida).append("</span>");
            }
            for (Pausa p : pausas) {
                String dur    = p.getDuracionMinutos() != null ? p.getDuracionMinutos() + "min" : "activa";
                String motivo = p.getTipoPausa() != null ? formatearTipoPausa(p.getTipoPausa().name()) : "";
                sb.append("<br>");
                if (puedeEditar) {
                    sb.append("<a href=\"").append(urlPausa(p.getId(), empleadoId, dia, p))
                      .append("\" class=\"link-pausa\">").append(esc(motivo)).append(" ").append(dur)
                      .append(" &#9998;</a>");
                } else {
                    sb.append("<span class=\"pausa-info\">").append(esc(motivo)).append(" ").append(dur)
                      .append("</span>");
                }
            }
            if (!horas.isEmpty()) {
                sb.append("<br><span class=\"horas\">").append(horas).append("</span>");
            }
        } else {
            String label = TIPO_LEGIBLE.getOrDefault(f.getTipo().name(), f.getTipo().name());
            if (puedeEditar) {
                sb.append("    <a href=\"").append(urlF).append("\" class=\"link-ausencia\">")
                  .append(esc(label)).append(" &#9998;</a>");
            } else {
                sb.append("    <span class=\"ausencia-label\">").append(esc(label)).append("</span>");
            }
        }
        return sb.toString();
    }

    private String celdaAusenciaPlanificada(PlanificacionAusencia a, Long empleadoId,
                                             LocalDate dia, boolean puedeEditar) {
        String label    = TIPO_LEGIBLE.getOrDefault(a.getTipoAusencia().name(), a.getTipoAusencia().name());
        boolean procesada = Boolean.TRUE.equals(a.getProcesado());
        String sufijo   = procesada ? "" : " (plan.)";
        if (puedeEditar && !procesada) {
            return "    <a href=\"" + urlAusencia(a.getId(), empleadoId, dia, a)
                    + "\" class=\"link-planif\" onclick=\"event.stopPropagation();\">" + esc(label) + sufijo + " &#9998;</a>\n";
        }
        return "    <span class=\"" + (procesada ? "ausencia-label" : "planif-label") + "\">"
                + esc(label) + sufijo + "</span>\n";
    }

    private String urlFichaje(Long fichajeId, Long empleadoId, LocalDate dia, Fichaje f) {
        StringBuilder url = new StringBuilder("staffflow://fichaje/").append(fichajeId);
        url.append("?variante=FICHAJE&empleadoId=").append(empleadoId)
           .append("&fecha=").append(dia)
           .append("&tipo=").append(f.getTipo().name());
        if (f.getHoraEntrada() != null) url.append("&horaEntrada=").append(f.getHoraEntrada().format(FMT_HORA));
        if (f.getHoraSalida()  != null) url.append("&horaSalida=").append(f.getHoraSalida().format(FMT_HORA));
        return url.toString();
    }

    private String urlPausa(Long pausaId, Long empleadoId, LocalDate dia, Pausa p) {
        StringBuilder url = new StringBuilder("staffflow://pausa/").append(pausaId);
        url.append("?variante=PAUSA&empleadoId=").append(empleadoId)
           .append("&fecha=").append(dia);
        if (p.getTipoPausa()  != null) url.append("&tipoPausa=").append(p.getTipoPausa().name());
        if (p.getHoraInicio() != null) url.append("&horaInicio=").append(p.getHoraInicio().format(FMT_HORA));
        if (p.getHoraFin()    != null) url.append("&horaFin=").append(p.getHoraFin().format(FMT_HORA));
        return url.toString();
    }

    private String urlAusencia(Long ausenciaId, Long empleadoId, LocalDate dia, PlanificacionAusencia a) {
        return "staffflow://ausencia/" + ausenciaId
                + "?empleadoId=" + empleadoId
                + "&fecha=" + dia
                + "&tipoAusencia=" + a.getTipoAusencia().name()
                + "&procesado=" + Boolean.TRUE.equals(a.getProcesado())
                + (empleadoId == null ? "&esFestivo=true" : "");
    }

    // =========================================================================
    // E60 — GET /api/v1/informes/ausencias
    // Resumen de ausencias globales de todos los empleados activos
    // =========================================================================

    /**
     * Genera el HTML del resumen de ausencias de todos los empleados activos.
     *
     * <p>Tabla: filas = empleados activos, columnas = días (desde–hasta).
     * Cada celda muestra la ausencia del día (ejecutada o planificada).
     * No incluye jornadas NORMAL ni DIA_LIBRE.</p>
     *
     * @param desde    primer día del rango
     * @param hasta    último día del rango
     * @param username username del usuario autenticado
     * @return HTML del resumen de ausencias
     */
    @Transactional(readOnly = true)
    public String informeAusenciasGlobal(LocalDate desde, LocalDate hasta, String username) {

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario no encontrado: " + username));
        Rol rol = usuario.getRol();
        LocalDate hoy = LocalDate.now();

        List<Empleado> empleados = empleadoRepository.findByActivo(true).stream()
                .sorted(Comparator.comparing(this::nombreCompleto))
                .collect(Collectors.toList());

        List<LocalDate> dias = new ArrayList<>();
        LocalDate cursor = desde;
        while (!cursor.isAfter(hasta)) { dias.add(cursor); cursor = cursor.plusDays(1); }

        // Fichajes de tipo ausencia: excluye NORMAL y DIA_LIBRE
        Map<Long, Map<LocalDate, Fichaje>> fichajesAusenciaMap = fichajeRepository
                .findByFiltros(null, desde, hasta, null).stream()
                .filter(f -> f.getTipo() != TipoFichaje.NORMAL
                          && f.getTipo() != TipoFichaje.DIA_LIBRE)
                .collect(Collectors.groupingBy(
                        f -> f.getEmpleado().getId(),
                        Collectors.toMap(Fichaje::getFecha, f -> f)));

        // Planificación de ausencias: individuales por empleado + festivos globales (empleado=null)
        List<PlanificacionAusencia> todasAusencias = planificacionRepository
                .findByFiltros(null, desde, hasta, null);

        Map<Long, Map<LocalDate, PlanificacionAusencia>> ausenciasMap = todasAusencias.stream()
                .filter(a -> a.getEmpleado() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getEmpleado().getId(),
                        Collectors.toMap(PlanificacionAusencia::getFecha, a -> a,
                                (a1, a2) -> a1)));

        Map<LocalDate, PlanificacionAusencia> festivosGlobales = todasAusencias.stream()
                .filter(a -> a.getEmpleado() == null)
                .collect(Collectors.toMap(PlanificacionAusencia::getFecha, a -> a,
                        (a1, a2) -> a1));

        return generarHtmlAusenciasGlobal(empleados, dias, fichajesAusenciaMap, ausenciasMap,
                festivosGlobales, rol, hoy, desde, hasta);
    }

    private String generarHtmlAusenciasGlobal(
            List<Empleado> empleados, List<LocalDate> dias,
            Map<Long, Map<LocalDate, Fichaje>> fichajesMap,
            Map<Long, Map<LocalDate, PlanificacionAusencia>> ausenciasMap,
            Map<LocalDate, PlanificacionAusencia> festivosGlobales,
            Rol rol, LocalDate hoy, LocalDate desde, LocalDate hasta) {

        DateTimeFormatter fmtDia = DateTimeFormatter.ofPattern("dd/MM");

        StringBuilder sb = new StringBuilder();
        sb.append(htmlCabeceraInforme());
        sb.append("<body>\n<style>\n");
        sb.append(".tabla-semana{border-collapse:collapse;min-width:100%;font-size:12px;}\n");
        sb.append(".tabla-semana th{background:#2c3e6b;color:#fff;padding:7px 8px;text-align:center;white-space:nowrap;border:1px solid #1a2d56;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.4px;}\n");
        sb.append(".tabla-semana td{padding:6px 8px;border:1px solid #e8eaf0;vertical-align:top;min-width:90px;}\n");
        sb.append(".tabla-semana tbody tr:nth-child(even) td{background:#f7f8fc;}\n");
        sb.append("@media(hover:hover){.tabla-semana tbody tr:hover td{background:#f0f2f8;}}\n");
        sb.append(".th-hoy{background:#1a6b3c!important;border-color:#0e4727!important;}\n");
        sb.append(".td-hoy{background:#f0faf4!important;}\n");
        sb.append(".td-futuro{color:#777;}\n");
        sb.append(".col-empleado{min-width:140px;font-weight:500;text-align:left!important;white-space:nowrap;cursor:pointer;}\n");
        sb.append(".col-empleado-sel{background:#dbeafe!important;border-left:3px solid #4285f4!important;}\n");
        sb.append(".link-ausencia{color:#534AB7;text-decoration:none;}\n");
        sb.append(".link-planif{color:#BA7517;text-decoration:none;}\n");
        sb.append(".link-ausencia:hover,.link-planif:hover{text-decoration:underline;}\n");
        sb.append(".ausencia-label{color:#534AB7;}\n");
        sb.append(".planif-label{color:#BA7517;}\n");
        sb.append(".vacio{color:#ccc;}\n");
        sb.append(".libre{color:#aaa;font-size:11px;font-style:italic;}\n");
        sb.append(".td-finde{background:#f4f4f6!important;}\n");
        sb.append(".sin-datos{text-align:center;color:#9aa5be;font-style:italic;padding:20px;}\n");
        sb.append(".tabla-semana td,.tabla-semana tr{user-select:none;-webkit-user-select:none;}\n");
        sb.append(".seleccionable{cursor:pointer;-webkit-tap-highlight-color:transparent;touch-action:manipulation;}\n");
        sb.append(".td-seleccionada{background:#c5d8fd!important;outline:2px solid #4285f4;outline-offset:-2px;}\n");
        sb.append("</style>\n");
        sb.append("<div class=\"page\">\n");
        sb.append("<div class=\"header\">\n  <div class=\"titulo\">Ausencias &mdash; ")
          .append(desde.format(FMT_FECHA)).append(" &ndash; ").append(hasta.format(FMT_FECHA))
          .append("</div>\n</div>\n");

        sb.append("<div class=\"tabla-scroll\">\n<table class=\"tabla-semana\">\n<thead>\n<tr>\n");
        sb.append("  <th class=\"col-empleado\">Empleado</th>\n");
        for (LocalDate dia : dias) {
            String clsTh = dia.isEqual(hoy) ? " th-hoy" : "";
            sb.append("  <th class=\"th-dia").append(clsTh).append("\">")
              .append(DIAS_ES.getOrDefault(dia.getDayOfWeek(), ""))
              .append("<br>").append(dia.format(fmtDia)).append("</th>\n");
        }
        sb.append("</tr>\n</thead>\n<tbody>\n");

        for (Empleado emp : empleados) {
            sb.append("<tr>\n  <td class=\"col-empleado\" data-emp-id=\"").append(emp.getId()).append("\">")
              .append(esc(nombreCompleto(emp)))
              .append("</td>\n");

            Map<LocalDate, Fichaje> fichajes = fichajesMap.getOrDefault(emp.getId(), Map.of());
            Map<LocalDate, PlanificacionAusencia> ausencias = ausenciasMap.getOrDefault(emp.getId(), Map.of());

            for (LocalDate dia : dias) {
                boolean esPasado = dia.isBefore(hoy);
                boolean esFuturo = dia.isAfter(hoy);
                boolean esFinde  = dia.getDayOfWeek() == DayOfWeek.SATURDAY
                                || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
                String clsTd = dia.isEqual(hoy) ? " td-hoy"
                        : (esFuturo ? " td-futuro" : (esFinde ? " td-finde" : ""));
                boolean puedeEditarFichaje  = !esFuturo && rol == Rol.ADMIN;
                boolean puedeEditarAusencia = rol == Rol.ADMIN || !esPasado;

                boolean esSeleccionable = !esPasado;
                sb.append("  <td class=\"celda").append(clsTd);
                if (esSeleccionable) sb.append(" seleccionable");
                sb.append("\"");
                if (esSeleccionable) {
                    sb.append(" data-emp=\"").append(emp.getId()).append("\"");
                    sb.append(" data-fecha=\"").append(dia).append("\"");
                }
                sb.append(">\n");

                Fichaje fichaje = fichajes.get(dia);
                PlanificacionAusencia ausencia = ausencias.get(dia);
                PlanificacionAusencia festivoGlobal = festivosGlobales.get(dia);

                if (fichaje != null) {
                    String label = TIPO_LEGIBLE.getOrDefault(fichaje.getTipo().name(), fichaje.getTipo().name());
                    if (puedeEditarFichaje) {
                        sb.append("    <a href=\"")
                          .append(urlFichaje(fichaje.getId(), emp.getId(), dia, fichaje))
                          .append("\" class=\"link-ausencia\" onclick=\"event.stopPropagation();\">").append(esc(label)).append(" &#9998;</a>\n");
                    } else {
                        sb.append("    <span class=\"ausencia-label\">").append(esc(label)).append("</span>\n");
                    }
                } else if (ausencia != null) {
                    sb.append(celdaAusenciaPlanificada(ausencia, emp.getId(), dia, puedeEditarAusencia));
                } else if (festivoGlobal != null) {
                    sb.append(celdaAusenciaPlanificada(festivoGlobal, null, dia, puedeEditarAusencia));
                } else if (esFinde) {
                    sb.append("    <span class=\"libre\">D&iacute;a libre</span>\n");
                } else {
                    sb.append("    <span class=\"vacio\">&mdash;</span>\n");
                }
                sb.append("  </td>\n");
            }
            sb.append("</tr>\n");
        }

        if (empleados.isEmpty()) {
            sb.append("<tr><td colspan=\"").append(dias.size() + 1)
              .append("\" class=\"sin-datos\">No hay empleados activos</td></tr>\n");
        }

        sb.append("</tbody>\n</table>\n</div>\n");
        sb.append("<div class=\"pie\">StaffFlow &mdash; Generado el ")
          .append(LocalDate.now().format(FMT_FECHA)).append("</div>\n");
        sb.append("</div>\n");
        sb.append("<script>\n");
        sb.append("var _sel={empId:null,fechas:[]};\n");
        sb.append("function toggleCelda(td,ev){\n");
        sb.append("  if(ev){ev.preventDefault();}\n");
        sb.append("  var e=td.dataset.emp,f=td.dataset.fecha;\n");
        sb.append("  if(!e||!f) return;\n");
        sb.append("  if(_sel.empId&&_sel.empId!==e){\n");
        sb.append("    document.querySelectorAll('.td-seleccionada').forEach(function(el){el.classList.remove('td-seleccionada');});\n");
        sb.append("    _sel={empId:null,fechas:[]};\n");
        sb.append("  }\n");
        sb.append("  _sel.empId=e;\n");
        sb.append("  var i=_sel.fechas.indexOf(f);\n");
        sb.append("  if(i>=0){_sel.fechas.splice(i,1);td.classList.remove('td-seleccionada');}\n");
        sb.append("  else{_sel.fechas.push(f);td.classList.add('td-seleccionada');}\n");
        sb.append("  if(_sel.fechas.length===0) _sel.empId=null;\n");
        sb.append("  var s=_sel.fechas.slice().sort();\n");
        sb.append("  if(s.length===0){location.href='staffflow://seleccion/vacia';return;}\n");
        sb.append("  var cons=true;\n");
        sb.append("  for(var j=1;j<s.length;j++){if((new Date(s[j])-new Date(s[j-1]))/86400000!==1){cons=false;break;}}\n");
        sb.append("  location.href='staffflow://seleccion/'+_sel.empId+'/'+s[0]+'/'+s[s.length-1]+'/'+cons;\n");
        sb.append("}\n");
        sb.append("document.querySelectorAll('.seleccionable').forEach(function(td){\n");
        sb.append("  td.addEventListener('touchstart',function(e){if(e.target.closest('a'))return;e.preventDefault();},{passive:false});\n");
        sb.append("  td.addEventListener('touchend',function(e){if(e.target.closest('a'))return;e.preventDefault();toggleCelda(td,e);});\n");
        sb.append("});\n");
        sb.append("document.querySelectorAll('td.col-empleado[data-emp-id]').forEach(function(td){\n");
        sb.append("  td.addEventListener('click',function(e){\n");
        sb.append("    if(e.target.closest('a')) return;\n");
        sb.append("    document.querySelectorAll('td.col-empleado-sel').forEach(function(el){el.classList.remove('col-empleado-sel');});\n");
        sb.append("    td.classList.add('col-empleado-sel');\n");
        sb.append("    location.href='staffflow://empleado/'+td.dataset.empId+'/").append(desde).append("';\n");
        sb.append("  });\n");
        sb.append("});\n");
        sb.append("</script>\n");
        sb.append("</body>\n</html>");
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
                    table.detalle.saldos th.bloque-vac,
                    table.detalle.saldos th.bloque-ap,
                    table.detalle.saldos th.bloque-dias,
                    table.detalle.saldos th.bloque-horas,
                    table.detalle.saldos th.bloque-ctrl { background: #2c3e6b; border: 1px solid #1a2d56; }
                    table.detalle.saldos thead tr:last-child th { background: #6b8bc4; border-color: #5a7ab8; }
                    table.detalle.saldos thead tr:first-child th { border-bottom: 1px solid #6b8bc4 !important; }
                    table.detalle.saldos thead tr:last-child th  { border-top: none !important; }
                    table.detalle.saldos td.col-empleado {
                      text-align: left;
                      font-weight: 500;
                      min-width: 160px;
                      position: sticky;
                      left: 0;
                      background: #ffffff;
                      z-index: 3;
                    }
                    table.detalle.saldos th.col-empleado {
                      text-align: center;
                      vertical-align: middle;
                      font-weight: 600;
                      min-width: 160px;
                      position: sticky;
                      left: 0;
                      background: #2c3e6b;
                      z-index: 5;
                    }
                    table.detalle.saldos tbody tr:nth-child(even) td.col-empleado { background: #f7f8fc; }
                    table.detalle.saldos tbody tr:hover td.col-empleado { background: #f0f2f8; }
                    table.detalle.saldos thead {
                      position: sticky;
                      top: 0;
                      z-index: 4;
                    }
                    table.detalle.saldos thead th { position: static; }
                    table.detalle.saldos thead th.col-empleado { position: sticky; left: 0; z-index: 5; }
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
                    .estado-ejecutada { color: #3B6D11; font-weight: 500; }
                    .estado-planificada { color: #534AB7; font-weight: 500; }
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

    /** Formatea una LocalDateTime a HH:mm:ss o devuelve "—" si es null. */
    private String celdaHora(LocalDateTime ldt) {
        return ldt != null ? ldt.format(FMT_HORA_SEG) : "—";
    }

    /** Formatea hora de pausa con asterisco si es manual. */
    private String celdaPausa(LocalDateTime ldt, boolean esManual) {
        if (ldt == null) return "—";
        return esManual ? ldt.format(FMT_HORA_SEG) + "*" : ldt.format(FMT_HORA_SEG);
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

    /** Fila del informe de ausencias (merge de planificacion_ausencias + fichajes). */
    private static class AusenciaInformeRow {
        LocalDate fecha;
        String    tipo;
        String    estado;        // "Ejecutada" | "Planificada"
        String    observaciones;

        AusenciaInformeRow(LocalDate fecha, String tipo, String estado, String observaciones) {
            this.fecha         = fecha;
            this.tipo          = tipo;
            this.estado        = estado;
            this.observaciones = observaciones;
        }
    }

    /** Acumula los totales del resumen del informe. */
    private static class ResumenInforme {
        int diasTrabajados              = 0;
        int horasEfectivasMinutos       = 0;
        int diasLibres                  = 0;
        int horasPermisoRetribuidoMinutos = 0;
        Map<String, Integer> ausenciasPorTipo = new LinkedHashMap<>();
    }

    // =========================================================================
    // Helpers de saldo semanal
    // =========================================================================

    /**
     * Calcula el saldo acumulado (en horas, BigDecimal) por empleado
     * desde el 01/01 del año hasta {@code hasta} (inclusive).
     *
     * <p>Optimización: usa {@link SaldoAnual#getSaldoHoras()} como checkpoint.
     * El proceso nocturno (23:55) deja SaldoAnual actualizado hasta ayer, así
     * que solo se escanea el delta entre ayer y {@code hasta}:
     * <ul>
     *   <li>hasta == ayer → SaldoAnual directo, sin scan adicional</li>
     *   <li>hasta &lt; ayer → SaldoAnual − fichajes(hasta+1..ayer)</li>
     *   <li>hasta &gt; ayer → SaldoAnual + fichajes(ayer+1..hasta)</li>
     * </ul>
     * Si algún empleado no tiene SaldoAnual se hace fallback al scan completo.</p>
     */
    private Map<Long, BigDecimal> calcularSaldoHastaFecha(
            List<Empleado> empleados, LocalDate desde, LocalDate hasta) {

        Map<Long, BigDecimal> result = new HashMap<>();
        empleados.forEach(emp -> result.put(emp.getId(), BigDecimal.ZERO));

        if (hasta.isBefore(desde)) return result;

        int anio = desde.getYear();
        LocalDate ayer = LocalDate.now().minusDays(1);

        // Intentar usar SaldoAnual como checkpoint
        Map<Long, BigDecimal> baseMap = new HashMap<>();
        for (Empleado emp : empleados) {
            saldoRepository.findByEmpleadoIdAndAnio(emp.getId(), anio)
                    .ifPresentOrElse(
                            sa -> baseMap.put(emp.getId(), sa.getSaldoHoras()),
                            ()  -> baseMap.put(emp.getId(), null));
        }

        boolean todosConSaldo = baseMap.values().stream().noneMatch(java.util.Objects::isNull);

        if (todosConSaldo && !ayer.isBefore(LocalDate.of(anio, 1, 1))) {
            // Checkpoint disponible: partir de SaldoAnual (01/01..ayer)
            empleados.forEach(emp -> result.put(emp.getId(), baseMap.get(emp.getId())));

            if (hasta.isBefore(ayer)) {
                // Restar fichajes de (hasta+1)..ayer
                acumularFichajes(result, hasta.plusDays(1), ayer, -1);
            } else if (hasta.isAfter(ayer)) {
                // Sumar fichajes de (ayer+1)..hasta
                acumularFichajes(result, ayer.plusDays(1), hasta, +1);
            }
            // hasta == ayer → result ya está correcto
        } else {
            // Fallback: scan completo desde el inicio del período
            acumularFichajes(result, desde, hasta, +1);
        }

        return result;
    }

    /**
     * Acumula (o resta, según {@code signo}) las contribuciones de los fichajes
     * del rango {@code desde..hasta} sobre el mapa {@code result}.
     * {@code signo} debe ser +1 para sumar o -1 para restar.
     */
    private void acumularFichajes(Map<Long, BigDecimal> result,
                                   LocalDate desde, LocalDate hasta, int signo) {
        fichajeRepository.findByFiltros(null, desde, hasta, null).forEach(f -> {
            Long empId = f.getEmpleado().getId();
            if (!result.containsKey(empId)) return;
            int jornadaDiaria = f.getEmpleado().getJornadaDiariaMinutos();
            int efectivo = f.getJornadaEfectivaMinutos() != null
                    ? f.getJornadaEfectivaMinutos() : 0;
            int contribMin = signo * saldoContribMinutos(f.getTipo(), efectivo, jornadaDiaria);
            result.merge(empId,
                    BigDecimal.valueOf(contribMin).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP),
                    BigDecimal::add);
        });
    }

    /**
     * Contribución de un fichaje al saldo de horas (en minutos):
     * <ul>
     *   <li>NORMAL: efectivo − jornada diaria teórica (puede ser negativo)</li>
     *   <li>BAJA_MEDICA / PERMISO_RETRIBUIDO: +jornada diaria teórica</li>
     *   <li>resto (VACACIONES, ASUNTO_PROPIO, FESTIVOS, DIA_LIBRE*, AUSENCIA_*): 0</li>
     * </ul>
     */
    private int saldoContribMinutos(TipoFichaje tipo, int efectivoMinutos, int jornadaDiariaMinutos) {
        return switch (tipo) {
            case NORMAL                                    -> efectivoMinutos - jornadaDiariaMinutos;
            case DIA_LIBRE_COMPENSATORIO,
                 AUSENCIA_INJUSTIFICADA                    -> -jornadaDiariaMinutos;
            default                                        -> 0;
        };
    }
}
