package com.ssoss.ssossbackend.credit.application.service;

import java.util.ArrayList;
import java.util.List;

import com.ssoss.ssossbackend.credit.domain.service.CreditFinder;
import com.ssoss.ssossbackend.credit.domain.service.CreditWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CreditService")
class CreditServiceTest {

    @Nested
    @DisplayName("renewCycles")
    class RenewCycles {

        @Test
        @DisplayName("회원 한 명의 갱신이 실패해도 배치가 중단되지 않고 나머지 회원은 갱신된다")
        void continuesRenewingOtherMembers_whenOneMemberRenewalFails() {
            List<Long> renewedMemberIds = new ArrayList<>();
            CreditFinder creditFinder = new CreditFinder(null, null) {
                @Override
                public List<Long> findAllMemberIds() {
                    return List.of(1L, 2L, 3L);
                }
            };
            CreditWriter creditWriter = new CreditWriter(null, null, null, 0) {
                @Override
                public boolean renewCycle(Long memberId) {
                    if (memberId.equals(2L)) {
                        throw new IllegalStateException("사이클 갱신 실패 시뮬레이션");
                    }
                    renewedMemberIds.add(memberId);
                    return true;
                }
            };
            CreditService creditService = new CreditService(creditFinder, creditWriter);

            CreditCycleRenewalResult result = creditService.renewCycles();

            assertThat(renewedMemberIds).containsExactly(1L, 3L);
            assertThat(result.targetCount()).isEqualTo(3);
            assertThat(result.renewedCount()).isEqualTo(2);
        }
    }
}
