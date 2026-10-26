package com.ssoss.ssossbackend.credit.domain.model;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Credit")
class CreditTest {

    private static final CreditCycle CYCLE = CreditCycle.current(Instant.parse("2026-07-15T00:00:00Z"));
    private static final CreditCycle NEXT_CYCLE = CreditCycle.current(Instant.parse("2026-08-15T00:00:00Z"));

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("잔액 행을 만들면 무료·충전 잔액이 모두 0 으로 시작한다")
        void startsWithZeroBalances_whenCreated() {
            Credit credit = Credit.create(1L);

            assertThat(credit.getFreeBalance()).isZero();
            assertThat(credit.getChargedBalance()).isZero();
            assertThat(credit.balance()).isZero();
        }
    }

    @Nested
    @DisplayName("grant")
    class Grant {

        @Test
        @DisplayName("무료 크레딧을 지급하면 무료 잔액에 더해지고 지급 사이클이 기록된다")
        void addsToFreeBalanceAndRecordsCycle_whenFreeCreditGranted() {
            Credit credit = Credit.create(1L).grant(100, CYCLE);

            assertThat(credit.getFreeBalance()).isEqualTo(100);
            assertThat(credit.getChargedBalance()).isZero();
            assertThat(credit.getGrantedCycleAt()).isEqualTo(CYCLE.startsAt());
        }
    }

    @Nested
    @DisplayName("isGrantedFor")
    class IsGrantedFor {

        @Test
        @DisplayName("지급받은 사이클로 판정하면 지급된 것으로 본다")
        void returnsTrue_whenQueriedWithGrantedCycle() {
            Credit credit = Credit.create(1L).grant(100, CYCLE);

            assertThat(credit.isGrantedFor(CYCLE)).isTrue();
        }

        @Test
        @DisplayName("지급받은 적 없는 사이클로 판정하면 지급되지 않은 것으로 본다")
        void returnsFalse_whenQueriedWithDifferentCycle() {
            Credit credit = Credit.create(1L).grant(100, CYCLE);

            assertThat(credit.isGrantedFor(NEXT_CYCLE)).isFalse();
        }

        @Test
        @DisplayName("지급 이력이 없으면 지급되지 않은 것으로 본다")
        void returnsFalse_whenNeverGranted() {
            Credit credit = Credit.create(1L);

            assertThat(credit.isGrantedFor(CYCLE)).isFalse();
        }
    }

    @Nested
    @DisplayName("expireFree")
    class ExpireFree {

        @Test
        @DisplayName("무료 잔액을 소멸하면 무료 잔액이 0 이 되고 소멸량이 반환된다")
        void zeroesFreeBalanceAndReturnsExpiredAmount_whenFreeBalanceExpired() {
            Credit credit = Credit.create(1L).grant(30, CYCLE);

            int expired = credit.expireFree();

            assertThat(expired).isEqualTo(30);
            assertThat(credit.getFreeBalance()).isZero();
        }

        @Test
        @DisplayName("무료 잔액이 없으면 소멸량은 0 이다")
        void returnsZero_whenFreeBalanceIsEmpty() {
            Credit credit = Credit.create(1L);

            int expired = credit.expireFree();

            assertThat(expired).isZero();
            assertThat(credit.getFreeBalance()).isZero();
        }
    }

    @Nested
    @DisplayName("canAfford")
    class CanAfford {

        @Test
        @DisplayName("잔액이 판정량과 같으면 차감 가능으로 본다")
        void returnsTrue_whenBalanceEqualsAmount() {
            Credit credit = Credit.create(1L).grant(20, CYCLE);

            assertThat(credit.canAfford(20)).isTrue();
        }

        @Test
        @DisplayName("잔액이 판정량보다 적으면 차감 불가로 본다")
        void returnsFalse_whenBalanceLessThanAmount() {
            Credit credit = Credit.create(1L).grant(19, CYCLE);

            assertThat(credit.canAfford(20)).isFalse();
        }

        @Test
        @DisplayName("무료 잔액과 충전 잔액의 합으로 판정한다")
        void sumsFreeAndChargedBalances_whenJudging() {
            Credit credit = new Credit(null, 1L, 3, 2, CYCLE.startsAt());

            assertThat(credit.canAfford(5)).isTrue();
        }
    }

    @Nested
    @DisplayName("deduct")
    class Deduct {

        @Test
        @DisplayName("무료 잔액이 충분하면 무료 잔액에서만 차감한다")
        void deductsFromFreeBalanceOnly_whenFreeBalanceSufficient() {
            Credit credit = new Credit(null, 1L, 50, 10, CYCLE.startsAt());

            credit.deduct(5);

            assertThat(credit.getFreeBalance()).isEqualTo(45);
            assertThat(credit.getChargedBalance()).isEqualTo(10);
        }

        @Test
        @DisplayName("무료 잔액이 모자라면 무료 잔액을 다 쓰고 남은 양을 충전 잔액에서 차감한다")
        void deductsRemainderFromChargedBalance_whenFreeBalanceShort() {
            Credit credit = new Credit(null, 1L, 3, 10, CYCLE.startsAt());

            credit.deduct(5);

            assertThat(credit.getFreeBalance()).isZero();
            assertThat(credit.getChargedBalance()).isEqualTo(8);
        }

        @Test
        @DisplayName("무료 잔액이 없으면 충전 잔액에서만 차감한다")
        void deductsFromChargedBalanceOnly_whenFreeBalanceEmpty() {
            Credit credit = new Credit(null, 1L, 0, 10, CYCLE.startsAt());

            credit.deduct(5);

            assertThat(credit.getFreeBalance()).isZero();
            assertThat(credit.getChargedBalance()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("balance")
    class Balance {

        @Test
        @DisplayName("잔액은 무료 잔액과 충전 잔액의 합이다")
        void sumsFreeAndChargedBalances_whenQueried() {
            Credit credit = Credit.create(1L).grant(30, CYCLE);

            assertThat(credit.balance()).isEqualTo(30);
        }
    }
}
