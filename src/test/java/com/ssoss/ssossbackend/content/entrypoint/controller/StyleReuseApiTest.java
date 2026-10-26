package com.ssoss.ssossbackend.content.entrypoint.controller;

import java.util.List;
import java.util.Map;

import com.ssoss.ssossbackend.auth.domain.model.AuthErrorCode;
import com.ssoss.ssossbackend.auth.entrypoint.response.SignupResponse;
import com.ssoss.ssossbackend.auth.entrypoint.response.SocialLoginResponse;
import com.ssoss.ssossbackend.content.domain.model.ContentErrorCode;
import com.ssoss.ssossbackend.content.entrypoint.response.ContentDetailResponse;
import com.ssoss.ssossbackend.content.entrypoint.response.ContentSaveResponse;
import com.ssoss.ssossbackend.content.entrypoint.response.GenerationDetailResponse;
import com.ssoss.ssossbackend.content.entrypoint.response.GenerationStartResponse;
import com.ssoss.ssossbackend.credit.domain.model.CreditErrorCode;
import com.ssoss.ssossbackend.shared.exception.CommonErrorCode;
import com.ssoss.ssossbackend.shared.exception.ErrorResponse;
import com.ssoss.ssossbackend.support.IntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("스타일 재사용 API")
class StyleReuseApiTest extends IntegrationTest {

    private static final List<String> ALL_CHANNELS = List.of("BLOG", "INSTAGRAM", "DAANGN_BIZ", "THREADS");

    private ContentSaveResponse savedOrigin(String accessToken, Map<String, Object> generationBody) {
        return fixture.contentsOfGeneration(accessToken, fixture.startedGenerationId(accessToken, generationBody));
    }

    private ContentSaveResponse savedOrigin(String accessToken, String channel) {
        return savedOrigin(accessToken, Map.of(
            "channels", List.of(channel),
            "purpose", "INFORMATION",
            "tone", "CASUAL",
            "emphasis", "원본 강조 내용"));
    }

    @Nested
    @DisplayName("POST /v1/contents/{contentId}/channels/{contentChannelId}/reuses")
    class Reuse {

        @Test
        @DisplayName("저장한 채널별 콘텐츠에서 재사용하면 작업 id 와 Location 헤더를 받고 폴링으로 결과를 받는다")
        void returnsGenerationIdAndPollableResult_whenStyleReused() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-start");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");

