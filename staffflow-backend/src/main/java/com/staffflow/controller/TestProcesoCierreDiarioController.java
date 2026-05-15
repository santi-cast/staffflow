package com.staffflow.controller;

import com.staffflow.service.scheduled.ProcesoCierreDiario;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller temporal SOLO para perfil dev.
 *
 * Permite disparar ProcesoCierreDiario manualmente desde Swagger o curl
 * sin esperar al cron de las 23:55. NO existe en produccion gracias
 * a @Profile("dev") — Spring no registra este bean con perfil mysql.
 *
 * ELIMINAR antes de la entrega final o dejar: @Profile("dev") lo
 * excluye completamente del perfil mysql, no afecta a produccion.
 */
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Profile("dev")
public class TestProcesoCierreDiarioController {

    private final ProcesoCierreDiario procesoCierreDiario;

    /**
     * Dispara el proceso nocturno manualmente.
     * Solo accesible con perfil dev activo.
     *
     * Con @EnableMethodSecurity activo, requiere autenticacion JWT como
     * cualquier otro endpoint no listado en SecurityConfig.permitAll().
     * En perfil dev cualquier usuario autenticado puede ejecutarlo (no hay
     * @PreAuthorize especifico). En perfil mysql el bean ni siquiera se
     * registra (@Profile("dev")).
     */
    @PostMapping("/cierre-diario")
    public ResponseEntity<String> ejecutar() {
        procesoCierreDiario.ejecutar();
        return ResponseEntity.ok("ProcesoCierreDiario ejecutado correctamente");
    }
}
