package com.jobplanner.service;

import com.jobplanner.model.FinanceTransaction;
import com.jobplanner.model.FinanceTransactionCreate;
import com.jobplanner.model.FinanceTransactionType;
import com.jobplanner.model.FinancialRecord;
import com.jobplanner.model.FinancialRecordCreate;
import com.jobplanner.model.JobEvent;
import com.jobplanner.model.JobEventCreate;
import com.jobplanner.model.UserProfile;
import com.jobplanner.persistence.FinanceTransactionEntity;
import com.jobplanner.persistence.FinanceTransactionRepository;
import com.jobplanner.persistence.FinancialRecordEntity;
import com.jobplanner.persistence.FinancialRecordRepository;
import com.jobplanner.persistence.JobEventEntity;
import com.jobplanner.persistence.JobEventRepository;
import com.jobplanner.persistence.ProfileEntity;
import com.jobplanner.persistence.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlannerStore {
    private final ProfileRepository profiles;
    private final JobEventRepository events;
    private final FinancialRecordRepository records;
    private final FinanceTransactionRepository transactions;

    public PlannerStore(ProfileRepository profiles, JobEventRepository events, FinancialRecordRepository records, FinanceTransactionRepository transactions) {
        this.profiles = profiles;
        this.events = events;
        this.records = records;
        this.transactions = transactions;
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

    @Transactional(readOnly = true)
    public Set<String> importedEmailIds(String userId) {
        // A stored value can be a comma-joined list (financial_rag equivalent doesn't
        // apply here - AI merges duplicate emails describing the same event into one
        // candidate with all their message ids joined) - split so each raw Gmail
        // message id is individually recognized as already-imported.
        return events.findImportedSourceEmailIds(userId).stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .collect(Collectors.toSet());
    }

    @Transactional
    public JobEvent addEvent(String userId, JobEventCreate payload) {
        JobEventEntity saved = events.save(new JobEventEntity(
                "event-" + UUID.randomUUID().toString().substring(0, 10), userId, payload.title(), payload.eventType(),
                payload.eventDate(), payload.expectedCost(), payload.memo() == null ? "" : payload.memo(),
                payload.sourceEmailId()
        ));
        return event(saved);
    }

    @Transactional
    public JobEvent updateEvent(String id, String userId, JobEventCreate payload) {
        JobEventEntity entity = requireOwnedEvent(id, userId);
        entity.setTitle(payload.title());
        entity.setEventType(payload.eventType());
        entity.setEventDate(payload.eventDate());
        entity.setExpectedCost(payload.expectedCost());
        entity.setMemo(payload.memo() == null ? "" : payload.memo());
        return event(events.save(entity));
    }

    @Transactional
    public void deleteEvent(String id, String userId) {
        events.delete(requireOwnedEvent(id, userId));
    }

    private JobEventEntity requireOwnedEvent(String id, String userId) {
        JobEventEntity entity = events.findById(id).orElseThrow(() -> new IllegalStateException("Event not found"));
        if (!entity.getUserId().equals(userId)) {
            throw new IllegalStateException("Event not found");
        }
        return entity;
    }

    @Transactional(readOnly = true)
    public List<FinanceTransaction> listTransactions(String userId) {
        return transactions.findByUserIdOrderByOccurredOnAsc(userId).stream().map(this::transaction).toList();
    }

    @Transactional
    public FinanceTransaction addTransaction(String userId, FinanceTransactionCreate payload) {
        FinanceTransactionEntity saved = transactions.save(new FinanceTransactionEntity(
                "txn-" + UUID.randomUUID().toString().substring(0, 10), userId, payload.occurredOn(), payload.type(),
                payload.category(), payload.amount(), payload.memo() == null ? "" : payload.memo(), payload.deductFromAvailableCash()
        ));
        adjustAvailableCash(userId, -deductionEffect(payload.type(), payload.amount(), payload.deductFromAvailableCash()));
        return transaction(saved);
    }

    @Transactional
    public FinanceTransaction updateTransaction(String id, String userId, FinanceTransactionCreate payload) {
        FinanceTransactionEntity entity = requireOwnedTransaction(id, userId);
        long previousEffect = deductionEffect(entity.getType(), entity.getAmount(), entity.isDeductFromAvailableCash());
        entity.setOccurredOn(payload.occurredOn());
        entity.setType(payload.type());
        entity.setCategory(payload.category());
        entity.setAmount(payload.amount());
        entity.setMemo(payload.memo() == null ? "" : payload.memo());
        entity.setDeductFromAvailableCash(payload.deductFromAvailableCash());
        FinanceTransaction saved = transaction(transactions.save(entity));
        long nextEffect = deductionEffect(payload.type(), payload.amount(), payload.deductFromAvailableCash());
        adjustAvailableCash(userId, previousEffect - nextEffect);
        return saved;
    }

    @Transactional
    public void deleteTransaction(String id, String userId) {
        FinanceTransactionEntity entity = requireOwnedTransaction(id, userId);
        long effect = deductionEffect(entity.getType(), entity.getAmount(), entity.isDeductFromAvailableCash());
        transactions.delete(entity);
        adjustAvailableCash(userId, effect);
    }

    private long deductionEffect(FinanceTransactionType type, long amount, boolean deductFromAvailableCash) {
        return type == FinanceTransactionType.expense && deductFromAvailableCash ? amount : 0;
    }

    private void adjustAvailableCash(String userId, long delta) {
        if (delta == 0) {
            return;
        }
        profiles.findById(userId).ifPresent(entity -> {
            entity.setAvailableCash(entity.getAvailableCash() + delta);
            profiles.save(entity);
        });
    }

    private FinanceTransactionEntity requireOwnedTransaction(String id, String userId) {
        FinanceTransactionEntity entity = transactions.findById(id).orElseThrow(() -> new IllegalStateException("Transaction not found"));
        if (!entity.getUserId().equals(userId)) {
            throw new IllegalStateException("Transaction not found");
        }
        return entity;
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
        return new JobEvent(value.getId(), value.getUserId(), value.getTitle(), value.getEventType(), value.getEventDate(), value.getExpectedCost(), value.getMemo(), value.getSourceEmailId());
    }

    private FinancialRecord record(FinancialRecordEntity value) {
        return new FinancialRecord(value.getUserId(), value.getMonth(), value.getSpend(), value.getBill(), value.getBalance(), value.getCreditScore(), value.getIncome());
    }

    private FinanceTransaction transaction(FinanceTransactionEntity value) {
        return new FinanceTransaction(value.getId(), value.getUserId(), value.getOccurredOn(), value.getType(), value.getCategory(), value.getAmount(), value.getMemo(), value.isDeductFromAvailableCash());
    }
}
