package com.staffflow.service;

import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.dto.request.FichajePatchRequest;
import com.staffflow.dto.request.FichajeRequest;
import com.staffflow.dto.response.FichajeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Servicio de gestión de fichajes.
 *
 * <p>Cubre los endpoints E22-E26 (Grupo 5). Los fichajes son inmutables
 * por mandato del RD-ley 8/2019 (RNF-L01): no existe DELETE. Solo se
 * permite PATCH en observaciones (E23). Las observaciones son obligatorias
 * en todo fichaje manual para garantizar la trazabilidad (RNF-L02).</p>
 *
 * <p>Un empleado no puede tener dos fichajes el mismo día
 * (UNIQUE empleado_id + fecha en BD).</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.FichajeRepository
 */
@Service
@RequiredArgsConstructor
public class FichajeService {

    private final FichajeRepository fichajeRepository;
    // PausaRepository necesario para calcular totalPausasMinutos al completar jornada
    private final PausaRepository pausaRepository;

    /**
     * Registra un fichaje manual para un empleado (E22).
     *
     * <p>Las observaciones son obligatorias (RNF-L02). El campo usuarioId
     * se rellena automáticamente con el usuario autenticado para auditoría.
     * Devuelve HTTP 409 si ya existe fichaje para ese empleado ese día.</p>
     *
     * @param request    datos del fichaje a crear
     * @param usuarioId  id del usuario autenticado (para auditoría)
     * @return fichaje creado con jornadaEfectivaMinutos calculada
     */
    public FichajeResponse crear(FichajeRequest request, Long usuarioId) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }

    /**
     * Modifica las observaciones de un fichaje existente (E23).
     *
     * <p>Las observaciones son obligatorias (RNF-L02). Si se modifican
     * horaEntrada o horaSalida, jornadaEfectivaMinutos se recalcula
     * automáticamente descontando totalPausasMinutos del día.</p>
     *
     * @param id        id del fichaje a modificar
     * @param request   campos a actualizar
     * @param usuarioId id del usuario autenticado (para auditoría)
     * @return fichaje actualizado con jornada recalculada
     */
    public FichajeResponse actualizar(Long id, FichajePatchRequest request, Long usuarioId) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }

    /**
     * Lista fichajes con filtros opcionales y combinables (E24).
     *
     * <p>Sin empleadoId devuelve fichajes de todos los empleados (RF-20).
     * Con empleadoId filtra por empleado (RF-19). Los filtros de fecha
     * son inclusivos en ambos extremos.</p>
     *
     * @param empleadoId filtro por empleado (nullable)
     * @param desde      fecha de inicio del rango (nullable)
     * @param hasta      fecha de fin del rango (nullable)
     * @param tipo       filtro por tipo de fichaje (nullable)
     * @return lista de fichajes que cumplen los filtros
     */
    public List<FichajeResponse> listar(Long empleadoId, LocalDate desde, LocalDate hasta, String tipo) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }

    /**
     * Lista los empleados con fichaje de entrada pero sin salida para una fecha (E25).
     *
     * <p>Permite al encargado detectar al final del día qué empleados han
     * olvidado fichar la salida (hora_salida = NULL, hora_entrada != NULL).</p>
     *
     * @param fecha fecha a consultar (defecto: hoy)
     * @return lista de fichajes incompletos con minutos transcurridos
     */
    public List<FichajeResponse> listarIncompletos(LocalDate fecha) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }

    /**
     * Lista los fichajes del empleado autenticado (E26 — /me).
     *
     * <p>Spring Security garantiza que el empleado solo ve sus propios
     * fichajes. Incluye totalPausasMinutos y jornadaEfectivaMinutos
     * para que el empleado pueda verificar sus horas.</p>
     *
     * @param empleadoId id del empleado autenticado
     * @param desde      fecha de inicio del rango (nullable)
     * @param hasta      fecha de fin del rango (nullable)
     * @param tipo       filtro por tipo de fichaje (nullable)
     * @return lista de fichajes propios del empleado
     */
    public List<FichajeResponse> listarMios(Long empleadoId, LocalDate desde, LocalDate hasta, String tipo) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }
}