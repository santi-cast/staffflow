package com.staffflow.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Datos para crear o actualizar la configuración global de la empresa.
 * Usado en E06 (POST /api/v1/empresa) y E07 (PUT /api/v1/empresa).
 * Solo accesible por ADMIN (decisión nº14). La tabla es singleton
 * (id = 1 siempre): E06 solo se ejecuta una vez en el alta inicial
 * del sistema (RF-06).
 *
 * @author Santiago Castillo
 */
@Data
public class EmpresaRequest {

    // Nombre fiscal de la empresa. Aparece en cabeceras de informes (RF-38).
    @NotBlank
    @Size(max = 150)
    private String nombreEmpresa;

    @NotBlank
    @Size(max = 20)
    private String cif;

    @Size(max = 255)
    private String direccion;

    // @Email valida formato RFC 5322.
    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 20)
    private String telefono;

    // Ruta relativa al logo en el servidor. Se incluye en PDFs firmables (RF-40).
    // Opcional: si es null no se muestra logo en los documentos.
    @Size(max = 255)
    private String logoPath;
}