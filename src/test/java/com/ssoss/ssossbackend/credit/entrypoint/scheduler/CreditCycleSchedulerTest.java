package com.ssoss.ssossbackend.credit.entrypoint.scheduler;

import java.time.Duration;
import java.util.List;

import com.ssoss.ssossbackend.auth.domain.contract.RefreshTokenRepository;
import com.ssoss.ssossbackend.auth.entrypoint.response.RecoveryResponse;
import com.ssoss.ssossbackend.auth.entrypoint.response.SignupResponse;
import com.ssoss.ssossbackend.credit.domain.contract.CreditLedgerRepository;
import com.ssoss.ssossbackend.credit.domain.contract.CreditRepository;
import com.ssoss.ssossbackend.credit.domain.model.CreditLedger;
import com.ssoss.ssossbackend.credit.domain.model.CreditLedgerType;
import com.ssoss.ssossbackend.credit.entrypoint.response.CreditBalanceResponse;
import com.ssoss.ssossbackend.member.domain.contract.MemberRepository;
import com.ssoss.ssossbackend.member.domain.contract.MemberTermRepository;
import com.ssoss.ssossbackend.member.domain.contract.MemberWithdrawalHistoryRepository;
import com.ssoss.ssossbackend.support.IntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("크레딧 사이클 배치 스케줄러")
class CreditCycleSchedulerTest extends IntegrationTest {

    private static final Duration PAST_CYCLE_BOUNDARY = Duration.ofDays(31);

    @Autowired
    private CreditCycleScheduler creditCycleScheduler;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberTermRepository memberTermRepository;

    @Autowired
    private MemberWithdrawalHistoryRepository memberWithdrawalHistoryRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CreditRepository creditRepository;

    @Autowired
    private CreditLedgerRepository creditLedgerRepository;

    @BeforeEach
    void resetDatabase() {
        memberWithdrawalHistoryRepository.deleteAll();
        memberTermRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        creditLedgerRepository.deleteAll();
        creditRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Nested
    @DisplayName("renewCycles")
    class RenewCycles {

        @Test
        @DisplayName("사이클 경계를 지나 배치가 돌면 직전 사이클 무료 잔액이 소멸 회수되고 새 사이클 무료 크레딧 100 이 지급된다")
        void expiresPreviousFreeBalanceAndGrantsNewCycleCredit_whenBatchRunsAfterCycleBoundary() {
            fixture.signupActiveMember("naver-cycle-renew");
            Long memberId = database.memberIdOf("naver-cycle-renew");

            clock.advanceBy(PAST_CYCLE_BOUNDARY);
            creditCycleScheduler.renewCycles();

            List<CreditLedger> entries = database.ledgerOf(memberId);
            assertThat(entries).filteredOn(entry -> entry.getType() == CreditLedgerType.EXPIRE)
                .singleElement()
                .satisfies(entry -> assertThat(entry.getAmount()).isEqualTo(-100));
            assertThat(entries).filteredOn(entry -> entry.getType() == CreditLedgerType.GRANT)
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.getAmount()).isEqualTo(100));
            int ledgerSum = entries.stream().mapToInt(CreditLedger::getAmount).sum();
            String accessToken = fixture.naverLoginMember("naver-cycle-renew").accessToken();
            fixture.getCreditBalance(accessToken)
                .expectStatus().isOk()
                .expectBody(CreditBalanceResponse.class)
                .value(body -> {
                    assertThat(body.balance()).isEqualTo(100);
                    assertThat(body.balance()).isEqualTo(ledgerSum);
                });
        }

