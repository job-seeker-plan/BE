package com.jobplanner.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
public class GmailService {

    private static final String GMAIL_BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final String JOB_SUBJECT_QUERY =
            "subject:(서류 OR 면접 OR 코딩테스트 OR 인적성 OR 채용 OR 합격 OR 전형 OR GSAT OR NCS OR interview OR recruit)";
    private static final int SEARCH_WINDOW_DAYS = 10;
    private static final DateTimeFormatter GMAIL_QUERY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final int MAX_BODY_CHARS = 4000;

    private final RestClient restClient;

    public GmailService() {
        this.restClient = RestClient.create();
    }

    public List<GmailMessage> fetchJobEmails(String accessToken) {
        // Job-hunting emails older than this are unlikely to still be actionable
        // (a coding test/interview date from 3 weeks ago is over), and narrowing the
        // window keeps each refresh fast and avoids re-surfacing a large backlog.
        String afterDate = LocalDate.now().minusDays(SEARCH_WINDOW_DAYS).format(GMAIL_QUERY_DATE);
        String query = JOB_SUBJECT_QUERY + " after:" + afterDate;
        GmailListResponse listResponse;
        try {
            listResponse = restClient.get()
                    .uri(GMAIL_BASE + "/messages?q={q}&maxResults=50", query)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(GmailListResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new GmailTokenExpiredException("Gmail 접근 토큰이 만료되었습니다. 다시 로그인해 주세요.");
            }
            throw new IllegalStateException("Gmail API 조회 실패: " + e.getMessage(), e);
        }
        if (listResponse == null || listResponse.messages() == null) return List.of();

        return listResponse.messages().stream()
                .map(ref -> fetchMessage(accessToken, ref.id()))
                .filter(Objects::nonNull)
                .toList();
    }

    private GmailMessage fetchMessage(String accessToken, String messageId) {
        try {
            // format=full (not "metadata") so payload.body/parts actually carry the message
            // text - metadata-only responses give GPT nothing but a ~200-char snippet, which
            // regularly cuts off before the actual date/instructions in the email.
            return restClient.get()
                    .uri(GMAIL_BASE + "/messages/{id}?format=full", messageId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(GmailMessage.class);
        } catch (RestClientResponseException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GmailListResponse(List<MessageRef> messages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageRef(String id, String threadId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GmailMessage(String id, String snippet, Payload payload) {
        public String getHeader(String name) {
            if (payload == null || payload.headers() == null) return null;
            return payload.headers().stream()
                    .filter(h -> name.equalsIgnoreCase(h.name()))
                    .map(Header::value)
                    .findFirst()
                    .orElse(null);
        }

        public String bodyText() {
            String text = payload == null ? null : extractText(payload);
            String value = (text == null || text.isBlank()) ? snippet : text;
            if (value == null) return "";
            return value.length() > MAX_BODY_CHARS ? value.substring(0, MAX_BODY_CHARS) : value;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String mimeType, List<Header> headers, Body body, List<Payload> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(String data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String name, String value) {}

    // Gmail messages are MIME - a simple message has its content directly in
    // payload.body, a multipart one nests it in payload.parts (recursively, since
    // multipart/alternative or multipart/mixed can nest further). Prefer text/plain;
    // fall back to a naive HTML-tag strip on text/html if that's all there is.
    private static String extractText(Payload payload) {
        String plain = findPart(payload, "text/plain");
        if (plain != null) return decode(plain);
        String html = findPart(payload, "text/html");
        if (html != null) return stripHtml(decode(html));
        return null;
    }

    private static String findPart(Payload payload, String mimeType) {
        if (payload == null) return null;
        if (mimeType.equals(payload.mimeType()) && payload.body() != null && payload.body().data() != null) {
            return payload.body().data();
        }
        if (payload.parts() != null) {
            for (Payload part : payload.parts()) {
                String found = findPart(part, mimeType);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String decode(String base64UrlData) {
        try {
            return new String(Base64.getUrlDecoder().decode(base64UrlData), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String stripHtml(String html) {
        return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static class GmailTokenExpiredException extends RuntimeException {
        public GmailTokenExpiredException(String message) {
            super(message);
        }
    }
}
