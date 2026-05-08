package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.SaldoAnual;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.enums.TipoAusencia;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import com.staffflow.dto.response.SaldoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * Servicio de saldos anuales de vacaciones, asuntos propios y horas.
 *
 * <p>Cubre los endpoints E38-E41 (Grupo 9). Todos son de consulta excepto
 * E40 (recalcular) que es la unica operacion de escritura del grupo.</p>
 *
 * <p>Calculo de horas en E38, E39 y E41 (sin query adicional):
 *   esperadas  = diasTrabajados * jornadaDiariaMinutos / 60.0
 *   trabajadas = esperadas + saldoHoras
 * saldoHoras acumula la diferencia real fichada por el proceso nocturno.
 * E40 (recalcular) si usa FichajeRepository para recalcular desde cero.</p>
 *
 * <p>Patron findOrCreate en E40: si no existe el registro de saldo para
 * el año solicitado se crea con los valores iniciales del contrato del
 * empleado antes de recalcular.</p>
 *
 * <p>recalcularParaProceso() es el metodo interno usado por
 * ProcesoCierreDiario (Tarea C). Contiene toda la logica de recalculo
 * pero no construye SaldoResponse, evitando trabajo innecesario en el
 * proceso nocturno (Decision 1, Opcion A).</p>
 *
 * @author Santiago Castillo
 */
@Service
@RequiredArgsConstructor
public class SaldoService {

    private final SaldoAnualRepository saldoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final FichajeRepository fichajeRepository;
    private final PlanificacionAusenciaRepository planificacionRepository;

    // ----------------------------------------------------------------
    // E38 — GET /api/v1/saldos/{empleadoId}
    // ----------------------------------------------------------------

