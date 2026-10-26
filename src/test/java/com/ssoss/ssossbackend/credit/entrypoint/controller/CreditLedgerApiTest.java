package com.ssoss.ssossbackend.credit.entrypoint.controller;

import java.time.Instant;
import java.util.List;

import com.ssoss.ssossbackend.auth.domain.model.AuthErrorCode;
import com.ssoss.ssossbackend.auth.entrypoint.response.SignupResponse;
import com.ssoss.ssossbackend.auth.entrypoint.response.SocialLoginResponse;
import com.ssoss.ssossbackend.credit.domain.model.CreditLedger;
import com.ssoss.ssossbackend.credit.domain.model.CreditLedgerType;
import com.ssoss.ssossbackend.credit.entrypoint.response.CreditBalanceResponse;
import com.ssoss.ssossbackend.credit.entrypoint.response.CreditLedgerListResponse;
import com.ssoss.ssossbackend.credit.entrypoint.response.CreditLedgerResponse;
import com.ssoss.ssossbackend.credit.entrypoint.scheduler.CreditCycleScheduler;
import com.ssoss.ssossbackend.shared.exception.CommonErrorCode;
import com.ssoss.ssossbackend.shared.exception.ErrorResponse;
import com.ssoss.ssossbackend.support.IntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("크레딧 내역 조회 API")
class CreditLedgerApiTest extends IntegrationTest {

    private static final Instant AUGUST_CYCLE = Instant.parse("2099-08-10T00:00:00Z");
    private static final Instant SEPTEMBER_CYCLE = Instant.parse("2099-09-05T00:00:00Z");

    @Autowired
    private CreditCycleScheduler creditCycleScheduler;

    @Nested
    @DisplayName("GET /v1/credits/me/ledgers")
    class ListLedgers {

        @Test
        @DisplayName("가입 직후 조회하면 가입 지급 행 하나가 보인다")
        void returnsSignupGrantRow_whenActiveMemberQueriesRightAfterSignup() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-signup");

            CreditLedgerListResponse body = fixture.creditLedgerList(signup.accessToken(), "");

            assertThat(body.totalCount()).isEqualTo(1);
            assertThat(body.ledgers()).singleElement().satisfies(ledger -> {
                assertThat(ledger.ledgerId()).isNotNull();
                assertThat(ledger.type()).isEqualTo("GRANT");
                assertThat(ledger.description()).isEqualTo("가입 크레딧 지급");
                assertThat(ledger.amount()).isEqualTo(100);
                assertThat(ledger.occurredAt()).isNotNull();
            });
        }

        @Test
        @DisplayName("여러 채널을 한 번에 생성하면 차감이 한 행으로 남고 고정 순서의 첫 채널에 나머지 건수가 붙는다")
        void listsLeadingChannelWithRemainingCount_whenMultiChannelGenerationSucceeds() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-multi");
            fixture.startedGenerationId(signup.accessToken(), List.of("THREADS", "BLOG", "INSTAGRAM"));

            CreditLedgerListResponse body = fixture.creditLedgerList(signup.accessToken(), "?type=USE");

