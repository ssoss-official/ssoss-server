package com.ssoss.ssossbackend.credit.entrypoint.controller;

import com.ssoss.ssossbackend.credit.entrypoint.response.CreditBalanceResponse;
import com.ssoss.ssossbackend.shared.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "크레딧")
interface CreditBalanceApi {

    @Operation(
        summary = "크레딧 잔액 조회",
        security = @SecurityRequirement(name = "bearerAuth"),
        description = """
            가입 회원(ACTIVE)이 현재 보유한 크레딧 잔액을 조회합니다.

            - 가입 회원(ACTIVE) accessToken 전용 API 입니다.
            - 잔액은 무료 크레딧과 충전 크레딧의 합입니다. 가입하면 즉시 무료 크레딧 100개가 지급됩니다.
            - 무료 크레딧은 크레딧 사이클(매달 3일 00:00 KST 시작)마다 새로 지급되며 미사용분은 사이클이 끝나면 소멸합니다.
              충전 크레딧은 소멸하지 않습니다.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "현재 보유한 크레딧 잔액을 반환합니다"),
        @ApiResponse(responseCode = "401", description = "accessToken 이 없거나 유효하지 않습니다 (A0006) — 다시 로그인해 주세요",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"code":"A0006","message":"유효하지 않은 인증 정보입니다. 다시 로그인해 주세요"}
                    """))),
        @ApiResponse(responseCode = "403", description = "가입 회원(ACTIVE) 토큰이 아닙니다 (A0007) — 가입 대기·탈퇴 대기 상태에서는 호출할 수 없습니다",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"code":"A0007","message":"접근 권한이 없습니다"}
                    """)))
    })
    CreditBalanceResponse getBalance(Long memberId);
}
