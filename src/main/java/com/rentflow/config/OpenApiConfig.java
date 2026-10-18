package com.rentflow.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Adds API info and an "Authorize" button (HTTP Basic) to Swagger UI. */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "RentFlow API",
        version = "1.0.0",
        description = "Vehicle rental management REST API. Demo users: admin / admin123, user / user123"))
@SecurityScheme(name = OpenApiConfig.BASIC_AUTH, type = SecuritySchemeType.HTTP, scheme = "basic")
public class OpenApiConfig {

    public static final String BASIC_AUTH = "basicAuth";
}
