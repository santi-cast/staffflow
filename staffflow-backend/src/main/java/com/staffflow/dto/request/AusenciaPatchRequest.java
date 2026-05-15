package com.staffflow.dto.request;

import com.staffflow.domain.enums.TipoAusencia;
import lombok.Data;

/**
 * Campos modificables de una ausencia planificada mediante actualización parcial.
 * Usado en E31 (PATCH /api/v1/ausencias/{id}), accesible por ADMIN y ENCARGADO.
 * Todos los campos son opcionales: el servicio solo actualiza los que
 * lleguen con valor no null (patrón PATCH).
 * Solo se permite modificar si procesado = false: una ausencia ya
 * procesada ha generado un fichaje y modificarla violaría la
 * trazabilidad (RNF-L01).
 * empleadoId y fecha no son modificables por este endpoint.
 *
 * @author Santiago Castillo
 */
@Data
public class AusenciaPatchRequest {

    // Cambio de tipo antes de que el proceso nocturno procese el registro.
    // Solo posible si procesado = false.
    private TipoAusencia tipoAusencia;

    private String observaciones;
}