package com.staffflow.dto.response;

/**
 * Respuesta de días pendientes de planificar para vacaciones y asuntos propios.
 *
 * Endpoint: GET /api/v1/ausencias/planificacion-vac-ap?empleadoId=&anio=
 * Accesible por ADMIN y ENCARGADO.
 *
 * anioFuturoSinCierre=true cuando el año consultado es posterior al año
 * actual: en ese caso el saldo se crea on-demand como proyeccion inicial
 * (derechoAnio del empleado, sin consumo ni pendientes anteriores). El
 * cliente debe interpretar el desglose como estimacion susceptible de
 * variar a medida que el empleado fiche en el año en curso.
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
