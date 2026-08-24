package com.jobplanner.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancialRecordRepository extends JpaRepository<FinancialRecordEntity, Long> {
    List<FinancialRecordEntity> findByUserIdOrderByMonthAsc(String userId);
    Optional<FinancialRecordEntity> findByUserIdAndMonth(String userId, String month);
}
