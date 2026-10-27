package com.ssoss.ssossbackend.content.entrypoint.controller;

import java.util.List;
import java.util.Map;

import com.ssoss.ssossbackend.auth.domain.model.AuthErrorCode;
import com.ssoss.ssossbackend.auth.entrypoint.response.SignupResponse;
import com.ssoss.ssossbackend.auth.entrypoint.response.SocialLoginResponse;
import com.ssoss.ssossbackend.content.domain.model.ContentErrorCode;
import com.ssoss.ssossbackend.content.entrypoint.response.ContentChannelSummaryResponse;
import com.ssoss.ssossbackend.content.entrypoint.response.ContentDetailResponse;
import com.ssoss.ssossbackend.content.entrypoint.response.ContentSaveResponse;
import com.ssoss.ssossbackend.content.entrypoint.response.GenerationChannelResultResponse;
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

@DisplayName("다른 채널용으로 만들기 API")
class ChannelConversionApiTest extends IntegrationTest {

    private static final List<String> ALL_CHANNELS = List.of("BLOG", "INSTAGRAM", "DAANGN_BIZ", "THREADS");

    private ContentSaveResponse savedOrigin(String accessToken, Map<String, Object> generationBody) {
        return fixture.contentsOfGeneration(accessToken, fixture.startedGenerationId(accessToken, generationBody));
    }

    private ContentSaveResponse savedBlogOrigin(String accessToken) {
        return savedOrigin(accessToken, Map.of(
            "channels", List.of("BLOG"),
            "purpose", "INFORMATION",
            "tone", "CASUAL",
            "emphasis", "원본 강조 내용"));
    }

    @Nested
    @DisplayName("POST /v1/contents/{contentId}/channels/{contentChannelId}/conversions")
    class Convert {

        @Test
        @DisplayName("저장한 채널별 콘텐츠에서 다른 채널로 만들면 작업 id 와 Location 헤더를 받고 폴링으로 결과를 받는다")
        void returnsGenerationIdAndPollableResult_whenConverted() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-start");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());