            Long generationId = fixture.reuseStyle(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"))
                .expectStatus().isCreated()
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectBody(GenerationStartResponse.class)
                .returnResult()
                .getResponseBody()
                .generationId();

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.status()).isEqualTo("SUCCEEDED"));
        }

        @Test
        @DisplayName("재사용하면 원본과 같은 채널의 결과 1건이 나오고 목적·톤도 원본 콘텐츠와 같다")
        void createsOneResultInOriginChannelWithOriginPurposeAndTone() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-same-channel");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), Map.of(
                "channels", List.of("INSTAGRAM"),
                "purpose", "EVENT_DISCOUNT",
                "tone", "EMOTIONAL",
                "emphasis", "원본 강조 내용"));

            Long generationId = fixture.reusedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.purpose()).isEqualTo("EVENT_DISCOUNT");
                    assertThat(body.tone()).isEqualTo("EMOTIONAL");
                    assertThat(body.results()).singleElement()
                        .satisfies(result -> assertThat(result.channel()).isEqualTo("INSTAGRAM"));
                });
        }

        @Test
        @DisplayName("재사용하면 작업 행에 원본 채널별 콘텐츠 id 가 남는다")
        void keepsSourceContentChannelId_whenStyleReused() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-source-row");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");
            Long contentChannelId = origin.contents().getFirst().contentChannelId();

            Long generationId = fixture.reusedGenerationId(signup.accessToken(), origin.contentId(),
                contentChannelId, fixture.styleReuseBody("새 강조 내용"));

            assertThat(database.generationsOf(database.memberIdOf("naver-reuse-source-row")))
                .filteredOn(generation -> generation.getId().equals(generationId))
                .singleElement()
                .satisfies(generation ->
                    assertThat(generation.getSourceContentChannelId()).isEqualTo(contentChannelId));
        }

        @Test
        @DisplayName("강조 내용 없이 요청하면 400 을 반환한다")
        void returns400_whenEmphasisMissing() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-no-emphasis");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");

            fixture.reuseStyle(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(), Map.of("forbidden", "가격 인상 언급"))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("없는 콘텐츠를 원본으로 요청하면 404 와 CT0005 를 반환한다")
        void returns404_whenContentMissing() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-no-content");

            fixture.reuseStyle(signup.accessToken(), 999_999L, 999_999L, fixture.styleReuseBody("새 강조 내용"))
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("삭제한 콘텐츠를 원본으로 요청하면 404 와 CT0005 를 반환한다")
        void returns404_whenContentDeleted() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-deleted");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");
            fixture.deletedContent(signup.accessToken(), origin.contentId());

            fixture.reuseStyle(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"))
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("그 콘텐츠에 없는 채널을 원본으로 요청하면 404 와 CT0006 을 반환한다")
        void returns404_whenContentChannelMissing() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-no-channel");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");

            fixture.reuseStyle(signup.accessToken(), origin.contentId(), 999_999L,
                    fixture.styleReuseBody("새 강조 내용"))
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code())
                    .isEqualTo(ContentErrorCode.CONTENT_CHANNEL_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("남의 콘텐츠를 원본으로 요청하면 404 와 CT0005 를 반환한다")
        void returns404_whenContentOwnedByOtherMember() {
            SignupResponse owner = fixture.signupActiveMember("naver-reuse-owner");
            ContentSaveResponse origin = savedOrigin(owner.accessToken(), "BLOG");
            SignupResponse other = fixture.signupActiveMember("naver-reuse-other");

            fixture.reuseStyle(other.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"))
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("진행 중 작업이 있으면 409 와 CT0001 을 반환한다")
        void returns409_whenInProgressGenerationExists() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-conflict");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");
            taskExecutor.hold();
            fixture.startedGenerationId(signup.accessToken(), List.of("INSTAGRAM"));

            fixture.reuseStyle(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"))
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code())
                    .isEqualTo(ContentErrorCode.GENERATION_IN_PROGRESS_EXISTS.getCode()));
        }

        @Test
        @DisplayName("크레딧이 부족하면 400 과 CR0002 를 반환하고 작업이 생기지 않는다")
        void returns400AndCreatesNoGeneration_whenBalanceInsufficient() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-credit-short");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM", "THREADS"));
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isZero();

            fixture.reuseStyle(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CreditErrorCode.CREDIT_INSUFFICIENT.getCode()));

            assertThat(database.generationsOf(database.memberIdOf("naver-reuse-credit-short"))).hasSize(6);
        }

        @Test
        @DisplayName("액세스 토큰 없이 요청하면 401 과 A0006 을 반환한다")
        void returns401_whenAccessTokenMissing() {
            fixture.client().post().uri("/v1/contents/1/channels/1/reuses")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN.getCode()));
        }

        @Test
        @DisplayName("가입 대기(PENDING) 토큰으로 요청하면 403 과 A0007 을 반환한다")
        void returns403_whenPendingTokenRequests() {
            SocialLoginResponse login = fixture.naverLoginMember("naver-reuse-pending");

            fixture.reuseStyle(login.accessToken(), 1L, 1L, fixture.styleReuseBody("새 강조 내용"))
                .expectStatus().isForbidden()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(AuthErrorCode.ACCESS_DENIED.getCode()));
        }
    }

    @Nested
    @DisplayName("LLM 요청")
    class LlmRequest {

        @Test
        @DisplayName("재사용하면 원본의 제목·본문이 실리고 사진 가이드 태그는 걷혀 있다")
        void carriesOriginTitleAndBodyWithoutPhotoGuideTag() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-material");
            ContentSaveResponse origin = fixture.contentsOfGeneration(signup.accessToken(),
                fixture.photoGuidedGenerationId(signup.accessToken(), List.of("BLOG")));
            llmApi.reset();

            fixture.reusedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"));

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .contains("[참고 글]")
                .contains("테스트 제목")
                .contains("테스트 본문")
                .doesNotContain("photo-guide", "사진 제목 1", "사진 설명 1"));
        }

        @Test
        @DisplayName("재사용해도 원본의 해시태그는 실리지 않는다")
        void omitsOriginHashtags() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-no-hashtag");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");
            llmApi.reset();

            fixture.reusedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"));

            assertThat(llmApi.recordedRequestBodies()).singleElement()
                .satisfies(request -> assertThat(request).doesNotContain("#테스트", "#쏘쓰"));
        }

        @Test
        @DisplayName("금지 내용을 비워도 소재를 새 강조 내용에서만 가져오라는 지시가 실린다")
        void carriesMaterialScopeInstruction_whenForbiddenEmpty() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-scope");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");
            llmApi.reset();

            fixture.reusedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"));

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .contains("[참고 범위]")
                .contains("말투와 문장 길이뿐이다")
                .doesNotContain("[금지 내용]"));
        }

        @Test
        @DisplayName("원본을 편집한 뒤 재사용하면 편집한 최신본이 실린다")
        void carriesEditedBody_whenOriginEdited() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-edited");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");
            Long contentChannelId = origin.contents().getFirst().contentChannelId();
            fixture.editContentChannel(signup.accessToken(), origin.contentId(), contentChannelId, Map.of(
                    "title", "편집한 제목",
                    "body", "편집한 본문",
                    "hashtags", List.of("#편집태그")))
                .expectStatus().isOk();
            llmApi.reset();

            fixture.reusedGenerationId(signup.accessToken(), origin.contentId(), contentChannelId,
                fixture.styleReuseBody("새 강조 내용"));

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .contains("편집한 제목")
                .contains("편집한 본문")
                .doesNotContain("테스트 제목", "테스트 본문", "#편집태그"));
        }

        @Test
        @DisplayName("재사용하면 요청에서 받은 강조·금지·키워드가 실리고 원본 작업의 강조·금지는 실리지 않는다")
        void carriesRequestedInputsOnly() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-inputs");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), Map.of(
                "channels", List.of("BLOG"),
                "purpose", "INFORMATION",
                "tone", "CASUAL",
                "emphasis", "원본 강조 내용",
                "forbidden", "원본 금지 내용",
                "keywords", List.of("원본 키워드")));
            llmApi.reset();

            fixture.reusedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), Map.of(
                    "emphasis", "새 강조 내용",
                    "forbidden", "새 금지 내용",
                    "keywords", List.of("새 키워드")));

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .contains("새 강조 내용")
                .contains("새 금지 내용")
                .contains("새 키워드")
                .doesNotContain("원본 강조 내용", "원본 금지 내용", "원본 키워드"));
        }

        @Test
        @DisplayName("사진 가이드를 체크해 재사용하면 결과 본문에 사진 가이드 태그가 담긴다")
        void assemblesPhotoGuideTag_whenPhotoGuideChecked() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-photo-guide");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");

            Long generationId = fixture.reusedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), Map.of(
                    "emphasis", "새 강조 내용",
                    "photoGuideChecked", true));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results()).singleElement()
                    .satisfies(result -> assertThat(result.body()).contains("<photo-guide title=")));
        }
    }

    @Nested
    @DisplayName("저장과 크레딧")
    class SaveAndCredit {

        @Test
        @DisplayName("재사용 결과를 저장하면 채널이 하나뿐인 콘텐츠가 새로 생긴다")
        void savesOneChannelContent_whenReuseResultSaved() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-save");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");

            Long generationId = fixture.reusedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"));
            ContentSaveResponse saved = fixture.contentsOfGeneration(signup.accessToken(), generationId);

            assertThat(saved.contentId()).isNotEqualTo(origin.contentId());
            assertThat(saved.contents()).singleElement()
                .satisfies(content -> assertThat(content.channel()).isEqualTo("BLOG"));
            fixture.getContent(signup.accessToken(), saved.contentId())
                .expectStatus().isOk()
                .expectBody(ContentDetailResponse.class)
                .value(body -> assertThat(body.contents()).hasSize(1));
        }

        @Test
        @DisplayName("재사용이 성공하면 신규 생성과 같은 규칙으로 5 가 차감된다")
        void deductsFiveCredits_whenReuseSucceeded() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-deduct");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(95);

            fixture.reusedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"));

            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(90);
            assertThat(database.deductionsOf(database.memberIdOf("naver-reuse-deduct"))).hasSize(2);
        }

        @Test
        @DisplayName("재사용 뒤 원본을 삭제해도 재사용 작업과 결과는 그대로 남는다")
        void keepsReuseResult_whenOriginDeletedAfterwards() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-origin-deleted");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");
            Long generationId = fixture.reusedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), fixture.styleReuseBody("새 강조 내용"));

            fixture.deletedContent(signup.accessToken(), origin.contentId());

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo("SUCCEEDED");
                    assertThat(body.results()).hasSize(1);
                });
            fixture.saveGeneratedContents(signup.accessToken(), generationId)
                .expectStatus().isCreated();
        }

        @Test
        @DisplayName("같은 원본으로 여러 번 재사용하면 작업이 각각 쌓인다")
        void createsIndependentGenerations_whenReusedRepeatedly() {
            SignupResponse signup = fixture.signupActiveMember("naver-reuse-repeat");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), "BLOG");
            Long contentChannelId = origin.contents().getFirst().contentChannelId();

            Long first = fixture.reusedGenerationId(signup.accessToken(), origin.contentId(), contentChannelId,
                fixture.styleReuseBody("첫 번째 강조 내용"));
            Long second = fixture.reusedGenerationId(signup.accessToken(), origin.contentId(), contentChannelId,
                fixture.styleReuseBody("두 번째 강조 내용"));

            assertThat(first).isNotEqualTo(second);
            assertThat(database.generationsOf(database.memberIdOf("naver-reuse-repeat"))).hasSize(3);
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(85);
        }
    }
}
