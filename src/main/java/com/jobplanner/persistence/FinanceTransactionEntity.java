package com.jobplanner.persistence;

import com.jobplanner.model.FinanceTransactionType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "finance_transactions")
public class FinanceTransactionEntity {
    @Id
    private String id;
    private String userId;
    private LocalDate occurredOn;
    @Enumerated(EnumType.STRING)
    private FinanceTransactionType type;
    private String category;
    private long amount;
    private String memo;
    private boolean deductFromAvailableCash;

    protected FinanceTransactionEntity() {
    }

    public FinanceTransactionEntity(String id, String userId, LocalDate occurredOn, FinanceTransactionType type, String category, long amount, String memo, boolean deductFromAvailableCash) {
        this.id = id;
        this.userId = userId;
        this.occurredOn = occurredOn;
        this.type = type;
        this.category = category;
        this.amount = amount;
        this.memo = memo;
        this.deductFromAvailableCash = deductFromAvailableCash;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public FinanceTransactionType getType() { return type; }
    public String getCategory() { return category; }
    public long getAmount() { return amount; }
    public String getMemo() { return memo; }
    public boolean isDeductFromAvailableCash() { return deductFromAvailableCash; }

    public void setOccurredOn(LocalDate occurredOn) { this.occurredOn = occurredOn; }
    public void setType(FinanceTransactionType type) { this.type = type; }
    public void setCategory(String category) { this.category = category; }
    public void setAmount(long amount) { this.amount = amount; }
    public void setMemo(String memo) { this.memo = memo; }
    public void setDeductFromAvailableCash(boolean deductFromAvailableCash) { this.deductFromAvailableCash = deductFromAvailableCash; }
}
