package com.hollow.build.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(OpenApiConfigurationProperties.class)
public class OpenApiConfiguration {

    private final OpenApiConfigurationProperties openApiConfigurationProperties;

    private static final String BEARER_AUTH_COMPONENT_NAME = "Bearer Authentication";
    private static final String BEARER_AUTH_SCHEME = "Bearer";
    private static final String API_KEY_HEADER_NAME = "X-API-KEY";
    public static final List<String> SWAGGER_V3_PATHS = List.of("/swagger-ui**/**", "/v3/api-docs**/**");

    @Bean
    public OpenAPI openApi() {
        final var info = new Info()
                .version(openApiConfigurationProperties.getApiVersion())
                .title(openApiConfigurationProperties.getTitle())
                .description(openApiConfigurationProperties.getDescription());

        return new OpenAPI()
                .info(info)
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_COMPONENT_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .in(SecurityScheme.In.HEADER)
                                        .scheme(BEARER_AUTH_SCHEME))
                        // X-API-KEY 请求头
                        .addSecuritySchemes(API_KEY_HEADER_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name(API_KEY_HEADER_NAME))
                )
                .addSecurityItem(new SecurityRequirement()
                        .addList(BEARER_AUTH_COMPONENT_NAME)
                        .addList(API_KEY_HEADER_NAME));
    }

}