package com.staffflow.domain.entity;

import com.staffflow.domain.enums.Rol;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Usuario del sistema con acceso a la aplicación móvil y/o web.
 * Todo empleado tiene un usuario asociado, pero no todo usuario
 * tiene un empleado (p.ej. un ADMIN sin ficha de empleado).
 * El rol determina los permisos de acceso (RF-01, RF-02, decisión nº14).
 *
 * @author Santiago Castillo
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuarios",
        indexes = {
                @Index(name = "idx_usuarios_activo", columnList = "activo"),
                @Index(name = "idx_usuarios_reset_token", columnList = "reset_token")
        }
)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    // Almacenado como hash BCrypt. Nunca en claro (RNF-S01).
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    // Baja lógica: activo=false. Nunca DELETE físico (decisión nº4).
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    // NULL en condiciones normales. Se rellena al solicitar recuperación
    // de contraseña y se invalida tras el primer uso (RNF-S04).
    @Column(name = "reset_token", length = 255)
    private String resetToken;

    // Token de un solo uso con validez de 30 minutos (RF-44, RF-45).
    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
    }
}