package com.staffflow.dto.request;

import com.staffflow.domain.enums.TipoFichaje;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO request para modificar un fichaje existente (E23 — PATCH /api/v1/fichajes/{id}).
 *
 * Todos los campos son opcionales: solo se actualizan en BD los que llegan
 * con valor no nulo (patrón PATCH del proyecto).
 *
 * EXCEPCIÓN: observaciones es obligatorio aunque sea un PATCH.
 * RNF-L02 exige que toda modificación de un fichaje deje constancia
 * del motivo. La validación la realiza FichajeService, no @NotBlank aquí,
 * porque @NotBlank impediría enviar null para los campos realmente opcionales.
 *
 * E23 permite a ENCARGADO o ADMIN modificar horaEntrada, horaSalida y tipo
 * como corrección manual además de observaciones. El service recalcula
 * jornadaEfectivaMinutos si cambian las horas, usando el totalPausasMinutos
 * ya almacenado en BD (E23 no toca pausas).
 *
 * Campos modificables:
 *   - tipo           → tipo de jornada
 *   - horaEntrada    → hora de entrada del empleado
 *   - horaSalida     → hora de salida del empleado
 *   - observaciones  → notas obligatorias para trazabilidad (RNF-L02)
 *
 * Campos NO modificables (no presentes en este DTO):
 *   - empleadoId     → la vinculación empleado-fichaje es permanente
 *   - fecha          → la fecha del fichaje identifica el registro (UNIQUE)
 *   - totalPausasMinutos     → gestionado por PausaService
 *   - jornadaEfectivaMinutos → recalculado por FichajeService al modificar horas
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichajePatchRequest {

    /**
     * Nuevo tipo de jornada para este fichaje.
     * Opcional — si null, el tipo actual en BD se mantiene.
     * Ejemplo: corregir un NORMAL por FESTIVO_LOCAL.
     */
    private TipoFichaje tipo;

    /**
     * Nueva hora de entrada del empleado.
     * Opcional — si null, la hora actual en BD se mantiene.
     * Si se modifica junto a horaSalida, el service recalcula jornadaEfectivaMinutos.
     * Formato ISO: 2026-03-13T08:00:00
     */
    private LocalDateTime horaEntrada;

    /**
     * Nueva hora de salida del empleado.
     * Opcional — si null, la hora actual en BD se mantiene.
     * Si se modifica junto a horaEntrada, el service recalcula jornadaEfectivaMinutos.
     * Formato ISO: 2026-03-13T17:00:00
     */
    private LocalDateTime horaSalida;

    /**
     * Observaciones sobre la modificación del fichaje.
     * Obligatorio aunque sea PATCH (RNF-L02): toda modificación manual
     * debe dejar constancia del motivo para cumplir el RD-ley 8/2019.
     * La validación se realiza en FichajeService (no @NotBlank aquí).
     * Ejemplo: "Corrección de horario por error en terminal del día 13/03"
     */
    private String observaciones;
}
