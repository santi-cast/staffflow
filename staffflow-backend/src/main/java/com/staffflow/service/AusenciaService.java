package com.staffflow.service;

import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.dto.request.AusenciaPatchRequest;
import com.staffflow.dto.request.AusenciaRequest;
import com.staffflow.dto.response.AusenciaResponse;
import com.staffflow.dto.response.MensajeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Servicio de planificación de ausencias.
 *
 * <p>Cubre los endpoints E30-E34 (Grupo 7). Gestiona las ausencias
 * planificadas con antelación. El único DELETE real del sistema (E32)
 * solo opera sobre ausencias con procesado=false.</p>
 *
 * <p>Si empleadoId es null en la creación, la ausencia es un festivo global
 * que ProcesoDiario aplicará a todos los empleados activos ese día (RF-26).</p>
 *
 * <p>Una ausencia con procesado=true ya fue convertida en fichaje por
 * ProcesoDiario y no puede modificarse ni eliminarse.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.PlanificacionAusenciaRepository
 */
@Service
@RequiredArgsConstructor
public class AusenciaService {

    private final PlanificacionAusenciaRepository ausenciaRepository;

    /**
     * Planifica una ausencia futura para un empleado (E30).
     *
     * <p>Si empleadoId es null crea un festivo global (RF-26).
     * Valida el UNIQUE(empleado_id, fecha) antes de insertar para
     * devolver HTTP 409 con mensaje claro en lugar de dejar explotar
     * DataIntegrityViolationException.</p>
     *
     * @param request    datos de la ausencia a planificar
     * @param usuarioId  id del usuario autenticado (para auditoría)
     * @return ausencia planificada creada con procesado=false
     */
    public AusenciaResponse crear(AusenciaRequest request, Long usuarioId) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }

    /**
     * Modifica una ausencia planificada (E31).
     *
     * <p>Solo se puede modificar si procesado=false. Si procesado=true
     * devuelve HTTP 409: hay que modificar el fichaje generado (E23).</p>
     *
     * @param id      id de la ausencia a modificar
     * @param request campos a actualizar
     * @return ausencia actualizada
     */
    public AusenciaResponse actualizar(Long id, AusenciaPatchRequest request) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }

    /**
     * Elimina una ausencia planificada (E32 — único DELETE real del sistema).
     *
     * <p>Solo funciona si procesado=false. Si procesado=true devuelve
     * HTTP 409: el fichaje generado es inmutable (RNF-L01).</p>
     *
     * @param id id de la ausencia a eliminar
     */
    public void eliminar(Long id) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }

    /**
     * Lista ausencias planificadas con filtros opcionales (E33).
     *
     * <p>Sin empleadoId devuelve las ausencias de todos los empleados.
     * Incluye festivos globales (empleadoId = null).</p>
     *
     * @param empleadoId filtro por empleado (nullable)
     * @param desde      fecha de inicio del rango (nullable)
     * @param hasta      fecha de fin del rango (nullable)
     * @param procesado  filtro por estado procesado (nullable)
     * @return lista de ausencias que cumplen los filtros
     */
    public List<AusenciaResponse> listar(Long empleadoId, LocalDate desde, LocalDate hasta, Boolean procesado) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }

    /**
     * Lista las ausencias planificadas del empleado autenticado (E34 — /me).
     *
     * <p>Spring Security garantiza que el empleado solo ve las suyas propias.
     * Incluye vacaciones y permisos planificados futuros.</p>
     *
     * @param empleadoId id del empleado autenticado
     * @param desde      fecha de inicio del rango (nullable)
     * @param hasta      fecha de fin del rango (nullable)
     * @return lista de ausencias propias del empleado
     */
    public List<AusenciaResponse> listarMias(Long empleadoId, LocalDate desde, LocalDate hasta) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }
}