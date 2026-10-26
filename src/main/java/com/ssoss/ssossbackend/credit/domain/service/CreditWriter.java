package com.ssoss.ssossbackend.credit.domain.service;

import java.time.Clock;

import com.ssoss.ssossbackend.credit.domain.contract.CreditLedgerRepository;
import com.ssoss.ssossbackend.credit.domain.contract.CreditRepository;
import com.ssoss.ssossbackend.credit.domain.model.Credit;
import com.ssoss.ssossbackend.credit.domain.model.CreditCycle;
import com.ssoss.ssossbackend.credit.domain.model.CreditErrorCode;
import com.ssoss.ssossbackend.credit.domain.model.CreditLedger;
import com.ssoss.ssossbackend.shared.exception.BusinessException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditWriter {

    private final CreditRepository creditRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final Clock clock;
    private final int freeGrant;

    public CreditWriter(
        CreditRepository creditRepository,
        CreditLedgerRepository creditLedgerRepository,
        Clock clock,
        @Value("${credit.free-grant}") int freeGrant
    ) {
        this.creditRepository = creditRepository;
        this.creditLedgerRepository = creditLedgerRepository;
        this.clock = clock;
        this.freeGrant = freeGrant;
    }

    @Transactional
    public void grant(Long memberId) {
        CreditCycle cycle = CreditCycle.current(clock.instant());
        creditRepository.save(Credit.create(memberId).grant(freeGrant, cycle));
        creditLedgerRepository.save(CreditLedger.signupGrant(memberId, freeGrant));
    }

    @Transactional
    public void deduct(Long memberId, Long generationId, int channelCount, String description) {
        int amount = Credit.CHANNEL_DEDUCTION * channelCount;
        Credit credit = creditRepository.findWithLockByMemberId(memberId)
            .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_NOT_FOUND));
        creditRepository.save(credit.deduct(amount));
        creditLedgerRepository.save(CreditLedger.deduct(memberId, amount, generationId, description));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewCycle(Long memberId) {
        CreditCycle cycle = CreditCycle.current(clock.instant());
        Credit credit = creditRepository.findWithLockByMemberId(memberId)
            .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_NOT_FOUND));
        if (credit.isGrantedFor(cycle)) {
            return false;
        }
        CreditCycle expiringCycle = credit.grantedCycle();
        int expired = credit.expireFree();
        if (expired > 0) {
            creditLedgerRepository.save(CreditLedger.expire(memberId, expired, expiringCycle));
        }
        creditRepository.save(credit.grant(freeGrant, cycle));
        creditLedgerRepository.save(CreditLedger.cycleGrant(memberId, freeGrant, cycle));
        return true;
    }

    @Transactional
    public void deleteAllByMemberId(Long memberId) {
        creditLedgerRepository.deleteAllByMemberId(memberId);
        creditRepository.deleteByMemberId(memberId);
    }
}
