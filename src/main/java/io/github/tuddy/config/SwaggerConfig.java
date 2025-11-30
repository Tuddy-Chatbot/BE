package io.github.tuddy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

// swagger ui
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "jwtAuth";

        // 1. API 요청 헤더에 인증 정보를 포함하도록 설정 (전역 적용)
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        // 2. Security Scheme 등록 (Type: HTTP, Scheme: bearer, Format: JWT)
        Components components = new Components()
            .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                .name(jwtSchemeName)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT"));

        // 3. OpenAPI 객체 반환
        return new OpenAPI()
            .info(new Info()
                .title("Tuddy API Document")
                .version("0.0.1")
                .description("Tuddy Chatbot API 명세서"))
            .addSecurityItem(securityRequirement)
            .components(components);
    }
}