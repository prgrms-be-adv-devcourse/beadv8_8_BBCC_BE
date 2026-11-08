package com.bbcc.kidly.boundedcontext.review.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.filter.RequestLoggingFilter;
import com.bbcc.kidly.global.security.TestAdmins;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 운영 조회 MCP 창구 HTTP 계약(커뮤니티 테스트 설정에서 켬): initialize · tools/list · 도구 호출, 관리자 헤더 확인,
 * 잘못된 인자는 도구 오류, 응답에 리뷰 본문 · 닉네임 · 회원 ID · 사진 주소 · DLT 원본 · 예외 메시지가 없다.
 */
@FunctionalTest
class CommunityMcpApiTest {

    private static final String MCP = "/api/v1/admin/community/mcp";
    private static final long ADMIN = 99L;
    private static final long MEMBER = 987_654_321L;
    private static final String MARK = "MARK" + UUID.randomUUID().toString().replace("-", "");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 10, 0, 0, 123_456_000);

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long deadLetterId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at, moderation_status, moderation_reason, moderated_at) "
            + "values (11, 14, ?, ?, 4, ?, ?, ?, 'HIDDEN', 'HATE', ?)",
            MEMBER, "닉" + MARK, "본문" + MARK, AT, AT, AT);
        Long reviewId = jdbcTemplate.queryForObject("select id from community.review where order_item_id = 11",
            Long.class);
        jdbcTemplate.update("insert into community.review_image (review_id, position, image_url, created_at) "
            + "values (?, 0, ?, ?)", reviewId, "https://img.example/" + MARK + ".jpg", AT);
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at, moderation_status) values (12, 14, ?, ?, 5, ?, ?, ?, 'PASSED')",
            MEMBER + 1, "닉2" + MARK, "본문2" + MARK, AT, AT);
        jdbcTemplate.update("insert into community.review_stats (product_id, review_count, rating_sum, created_at, "
            + "updated_at) values (14, 1, 5, ?, ?)", AT, AT);
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (14, ?, ?, 'ON_SALE', ?, ?, ?)",
            "상품" + MARK, "https://img.example/thumb" + MARK + ".png", AT, AT, AT);
        jdbcTemplate.update("""
            insert into community.dead_letter
              (dlt_topic, dlt_partition, dlt_offset, source_topic, source_partition, source_offset,
               message_key, event_type, event_id, payload, failure_type, state, replay_count,
               last_replay_failure_type, created_at, updated_at)
            values ('kidly.order.events.community.dlt', 0, 5, 'kidly.order.events', 1, 9, ?,
                    'order-item.purchase-confirmed', ?, ?, ?, 'OPEN', 1, ?, ?, ?)
            """, MARK + "@example.com", "id " + MARK, ("{\"memberId\":" + MEMBER + ",\"x\":\"" + MARK + "\"}")
            .getBytes(StandardCharsets.UTF_8), "org.springframework.kafka.support.serializer.DeserializationException",
            "java.lang.IllegalStateException", AT, AT);
        deadLetterId = jdbcTemplate.queryForObject("select id from community.dead_letter", Long.class);
    }

    @Test
    @DisplayName("initialize · tools/list: 서버 정보와 도구 다섯 개")
    void initializeAndList() {
        Map<String, Object> init = rpc("initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
            "clientInfo", Map.of("name", "test", "version", "1")));
        assertThat(asMap(init.get("serverInfo")).get("name")).isEqualTo("kidly-community-ops");
        assertThat(asMap(init.get("capabilities"))).containsKey("tools");

        Map<String, Object> list = rpc("tools/list", Map.of());
        assertThat((List<?>) list.get("tools")).extracting(tool -> (Object) asMap(tool).get("name"))
            .containsExactly("review_summary", "dead_letters", "dead_letter_detail", "moderation_queue",
                "replica_health");
    }

    @Test
    @DisplayName("도구 호출: 각 도구가 정상 응답(isError false)이고 응답 어디에도 리뷰 본문 · 닉네임 · 회원 ID · 사진 주소 · DLT 원본 · 예외 메시지가 없다")
    void callsWithoutPersonalData() {
        List<String> bodies = new ArrayList<>();

        Map<String, Object> summary = callTool("review_summary", Map.of("productId", "14"), bodies);
        assertThat(asMap(summary.get("structuredContent"))).containsEntry("reviewCount", 1);
        Map<String, Object> letters = callTool("dead_letters", Map.of("state", "OPEN"), bodies);
        assertThat(asMap(letters.get("structuredContent"))).containsEntry("totalElements", 1);
        Map<String, Object> detail = callTool("dead_letter_detail", Map.of("id", deadLetterId), bodies);
        // 실패 예외는 저장 형식(전체 이름)에서 단순 이름으로, 자유 문구가 될 수 있는 키 · 이벤트 ID는 형식이 아니면 null
        assertThat(asMap(detail.get("structuredContent"))).containsEntry("eventType",
            "order-item.purchase-confirmed").containsEntry("messageKey", null).containsEntry("eventId", null)
            .containsEntry("failureType", "DeserializationException")
            .containsEntry("lastReplayFailureType", "IllegalStateException");
        assertThat(asMap(((List<?>) asMap(letters.get("structuredContent")).get("items")).getFirst()))
            .containsEntry("failureType", "DeserializationException");
        Map<String, Object> queue = callTool("moderation_queue", Map.of(), bodies);
        assertThat(asMap(asMap(queue.get("structuredContent")).get("counts"))).containsEntry("HIDDEN", 1);
        Map<String, Object> health = callTool("replica_health", Map.of(), bodies);
        assertThat(asMap(health.get("structuredContent"))).containsEntry("total", 1);

        assertThat(String.join("\n", bodies)).doesNotContain(MARK).doesNotContain(String.valueOf(MEMBER))
            .doesNotContain(String.valueOf(MEMBER + 1)).doesNotContain("org.springframework")
            .doesNotContain("java.lang");
    }

    @Test
    @DisplayName("잘못된 인자(음수 쪽 · 없는 상태 · 숫자 아닌 ID · 없는 DLT · 모르는 도구)는 도구 오류(isError)이고 서버 오류가 아니다")
    void invalidArgumentsAreToolErrors() {
        List<String> bodies = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> call : List.of(
            Map.entry("dead_letters", Map.<String, Object>of("page", -1)),
            Map.entry("dead_letters", Map.<String, Object>of("state", "CLOSED")),
            Map.entry("dead_letter_detail", Map.<String, Object>of("id", "abc")),
            Map.entry("dead_letter_detail", Map.<String, Object>of("id", 999_999)),
            Map.entry("review_summary", Map.<String, Object>of()))) {
            Map<String, Object> result = rawCallTool(call.getKey(), call.getValue(), bodies);
            assertThat(result.get("isError")).as(call.toString()).isEqualTo(true);
        }
    }

    @Test
    @DisplayName("관리자 헤더가 없거나 잘못되면 401 ProblemDetail(회원 헤더로도 안 된다)")
    void requiresAdmin() {
        for (java.util.function.Consumer<org.springframework.http.HttpHeaders> headers : List.of(
            (java.util.function.Consumer<org.springframework.http.HttpHeaders>) h -> { },
            h -> h.set("X-Admin-Id", "abc"),
            h -> h.set("X-Admin-Id", "0"),
            com.bbcc.kidly.global.security.TestMembers.buyer(1L))) {
            client.post().uri(MCP).headers(headers).contentType(MediaType.APPLICATION_JSON)
                .header("Accept", "application/json, text/event-stream")
                .body(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list", "params", Map.of()))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isEqualTo(CommonErrorCode.UNAUTHORIZED.title())
                .jsonPath("$.instance").isEqualTo(MCP);
        }
    }

    @Test
    @DisplayName("본문이 64KB를 넘으면 413 ProblemDetail")
    void rejectsLargeRequest() {
        client.post().uri(MCP).headers(TestAdmins.of(ADMIN)).contentType(MediaType.APPLICATION_JSON)
            .header("Accept", "application/json, text/event-stream")
            .body(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list",
                "params", Map.of("pad", "x".repeat(70_000))))
            .exchange()
            .expectStatus().isEqualTo(413)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.status").isEqualTo(413);
    }

    @Test
    @DisplayName("401 · 413 · 200 응답 모두 요청 추적 헤더(X-Request-Id)가 있다: 요청에 주면 같은 값, 안 주면 새 값")
    void traceIdOnEveryResponse() {
        String traceId = "0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f";
        Map<String, Object> list = Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list", "params", Map.of());

        client.post().uri(MCP).header(RequestLoggingFilter.TRACE_ID_HEADER, traceId)
            .contentType(MediaType.APPLICATION_JSON).header("Accept", "application/json, text/event-stream")
            .body(list).exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().valueEquals(RequestLoggingFilter.TRACE_ID_HEADER, traceId);
        client.post().uri(MCP).headers(TestAdmins.of(ADMIN)).header(RequestLoggingFilter.TRACE_ID_HEADER, traceId)
            .contentType(MediaType.APPLICATION_JSON).header("Accept", "application/json, text/event-stream")
            .body(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list",
                "params", Map.of("pad", "x".repeat(70_000))))
            .exchange()
            .expectStatus().isEqualTo(413)
            .expectHeader().valueEquals(RequestLoggingFilter.TRACE_ID_HEADER, traceId);
        client.post().uri(MCP).headers(TestAdmins.of(ADMIN)).header(RequestLoggingFilter.TRACE_ID_HEADER, traceId)
            .contentType(MediaType.APPLICATION_JSON).header("Accept", "application/json, text/event-stream")
            .body(list).exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(RequestLoggingFilter.TRACE_ID_HEADER, traceId);
        client.post().uri(MCP).contentType(MediaType.APPLICATION_JSON)
            .header("Accept", "application/json, text/event-stream")
            .body(list).exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().valueMatches(RequestLoggingFilter.TRACE_ID_HEADER, "[0-9a-f-]{36}");
    }

    private Map<String, Object> callTool(String name, Map<String, Object> arguments, List<String> bodies) {
        Map<String, Object> result = rawCallTool(name, arguments, bodies);
        assertThat(result.get("isError")).as(name).isEqualTo(false);
        return result;
    }

    private Map<String, Object> rawCallTool(String name, Map<String, Object> arguments, List<String> bodies) {
        String body = post("tools/call", Map.of("name", name, "arguments", arguments));
        bodies.add(body);
        return asMap(parse(body).get("result"));
    }

    private Map<String, Object> rpc(String method, Map<String, Object> params) {
        return asMap(parse(post(method, params)).get("result"));
    }

    private String post(String method, Map<String, Object> params) {
        return client.post().uri(MCP).headers(TestAdmins.of(ADMIN)).contentType(MediaType.APPLICATION_JSON)
            .header("Accept", "application/json, text/event-stream")
            .body(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params))
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody();
    }

    private static Map<String, Object> parse(String json) {
        return asMap(tools.jackson.databind.json.JsonMapper.builder().build().readValue(json, Map.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