            Long generationId = fixture.convertChannels(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(),
                    fixture.channelConversionBody(List.of("INSTAGRAM", "THREADS")))
                .expectStatus().isCreated()
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectBody(GenerationStartResponse.class)
                .returnResult()
                .getResponseBody()
                .generationId();

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo("SUCCEEDED");
                    assertThat(body.results()).extracting(GenerationChannelResultResponse::channel)
                        .containsExactlyInAnyOrder("INSTAGRAM", "THREADS");
                });
        }

        @Test
        @DisplayName("원본을 뺀 나머지 세 채널을 한 번에 고를 수 있다")
        void createsThreeResults_whenEveryOtherChannelChosen() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-three");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());

            Long generationId = fixture.convertedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), List.of("INSTAGRAM", "DAANGN_BIZ", "THREADS"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results())
                    .extracting(GenerationChannelResultResponse::channel)
                    .containsExactlyInAnyOrder("INSTAGRAM", "DAANGN_BIZ", "THREADS"));
        }

        @Test
        @DisplayName("작업 행에 원본 채널별 콘텐츠 id 가 남는다")
        void keepsSourceContentChannelId_whenConverted() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-source-row");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());
            Long contentChannelId = origin.contents().getFirst().contentChannelId();

            Long generationId = fixture.convertedGenerationId(signup.accessToken(), origin.contentId(),
                contentChannelId, List.of("INSTAGRAM"));

            assertThat(database.generationsOf(database.memberIdOf("naver-convert-source-row")))
                .filteredOn(generation -> generation.getId().equals(generationId))
                .singleElement()
                .satisfies(generation ->
                    assertThat(generation.getSourceContentChannelId()).isEqualTo(contentChannelId));
        }

        @Test
        @DisplayName("여러 채널을 저장한 콘텐츠에서도 원본 채널만 빠지고 형제 채널은 고를 수 있다")
        void allowsSiblingChannel_whenOriginContentHasSeveralChannels() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-sibling");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), Map.of(
                "channels", List.of("BLOG", "INSTAGRAM"),
                "purpose", "INFORMATION",
                "tone", "CASUAL",
                "emphasis", "원본 강조 내용"));
            Long blogChannelId = origin.contents().stream()
                .filter(content -> "BLOG".equals(content.channel()))
                .findFirst()
                .orElseThrow()
                .contentChannelId();

            Long generationId = fixture.convertedGenerationId(signup.accessToken(), origin.contentId(),
                blogChannelId, List.of("INSTAGRAM", "THREADS"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results())
                    .extracting(GenerationChannelResultResponse::channel)
                    .containsExactlyInAnyOrder("INSTAGRAM", "THREADS"));
        }

        @Test
        @DisplayName("원본과 같은 채널을 고르면 400 과 CT0011 을 반환한다")
        void returns400_whenOriginChannelChosen() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-same-channel");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());

            fixture.convertChannels(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(),
                    fixture.channelConversionBody(List.of("INSTAGRAM", "BLOG")))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code())
                    .isEqualTo(ContentErrorCode.SOURCE_CHANNEL_NOT_ALLOWED.getCode()));
        }

        @Test
        @DisplayName("채널을 하나도 고르지 않으면 400 을 반환한다")
        void returns400_whenChannelsEmpty() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-no-channel");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());

            fixture.convertChannels(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(), fixture.channelConversionBody(List.of()))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("같은 채널을 두 번 고르면 400 을 반환한다")
        void returns400_whenChannelsDuplicated() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-duplicated");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());

            fixture.convertChannels(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(),
                    fixture.channelConversionBody(List.of("INSTAGRAM", "INSTAGRAM")))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("채널을 네 개 고르면 400 을 반환한다")
        void returns400_whenMoreThanThreeChannelsChosen() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-too-many");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());

            fixture.convertChannels(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(), fixture.channelConversionBody(ALL_CHANNELS))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("없는 콘텐츠를 원본으로 요청하면 404 와 CT0005 를 반환한다")
        void returns404_whenContentMissing() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-no-content");

            fixture.convertChannels(signup.accessToken(), 999_999L, 999_999L,
                    fixture.channelConversionBody(List.of("INSTAGRAM")))
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("삭제한 콘텐츠를 원본으로 요청하면 404 와 CT0005 를 반환한다")
        void returns404_whenContentDeleted() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-deleted");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());
            fixture.deletedContent(signup.accessToken(), origin.contentId());

            fixture.convertChannels(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(),
                    fixture.channelConversionBody(List.of("INSTAGRAM")))
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("그 콘텐츠에 없는 채널을 원본으로 요청하면 404 와 CT0006 을 반환한다")
        void returns404_whenContentChannelMissing() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-no-origin-channel");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());

            fixture.convertChannels(signup.accessToken(), origin.contentId(), 999_999L,
                    fixture.channelConversionBody(List.of("INSTAGRAM")))
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code())
                    .isEqualTo(ContentErrorCode.CONTENT_CHANNEL_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("남의 콘텐츠를 원본으로 요청하면 404 와 CT0005 를 반환한다")
        void returns404_whenContentOwnedByOtherMember() {
            SignupResponse owner = fixture.signupActiveMember("naver-convert-owner");
            ContentSaveResponse origin = savedBlogOrigin(owner.accessToken());
            SignupResponse other = fixture.signupActiveMember("naver-convert-other");

            fixture.convertChannels(other.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(),
                    fixture.channelConversionBody(List.of("INSTAGRAM")))
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("진행 중 작업이 있으면 409 와 CT0001 을 반환한다")
        void returns409_whenInProgressGenerationExists() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-conflict");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());
            taskExecutor.hold();
            fixture.startedGenerationId(signup.accessToken(), List.of("INSTAGRAM"));

            fixture.convertChannels(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(),
                    fixture.channelConversionBody(List.of("THREADS")))
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code())
                    .isEqualTo(ContentErrorCode.GENERATION_IN_PROGRESS_EXISTS.getCode()));
        }

        @Test
        @DisplayName("크레딧이 부족하면 400 과 CR0002 를 반환하고 작업이 생기지 않는다")
        void returns400AndCreatesNoGeneration_whenBalanceInsufficient() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-credit-short");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM", "THREADS"));
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isZero();

            fixture.convertChannels(signup.accessToken(), origin.contentId(),
                    origin.contents().getFirst().contentChannelId(),
                    fixture.channelConversionBody(List.of("INSTAGRAM")))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CreditErrorCode.CREDIT_INSUFFICIENT.getCode()));

            assertThat(database.generationsOf(database.memberIdOf("naver-convert-credit-short"))).hasSize(6);
        }

        @Test
        @DisplayName("액세스 토큰 없이 요청하면 401 과 A0006 을 반환한다")
        void returns401_whenAccessTokenMissing() {
            fixture.client().post().uri("/v1/contents/1/channels/1/conversions")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN.getCode()));
        }

        @Test
        @DisplayName("가입 대기(PENDING) 토큰으로 요청하면 403 과 A0007 을 반환한다")
        void returns403_whenPendingTokenRequests() {
            SocialLoginResponse login = fixture.naverLoginMember("naver-convert-pending");

            fixture.convertChannels(login.accessToken(), 1L, 1L, fixture.channelConversionBody(List.of("INSTAGRAM")))
                .expectStatus().isForbidden()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(AuthErrorCode.ACCESS_DENIED.getCode()));
        }
    }

    @Nested
    @DisplayName("LLM 요청")
    class LlmRequest {

        @Test
        @DisplayName("원본이 만들어 낸 제목·본문은 실리지 않는다")
        void omitsOriginTitleAndBody() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-material");
            ContentSaveResponse origin = fixture.contentsOfGeneration(signup.accessToken(),
                fixture.photoGuidedGenerationId(signup.accessToken(), List.of("BLOG")));
            llmApi.reset();

            fixture.convertedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), List.of("INSTAGRAM"));

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .doesNotContain("테스트 제목", "테스트 본문", "이어지는 본문 1", "사진 제목 1", "사진 설명 1")
                .contains("테스트 강조 내용"));
        }

        @Test
        @DisplayName("원본 작업의 목적·톤·강조·금지·키워드 지시가 그대로 다시 실린다")
        void carriesOriginGenerationInputs() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-inputs");
            ContentSaveResponse origin = savedOrigin(signup.accessToken(), Map.of(
                "channels", List.of("BLOG"),
                "purpose", "EVENT_DISCOUNT",
                "tone", "EMOTIONAL",
                "emphasis", "원본 강조 내용",
                "forbidden", "원본 금지 내용",
                "keywords", List.of("원본 키워드")));
            llmApi.reset();

            Long generationId = fixture.convertedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), List.of("THREADS"));

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .contains("이벤트/할인")
                .contains("감성형")
                .contains("원본 강조 내용")
                .contains("원본 금지 내용")
                .contains("원본 키워드"));
            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.purpose()).isEqualTo("EVENT_DISCOUNT");
                    assertThat(body.tone()).isEqualTo("EMOTIONAL");
                    assertThat(body.keywords()).containsExactly("원본 키워드");
                });
        }

        @Test
        @DisplayName("원본을 편집해도 프롬프트는 달라지지 않는다")
        void ignoresEditedBody_whenOriginEdited() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-edited");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());
            Long contentChannelId = origin.contents().getFirst().contentChannelId();
            fixture.editContentChannel(signup.accessToken(), origin.contentId(), contentChannelId, Map.of(
                    "title", "편집한 제목",
                    "body", "편집한 본문",
                    "hashtags", List.of("#편집태그")))
                .expectStatus().isOk();
            llmApi.reset();

            fixture.convertedGenerationId(signup.accessToken(), origin.contentId(), contentChannelId,
                List.of("INSTAGRAM"));

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .doesNotContain("편집한 제목", "편집한 본문", "#편집태그", "테스트 제목", "테스트 본문")
                .contains("원본 강조 내용"));
        }

        @Test
        @DisplayName("원본의 해시태그는 실리지 않는다")
        void omitsOriginHashtags() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-no-hashtag");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());
            llmApi.reset();

            fixture.convertedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), List.of("INSTAGRAM"));

            assertThat(llmApi.recordedRequestBodies()).singleElement()
                .satisfies(request -> assertThat(request).doesNotContain("#테스트", "#쏘쓰"));
        }

        @Test
        @DisplayName("원본 작업이 사진 가이드를 체크했으면 새 결과 본문에도 사진 가이드 태그가 담긴다")
        void assemblesPhotoGuideTag_whenOriginGenerationPhotoGuideChecked() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-photo-guide");
            ContentSaveResponse origin = fixture.contentsOfGeneration(signup.accessToken(),
                fixture.photoGuidedGenerationId(signup.accessToken(), List.of("BLOG")));

            Long generationId = fixture.convertedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), List.of("INSTAGRAM"));

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
        @DisplayName("결과를 저장하면 고른 채널만 담긴 콘텐츠가 새로 생긴다")
        void savesChosenChannelsOnly_whenConversionResultSaved() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-save");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());

            Long generationId = fixture.convertedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), List.of("INSTAGRAM", "THREADS"));
            ContentSaveResponse saved = fixture.contentsOfGeneration(signup.accessToken(), generationId);

            assertThat(saved.contentId()).isNotEqualTo(origin.contentId());
            assertThat(saved.contents()).extracting(ContentChannelSummaryResponse::channel)
                .containsExactlyInAnyOrder("INSTAGRAM", "THREADS");
            fixture.getContent(signup.accessToken(), saved.contentId())
                .expectStatus().isOk()
                .expectBody(ContentDetailResponse.class)
                .value(body -> assertThat(body.contents()).hasSize(2));
        }

        @Test
        @DisplayName("성공하면 신규 생성과 같은 규칙으로 채널 수만큼 차감된다")
        void deductsPerChannel_whenConversionSucceeded() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-deduct");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(95);

            Long generationId = fixture.convertedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), List.of("INSTAGRAM", "THREADS"));

            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(85);
            assertThat(database.deductionsOf(database.memberIdOf("naver-convert-deduct")))
                .filteredOn(deduction -> deduction.getGenerationId().equals(generationId))
                .singleElement()
                .satisfies(deduction -> assertThat(deduction.getAmount()).isEqualTo(-10));
        }

        @Test
        @DisplayName("만든 뒤 원본을 삭제해도 작업과 결과는 그대로 남는다")
        void keepsConversionResult_whenOriginDeletedAfterwards() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-origin-deleted");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());
            Long generationId = fixture.convertedGenerationId(signup.accessToken(), origin.contentId(),
                origin.contents().getFirst().contentChannelId(), List.of("INSTAGRAM"));

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
        @DisplayName("같은 원본으로 여러 번 만들면 작업이 각각 쌓인다")
        void createsIndependentGenerations_whenConvertedRepeatedly() {
            SignupResponse signup = fixture.signupActiveMember("naver-convert-repeat");
            ContentSaveResponse origin = savedBlogOrigin(signup.accessToken());
            Long contentChannelId = origin.contents().getFirst().contentChannelId();

            Long first = fixture.convertedGenerationId(signup.accessToken(), origin.contentId(), contentChannelId,
                List.of("INSTAGRAM"));
            Long second = fixture.convertedGenerationId(signup.accessToken(), origin.contentId(), contentChannelId,
                List.of("INSTAGRAM"));

            assertThat(first).isNotEqualTo(second);
            assertThat(database.generationsOf(database.memberIdOf("naver-convert-repeat"))).hasSize(3);
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(85);
        }
    }
}
