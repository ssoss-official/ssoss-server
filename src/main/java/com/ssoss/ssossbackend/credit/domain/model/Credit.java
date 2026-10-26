package com.ssoss.ssossbackend.credit.domain.model;

import java.time.Instant;

import lombok.Getter;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("credit")
public class Credit {

    public static final int CHANNEL_DEDUCTION = 5;

    @Id
    private Long id;
    private Long memberId;
    private int freeBalance;
    private int chargedBalance;
    private Instant grantedCycleAt;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    Credit(Long id, Long memberId, int freeBalance, int chargedBalance, Instant grantedCycleAt) {
        this.id = id;
        this.memberId = memberId;
        this.freeBalance = freeBalance;
        this.chargedBalance = chargedBalance;
        this.grantedCycleAt = grantedCycleAt;
    }

    public static Credit create(Long memberId) {
        return new Credit(null, memberId, 0, 0, null);
    }

    public Credit grant(int amount, CreditCycle cycle) {
        this.freeBalance += amount;
        this.grantedCycleAt = cycle.startsAt();
        return this;
    }

    public CreditCycle grantedCycle() {
        return new CreditCycle(grantedCycleAt);
    }

    public boolean isGrantedFor(CreditCycle cycle) {
        return cycle.startsAt().equals(grantedCycleAt);
    }

    public boolean canAfford(int amount) {
        return balance() >= amount;
    }

    public Credit deduct(int amount) {
        int fromFree = Math.min(freeBalance, amount);
        this.freeBalance -= fromFree;
        this.chargedBalance -= amount - fromFree;
        return this;
    }

    public int expireFree() {
        int expired = this.freeBalance;
        this.freeBalance = 0;
        return expired;
    }

    public int balance() {
        return freeBalance + chargedBalance;
    }
}
