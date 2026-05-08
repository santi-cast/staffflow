package com.staffflow.controller;

import com.staffflow.dto.response.SaldoResponse;
import com.staffflow.service.SaldoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller de saldos anuales de vacaciones, asuntos propios y horas.
 *
 * <p>Grupo 9 de la API REST. Cubre E38-E41.
 * Todos los endpoints son de solo lectura excepto E40 (recalcular),
 * que es una operacion de escritura exclusiva del rol ADMIN.</p>
 *
 * <p>Convencion /me primero: E41 (/me) se declara antes que E38 (/{empleadoId})
 * para evitar que Spring interprete "me" como un Long al resolver el path.
 * Mismo patron aplicado en EmpleadoController (E21), FichajeController (E26),
 * AusenciaController (E34) y PresenciaController (E37).</p>
 *
 * <p>E41 extrae el username con authentication.getName() y delega en
 * SaldoService la resolucion del empleado con findByUsuarioUsername.
 * El controller no accede a ningun repository (regla de oro).</p>
 *
 * @author Santiago Castillo
 */
@RestController
@RequestMapping("/api/v1/saldos")
@RequiredArgsConstructor
@Tag(name = "Saldos", description = "Consulta y recalculo de saldos anuales (E38-E41)")
@SecurityRequirement(name = "bearerAuth")
public class SaldoController {

    private final SaldoService saldoService;

    // ----------------------------------------------------------------
    // E41 — GET /api/v1/saldos/me
    // DECLARADO ANTES de /{empleadoId} para evitar conflicto de path
    // ----------------------------------------------------------------

    /**
     * Devuelve el saldo anual del empleado autenticado (E41).
     *
     * <p>Roles: EMPLEADO y ENCARGADO (ambos son personas físicas
     * trabajadoras con perfil de empleado). ADMIN excluido. RF-53.
     * El username se extrae del JWT con authentication.getName().
     * El service resuelve el empleado internamente con findByUsuarioUsername,
     * mismo patron que E34 (AusenciaController) y E37 (PresenciaController).
     * El controller no accede a ningun repository (regla de oro).</p>
     *
     * @param anio           año a consultar (opcional, defecto: año actual)
     * @param authentication objeto de seguridad con el username del JWT
     * @return saldo anual completo del empleado autenticado
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPLEADO','ENCARGADO')")
    @Operation(summary = "Mi saldo anual",
               description = "Devuelve el saldo anual del empleado autenticado (RF-53)")
    public ResponseEntity<SaldoResponse> obtenerMiSaldo(
            @RequestParam(required = false) Integer anio,
            Authentication authentication) {

        return ResponseEntity.ok(
                saldoService.obtenerMiSaldo(authentication.getName(), anio));
    }

    // ----------------------------------------------------------------
    // E38 — GET /api/v1/saldos/{empleadoId}
    // ----------------------------------------------------------------

    /**
     * Devuelve el saldo anual de un empleado concreto (E38).
     *
     * <p>Roles: ADMIN, ENCARGADO. RF-35.
     * Devuelve 404 si el empleado no existe o si no hay registro de
     * saldo para el año solicitado.</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año a consultar (opcional, defecto: año actual)
     * @return saldo anual completo del empleado
     */
    @GetMapping("/{empleadoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    @Operation(summary = "Saldo anual de un empleado",
               description = "Devuelve el saldo anual completo de un empleado (RF-35)")
    public ResponseEntity<SaldoResponse> obtenerPorEmpleado(
            @PathVariable Long empleadoId,
            @RequestParam(required = false) Integer anio) {

        return ResponseEntity.ok(saldoService.obtenerPorEmpleado(empleadoId, anio));
    }

    // ----------------------------------------------------------------
    // E39 — GET /api/v1/saldos
    // ----------------------------------------------------------------

    /**
     * Devuelve el saldo anual de todos los empleados para un año (E39).
     *
     * <p>Roles: ADMIN, ENCARGADO. RF-36.
     * Devuelve lista vacia si no hay saldos registrados para ese año.
     * Incluye empleados inactivos con saldo historico valido.</p>
     *
     * @param anio año a consultar (opcional, defecto: año actual)
     * @return lista de saldos anuales de todos los empleados con registro ese año
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")
    @Operation(summary = "Saldos anuales de todos los empleados",
               description = "Lista el saldo anual de todos los empleados para un año (RF-36)")
    public ResponseEntity<List<SaldoResponse>> listarTodos(
            @RequestParam(required = false) Integer anio) {

        return ResponseEntity.ok(saldoService.listarTodos(anio));
    }

    // ----------------------------------------------------------------
    // E40 — POST /api/v1/saldos/{empleadoId}/recalcular
    // ----------------------------------------------------------------

    /**
     * Fuerza el recalculo completo del saldo anual de un empleado (E40).
     *
     * <p>Rol: solo ADMIN. RF-37.
     * Operacion idempotente: ejecutarla varias veces produce el mismo
     * resultado. Util para corregir inconsistencias tras modificar
     * fichajes retroactivos. Devuelve el saldo recalculado completo.</p>
     *
     * <p>Se usa POST en lugar de PATCH porque no modifica campos parciales:
     * recalcula el registro completo desde cero. La convencion PUT/PATCH
     * del proyecto reserva PATCH para cambios de estado o campos parciales
     * (convencion cerrada en Fase 1).</p>
     *
     * @param empleadoId id del empleado
     * @param anio       año a recalcular (opcional, defecto: año actual)
     * @return saldo recalculado completo
     */
    @PostMapping("/{empleadoId}/recalcular")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Recalcular saldo anual",
               description = "Fuerza el recalculo completo del saldo anual de un empleado (RF-37)")
    public ResponseEntity<SaldoResponse> recalcular(
            @PathVariable Long empleadoId,
            @RequestParam(required = false) Integer anio) {

        return ResponseEntity.ok(saldoService.recalcular(empleadoId, anio));
    }
}
