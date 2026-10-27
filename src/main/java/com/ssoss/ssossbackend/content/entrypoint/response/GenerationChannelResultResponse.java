package com.ssoss.ssossbackend.content.entrypoint.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채널별 생성 결과")
public record GenerationChannelResultResponse(
    @Schema(description = "채널", allowableValues = {"BLOG", "INSTAGRAM", "DAANGN_BIZ", "THREADS"}, example = "BLOG")
    String channel,
    @Schema(description = "제목 — 블로그만 있고 나머지 채널은 null 입니다",
        example = "주말엔 아메리카노 1+1, 놓치면 아쉬운 이벤트", nullable = true)
    String title,
    @Schema(description = """
        본문.
        사진 가이드를 체크한 생성이면 본문 안에 `<photo-guide title="..." description="..."/>` 태그가 섞여 있습니다.
        네이버 블로그는 문단 사이에, 인스타그램·당근 비즈·스레드는 본문 맨 위에 태그가 있습니다.
        닫는 태그가 없는 한 덩어리입니다.
        속성값의 &, <, >, " 는 HTML 방식으로 이스케이프됩니다.""",
        example = "이번 주말, 매장에서 아메리카노 1+1 이벤트를 진행합니다..."
            + "<photo-guide title=\"시그니처 메뉴\" description=\"위에서 내려다보는 각도로 찍어 주세요\"/>...")
    String body,
    @Schema(description = "해시태그 목록 — 당근 비즈는 빈 배열입니다", example = "[\"#카페이벤트\", \"#아메리카노\"]")
    List<String> hashtags
) {
}
