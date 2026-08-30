package com.mapharitechnologies.eccprayerbot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 * Defines security schemes so the Swagger UI "Authorize" button works
 * for both API key and admin token headers.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bibleApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mount Zion Bible API")
                        .description(
                                "REST API for Bible verse lookup, chapter reading, and text search.\n\n"
                                + "**Authentication:** All `/api/v1/**` endpoints require an `X-API-Key` header.\n\n"
                                + "**Admin endpoints:** `/api/admin/**` require an `X-Admin-Token` header.\n\n"
                                + "Generated from the ECCPrayerBot Bible services.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Maphari Technologies")
                                .email("support@mapharitechnologies.com"))
                        .license(new License()
                                .name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("API Key"))
                .schemaRequirement("API Key",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .name("X-API-Key")
                                .in(SecurityScheme.In.HEADER)
                                .description("API key for Bible endpoints (prefix: zb_)"))
                .schemaRequirement("Admin Token",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .name("X-Admin-Token")
                                .in(SecurityScheme.In.HEADER)
                                .description("Admin token for key management endpoints"));
    }
}
