package com.staffflow.domain.entity;

import com.staffflow.domain.enums.TipoAusencia;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "planificacion_ausencias")
public class PlanificacionAusencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = true)
    private Empleado empleado;

    @ManyToOne
    @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAusencia tipoAusencia;

    @Column(nullable = false)
    private LocalDate fechaInicio;

    @Column(nullable = false)
    private LocalDate fechaFin;

    @Column(length = 255)
    private String observaciones;
}