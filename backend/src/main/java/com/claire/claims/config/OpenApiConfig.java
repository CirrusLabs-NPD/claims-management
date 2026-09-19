package com.claire.claims.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only class that imports springdoc/swagger types.
 *
 * If the springdoc dependency ever has to be dropped, delete this file and the
 * corresponding block in pom.xml; nothing else in the codebase is affected.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI claimManagementOpenApi() {
        final String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Claim Management API")
                        .version("1.0.0")
                        .description("""
                                Healthcare claim management.

                                **Phase 1** covers the full claim lifecycle: patients, payers, providers,
                                coverage, claim capture with service lines and diagnoses, the status
                                state machine, and the audit trail.

                                **Phase 2** endpoints are present but return `501 Not Implemented` with an
                                `X-Phase: 2` header. They are specified in `docs/PHASE2_HANDOFF.md`.

                                Sign in at `POST /api/auth/login` (try `biller` / `biller123`), then click
                                Authorize and paste the token.
                                """)
                        .contact(new Contact().name("Claire").url("https://example.com")))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
