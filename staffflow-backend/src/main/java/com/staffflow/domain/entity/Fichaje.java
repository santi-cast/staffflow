package com.staffflow.domain.entity;

import com.staffflow.domain.enums.TipoFichaje;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fichajes",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"empleado_id", "fecha", "tipo"}
        )
)
public class Fichaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @ManyToOne
    @JoinColumn(name = "registrado_por", nullable = false)
    private Usuario registradoPor;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoFichaje tipo;

    @Column(nullable = false)
    private Boolean manual = false;

    @Column(length = 255)
    private String observaciones;
}