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
    private static final Map<String, String> REGION_CODES = Map.ofEntries(
            Map.entry("11", "서울"), Map.entry("26", "부산"),
            Map.entry("27", "대구"), Map.entry("28", "인천"),
            Map.entry("29", "광주"), Map.entry("30", "대전"),
            Map.entry("31", "울산"), Map.entry("36", "세종"),
            Map.entry("41", "경기"), Map.entry("42", "강원"),
            Map.entry("43", "충북"), Map.entry("44", "충남"),
            Map.entry("45", "전북"), Map.entry("46", "전남"),
            Map.entry("47", "경북"), Map.entry("48", "경남"),
            Map.entry("50", "제주"),
            Map.entry("003002001", "서울"), Map.entry("003002002", "부산"),
            Map.entry("003002003", "대구"), Map.entry("003002004", "인천"),
            Map.entry("003002005", "광주"), Map.entry("003002006", "대전"),
            Map.entry("003002007", "울산"), Map.entry("003002008", "경기"),
            Map.entry("003002009", "강원"), Map.entry("003002010", "충북"),
            Map.entry("003002011", "충남"), Map.entry("003002012", "전북"),
            Map.entry("003002013", "전남"), Map.entry("003002014", "경북"),
            Map.entry("003002015", "경남"), Map.entry("003002016", "제주"),
            Map.entry("003002017", "세종")
    );

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

    public List<Policy> fetchAllPolicies(PolicySearchRequest search) {
        PolicySearchRequest base = search == null
                ? new PolicySearchRequest(1, 100, null, "취업", "일자리", null, null, null)
                : search;
        List<Policy> allPolicies = new ArrayList<>();
        int pageSize = base.display() == 0 ? 100 : base.display();
        for (int page = Math.max(1, base.page()); page <= 100; page++) {
            PolicySearchRequest pageRequest = new PolicySearchRequest(
                    page, pageSize, base.plcyNm(), base.plcyKywdNm(), base.lclsfNm(), base.mclsfNm(), base.zipCd(), base.plcyNo());
            List<Policy> pagePolicies = fetchPolicies(pageRequest);
            allPolicies.addAll(pagePolicies);
            if (pagePolicies.size() < pageSize) {
                break;
            }
        }
        return allPolicies.stream().distinct().toList();
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
        String agencyLocation = first(row, "operInstCdNm", "sprvsnInstCdNm");
        List<String> regionCodes = normalizeRegions(
                first(row, "zipCd", "region", "polyRlmCd", "plcyRgnSeCd"), agencyLocation);
        return new Policy(
                first(row, "plcyNo", "bizId", "srchPolicyId"),
                first(row, "plcyNm", "polyBizSjnm", "bizName"),
                String.join(", ", regionCodes),
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

    /**
     * 서비스 내부의 지역 표준은 광역자치단체 한글명이다.
     * 온통청년의 구형 광역코드, 신규 zipCd(시군구 코드), 한글 지역명을 모두
     * 같은 표준값으로 변환해 FE와 매칭 로직에서 동일하게 사용한다.
     */
    private List<String> normalizeRegions(String value, String locationText) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> regions = new ArrayList<>();
        for (String raw : split(value)) {
            String token = raw.trim();
            if (token.isBlank()) {
                continue;
            }
            String region = REGION_CODES.get(token);
            if (region == null && token.matches("\\d{5,10}")) {
                region = switch (token.substring(0, 2)) {
                    case "11" -> "서울";
                    case "26" -> "부산";
                    case "27" -> "대구";
                    case "28" -> "인천";
                    case "29" -> "광주";
                    case "30" -> "대전";
                    case "31" -> "울산";
                    case "36" -> "세종";
                    case "41" -> "경기";
                    case "42" -> "강원";
                    case "43" -> "충북";
                    case "44" -> "충남";
                    case "45" -> "전북";
                    case "46" -> "전남";
                    case "47" -> "경북";
                    case "48" -> "경남";
                    case "50" -> "제주";
                    case "51" -> "강원";
                    default -> null;
                };
            }
            if (region == null) {
                region = token;
            }
            if (token.matches("\\d{5,10}")) {
                String agencyRegion = regionFromText(locationText);
                if (agencyRegion != null) {
                    region = agencyRegion;
                }
            }
            if (!regions.contains(region)) {
                regions.add(region);
            }
        }
        return regions;
    }

    private String regionFromText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.replace(" ", "");
        if (text.contains("서울")) return "서울";
        if (text.contains("부산")) return "부산";
        if (text.contains("대구")) return "대구";
        if (text.contains("인천")) return "인천";
        if (text.contains("광주")) return "광주";
        if (text.contains("대전")) return "대전";
        if (text.contains("울산")) return "울산";
        if (text.contains("세종")) return "세종";
        if (text.contains("경기")) return "경기";
        if (text.contains("강원") || text.matches(".*(춘천|원주|강릉|동해|태백|속초|삼척|홍천|횡성|영월|평창|정선|철원|화천|양구|인제|고성|양양).*")) return "강원";
        if (text.contains("충북")) return "충북";
        if (text.contains("충남")) return "충남";
        if (text.contains("전북")) return "전북";
        if (text.contains("전남") || text.matches(".*(목포|여수|순천|나주|광양|담양|곡성|구례|고흥|보성|화순|장흥|강진|해남|영암|무안|함평|영광|장성|완도|진도|신안).*")) return "전남";
        if (text.contains("경북")) return "경북";
        if (text.contains("경남")) return "경남";
        if (text.contains("제주")) return "제주";
        return null;
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
