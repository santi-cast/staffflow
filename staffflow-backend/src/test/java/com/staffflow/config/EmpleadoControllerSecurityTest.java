package com.staffflow.config;

import com.staffflow.controller.EmpleadoController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests estructurales de seguridad declarativa para EmpleadoController.
 *
 * <p>Verifica por reflexión sobre los bytecodes compilados que los endpoints
 * de gestión de empleados (E13-E18, parte diario, exportación, E65 y E68) están
 * protegidos con la expresión {@code @PreAuthorize} correspondiente.
 * Los endpoints de gestión operativa usan {@code @PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO')")}.
 * El endpoint E68 es excepción: usa {@code @PreAuthorize("hasRole('ADMIN')")}.
 *
 * <p>Cobertura:
 * <ul>
 *   <li>E13 POST /api/v1/empleados — {@code crear}</li>
 *   <li>E14 GET /api/v1/empleados — {@code listar}</li>
 *   <li>E15 GET /api/v1/empleados/{id} — {@code obtenerPorId}</li>
 *   <li>E16 PATCH /api/v1/empleados/{id} — {@code actualizar}</li>
 *   <li>E17 PATCH /api/v1/empleados/{id}/baja — {@code darDeBaja}</li>
 *   <li>E18 PATCH /api/v1/empleados/{id}/reactivar — {@code reactivar}</li>
 *   <li>GET /api/v1/empleados/estado — {@code obtenerEstado} (parte diario)</li>
 *   <li>GET /api/v1/empleados/export — {@code exportar}</li>
 *   <li>E65 POST /api/v1/empleados/{id}/regenerar-pin — {@code regenerarPin}</li>
 *   <li>E68 GET /api/v1/empleados/by-usuario/{usuarioId} — {@code obtenerPorUsuarioId} (SOLO ADMIN)</li>
 * </ul>
 *
 * <p>El endpoint {@code obtenerMiPerfil} (E33 GET /me) se excluye porque ya
 * está cubierto en {@link MethodSecurityConfigTest} con
 * {@code hasAnyRole('EMPLEADO','ENCARGADO')}.
 *
 * @author Santiago Castillo
 */
@DisplayName("Seguridad declarativa: EmpleadoController exige hasAnyRole('ADMIN','ENCARGADO')")
class EmpleadoControllerSecurityTest {

    private static final String EXPECTED_EXPR = "hasAnyRole('ADMIN', 'ENCARGADO')";

    @Test
    @DisplayName("E13 EmpleadoController#crear usa hasAnyRole('ADMIN', 'ENCARGADO')")
    void crear_exigeAdminOEncargado() {
        assertPreAuthorizeValue(EmpleadoController.class, "crear", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E14 EmpleadoController#listar usa hasAnyRole('ADMIN', 'ENCARGADO')")
    void listar_exigeAdminOEncargado() {
        assertPreAuthorizeValue(EmpleadoController.class, "listar", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E15 EmpleadoController#obtenerPorId usa hasAnyRole('ADMIN', 'ENCARGADO')")
    void obtenerPorId_exigeAdminOEncargado() {
        assertPreAuthorizeValue(EmpleadoController.class, "obtenerPorId", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E16 EmpleadoController#actualizar usa hasAnyRole('ADMIN', 'ENCARGADO')")
    void actualizar_exigeAdminOEncargado() {
        assertPreAuthorizeValue(EmpleadoController.class, "actualizar", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E17 EmpleadoController#darDeBaja usa hasAnyRole('ADMIN', 'ENCARGADO')")
    void darDeBaja_exigeAdminOEncargado() {
        assertPreAuthorizeValue(EmpleadoController.class, "darDeBaja", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E18 EmpleadoController#reactivar usa hasAnyRole('ADMIN', 'ENCARGADO')")
    void reactivar_exigeAdminOEncargado() {
        assertPreAuthorizeValue(EmpleadoController.class, "reactivar", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("EmpleadoController#obtenerEstado (parte diario) usa hasAnyRole('ADMIN', 'ENCARGADO')")
    void obtenerEstado_exigeAdminOEncargado() {
        assertPreAuthorizeValue(EmpleadoController.class, "obtenerEstado", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("EmpleadoController#exportar usa hasAnyRole('ADMIN', 'ENCARGADO')")
    void exportar_exigeAdminOEncargado() {
        assertPreAuthorizeValue(EmpleadoController.class, "exportar", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E65 EmpleadoController#regenerarPin usa hasAnyRole('ADMIN', 'ENCARGADO')")
    void regenerarPin_exigeAdminOEncargado() {
        assertPreAuthorizeValue(EmpleadoController.class, "regenerarPin", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E68 EmpleadoController#obtenerPorUsuarioId usa hasRole('ADMIN')")
    void obtenerPorUsuarioId_exigeSoloAdmin() {
        assertPreAuthorizeValue(EmpleadoController.class, "obtenerPorUsuarioId", "hasRole('ADMIN')");
    }

    // -----------------------------------------------------------------------
    // Triangulación: ningún endpoint de gestión abre la puerta a EMPLEADO solo.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Triangulación: ningún endpoint de EmpleadoController acepta hasRole('EMPLEADO') sin ENCARGADO")
    void ningunEndpointAceptaEmpleadoSolo() {
        long aperturasIndebidas = Arrays.stream(EmpleadoController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PreAuthorize.class))
                .map(m -> m.getAnnotation(PreAuthorize.class).value())
                .filter(v -> v.equals("hasRole('EMPLEADO')"))
                .count();
        assertThat(aperturasIndebidas)
                .as("EmpleadoController no debe tener hasRole('EMPLEADO') solo")
                .isZero();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Verifica que el primer método declarado con ese nombre en la clase tenga
     * el valor de @PreAuthorize esperado.
     */
    private void assertPreAuthorizeValue(Class<?> controller, String methodName, String expectedValue) {
        Optional<Method> metodo = Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst();

        assertThat(metodo)
                .as("Debe existir un metodo '%s' en %s", methodName, controller.getSimpleName())
                .isPresent();

        PreAuthorize annotation = metodo.get().getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("El metodo '%s' debe tener @PreAuthorize", methodName)
                .isNotNull();

        assertThat(annotation.value())
                .as("@PreAuthorize de '%s' debe ser '%s'", methodName, expectedValue)
                .isEqualTo(expectedValue);
    }
}
