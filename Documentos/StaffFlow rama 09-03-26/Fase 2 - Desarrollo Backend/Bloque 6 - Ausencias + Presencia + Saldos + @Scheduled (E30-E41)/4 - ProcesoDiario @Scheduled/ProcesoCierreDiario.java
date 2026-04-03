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
import com.staffflow.service.SaldoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * Proceso nocturno automatico de cierre de jornada.
 *
 * <p>Se ejecuta cada dia a las 23:55 mediante @Scheduled (decision n8,
 * cron definido en sesion 16). Es el unico proceso que permanece
 * automatico: su fallo deja el dia entero mal registrado antes de que
 * lleguen los empleados al dia siguiente.</p>
 *
 * <p>Ejecuta tres tareas en secuencia dentro de una sola transaccion:
 * <ul>
 *   <li>Tarea A: cierra ausencias injustificadas. Los empleados activos
 *       sin fichaje a las 23:55 reciben un fichaje AUSENCIA_INJUSTIFICADA
 *       automatico. Evita que los dias sin actividad desaparezcan de BD
 *       sin dejar rastro (problema detectado en sesion 16).</li>
 *   <li>Tarea B: materializa planificaciones pendientes. Convierte las
 *       ausencias planificadas (procesado=false) con fecha <= manana en
 *       fichajes del tipo correspondiente y marca procesado=true.
 *       Usar fecha <= manana (plusDays(1)) permite procesar festivos
 *       globales la noche anterior a que ocurran.</li>
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
 * <p>CierreDiario y CierreAnual son botones manuales (E40, E41)
 * gestionados por SaldoService. No son @Scheduled (decisiones n11
 * y n12).</p>
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
     * Si no existe, el proceso falla con IllegalStateException y ningun fichaje
     * automatico se crea ese dia.</p>
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
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario 'terminal_service' no encontrado en BD. "
                        + "El proceso nocturno no puede ejecutarse sin este usuario."));

        // Cargar empleados activos una sola vez — se reutiliza en las tres tareas
        List<Empleado> empleadosActivos = empleadoRepository.findByActivo(true);

        // -------------------------------------------------------------------
        // TAREA A — Cierre ausencias injustificadas
        // -------------------------------------------------------------------
        // Empleados activos sin fichaje a las 23:55 reciben AUSENCIA_INJUSTIFICADA.
        // Esto evita que el dia desaparezca de BD sin dejar rastro: E35/E36 solo
        // pueden mostrar SIN_JUSTIFICAR mientras el dia esta en curso; al dia
        // siguiente el dato ya no aparece si no hay fichaje. Tarea A cierra ese hueco.
        // -------------------------------------------------------------------
        log.info("Tarea A iniciada: cierre ausencias injustificadas para {}", hoy);
        int fichajesInjustificados = 0;

        for (Empleado empleado : empleadosActivos) {
            // Verificar si ya existe fichaje para este empleado hoy
            boolean tieneFichaje = fichajeRepository
                    .findByEmpleadoIdAndFecha(empleado.getId(), hoy)
                    .isPresent();

            if (!tieneFichaje) {
                // No tiene fichaje → crear AUSENCIA_INJUSTIFICADA automatica
                Fichaje fichaje = new Fichaje();
                fichaje.setEmpleado(empleado);
                fichaje.setFecha(hoy);
                fichaje.setTipo(TipoFichaje.AUSENCIA_INJUSTIFICADA);
                fichaje.setHoraEntrada(null);           // sin horas: ausencia sin presencia
                fichaje.setHoraSalida(null);
                fichaje.setJornadaEfectivaMinutos(0);   // no trabajo efectivo
                fichaje.setTotalPausasMinutos(0);        // sin pausas
                fichaje.setObservaciones(
                        "Generado automaticamente por ProcesoCierreDiario");
                fichaje.setUsuario(usuarioSistema);

                fichajeRepository.save(fichaje);
                fichajesInjustificados++;
                log.debug("AUSENCIA_INJUSTIFICADA creada para empleado {} en {}",
                        empleado.getId(), hoy);
            }
        }
        log.info("Tarea A completada: {} fichajes AUSENCIA_INJUSTIFICADA creados",
                fichajesInjustificados);

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

        // -------------------------------------------------------------------
        // TAREA C — Actualizacion saldos anuales
        // -------------------------------------------------------------------
        // Se ejecuta siempre despues de A y B para que el recalculo incluya
        // todos los fichajes del dia recien cerrado. Llama a
        // SaldoService.recalcularParaProceso() que hace el recalculo completo
        // desde cero sin construir DTO (Decision 1, Opcion A, sesion 16).
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
                + "Injustificadas={}, Planificaciones={}, Saldos={}",
                hoy, fichajesInjustificados, planificacionesProcesadas, saldosRecalculados);
    }

    // -------------------------------------------------------------------
    // Metodos auxiliares privados
    // -------------------------------------------------------------------

    /**
     * Crea un fichaje de ausencia para un empleado y fecha si no existe ya.
     *
     * <p>La verificacion de existencia previa es necesaria porque:
     *   - En Tarea A puede que ya se haya creado AUSENCIA_INJUSTIFICADA
     *     para ese empleado ese dia antes de procesar la planificacion.
     *   - En Tarea B un festivo global puede coincidir con una ausencia
     *     individual planificada para el mismo empleado.
     * En ambos casos se respeta el fichaje existente sin sobreescribirlo.</p>
     *
     * @param empleado      empleado para el que crear el fichaje
     * @param fecha         fecha del fichaje
     * @param tipoFichaje   tipo de fichaje a crear
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
     * verificada en el diseño del sistema (sesion 16) y los enums lo
     * garantizan. Se usa valueOf para que una discrepancia futura entre
     * enums falle en tiempo de ejecucion con mensaje claro en lugar de
     * producir datos incorrectos silenciosamente.</p>
     *
     * <p>Mapeo completo:
     *   FESTIVO_NACIONAL        → TipoFichaje.FESTIVO_NACIONAL
     *   FESTIVO_LOCAL           → TipoFichaje.FESTIVO_LOCAL
     *   VACACIONES              → TipoFichaje.VACACIONES
     *   ASUNTO_PROPIO           → TipoFichaje.ASUNTO_PROPIO
     *   PERMISO_RETRIBUIDO      → TipoFichaje.PERMISO_RETRIBUIDO
     *   DIA_LIBRE_COMPENSATORIO → TipoFichaje.DIA_LIBRE_COMPENSATORIO
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