    /**
     * Devuelve el saldo anual de un empleado concreto para un año (E38).
     *
     * <p>Roles: ADMIN, ENCARGADO. RF-35.
     * Devuelve HTTP 404 si el empleado no existe o si no hay registro
     * de saldo para el año solicitado.</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año a consultar; si null se usa el año actual
     * @return saldo anual completo del empleado
     */
    public SaldoResponse obtenerPorEmpleado(Long empleadoId, Integer anio) {
        int anioConsulta = resolverAnio(anio);

        // 404 si el empleado no existe
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Empleado no encontrado con id: " + empleadoId));

        // Crear on-demand solo para el año actual: evita persistir registros vacíos
        // para años pasados o futuros sin fichajes reales.
        if (anioConsulta == Year.now().getValue()
                && saldoRepository.findByEmpleadoIdAndAnio(empleadoId, anioConsulta).isEmpty()) {
            recalcularParaProceso(empleadoId, anioConsulta);
        }

        SaldoAnual saldo = saldoRepository.findByEmpleadoIdAndAnio(empleadoId, anioConsulta)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe saldo para el empleado " + empleadoId
                        + " en el año " + anioConsulta));

        return toSaldoResponse(saldo, empleado);
    }

    // ----------------------------------------------------------------
    // E39 — GET /api/v1/saldos
    // ----------------------------------------------------------------

    /**
     * Devuelve el saldo anual de todos los empleados para un año (E39).
     *
     * <p>Roles: ADMIN, ENCARGADO. RF-36.
     * Devuelve lista vacia si no hay saldos registrados para ese año.
     * No filtra por activo: un empleado inactivo puede tener saldo
     * historico valido consultable.</p>
     *
     * @param anio año a consultar; si null se usa el año actual
     * @return lista de saldos anuales de todos los empleados con registro ese año
     */
    public List<SaldoResponse> listarTodos(Integer anio) {
        int anioConsulta = resolverAnio(anio);

        // Crear on-demand solo para el año actual: evita persistir registros vacíos
        // para años pasados o futuros sin fichajes reales.
        if (anioConsulta == Year.now().getValue()) {
            empleadoRepository.findAll().stream()
                    .filter(e -> Boolean.TRUE.equals(e.getActivo()))
                    .filter(e -> saldoRepository.findByEmpleadoIdAndAnio(e.getId(), anioConsulta).isEmpty())
                    .forEach(e -> recalcularParaProceso(e.getId(), anioConsulta));
        }

        List<SaldoAnual> saldos = saldoRepository.findByAnio(anioConsulta);

        // Cada SaldoAnual tiene su empleado cargado via @ManyToOne.
        // Con fetch LAZY Hibernate hara una query por empleado al acceder
        // a saldo.getEmpleado() (N+1). Para un volumen de PYME (max ~50
        // empleados) es aceptable. Si se detecta problema de rendimiento
        // la solucion es anadir @EntityGraph o JOIN FETCH en findByAnio.
        return saldos.stream()
                .map(s -> toSaldoResponse(s, s.getEmpleado()))
                .toList();
    }

    // ----------------------------------------------------------------
    // E40 — POST /api/v1/saldos/{empleadoId}/recalcular
    // ----------------------------------------------------------------

    /**
     * Fuerza el recalculo completo del saldo anual de un empleado (E40).
     *
     * <p>Roles: solo ADMIN. RF-37.
     * Delega toda la logica de recalculo en recalcularParaProceso() y
     * construye el SaldoResponse con el resultado persistido.
     * La operacion es idempotente: ejecutarla varias veces produce el
     * mismo resultado porque parte siempre de cero y suma todos los
     * fichajes del año.</p>
     *
     * <p>Patron findOrCreate: si no existe el registro de saldo para el año
     * lo crea con los valores iniciales del contrato del empleado antes
     * de recalcular. Asi E40 puede usarse incluso antes de que el proceso
     * nocturno haya creado el registro.</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año a recalcular; si null se usa el año actual
     * @return saldo recalculado completo
     */
    @Transactional
    public SaldoResponse recalcular(Long empleadoId, Integer anio) {
        int anioConsulta = resolverAnio(anio);

        // 404 si el empleado no existe
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Empleado no encontrado con id: " + empleadoId));

        // Delegar logica de recalculo al metodo interno compartido con
        // ProcesoCierreDiario. recalcularParaProceso persiste el saldo
        // actualizado — aqui solo construimos el DTO para devolverlo al cliente.
        recalcularParaProceso(empleadoId, anioConsulta);

        // Cargar el saldo ya persistido para construir el DTO de respuesta.
        // Siempre existe en este punto: recalcularParaProceso usa findOrCreate.
        SaldoAnual saldo = saldoRepository
                .findByEmpleadoIdAndAnio(empleadoId, anioConsulta)
                .orElseThrow(() -> new IllegalStateException(
                        "Error inesperado: saldo no encontrado tras recalculo"));

        return toSaldoResponse(saldo, empleado);
    }

    // ----------------------------------------------------------------
    // Metodo interno compartido — usado por E40 y ProcesoCierreDiario
    // ----------------------------------------------------------------

    /**
     * Recalcula y persiste el saldo anual de un empleado sin construir DTO.
     *
     * <p>Contiene toda la logica de recalculo de E40 pero no construye
     * SaldoResponse. Esto evita trabajo innecesario cuando lo llama
     * ProcesoCierreDiario a las 23:55 para cada empleado activo
     * (Decision 1, Opcion A de la sesion 16).</p>
     *
     * <p>El metodo recalcular() (E40) llama a este metodo internamente
     * y luego construye el SaldoResponse para devolverlo al cliente.
     * ProcesoCierreDiario llama directamente a este metodo e ignora
     * el resultado porque es un proceso interno sin respuesta HTTP.</p>
     *
     * <p>Patron findOrCreate: si no existe el registro de saldo para el año
     * lo crea con los valores iniciales del contrato del empleado.
     * La operacion es idempotente: siempre reinicia contadores a cero
     * y recalcula desde todos los fichajes del año.</p>
     *
     * @param empleadoId id del empleado a recalcular
     * @param anio       año a recalcular (ya resuelto, nunca null)
     */
    @Transactional
    public void recalcularParaProceso(Long empleadoId, int anio) {

        // Cargar empleado — necesario para jornadaDiariaMinutos y findOrCreate
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Empleado no encontrado con id: " + empleadoId));

        // findOrCreate: si no existe el saldo lo crea con valores iniciales
        SaldoAnual saldo = saldoRepository
                .findByEmpleadoIdAndAnio(empleadoId, anio)
                .orElseGet(() -> crearSaldoInicial(empleado, anio));

        // Obtener todos los fichajes del empleado en ese año
        List<Fichaje> fichajes = fichajeRepository
                .findByEmpleadoIdAndFechaBetween(
                        empleadoId,
                        LocalDate.of(anio, 1, 1),
                        LocalDate.of(anio, 12, 31));

        // Reiniciar contadores antes de recalcular desde cero.
        // La operacion es idempotente: siempre parte de cero y suma todo.
        saldo.setDiasTrabajados(0);
        saldo.setDiasBajaMedica(0);
        saldo.setDiasPermisoRetribuido(0);
        saldo.setDiasAusenciaInjustificada(0);
        saldo.setDiasVacacionesConsumidos(0);
        saldo.setDiasAsuntosPropiosConsumidos(0);
        saldo.setSaldoHoras(BigDecimal.ZERO);

        // Acumular contadores recorriendo los fichajes del año
        for (Fichaje f : fichajes) {
            TipoFichaje tipo = f.getTipo();

            switch (tipo) {
                case NORMAL -> {
                    saldo.setDiasTrabajados(saldo.getDiasTrabajados() + 1);
                    // Diferencia = efectivos - esperados. Positivo = extra.
                    // jornadaEfectivaMinutos ya descuenta pausas (FichajeService).
                    int diferencia = f.getJornadaEfectivaMinutos()
                                     - empleado.getJornadaDiariaMinutos();
                    saldo.setSaldoHoras(saldo.getSaldoHoras()
                            .add(minutosAHoras(diferencia)));
                }
                case FESTIVO_NACIONAL, FESTIVO_LOCAL -> {
                    // Neutro: no suma diasTrabajados ni saldoHoras.
                }
                case DIA_LIBRE_COMPENSATORIO -> {
                    // Resta la jornada teórica: el empleado "gasta" horas acumuladas.
                    saldo.setSaldoHoras(saldo.getSaldoHoras()
                            .subtract(minutosAHoras(empleado.getJornadaDiariaMinutos())));
                }
                case VACACIONES -> {
                    saldo.setDiasVacacionesConsumidos(
                            saldo.getDiasVacacionesConsumidos() + 1);
                }
                case ASUNTO_PROPIO -> {
                    saldo.setDiasAsuntosPropiosConsumidos(
                            saldo.getDiasAsuntosPropiosConsumidos() + 1);
                }
                case PERMISO_RETRIBUIDO -> {
                    // Cuenta como día trabajado pero neutro en saldo:
                    // jornada efectiva = jornada teórica, ni suma ni resta.
                    saldo.setDiasPermisoRetribuido(
                            saldo.getDiasPermisoRetribuido() + 1);
                    saldo.setDiasTrabajados(saldo.getDiasTrabajados() + 1);
                }
                case BAJA_MEDICA -> {
                    // Ídem permiso retribuido: día trabajado, neutro en saldo.
                    saldo.setDiasBajaMedica(saldo.getDiasBajaMedica() + 1);
                    saldo.setDiasTrabajados(saldo.getDiasTrabajados() + 1);
                }
                case AUSENCIA_INJUSTIFICADA -> {
                    saldo.setDiasAusenciaInjustificada(
                            saldo.getDiasAusenciaInjustificada() + 1);
                    // Resta la jornada teórica: el empleado no trabajó ni tiene justificación.
                    saldo.setSaldoHoras(saldo.getSaldoHoras()
                            .subtract(minutosAHoras(empleado.getJornadaDiariaMinutos())));
                }
            }
        }

        // Recalcular disponibles a partir de los nuevos consumidos
        saldo.setDiasVacacionesDisponibles(
                saldo.getDiasVacacionesDerechoAnio()
                + saldo.getDiasVacacionesPendientesAnioAnterior()
                - saldo.getDiasVacacionesConsumidos());

        saldo.setDiasAsuntosPropiosDisponibles(
                saldo.getDiasAsuntosPropiosDerechoAnio()
                + saldo.getDiasAsuntosPropiosPendientesAnterior()
                - saldo.getDiasAsuntosPropiosConsumidos());

        // Actualizar calculadoHastaFecha al dia de hoy
        saldo.setCalculadoHastaFecha(LocalDate.now());

        saldoRepository.save(saldo);
    }

    // ----------------------------------------------------------------
    // E41 — GET /api/v1/saldos/me
    // ----------------------------------------------------------------

    /**
     * Devuelve el saldo anual del empleado autenticado (E41 -- /me).
     *
     * <p>Roles: solo EMPLEADO. RF-53.
     * Recibe el username extraido del JWT por authentication.getName()
     * en el controller. Resuelve el empleado con findByUsuarioUsername,
     * el mismo patron que usan AusenciaService.listarMias() (E34) y
     * PresenciaService.obtenerMiPresencia() (E37). Ningun controller
     * accede directo a un repository (regla de oro del proyecto).</p>
     *
     * <p>Devuelve 404 si no hay saldo registrado para ese año. Esto
     * puede ocurrir si el proceso nocturno aun no ha creado el registro
     * del año en curso.</p>
     *
     * @param username username del empleado autenticado (de authentication.getName())
     * @param anio     año a consultar; si null se usa el año actual
     * @return saldo anual del empleado autenticado
     */
    public SaldoResponse obtenerMiSaldo(String username, Integer anio) {
        int anioConsulta = resolverAnio(anio);

        // Resuelve el empleado desde el username del JWT.
        // Mismo patron que E34 (AusenciaService) y E37 (PresenciaService).
        // ADMIN devuelve Optional.empty() pero @PreAuthorize lo impide antes.
        Empleado empleado = empleadoRepository.findByUsuarioUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Empleado no encontrado para el usuario: " + username));

        // Validar que el año solicitado tiene sentido para este empleado.
        // Antes del año de alta o en el futuro → 404 (sin datos reales).
        int anioActual = Year.now().getValue();
        LocalDate fechaAlta = empleado.getFechaAlta();
        if (anioConsulta > anioActual) {
            throw new IllegalStateException(
                    "No hay datos de saldo para el año " + anioConsulta);
        }
        if (fechaAlta != null && anioConsulta < fechaAlta.getYear()) {
            throw new IllegalStateException(
                    "No hay datos de saldo para el año " + anioConsulta);
        }

        // Si no existe el registro para este año, calcularlo on-demand.
        // Mismo patron que E38 y E40. Evita el 404 antes del primer cierre
        // diario del año.
        if (saldoRepository.findByEmpleadoIdAndAnio(empleado.getId(), anioConsulta).isEmpty()) {
            recalcularParaProceso(empleado.getId(), anioConsulta);
        }

        SaldoAnual saldo = saldoRepository
                .findByEmpleadoIdAndAnio(empleado.getId(), anioConsulta)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe saldo para el año " + anioConsulta));

        return toSaldoResponse(saldo, empleado);
    }

    // ----------------------------------------------------------------
    // Metodos privados
    // ----------------------------------------------------------------

    /**
     * Resuelve el año de consulta.
     *
     * Si anio es null devuelve el año actual. Permite que todos los
     * endpoints usen ?anio como parametro opcional con defecto implicito.
     *
     * @param anio año recibido del query param (puede ser null)
     * @return año a usar en la consulta
     */
    private int resolverAnio(Integer anio) {
        return anio != null ? anio : Year.now().getValue();
    }

    /**
     * Crea un registro de saldo inicial para un empleado y año.
     *
     * <p>Se usa en el patron findOrCreate de recalcularParaProceso() cuando
     * no existe aun el registro para ese año. Los valores de derecho se
     * toman del contrato del empleado en el momento de la creacion.
     * Pendientes del año anterior = 0 porque este metodo solo actua
     * cuando no existe el registro previo (el cierre anual se encarga
     * de arrastrar pendientes, decision n12).</p>
     *
     * @param empleado empleado para el que se crea el saldo
     * @param anio     año del nuevo registro
     * @return saldo guardado con valores iniciales
     */
    private SaldoAnual crearSaldoInicial(Empleado empleado, int anio) {
        SaldoAnual nuevo = new SaldoAnual();
        nuevo.setEmpleado(empleado);
        nuevo.setAnio(anio);
        nuevo.setDiasTrabajados(0);
        nuevo.setDiasBajaMedica(0);
        nuevo.setDiasPermisoRetribuido(0);
        nuevo.setDiasAusenciaInjustificada(0);

        // Prorrateo por fecha de alta: si el empleado se dio de alta este año,
        // se calculan los días proporcionales al número de días desde la fecha
        // de alta hasta el 31 de diciembre (ambos inclusive), sobre el total
        // de días del año. Redondeo al alza (ceil) para que el empleado no
        // pierda fracción de día.
        // Si la fecha de alta es de años anteriores, se asignan los días completos.
        int diasVacaciones;
        int diasAsuntosPropios;
        LocalDate fechaAlta = empleado.getFechaAlta();
        if (fechaAlta != null && fechaAlta.getYear() == anio) {
            LocalDate finAnio = LocalDate.of(anio, 12, 31);
            long diasRestantes = fechaAlta.until(finAnio, java.time.temporal.ChronoUnit.DAYS) + 1;
            long diasTotalesAnio = java.time.Year.of(anio).length();
            diasVacaciones = (int) Math.ceil(
                    empleado.getDiasVacacionesAnuales() * diasRestantes / (double) diasTotalesAnio);
            diasAsuntosPropios = (int) Math.round(
                    empleado.getDiasAsuntosPropiosAnuales() * diasRestantes / (double) diasTotalesAnio);
        } else {
            diasVacaciones = empleado.getDiasVacacionesAnuales();
            diasAsuntosPropios = empleado.getDiasAsuntosPropiosAnuales();
        }

        nuevo.setDiasVacacionesDerechoAnio(diasVacaciones);
        nuevo.setDiasVacacionesPendientesAnioAnterior(0);
        nuevo.setDiasVacacionesConsumidos(0);
        nuevo.setDiasVacacionesDisponibles(diasVacaciones);
        nuevo.setDiasAsuntosPropiosDerechoAnio(diasAsuntosPropios);
        nuevo.setDiasAsuntosPropiosPendientesAnterior(0);
        nuevo.setDiasAsuntosPropiosConsumidos(0);
        nuevo.setDiasAsuntosPropiosDisponibles(diasAsuntosPropios);
        nuevo.setHorasAusenciaRetribuida(BigDecimal.ZERO);
        nuevo.setSaldoHoras(BigDecimal.ZERO);
        nuevo.setCalculadoHastaFecha(null);
        return saldoRepository.save(nuevo);
    }

    /**
     * Convierte minutos a horas con dos decimales.
     *
     * BigDecimal para mantener la precision decimal del calculo
     * de saldo de horas (decision n22). RoundingMode.HALF_UP
     * es el redondeo estandar para valores monetarios y temporales.
     *
     * @param minutos minutos a convertir (puede ser negativo)
     * @return horas equivalentes con dos decimales
     */
    private BigDecimal minutosAHoras(int minutos) {
        return BigDecimal.valueOf(minutos)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /**
     * Convierte una entidad SaldoAnual en SaldoResponse.
     *
     * <p>Calcula los campos de horas en memoria sin query adicional:
     *   esperadas  = diasTrabajados * jornadaDiariaMinutos / 60.0
     *   trabajadas = esperadas + saldoHoras
     * Ambos valores se redondean a 2 decimales (decision n22).</p>
     *
     * @param saldo    entidad con los datos persistidos
     * @param empleado entidad con los datos contractuales (jornadaDiariaMinutos)
     * @return DTO listo para serializar como respuesta JSON
     */
    private SaldoResponse toSaldoResponse(SaldoAnual saldo, Empleado empleado) {
        // Calculo de horas en memoria
        BigDecimal esperadas = BigDecimal.valueOf(saldo.getDiasTrabajados())
                .multiply(BigDecimal.valueOf(empleado.getJornadaDiariaMinutos()))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        BigDecimal trabajadas = esperadas.add(saldo.getSaldoHoras());

        SaldoResponse.HorasDesglose horas = new SaldoResponse.HorasDesglose(
                esperadas,
                trabajadas,
                saldo.getSaldoHoras(),
                saldo.getDiasTrabajados(),
                saldo.getDiasBajaMedica(),
                saldo.getDiasPermisoRetribuido(),
                saldo.getDiasAusenciaInjustificada()
        );

        int anio = saldo.getAnio();
        LocalDate inicioAnio = LocalDate.of(anio, 1, 1);
        LocalDate finAnio    = LocalDate.of(anio, 12, 31);
        Long empId = saldo.getEmpleado().getId();

        int planVac = planificacionRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                empId, TipoAusencia.VACACIONES, inicioAnio, finAnio);
        int planAp  = planificacionRepository.countPlanificadasByEmpleadoAndTipoAndRango(
                empId, TipoAusencia.ASUNTO_PROPIO, inicioAnio, finAnio);

        SaldoResponse.VacacionesDesglose vacaciones = new SaldoResponse.VacacionesDesglose(
                saldo.getDiasVacacionesDerechoAnio(),
                saldo.getDiasVacacionesPendientesAnioAnterior(),
                saldo.getDiasVacacionesConsumidos(),
                saldo.getDiasVacacionesDisponibles(),
                saldo.getDiasVacacionesDisponibles() - planVac
        );

        SaldoResponse.AsuntosPropiosDesglose asuntosPropios = new SaldoResponse.AsuntosPropiosDesglose(
                saldo.getDiasAsuntosPropiosDerechoAnio(),
                saldo.getDiasAsuntosPropiosPendientesAnterior(),
                saldo.getDiasAsuntosPropiosConsumidos(),
                saldo.getDiasAsuntosPropiosDisponibles(),
                saldo.getDiasAsuntosPropiosDisponibles() - planAp
        );

        String nombreCompleto = empleado.getNombre() + " " + empleado.getApellido1();

        return new SaldoResponse(
                saldo.getEmpleado().getId(),
                nombreCompleto,
                saldo.getAnio(),
                vacaciones,
                asuntosPropios,
                horas,
                saldo.getCalculadoHastaFecha()
        );
    }
}
