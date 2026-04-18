package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.PlanificacionAusencia;
import com.staffflow.domain.entity.Pausa;
import com.staffflow.domain.enums.EstadoPresencia;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.dto.response.DetallePresenciaResponse;
import com.staffflow.dto.response.ParteDiarioResponse;
import com.staffflow.dto.response.SinJustificarResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de control de presencia en tiempo real.
 *
 * Calcula el estado de cada empleado activo para una fecha dada
 * consultando fichajes, pausas activas y ausencias planificadas.
 * No crea ni modifica ningún dato: es una capa de solo lectura
 * que agrega información de tres tablas en un único DTO.
 *
 * Patrón de carga de datos (antiN+1):
 *   Se ejecutan exactamente 4 queries planas al inicio de cada método
 *   principal, independientemente del número de empleados:
 *     1. findByActivo(true)               → todos los empleados activos
 *     2. findByFechaWithEmpleado(fecha)    → todos los fichajes del día
 *     3. findPausasActivasByFecha(fecha)   → todas las pausas activas del día
 *     4. findByFechaAndProcesadoFalse(fecha) → ausencias planificadas del día
 *   A partir de ahí la clasificación se hace en memoria con Maps y Sets,
 *   sin lanzar ninguna query adicional (decisión técnica Bloque 6 Tarea 2).
 *
 * Lógica de clasificación por orden de prioridad (EstadoPresencia):
 *   1. EN_PAUSA           → empleado tiene pausa con horaFin = null hoy
 *   2. JORNADA_COMPLETADA → fichaje con horaEntrada != null y horaSalida != null
 *   3. JORNADA_INICIADA   → fichaje con horaEntrada != null y horaSalida = null
 *   4. AUSENCIA_REGISTRADA → fichaje con tipo != NORMAL
 *   5. AUSENCIA_PLANIFICADA → ausencia en planificacion_ausencias con procesado=false
 *   6. SIN_JUSTIFICAR     → ninguno de los anteriores
 *
 * Nota sobre festivos globales (empleado = null en planificacion_ausencias):
 *   Si existe un festivo global para la fecha, todos los empleados sin fichaje
 *   ni ausencia individual tienen estado AUSENCIA_PLANIFICADA, no SIN_JUSTIFICAR.
 *   Esto evita que un festivo nacional marque a todos los empleados como sin
 *   justificar cuando el proceso nocturno aún no ha generado los fichajes.
 *
 * @author Santiago Castillo
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PresenciaService {

    private final EmpleadoRepository empleadoRepository;
    private final FichajeRepository fichajeRepository;
    private final PausaRepository pausaRepository;
    private final PlanificacionAusenciaRepository ausenciaRepository;

    // ================================================================
    // E35 — GET /api/v1/presencia/parte-diario
    // RF-30 — Parte diario completo
    // Roles: ADMIN, ENCARGADO
    // ================================================================

    /**
     * Devuelve el parte diario completo de una fecha.
     *
     * Carga 4 colecciones en memoria y clasifica a cada empleado activo
     * en uno de los 6 estados de EstadoPresencia. Calcula los contadores
     * globales (fichados, enPausa, ausencias, sinJustificar) y devuelve
     * el listado de detalle ordenado alfabéticamente por apellido1.
     *
     * @param fecha fecha del parte (por defecto hoy en el controller)
     * @return ParteDiarioResponse con contadores globales y detalle por empleado
     */
    public ParteDiarioResponse obtenerParteDiario(LocalDate fecha) {

        // --- Carga de datos: 4 queries planas ---
        List<Empleado> activos = empleadoRepository.findByActivo(true);
        Map<Long, Fichaje> fichajesPorEmpleado = fichajeRepository
                .findByFechaWithEmpleado(fecha)
                .stream()
                .collect(Collectors.toMap(f -> f.getEmpleado().getId(), f -> f));
        Set<Long> enPausaIds = pausaRepository
                .findPausasActivasByFecha(fecha)
                .stream()
                .map(p -> p.getEmpleado().getId())
                .collect(Collectors.toSet());
        List<PlanificacionAusencia> ausencias = ausenciaRepository
                .findByFechaAndProcesadoFalse(fecha);

        // Ausencias individuales por empleadoId (empleado != null)
        Set<Long> ausenciaIndividualIds = ausencias.stream()
                .filter(a -> a.getEmpleado() != null)
                .map(a -> a.getEmpleado().getId())
                .collect(Collectors.toSet());

        // Festivo global: existe ausencia con empleado = null para esta fecha
        boolean festivoGlobal = ausencias.stream()
                .anyMatch(a -> a.getEmpleado() == null);

        // --- Clasificación en memoria ---
        List<DetallePresenciaResponse> detalle = new ArrayList<>();
        int fichados = 0, enPausaCount = 0, ausenciasCount = 0, sinJustificarCount = 0;

        for (Empleado emp : activos) {
            DetallePresenciaResponse fila = clasificarEmpleado(
                    emp, fichajesPorEmpleado.get(emp.getId()),
                    enPausaIds.contains(emp.getId()),
                    ausenciaIndividualIds.contains(emp.getId()),
                    festivoGlobal);
            detalle.add(fila);

            // Acumular contadores globales
            switch (fila.getEstado()) {
                case EN_PAUSA:
                    enPausaCount++;
                    fichados++;   // EN_PAUSA es subconjunto de fichados
                    break;
                case JORNADA_INICIADA:
                case JORNADA_COMPLETADA:
                    fichados++;
                    break;
                case AUSENCIA_REGISTRADA:
                case AUSENCIA_PLANIFICADA:
                    ausenciasCount++;
                    break;
                case SIN_JUSTIFICAR:
                    sinJustificarCount++;
                    break;
            }
        }

        // Ordenar por apellido1, apellido2, nombre (alfabético)
        detalle.sort((a, b) -> {
            int cmp = a.getApellido1().compareToIgnoreCase(b.getApellido1());
            if (cmp != 0) return cmp;
            String a2 = a.getApellido2() != null ? a.getApellido2() : "";
            String b2 = b.getApellido2() != null ? b.getApellido2() : "";
            cmp = a2.compareToIgnoreCase(b2);
            if (cmp != 0) return cmp;
            return a.getNombre().compareToIgnoreCase(b.getNombre());
        });

        return new ParteDiarioResponse(
                fecha,
                activos.size(),
                fichados,
                enPausaCount,
                ausenciasCount,
                sinJustificarCount,
                detalle);
    }

    // ================================================================
    // E36 — GET /api/v1/presencia/sin-justificar
    // RF-31 — Empleados sin justificación
    // Roles: ADMIN, ENCARGADO
    // ================================================================

    /**
     * Devuelve la lista de empleados sin ningún registro para la fecha indicada.
     *
     * Un empleado está sin justificar cuando no tiene fichaje, ni ausencia
     * registrada (fichajes con tipo != NORMAL), ni ausencia planificada
     * (planificacion_ausencias con procesado=false) para ese día.
     * Es un subconjunto del parte diario filtrado por SIN_JUSTIFICAR.
     *
     * Los festivos globales evitan que todos los empleados sin fichaje aparezcan
     * como sin justificar cuando el proceso nocturno aún no ha actuado.
     *
     * @param fecha fecha a consultar (por defecto hoy en el controller)
     * @return lista de empleados sin justificación (puede ser vacía)
     */
    public List<SinJustificarResponse> obtenerSinJustificar(LocalDate fecha) {

        // Reutiliza la misma lógica de carga que obtenerParteDiario
        List<Empleado> activos = empleadoRepository.findByActivo(true);
        Set<Long> conFichajeIds = fichajeRepository
                .findByFechaWithEmpleado(fecha)
                .stream()
                .map(f -> f.getEmpleado().getId())
                .collect(Collectors.toSet());
        Set<Long> enPausaIds = pausaRepository
                .findPausasActivasByFecha(fecha)
                .stream()
                .map(p -> p.getEmpleado().getId())
                .collect(Collectors.toSet());
        List<PlanificacionAusencia> ausencias = ausenciaRepository
                .findByFechaAndProcesadoFalse(fecha);
        Set<Long> ausenciaIndividualIds = ausencias.stream()
                .filter(a -> a.getEmpleado() != null)
                .map(a -> a.getEmpleado().getId())
                .collect(Collectors.toSet());
        boolean festivoGlobal = ausencias.stream()
                .anyMatch(a -> a.getEmpleado() == null);

        // Si hay festivo global, nadie está sin justificar:
        // todos tienen AUSENCIA_PLANIFICADA aunque no tengan fichaje todavía.
        if (festivoGlobal) {
            return List.of();
        }

        // Filtra empleados sin ningún registro
        return activos.stream()
                .filter(emp -> !conFichajeIds.contains(emp.getId())
                        && !enPausaIds.contains(emp.getId())
                        && !ausenciaIndividualIds.contains(emp.getId()))
                .map(emp -> new SinJustificarResponse(
                        emp.getId(),
                        emp.getNombre(),
                        emp.getApellido1(),
                        emp.getApellido2()))
                .sorted((a, b) -> a.getApellido1().compareToIgnoreCase(b.getApellido1()))
                .collect(Collectors.toList());
    }

    // ================================================================
    // E37 — GET /api/v1/presencia/parte-diario/me
    // RF-54 — Estado propio del empleado autenticado
    // Rol: EMPLEADO
    // ================================================================

    /**
     * Devuelve el estado de presencia del empleado autenticado para la fecha indicada.
     *
     * Extrae el username del JWT en el controller, lo resuelve a empleadoId
     * mediante findByUsuarioUsername, y devuelve el detalle de presencia
     * de ese empleado concreto.
     *
     * Devuelve DetallePresenciaResponse (misma estructura que cada fila del
     * parte diario) porque el empleado ve exactamente la misma información
     * que el encargado ve sobre él, sin datos adicionales ni reducidos.
     *
     * @param username username extraído del JWT en el controller
     * @param fecha    fecha a consultar (por defecto hoy en el controller)
     * @return detalle de presencia del empleado para esa fecha
     * @throws IllegalStateException si el username no tiene perfil de empleado
     */
    public DetallePresenciaResponse obtenerMiPresencia(String username, LocalDate fecha) {

        // Resuelve el empleado desde el username del JWT
        Empleado emp = empleadoRepository.findByUsuarioUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario autenticado no tiene perfil de empleado."));

        // Carga los datos relevantes solo para este empleado
        Fichaje fichaje = fichajeRepository
                .findByEmpleadoIdAndFecha(emp.getId(), fecha)
                .orElse(null);

        boolean enPausa = pausaRepository
                .findByEmpleadoIdAndFechaAndHoraFinIsNull(emp.getId(), fecha)
                .isPresent();

        List<PlanificacionAusencia> ausencias = ausenciaRepository
                .findByFechaAndProcesadoFalse(fecha);

        boolean tieneAusenciaIndividual = ausencias.stream()
                .filter(a -> a.getEmpleado() != null)
                .anyMatch(a -> a.getEmpleado().getId().equals(emp.getId()));

        boolean festivoGlobal = ausencias.stream()
                .anyMatch(a -> a.getEmpleado() == null);

        DetallePresenciaResponse response = clasificarEmpleado(
                emp, fichaje, enPausa, tieneAusenciaIndividual, festivoGlobal);

        // Cargar pausas del dia para mostrarlas en P12 (Mi hoy)
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        List<DetallePresenciaResponse.PausaResumen> pausasHoy = pausaRepository
                .findByEmpleadoIdAndFecha(emp.getId(), fecha)
                .stream()
                .map(p -> new DetallePresenciaResponse.PausaResumen(
                        p.getHoraInicio() != null ? p.getHoraInicio().format(fmt) : null,
                        p.getHoraFin() != null ? p.getHoraFin().format(fmt) : null,
                        p.getTipoPausa() != null ? p.getTipoPausa().name() : null,
                        p.getDuracionMinutos()))
                .collect(Collectors.toList());
        response.setPausas(pausasHoy);

        return response;
    }

    // ================================================================
    // MÉTODO PRIVADO — Lógica de clasificación compartida
    // ================================================================

    /**
     * Clasifica el estado de presencia de un empleado para un día dado.
     *
     * Aplica la lógica de prioridad definida en EstadoPresencia:
     *   1. EN_PAUSA           (pausa activa en este momento)
     *   2. JORNADA_COMPLETADA (fichaje cerrado: entrada + salida)
     *   3. JORNADA_INICIADA   (fichaje abierto: entrada sin salida)
     *   4. AUSENCIA_REGISTRADA (fichaje con tipo != NORMAL)
     *   5. AUSENCIA_PLANIFICADA (ausencia en planificacion_ausencias)
     *   6. SIN_JUSTIFICAR     (ninguno de los anteriores)
     *
     * Este método se comparte entre obtenerParteDiario() y obtenerMiPresencia()
     * para garantizar que la lógica de clasificación es idéntica en E35 y E37.
     * Alternativa descartada: duplicar la lógica en cada método — mayor riesgo
     * de inconsistencia si se modifica el criterio de clasificación en el futuro.
     *
     * @param emp                   empleado a clasificar
     * @param fichaje               fichaje del día, null si no existe
     * @param enPausa               true si tiene pausa activa en este momento
     * @param tieneAusenciaIndividual true si tiene ausencia planificada individual
     * @param festivoGlobal         true si hay festivo global para este día
     * @return DetallePresenciaResponse con el estado calculado y los campos del fichaje
     */
    private DetallePresenciaResponse clasificarEmpleado(
            Empleado emp,
            Fichaje fichaje,
            boolean enPausa,
            boolean tieneAusenciaIndividual,
            boolean festivoGlobal) {

        EstadoPresencia estado;
        Boolean pausaActiva = enPausa;

        if (fichaje != null) {
            if (enPausa) {
                // Prioridad 1: pausa activa (aunque tenga fichaje abierto)
                estado = EstadoPresencia.EN_PAUSA;
            } else if (fichaje.getHoraSalida() != null) {
                // Prioridad 2: jornada cerrada (entrada y salida registradas)
                estado = EstadoPresencia.JORNADA_COMPLETADA;
            } else if (fichaje.getHoraEntrada() != null) {
                // Prioridad 3: jornada en curso (entrada sin salida)
                estado = EstadoPresencia.JORNADA_INICIADA;
            } else {
                // Prioridad 4: fichaje sin hora de entrada = ausencia registrada
                // (VACACIONES, BAJA_MEDICA, etc. generadas por ProcesoCierreDiario)
                estado = EstadoPresencia.AUSENCIA_REGISTRADA;
            }
        } else if (tieneAusenciaIndividual || festivoGlobal) {
            // Prioridad 5: ausencia planificada (proceso nocturno no ha actuado aún)
            estado = EstadoPresencia.AUSENCIA_PLANIFICADA;
        } else {
            // Prioridad 6: sin ningún registro
            estado = EstadoPresencia.SIN_JUSTIFICAR;
        }

        return new DetallePresenciaResponse(
                emp.getId(),
                emp.getNombre(),
                emp.getApellido1(),
                emp.getApellido2(),
                estado,
                fichaje != null ? fichaje.getHoraEntrada() : null,
                fichaje != null ? fichaje.getHoraSalida() : null,
                pausaActiva,
                fichaje != null ? fichaje.getTipo() : null,
                null);   // pausas: solo se rellena en E37 via obtenerMiPresencia()
    }
}
