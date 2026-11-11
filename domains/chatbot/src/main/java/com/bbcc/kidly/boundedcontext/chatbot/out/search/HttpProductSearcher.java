package com.bbcc.kidly.boundedcontext.chatbot.out.search;

import com.bbcc.kidly.shared.search.dto.ProductHit;
import com.bbcc.kidly.shared.search.dto.ProductSearchQuery;
import com.bbcc.kidly.shared.search.out.ProductSearcher;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 검색 서비스의 내부 경로(GET /internal/products/search)로 {@link ProductSearcher}를 부른다.
 * 실패 · 시간 초과 · 읽을 수 없는 응답은 {@link IllegalStateException}으로 알린다. 예외 메시지에 검색어를 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
public class HttpProductSearcher implements ProductSearcher {

    private static final String PATH = "/internal/products/search";

    private final RestClient chatbotSearchRestClient;
    private final ChatbotSearchProperties properties;

    @Override
    public List<ProductHit> search(ProductSearchQuery query) {
        SearchedProduct[] products;
        try {
            products = chatbotSearchRestClient.get()
                .uri(properties.uri() + PATH, uri -> uri
                    .queryParam("keyword", "{keyword}")
                    .queryParam("limit", query.limit())
                    .queryParamIfPresent("minPrice", Optional.ofNullable(query.minPrice()))
                    .queryParamIfPresent("maxPrice", Optional.ofNullable(query.maxPrice()))
                    .build(query.keyword()))
                .retrieve()
                .body(SearchedProduct[].class);
        } catch (RestClientException e) {
            throw new IllegalStateException("상품 검색 호출 실패", e);
        }
        if (products == null || !Arrays.stream(products).allMatch(product -> product != null && product.isReadable())) {
            throw new IllegalStateException("상품 검색 응답을 읽을 수 없습니다");
        }
        return Arrays.stream(products).map(SearchedProduct::toHit).toList();
    }

    private record SearchedProduct(Long productId, String name, String businessName, Long price, String thumbnailUrl,
                                   Boolean soldOut) {

        boolean isReadable() {
            return productId != null && name != null && price != null;
        }

        ProductHit toHit() {
            return new ProductHit(productId, name, businessName, price, thumbnailUrl, Boolean.TRUE.equals(soldOut));
        }
    }
}
