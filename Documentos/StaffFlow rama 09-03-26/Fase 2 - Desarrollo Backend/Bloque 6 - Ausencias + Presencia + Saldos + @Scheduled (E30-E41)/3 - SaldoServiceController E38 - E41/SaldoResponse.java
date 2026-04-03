package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Respuesta del saldo anual completo de un empleado.
 *
 * <p>Usado por E38 (GET /saldos/{empleadoId}), E39 (GET /saldos),
 * E40 (POST /saldos/{empleadoId}/recalcular) y E41 (GET /saldos/me).
 * Los cuatro endpoints devuelven exactamente el mismo desglose.</p>
 *
 * <p>El desglose se organiza en tres bloques anidados para separar
 * responsabilidades y facilitar la lectura en el cliente Android:
 * vacaciones, asuntos propios y horas. Cada bloque es una clase
 * estática interna: solo se usan dentro de SaldoResponse y no
 * justifican ficheros separados.</p>
 *
 * <p>Calculo de horas:
 *   esperadas  = diasTrabajados * jornadaDiariaMinutos / 60.0
 *   trabajadas = esperadas + saldoHoras
 * No se necesita query adicional a FichajeRepository porque saldoHoras
 * ya acumula la diferencia real fichada por el proceso nocturno.</p>
 *
 * @author Santiago Castillo
 */
@Data
@AllArgsConstructor
public class SaldoResponse {

    /** ID del empleado al que pertenece este saldo. */
    private Long empleadoId;

    /** Año al que corresponde el saldo (ej: 2026). */
    private Integer anio;

    /** Desglose completo de vacaciones. */
    private VacacionesDesglose vacaciones;

    /** Desglose completo de asuntos propios. */
    private AsuntosPropiosDesglose asuntosPropios;

    /** Desglose de horas trabajadas, esperadas y saldo. */
    private HorasDesglose horas;

    /**
     * Ultimo dia procesado por el proceso nocturno (@Scheduled).
     * Indica hasta que fecha estan actualizados los datos.
     * Puede ser null si el proceso aun no ha actuado sobre este registro.
     */
    private LocalDate calculadoHastaFecha;

    // ----------------------------------------------------------------
    // Clases internas de desglose
    // ----------------------------------------------------------------

    /**
     * Desglose de dias de vacaciones del empleado para el año.
     *
     * <p>La formula de disponibles es:
     *   disponibles = derechoAnio + pendientesAnterior - consumidos
     * El campo pendientesAnterior se arrastra desde el cierre del año
     * anterior (decision n12).</p>
     */
    @Data
    @AllArgsConstructor
    public static class VacacionesDesglose {

        /**
         * Dias de vacaciones que corresponden por convenio este año.
         * Viene de Empleado.diasVacacionesAnuales.
         */
        private Integer derechoAnio;

        /**
         * Dias de vacaciones no disfrutados del año anterior
         * y trasladados a este. Los genera el cierre anual (decision n12).
         */
        private Integer pendientesAnterior;

        /** Dias de vacaciones ya consumidos este año. */
        private Integer consumidos;

        /**
         * Dias de vacaciones disponibles para disfrutar.
         * derechoAnio + pendientesAnterior - consumidos (RF-35).
         */
        private Integer disponibles;
    }

    /**
     * Desglose de dias de asuntos propios del empleado para el año.
     *
     * <p>Misma estructura que VacacionesDesglose pero para asuntos propios.
     * La formula de disponibles es identica:
     *   disponibles = derechoAnio + pendientesAnterior - consumidos</p>
     */
    @Data
    @AllArgsConstructor
    public static class AsuntosPropiosDesglose {

        /**
         * Dias de asuntos propios que corresponden por convenio este año.
         * Viene de Empleado.diasAsuntosPropiosAnuales.
         */
        private Integer derechoAnio;

        /**
         * Dias de asuntos propios no disfrutados del año anterior
         * y trasladados a este. Los genera el cierre anual (decision n12).
         */
        private Integer pendientesAnterior;

        /** Dias de asuntos propios ya consumidos este año. */
        private Integer consumidos;

        /**
         * Dias de asuntos propios disponibles para disfrutar.
         * derechoAnio + pendientesAnterior - consumidos (RF-36).
         */
        private Integer disponibles;
    }

    /**
     * Desglose de horas del empleado para el año.
     *
     * <p>trabajadas y esperadas se calculan en SaldoService a partir
     * de los datos persistidos en SaldoAnual y Empleado, sin query
     * adicional a FichajeRepository:
     *   esperadas  = diasTrabajados * jornadaDiariaMinutos / 60.0
     *   trabajadas = esperadas + saldoHoras
     * BigDecimal para mantener la precision decimal del calculo (decision n22).</p>
     */
    @Data
    @AllArgsConstructor
    public static class HorasDesglose {

        /**
         * Horas que el empleado deberia haber trabajado este año segun contrato.
         * diasTrabajados * jornadaDiariaMinutos / 60.0
         */
        private BigDecimal esperadas;

        /**
         * Horas reales trabajadas este año segun los fichajes procesados.
         * esperadas + saldoHoras
         */
        private BigDecimal trabajadas;

        /**
         * Diferencia entre horas trabajadas y esperadas.
         * Positivo = horas extra acumuladas. Negativo = deficit de jornada.
         * Persistido en SaldoAnual.saldoHoras y actualizado por el proceso nocturno.
         */
        private BigDecimal saldoHoras;
    }
}
