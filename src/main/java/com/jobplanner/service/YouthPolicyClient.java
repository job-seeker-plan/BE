package com.jobplanner.service;

import com.jobplanner.config.AppProperties;
import com.jobplanner.model.EmploymentStatus;
import com.jobplanner.model.Policy;
import com.jobplanner.model.PolicySearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class YouthPolicyClient {
    // 온통청년 Open API moved off the legacy /opi/youthPlcyList.do path (now dead —
    // redirects to an unreachable internal port) onto this endpoint, with renamed
    // query params (apiKeyNm/pageNum/pageSize) and name-based classification
    // filters (lclsfNm/mclsfNm) replacing the old numeric business-type codes.
    private static final String API_URL = "https://www.youthcenter.go.kr/go/ythip/getPlcy";

    private final AppProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public YouthPolicyClient(AppProperties properties) {
        this.properties = properties;
    }

    public List<Policy> fetchPolicies(PolicySearchRequest search) {
        if (properties.govApiKey() == null || properties.govApiKey().isBlank()) {
            throw new IllegalStateException("GOV_API is required");
        }
        PolicySearchRequest effective = search == null
                ? new PolicySearchRequest(1, 100, null, "취업", "일자리", null, null, null)
                : search;
        URI uri = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("apiKeyNm", properties.govApiKey())
                .queryParam("pageNum", effective.page() == 0 ? 1 : effective.page())
                .queryParam("pageSize", effective.display() == 0 ? 20 : effective.display())
                .queryParam("rtnType", "xml")
                .queryParamIfPresent("plcyNm", optional(effective.plcyNm()))
                .queryParamIfPresent("plcyKywdNm", optional(effective.plcyKywdNm()))
                .queryParamIfPresent("lclsfNm", optional(effective.lclsfNm()))
                .queryParamIfPresent("mclsfNm", optional(effective.mclsfNm()))
                .queryParamIfPresent("zipCd", optional(effective.zipCd()))
                .queryParamIfPresent("plcyNo", optional(effective.plcyNo()))
                .build()
                .encode()
                .toUri();
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/xml,text/xml")
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Youth policy API returned HTTP " + response.statusCode());
            }
            return parseXml(response.body());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Youth policy API request was interrupted", error);
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Youth policy API request failed", error);
        }
    }

    private java.util.Optional<String> optional(String value) {
        return value == null || value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(value);
    }

    private List<Policy> parseXml(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
            factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList nodes = document.getElementsByTagName("*");
            List<Policy> policies = new ArrayList<>();
            for (int index = 0; index < nodes.getLength(); index++) {
                if (!(nodes.item(index) instanceof Element element)) {
                    continue;
                }
                Map<String, String> row = childMap(element);
                if (row.containsKey("plcyNo") || row.containsKey("plcyNm") || row.containsKey("polyBizSjnm")) {
                    Policy policy = normalize(row);
                    if (!policy.id().isBlank() && !policy.name().isBlank()) {
                        policies.add(policy);
                    }
                }
            }
            return policies.stream().distinct().toList();
        } catch (Exception error) {
            throw new IllegalStateException("Failed to parse youth policy API response", error);
        }
    }

    private Map<String, String> childMap(Element element) {
        java.util.HashMap<String, String> result = new java.util.HashMap<>();
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child) {
                result.put(child.getTagName(), child.getTextContent() == null ? "" : child.getTextContent().trim());
            }
        }
        return result;
    }

    private Policy normalize(Map<String, String> row) {
        String employmentCode = first(row, "jobCd");
        String incomeTypeCode = first(row, "earnCndSeCd");
        Long incomeMax = nullableLong(first(row, "earnMaxAmt"));
        List<String> regionCodes = split(first(row, "zipCd"));
        return new Policy(
                first(row, "plcyNo", "bizId", "srchPolicyId"),
                first(row, "plcyNm", "polyBizSjnm", "bizName"),
                regionCodes.isEmpty() ? first(row, "region", "polyRlmCd", "plcyRgnSeCd") : "",
                nullableInt(first(row, "sprtTrgtMinAge"), 0),
                nullableInt(first(row, "sprtTrgtMaxAge"), 150),
                "0013003".equals(employmentCode) ? EmploymentStatus.unemployed : EmploymentStatus.any,
                "0043001".equals(incomeTypeCode) ? null : incomeMax,
                null,
                "지원 금액은 공고 원문 확인 필요",
                first(row, "plcyExplnCn", "polyItcnCn", "description"),
                first(row, "plcySprtCn", "sprtCn", "sporCn"),
                first(row, "sprvsnInstCdNm"),
                first(row, "operInstCdNm"),
                "Y".equals(first(row, "sprtTrgtAgeLmtYn")),
                incomeTypeCode,
                nullableLong(first(row, "earnMinAmt")),
                employmentCode,
                first(row, "schoolCd"),
                first(row, "sBizCd", "sbizCd"),
                regionCodes,
                first(row, "plcyAplyMthdCn"),
                first(row, "aplyUrlAddr", "refUrlAddr1"),
                first(row, "sbmsnDcmntCn"),
                first(row, "addAplyQlfcCndCn"),
                first(row, "ptcpPrpTrgtCn"),
                first(row, "lastMdfcnDt", "frstRegDt"),
                first(row, "aplyYmd", "rqutPrdCn", "applicationPeriod"),
                "온통청년 Open API"
        );
    }

    private String first(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    private int nullableInt(String value, int fallback) {
        Long parsed = nullableLong(value);
        return parsed == null ? fallback : parsed.intValue();
    }

    private Long nullableLong(String value) {
        if (value == null || value.isBlank() || "0".equals(value)) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : Long.parseLong(digits);
    }

}
