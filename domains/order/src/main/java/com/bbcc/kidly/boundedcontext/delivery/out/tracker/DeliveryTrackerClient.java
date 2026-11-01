package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerProperties;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
        JsonNode data = query(Map.of("carrierId", carrierId, "trackingNumber", trackingNumber));
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

    private JsonNode query(Map<String, Object> variables) {
        if (!isEnabled()) {
            throw new DeliveryTrackerException("DISABLED", "인증값 없음");
        }
        try {
            return restClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", TRACK_QUERY, "variables", variables))
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
