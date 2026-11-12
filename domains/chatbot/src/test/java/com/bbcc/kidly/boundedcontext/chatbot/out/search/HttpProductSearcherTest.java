package com.bbcc.kidly.boundedcontext.chatbot.out.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bbcc.kidly.shared.search.dto.ProductHit;
import com.bbcc.kidly.shared.search.dto.ProductSearchQuery;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class HttpProductSearcherTest {

    private static final String SEARCH = "http://search/internal/products/search";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpProductSearcher searcher = new HttpProductSearcher(builder.build(),
        new ChatbotSearchProperties("http://search"));

    @Test
    @DisplayName("검색어는 인코딩하고 가격은 있을 때만 보내며, 모르는 칸은 무시하고 읽는다")
    void searches() {
        server.expect(requestTo(SEARCH + "?keyword=%EC%97%AC%EC%95%84%20%EC%9B%90%ED%94%BC%EC%8A%A4%20%26%20%2B&limit=5"
                + "&maxPrice=30000"))
            .andRespond(withSuccess("""
                [{"productId":10,"name":"여아 원피스","businessName":null,"price":25000,"thumbnailUrl":null,
                  "soldOut":true,"rank":1}]""", MediaType.APPLICATION_JSON));

        assertThat(searcher.search(new ProductSearchQuery("여아 원피스 & +", null, 30_000L, 5)))
            .containsExactly(new ProductHit(10L, "여아 원피스", null, 25_000L, null, true));
        server.verify();
    }

    @Test
    @DisplayName("결과가 없으면 빈 목록이다")
    void empty() {
        server.expect(requestTo(SEARCH + "?keyword=x&limit=5&minPrice=1000&maxPrice=2000"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(searcher.search(new ProductSearchQuery("x", 1_000L, 2_000L, 5))).isEmpty();
    }

    @Test
    @DisplayName("오류 응답 · 시간 초과 · 읽을 수 없는 응답은 IllegalStateException이다")
    void failures() {
        server.expect(requestTo(SEARCH + "?keyword=x&limit=5")).andRespond(withServerError());
        server.expect(requestTo(SEARCH + "?keyword=x&limit=5")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo(SEARCH + "?keyword=x&limit=5")).andRespond(request -> {
            throw new ResourceAccessException("시간 초과", new IOException());
        });
        server.expect(requestTo(SEARCH + "?keyword=x&limit=5"))
            .andRespond(withSuccess("잘못된 응답", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEARCH + "?keyword=x&limit=5"))
            .andRespond(withSuccess("[{\"productId\":10,\"name\":\"이름\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEARCH + "?keyword=x&limit=5"))
            .andRespond(withSuccess("[null]", MediaType.APPLICATION_JSON));

        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> searcher.search(new ProductSearchQuery("x", null, null, 5)))
                .isInstanceOf(IllegalStateException.class);
        }
        server.verify();
    }
}
