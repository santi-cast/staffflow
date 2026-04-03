package com.staffflow.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Configuración global de la empresa. Tabla singleton (id = 1 siempre).
 * Contiene los datos identificativos y de contacto que aparecen
 * en cabeceras de informes y PDFs firmables (RF-38, RF-39, RF-40).
 *
 * @author Santiago Castillo
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "configuracion_empresa")
public class ConfiguracionEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_empresa", nullable = false, length = 100)
    private String nombreEmpresa;

    @Column(name = "cif", unique = true, nullable = false, length = 20)
    private String cif;

    @Column(name = "direccion", columnDefinition = "TEXT")
    private String direccion;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "telefono", length = 20)
    private String telefono;

    // Ruta relativa al logo almacenado en el servidor.
    // Se incluye en la cabecera de los PDFs firmables (RF-40).
    @Column(name = "logo_path", length = 255)
    private String logoPath;
}