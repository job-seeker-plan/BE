package com.jobplanner.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobEventRepository extends JpaRepository<JobEventEntity, String> {
    List<JobEventEntity> findByUserIdOrderByEventDateAsc(String userId);
}
