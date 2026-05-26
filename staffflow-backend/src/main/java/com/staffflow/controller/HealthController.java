package com.staffflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller de comprobacion de estado del servidor.
 *
 * <p>Expone un unico endpoint publico (sin JWT) para verificar que
 * la aplicacion esta en ejecucion y responde correctamente. Usado
 * por herramientas de monitorizacion, Docker health checks y el
 * evaluador para confirmar que el backend arranco correctamente
 * antes de ejecutar las pruebas.</p>
 *
 * <p>Ruta base: /api — fuera de /api/v1 de forma intencionada.
 * El health check es independiente de la version de la API: una
 * herramienta de monitorizacion no debe cambiar su URL cuando la
 * API evoluciona de v1 a v2.</p>
 *
 * <p>Seguridad: ruta publica declarada en SecurityConfig como
 * permitAll(). No requiere JWT ni PIN.</p>
 *
 * <p>Endpoint tecnico de monitorizacion: no esta vinculado a ningun
 * requisito funcional del catalogo (RF-01 a RF-54 cubren funciones
 * de los actores ADMIN, ENCARGADO y EMPLEADO; el health check es
 * infraestructura operativa).</p>
 *
 * @author Santiago Castillo
 */
@Tag(name = "Health", description = "Comprobacion de estado del servidor (E56)")
@RestController
@RequestMapping("/api")
public class HealthController {

    // E56 — GET /api/health
    // Endpoint tecnico de monitorizacion (no asociado a ningun RF del catalogo)

    /**
     * Devuelve el estado actual del servidor (E56).
     *
     * <p>Ruta publica: no requiere autenticacion. Devuelve siempre
     * HTTP 200 con {"status": "UP"} mientras la aplicacion este en
     * ejecucion. Si el servidor no responde o devuelve un codigo
     * distinto de 200, la herramienta de monitorizacion lo interpreta
     * como caida del servicio.</p>
     *
     * <p>Uso en Docker: el archivo docker-compose.yml puede usar esta
     * ruta como healthcheck para determinar si el contenedor esta listo
     * antes de enrutar trafico hacia el.</p>
     *
     * <p>Codigos HTTP:
     * <ul>
     *   <li>200 OK → servidor en ejecucion, {"status": "UP"}</li>
     * </ul></p>
     *
     * @return 200 OK con mapa {"status": "UP"}
     */
    @Operation(
            summary = "Estado del servidor",
            description = "Endpoint publico. Sin JWT. Devuelve {\"status\": \"UP\"} si el servidor esta en ejecucion."
    )
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
