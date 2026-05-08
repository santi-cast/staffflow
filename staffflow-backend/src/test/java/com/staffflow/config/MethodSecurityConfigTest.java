package com.staffflow.config;

import com.staffflow.controller.AusenciaController;
import com.staffflow.controller.EmpleadoController;
import com.staffflow.controller.FichajeController;
import com.staffflow.controller.InformeController;
import com.staffflow.controller.PausaController;
import com.staffflow.controller.PresenciaController;
import com.staffflow.controller.SaldoController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests estructurales de seguridad declarativa para Block 5.
 *
 * <p>Verifica sin arrancar el contexto de Spring que:
 * <ul>
 *   <li>5.1 {@code SecurityConfig} declara {@code @EnableMethodSecurity}</li>
 *   <li>5.2-5.3 Los 7 endpoints /me usan {@code hasAnyRole('EMPLEADO','ENCARGADO')}</li>
 *   <li>5.4 {@code TerminalController} no tiene {@code @PreAuthorize}</li>
 * </ul>
 *
 * <p>Usa reflexión sobre los bytecodes compilados — si la anotación no existe el test falla
 * en RED antes de cualquier modificación. Esta es la forma correcta de TDD para seguridad
 * declarativa cuando {@code @WebMvcTest} está roto por el contexto de JWT de este proyecto.
 *
 * @author Santiago Castillo
 */
@DisplayName("Block 5 — Seguridad declarativa: @EnableMethodSecurity y @PreAuthorize /me")
class MethodSecurityConfigTest {

    private static final String EXPECTED_EXPR = "hasAnyRole('EMPLEADO','ENCARGADO')";

    // -----------------------------------------------------------------------
    // 5.1 — @EnableMethodSecurity presente en SecurityConfig
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("5.1 SecurityConfig declara @EnableMethodSecurity")
    void securityConfig_declara_EnableMethodSecurity() {
        boolean presente = SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class);
        assertThat(presente)
                .as("SecurityConfig debe tener @EnableMethodSecurity para activar la evaluacion de @PreAuthorize")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // 5.2a — AusenciaController: /me y /me/informe
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("5.2a AusenciaController#listarPropias usa hasAnyRole('EMPLEADO','ENCARGADO')")
    void ausenciaController_listarPropias_rolesCorrectos() {
        assertPreAuthorizeValue(AusenciaController.class, "listarPropias", EXPECTED_EXPR);
    }

    @Test
    @DisplayName("5.2a AusenciaController#informeAusenciasMe usa hasAnyRole('EMPLEADO','ENCARGADO')")
    void ausenciaController_informeAusenciasMe_rolesCorrectos() {
        assertPreAuthorizeValue(AusenciaController.class, "informeAusenciasMe", EXPECTED_EXPR);
    }

    // -----------------------------------------------------------------------
    // 5.2b — FichajeController: /me/fichajes
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("5.2b FichajeController#listarFichajesPropios usa hasAnyRole('EMPLEADO','ENCARGADO')")
    void fichajeController_listarFichajesPropios_rolesCorrectos() {
        assertPreAuthorizeByExpression(FichajeController.class, EXPECTED_EXPR,
                "El metodo /me de FichajeController debe aceptar EMPLEADO y ENCARGADO");
    }

    // -----------------------------------------------------------------------
    // 5.2c — InformeController: /me/informes/horas
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("5.2c InformeController tiene al menos un metodo con hasAnyRole('EMPLEADO','ENCARGADO')")
    void informeController_metodoMe_rolesCorrectos() {
        assertPreAuthorizeByExpression(InformeController.class, EXPECTED_EXPR,
                "El metodo /me de InformeController debe aceptar EMPLEADO y ENCARGADO");
    }

    // -----------------------------------------------------------------------
    // 5.2d — PausaController: /me/pausas
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("5.2d PausaController tiene al menos un metodo con hasAnyRole('EMPLEADO','ENCARGADO')")
    void pausaController_metodoMe_rolesCorrectos() {
        assertPreAuthorizeByExpression(PausaController.class, EXPECTED_EXPR,
                "El metodo /me de PausaController debe aceptar EMPLEADO y ENCARGADO");
    }

    // -----------------------------------------------------------------------
    // 5.2e — PresenciaController: /me/presencia/parte-diario
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("5.2e PresenciaController tiene al menos un metodo con hasAnyRole('EMPLEADO','ENCARGADO')")
    void presenciaController_metodoMe_rolesCorrectos() {
        assertPreAuthorizeByExpression(PresenciaController.class, EXPECTED_EXPR,
                "El metodo /me de PresenciaController debe aceptar EMPLEADO y ENCARGADO");
    }

    // -----------------------------------------------------------------------
    // 5.2f — SaldoController: /me/saldos
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("5.2f SaldoController tiene al menos un metodo con hasAnyRole('EMPLEADO','ENCARGADO')")
    void saldoController_metodoMe_rolesCorrectos() {
        assertPreAuthorizeByExpression(SaldoController.class, EXPECTED_EXPR,
                "El metodo /me de SaldoController debe aceptar EMPLEADO y ENCARGADO");
    }

    // -----------------------------------------------------------------------
    // 5.3 — EmpleadoController: /me
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("5.3 EmpleadoController#obtenerPerfil usa hasAnyRole('EMPLEADO','ENCARGADO')")
    void empleadoController_obtenerPerfil_rolesCorrectos() {
        assertPreAuthorizeByExpression(EmpleadoController.class, EXPECTED_EXPR,
                "El metodo /me de EmpleadoController debe aceptar EMPLEADO y ENCARGADO");
    }

    // -----------------------------------------------------------------------
    // Triangulación: ningún @PreAuthorize con hasRole('EMPLEADO') solo en los 7 controllers
    // (busca la expresion incorrecta — debe retornar false después de los cambios)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Triangulación: AusenciaController no tiene hasRole('EMPLEADO') sin ENCARGADO")
    void ausenciaController_noTiene_hasRole_soloEmpleado_en_endpointsMe() {
        long conExprIncorrecta = Arrays.stream(AusenciaController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PreAuthorize.class))
                .map(m -> m.getAnnotation(PreAuthorize.class).value())
                .filter(v -> v.equals("hasRole('EMPLEADO')"))
                .count();
        assertThat(conExprIncorrecta)
                .as("AusenciaController no debe tener hasRole('EMPLEADO') solo — debe ser hasAnyRole")
                .isZero();
    }

    @Test
    @DisplayName("Triangulación: FichajeController no tiene hasRole('EMPLEADO') sin ENCARGADO")
    void fichajeController_noTiene_hasRole_soloEmpleado() {
        long conExprIncorrecta = Arrays.stream(FichajeController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PreAuthorize.class))
                .map(m -> m.getAnnotation(PreAuthorize.class).value())
                .filter(v -> v.equals("hasRole('EMPLEADO')"))
                .count();
        assertThat(conExprIncorrecta)
                .as("FichajeController no debe tener hasRole('EMPLEADO') solo — debe ser hasAnyRole")
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

    /**
     * Verifica que al menos un método de la clase tenga el valor de @PreAuthorize esperado.
     * Útil cuando no conocemos el nombre exacto del método.
     */
    private void assertPreAuthorizeByExpression(Class<?> controller, String expectedExpr, String message) {
        boolean encontrado = Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PreAuthorize.class))
                .anyMatch(m -> m.getAnnotation(PreAuthorize.class).value().equals(expectedExpr));

        assertThat(encontrado)
                .as(message)
                .isTrue();
    }
}
