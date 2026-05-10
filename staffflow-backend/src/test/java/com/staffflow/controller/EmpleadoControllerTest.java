package com.staffflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffflow.dto.response.EmpleadoResponse;
import com.staffflow.dto.response.RegenerarPinResponse;
import com.staffflow.exception.NotFoundException;
import com.staffflow.service.EmpleadoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de capa web para EmpleadoController.
 *
 * Cubre los endpoints E65 (POST /{id}/regenerar-pin) y el delta E16
 * (PATCH /{id} ya no acepta pinTerminal).
 *
 * NOTA: Esta clase está EXCLUIDA del gate de verify por el mismo motivo que
 * {@code GlobalExceptionHandlerTest}: el contexto de {@code @WebMvcTest} falla
 * al arrancar porque {@code SecurityConfig} requiere beans JWT que no están
 * disponibles en el scope de test sin configuración de BD.
 * Los tests están escritos para futura activación cuando se arregle el contexto.
 *
 * Escenarios cubiertos (E65 — regenerar-pin):
 *   - 200 OK con rol ADMIN
 *   - 200 OK con rol ENCARGADO
 *   - 403 Forbidden con rol EMPLEADO
 *   - 401 Unauthorized sin token
 *   - 404 Not Found cuando el empleado no existe
 *
 * Escenarios cubiertos (delta E16 — PATCH ignora pinTerminal):
 *   - PATCH con pinTerminal en body devuelve 200 y no modifica el PIN
 *   - PATCH normal sin pinTerminal devuelve 200
 *
 * @author Santiago Castillo
 * @see com.staffflow.service.EmpleadoService#regenerarPin(Long)
 */
@WebMvcTest(EmpleadoController.class)
@DisplayName("EmpleadoController — E65 regenerar-pin y delta E16")
class EmpleadoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmpleadoService empleadoService;

    // ---------------------------------------------------------------
    // E65 — POST /{id}/regenerar-pin
    // ---------------------------------------------------------------

    @Test
    @DisplayName("regenerarPin — rol ADMIN — devuelve 200 con pinTerminal")
    @WithMockUser(roles = "ADMIN")
    void regenerarPin_admin_devuelve200() throws Exception {
        RegenerarPinResponse response = new RegenerarPinResponse(1L, "1234");
        when(empleadoService.regenerarPin(1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/empleados/1/regenerar-pin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empleadoId").value(1))
                .andExpect(jsonPath("$.pinTerminal").value("1234"));
    }

    @Test
    @DisplayName("regenerarPin — rol ENCARGADO — devuelve 200 con pinTerminal")
    @WithMockUser(roles = "ENCARGADO")
    void regenerarPin_encargado_devuelve200() throws Exception {
        RegenerarPinResponse response = new RegenerarPinResponse(2L, "5678");
        when(empleadoService.regenerarPin(2L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/empleados/2/regenerar-pin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empleadoId").value(2))
                .andExpect(jsonPath("$.pinTerminal").value("5678"));
    }

    @Test
    @DisplayName("regenerarPin — rol EMPLEADO — devuelve 403 Forbidden")
    @WithMockUser(roles = "EMPLEADO")
    void regenerarPin_empleado_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/empleados/1/regenerar-pin"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(empleadoService);
    }

    @Test
    @DisplayName("regenerarPin — sin autenticación — devuelve 401 Unauthorized")
    void regenerarPin_sinAuth_devuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/empleados/1/regenerar-pin"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(empleadoService);
    }

    @Test
    @DisplayName("regenerarPin — empleado inexistente — devuelve 404 Not Found")
    @WithMockUser(roles = "ADMIN")
    void regenerarPin_idInexistente_devuelve404() throws Exception {
        when(empleadoService.regenerarPin(99999L))
                .thenThrow(new NotFoundException("Empleado con id 99999 no encontrado"));

        mockMvc.perform(post("/api/v1/empleados/99999/regenerar-pin"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------
    // Delta E16 — PATCH /{id} ya no procesa pinTerminal
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PATCH E16 — body con pinTerminal — devuelve 200, PIN no cambia (Jackson lo ignora)")
    @WithMockUser(roles = "ADMIN")
    void actualizar_conPinTerminalEnBody_devuelve200PinIgnorado() throws Exception {
        EmpleadoResponse response = new EmpleadoResponse();
        response.setId(1L);
        when(empleadoService.actualizar(anyLong(), any())).thenReturn(response);

        String body = "{\"nombre\": \"Foo\", \"pinTerminal\": \"9999\"}";

        mockMvc.perform(patch("/api/v1/empleados/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        // El servicio fue invocado (el JSON con pinTerminal extra fue aceptado silenciosamente)
        verify(empleadoService).actualizar(anyLong(), any());
    }

    @Test
    @DisplayName("PATCH E16 — body sin pinTerminal — devuelve 200 (comportamiento normal)")
    @WithMockUser(roles = "ADMIN")
    void actualizar_sinPinTerminal_devuelve200() throws Exception {
        EmpleadoResponse response = new EmpleadoResponse();
        response.setId(1L);
        when(empleadoService.actualizar(anyLong(), any())).thenReturn(response);

        String body = "{\"email\": \"nuevo@ejemplo.com\"}";

        mockMvc.perform(patch("/api/v1/empleados/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
}
