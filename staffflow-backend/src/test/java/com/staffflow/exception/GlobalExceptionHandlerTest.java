package com.staffflow.exception;

import jakarta.persistence.EntityNotFoundException;
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
 * Tests del {@link GlobalExceptionHandler}: mapeo de excepciones a códigos HTTP
 * y formato del body de error (contrato { error, timestamp, path }).
 *
 * <p>Usa MockMvc en modo standalone (no levanta el contexto de Spring) para
 * sortear la deuda M-036: el wiring JWT está roto en {@code @WebMvcTest} y
 * {@code @SpringBootTest}. Standalone solo registra el handler con un
 * controlador interno que lanza excepciones a petición.
 *
 * <p>Casos cubiertos:
 * <ul>
 *   <li>{@link IllegalArgumentException}  → 400 (error de negocio)</li>
 *   <li>{@link NotFoundException}         → 404 (recurso no encontrado, dos mensajes distintos)</li>
 *   <li>{@link EntityNotFoundException}   → 404 (fix bug E52: PIN inválido ya no devuelve 500)</li>
 *   <li>{@link ConflictException}         → 409 (unicidad de dominio)</li>
 *   <li>{@link PinBloqueadoException}     → 423 (RNF-S05, bloqueo terminal)</li>
 *   <li>{@link IllegalStateException}     → 500 (handler específico eliminado en ISE-01;
 *                                              cae al genérico — ISE se reserva para fallos
 *                                              internos genuinos como iText7 en PdfService)</li>
 *   <li>{@link Exception} no controlada   → 500 (mensaje opaco, sin filtrar detalles internos)</li>
 *   <li>Formato del body                  → todos los errores incluyen timestamp y path</li>
 * </ul>
 *
 * @see GlobalExceptionHandler
 * @author Santiago Castillo
 */
@DisplayName("GlobalExceptionHandler — mapeo de excepciones a HTTP")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---------------------------------------------------------------
    // 400 BAD REQUEST
    // ---------------------------------------------------------------

    @Test
    @DisplayName("IllegalArgumentException → 400 con mensaje en campo 'error'")
    void illegalArgumentException_devuelve400() throws Exception {
        mockMvc.perform(get("/test-geh/illegal-argument")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dato invalido de prueba"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists());
    }

    // ---------------------------------------------------------------
    // 404 NOT FOUND — NotFoundException (custom)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("NotFoundException → 404 con mensaje del recurso ausente en campo 'error'")
    void notFoundException_devuelve404() throws Exception {
        mockMvc.perform(get("/test-geh/not-found")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Empleado no encontrado con id: 99"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists());
    }

    @Test
    @DisplayName("NotFoundException — triangulación: mensaje distinto también devuelve 404")
    void notFoundException_mensajeDistinto_devuelve404() throws Exception {
        mockMvc.perform(get("/test-geh/not-found-empresa")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Configuración de empresa no encontrada"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------------------------------------------------------------
    // 404 NOT FOUND — EntityNotFoundException (jakarta.persistence)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("EntityNotFoundException → 404 (fix: E52 PIN invalido ya no devuelve 500)")
    void entityNotFoundException_devuelve404() throws Exception {
        // jakarta.persistence.EntityNotFoundException la lanzan AuthService,
        // FichajeService, PausaService, InformeService y TerminalService al
        // resolver entidades que no existen via orElseThrow(). Antes caia al
        // handler generico y devolvia 500; ahora tiene su propio handler 404.
        mockMvc.perform(get("/test-geh/entity-not-found")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Entidad no encontrada de prueba"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------------------------------------------------------------
    // 409 CONFLICT
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ConflictException → 409 con mensaje del conflicto en campo 'error'")
    void conflictException_devuelve409() throws Exception {
        mockMvc.perform(get("/test-geh/conflict")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflicto de unicidad de prueba"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------------------------------------------------------------
    // 423 LOCKED — RNF-S05
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PinBloqueadoException → 423 (bloqueo terminal RNF-S05)")
    void pinBloqueadoException_devuelve423() throws Exception {
        // 423 Locked: el dispositivo supero los 5 intentos fallidos de PIN
        // y queda bloqueado hasta desbloqueo manual (E54), PIN exitoso o
        // reinicio del servidor. El bloqueo es por dispositivo, no por empleado.
        mockMvc.perform(get("/test-geh/pin-bloqueado")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("Dispositivo bloqueado de prueba"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------------------------------------------------------------
    // 500 INTERNAL SERVER ERROR — IllegalStateException (ISE-01)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("IllegalStateException → 500 (handler especifico eliminado en ISE-01)")
    void illegalStateException_devuelve500() throws Exception {
        // Tras ISE-01 (SDD backend-hardening-high-issues) el handler de
        // IllegalStateException fue eliminado: las ISE intencionales que
        // quedan (PdfService envolviendo fallos de iText7 en E20/E45/E46/
        // E47/E57) caen al handler generico → 500 con mensaje opaco al
        // cliente. ISE ya NO se usa para "no encontrado": para eso esta
        // NotFoundException.
        mockMvc.perform(get("/test-geh/illegal-state")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error interno del servidor"));
    }

    // ---------------------------------------------------------------
    // 500 INTERNAL SERVER ERROR — Exception (manejador de último recurso)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Exception no controlada → 500 con mensaje generico (no expone detalles internos)")
    void exceptionNoControlada_devuelve500ConMensajeGenerico() throws Exception {
        // El handler generico NO debe devolver el mensaje real de la
        // excepcion: ese podria contener nombres de clases, paths,
        // stack info o detalles de BD. Siempre devuelve "Error interno
        // del servidor" — el detalle queda en el log del servidor.
        mockMvc.perform(get("/test-geh/error-generico")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error interno del servidor"));
    }

    // ---------------------------------------------------------------
    // Formato del body — contrato API
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Todos los errores incluyen timestamp y path en el body")
    void respuestaError_siempreIncluyeTimestampYPath() throws Exception {
        mockMvc.perform(get("/test-geh/conflict")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists());
    }

    // ---------------------------------------------------------------
    // TestController interno — lanza excepciones a petición
    // ---------------------------------------------------------------

    @RestController
    @RequestMapping("/test-geh")
    static class TestController {

        @GetMapping("/illegal-argument")
        public void illegalArgument() {
            throw new IllegalArgumentException("Dato invalido de prueba");
        }

        @GetMapping("/not-found")
        public void notFound() {
            throw new NotFoundException("Empleado no encontrado con id: 99");
        }

        @GetMapping("/not-found-empresa")
        public void notFoundEmpresa() {
            throw new NotFoundException("Configuración de empresa no encontrada");
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

        @GetMapping("/illegal-state")
        public void illegalState() {
            throw new IllegalStateException("Error generando el informe PDF: iText7 falla");
        }

        @GetMapping("/error-generico")
        public void errorGenerico() throws Exception {
            throw new Exception("Error interno que no debe exponerse al cliente");
        }
    }
}
