package com.staffflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de SpringDoc OpenAPI para StaffFlow.
 *
 * <p>Registra el esquema de seguridad Bearer JWT para que Swagger UI
 * muestre el boton Authorize y permita enviar el token en las peticiones
 * de prueba. Sin esta configuracion, Swagger UI no añade la cabecera
 * Authorization: Bearer {token} en las llamadas a endpoints protegidos.
 *
 * @author Santiago Castillo
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configura la especificacion OpenAPI con metadatos del proyecto
     * y el esquema de autenticacion JWT Bearer.
     *
     * <p>El nombre "bearerAuth" es el identificador interno que conecta
     * el esquema declarado en Components con el requisito de seguridad
     * global aplicado a todos los endpoints.
     *
     * @return instancia OpenAPI con seguridad JWT configurada
     */
    @Bean
    public OpenAPI openAPI() {
        // Nombre del esquema de seguridad — debe ser igual en Components y SecurityRequirement
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("StaffFlow API")
                        .description("API REST para control de presencia laboral — TFG DAM 2025/2026")
                        .version("1.0.0"))
                // Requisito de seguridad global: se aplica a todos los endpoints por defecto.
                // Los endpoints publicos lo ignoran en la practica porque SecurityConfig los
                // declara permitAll (login, recuperacion/reset de contrasena, health, swagger,
                // h2-console) o los aisla en terminalFilterChain (E48-E52, terminal por PIN).
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                // HTTP Bearer: Swagger añade "Bearer " automaticamente
                                // El usuario solo pega el token sin el prefijo
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Pega el token JWT obtenido en POST /api/v1/auth/login")));
    }
}
