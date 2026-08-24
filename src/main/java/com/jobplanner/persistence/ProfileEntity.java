package com.jobplanner.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.jobplanner.model.EmploymentStatus;

@Entity
@Table(name = "user_profiles")
public class ProfileEntity {
    @Id
    private String userId;
    private long availableCash;
    private long monthlyIncome;
    private int age;
    private String region;
    @Enumerated(EnumType.STRING)
    private EmploymentStatus employmentStatus;
    private long monthlyIncomeForPolicy;
    private String targetJobMonth;

    protected ProfileEntity() {
    }

    public ProfileEntity(String userId, long availableCash, long monthlyIncome, int age, String region, EmploymentStatus employmentStatus, long monthlyIncomeForPolicy, String targetJobMonth) {
        this.userId = userId;
        this.availableCash = availableCash;
        this.monthlyIncome = monthlyIncome;
        this.age = age;
        this.region = region;
        this.employmentStatus = employmentStatus;
        this.monthlyIncomeForPolicy = monthlyIncomeForPolicy;
        this.targetJobMonth = targetJobMonth;
    }

    public String getUserId() { return userId; }
    public long getAvailableCash() { return availableCash; }
    public long getMonthlyIncome() { return monthlyIncome; }
    public int getAge() { return age; }
    public String getRegion() { return region; }
    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    public long getMonthlyIncomeForPolicy() { return monthlyIncomeForPolicy; }
    public String getTargetJobMonth() { return targetJobMonth; }
}
