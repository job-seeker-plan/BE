package com.jobplanner.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransactionEntity, String> {
    List<FinanceTransactionEntity> findByUserIdOrderByOccurredOnAsc(String userId);
}
