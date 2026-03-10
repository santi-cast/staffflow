package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representación del saldo anual de vacaciones, asuntos propios
 * y horas de un empleado.
 * Usado en E38 (GET /api/v1/saldos/empleado/{id}),
 * E39 (GET /api/v1/saldos/empleado/{id}/anio/{anio}),
 * E40 (POST cierre diario) y E41 (POST cierre anual).
 * ADMIN y ENCARGADO acceden a todos los saldos.
 * EMPLEADO solo puede consultar el suyo (decisión nº10 y nº14).
 *
 * El saldo lo actualiza ProcesoDiario @Scheduled de forma
 * incremental e idempotente. CierreDiario (E40) y CierreAnual (E41)
 * son botones manuales de ENCARGADO/ADMIN (decisiones nº11 y nº12).
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaldoResponse {

    private Long id;

    private Long empleadoId;

    private Integer anio;

    // --- Contadores de días por tipo ---

    private Integer diasTrabajados;

    private Integer diasBajaMedica;

    private Integer diasPermisoRetribuido;

    private Integer diasAusenciaInjustificada;

    // --- Vacaciones ---

    // Días que corresponden por convenio este año (Empleado.diasVacacionesAnuales).
    private Integer diasVacacionesDerechoAnio;

    // Días no disfrutados del año anterior trasladados a este (cierre anual).
    private Integer diasVacacionesPendientesAnioAnterior;

    private Integer diasVacacionesConsumidos;

    // derechoAnio + pendientesAnioAnterior - consumidos (RF-35).
    private Integer diasVacacionesDisponibles;

    // --- Asuntos propios ---

    // Días que corresponden por convenio este año (Empleado.diasAsuntosPropiosAnuales).
    private Integer diasAsuntosPropiosDerechoAnio;

    // Días no disfrutados del año anterior trasladados a este (cierre anual).
    private Integer diasAsuntosPropiosPendientesAnterior;

    private Integer diasAsuntosPropiosConsumidos;

    // derechoAnio + pendientesAnioAnterior - consumidos (RF-36).
    private Integer diasAsuntosPropiosDisponibles;

    // --- Horas ---

    // Horas de ausencia retribuida acumuladas (pausas AUSENCIA_RETRIBUIDA).
    // BigDecimal por precisión decimal en el cálculo (decisión nº22).
    private BigDecimal horasAusenciaRetribuida;

    // Positivo = horas extra acumuladas. Negativo = déficit de jornada.
    private BigDecimal saldoHoras;

    // --- Control de proceso ---

    // Último día procesado por ProcesoDiario. Permite al cliente Android
    // mostrar hasta qué fecha están actualizados los datos.
    private LocalDate calculadoHastaFecha;

    private LocalDateTime fechaUltimaModificacion;
}