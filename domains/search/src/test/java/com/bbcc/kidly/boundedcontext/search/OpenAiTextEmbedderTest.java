package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bbcc.kidly.boundedcontext.search.out.OpenAiTextEmbedder;
import com.bbcc.kidly.boundedcontext.search.out.SearchEmbeddingProperties;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiTextEmbedderTest {

    private static final String TEXT = "여아 원피스\n키들리";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final MutableClock clock = new MutableClock();

    @Test
    @DisplayName("모델 · 문장 · 차원 수를 키와 함께 보내고 첫 벡터를 돌려준다")
    void embeds() {
        OpenAiTextEmbedder embedder = embedder("sk-test", 3);
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test"))
            .andExpect(content().json("""
                {"model": "text-embedding-3-small", "input": "여아 원피스\\n키들리", "dimensions": 3}
                """, JsonCompareMode.STRICT))
            .andRespond(withSuccess("""
                {"object": "list", "data": [{"object": "embedding", "index": 0, "embedding": [0.1, -0.2, 0.3]}],
                 "model": "text-embedding-3-small", "usage": {"prompt_tokens": 5, "total_tokens": 5}}
                """, MediaType.APPLICATION_JSON));

        assertThat(embedder.embed(TEXT)).hasValueSatisfying(vector -> assertThat(vector).containsExactly(0.1f, -0.2f,
            0.3f));
        server.verify();
    }

    @Test
    @DisplayName("키가 없으면(로컬 · 테스트) API를 부르지 않고 비워서 돌려준다")
    void disabledWithoutKey() {
        assertThat(embedder("", 3).embed(TEXT)).isEmpty();
        assertThat(embedder(null, 3).embed(TEXT)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("API가 실패하면 상태 코드만 남기고 비워서 돌려준다. 문장은 로그에 남기지 않는다")
    void apiError(CapturedOutput output) {
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\": {\"message\": \"" + TEXT + "\"}}"));

        assertThat(embedder("sk-test", 3).embed(TEXT)).isEmpty();
        assertThat(output).contains("status=429").doesNotContain("여아 원피스").doesNotContain("sk-test");
    }

    @Test
    @DisplayName("연결 실패 · 시간 초과면 원인 종류만 남기고 비워서 돌려준다")
    void connectionFailure(CapturedOutput output) {
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withException(new SocketTimeoutException("timeout")));

        assertThat(embedder("sk-test", 3).embed(TEXT)).isEmpty();
        assertThat(output).contains("cause=SocketTimeoutException");
    }

    @Test
    @DisplayName("벡터의 차원 수가 설정과 다르면(색인이 거절함) 쓰지 않는다")
    void wrongDimensions() {
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withSuccess("{\"data\": [{\"embedding\": [0.1, 0.2]}]}", MediaType.APPLICATION_JSON));

        assertThat(embedder("sk-test", 3).embed(TEXT)).isEmpty();
    }

    @Test
    @DisplayName("응답에 벡터가 없으면 비워서 돌려준다")
    void emptyData() {
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withSuccess("{\"data\": []}", MediaType.APPLICATION_JSON));

        assertThat(embedder("sk-test", 3).embed(TEXT)).isEmpty();
    }

    @Test
    @DisplayName("연속 5번 실패하면 30초 동안 부르지 않고 바로 비워서 돌려준다. 열 때만 한 번 알린다")
    void opensAfterConsecutiveFailures(CapturedOutput output) {
        OpenAiTextEmbedder embedder = embedder("sk-test", 3);
        server.expect(ExpectedCount.times(5), requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withException(new SocketTimeoutException("timeout")));

        for (int i = 0; i < 5; i++) {
            assertThat(embedder.embed(TEXT)).isEmpty();
        }
        clock.advance(Duration.ofSeconds(29));
        assertThat(embedder.embed(TEXT)).isEmpty();
        assertThat(embedder.embedKeyword("원피스")).isEmpty();

        server.verify();
        assertThat(output).containsOnlyOnce("연속 5번 실패해 30초 동안 부르지 않습니다");
    }

    @Test
    @DisplayName("응답은 왔어도 벡터를 쓸 수 없으면(차원 불일치) 실패로 세어 5번이면 부르지 않는다")
    void unusableResponseCountsAsFailure() {
        OpenAiTextEmbedder embedder = embedder("sk-test", 3);
        server.expect(ExpectedCount.times(5), requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withSuccess("{\"data\": [{\"embedding\": [0.1, 0.2]}]}", MediaType.APPLICATION_JSON));

        for (int i = 0; i < 6; i++) {
            assertThat(embedder.embed(TEXT)).isEmpty();
        }

        server.verify();
    }

    @Test
    @DisplayName("중간에 성공하면 연속 실패 수를 처음부터 센다")
    void successResetsFailures() {
        OpenAiTextEmbedder embedder = embedder("sk-test", 3);
        server.expect(ExpectedCount.times(4), requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.openai.test/v1/embeddings")).andRespond(vector());
        server.expect(ExpectedCount.times(4), requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.openai.test/v1/embeddings")).andRespond(vector());

        for (int i = 0; i < 10; i++) {
            embedder.embed(TEXT);
        }

        server.verify();
    }

    @Test
    @DisplayName("30초가 지나면 하나만 시험으로 부르고, 성공하면 다시 부른다")
    void probeSuccessCloses(CapturedOutput output) {
        OpenAiTextEmbedder embedder = openedEmbedder();
        server.expect(ExpectedCount.times(2), requestTo("https://api.openai.test/v1/embeddings")).andRespond(vector());

        clock.advance(Duration.ofSeconds(30));
        assertThat(embedder.embed(TEXT)).isPresent();
        assertThat(embedder.embed(TEXT)).isPresent();

        server.verify();
        assertThat(output).contains("임베딩 API가 다시 응답해 의미 검색을 다시 씁니다");
    }

    @Test
    @DisplayName("시험 호출이 실패하면 다시 30초 동안 부르지 않는다")
    void probeFailureReopens() {
        OpenAiTextEmbedder embedder = openedEmbedder();
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.openai.test/v1/embeddings")).andRespond(vector());

        clock.advance(Duration.ofSeconds(30));
        assertThat(embedder.embed(TEXT)).isEmpty();
        clock.advance(Duration.ofSeconds(29));
        assertThat(embedder.embed(TEXT)).isEmpty();
        clock.advance(Duration.ofSeconds(1));
        assertThat(embedder.embed(TEXT)).isPresent();

        server.verify();
    }

    @Test
    @DisplayName("같은 검색어는 10분 동안 기억해 다시 부르지 않는다")
    void cachesKeywordVector() {
        OpenAiTextEmbedder embedder = embedder("sk-test", 3);
        server.expect(ExpectedCount.times(2), requestTo("https://api.openai.test/v1/embeddings")).andRespond(vector());

        assertThat(embedder.embedKeyword("원피스")).hasValueSatisfying(v -> assertThat(v).containsExactly(0.1f, 0.2f,
            0.3f));
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        assertThat(embedder.embedKeyword("원피스")).hasValueSatisfying(v -> assertThat(v).containsExactly(0.1f, 0.2f,
            0.3f));
        clock.advance(Duration.ofSeconds(1));
        assertThat(embedder.embedKeyword("원피스")).isPresent();

        server.verify();
    }

    @Test
    @DisplayName("벡터를 얻지 못한 검색어는 기억하지 않아 다음 요청에서 다시 부른다")
    void doesNotCacheFailure() {
        OpenAiTextEmbedder embedder = embedder("sk-test", 3);
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.openai.test/v1/embeddings")).andRespond(vector());

        assertThat(embedder.embedKeyword("원피스")).isEmpty();
        assertThat(embedder.embedKeyword("원피스")).isPresent();

        server.verify();
    }

    // 연속 5번 실패해 열린 상태. 그 5번의 기대는 검증까지 끝내 둔다
    private OpenAiTextEmbedder openedEmbedder() {
        OpenAiTextEmbedder embedder = embedder("sk-test", 3);
        server.expect(ExpectedCount.times(5), requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        for (int i = 0; i < 5; i++) {
            embedder.embed(TEXT);
        }
        server.verify();
        server.reset();
        return embedder;
    }

    private static ResponseCreator vector() {
        return withSuccess("{\"data\": [{\"embedding\": [0.1, 0.2, 0.3]}]}", MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("키를 설정 문자열에 드러내지 않는다")
    void hidesKey() {
        assertThat(properties("sk-secret", 3).toString()).doesNotContain("sk-secret").contains("apiKey=***");
    }

    private OpenAiTextEmbedder embedder(String apiKey, int dimensions) {
        return new OpenAiTextEmbedder(properties(apiKey, dimensions), builder, clock);
    }

    private static SearchEmbeddingProperties properties(String apiKey, int dimensions) {
        return new SearchEmbeddingProperties(apiKey, "https://api.openai.test/v1", "text-embedding-3-small",
            dimensions, 0.3f);
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-04T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
