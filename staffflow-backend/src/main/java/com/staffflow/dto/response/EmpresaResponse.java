package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representación de la configuración global de la empresa.
 * Usado en E06 (GET /api/v1/empresa) y E07 (PUT /api/v1/empresa).
 * Solo accesible por ADMIN (decisión nº14).
 *
 * La tabla configuracion_empresa es singleton (id = 1 siempre).
 * Los datos de esta respuesta aparecen en cabeceras de informes
 * operativos y PDFs firmables (RF-38, RF-39, RF-40).
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaResponse {

    // Siempre 1 al ser tabla singleton. Se incluye por consistencia
    // con el resto de responses y para facilitar el debugging.
    private Long id;

    private String nombreEmpresa;

    private String cif;

    private String direccion;

    private String email;

    private String telefono;

    // Ruta relativa al logo en el servidor. NULL si no se ha configurado.
    // Se incluye en la cabecera de los PDFs firmables (RF-40).
    private String logoPath;
}