package com.ssoss.ssossbackend.documentation

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun ssossOpenApi(): OpenAPI =
        OpenAPI()
            .info(Info().title("SSOSS API").version("v1"))
            .components(
                Components().addSecuritySchemes(
                    "bearerAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("소셜 로그인·토큰 재발급으로 발급받은 액세스 토큰"),
                ),
            ).tags(
                listOf(
                    Tag()
                        .name("인증")
                        .description(
                            """
                            소셜 프로바이더 인증으로 세션을 시작하고, 발급된 토큰 쌍으로 세션을 유지·종료하는 API 그룹입니다.

                            **토큰 모델**
                            - 액세스 토큰 — JWT. `Authorization: Bearer {accessToken}` 헤더로 API 를 호출합니다.
                              `role` 클레임에 회원 상태(PENDING/ACTIVE/WITHDRAWN)가 담기며, 호출할 수 있는 API 는 role 이 결정합니다.
                              가입 회원(ACTIVE)은 전체 API 를, 가입 대기(PENDING)는 회원가입만, 탈퇴 대기(WITHDRAWN)는 복구 용도만 호출할 수 있습니다.
                            - 리프레시 토큰 — opaque 문자열. 토큰 재발급·로그아웃 요청 본문으로만 제출합니다.

                            **Bearer 인증 공통 에러**
                            - 401 A0006 — 액세스 토큰이 없거나 무효·만료된 경우. 다시 로그인해야 합니다.
                            - 403 A0007 — 액세스 토큰은 유효하지만 role 에 허용되지 않는 API 를 호출한 경우 (예: 가입 대기 토큰으로 회원가입 외 API 호출).

                            **세션 생명주기**
                            1. 소셜 로그인 — 프로바이더 액세스 토큰을 검증하고 회원 상태와 토큰 쌍을 발급합니다.
                               처음 인증한 계정은 가입 대기(PENDING) 회원으로 생성됩니다.
                            2. 토큰 재발급 — 리프레시 토큰을 회전(RTR)해 새 토큰 쌍을 발급합니다. 만료 시각은 회전마다 다시 연장됩니다.
                            3. 로그아웃 — 현재 세션의 리프레시 토큰을 폐기합니다. 다른 기기의 세션은 유지됩니다.
                            4. 탈퇴 — 탈퇴 대기(WITHDRAWN)로 전환되며 기존 리프레시 토큰이 전부 무효화됩니다.
                               복구 유예 중 로그인하면 복구 용도 전용(role=WITHDRAWN) 토큰 쌍이 발급됩니다.

                            """.trimIndent(),
                        ),
                ),
            )
}
