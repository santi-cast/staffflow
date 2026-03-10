package com.staffflow.service;

import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Servicio de informes de horas y vacaciones en formato JSON o HTML.
 *
 * <p>Cubre los endpoints E42-E44 (Grupo 10). Devuelve datos estructurados
 * como Map o HTML imprimible según el parámetro ?formato= de la petición.
 * El formato HTML está diseñado para PrintManager + WebView en Android.</p>
 *
 * <p>Estos endpoints no tienen DTO de response fijo: el controller
 * devuelve ResponseEntity con el Content-Type apropiado según el formato
 * solicitado (decisión de diseño, se resuelve en Bloque 7).</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.FichajeRepository
 * @see com.staffflow.domain.repository.SaldoAnualRepository
 */
@Service
@RequiredArgsConstructor
public class InformeService {

    private final FichajeRepository fichajeRepository;
    private final SaldoAnualRepository saldoRepository;
    private final EmpleadoRepository empleadoRepository;

    /**
     * Genera el informe de horas de un empleado en un periodo (E42).
     *
     * <p>Detalla los días con jornada NORMAL, días de ausencia por tipo
     * y el total de horas efectivas. Si formato=html devuelve HTML
     * imprimible; si formato=json devuelve Map estructurado.</p>
     *
     * @param empleadoId id del empleado
     * @param desde      fecha de inicio del periodo
     * @param hasta      fecha de fin del periodo
     * @param formato    "json" o "html" (defecto: json)
     * @return informe de horas del empleado en el formato solicitado
     */
    public Object informeHorasEmpleado(Long empleadoId, LocalDate desde, LocalDate hasta, String formato) {
        // TODO: implementar en Bloque 7
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 7");
    }

    /**
     * Genera el informe global de horas de todos los empleados (E43).
     *
     * <p>Resumen por empleado con el total de horas efectivas y desglose
     * de tipos de jornada. Acepta ?formato=html para impresión desde Android.</p>
     *
     * @param desde   fecha de inicio del periodo
     * @param hasta   fecha de fin del periodo
     * @param formato "json" o "html" (defecto: json)
     * @return informe global de horas en el formato solicitado
     */
    public Object informeHorasGlobal(LocalDate desde, LocalDate hasta, String formato) {
        // TODO: implementar en Bloque 7
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 7");
    }

    /**
     * Genera el informe global de vacaciones de todos los empleados activos (E44).
     *
     * <p>Para cada empleado muestra el derecho anual, los días consumidos
     * y los disponibles. Permite planificar el calendario vacacional del equipo.</p>
     *
     * @param anio    año a consultar (defecto: año actual)
     * @param formato "json" o "html" (defecto: json)
     * @return informe de vacaciones en el formato solicitado
     */
    public Object informeVacaciones(Integer anio, String formato) {
        // TODO: implementar en Bloque 7
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 7");
    }
}