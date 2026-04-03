package com.staffflow.service;

import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Servicio de generación de informes PDF firmables con iText 7.
 *
 * <p>Cubre los endpoints E45-E47 (Grupo 11). Genera documentos PDF
 * binarios que incluyen espacio para firma física del empleado, requisito
 * para la documentación oficial ante la Inspección de Trabajo (RD-ley 8/2019).</p>
 *
 * <p>RNF-R04: la generación debe completarse en menos de 3 segundos para
 * documentos de hasta 12 meses. El controller devuelve ResponseEntity&lt;byte[]&gt;
 * con Content-Type: application/pdf.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.FichajeRepository
 * @see com.staffflow.domain.repository.SaldoAnualRepository
 */
@Service
@RequiredArgsConstructor
public class PdfService {

    private final FichajeRepository fichajeRepository;
    private final SaldoAnualRepository saldoRepository;
    private final EmpleadoRepository empleadoRepository;

    /**
     * Genera el PDF del resumen mensual de un empleado (E45).
     *
     * <p>Incluye todos los fichajes del mes, pausas, tipos de jornada
     * y totales. Espacio para firma del empleado (RD-ley 8/2019, RF-38).</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año del informe
     * @param mes        mes del informe (1-12)
     * @return bytes del PDF generado con iText 7
     */
    public byte[] generarMensual(Long empleadoId, Integer anio, Integer mes) {
        // TODO: implementar en Bloque 7
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 7");
    }

    /**
     * Genera el PDF de cierre anual de un empleado (E46).
     *
     * <p>Incluye el saldo completo del año: vacaciones (derecho, pendientes
     * de año anterior, consumidos, disponibles), asuntos propios (ídem) y
     * saldo de horas. Espacio para firma del empleado (RF-39).</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año del cierre (defecto: año actual)
     * @return bytes del PDF generado con iText 7
     */
    public byte[] generarAnual(Long empleadoId, Integer anio) {
        // TODO: implementar en Bloque 7
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 7");
    }

    /**
     * Genera el PDF del calendario de vacaciones y asuntos propios (E47).
     *
     * <p>Incluye todas las ausencias planificadas y consumidas del año
     * con sus fechas. Espacio para firma del empleado (RF-40).</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año del calendario (defecto: año actual)
     * @return bytes del PDF generado con iText 7
     */
    public byte[] generarVacaciones(Long empleadoId, Integer anio) {
        // TODO: implementar en Bloque 7
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 7");
    }
}