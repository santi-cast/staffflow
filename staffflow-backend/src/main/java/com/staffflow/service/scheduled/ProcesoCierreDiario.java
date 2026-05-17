package com.staffflow.service.scheduled;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.PlanificacionAusencia;
import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.TipoAusencia;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.exception.NotFoundException;
import com.staffflow.service.SaldoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * Proceso nocturno automatico de cierre de jornada.
 *
 * <p>Se ejecuta cada dia a las 23:55 mediante @Scheduled. Es el unico proceso
 * automatico del sistema y su fallo deja el dia entero mal registrado antes
 * de que lleguen los empleados al dia siguiente.</p>
 *
 * <p>Ejecuta tres tareas en secuencia dentro de una sola transaccion:</p>
 *
 * <ul>
 *   <li>Tarea A: cierra la jornada del dia. Los empleados activos sin fichaje
 *       a las 23:55 reciben: AUSENCIA_INJUSTIFICADA si es dia laborable
 *       (lunes a viernes), o DIA_LIBRE si es sabado o domingo. Garantiza que
 *       todos los dias quedan registrados en BD sin excepcion.</li>
 *   <li>Tarea B: materializa planificaciones pendientes. Convierte las
 *       ausencias planificadas (procesado=false) con fecha <= manana en
 *       fichajes del tipo correspondiente y marca procesado=true.
 *       Usar fecha <= manana (plusDays(1)) permite procesar festivos
 *       globales la noche anterior a que ocurran. Ademas, si el dia
 *       siguiente es sabado o domingo, crea DIA_LIBRE para todos los
 *       empleados activos sin fichaje ese dia.</li>
 *   <li>Tarea C: recalcula saldos anuales. Llama a
 *       SaldoService.recalcularParaProceso() para cada empleado activo
 *       una vez que Tarea A y Tarea B han completado todos los fichajes
 *       del dia.</li>
 * </ul></p>
 *
 * <p>La operacion completa es idempotente: si se ejecuta dos veces el
 * mismo dia el segundo ciclo no genera duplicados porque verifica
 * existencia de fichaje antes de crear, y procesado=true evita
 * reprocesar planificaciones ya materializadas.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.service.SaldoService#recalcularParaProceso(Long, int)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesoCierreDiario {

    private final PlanificacionAusenciaRepository ausenciaRepository;
    private final FichajeRepository               fichajeRepository;
    private final EmpleadoRepository              empleadoRepository;
    private final UsuarioRepository               usuarioRepository;
    private final SaldoService                    saldoService;

    /**
     * Proceso nocturno que cierra la jornada del dia (23:55).
     *
     * <p>Las tres tareas se ejecutan en orden dentro de una unica transaccion:
     * si cualquiera falla, ninguna modificacion llega a BD. El log registra
     * el inicio y fin de cada tarea para facilitar la depuracion si el
     * proceso se interrumpe a mitad.</p>
     *
     * <p>El usuario terminal_service (username fijo) debe existir en BD tanto
     * en dev como en prod. Es el autor de los fichajes generados automaticamente.
     * Si no existe, el proceso falla con NotFoundException y ningun fichaje
     * automatico se crea ese dia. La transaccion hace rollback completo.</p>
     */
    @Scheduled(cron = "0 55 23 * * *")
    @Transactional
    public void ejecutar() {
        LocalDate hoy = LocalDate.now();
        log.info("ProcesoCierreDiario iniciado para la fecha {}", hoy);

        // Cargar el usuario sistema una sola vez para usarlo en Tarea A y Tarea B.
        // terminal_service es el autor de todos los fichajes automaticos.
        // Se busca por username (no por id) para no depender del autoincremental
        // de BD, que puede diferir entre dev y prod.
        Usuario usuarioSistema = usuarioRepository.findByUsername("terminal_service")
                .orElseThrow(() -> new NotFoundException(
                        "Usuario 'terminal_service' no encontrado en BD. "
                        + "El proceso nocturno no puede ejecutarse sin este usuario."));

        // Cargar empleados activos una sola vez — se reutiliza en las tres tareas
        List<Empleado> empleadosActivos = empleadoRepository.findByActivo(true);

        // -------------------------------------------------------------------
        // TAREA A — Cierre ausencias injustificadas
        // -------------------------------------------------------------------
        // Empleados activos sin fichaje a las 23:55 reciben:
        //   - AUSENCIA_INJUSTIFICADA si es dia laborable (lunes a viernes).
        //   - DIA_LIBRE si es sabado o domingo.
        // Distinguir el tipo segun el dia de la semana evita que un fin de
        // semana sin fichaje quede registrado como ausencia injustificada,
        // lo que distorsionaria los saldos y el informe de fichajes.
        // Registrar explicitamente los dias de fin de semana garantiza ademas
        // que aparecen en BD sin depender de la logica de InformeService para
        // deducirlos, lo que evita que cualquier dia desaparezca de BD sin rastro.
        // -------------------------------------------------------------------
        log.info("Tarea A iniciada: cierre ausencias injustificadas para {}", hoy);
        int fichajesInjustificados = 0;

        // Determinar el tipo de fichaje segun el dia de la semana.
        // Los fines de semana son DIA_LIBRE; los dias laborables sin fichaje
        // son AUSENCIA_INJUSTIFICADA.
        boolean esFinDeSemanaHoy = hoy.getDayOfWeek() == DayOfWeek.SATURDAY
                || hoy.getDayOfWeek() == DayOfWeek.SUNDAY;
        TipoFichaje tipoTareaA = esFinDeSemanaHoy
                ? TipoFichaje.DIA_LIBRE
                : TipoFichaje.AUSENCIA_INJUSTIFICADA;

        for (Empleado empleado : empleadosActivos) {
            boolean tieneFichaje = fichajeRepository
                    .findByEmpleadoIdAndFecha(empleado.getId(), hoy)
                    .isPresent();

            if (!tieneFichaje) {
                Fichaje fichaje = new Fichaje();
                fichaje.setEmpleado(empleado);
                fichaje.setFecha(hoy);
                fichaje.setTipo(tipoTareaA);
                fichaje.setHoraEntrada(null);
                fichaje.setHoraSalida(null);
                fichaje.setJornadaEfectivaMinutos(0);
                fichaje.setTotalPausasMinutos(0);
                fichaje.setObservaciones(
                        "Generado automaticamente por ProcesoCierreDiario");
                fichaje.setUsuario(usuarioSistema);

                fichajeRepository.save(fichaje);
                fichajesInjustificados++;
                log.debug("{} creada para empleado {} en {}",
                        tipoTareaA, empleado.getId(), hoy);
            }
        }
        log.info("Tarea A completada: {} fichajes {} creados",
                fichajesInjustificados, tipoTareaA);

        // -------------------------------------------------------------------
        // TAREA B — Procesado planificaciones pendientes
        // -------------------------------------------------------------------
        // Convierte ausencias planificadas (procesado=false) con fecha <= manana
        // en fichajes del tipo correspondiente. Usar plusDays(1) permite procesar
        // los festivos globales la noche anterior, de forma que el dia del festivo
        // todos los empleados ya tienen fichaje y Tarea A no genera injustificadas.
        // -------------------------------------------------------------------
        log.info("Tarea B iniciada: procesado planificaciones pendientes hasta {}",
                hoy.plusDays(1));
        int planificacionesProcesadas = 0;

        List<PlanificacionAusencia> pendientes = ausenciaRepository
                .findPendientesByFechaLessThanEqual(hoy.plusDays(1));

        for (PlanificacionAusencia planificacion : pendientes) {

            TipoFichaje tipoFichaje = mapearTipoAusenciaATipoFichaje(
                    planificacion.getTipoAusencia());

            if (planificacion.getEmpleado() == null) {
                // Festivo global (RF-26): empleadoId = null en BD.
                // Crear fichaje para cada empleado activo que no tenga ya uno.
                for (Empleado empleado : empleadosActivos) {
                    crearFichajeAusenciaSiNoExiste(
                            empleado, planificacion.getFecha(),
                            tipoFichaje, usuarioSistema);
                }
            } else {
                // Ausencia individual: crear fichaje solo para ese empleado.
                crearFichajeAusenciaSiNoExiste(
                        planificacion.getEmpleado(), planificacion.getFecha(),
                        tipoFichaje, usuarioSistema);
            }

            // Marcar como procesada para que el proceso no la toque de nuevo
            planificacion.setProcesado(true);
            ausenciaRepository.save(planificacion);
            planificacionesProcesadas++;
            log.debug("Planificacion {} procesada: empleado={}, fecha={}, tipo={}",
                    planificacion.getId(),
                    planificacion.getEmpleado() != null
                            ? planificacion.getEmpleado().getId() : "GLOBAL",
                    planificacion.getFecha(),
                    tipoFichaje);
        }
        log.info("Tarea B completada: {} planificaciones procesadas",
                planificacionesProcesadas);

        // Crear DIA_LIBRE para el dia siguiente si es sabado o domingo.
        // El descanso semanal obligatorio se registra la noche anterior para
        // que el empleado no pueda fichar ese dia desde el terminal.
        // Si la empresa trabaja ese dia, el ENCARGADO planifica el dia como
        // NORMAL antes del cierre y el proceso no lo toca (ya tiene fichaje).
        LocalDate manana = hoy.plusDays(1);
        DayOfWeek diaSemanaManana = manana.getDayOfWeek();
        if (diaSemanaManana == DayOfWeek.SATURDAY || diaSemanaManana == DayOfWeek.SUNDAY) {
            log.info("Creando DIA_LIBRE para {} ({}) — descanso semanal", manana, diaSemanaManana);
            int diasLibresCreados = 0;
            for (Empleado empleado : empleadosActivos) {
                boolean tieneManana = fichajeRepository
                        .findByEmpleadoIdAndFecha(empleado.getId(), manana)
                        .isPresent();
                if (!tieneManana) {
                    Fichaje diaLibre = new Fichaje();
                    diaLibre.setEmpleado(empleado);
                    diaLibre.setFecha(manana);
                    diaLibre.setTipo(TipoFichaje.DIA_LIBRE);
                    diaLibre.setHoraEntrada(null);
                    diaLibre.setHoraSalida(null);
                    diaLibre.setJornadaEfectivaMinutos(0);
                    diaLibre.setTotalPausasMinutos(0);
                    diaLibre.setObservaciones(
                            "Dia libre generado automaticamente por ProcesoCierreDiario ("
                            + diaSemanaManana + ")");
                    diaLibre.setUsuario(usuarioSistema);
                    fichajeRepository.save(diaLibre);
                    diasLibresCreados++;
                }
            }
            log.info("DIA_LIBRE creado para {} empleados el {}", diasLibresCreados, manana);
        }

        // -------------------------------------------------------------------
        // TAREA C — Actualizacion saldos anuales
        // -------------------------------------------------------------------
        // Se ejecuta siempre despues de A y B para que el recalculo incluya
        // todos los fichajes del dia recien cerrado. Llama a
        // SaldoService.recalcularParaProceso() que hace el recalculo completo
        // desde cero sin construir DTO.
        // -------------------------------------------------------------------
        log.info("Tarea C iniciada: recalculo saldos anuales para {} empleados activos",
                empleadosActivos.size());
        int anioActual = Year.now().getValue();
        int saldosRecalculados = 0;

        for (Empleado empleado : empleadosActivos) {
            saldoService.recalcularParaProceso(empleado.getId(), anioActual);
            saldosRecalculados++;
        }
        log.info("Tarea C completada: {} saldos recalculados", saldosRecalculados);

        log.info("ProcesoCierreDiario finalizado para {}. "
                + "Fichajes automaticos={}, Planificaciones={}, Saldos={}",
                hoy, fichajesInjustificados, planificacionesProcesadas, saldosRecalculados);
    }

    // -------------------------------------------------------------------
    // Metodos auxiliares privados
    // -------------------------------------------------------------------

    /**
     * Crea un fichaje de ausencia para un empleado y fecha si no existe ya.
     *
     * <p>La verificacion de existencia previa es necesaria porque:
     *   - En Tarea A puede que ya se haya creado un fichaje automatico
     *     para ese empleado ese dia antes de procesar la planificacion.
     *   - En Tarea B un festivo global puede coincidir con una ausencia
     *     individual planificada para el mismo empleado.
     * En ambos casos se respeta el fichaje existente sin sobreescribirlo.</p>
     *
     * @param empleado       empleado para el que crear el fichaje
     * @param fecha          fecha del fichaje
     * @param tipoFichaje    tipo de fichaje a crear
     * @param usuarioSistema usuario sistema para el campo de auditoria
     */
    private void crearFichajeAusenciaSiNoExiste(Empleado empleado, LocalDate fecha,
                                                 TipoFichaje tipoFichaje,
                                                 Usuario usuarioSistema) {
        boolean yaExiste = fichajeRepository
                .findByEmpleadoIdAndFecha(empleado.getId(), fecha)
                .isPresent();

        if (!yaExiste) {
            Fichaje fichaje = new Fichaje();
            fichaje.setEmpleado(empleado);
            fichaje.setFecha(fecha);
            fichaje.setTipo(tipoFichaje);
            fichaje.setHoraEntrada(null);           // ausencias sin horas de entrada/salida
            fichaje.setHoraSalida(null);
            fichaje.setJornadaEfectivaMinutos(0);   // no hay jornada efectiva en ausencia
            fichaje.setTotalPausasMinutos(0);
            fichaje.setObservaciones(
                    "Generado automaticamente por ProcesoCierreDiario");
            fichaje.setUsuario(usuarioSistema);

            fichajeRepository.save(fichaje);
            log.debug("Fichaje {} creado para empleado {} en {}",
                    tipoFichaje, empleado.getId(), fecha);
        }
    }

    /**
     * Mapea un TipoAusencia al TipoFichaje correspondiente.
     *
     * <p>El mapeo es directo por nombre: todos los valores de TipoAusencia
     * tienen un TipoFichaje con el mismo nombre. Esta correspondencia fue
     * verificada en el diseño del sistema y los enums lo garantizan.
     * Se usa valueOf para que una discrepancia futura entre enums falle
     * en tiempo de ejecucion con mensaje claro en lugar de producir datos
     * incorrectos silenciosamente.</p>
     *
     * <p>Mapeo completo:
     *   FESTIVO_NACIONAL        → TipoFichaje.FESTIVO_NACIONAL
     *   FESTIVO_LOCAL           → TipoFichaje.FESTIVO_LOCAL
     *   VACACIONES              → TipoFichaje.VACACIONES
     *   ASUNTO_PROPIO           → TipoFichaje.ASUNTO_PROPIO
     *   PERMISO_RETRIBUIDO      → TipoFichaje.PERMISO_RETRIBUIDO
     *   DIA_LIBRE_COMPENSATORIO → TipoFichaje.DIA_LIBRE_COMPENSATORIO
     *   DIA_LIBRE               → TipoFichaje.DIA_LIBRE
     * </p>
     *
     * @param tipoAusencia tipo de ausencia planificada
     * @return tipo de fichaje equivalente
     * @throws IllegalArgumentException si el nombre no existe en TipoFichaje
     */
    private TipoFichaje mapearTipoAusenciaATipoFichaje(TipoAusencia tipoAusencia) {
        return TipoFichaje.valueOf(tipoAusencia.name());
    }
}
