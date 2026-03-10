package com.staffflow.service;

import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.dto.request.PausaPatchRequest;
import com.staffflow.dto.request.PausaRequest;
import com.staffflow.dto.response.PausaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Servicio de gestión de pausas dentro de la jornada laboral.
 *
 * <p>Cubre los endpoints E27-E29 (Grupo 6). Las pausas son inmutables
 * igual que los fichajes (RNF-L01): no existe DELETE. Solo se permite
 * PATCH en observaciones (E28).</p>
 *
 * <p>Las pausas de tipo AUSENCIA_RETRIBUIDA no descuentan de la jornada
 * efectiva. Se acumulan en horasAusenciaRetribuida del saldo anual.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.PausaRepository
 */
@Service
@RequiredArgsConstructor
public class PausaService {

    private final PausaRepository pausaRepository;
    // FichajeRepository necesario para recalcular jornadaEfectivaMinutos
    // cuando se modifica una pausa que cambia de tipo retribuido a no retribuido
    private final FichajeRepository fichajeRepository;

    /**
     * Registra una pausa manual para un empleado (E27).
     *
     * <p>Si se proporciona horaFin, duracionMinutos se calcula con
     * Math.floor (redondeo a la baja, beneficia al empleado). Si no se
     * proporciona horaFin, la pausa queda activa (horaFin = NULL).</p>
     *
     * @param request    datos de la pausa a crear
     * @param usuarioId  id del usuario autenticado (para auditoría)
     * @return pausa creada con duracionMinutos calculada si horaFin presente
     */
    public PausaResponse crear(PausaRequest request, Long usuarioId) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }

    /**
     * Modifica una pausa existente (E28).
     *
     * <p>Las observaciones son obligatorias (RNF-L02). Si el cambio afecta
     * al tipo (retribuido/no retribuido), jornadaEfectivaMinutos del fichaje
     * del día se recalcula automáticamente.</p>
     *
     * @param id        id de la pausa a modificar
     * @param request   campos a actualizar
     * @param usuarioId id del usuario autenticado (para auditoría)
     * @return pausa actualizada con duración recalculada
     */
    public PausaResponse actualizar(Long id, PausaPatchRequest request, Long usuarioId) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }

    /**
     * Lista pausas con filtros opcionales (E29).
     *
     * <p>Sin empleadoId devuelve pausas de todos los empleados.
     * Útil para detectar patrones de pausa o verificar tiempos de descanso.</p>
     *
     * @param empleadoId filtro por empleado (nullable)
     * @param desde      fecha de inicio del rango (nullable)
     * @param hasta      fecha de fin del rango (nullable)
     * @param tipoPausa  filtro por tipo de pausa (nullable)
     * @return lista de pausas que cumplen los filtros
     */
    public List<PausaResponse> listar(Long empleadoId, LocalDate desde, LocalDate hasta, String tipoPausa) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }
}