package com.staffflow.dto.request;

import com.staffflow.domain.enums.TipoFichaje;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Datos para registrar manualmente un fichaje de un empleado.
 * Usado en E22 (POST /api/v1/fichajes), accesible por ADMIN y ENCARGADO.
 * El fichaje manual lo crea un responsable, no el empleado directamente:
 * para el fichaje desde terminal se usa {@link TerminalPinRequest} (decisión nº8).
 * Un empleado solo puede tener un fichaje por día (RNF-I02, RD-ley 8/2019).
 *
 * @author Santiago Castillo
 */
@Data
public class FichajeRequest {

    @NotNull
    private Long empleadoId;

    @NotNull
    private LocalDate fecha;

    // Determina cómo se contabiliza el día en SaldoAnual (RF-35, RF-36).
    @NotNull
    private TipoFichaje tipo;

    // nullable: ausencias planificadas generan fichaje sin hora de entrada
    // (decisión nº7). También puede llegar sin hora si se registra a posteriori.
    private LocalDateTime horaEntrada;

    // nullable: fichaje abierto = jornada en curso, o ausencia sin horas.
    private LocalDateTime horaSalida;

    private String observaciones;
}