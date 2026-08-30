package com.jobplanner.persistence;

import com.jobplanner.model.JobEventType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "job_events")
public class JobEventEntity {
    @Id
    private String id;
    private String userId;
    private String title;
    @Enumerated(EnumType.STRING)
    private JobEventType eventType;
    private LocalDate eventDate;
    private long expectedCost;
    private String memo;

    protected JobEventEntity() {
    }

    public JobEventEntity(String id, String userId, String title, JobEventType eventType, LocalDate eventDate, long expectedCost, String memo) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.expectedCost = expectedCost;
        this.memo = memo;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public JobEventType getEventType() { return eventType; }
    public LocalDate getEventDate() { return eventDate; }
    public long getExpectedCost() { return expectedCost; }
    public String getMemo() { return memo; }

    public void update(String title, JobEventType eventType, LocalDate eventDate, long expectedCost, String memo) {
        this.title = title;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.expectedCost = expectedCost;
        this.memo = memo;
    }
}
