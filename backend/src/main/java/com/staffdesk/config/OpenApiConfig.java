package com.staffdesk.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI api() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Staffdesk API")
                .version("v1")
                .description(
                    "Employee directory and account administration. Login to obtain a short-lived bearer token."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearer"));
  }

  @Bean
  org.springdoc.core.customizers.OpenApiCustomizer publicOperations() {
    return spec -> {
      var login = spec.getPaths().get("/api/v1/auth/login");
      if (login != null) login.getPost().setSecurity(java.util.List.of());
      var version = spec.getPaths().get("/api/v1/version");
      if (version != null) version.getGet().setSecurity(java.util.List.of());
    };
  }
}
