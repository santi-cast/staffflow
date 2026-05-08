package com.staffflow.dto.response;

/**
 * Respuesta de días pendientes de planificar para vacaciones y asuntos propios.
 *
 * Endpoint: GET /api/v1/ausencias/planificacion-vac-ap?empleadoId=&anio=
 * Accesible por ADMIN y ENCARGADO.
 *
 * anioFuturoSinCierre=true indica que el saldo fue creado on-demand
 * porque aún no se hizo el cierre anual. En ese caso, pendientesPlanificar
 * refleja solo el derecho del año — los pendientes del año anterior
 * se añadirán automáticamente al hacer el cierre anual.
 *
 * @author Santiago Castillo
 */
public record PlanificacionVacApResponse(
        VacAp vacaciones,
        VacAp asuntosPropios,
        boolean anioFuturoSinCierre
) {
    /**
     * Desglose de días para un tipo (vacaciones o asuntos propios).
     *
     * disponibles         = derechoAnio + pendientesAnterior - consumidos (del SaldoAnual).
     * planificados        = count ausencias procesado=false del año para ese tipo.
     * pendientesPlanificar = disponibles - planificados (mínimo 0).
     */
    public record VacAp(
            int disponibles,
            int planificados,
            int pendientesPlanificar
    ) {}
}
