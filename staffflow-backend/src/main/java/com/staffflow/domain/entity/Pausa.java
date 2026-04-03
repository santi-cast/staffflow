package com.staffflow.domain.entity;

import com.staffflow.domain.enums.TipoPausa;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de una pausa dentro de la jornada laboral de un empleado.
 * Una pausa activa se identifica por horaFin = NULL (E50, E51).
 * Al cerrar la pausa se calcula duracionMinutos con Math.floor,
 * redondeando a la baja para beneficiar al empleado.
 * Sin DELETE físico (RNF-L01, decisión nº1).
 *
 * @author Santiago Castillo
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pausas",
        indexes = @Index(name = "idx_pausas_empleado_fecha", columnList = "empleado_id, fecha")
)
public class Pausa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalDateTime horaInicio;

    // NULL = pausa activa en curso. Se rellena al finalizar la pausa (E51).
    // Crítico para el terminal: detecta pausa activa con WHERE hora_fin IS NULL.
    @Column(name = "hora_fin")
    private LocalDateTime horaFin;

    // NULL hasta cerrar la pausa. Math.floor al calcular (beneficia al empleado).
    // Se usa para actualizar totalPausasMinutos en Fichaje (E28).
    @Column(name = "duracion_minutos")
    private Integer duracionMinutos;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pausa", nullable = false, length = 20)
    private TipoPausa tipoPausa;

    // Auditoría: usuario que registró la pausa (RNF-L01).
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