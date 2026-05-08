package com.staffflow.domain.entity;

import com.staffflow.domain.enums.CategoriaEmpleado;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Ficha del empleado vinculada a un {@link Usuario} del sistema.
 * Contiene los datos contractuales y operativos necesarios para
 * el cálculo de jornada, vacaciones y asuntos propios (RF-35, RF-36).
 * La categoría es informativa y no afecta a los permisos (decisión nº19).
 *
 * @author Santiago Castillo
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "empleados")
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación 1:1 obligatoria. Un empleado siempre tiene usuario asociado.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "apellido1", nullable = false, length = 50)
    private String apellido1;

    @Column(name = "apellido2", length = 50)
    private String apellido2;

    @Column(name = "dni", nullable = false, unique = true, length = 20)
    private String dni;

    // Renombrado desde nss (v1.0). Identificador interno de empleado.
    // Se usa en cabeceras de PDFs firmables (RF-40). Nunca expone datos
    // de seguridad social. Pendiente de gestión desde UI en v2.0.
    @Column(name = "numero_empleado", nullable = false, unique = true, length = 20)
    private String numeroEmpleado;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta;

    // Informativa: no determina permisos ni acceso (decisión nº19).
    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 20)
    private CategoriaEmpleado categoria;

    // Horas semanales contractuales. Base para el cálculo de saldo de horas.
    // Double para soportar jornadas parciales con decimales (ej: 37.5h).
    @Column(name = "jornada_semanal_horas", nullable = false)
    private Double jornadaSemanalHoras = 40.0;

    // Minutos de jornada diaria esperada. Usado en el cálculo de jornada efectiva.
    @Column(name = "jornada_diaria_minutos", nullable = false)
    private Integer jornadaDiariaMinutos = 480;

    @Column(name = "dias_vacaciones_anuales", nullable = false)
    private Integer diasVacacionesAnuales = 22;

    @Column(name = "dias_asuntos_propios_anuales", nullable = false)
    private Integer diasAsuntosPropiosAnuales = 3;

    // PIN de 4 dígitos para fichar desde el terminal compartido (decisión nº21).
    // No se expone en ningún DTO response (seguridad).
    // CHAR(4) fijo porque el PIN siempre tiene exactamente 4 dígitos.
    @Column(name = "pin_terminal", nullable = false, unique = true, length = 4, columnDefinition = "CHAR(4)")
    private String pinTerminal;

    // Alternativa al PIN para fichaje por NFC (RF-48). Opcional.
    @Column(name = "codigo_nfc", unique = true, length = 50)
    private String codigoNfc;

    // Baja lógica: activo=false. Nunca DELETE físico (decisión nº4).
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
