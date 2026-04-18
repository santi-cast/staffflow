package com.staffflow.exception;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del GlobalExceptionHandler via MockMvc.
 *
 * Verifica que cada tipo de excepcion se mapea al codigo HTTP correcto
 * y que el body sigue el formato del contrato: { error, timestamp, path }.
 *
 * Usa un TestController interno que lanza las excepciones a peticion.
 * Se excluye SecurityAutoConfiguration para que el test sea rapido y
 * no dependa de la config JWT (lo que se testea aqui es el mapeo de
 * excepciones, no la seguridad).
 *
 * Casos criticos cubiertos:
 *   - IllegalStateException         → 404 (recurso no encontrado)
 *   - EntityNotFoundException       → 404 (fix del bug E52 — PIN invalido 500 → 404)
 *   - ConflictException             → 409 (unicidad de dominio)
 *   - PinBloqueadoException         → 423 (bloqueo terminal RNF-S05)
 *   - IllegalArgumentException      → 400 (error de negocio)
 *   - Exception (no controlada)     → 500 (manejador de ultimo recurso, sin detalles internos)
 *
 * @author Santiago Castillo
 */
@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.TestController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@DisplayName("GlobalExceptionHandler — mapeo de excepciones a HTTP")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    // ---------------------------------------------------------------
    // 400 BAD REQUEST
    // ---------------------------------------------------------------

    @Test
    @DisplayName("IllegalArgumentException → 400 con mensaje en campo 'error'")
    void illegalArgumentException_devuelve400() throws Exception {
        mockMvc.perform(get("/test/illegal-argument")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dato invalido de prueba"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/illegal-argument"));
    }

    // ---------------------------------------------------------------
    // 404 NOT FOUND
    // ---------------------------------------------------------------

    @Test
    @DisplayName("IllegalStateException → 404 (recurso no encontrado)")
    void illegalStateException_devuelve404() throws Exception {
        mockMvc.perform(get("/test/illegal-state")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso no encontrado de prueba"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("EntityNotFoundException → 404 (fix: E52 PIN invalido ya no devuelve 500)")
    void entityNotFoundException_devuelve404() throws Exception {
        mockMvc.perform(get("/test/entity-not-found")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Entidad no encontrada de prueba"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------------------------------------------------------------
    // 409 CONFLICT
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ConflictException → 409 con mensaje del conflicto")
    void conflictException_devuelve409() throws Exception {
        mockMvc.perform(get("/test/conflict")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflicto de unicidad de prueba"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------------------------------------------------------------
    // 423 LOCKED
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PinBloqueadoException → 423 (bloqueo terminal RNF-S05)")
    void pinBloqueadoException_devuelve423() throws Exception {
        mockMvc.perform(get("/test/pin-bloqueado")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("Dispositivo bloqueado de prueba"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------------------------------------------------------------
    // 500 INTERNAL SERVER ERROR
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Exception no controlada → 500 con mensaje generico (no expone detalles internos)")
    void exceptionNoControlada_devuelve500ConMensajeGenerico() throws Exception {
        mockMvc.perform(get("/test/error-generico")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error interno del servidor"));
    }

    // ---------------------------------------------------------------
    // Formato del body
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Todos los errores incluyen timestamp y path en el body")
    void respuestaError_siempreIncluyeTimestampYPath() throws Exception {
        mockMvc.perform(get("/test/conflict")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists());
    }

    // ---------------------------------------------------------------
    // TestController interno — lanza excepciones a peticion
    // ---------------------------------------------------------------

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/illegal-argument")
        public void illegalArgument() {
            throw new IllegalArgumentException("Dato invalido de prueba");
        }

        @GetMapping("/illegal-state")
        public void illegalState() {
            throw new IllegalStateException("Recurso no encontrado de prueba");
        }

        @GetMapping("/entity-not-found")
        public void entityNotFound() {
            throw new EntityNotFoundException("Entidad no encontrada de prueba");
        }

        @GetMapping("/conflict")
        public void conflict() {
            throw new ConflictException("Conflicto de unicidad de prueba");
        }

        @GetMapping("/pin-bloqueado")
        public void pinBloqueado() {
            throw new PinBloqueadoException("Dispositivo bloqueado de prueba");
        }

        @GetMapping("/error-generico")
        public void errorGenerico() throws Exception {
            throw new Exception("Error interno que no debe exponerse al cliente");
        }
    }
}
