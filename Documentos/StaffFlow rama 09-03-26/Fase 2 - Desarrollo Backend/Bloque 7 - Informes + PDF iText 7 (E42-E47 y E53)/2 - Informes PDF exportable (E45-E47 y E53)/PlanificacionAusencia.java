package com.staffflow.domain.entity;

import com.staffflow.domain.enums.TipoAusencia;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Planificación anticipada de una ausencia o festivo.
 * Un registro por día: el proceso nocturno (@Scheduled, 23:55h)
 * consulta WHERE fecha = HOY AND procesado = FALSE y convierte
 * cada registro en un {@link Fichaje} del tipo correspondiente (RF-26).
 * Si empleado = NULL el registro es un festivo global que aplica
 * a todos los empleados activos (RF-26, decisión nº7).
 * Solo se permite DELETE si procesado = FALSE (decisión nº2).
 *
 * @author Santiago Castillo
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "planificacion_ausencias",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"empleado_id", "fecha"}  // 1 ausencia por empleado por día (NULL tratado como valor distinto en MySQL)
        ),
        indexes = @Index(name = "idx_planificacion_fecha_procesado", columnList = "fecha, procesado")
        // Índice crítico: proceso nocturno → WHERE fecha = HOY AND procesado = FALSE
)
public class PlanificacionAusencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NULL = festivo global: aplica a todos los empleados activos (RF-26).
    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = true)
    private Empleado empleado;

    // Un registro por día. El modelo de rango (fechaInicio/fechaFin)
    // fue descartado por complicar innecesariamente el proceso nocturno.
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ausencia", nullable = false, length = 25)
    private TipoAusencia tipoAusencia;

    // false = pendiente de procesar. true = fichaje ya generado por @Scheduled.
    // El proceso nocturno solo actúa sobre registros con procesado = false.
    @Column(name = "procesado", nullable = false)
    private Boolean procesado = false;

    // Auditoría: usuario que planificó la ausencia (RNF-L01).
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
    }
}