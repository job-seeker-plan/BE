package com.jobplanner.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_records")
public class FinancialRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;
    private String month;
    private long spend;
    private long bill;
    private long balance;
    private Integer creditScore;
    private long income;

    protected FinancialRecordEntity() {
    }

    public FinancialRecordEntity(String userId, String month, long spend, long bill, long balance, Integer creditScore, long income) {
        this.userId = userId;
        this.month = month;
        this.spend = spend;
        this.bill = bill;
        this.balance = balance;
        this.creditScore = creditScore;
        this.income = income;
    }

    public String getUserId() { return userId; }
    public String getMonth() { return month; }
    public long getSpend() { return spend; }
    public long getBill() { return bill; }
    public long getBalance() { return balance; }
    public Integer getCreditScore() { return creditScore; }
    public long getIncome() { return income; }
}
