package com.ssoss.ssossbackend.credit.entrypoint.controller;

import com.ssoss.ssossbackend.credit.entrypoint.request.CreditLedgerListRequest;
import com.ssoss.ssossbackend.credit.entrypoint.response.CreditLedgerListResponse;
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
interface CreditLedgerApi {

    @Operation(
        summary = "크레딧 내역 조회",
        security = @SecurityRequirement(name = "bearerAuth"),
        description = """
            가입 회원(ACTIVE)의 크레딧 변동 내역을 최신순으로 조회합니다.

            - 가입 회원(ACTIVE) accessToken 전용 API 입니다.
            - `type` 은 화면의 탭입니다. `ALL` 은 전체, `USE` 는 차감, `GAIN` 은 지급과 충전이며 생략하면 `ALL` 입니다.
            - 무료 크레딧 소멸은 내역에 담기지 않아, 내역의 변동량을 더한 값은 잔액과 다를 수 있습니다.
            - 각 행의 사유 문구는 변동이 일어난 시점에 굳혀 저장한 값이라, 문구 규칙이 바뀌어도 과거 행은 그대로 남습니다.
            - 화면 상단의 잔액은 `GET /v1/credits/me` 를 따로 호출해 받습니다.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "변동 시각 최신순 내역 목록을 반환합니다",
            content = @Content(schema = @Schema(implementation = CreditLedgerListResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "totalCount": 3,
                      "page": 0,
                      "size": 20,
                      "hasNext": false,
                      "ledgers": [
                        {
                          "ledgerId": 12,
                          "type": "DEDUCT",
                          "description": "블로그 외 1건 콘텐츠 생성",
                          "amount": -10,
                          "occurredAt": "2026-08-05T02:10:00Z"
                        },
                        {
                          "ledgerId": 8,
                          "type": "GRANT",
                          "description": "8월 크레딧 지급",
                          "amount": 100,
                          "occurredAt": "2026-08-02T15:00:00Z"
                        },
                        {
                          "ledgerId": 1,
                          "type": "GRANT",
                          "description": "가입 크레딧 지급",
                          "amount": 100,
                          "occurredAt": "2026-07-17T04:22:11Z"
                        }
                      ]
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "type 이 없는 값이거나 page·size 가 허용 범위를 벗어났습니다 (C0001)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "size 상한 초과", value = """
                        {"code":"C0001","message":"한 번에 최대 50건까지 조회할 수 있습니다"}
                        """),
                    @ExampleObject(name = "없는 탭", value = """
                        {"code":"C0001","message":"입력값을 다시 확인해 주세요"}
                        """)})),
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
    CreditLedgerListResponse list(Long memberId, CreditLedgerListRequest request);
}