            assertThat(body.ledgers()).singleElement().satisfies(ledger -> {
                assertThat(ledger.type()).isEqualTo("DEDUCT");
                assertThat(ledger.description()).isEqualTo("블로그 외 2건 콘텐츠 생성");
                assertThat(ledger.amount()).isEqualTo(-15);
            });
        }

        @Test
        @DisplayName("블로그 없이 여러 채널을 생성하면 고정 순서에서 앞선 채널이 첫 채널이 된다")
        void listsNextChannelInFixedOrder_whenBlogNotChosen() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-no-blog");
            fixture.startedGenerationId(signup.accessToken(), List.of("THREADS", "DAANGN_BIZ", "INSTAGRAM"));

            CreditLedgerListResponse body = fixture.creditLedgerList(signup.accessToken(), "?type=USE");

            assertThat(body.ledgers()).singleElement().satisfies(ledger ->
                assertThat(ledger.description()).isEqualTo("인스타그램 외 2건 콘텐츠 생성"));
        }

        @Test
        @DisplayName("단일 채널로 생성하면 그 채널만 적힌 차감 행이 남는다")
        void listsSingleChannelDeduction_whenOneChannelGenerationSucceeds() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-single");
            fixture.startedGenerationId(signup.accessToken(), List.of("DAANGN_BIZ"));

            CreditLedgerListResponse body = fixture.creditLedgerList(signup.accessToken(), "?type=USE");

            assertThat(body.ledgers()).singleElement().satisfies(ledger -> {
                assertThat(ledger.description()).isEqualTo("당근 비즈 콘텐츠 생성");
                assertThat(ledger.amount()).isEqualTo(-5);
            });
        }

        @Test
        @DisplayName("사이클 배치가 지급하면 사이클 월을 붙인 지급 행이 보인다")
        void listsCycleGrantLabeledWithCycleMonth_whenBatchGrantsNewCycle() {
            clock.moveTo(AUGUST_CYCLE);
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-cycle");
            clock.moveTo(SEPTEMBER_CYCLE);
            creditCycleScheduler.renewCycles();

            CreditLedgerListResponse body = fixture.creditLedgerList(signup.accessToken(), "?type=GAIN");

            assertThat(body.ledgers()).extracting(CreditLedgerResponse::description)
                .containsExactly("9월 크레딧 지급", "가입 크레딧 지급");
        }

        @Test
        @DisplayName("무료 크레딧이 소멸해도 전체·사용·지급 어느 조회에도 소멸 행은 담기지 않는다")
        void omitsExpireRowFromEveryQuery_whenFreeCreditExpired() {
            clock.moveTo(AUGUST_CYCLE);
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-expire");
            clock.moveTo(SEPTEMBER_CYCLE);
            creditCycleScheduler.renewCycles();

            List<String> everyQuery = List.of("", "?type=ALL", "?type=USE", "?type=GAIN");

            assertThat(everyQuery).allSatisfy(query ->
                assertThat(fixture.creditLedgerList(signup.accessToken(), query).ledgers())
                    .extracting(CreditLedgerResponse::type)
                    .doesNotContain(CreditLedgerType.EXPIRE.name()));
        }

        @Test
        @DisplayName("사용 탭으로 조회하면 차감 행만 담긴다")
        void listsOnlyDeductions_whenUseTabRequested() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-use-tab");
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            CreditLedgerListResponse body = fixture.creditLedgerList(signup.accessToken(), "?type=USE");

            assertThat(body.totalCount()).isEqualTo(1);
            assertThat(body.ledgers()).extracting(CreditLedgerResponse::type).containsOnly("DEDUCT");
        }

        @Test
        @DisplayName("지급 탭으로 조회하면 차감 행이 빠진다")
        void excludesDeductions_whenGainTabRequested() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-gain-tab");
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            CreditLedgerListResponse body = fixture.creditLedgerList(signup.accessToken(), "?type=GAIN");

            assertThat(body.totalCount()).isEqualTo(1);
            assertThat(body.ledgers()).extracting(CreditLedgerResponse::type).containsOnly("GRANT");
        }

        @Test
        @DisplayName("탭을 생략하면 지급과 차감이 한 흐름으로 최신순으로 담긴다")
        void listsEveryTypeInLatestFirstOrder_whenTabOmitted() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-all-tab");
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            CreditLedgerListResponse body = fixture.creditLedgerList(signup.accessToken(), "");

            assertThat(body.totalCount()).isEqualTo(2);
            assertThat(body.ledgers()).extracting(CreditLedgerResponse::type)
                .containsExactly("DEDUCT", "GRANT");
        }

        @Test
        @DisplayName("전체 탭으로 조회하면 탭을 생략한 것과 같이 담긴다")
        void listsEveryType_whenAllTabRequested() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-all-explicit");
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            CreditLedgerListResponse body = fixture.creditLedgerList(signup.accessToken(), "?type=ALL");

            assertThat(body.totalCount()).isEqualTo(2);
            assertThat(body.ledgers()).extracting(CreditLedgerResponse::type)
                .containsExactly("DEDUCT", "GRANT");
        }

        @Test
        @DisplayName("탭을 빈 값으로 보내면 생략한 것과 같이 전체가 담긴다")
        void listsEveryType_whenTabBlank() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-blank-tab");
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            CreditLedgerListResponse body = fixture.creditLedgerList(signup.accessToken(), "?type=");

            assertThat(body.totalCount()).isEqualTo(2);
            assertThat(body.ledgers()).extracting(CreditLedgerResponse::type)
                .containsExactly("DEDUCT", "GRANT");
        }

        @Test
        @DisplayName("페이지 크기보다 내역이 많으면 다음 페이지가 있다고 알려주고 다음 페이지에 나머지가 담긴다")
        void reportsNextPageAndReturnsRemainder_whenLedgerExceedsPageSize() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-paging");
            fixture.startedGenerationId(signup.accessToken(), List.of("BLOG"));

            CreditLedgerListResponse first = fixture.creditLedgerList(signup.accessToken(), "?size=1");
            CreditLedgerListResponse second = fixture.creditLedgerList(signup.accessToken(), "?size=1&page=1");

            assertThat(first.totalCount()).isEqualTo(2);
            assertThat(first.page()).isZero();
            assertThat(first.size()).isEqualTo(1);
            assertThat(first.hasNext()).isTrue();
            assertThat(first.ledgers()).extracting(CreditLedgerResponse::type).containsExactly("DEDUCT");
            assertThat(second.page()).isEqualTo(1);
            assertThat(second.hasNext()).isFalse();
            assertThat(second.ledgers()).extracting(CreditLedgerResponse::type).containsExactly("GRANT");
        }

        @Test
        @DisplayName("다른 회원의 내역은 섞이지 않는다")
        void excludesOtherMembersLedger_whenMemberQueries() {
            SignupResponse mine = fixture.signupActiveMember("naver-ledger-mine");
            SignupResponse others = fixture.signupActiveMember("naver-ledger-others");
            fixture.startedGenerationId(others.accessToken(), List.of("BLOG"));

            CreditLedgerListResponse body = fixture.creditLedgerList(mine.accessToken(), "");

            assertThat(body.totalCount()).isEqualTo(1);
            assertThat(body.ledgers()).extracting(CreditLedgerResponse::description)
                .containsExactly("가입 크레딧 지급");
        }

        @Test
        @DisplayName("size 가 상한을 넘으면 400 과 C0001 을 반환한다")
        void returns400_whenSizeExceedsLimit() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-size");

            fixture.getCreditLedgers(signup.accessToken(), "?size=51")
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("없는 탭으로 조회하면 400 과 C0001 을 반환한다")
        void returns400_whenTabUnknown() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-unknown-tab");

            fixture.getCreditLedgers(signup.accessToken(), "?type=EXPIRE")
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode()));
        }

        @Test
        @DisplayName("가입 대기(PENDING) 토큰으로 조회하면 403 과 A0007 을 반환한다")
        void returns403_whenPendingTokenQueriesLedger() {
            SocialLoginResponse login = fixture.naverLoginMember("naver-ledger-pending");

            fixture.getCreditLedgers(login.accessToken(), "")
                .expectStatus().isForbidden()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(AuthErrorCode.ACCESS_DENIED.getCode()));
        }

        @Test
        @DisplayName("탈퇴 대기(WITHDRAWN) 토큰으로 조회하면 403 과 A0007 을 반환한다")
        void returns403_whenWithdrawnTokenQueriesLedger() {
            SignupResponse signup = fixture.signupActiveMember("naver-ledger-withdrawn");
            fixture.withdraw(signup.accessToken()).expectStatus().isNoContent();
            SocialLoginResponse withdrawnLogin = fixture.naverLoginMember("naver-ledger-withdrawn");

            fixture.getCreditLedgers(withdrawnLogin.accessToken(), "")
                .expectStatus().isForbidden()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(AuthErrorCode.ACCESS_DENIED.getCode()));
        }

        @Test
        @DisplayName("액세스 토큰 없이 조회하면 401 과 A0006 을 반환한다")
        void returns401_whenAccessTokenMissing() {
            fixture.client().get().uri("/v1/credits/me/ledgers")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.code()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN.getCode()));
        }
    }

    @Nested
    @DisplayName("감춘 소멸 행의 원장 기록")
    class HiddenExpireLedger {

        @Test
        @DisplayName("소멸은 내역에 담기지 않아도 사이클 월을 붙인 문구로 원장에 남고 원장 합이 잔액과 같다")
        void keepsExpireRowWithCycleMonthDescription_whenFreeCreditExpired() {
            clock.moveTo(AUGUST_CYCLE);
            fixture.signupActiveMember("naver-ledger-hidden-expire");
            Long memberId = database.memberIdOf("naver-ledger-hidden-expire");
            clock.moveTo(SEPTEMBER_CYCLE);
            creditCycleScheduler.renewCycles();

            List<CreditLedger> entries = database.ledgerOf(memberId);

            assertThat(entries).filteredOn(entry -> entry.getType() == CreditLedgerType.EXPIRE)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getDescription()).isEqualTo("8월 크레딧 소멸");
                    assertThat(entry.getAmount()).isEqualTo(-100);
                });
            int ledgerSum = entries.stream().mapToInt(CreditLedger::getAmount).sum();
            String accessToken = fixture.naverLoginMember("naver-ledger-hidden-expire").accessToken();
            fixture.getCreditBalance(accessToken)
                .expectBody(CreditBalanceResponse.class)
                .value(body -> assertThat(body.balance()).isEqualTo(ledgerSum));
        }
    }
}
