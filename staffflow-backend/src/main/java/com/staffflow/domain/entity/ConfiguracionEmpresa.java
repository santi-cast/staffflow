package com.staffflow.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "configuracion_empresa")
public class ConfiguracionEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer horasJornadaDiaria;

    @Column(nullable = false)
    private Integer minutosToleranciaEntrada;

    @Column(nullable = false)
    private Integer minutosToleranciaFichaje;

    @Column(nullable = false)
    private Integer diasVacacionesAnuales;

    @Column(nullable = false)
    private Integer maxMinutosPausaDiaria;
}