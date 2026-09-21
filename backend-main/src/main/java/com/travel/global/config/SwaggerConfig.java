package com.travel.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {

        String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("TripBuddy 여행 서비스 API")
                        .description("여행 검색, 추천, 일정 생성·편집, 경로 및 사용자 인증을 제공하는 REST API 명세입니다. "
                                + "자물쇠가 표시된 API는 로그인 후 발급받은 JWT 액세스 토큰을 우측 상단 Authorize에 입력해야 합니다.")
                        .version("1.0.0")
                )

                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(securitySchemeName)
                )

                .components(
                        new Components()
                                .addSecuritySchemes(
                                        securitySchemeName,
                                        new SecurityScheme()
                                                .name(securitySchemeName)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("로그인 응답으로 받은 액세스 토큰을 입력합니다. Bearer 접두사는 Swagger UI가 자동으로 추가합니다.")
                                )
                );
    }
}
