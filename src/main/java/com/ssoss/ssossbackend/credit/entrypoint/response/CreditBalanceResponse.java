package com.ssoss.ssossbackend.credit.entrypoint.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "크레딧 잔액 응답 — 현재 보유한 크레딧 잔액")
public record CreditBalanceResponse(
    @Schema(description = "크레딧 잔액 (무료 + 충전)", example = "100")
    int balance
) {
}
