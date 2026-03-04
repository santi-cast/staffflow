package com.staffflow.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "empleados")
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(nullable = false, unique = true, length = 9)
    private String dni;

    @Column(nullable = false, unique = true, length = 12)
    private String nss;

    @Column(nullable = false, unique = true, length = 6)
    private String pinTerminal;

    @Column(nullable = false)
    private Boolean activo = true;
}