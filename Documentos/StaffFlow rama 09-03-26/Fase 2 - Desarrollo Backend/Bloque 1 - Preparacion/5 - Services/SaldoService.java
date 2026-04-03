package com.staffflow.service;

import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.SaldoAnualRepository;
import com.staffflow.dto.response.MensajeResponse;
import com.staffflow.dto.response.SaldoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio de saldos anuales de vacaciones, asuntos propios y horas.
 *
 * <p>Cubre los endpoints E38-E41 (Grupo 9). Aplica el patrón findOrCreate:
 * si no existe el registro de saldo para el año en curso lo crea con los
 * valores iniciales del contrato del empleado antes de operar sobre él.</p>
 *
 * <p>Los saldos los actualiza CierreDiario (botón manual ENCARGADO/ADMIN,
 * E40 recalcular, decisión nº11). CierreAnual (solo ADMIN) crea el registro
 * del año siguiente con los pendientes arrastrados (decisión nº12).</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.SaldoAnualRepository
 */
@Service
@RequiredArgsConstructor
public class SaldoService {

    private final SaldoAnualRepository saldoRepository;
    private final EmpleadoRepository empleadoRepository;
    // FichajeRepository necesario para recalcular el saldo desde los fichajes del año
    private final FichajeRepository fichajeRepository;

    /**
     * Devuelve el saldo anual de un empleado concreto para un año (E38).
     *
     * <p>Incluye desglose completo de vacaciones (derecho, pendientes de año
     * anterior, consumidos, disponibles), asuntos propios (ídem) y saldo
     * de horas. Devuelve HTTP 404 si el empleado o el saldo no existen.</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año a consultar (defecto: año actual)
     * @return saldo anual completo del empleado
     */
    public SaldoResponse obtenerPorEmpleado(Long empleadoId, Integer anio) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }

    /**
     * Devuelve el saldo anual de todos los empleados para un año (E39).
     *
     * <p>Mismo desglose completo que E38 por cada empleado activo.
     * Útil para la vista de dirección de recursos humanos.</p>
     *
     * @param anio año a consultar (defecto: año actual)
     * @return lista de saldos anuales de todos los empleados
     */
    public List<SaldoResponse> listarTodos(Integer anio) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }

    /**
     * Fuerza el recálculo completo del saldo anual de un empleado (E40).
     *
     * <p>Recalcula desde el primer fichaje del año hasta hoy. La operación
     * es idempotente: ejecutarla varias veces produce el mismo resultado.
     * calculadoHastaFecha garantiza la idempotencia del proceso nocturno.
     * Útil para corregir inconsistencias tras modificar fichajes retroactivos.</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año a recalcular (defecto: año actual)
     * @return saldo recalculado completo
     */
    public SaldoResponse recalcular(Long empleadoId, Integer anio) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }

    /**
     * Devuelve el saldo anual del empleado autenticado (E41 — /me).
     *
     * <p>Mismo desglose completo que E38. Spring Security garantiza
     * que el empleado solo ve sus propios datos.</p>
     *
     * @param empleadoId id del empleado autenticado
     * @param anio       año a consultar (defecto: año actual)
     * @return saldo anual del empleado autenticado
     */
    public SaldoResponse obtenerMiSaldo(Long empleadoId, Integer anio) {
        // TODO: implementar en Bloque 6
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 6");
    }
}