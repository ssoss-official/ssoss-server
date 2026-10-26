package com.ssoss.ssossbackend.content.entrypoint.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ssoss.ssossbackend.auth.domain.model.AuthErrorCode;
import com.ssoss.ssossbackend.auth.entrypoint.response.SignupResponse;
import com.ssoss.ssossbackend.auth.entrypoint.response.SocialLoginResponse;
import com.ssoss.ssossbackend.content.domain.model.ContentErrorCode;
import com.ssoss.ssossbackend.content.domain.model.Generation;
import com.ssoss.ssossbackend.content.domain.model.GenerationResult;
import com.ssoss.ssossbackend.content.domain.model.GenerationResultStatus;
import com.ssoss.ssossbackend.content.entrypoint.response.GenerationChannelResultResponse;
import com.ssoss.ssossbackend.content.entrypoint.response.GenerationDetailResponse;
import com.ssoss.ssossbackend.content.entrypoint.response.GenerationStartResponse;
import com.ssoss.ssossbackend.credit.domain.model.CreditErrorCode;
import com.ssoss.ssossbackend.credit.entrypoint.response.CreditBalanceResponse;
import com.ssoss.ssossbackend.credit.entrypoint.scheduler.CreditCycleScheduler;
import com.ssoss.ssossbackend.shared.exception.CommonErrorCode;
import com.ssoss.ssossbackend.shared.exception.ErrorResponse;
import com.ssoss.ssossbackend.support.IntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("생성 작업 API")
class GenerationApiTest extends IntegrationTest {

    @Autowired
    private CreditCycleScheduler creditCycleScheduler;

    @Nested
    @DisplayName("POST /v1/generations")
    class Start {

        @Test
        @DisplayName("생성을 요청하면 작업 id 와 Location 헤더를 즉시 반환한다")
        void returnsGenerationIdImmediately_whenGenerationRequested() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-start");

            fixture.startGeneration(signup.accessToken(), List.of("BLOG"))
                .expectStatus().isCreated()
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectBody(GenerationStartResponse.class)
                .value(body -> assertThat(body.generationId()).isNotNull());
        }

