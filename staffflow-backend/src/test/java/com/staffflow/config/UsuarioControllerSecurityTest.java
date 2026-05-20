package com.staffflow.config;

import com.staffflow.controller.UsuarioController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests estructurales de seguridad declarativa para UsuarioController.
 *
 * <p>Verifica por reflexión sobre los bytecodes compilados que los 6 endpoints
 * de gestión de usuarios (E08-E12, E66) están protegidos con
 * {@code @PreAuthorize("hasRole('ADMIN')")}. UsuarioController es exclusivo de
 * ADMIN: ningún método debe abrir su acceso a ENCARGADO o EMPLEADO.
 *
 * <p>Cobertura:
 * <ul>
 *   <li>E08 POST /api/v1/usuarios — {@code crear}</li>
 *   <li>E09 GET /api/v1/usuarios — {@code listar}</li>
 *   <li>E10 GET /api/v1/usuarios/{id} — {@code obtenerPorId}</li>
 *   <li>E11 PATCH /api/v1/usuarios/{id} — {@code actualizar}</li>
 *   <li>E12 DELETE /api/v1/usuarios/{id} — {@code desactivar}</li>
 *   <li>E66 PATCH /api/v1/usuarios/{id}/password — {@code resetearPassword}</li>
 * </ul>
 *
 * <p>El patrón es el mismo de {@link MethodSecurityConfigTest}: reflexión sin
 * arrancar Spring, asserción directa sobre el valor de {@code @PreAuthorize}.
 *
 * @author Santiago Castillo
 */
@DisplayName("Seguridad declarativa: UsuarioController exige hasRole('ADMIN')")
class UsuarioControllerSecurityTest {

    private static final String EXPECTED_EXPR = "hasRole('ADMIN')";

    @Test
    @DisplayName("E08 UsuarioController#crear usa hasRole('ADMIN')")
    void crear_exigeAdmin() {
        assertPreAuthorizeValue(UsuarioController.class, "crear", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E09 UsuarioController#listar usa hasRole('ADMIN')")
    void listar_exigeAdmin() {
        assertPreAuthorizeValue(UsuarioController.class, "listar", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E10 UsuarioController#obtenerPorId usa hasRole('ADMIN')")
    void obtenerPorId_exigeAdmin() {
        assertPreAuthorizeValue(UsuarioController.class, "obtenerPorId", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E11 UsuarioController#actualizar usa hasRole('ADMIN')")
    void actualizar_exigeAdmin() {
        assertPreAuthorizeValue(UsuarioController.class, "actualizar", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E12 UsuarioController#desactivar usa hasRole('ADMIN')")
    void desactivar_exigeAdmin() {
        assertPreAuthorizeValue(UsuarioController.class, "desactivar", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("E66 UsuarioController#resetearPassword usa hasRole('ADMIN')")
    void resetearPassword_exigeAdmin() {
        assertPreAuthorizeValue(UsuarioController.class, "resetearPassword", EXPECTED_EXPR);
    }

    // -----------------------------------------------------------------------
    // Triangulación: ningún método de UsuarioController abre la puerta a
    // ENCARGADO o EMPLEADO (debe encontrar 0 ocurrencias).
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Triangulación: ningún endpoint de UsuarioController acepta ENCARGADO ni EMPLEADO")
    void ningunEndpointAceptaRolesOperativos() {
        long aperturasIndebidas = Arrays.stream(UsuarioController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PreAuthorize.class))
                .map(m -> m.getAnnotation(PreAuthorize.class).value())
                .filter(v -> v.contains("ENCARGADO") || v.contains("EMPLEADO"))
                .count();
        assertThat(aperturasIndebidas)
                .as("UsuarioController es exclusivo de ADMIN — no debe aceptar otros roles")
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
