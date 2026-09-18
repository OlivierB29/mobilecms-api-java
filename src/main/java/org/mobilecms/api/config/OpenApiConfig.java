package org.mobilecms.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MobileCMS API",
                version = "v50",
                description = "REST API for JSON-file content, authentication, media, and administration.",
                license = @License(name = "MIT")
        ),
        security = @SecurityRequirement(name = "bearerAuth"),
        tags = {
                @Tag(name = "Auth", description = "Authentication and account management endpoints"),
                @Tag(name = "CMS", description = "Content management endpoints"),
                @Tag(name = "Web", description = "Public read-only content endpoints"),
                @Tag(name = "Files", description = "File upload and media endpoints"),
                @Tag(name = "Admin", description = "Administrative operations"),
                @Tag(name = "Misc", description = "Diagnostic and root endpoints")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
