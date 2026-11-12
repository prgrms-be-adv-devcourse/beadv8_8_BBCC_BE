package com.bbcc.kidly.boundedcontext.chatbot.app;

import com.bbcc.kidly.boundedcontext.chatbot.app.port.ChatbotAi.Tool;
import com.bbcc.kidly.shared.search.dto.ProductHit;
import com.bbcc.kidly.shared.search.dto.ProductSearchQuery;
import com.bbcc.kidly.shared.search.out.ProductSearcher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 모델이 부르는 상품 검색 도구. 질문 하나마다 새로 만들어, 이번 답에서 찾은 상품을 모아 둔다(화면의 상품 카드).
 * 검색 도메인에는 검색어 · 가격만 보낸다(회원 정보는 보내지 않는다). 검색어는 로그에 남기지 않는다.
 */
@Slf4j
final class ProductSearchTool {

    static final String NAME = "searchProducts";
    static final int MAX_PRODUCTS = 6;
    static final String UNAVAILABLE = "상품 검색을 지금 할 수 없습니다. 상품을 찾지 못했다고 짧게 안내하고 잠시 뒤 다시 물어 달라고 하세요.";

    private static final int HITS_PER_SEARCH = 5;
    // 검색 도메인이 받는 검색어 길이 상한
    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final String DESCRIPTION = """
        Kidly에서 판매중인 상품을 검색어 · 가격으로 찾습니다. 관련도순으로 최대 5개를 줍니다.
        상품을 찾거나 추천해 달라는 질문이면 이 도구를 부르세요. 결과가 없으면 검색어를 바꿔 한 번 더 찾을 수 있습니다.
        """;
    private static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "keyword": {
              "type": "string",
              "description": "상품 이름에 들어갈 만한 짧은 검색어. 나이 · 성별은 여아 · 남아 · 유아 같은 말로 바꾸세요. 예: 여아 여름 원피스, 래시가드, 남아 운동화"
            },
            "minPrice": {"type": "integer", "description": "최저 가격(원). 회원이 말하지 않았으면 보내지 마세요"},
            "maxPrice": {"type": "integer", "description": "최고 가격(원). 예: 3만원 이하면 30000"}
          },
          "required": ["keyword"],
          "additionalProperties": false
        }
        """;

    private final ProductSearcher productSearcher;
    private final Map<Long, ProductHit> found = new LinkedHashMap<>();

    ProductSearchTool(ProductSearcher productSearcher) {
        this.productSearcher = productSearcher;
    }

    Tool<Input> tool() {
        return new Tool<>(NAME, DESCRIPTION, INPUT_SCHEMA, Input.class, this::search);
    }

    /**
     * 이번 답에서 찾은 상품. 처음 찾은 순서대로, 같은 상품은 한 번, 최대 {@value #MAX_PRODUCTS}개.
     */
    List<ProductHit> found() {
        return List.copyOf(found.values());
    }

    private Object search(Input input) {
        ProductSearchQuery query = input.toQuery();
        if (query == null) {
            return new Failure("검색어를 넣어 다시 부르세요.");
        }
        List<ProductHit> hits;
        try {
            hits = productSearcher.search(query);
        } catch (RuntimeException e) {
            log.warn("챗봇 상품 검색 실패: cause={}", e.getClass().getSimpleName());
            return new Failure(UNAVAILABLE);
        }
        for (ProductHit hit : hits) {
            if (found.size() >= MAX_PRODUCTS) {
                break;
            }
            found.putIfAbsent(hit.productId(), hit);
        }
        log.info("챗봇 상품 검색: hits={}", hits.size());
        return hits.stream().map(Found::from).toList();
    }

    /**
     * 모델이 보낸 인자. 모델이 만든 값이라 검색 도메인이 거절할 값은 여기서 고친다.
     */
    record Input(String keyword, Long minPrice, Long maxPrice) {

        ProductSearchQuery toQuery() {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String trimmed = keyword.strip();
            if (trimmed.length() > MAX_KEYWORD_LENGTH) {
                trimmed = trimmed.substring(0, MAX_KEYWORD_LENGTH);
            }
            Long min = minPrice == null || minPrice < 0 ? null : minPrice;
            Long max = maxPrice == null || maxPrice < 0 ? null : maxPrice;
            if (min != null && max != null && min > max) {
                return new ProductSearchQuery(trimmed, max, min, HITS_PER_SEARCH);
            }
            return new ProductSearchQuery(trimmed, min, max, HITS_PER_SEARCH);
        }
    }

    // 모델에 주는 값. 썸네일은 답에 쓰지 않아 뺀다
    record Found(Long productId, String name, String businessName, Long price, boolean soldOut) {

        static Found from(ProductHit hit) {
            return new Found(hit.productId(), hit.name(), hit.businessName(), hit.price(), hit.soldOut());
        }
    }

    record Failure(String error) {
    }
}
