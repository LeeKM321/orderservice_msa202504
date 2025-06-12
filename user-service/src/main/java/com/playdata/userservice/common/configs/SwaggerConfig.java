package com.playdata.userservice.common.configs;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "User-Service API",
                version = "1.0.0",
                description = """
            MSA 기반 사용자 관리 서비스 API입니다.
            
            ## 주요 기능
            - 회원가입 및 로그인
            - JWT 기반 인증/인가
            - 관리자 권한 관리
            - 이메일 인증
            - 사용자 정보 조회 및 관리
            
            ## 인증 방법
            1. `/user/doLogin`으로 로그인
            2. 응답으로 받은 JWT 토큰을 'Bearer {token}' 형식으로 Authorization 헤더에 포함
            """,
                contact = @Contact(
                        name = "PlayData 8th Develop Team",
                        email = "dev@playdata.io"
                ),
                license = @License(
                        name = "MIT License",
                        url = "https://opensource.org/licenses/MIT"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8000/user-service", description = "로컬 개발 서버"),
                @Server(url = "https://api.playdatashop8917.store", description = "운영 서버")
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer",
        description = "JWT 토큰을 입력하세요. (Bearer 접두사 제외)"
)
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components());
    }

    /*
    private Info apiInfo() {
        return new Info()
                .title("User-Service API") // 문서의 제목
                .description("orderservice의 user 관련 api 모음 문서입니다.") // 문서의 설명
                .version("1.0.0");
    }
     */

}










