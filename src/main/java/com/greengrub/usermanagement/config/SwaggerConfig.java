package com.greengrub.usermanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI configuration.
 *
 * The API Gateway terminates authentication, so the service no longer advertises
 * a Bearer security scheme. Swagger here is purely for documenting and exercising
 * endpoints during development.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customerServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Management Service API")
                        .description("""
                                ## GreenGrub User Management Service

                                REST API for user records (donors, recipients, admins).
                                Authentication and authorization are handled upstream by
                                the GCP API Gateway; this service receives identity via
                                gateway-injected headers (X-User-Id, X-User-Email, X-User-Role).

                                ### Roles
                                - **DONOR**: donates food
                                - **RECIPIENT**: receives food
                                - **ADMIN**: administrative access
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("GreenGrub Team")
                                .email("support@greengrub.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8082")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.greengrub.com")
                                .description("Production (via API Gateway)")
                ));
    }
}