        @Test
        @DisplayName("진행 중 작업이 있으면 새 생성이 409 로 거부된다")
        void returns409_whenInProgressGenerationExists() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-conflict");
            taskExecutor.hold();
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            fixture.startGeneration(signup.accessToken(), List.of("INSTAGRAM"))
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code())
                    .isEqualTo(ContentErrorCode.GENERATION_IN_PROGRESS_EXISTS.getCode()));
        }

        @Test
        @DisplayName("완료된 작업만 있으면 새 생성이 허용된다")
        void allowsNewGeneration_whenPreviousGenerationCompleted() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-again");
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            fixture.startGeneration(signup.accessToken(), List.of("INSTAGRAM"))
                .expectStatus().isCreated();
        }

        @Test
        @DisplayName("강조 내용이 없으면 400 을 반환한다")
        void returns400_whenEmphasisMissing() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-no-emphasis");

            fixture.startGeneration(signup.accessToken(), Map.of(
                    "channels", List.of("BLOG"),
                    "purpose", "INFORMATION",
                    "tone", "CASUAL"))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("톤이 없으면 400 을 반환한다")
        void returns400_whenToneMissing() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-no-tone");

            fixture.startGeneration(signup.accessToken(), Map.of(
                    "channels", List.of("BLOG"),
                    "purpose", "INFORMATION",
                    "emphasis", "테스트 강조 내용"))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("채널을 하나도 고르지 않으면 400 을 반환한다")
        void returns400_whenChannelsEmpty() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-no-channel");

            fixture.startGeneration(signup.accessToken(), List.of())
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("키워드를 10개 넘게 보내면 400 을 반환한다")
        void returns400_whenKeywordsExceedLimit() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-many-keywords");

            fixture.startGeneration(signup.accessToken(), Map.of(
                    "channels", List.of("BLOG"),
                    "purpose", "INFORMATION",
                    "tone", "CASUAL",
                    "emphasis", "테스트 강조 내용",
                    "keywords", List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("빈 키워드를 보내면 400 을 반환한다")
        void returns400_whenKeywordBlank() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-blank-keyword");

            fixture.startGeneration(signup.accessToken(), Map.of(
                    "channels", List.of("BLOG"),
                    "purpose", "INFORMATION",
                    "tone", "CASUAL",
                    "emphasis", "테스트 강조 내용",
                    "keywords", List.of("디저트", "   ")))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("키워드 하나가 30자를 넘으면 400 을 반환한다")
        void returns400_whenKeywordTooLong() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-long-keyword");

            fixture.startGeneration(signup.accessToken(), Map.of(
                    "channels", List.of("BLOG"),
                    "purpose", "INFORMATION",
                    "tone", "CASUAL",
                    "emphasis", "테스트 강조 내용",
                    "keywords", List.of("가".repeat(31))))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("같은 채널을 중복 선택하면 400 을 반환한다")
        void returns400_whenChannelsDuplicated() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-dup-channel");

            fixture.startGeneration(signup.accessToken(), List.of("BLOG", "BLOG"))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("대소문자만 다른 같은 채널을 선택해도 중복으로 400 을 반환한다")
        void returns400_whenChannelsDuplicatedWithDifferentCase() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-case-dup-channel");

            fixture.startGeneration(signup.accessToken(), List.of("BLOG", "blog"))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("같은 회원이 동시에 요청하면 하나만 생성되고 나머지는 409 로 거부된다")
        void createsOnlyOne_whenSameMemberRequestsConcurrently() throws Exception {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-concurrent");
            taskExecutor.hold();
            CyclicBarrier barrier = new CyclicBarrier(2);
            Callable<Integer> attempt = () -> {
                barrier.await();
                return fixture.startGeneration(signup.accessToken(), List.of("BLOG"))
                    .expectBody(String.class)
                    .returnResult()
                    .getStatus()
                    .value();
            };

            List<Integer> statuses;
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                statuses = executor.invokeAll(List.of(attempt, attempt)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
            }

            assertThat(statuses).containsExactlyInAnyOrder(
                HttpStatus.CREATED.value(), HttpStatus.CONFLICT.value());
            assertThat(database.generationsOf(database.memberIdOf("naver-gen-concurrent"))).hasSize(1);
        }

        @Test
        @DisplayName("가입 대기(PENDING) 토큰으로 요청하면 403 과 A0007 을 반환한다")
        void returns403_whenPendingTokenRequests() {
            SocialLoginResponse login = fixture.naverLoginMember("naver-gen-pending");

            fixture.startGeneration(login.accessToken(), List.of("BLOG"))
                .expectStatus().isForbidden()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(AuthErrorCode.ACCESS_DENIED.getCode()));
        }
    }

    @Nested
    @DisplayName("GET /v1/generations/{generationId}")
    class GetById {

        @Test
        @DisplayName("블로그 생성이 성공하면 SUCCEEDED 와 제목·본문·해시태그가 반환된다")
        void returnsBlogResult_whenGenerationSucceeded() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-blog");
            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo("SUCCEEDED");
                    assertThat(body.results()).singleElement().satisfies(result -> {
                        assertThat(result.channel()).isEqualTo("BLOG");
                        assertThat(result.title()).isNotBlank();
                        assertThat(result.body()).isNotBlank();
                        assertThat(result.hashtags()).isNotEmpty().allSatisfy(tag -> assertThat(tag).startsWith("#"));
                    });
                });
        }

        @Test
        @DisplayName("제목 없는 채널의 결과는 제목이 null 이다")
        void returnsNullTitle_whenChannelHasNoTitle() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-insta");
            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("INSTAGRAM"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results()).singleElement().satisfies(result -> {
                    assertThat(result.title()).isNull();
                    assertThat(result.body()).isNotBlank();
                }));
        }

        @Test
        @DisplayName("선택한 채널이 요청한 순서 그대로 결과에 담긴다")
        void returnsResultPerSelectedChannelInRequestedOrder() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-multi");
            Long generationId = fixture.startedGenerationId(signup.accessToken(),
                List.of("INSTAGRAM", "BLOG", "DAANGN_BIZ", "THREADS"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo("SUCCEEDED");
                    assertThat(body.results())
                        .extracting(GenerationChannelResultResponse::channel)
                        .containsExactly("INSTAGRAM", "BLOG", "DAANGN_BIZ", "THREADS");
                    assertThat(body.results())
                        .filteredOn(result -> result.channel().equals("BLOG"))
                        .singleElement()
                        .satisfies(result -> assertThat(result.title()).isNotBlank());
                    assertThat(body.results())
                        .filteredOn(result -> !result.channel().equals("BLOG"))
                        .allSatisfy(result -> assertThat(result.title()).isNull());
                });
        }

        @Test
        @DisplayName("생성 조건인 목적·톤·키워드가 함께 반환된다")
        void returnsGenerationConditions_whenQueried() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-condition");
            Long generationId = fixture.startGeneration(signup.accessToken(), Map.of(
                    "channels", List.of("BLOG"),
                    "purpose", "EVENT_DISCOUNT",
                    "tone", "EMOTIONAL",
                    "emphasis", "테스트 강조 내용",
                    "keywords", List.of("디저트, 크루아상", "을지로 \"베이커리\"")))
                .expectStatus().isCreated()
                .expectBody(GenerationStartResponse.class)
                .returnResult()
                .getResponseBody()
                .generationId();

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.purpose()).isEqualTo("EVENT_DISCOUNT");
                    assertThat(body.tone()).isEqualTo("EMOTIONAL");
                    assertThat(body.keywords()).containsExactly("디저트, 크루아상", "을지로 \"베이커리\"");
                });
        }

        @Test
        @DisplayName("키워드를 보내지 않으면 빈 배열로 반환된다")
        void returnsEmptyKeywords_whenNotGiven() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-no-keyword");
            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.keywords()).isEmpty());
        }

        @Test
        @DisplayName("작업이 끝나기 전에는 상태만 진행 중으로 오고 결과가 담기지 않는다")
        void returnsNoResults_whenGenerationInProgress() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-progress");
            taskExecutor.hold();
            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo("IN_PROGRESS");
                    assertThat(body.results()).isEmpty();
                });

            taskExecutor.release();

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo("SUCCEEDED");
                    assertThat(body.results())
                        .extracting(GenerationChannelResultResponse::channel)
                        .containsExactly("BLOG", "INSTAGRAM");
                });
        }

        @Test
        @DisplayName("모든 채널이 실패하면 FAILED 로 오고 결과가 담기지 않는다")
        void returnsNoResults_whenAllChannelsFail() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-all-fail");
            llmApi.stubFailure(429);
            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo("FAILED");
                    assertThat(body.results()).isEmpty();
                });
        }

        @Test
        @DisplayName("deadline 이 지나도록 끝나지 않으면 FAILED 로 오고 결과가 담기지 않는다")
        void returnsNoResults_whenDeadlinePassedWithoutFinish() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-timeout");
            taskExecutor.hold();
            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            clock.advanceBy(Generation.DEADLINE.plusSeconds(1));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo("FAILED");
                    assertThat(body.results()).isEmpty();
                });
        }

        @Test
        @DisplayName("채널 하나가 실패하면 작업 전체가 FAILED 로 오고 성공한 채널의 결과도 담기지 않는다")
        void returnsNoResults_whenOneChannelFails() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-one-fail");
            llmApi.stubEmptyBodyForUntitled();
            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM"));

            assertThat(database.resultsOf(generationId))
                .extracting(GenerationResult::getStatus)
                .containsExactlyInAnyOrder(GenerationResultStatus.SUCCEEDED, GenerationResultStatus.EMPTY_OUTPUT);
            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo("FAILED");
                    assertThat(body.results()).isEmpty();
                });
        }

        @Test
        @DisplayName("없는 작업을 조회하면 404 와 CT0002 를 반환한다")
        void returns404_whenGenerationDoesNotExist() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-not-found");

            fixture.getGeneration(signup.accessToken(), 999_999L)
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(ContentErrorCode.GENERATION_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("다른 회원의 작업을 조회하면 404 와 CT0002 를 반환한다")
        void returns404_whenQueryingOtherMembersGeneration() {
            SignupResponse owner = fixture.signupActiveMember("naver-gen-owner");
            Long generationId = fixture.startedGenerationId(owner.accessToken(), List.of("BLOG"));
            SignupResponse other = fixture.signupActiveMember("naver-gen-other");

            fixture.getGeneration(other.accessToken(), generationId)
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(ContentErrorCode.GENERATION_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("액세스 토큰 없이 조회하면 401 과 A0006 을 반환한다")
        void returns401_whenAccessTokenMissing() {
            fixture.client().get().uri("/v1/generations/1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN.getCode()));
        }
    }

    @Nested
    @DisplayName("LLM 요청")
    class LlmRequest {

        @Test
        @DisplayName("LLM 요청에 강조·금지·키워드·톤·목적 지시가 실려 있다")
        void carriesAllInstructions_inLlmRequest() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-prompt");

            fixture.startGeneration(signup.accessToken(), Map.of(
                    "channels", List.of("BLOG"),
                    "purpose", "EVENT_DISCOUNT",
                    "tone", "EMOTIONAL",
                    "emphasis", "주말 아메리카노 1+1 이벤트",
                    "forbidden", "가격 인상 언급",
                    "keywords", List.of("디저트 맛집", "데이트 코스")))
                .expectStatus().isCreated();

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .contains("주말 아메리카노 1+1 이벤트")
                .contains("가격 인상 언급")
                .contains("디저트 맛집")
                .contains("데이트 코스")
                .contains("감성")
                .contains("이벤트")
                .contains("할인"));
        }

        @Test
        @DisplayName("채널 수만큼 LLM 요청이 발생한다")
        void sendsOneLlmRequestPerChannel() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-fanout");

            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM", "THREADS"));

            assertThat(llmApi.recordedRequestBodies()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("매장 정보 재료")
    class StoreSection {

        @Test
        @DisplayName("매장 정보를 입력한 회원이 생성을 요청하면 LLM 요청에 매장 값이 실린다")
        void carriesStoreValues_whenStoreWritten() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-store");
            fixture.saveStoreBasicInfo(signup.accessToken(), fixture.storeBasicInfoBody("보니스커피", "BAKERY_CAFE",
                    "서울 중구 을지로 100", "을지로 크루아상 카페"))
                .expectStatus().isNoContent();
            fixture.saveStoreOperationInfo(signup.accessToken(), fixture.storeOperationInfoBody(
                    List.of("MONDAY", "TUESDAY"), "09:00", "22:00", List.of("크루아상", "아메리카노"), true, false, true))
                .expectStatus().isNoContent();

            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .contains("매장명: 보니스커피")
                .contains("매장 유형: 베이커리 카페")
                .contains("주소: 서울 중구 을지로 100")
                .contains("한 줄 소개: 을지로 크루아상 카페")
                .contains("영업일: 월요일, 화요일")
                .contains("영업 시간: 09:00~22:00")
                .contains("대표 메뉴: 크루아상, 아메리카노")
                .contains("편의 시설: 포장 가능, 주차 가능")
                .doesNotContain("매장 정보가 제공되지 않았다"));
        }

        @Test
        @DisplayName("매장 콘텐츠 정보를 입력해도 그 값은 LLM 요청에 실리지 않는다")
        void omitsContentInfo_whenContentInfoWritten() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-store-content");
            fixture.saveStoreBasicInfo(signup.accessToken(), fixture.storeBasicInfoBody("보니스커피", "CAFE",
                    "서울 중구 을지로 100", "을지로 크루아상 카페"))
                .expectStatus().isNoContent();
            fixture.saveStoreContentInfo(signup.accessToken(), fixture.storeContentInfoBody("직접 굽는 크루아상",
                    List.of("스페셜티 원두"), "최저가 표현", "INFORMATIVE"))
                .expectStatus().isNoContent();

            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .contains("보니스커피")
                .doesNotContain("직접 굽는 크루아상", "스페셜티 원두", "최저가 표현", "정보형"));
        }

        @Test
        @DisplayName("기본 정보를 건너뛰고 운영 정보만 저장해도 그 값이 LLM 요청에 실린다")
        void carriesOperationValues_whenBasicInfoSkipped() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-store-operation");
            fixture.saveStoreOperationInfo(signup.accessToken(), fixture.storeOperationInfoBody(
                    List.of("MONDAY", "TUESDAY"), "09:00", "22:00", List.of("크루아상"), true, false, false))
                .expectStatus().isNoContent();

            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            assertThat(llmApi.recordedRequestBodies()).singleElement().satisfies(request -> assertThat(request)
                .contains("영업일: 월요일, 화요일")
                .contains("영업 시간: 09:00~22:00")
                .contains("대표 메뉴: 크루아상")
                .contains("편의 시설: 포장 가능")
                .doesNotContain("매장명:", "매장 정보가 제공되지 않았다"));
        }

        @Test
        @DisplayName("매장 정보를 채우지 않은 회원도 생성이 되고 매장 정보가 없다는 문구가 실린다")
        void carriesNoStoreNotice_whenOnboardingSkipped() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-no-store");

            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            assertThat(llmApi.recordedRequestBodies()).singleElement()
                .satisfies(request -> assertThat(request).contains("매장 정보가 제공되지 않았다"));
            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.status()).isEqualTo("SUCCEEDED"));
        }

        @Test
        @DisplayName("채널을 여럿 고르면 모든 채널 요청이 같은 매장 값을 싣는다")
        void carriesSameStoreValues_acrossChannels() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-store-fanout");
            fixture.saveStoreBasicInfo(signup.accessToken(), fixture.storeBasicInfoBody("보니스커피", "CAFE",
                    "서울 중구 을지로 100", "을지로 크루아상 카페"))
                .expectStatus().isNoContent();

            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM", "THREADS"));

            assertThat(llmApi.recordedRequestBodies()).hasSize(3)
                .allSatisfy(request -> assertThat(request).contains("매장명: 보니스커피"));
        }
    }

    @Nested
    @DisplayName("사진 가이드")
    class PhotoGuide {

        @Test
        @DisplayName("사진 가이드를 체크하면 본문에 사진 가이드 태그가 조립되어 담긴다")
        void assemblesTagsIntoBody_whenPhotoGuideChecked() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-guide-on");
            Long generationId = fixture.photoGuidedGenerationId(signup.accessToken(), List.of("BLOG"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results()).singleElement().satisfies(result -> {
                    assertThat(result.body())
                        .contains("<photo-guide title=\"사진 제목 1\" description=\"사진 설명 1\"/>")
                        .contains("<photo-guide title=\"사진 제목 2\" description=\"사진 설명 2\"/>")
                        .doesNotContain("<photo-guide/>");
                }));
        }

        @Test
        @DisplayName("제목 없는 채널도 사진 가이드 태그가 조립되어 담긴다")
        void assemblesTagsIntoBody_whenChannelHasNoTitle() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-guide-untitled");
            Long generationId = fixture.photoGuidedGenerationId(signup.accessToken(), List.of("INSTAGRAM"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results()).singleElement().satisfies(result -> {
                    assertThat(result.title()).isNull();
                    assertThat(result.body())
                        .contains("<photo-guide title=\"사진 제목 1\" description=\"사진 설명 1\"/>")
                        .doesNotContain("<photo-guide/>");
                }));
        }

        @Test
        @DisplayName("블로그는 사진 가이드 태그가 문단 사이에 들어간다")
        void placesTagsBetweenParagraphs_whenChannelBlog() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-guide-blog-position");
            Long generationId = fixture.photoGuidedGenerationId(signup.accessToken(), List.of("BLOG"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results()).singleElement()
                    .satisfies(result -> assertThat(result.body()).isEqualTo("""
                        테스트 본문
                        <photo-guide title="사진 제목 1" description="사진 설명 1"/>
                        이어지는 본문 1
                        <photo-guide title="사진 제목 2" description="사진 설명 2"/>
                        이어지는 본문 2""")));
        }

        @Test
        @DisplayName("인스타그램·당근 비즈·스레드는 사진 가이드 태그가 본문 맨 위에 모이고 그 아래에 본문이 이어진다")
        void placesTagsAboveBody_whenChannelIsNotBlog() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-guide-top-position");
            Long generationId = fixture.photoGuidedGenerationId(signup.accessToken(),
                List.of("INSTAGRAM", "DAANGN_BIZ", "THREADS"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results()).hasSize(3)
                    .allSatisfy(result -> assertThat(result.body()).isEqualTo("""
                        <photo-guide title="사진 제목 1" description="사진 설명 1"/>
                        <photo-guide title="사진 제목 2" description="사진 설명 2"/>
                        테스트 본문

                        이어지는 본문 1

                        이어지는 본문 2""")));
        }

        @Test
        @DisplayName("사진 가이드를 체크하지 않으면 본문에 사진 가이드 태그가 없다")
        void keepsBodyPlain_whenPhotoGuideUnchecked() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-guide-off");
            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results()).singleElement().satisfies(result -> {
                    assertThat(result.body()).doesNotContain("<photo-guide");
                }));
        }

        @Test
        @DisplayName("마커가 카드보다 많아도 채널이 성공하고 짝지어진 만큼만 조립된다")
        void succeedsWithPairedTagsOnly_whenMarkersOutnumberGuides() {
            llmApi.stubPhotoGuides(3, 1);
            SignupResponse signup = fixture.signupActiveMember("naver-gen-guide-many-markers");
            Long generationId = fixture.photoGuidedGenerationId(signup.accessToken(), List.of("BLOG"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results()).singleElement().satisfies(result -> {
                    assertThat(result.body())
                        .containsOnlyOnce("<photo-guide title=")
                        .doesNotContain("<photo-guide/>");
                }));
        }

        @Test
        @DisplayName("카드가 마커보다 많아도 채널이 성공하고 자리 없는 카드는 버려진다")
        void succeedsDroppingExtraGuides_whenGuidesOutnumberMarkers() {
            llmApi.stubPhotoGuides(1, 3);
            SignupResponse signup = fixture.signupActiveMember("naver-gen-guide-many-guides");
            Long generationId = fixture.photoGuidedGenerationId(signup.accessToken(), List.of("BLOG"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results()).singleElement().satisfies(result -> {
                    assertThat(result.body())
                        .containsOnlyOnce("<photo-guide title=")
                        .contains("사진 제목 1")
                        .doesNotContain("사진 제목 2");
                }));
        }

        @Test
        @DisplayName("사진 가이드를 체크했을 때만 LLM 출력 스키마에 배열 필드가 실린다")
        void carriesPhotoGuidesField_onlyWhenChecked() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-guide-schema");

            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));
            assertThat(llmApi.recordedOutputSchemas()).singleElement()
                .satisfies(schema -> assertThat(schema).contains("title").doesNotContain("photoGuides"));

            llmApi.reset();
            fixture.photoGuidedGenerationId(signup.accessToken(), List.of("BLOG"));
            assertThat(llmApi.recordedOutputSchemas()).singleElement()
                .satisfies(schema -> assertThat(schema).contains("photoGuides").doesNotContain("마커"));
        }
    }

    @Nested
    @DisplayName("채널별 해시태그 정책")
    class ChannelHashtagPolicy {

        @Test
        @DisplayName("당근 비즈만 LLM 출력 스키마에서 해시태그 필드가 빠진다")
        void omitsHashtagsField_onlyForDaangnBiz() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-tag-schema");

            fixture.startedGenerationId(signup.accessToken(), List.of("DAANGN_BIZ"));
            assertThat(llmApi.recordedOutputSchemas()).singleElement()
                .satisfies(schema -> assertThat(schema).contains("paragraphs").doesNotContain("hashtags"));

            llmApi.reset();
            fixture.startedGenerationId(signup.accessToken(), List.of("THREADS"));
            assertThat(llmApi.recordedOutputSchemas()).singleElement()
                .satisfies(schema -> assertThat(schema).contains("hashtags"));
        }

        @Test
        @DisplayName("당근 비즈 결과의 해시태그는 빈 배열이다")
        void returnsEmptyHashtags_whenChannelIsDaangnBiz() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-tag-daangn");
            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("DAANGN_BIZ"));

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectStatus().isOk()
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.results()).singleElement().satisfies(result -> {
                    assertThat(result.body()).isNotBlank();
                    assertThat(result.hashtags()).isEmpty();
                }));
        }

        @Test
        @DisplayName("당근 비즈 요청에는 해시태그를 만들지 않는다는 지시가 실린다")
        void carriesNoHashtagInstruction_whenChannelIsDaangnBiz() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-tag-instruction");

            fixture.startedGenerationId(signup.accessToken(), List.of("DAANGN_BIZ"));

            assertThat(llmApi.recordedRequestBodies()).singleElement()
                .satisfies(request -> assertThat(request).contains("해시태그를 만들지 않는다"));
        }
    }

    @Nested
    @DisplayName("결과 상태 기록")
    class ResultStatusRecord {

        @Test
        @DisplayName("성공한 채널은 SUCCEEDED 행으로 토큰 사용량·원문과 함께 기록된다")
        void recordsSucceededResultWithTokens_whenChannelSucceeds() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-obs-success");

            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM"));

            assertThat(database.resultsOf(generationId)).hasSize(2).allSatisfy(result -> {
                assertThat(result.getStatus()).isEqualTo(GenerationResultStatus.SUCCEEDED);
                assertThat(result.getInputTokens()).isEqualTo(10);
                assertThat(result.getOutputTokens()).isEqualTo(20);
                assertThat(result.getRawResponse()).contains("테스트 본문");
            });
        }

        @Test
        @DisplayName("LLM 이 429 를 반환하면 RATE_LIMITED 행으로 기록되고 작업이 실패로 조회된다")
        void recordsRateLimitedResult_whenLlmReturns429() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-obs-429");
            llmApi.stubFailure(429);

            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            assertThat(database.resultsOf(generationId)).singleElement().satisfies(result -> {
                assertThat(result.getStatus()).isEqualTo(GenerationResultStatus.RATE_LIMITED);
                assertThat(result.getInputTokens()).isNull();
                assertThat(result.getBody()).isNull();
            });
            fixture.getGeneration(signup.accessToken(), generationId)
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.status()).isEqualTo("FAILED"));
        }

        @Test
        @DisplayName("LLM 이 5xx 를 반환하면 SERVER_ERROR 행으로 기록된다")
        void recordsServerErrorResult_whenLlmReturns5xx() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-obs-500");
            llmApi.stubFailure(500);

            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            assertThat(database.resultsOf(generationId)).singleElement().satisfies(result ->
                assertThat(result.getStatus()).isEqualTo(GenerationResultStatus.SERVER_ERROR));
        }

        @Test
        @DisplayName("응답이 왔지만 변환에 실패한 산출은 토큰·원문과 함께 EMPTY_OUTPUT 으로 기록된다")
        void recordsEmptyOutputWithTokens_whenContentUnparseable() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-obs-malformed");
            llmApi.stubMalformedContent();

            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            assertThat(database.resultsOf(generationId)).singleElement().satisfies(result -> {
                assertThat(result.getStatus()).isEqualTo(GenerationResultStatus.EMPTY_OUTPUT);
                assertThat(result.getInputTokens()).isEqualTo(10);
                assertThat(result.getOutputTokens()).isEqualTo(20);
                assertThat(result.getRawResponse()).isEqualTo("이건 JSON 이 아닙니다");
            });
        }

        @Test
        @DisplayName("본문이 빈 산출은 EMPTY_OUTPUT 행으로 기록되고 작업이 실패로 조회된다")
        void recordsEmptyOutputResult_whenBodyBlank() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-obs-empty");
            llmApi.stubEmptyBody();

            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("INSTAGRAM"));

            assertThat(database.resultsOf(generationId)).singleElement().satisfies(result -> {
                assertThat(result.getStatus()).isEqualTo(GenerationResultStatus.EMPTY_OUTPUT);
                assertThat(result.getBody()).isNull();
            });
            fixture.getGeneration(signup.accessToken(), generationId)
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.status()).isEqualTo("FAILED"));
        }
    }

    @Nested
    @DisplayName("크레딧 차감")
    class Deduction {

        @Test
        @DisplayName("작업이 성공하면 채널 수 × 5 가 작업 참조와 함께 한 번에 차감된다")
        void deductsChannelCountTimesFive_whenGenerationSucceeded() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-credit-deduct");

            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM"));

            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(40);
            assertThat(database.deductionsOf(database.memberIdOf("naver-gen-credit-deduct"))).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAmount()).isEqualTo(-10);
                    assertThat(entry.getGenerationId()).isEqualTo(generationId);
                });
        }

        @Test
        @DisplayName("채널 하나가 실패하면 성공한 채널 몫도 차감되지 않는다")
        void doesNotDeduct_whenOneChannelFails() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-credit-one-fail");
            llmApi.stubEmptyBodyForUntitled();

            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM"));

            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(50);
            assertThat(database.deductionsOf(database.memberIdOf("naver-gen-credit-one-fail"))).isEmpty();
        }

        @Test
        @DisplayName("전 채널이 실패하면 차감되지 않는다")
        void doesNotDeduct_whenAllChannelsFail() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-credit-fail");
            llmApi.stubFailure(429);

            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM"));

            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(50);
            assertThat(database.deductionsOf(database.memberIdOf("naver-gen-credit-fail"))).isEmpty();
        }

        @Test
        @DisplayName("같은 입력으로 다시 만들어도 같은 규칙으로 차감된다")
        void deductsAgain_whenRegeneratedWithSameInput() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-credit-again");

            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(40);
            assertThat(database.deductionsOf(database.memberIdOf("naver-gen-credit-again"))).hasSize(2);
        }

        @Test
        @DisplayName("deadline 이 지나도록 결과가 확정되지 않은 작업은 실패로 조회되고 차감되지 않는다")
        void doesNotDeduct_whenGenerationExpiredWithoutSettledResult() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-credit-late");
            taskExecutor.hold();
            Long generationId = fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            clock.advanceBy(Generation.DEADLINE.plusSeconds(1));
            taskExecutor.release();

            fixture.getGeneration(signup.accessToken(), generationId)
                .expectBody(GenerationDetailResponse.class)
                .value(body -> assertThat(body.status()).isEqualTo("FAILED"));
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(50);
            assertThat(database.deductionsOf(database.memberIdOf("naver-gen-credit-late"))).isEmpty();
        }

        @Test
        @DisplayName("사이클 경계를 넘겨 배치가 돌면 잔액이 50 으로 돌아온다")
        void returnsFullBalance_whenCycleBoundaryPassedAndBatchRan() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-credit-cycle");
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM"));
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(40);

            clock.advanceBy(Duration.ofDays(31));
            creditCycleScheduler.renewCycles();

            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("크레딧 부족 판정")
    class InsufficiencyCheck {

        private static final List<String> ALL_CHANNELS = List.of("BLOG", "INSTAGRAM", "DAANGN_BIZ", "THREADS");

        @Test
        @DisplayName("잔액이 차감량 × 선택 채널 수보다 적으면 400 으로 거부되고 작업이 생성되지 않는다")
        void returns400AndCreatesNoGeneration_whenBalanceInsufficient() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-credit-short");
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(10);

            fixture.startGeneration(signup.accessToken(), ALL_CHANNELS)
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CreditErrorCode.CREDIT_INSUFFICIENT.getCode()));

            assertThat(database.generationsOf(database.memberIdOf("naver-gen-credit-short"))).hasSize(2);
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(10);
        }

        @Test
        @DisplayName("잔액을 소진할 때까지 생성하면 다음 생성이 거부된다")
        void rejectsNextGeneration_whenBalanceExhausted() {
            SignupResponse signup = fixture.signupActiveMember("naver-gen-credit-exhaust");
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), ALL_CHANNELS);
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG", "INSTAGRAM"));
            assertThat(fixture.creditBalance(signup.accessToken()).balance()).isEqualTo(0);

            fixture.startGeneration(signup.accessToken(), List.of("BLOG"))
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CreditErrorCode.CREDIT_INSUFFICIENT.getCode()));
        }
    }
}
