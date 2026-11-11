package com.bbcc.kidly.boundedcontext.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminKnowledgeDocumentApiTest {

    private static final String PATH = "/api/v1/admin/chatbot/documents";
    private static final long ADMIN_ID = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeEmbeddingModel embeddingModel;

    @AfterEach
    void restoreAi() {
        embeddingModel.failWith(null);
    }

    @Test
    @DisplayName("문서를 저장하면 201 + Location이고, 본문을 조각내 임베딩해 둔다")
    void createStoresChunks() {
        String content = paragraphs(3, 300);

        client.post().uri(PATH).headers(TestAdmins.of(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("title", "교환 · 반품 정책", "category", "POLICY", "content", content))
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().location(PATH + "/1")
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.title").isEqualTo("교환 · 반품 정책")
            .jsonPath("$.category").isEqualTo("POLICY")
            .jsonPath("$.content").isEqualTo(content)
            .jsonPath("$.chunkCount").isEqualTo(2)
            .jsonPath("$.createdAt").isNotEmpty();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT seq, array_length(embedding, 1) AS dims FROM chatbot.knowledge_chunk
            WHERE document_id = 1 ORDER BY seq
            """);
        assertThat(rows).extracting(row -> row.get("seq")).containsExactly(0, 1);
        assertThat(rows).extracting(row -> row.get("dims")).containsOnly(512);
    }

    @Test
    @DisplayName("목록은 최신 문서부터 쪽으로 주고 본문은 넣지 않는다")
    void listsNewestFirst() {
        create("첫 문서", "FAQ", "첫 본문");
        create("둘째 문서", "SIZE_GUIDE", "둘째 본문");

        client.get().uri(PATH + "?page=0&size=1").headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].title").isEqualTo("둘째 문서")
            .jsonPath("$.content[0].category").isEqualTo("SIZE_GUIDE")
            .jsonPath("$.content[0].content").doesNotExist()
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);
    }

    @Test
    @DisplayName("수정하면 문서를 바꾸고 조각을 모두 새로 만든다")
    void updateReplacesChunks() {
        create("교환 정책", "POLICY", paragraphs(3, 300));
        List<Long> before = chunkIds();

        client.put().uri(PATH + "/1").headers(TestAdmins.of(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("title", "교환 · 반품 정책", "category", "FAQ", "content", "짧아진 본문"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.title").isEqualTo("교환 · 반품 정책")
            .jsonPath("$.category").isEqualTo("FAQ")
            .jsonPath("$.chunkCount").isEqualTo(1);

        assertThat(before).hasSize(2);
        assertThat(chunkIds()).hasSize(1).doesNotContainAnyElementsOf(before);
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM chatbot.knowledge_chunk", String.class))
            .isEqualTo("짧아진 본문");
        client.get().uri(PATH + "/1").headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEqualTo("짧아진 본문")
            .jsonPath("$.chunkCount").isEqualTo(1);
    }

    @Test
    @DisplayName("지우면 204이고 조각도 함께 지워지며, 다시 읽으면 404다")
    void deleteRemovesChunks() {
        create("교환 정책", "POLICY", "교환은 7일 안에 신청합니다.");

        client.delete().uri(PATH + "/1").headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isNoContent();

        assertThat(chunkIds()).isEmpty();
        client.get().uri(PATH + "/1").headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.title").isEqualTo("문서를 찾을 수 없습니다");
        client.delete().uri(PATH + "/1").headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("없는 문서를 고치면 임베딩하지 않고 404다")
    void updateMissingIsNotFound() {
        embeddingModel.failWith(new IllegalStateException("불리면 안 된다"));

        client.put().uri(PATH + "/99").headers(TestAdmins.of(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("title", "제목", "category", "FAQ", "content", "본문"))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("제목 · 본문 · 분류가 틀리면 400이고 저장하지 않는다")
    void rejectsInvalidInput() {
        List<Map<String, Object>> invalid = List.of(
            Map.of("title", " ", "category", "FAQ", "content", "본문"),
            Map.of("title", "가".repeat(201), "category", "FAQ", "content", "본문"),
            Map.of("title", "제목", "category", "FAQ", "content", "가".repeat(20_001)),
            Map.of("title", "제목", "category", "FAQ"),
            Map.of("title", "제목", "content", "본문"));
        for (Map<String, Object> body : invalid) {
            client.post().uri(PATH).headers(TestAdmins.of(ADMIN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        }
        client.post().uri(PATH).headers(TestAdmins.of(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("title", "제목", "category", "NOTICE", "content", "본문"))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("잘못된 문서 분류입니다");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chatbot.knowledge_document", Long.class)).isZero();
    }

    @Test
    @DisplayName("임베딩이 실패하면 503 ProblemDetail이고 문서를 남기지 않는다")
    void aiFailureIsServiceUnavailable() {
        embeddingModel.failWith(new RuntimeException("timeout"));

        client.post().uri(PATH).headers(TestAdmins.of(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("title", "교환 정책", "category", "POLICY", "content", "교환은 7일 안에 신청합니다."))
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("AI를 쓸 수 없습니다");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chatbot.knowledge_document", Long.class)).isZero();
    }

    @Test
    @DisplayName("관리자 헤더가 없으면 401이다")
    void requiresAdmin() {
        client.get().uri(PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private void create(String title, String category, String content) {
        client.post().uri(PATH).headers(TestAdmins.of(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("title", title, "category", category, "content", content))
            .exchange()
            .expectStatus().isCreated();
    }

    private List<Long> chunkIds() {
        return jdbcTemplate.queryForList("SELECT id FROM chatbot.knowledge_chunk ORDER BY id", Long.class);
    }

    // 빈 줄로 나뉜 문단 count개. 문단마다 length자
    private static String paragraphs(int count, int length) {
        return String.join("\n\n", IntStream.range(0, count)
            .mapToObj(i -> String.valueOf((char) ('가' + i)).repeat(length))
            .toList());
    }
}
