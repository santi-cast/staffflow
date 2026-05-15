package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta del parte diario completo de presencia (E35).
 * Devuelve los contadores globales del día más el detalle
 * individual de cada empleado activo.
 *
 * Usado en E35 (GET /api/v1/presencia/parte-diario).
 * Accesible por ADMIN y ENCARGADO (RF-30).
 *
 * Los contadores globales (fichados, enPausa, ausencias, sinJustificar)
 * permiten al encargado ver el resumen del día de un vistazo.
 * El array detalle[] contiene la fila individual de cada empleado
 * activo, calculada en tiempo real sin persistir en BD.
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParteDiarioResponse {

    // Fecha del parte. Por defecto hoy (?fecha no informado).
    // Permite consultar partes históricos pasando ?fecha=YYYY-MM-DD.
    private LocalDate fecha;

    // Número total de empleados activos en el sistema.
    // Base de referencia para los contadores siguientes.
    private Integer totalEmpleados;

    // Empleados con entrada registrada y sin salida (JORNADA_INICIADA + EN_PAUSA).
    // No incluye JORNADA_COMPLETADA: esos ya terminaron su jornada.
    private Integer trabajando;

    // Empleados con estado EN_PAUSA en este momento.
    // Subconjunto de trabajando: un empleado en pausa tiene entrada sin salida.
    private Integer enPausa;

    // Empleados con estado AUSENCIA_REGISTRADA o AUSENCIA_PLANIFICADA.
    // Son ausencias conocidas: no requieren atención inmediata del encargado.
    private Integer ausencias;

    // Empleados con estado SIN_JUSTIFICAR.
    // Requieren atención inmediada: no tienen fichaje ni ausencia registrada.
    // Es el contador más relevante para el encargado (RF-31).
    private Integer sinJustificar;

    // Empleados con estado JORNADA_COMPLETADA (entrada y salida registradas).
    // Subconjunto de fichados que ya han terminado su jornada.
    private Integer jornadaCompletada;

    // Detalle individual de cada empleado activo.
    // Ordenado por apellido1, apellido2, nombre para facilitar la lectura.
    private List<DetallePresenciaResponse> detalle;
}
