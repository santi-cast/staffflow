package com.staffflow.domain.entity;

import com.staffflow.domain.enums.Rol;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Usuario del sistema con acceso a la aplicación móvil y/o web.
 * Todo empleado tiene un usuario asociado, pero no todo usuario
 * tiene un empleado (p.ej. un ADMIN sin ficha de empleado).
 * El rol determina los permisos de acceso (RF-01, RF-02).
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

    // Baja lógica: activo=false. Nunca DELETE físico.
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Token opaco para el flujo de recuperación de contraseña.
     *
     * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
     * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
     * descrito a continuación pertenece al andamiaje reservado para v2.0
     * (ver memoria TFG, bloque B10 Vías Futuras → Reset password con token UUID).</p>
     *
     * <p>Estado real en v1: este campo permanece siempre {@code null}. Ningún
     * flujo de v1 lo escribe; E04 entrega la contraseña temporal directamente
     * por email sin tocar este campo.</p>
     *
     * <p>Rol previsto en v2.0: se rellenaría al solicitar recuperación de
     * contraseña con un UUID generado por el servicio y se invalidaría tras
     * el primer uso (RNF-S04).</p>
     */
    @Column(name = "reset_token", length = 255)
    private String resetToken;

    /**
     * Fecha y hora de caducidad del token de recuperación.
     *
     * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
     * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
     * descrito a continuación pertenece al andamiaje reservado para v2.0
     * (ver memoria TFG, bloque B10 Vías Futuras → Reset password con token UUID).</p>
     *
     * <p>Estado real en v1: este campo permanece siempre {@code null}. Ningún
     * flujo de v1 lo escribe.</p>
     *
     * <p>Rol previsto en v2.0 (RF-44): token de un solo uso con validez de
     * 30 minutos; este campo guarda el instante de expiración para que E05
     * pueda rechazar tokens caducados.</p>
     */
    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
    }
}