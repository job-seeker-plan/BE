package com.jobplanner.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface JobEventRepository extends JpaRepository<JobEventEntity, String> {
    List<JobEventEntity> findByUserIdOrderByEventDateAsc(String userId);

    @Query("SELECT e.sourceEmailId FROM JobEventEntity e WHERE e.userId = :userId AND e.sourceEmailId IS NOT NULL")
    Set<String> findImportedSourceEmailIds(@Param("userId") String userId);
}
