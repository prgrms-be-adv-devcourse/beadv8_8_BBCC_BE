package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerProperties;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/**
 * Delivery Tracker GraphQL 조회(POST {baseUrl}/graphql). 인증값이 없으면 비활성이고 요청하지 않는다.
 * 오류는 대부분 HTTP 200 + errors로 오므로(10/15 실측) HTTP 상태보다 본문의 errors를 먼저 본다.
 */
class DeliveryTrackerClient {

    static final String UNKNOWN = "UNKNOWN";
    private static final String DELIVERED = "DELIVERED";

    private static final String TRACK_QUERY = """
        query Track($carrierId: ID!, $trackingNumber: String!) {
          track(carrierId: $carrierId, trackingNumber: $trackingNumber) {
            lastEvent {
              time
              status { code }
            }
          }
        }
        """;

    /**
     * 진행 이력(#1320)으로 읽는 최근 이벤트 수 상한. 페이지를 넘기지 않는다(이벤트는 오래된 것부터라 last로 최근 것을 읽는다).
     */
    static final int EVENTS_LIMIT = 30;

    // 시각 · 상태 코드 · 장소 이름만 요청한다(설명 · 연락처 · 보내는 사람 · 받는 사람은 요청하지 않는다)
    private static final String EVENTS_QUERY = """
        query TrackEvents($carrierId: ID!, $trackingNumber: String!, $last: Int!) {
          track(carrierId: $carrierId, trackingNumber: $trackingNumber) {
            events(last: $last) {
              edges {
                node {
                  time
                  status { code }
                  location { name }
                }
              }
            }
          }
        }
        """;

    private final RestClient restClient;

    DeliveryTrackerClient(DeliveryTrackerProperties properties, RestClient.Builder builder) {
        this.restClient = properties.enabled()
            ? builder.baseUrl(properties.baseUrl())
                .defaultHeader("Authorization",
                    "TRACKQL-API-KEY " + properties.clientId() + ":" + properties.clientSecret())
                .build()
            : null;
    }

    boolean isEnabled() {
        return restClient != null;
    }

    /**
     * @return lastEvent.status.code와 lastEvent.time. 시각은 DELIVERED일 때만 읽고(그 밖은 null), 이벤트가 아직 없으면 UNKNOWN · null
     * @throws DeliveryTrackerException 비활성 · HTTP 오류 · GraphQL errors · 응답 모양 오류 · 네트워크
     */
    TrackResult track(String carrierId, String trackingNumber) {
        JsonNode data = query(TRACK_QUERY, Map.of("carrierId", carrierId, "trackingNumber", trackingNumber));
        JsonNode track = data.path("track");
        if (track.isMissingNode() || track.isNull()) {
            throw new DeliveryTrackerException("INVALID_RESPONSE", "track 없음");
        }
        JsonNode lastEvent = track.path("lastEvent");
        JsonNode code = lastEvent.path("status").path("code");
        if (!code.isString()) {
            return new TrackResult(UNKNOWN, null);
        }
        // 시각은 완료 판단(택배사 시각 > 발송 시각)에만 쓴다. 배송 중 이벤트의 시각이 없다고 오류로 보면 매 주기 WARN이 쌓인다
        if (!DELIVERED.equals(code.asString())) {
            return new TrackResult(code.asString(), null);
        }
        return new TrackResult(DELIVERED, parseTime(lastEvent.path("time")));
    }

    /**
     * 최근 이벤트를 오래된 것부터(응답 순서 그대로) 최대 {@link #EVENTS_LIMIT}개 읽는다(#1320). 이벤트가 없으면 빈 목록.
     * 진행 이력은 보여 주기만 하므로 이벤트 하나의 시각이 없거나 형식이 틀려도 전체를 실패시키지 않고 그 시각만 비운다.
     *
     * @throws DeliveryTrackerException 비활성 · HTTP 오류 · GraphQL errors · 응답 모양 오류 · 네트워크
     */
    List<TrackEventRow> events(String carrierId, String trackingNumber) {
        JsonNode data = query(EVENTS_QUERY,
            Map.of("carrierId", carrierId, "trackingNumber", trackingNumber, "last", EVENTS_LIMIT));
        JsonNode track = data.path("track");
        if (track.isMissingNode() || track.isNull()) {
            throw new DeliveryTrackerException("INVALID_RESPONSE", "track 없음");
        }
        List<TrackEventRow> rows = new ArrayList<>();
        for (JsonNode edge : track.path("events").path("edges")) {
            JsonNode node = edge.path("node");
            if (node.isMissingNode() || node.isNull()) {
                continue;
            }
            JsonNode code = node.path("status").path("code");
            JsonNode location = node.path("location").path("name");
            rows.add(new TrackEventRow(eventTime(node.path("time")), location.isString() ? location.asString() : null,
                code.isString() ? code.asString() : UNKNOWN));
        }
        return rows;
    }

    private static OffsetDateTime eventTime(JsonNode time) {
        if (!time.isString()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(time.asString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // DELIVERED는 발송 시각과 비교하려면 시각이 꼭 있어야 하므로 못 읽으면 응답 오류로 본다
    private static OffsetDateTime parseTime(JsonNode time) {
        if (!time.isString()) {
            throw new DeliveryTrackerException("INVALID_RESPONSE", "lastEvent.time 없음");
        }
        try {
            return OffsetDateTime.parse(time.asString());
        } catch (DateTimeParseException e) {
            throw new DeliveryTrackerException("INVALID_RESPONSE", "lastEvent.time 형식 오류", e);
        }
    }

    private JsonNode query(String query, Map<String, Object> variables) {
        if (!isEnabled()) {
            throw new DeliveryTrackerException("DISABLED", "인증값 없음");
        }
        try {
            return restClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", query, "variables", variables))
                .exchange((request, response) -> {
                    JsonNode body = readBody(response);
                    JsonNode firstError = body.path("errors").path(0);
                    if (!firstError.isMissingNode()) {
                        throw new DeliveryTrackerException(
                            firstError.path("extensions").path("code").asString("UNKNOWN_ERROR"),
                            firstError.path("message").asString(""));
                    }
                    if (response.getStatusCode().isError()) {
                        throw new DeliveryTrackerException(
                            "HTTP_" + response.getStatusCode().value(), "errors 없는 오류 응답");
                    }
                    JsonNode data = body.path("data");
                    if (data.isMissingNode() || data.isNull()) {
                        throw new DeliveryTrackerException("INVALID_RESPONSE", "data 없음");
                    }
                    return data;
                });
        } catch (ResourceAccessException e) {
            throw new DeliveryTrackerException("NETWORK", e.getClass().getSimpleName(), e);
        }
    }

    // 게이트웨이 HTML · 빈 본문처럼 JSON이 아니면 빈 노드로 보고 상태 코드로 판단한다
    private static JsonNode readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            JsonNode body = response.bodyTo(JsonNode.class);
            return body == null ? MissingNode.getInstance() : body;
        } catch (RestClientException e) {
            return MissingNode.getInstance();
        }
    }
}
