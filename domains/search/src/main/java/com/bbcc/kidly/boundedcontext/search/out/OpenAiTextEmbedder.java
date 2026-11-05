package com.bbcc.kidly.boundedcontext.search.out;

import com.bbcc.kidly.boundedcontext.search.app.port.TextEmbedder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpenAI 임베딩 API(POST /embeddings)로 문장을 벡터로 바꾼다(#797).
 * 실패해도 예외를 던지지 않고 비워서 돌려준다. 색인은 벡터 없이, 검색은 키워드로만 이어 간다.
 * 문장(상품 정보 · 검색어)과 응답 본문은 로그에 남기지 않는다. 남기는 값은 상태 코드와 예외 종류뿐이다.
 */
@Slf4j
public class OpenAiTextEmbedder implements TextEmbedder {

    private final SearchEmbeddingProperties properties;
    private final RestClient restClient;
    private final EmbeddingCircuitBreaker circuitBreaker;
    private final KeywordVectorCache keywordCache;

    public OpenAiTextEmbedder(SearchEmbeddingProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, restClientBuilder, Clock.systemUTC());
    }

    public OpenAiTextEmbedder(SearchEmbeddingProperties properties, RestClient.Builder restClientBuilder,
        Clock clock) {
        this.properties = properties;
        this.circuitBreaker = new EmbeddingCircuitBreaker(clock);
        this.keywordCache = new KeywordVectorCache(clock);
        this.restClient = restClientBuilder
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
            .build();
    }

    @Override
    public Optional<List<Float>> embed(String text) {
        if (!properties.enabled() || text == null || text.isBlank()) {
            return Optional.empty();
        }
        EmbeddingCircuitBreaker.Permit permit = circuitBreaker.tryAcquire();
        if (permit == EmbeddingCircuitBreaker.Permit.REJECTED) {
            return Optional.empty();
        }
        Optional<List<Float>> vector = Optional.empty();
        try {
            EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbeddingRequest(properties.model(), text, properties.dimensions()))
                .retrieve()
                .body(EmbeddingResponse.class);
            vector = vectorOf(response);
            return vector;
        } catch (RestClientResponseException e) {
            log.warn("임베딩 API가 실패해 벡터 없이 이어 갑니다. status={}", e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("임베딩 API를 부르지 못해 벡터 없이 이어 갑니다. cause={}",
                e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName());
        } finally {
            // 예상하지 못한 예외로 끝나도 시험 호출 자리를 돌려주도록 여기서 기록한다.
            // 응답은 왔어도 쓸 수 있는 벡터가 없으면(빈 응답 · 차원 불일치) 실패로 센다. 설정이 잘못되면 매번 같아서다
            if (vector.isPresent()) {
                circuitBreaker.onSuccess(permit);
            } else {
                circuitBreaker.onFailure(permit);
            }
        }
        return Optional.empty();
    }

    // 벡터를 얻었을 때만 기억한다. 실패는 기억하지 않아 API가 돌아오면 바로 다시 만든다
    @Override
    public Optional<List<Float>> embedKeyword(String keyword) {
        if (!properties.enabled() || keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }
        Optional<List<Float>> cached = keywordCache.get(keyword);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<List<Float>> vector = embed(keyword);
        vector.ifPresent(found -> keywordCache.put(keyword, found));
        return vector;
    }

    // 차원 수가 색인 설정과 다르면 Elasticsearch가 문서를 거절하므로 미리 거른다
    private Optional<List<Float>> vectorOf(EmbeddingResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()
            || response.data().getFirst().embedding() == null) {
            log.warn("임베딩 API 응답에 벡터가 없어 벡터 없이 이어 갑니다");
            return Optional.empty();
        }
        List<Float> vector = response.data().getFirst().embedding();
        if (vector.size() != properties.dimensions()) {
            log.warn("임베딩 벡터의 차원 수가 설정과 달라 쓰지 않습니다. expected={}, actual={}",
                properties.dimensions(), vector.size());
            return Optional.empty();
        }
        return Optional.of(vector);
    }

    record EmbeddingRequest(String model, String input, int dimensions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<EmbeddingData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(List<Float> embedding) {
    }
}
