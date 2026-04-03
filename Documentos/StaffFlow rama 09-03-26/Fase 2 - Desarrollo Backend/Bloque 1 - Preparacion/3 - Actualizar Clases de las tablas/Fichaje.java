package com.staffflow.domain.entity;

import com.staffflow.domain.enums.TipoFichaje;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro diario de jornada laboral de un empleado.
 * Un único fichaje por empleado por día (RNF-I02, RD-ley 8/2019).
 * Inmutable tras su creación: sin DELETE físico ni modificación
 * de datos de jornada una vez cerrada (RNF-L01, decisión nº1).
 *
 * @author Santiago Castillo
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fichajes",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"empleado_id", "fecha"}  // 1 fichaje por empleado por día (RNF-I02, RD-ley 8/2019)
        )
)
public class Fichaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoFichaje tipo;

    // nullable: ausencias planificadas generan fichaje sin hora de entrada (decisión nº7).
    @Column(name = "hora_entrada")
    private LocalDateTime horaEntrada;

    // nullable: fichaje abierto = jornada en curso.
    @Column(name = "hora_salida")
    private LocalDateTime horaSalida;

    // Suma de duracionMinutos de todas las pausas del día.
    // Se actualiza al cerrar cada pausa (E28).
    @Column(name = "total_pausas_minutos", nullable = false)
    private Integer totalPausasMinutos = 0;

    // (horaSalida - horaEntrada) - totalPausasMinutos. Math.floor (beneficia al empleado).
    @Column(name = "jornada_efectiva_minutos", nullable = false)
    private Integer jornadaEfectivaMinutos = 0;

    // Auditoría: usuario que registró o modificó el fichaje (RNF-L01).
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