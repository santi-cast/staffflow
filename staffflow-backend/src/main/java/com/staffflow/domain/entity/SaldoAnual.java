package com.staffflow.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Saldo anual de vacaciones, asuntos propios y horas de un empleado.
 * Un registro por empleado por año. Lo actualiza el proceso nocturno
 * (@Scheduled) de forma incremental e idempotente: calculadoHastaFecha
 * evita reprocesar días ya contabilizados (RF-35, RF-36, RF-53).
 * El desglose completo (derecho + pendientes anterior + consumidos)
 * es necesario para el cálculo correcto al cambio de año.
 *
 * @author Santiago Castillo
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "saldos_anuales",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"empleado_id", "anio"}   // 1 registro por empleado por año
        ),
        indexes = @Index(name = "idx_saldos_anio", columnList = "anio")
)
public class SaldoAnual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(name = "anio", nullable = false)
    private Integer anio;

    // --- Contadores de días por tipo ---

    @Column(name = "dias_trabajados", nullable = false)
    private Integer diasTrabajados = 0;

    @Column(name = "dias_baja_medica", nullable = false)
    private Integer diasBajaMedica = 0;

    @Column(name = "dias_permiso_retribuido", nullable = false)
    private Integer diasPermisoRetribuido = 0;

    @Column(name = "dias_ausencia_injustificada", nullable = false)
    private Integer diasAusenciaInjustificada = 0;

    // --- Vacaciones ---

    // Días que corresponden por convenio en este año (Empleado.diasVacacionesAnuales).
    @Column(name = "dias_vacaciones_derecho_anio", nullable = false)
    private Integer diasVacacionesDerechoAnio;

    // Días no disfrutados del año anterior y trasladados a este (cierre anual).
    @Column(name = "dias_vacaciones_pendientes_anio_anterior", nullable = false)
    private Integer diasVacacionesPendientesAnioAnterior = 0;

    @Column(name = "dias_vacaciones_consumidos", nullable = false)
    private Integer diasVacacionesConsumidos = 0;

    // derecho + pendientes anterior - consumidos (RF-35).
    @Column(name = "dias_vacaciones_disponibles", nullable = false)
    private Integer diasVacacionesDisponibles;

    // --- Asuntos propios ---

    // Días que corresponden por convenio en este año (Empleado.diasAsuntosPropiosAnuales).
    @Column(name = "dias_asuntos_propios_derecho_anio", nullable = false)
    private Integer diasAsuntosPropiosDerechoAnio;

    // Días no disfrutados del año anterior y trasladados a este (cierre anual).
    @Column(name = "dias_asuntos_propios_pendientes_anterior", nullable = false)
    private Integer diasAsuntosPropiosPendientesAnterior = 0;

    @Column(name = "dias_asuntos_propios_consumidos", nullable = false)
    private Integer diasAsuntosPropiosConsumidos = 0;

    // derecho + pendientes anterior - consumidos (RF-36).
    @Column(name = "dias_asuntos_propios_disponibles", nullable = false)
    private Integer diasAsuntosPropiosDisponibles;

    // --- Horas ---

    // Horas de ausencia retribuida acumuladas (pausas AUSENCIA_RETRIBUIDA).
    // BigDecimal por precisión decimal en el cálculo.
    @Column(name = "horas_ausencia_retribuida", nullable = false, precision = 10, scale = 2)
    private BigDecimal horasAusenciaRetribuida = BigDecimal.ZERO;

    // Positivo = horas extra acumuladas. Negativo = déficit de jornada.
    @Column(name = "saldo_horas", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoHoras = BigDecimal.ZERO;

    // --- Control de proceso ---

    // Último día procesado por @Scheduled. Garantiza idempotencia:
    // el proceso nocturno solo actúa sobre días posteriores a esta fecha.
    @Column(name = "calculado_hasta_fecha")
    private LocalDate calculadoHastaFecha;

    // Equivale al ON UPDATE CURRENT_TIMESTAMP del DDL (gestionado por @PreUpdate).
    @Column(name = "fecha_ultima_modificacion", nullable = false)
    private LocalDateTime fechaUltimaModificacion;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.fechaUltimaModificacion = LocalDateTime.now();
    }
}