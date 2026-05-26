package com.staffflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Bean unico de {@link Clock} consumido por los servicios que necesitan
 * resolver la fecha y hora actuales.
 *
 * <p>Inyectar {@code Clock} en lugar de llamar a {@code LocalDate.now()}
 * directamente permite sustituirlo por {@code Clock.fixed(...)} en los
 * tests y hacer deterministas las ramas que dependen del dia de la
 * semana o de la hora exacta. Consumidores actuales:</p>
 * <ul>
 *   <li>{@link com.staffflow.service.scheduled.ProcesoCierreDiario}: el
 *       proceso nocturno @Scheduled 23:55, cuyo comportamiento varia
 *       segun sea laborable o fin de semana.</li>
 *   <li>{@link com.staffflow.service.PausaService}: validacion de fechas
 *       futuras y restriccion del ENCARGADO al dia actual.</li>
 *   <li>{@link com.staffflow.service.FichajeService}: idem para los
 *       endpoints E22 (crear) y E23 (actualizar).</li>
 *   <li>{@link com.staffflow.service.AusenciaService}: idem para los
 *       endpoints E30 (crear) y E63 (crearRango).</li>
 *   <li>{@link com.staffflow.service.AuthService}: comparacion de
 *       resetTokenExpiry en E05 (restablecerPassword).</li>
 * </ul>
 *
 * <p>Regla del proyecto: NO propagar {@code Clock} proactivamente al resto
 * de services; agregarlo solo cuando un test concreto lo demande.</p>
 *
 * <p>Se fija explicitamente la zona horaria {@code Europe/Madrid}, que es
 * la zona operativa de la empresa. No depender del huso del servidor
 * evita sorpresas si el despliegue se mueve a otra region o si la JVM
 * arranca con {@code TZ} mal configurado: el proceso nocturno @Scheduled
 * 23:55 tiene que disparar a las 23:55 hora de Espana sin excepcion.</p>
 *
 * @author Santiago Castillo
 */
@Configuration
public class ClockConfig {

    /**
     * Reloj de sistema fijado a la zona horaria de la empresa
     * ({@code Europe/Madrid}).
     *
     * @return reloj sistema con la zona horaria operativa
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Madrid"));
    }
}
