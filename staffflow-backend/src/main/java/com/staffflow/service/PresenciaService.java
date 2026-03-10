package com.staffflow.service;

import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.dto.response.ParteDiarioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Servicio de control de presencia en tiempo real.
 *
 * <p>Cubre los endpoints E35-E37 (Grupo 8). No realiza operaciones CRUD:
 * agrega datos de cuatro tablas (empleados, fichajes, pausas y
 * planificacion_ausencias) para calcular el estado de presencia de cada
 * empleado en un momento dado (patrón Query Object).</p>
 *
 * <p>Calcula el enum EstadoPresencia para cada empleado activo siguiendo
 * este orden de prioridad: EN_PAUSA → JORNADA_COMPLETADA → JORNADA_INICIADA
 * → AUSENCIA_REGISTRADA → AUSENCIA_PLANIFICADA → SIN_JUSTIFICAR (D-012).</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.EmpleadoRepository
 * @see com.staffflow.domain.repository.FichajeRepository
 * @see com.staffflow.domain.repository.PausaRepository
 * @see com.staffflow.domain.repository.PlanificacionAusenciaRepository
 */
@Service
@RequiredArgsConstructor
public class PresenciaService {

    private final EmpleadoRepository empleadoRepository;
    private final FichajeRepository fichajeRepository;
    private final PausaRepository pausaRepository;
    private final PlanificacionAusenciaRepository ausenciaRepository;

    /**
     * Devuelve el parte diario completo de una fecha (E35).
     *
     * <p>Calcula el EstadoPresencia de cada empleado activo consultando
     * en tiempo real fichajes, pausas y ausencias planificadas. Incluye
     * contadores globales: total empleados, fichados, en pausa, ausencias,
     * sin justificar.</p>
     *
     * @param fecha fecha del parte (defecto: hoy)
     * @return parte diario con estado de cada empleado activo
     */
    public List<ParteDiarioResponse> obtenerParteDiario(LocalDate fecha) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }

    /**
     * Lista los empleados sin ningún registro para una fecha (E36).
     *
     * <p>Un empleado está SIN_JUSTIFICAR si no tiene fichaje, ausencia
     * registrada ni ausencia planificada para la fecha indicada. Requiere
     * atención del encargado (RF-31).</p>
     *
     * @param fecha fecha a consultar (defecto: hoy)
     * @return lista de empleados sin justificar
     */
    public List<ParteDiarioResponse> listarSinJustificar(LocalDate fecha) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }

    /**
     * Devuelve el estado del empleado autenticado para una fecha (E37 — /me).
     *
     * <p>Mismo cálculo que obtenerParteDiario pero para un único empleado.
     * Spring Security garantiza que el empleado solo ve su propio estado.</p>
     *
     * @param empleadoId id del empleado autenticado
     * @param fecha      fecha a consultar (defecto: hoy)
     * @return estado de presencia del empleado autenticado
     */
    public ParteDiarioResponse obtenerMiEstado(Long empleadoId, LocalDate fecha) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }
}