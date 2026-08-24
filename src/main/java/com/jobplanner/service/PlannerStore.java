package com.jobplanner.service;

import com.jobplanner.model.FinancialRecord;
import com.jobplanner.model.FinancialRecordCreate;
import com.jobplanner.model.JobEvent;
import com.jobplanner.model.JobEventCreate;
import com.jobplanner.model.UserProfile;
import com.jobplanner.persistence.FinancialRecordEntity;
import com.jobplanner.persistence.FinancialRecordRepository;
import com.jobplanner.persistence.JobEventEntity;
import com.jobplanner.persistence.JobEventRepository;
import com.jobplanner.persistence.ProfileEntity;
import com.jobplanner.persistence.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PlannerStore {
    private final ProfileRepository profiles;
    private final JobEventRepository events;
    private final FinancialRecordRepository records;

    public PlannerStore(ProfileRepository profiles, JobEventRepository events, FinancialRecordRepository records) {
        this.profiles = profiles;
        this.events = events;
        this.records = records;
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(String userId) {
        return profiles.findById(userId).map(this::profile).orElse(null);
    }

    @Transactional
    public UserProfile saveProfile(UserProfile profile) {
        ProfileEntity saved = profiles.save(new ProfileEntity(
                profile.userId(), profile.availableCash(), profile.monthlyIncome(), profile.age(), profile.region(),
                profile.employmentStatus(), profile.monthlyIncomeForPolicy(), profile.targetJobMonth()
        ));
        return profile(saved);
    }

    @Transactional(readOnly = true)
    public List<JobEvent> listEvents(String userId) {
        return events.findByUserIdOrderByEventDateAsc(userId).stream().map(this::event).toList();
    }

    @Transactional
    public JobEvent addEvent(String userId, JobEventCreate payload) {
        JobEventEntity saved = events.save(new JobEventEntity(
                "event-" + UUID.randomUUID().toString().substring(0, 10), userId, payload.title(), payload.eventType(),
                payload.eventDate(), payload.expectedCost(), payload.memo() == null ? "" : payload.memo()
        ));
        return event(saved);
    }

    @Transactional(readOnly = true)
    public List<FinancialRecord> listRecords(String userId) {
        return records.findByUserIdOrderByMonthAsc(userId).stream().map(this::record).toList();
    }

    @Transactional
    public FinancialRecord upsertRecord(String userId, FinancialRecordCreate payload) {
        records.findByUserIdAndMonth(userId, payload.month()).ifPresent(records::delete);
        FinancialRecordEntity saved = records.save(new FinancialRecordEntity(
                userId, payload.month(), payload.spend(), payload.bill(), payload.balance(), payload.creditScore(), payload.income()
        ));
        return record(saved);
    }

    private UserProfile profile(ProfileEntity value) {
        return new UserProfile(value.getUserId(), value.getAvailableCash(), value.getMonthlyIncome(), value.getAge(), value.getRegion(),
                value.getEmploymentStatus(), value.getMonthlyIncomeForPolicy(), value.getTargetJobMonth());
    }

    private JobEvent event(JobEventEntity value) {
        return new JobEvent(value.getId(), value.getUserId(), value.getTitle(), value.getEventType(), value.getEventDate(), value.getExpectedCost(), value.getMemo());
    }

    private FinancialRecord record(FinancialRecordEntity value) {
        return new FinancialRecord(value.getUserId(), value.getMonth(), value.getSpend(), value.getBill(), value.getBalance(), value.getCreditScore(), value.getIncome());
    }
}
