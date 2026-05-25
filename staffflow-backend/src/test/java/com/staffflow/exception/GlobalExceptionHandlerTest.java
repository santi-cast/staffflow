package com.staffflow.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del GlobalExceptionHandler para NotFoundException e IllegalStateException.
 *
 * <p>Verifica:
 * (a) {@link NotFoundException} → HTTP 404 con body {@code { error, timestamp, path }}
 * (b) {@link IllegalStateException} → HTTP 500 (estado interno inválido, no recurso ausente)
 *
 * <p>Usa MockMvc en modo standalone para no depender del contexto completo de Spring.
 * Esto elimina la necesidad de configurar JWT, datasource y mail para un test que
 * solo verifica el mapeo de excepciones a códigos HTTP.
 *
 * @see GlobalExceptionHandler
 * @author Santiago Castillo
 */
@DisplayName("GlobalExceptionHandler — NotFoundException y ISE")
class GlobalExceptionHandlerNotFoundTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---------------------------------------------------------------
    // (a) NotFoundException → 404
    // ---------------------------------------------------------------

    @Test
    @DisplayName("NotFoundException → 404 con mensaje del recurso ausente en campo 'error'")
    void notFoundException_devuelve404() throws Exception {
        mockMvc.perform(get("/test-nf/not-found")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Empleado no encontrado con id: 99"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists());
    }

    @Test
    @DisplayName("NotFoundException — triangulación: mensaje distinto también devuelve 404")
    void notFoundException_mensajeDistinto_devuelve404() throws Exception {
        mockMvc.perform(get("/test-nf/not-found-empresa")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Configuración de empresa no encontrada"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------------------------------------------------------------
    // (b) IllegalStateException → 500 (ya no es 404)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("IllegalStateException → 500 (estado interno inválido, no recurso ausente)")
    void illegalStateException_devuelve500() throws Exception {
        mockMvc.perform(get("/test-nf/illegal-state")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error interno del servidor"));
    }

    // ---------------------------------------------------------------
    // TestController interno — lanza excepciones a petición
    // ---------------------------------------------------------------

    @RestController
    @RequestMapping("/test-nf")
    static class TestController {

        @GetMapping("/not-found")
        public void notFound() {
            throw new NotFoundException("Empleado no encontrado con id: 99");
        }

        @GetMapping("/not-found-empresa")
        public void notFoundEmpresa() {
            throw new NotFoundException("Configuración de empresa no encontrada");
        }

        @GetMapping("/illegal-state")
        public void illegalState() {
            throw new IllegalStateException("Error generando el informe PDF: iText7 falla");
        }
    }
}
