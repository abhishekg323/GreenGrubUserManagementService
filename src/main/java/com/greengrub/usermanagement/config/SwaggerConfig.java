package com.greengrub.usermanagement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI Configuration
 * Provides interactive API documentation and testing interface
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customerServiceOpenAPI() {
        // Define JWT security scheme
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("Enter JWT token obtained from login/signup. Example: Just paste the token, 'Bearer' prefix is added automatically.");

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("Bearer Authentication");

        return new OpenAPI()
                .info(new Info()
                        .title("User Management Service API")
                        .description("""
                                ## GreenGrub Customer Microservice API

                                Complete REST API for managing customer/user operations in the GreenGrub application.

                                ### Features
                                - 🔐 JWT-based authentication
                                - 👥 User management (CRUD)
                                - 🔑 Password management
                                - 🔍 Search and filtering
                                - 📊 Statistics (Admin only)
                                - 🛡️ Role-based access control

                                ### How to Use
                                1. **Sign Up** or **Login** to get a JWT token
                                2. Click the **Authorize** button (🔓) at the top
                                3. Paste your token in the value field
                                4. Click **Authorize** to apply the token
                                5. Try out the protected endpoints!

                                ### User Roles
                                - **DONOR**: Can donate food
                                - **RECIPIENT**: Can receive food
                                - **ADMIN**: Full administrative access
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
                                .description("Production Server (if available)")
                ))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", securityScheme))
                .addSecurityItem(securityRequirement);
    }
}
