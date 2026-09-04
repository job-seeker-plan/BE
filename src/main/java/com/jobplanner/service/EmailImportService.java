package com.jobplanner.service;

import com.jobplanner.model.AuthUser;
import com.jobplanner.model.EmailEventCandidate;
import com.jobplanner.model.EmailMessageInput;
import com.jobplanner.model.EmailPreviewEvent;
import com.jobplanner.model.JobEventType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class EmailImportService {
    private final OAuthTokenStore tokenStore;
    private final GoogleTokenRefresher tokenRefresher;
    private final GmailService gmailService;
    private final AiClient aiClient;
    private final PlannerStore store;

    public EmailImportService(OAuthTokenStore tokenStore, GoogleTokenRefresher tokenRefresher,
                               GmailService gmailService, AiClient aiClient, PlannerStore store) {
        this.tokenStore = tokenStore;
        this.tokenRefresher = tokenRefresher;
        this.gmailService = gmailService;
        this.aiClient = aiClient;
        this.store = store;
    }

    public List<EmailPreviewEvent> preview(AuthUser user) {
        if (!"google".equals(user.provider())) {
            throw new EmailImportUnsupportedException("이메일 가져오기는 Google 로그인 사용자만 지원합니다.");
        }
        OAuthTokenStore.GoogleTokens tokens = tokenStore.getGoogle(user.userId());
        if (tokens == null) {
            throw new GmailService.GmailTokenExpiredException("Gmail 접근 권한이 없습니다. 다시 로그인해 주세요.");
        }

        List<GmailService.GmailMessage> messages = fetchWithRefresh(user.userId(), tokens);

        Set<String> alreadyImported = store.importedEmailIds(user.userId());
        List<EmailMessageInput> inputs = messages.stream()
                .filter(message -> !alreadyImported.contains(message.id()))
                .map(message -> new EmailMessageInput(
                        message.id(),
                        Objects.requireNonNullElse(message.getHeader("Subject"), ""),
                        Objects.requireNonNullElse(message.getHeader("From"), ""),
                        Objects.requireNonNullElse(message.getHeader("Date"), ""),
                        message.bodyText()
                ))
                .toList();

        List<EmailEventCandidate> candidates = aiClient.parseEmailEvents(inputs);
        return candidates.stream().map(this::toPreview).filter(Objects::nonNull).toList();
    }

    private List<GmailService.GmailMessage> fetchWithRefresh(String userId, OAuthTokenStore.GoogleTokens tokens) {
        try {
            return gmailService.fetchJobEmails(tokens.accessToken());
        } catch (GmailService.GmailTokenExpiredException expired) {
            if (tokens.refreshToken() == null) {
                throw expired;
            }
            String refreshedAccessToken = tokenRefresher.refresh(tokens.refreshToken());
            tokenStore.updateAccessToken(userId, refreshedAccessToken);
            return gmailService.fetchJobEmails(refreshedAccessToken);
        }
    }

    private EmailPreviewEvent toPreview(EmailEventCandidate candidate) {
        LocalDate eventDate;
        try {
            eventDate = LocalDate.parse(candidate.eventDate());
        } catch (DateTimeParseException | NullPointerException error) {
            return null;
        }
        JobEventType eventType = parseType(candidate.eventType());
        if (candidate.title() == null || candidate.title().isBlank()) {
            return null;
        }
        return new EmailPreviewEvent(candidate.messageId(), candidate.title(), eventType, eventDate, candidate.memo());
    }

    private JobEventType parseType(String type) {
        try {
            return JobEventType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException error) {
            return JobEventType.other;
        }
    }

    public static class EmailImportUnsupportedException extends RuntimeException {
        public EmailImportUnsupportedException(String message) {
            super(message);
        }
    }
}
