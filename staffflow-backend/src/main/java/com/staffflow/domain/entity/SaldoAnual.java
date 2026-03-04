package com.staffflow.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "saldos_anuales",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"empleado_id", "anio"}
        )
)
public class SaldoAnual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    private Integer diasVacacionesTotales;

    @Column(nullable = false)
    private Integer diasVacacionesUsados;

    @Column(nullable = false)
    private Integer minutosTrabaladosTotal;

    @Column(nullable = false)
    private Integer minutosEsperadosTotal;

    @Column(nullable = false)
    private Integer minutosDevengados;
}