        @Test
        @DisplayName("같은 사이클에 배치를 두 번 돌려도 소멸과 지급이 중복되지 않는다")
        void doesNotDuplicateExpireAndGrant_whenBatchRunsTwiceInSameCycle() {
            fixture.signupActiveMember("naver-cycle-idempotent");
            Long memberId = database.memberIdOf("naver-cycle-idempotent");

            clock.advanceBy(PAST_CYCLE_BOUNDARY);
            creditCycleScheduler.renewCycles();
            creditCycleScheduler.renewCycles();

            List<CreditLedger> entries = database.ledgerOf(memberId);
            assertThat(entries).filteredOn(entry -> entry.getType() == CreditLedgerType.EXPIRE).hasSize(1);
            assertThat(entries).filteredOn(entry -> entry.getType() == CreditLedgerType.GRANT).hasSize(2);
            assertThat(creditRepository.findByMemberId(memberId))
                .get()
                .satisfies(credit -> assertThat(credit.getFreeBalance()).isEqualTo(100));
        }

        @Test
        @DisplayName("사이클 중간 가입자는 배치가 돌기 전에도 가입 시점 지급 잔액 100 을 보유한다")
        void holdsSignupGrantedBalance_whenMemberSignedUpMidCycleBeforeBatch() {
            clock.advanceBy(PAST_CYCLE_BOUNDARY);

            SignupResponse signup = fixture.signupActiveMember("naver-cycle-midcycle");

            fixture.getCreditBalance(signup.accessToken())
                .expectStatus().isOk()
                .expectBody(CreditBalanceResponse.class)
                .value(body -> assertThat(body.balance()).isEqualTo(100));
        }

        @Test
        @DisplayName("지연 실행된 배치는 사이클 중간 가입자에게 다시 지급하지 않고 경계 이전 가입자만 갱신한다")
        void skipsMidCycleSignupAndRenewsOnlyEarlierMembers_whenBatchRunsLate() {
            fixture.signupActiveMember("naver-cycle-early");
            Long earlyId = database.memberIdOf("naver-cycle-early");
            clock.advanceBy(PAST_CYCLE_BOUNDARY);
            fixture.signupActiveMember("naver-cycle-late");
            Long lateId = database.memberIdOf("naver-cycle-late");

            creditCycleScheduler.renewCycles();

            assertThat(database.ledgerOf(earlyId)).hasSize(3);
            assertThat(database.ledgerOf(lateId))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getType()).isEqualTo(CreditLedgerType.GRANT);
                    assertThat(entry.getAmount()).isEqualTo(100);
                });
            assertThat(creditRepository.findByMemberId(earlyId))
                .get()
                .satisfies(credit -> assertThat(credit.getFreeBalance()).isEqualTo(100));
            assertThat(creditRepository.findByMemberId(lateId))
                .get()
                .satisfies(credit -> assertThat(credit.getFreeBalance()).isEqualTo(100));
        }

        @Test
        @DisplayName("탈퇴 유예 중인 회원도 사이클이 갱신되어 복구하면 새 사이클 잔액 100 을 보유한다")
        void renewsWithdrawnMemberInGracePeriod_whenBatchRunsBeforeRecovery() {
            fixture.signupActiveMember("naver-cycle-withdrawn");
            Long memberId = database.memberIdOf("naver-cycle-withdrawn");
            clock.advanceBy(PAST_CYCLE_BOUNDARY);
            String activeToken = fixture.naverLoginMember("naver-cycle-withdrawn").accessToken();
            fixture.withdraw(activeToken).expectStatus().isNoContent();

            creditCycleScheduler.renewCycles();

            assertThat(database.ledgerOf(memberId)).filteredOn(entry -> entry.getType() == CreditLedgerType.EXPIRE).hasSize(1);
            String withdrawnToken = fixture.naverLoginMember("naver-cycle-withdrawn").accessToken();
            RecoveryResponse recovered = fixture.recover(withdrawnToken)
                .expectStatus().isOk()
                .expectBody(RecoveryResponse.class)
                .returnResult()
                .getResponseBody();
            fixture.getCreditBalance(recovered.accessToken())
                .expectStatus().isOk()
                .expectBody(CreditBalanceResponse.class)
                .value(body -> assertThat(body.balance()).isEqualTo(100));
        }
    }
}
