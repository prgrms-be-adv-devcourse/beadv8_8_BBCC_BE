package com.bbcc.kidly.boundedcontext.search.app;

import com.bbcc.kidly.boundedcontext.search.app.dto.ProductListRequest;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearch;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.search.app.port.SearchEventSender;
import com.bbcc.kidly.boundedcontext.search.app.port.SearchUnavailableException;
import com.bbcc.kidly.boundedcontext.search.app.port.TextEmbedder;
import com.bbcc.kidly.boundedcontext.search.domain.SearchErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.search.dto.ProductHit;
import com.bbcc.kidly.shared.search.dto.ProductSearchQuery;
import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;
import com.bbcc.kidly.shared.search.out.ProductSearcher;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 구매자 상품 목록 조회 유스케이스다. 요청을 검색 조건으로 바꿔 검색 색인에서 찾는다.
 * 로그인 회원이 검색어로 찾은 첫 페이지면 검색 이벤트(search.performed)를 보낸다(#611).
 * 검색어로 찾으면 정렬과 관계없이 검색어를 의미 검색 벡터로 바꿔 함께 넘긴다(#797). 바꾸지 못하면 키워드로만 찾는다.
 * 다른 도메인(챗봇)의 상품 검색 계약 {@link ProductSearcher}도 같은 검색으로 구현한다(#1191). 회원을 모르므로 검색 이벤트는 보내지 않는다.
 * DB를 쓰지 않으므로 트랜잭션을 열지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductListService implements ProductSearcher {

    private final ProductSearch productSearch;
    private final SearchEventSender searchEventSender;
    private final TextEmbedder textEmbedder;

    /**
     * @param memberId 로그인 회원 ID. 비로그인이면 null
     */
    public Page<ProductSearchItem> list(ProductListRequest request, Long memberId) {
        String keyword = stripKeyword(request.keyword());
        ProductSearchSort sort = ProductSearchSort.valueOf(request.sortOrDefault());
        ProductSearchCondition condition = new ProductSearchCondition(
            request.categoryId(),
            keyword,
            groupByCode(request.attributesOrEmpty()),
            sort,
            keywordVector(keyword));
        Page<ProductSearchItem> result = search(condition,
            PageRequest.of(request.pageOrDefault(), request.sizeOrDefault()));
        if (isNewSearchByMember(memberId, condition, request)) {
            searchEventSender.send(searchPerformed(memberId, condition, result.getTotalElements(), request.childId()));
        }
        return result;
    }

    @Override
    public List<ProductHit> search(ProductSearchQuery query) {
        String keyword = stripKeyword(query.keyword());
        ProductSearchCondition condition = new ProductSearchCondition(null, keyword, Map.of(),
            ProductSearchSort.RELEVANCE, keywordVector(keyword), query.minPrice(), query.maxPrice());
        return search(condition, PageRequest.of(0, query.limit())).stream()
            .map(item -> new ProductHit(item.productId(), item.name(), item.businessName(), item.minPrice(),
                item.thumbnailUrl(), item.soldOut()))
            .toList();
    }

    private Page<ProductSearchItem> search(ProductSearchCondition condition, Pageable pageable) {
        try {
            return productSearch.search(condition, pageable);
        } catch (SearchUnavailableException e) {
            // 요청마다 같은 장애가 반복되므로 스택 트레이스 대신 위치(색인 · 상태)와 원인 종류만 남긴다.
            // 원인 메시지에는 Elasticsearch가 돌려준 요청 일부(검색어)가 들어갈 수 있어 남기지 않는다
            log.warn("상품 검색 색인을 쓸 수 없어 503으로 응답합니다. {} cause={}", e.getMessage(),
                e.getCause() == null ? null : e.getCause().getClass().getSimpleName());
            throw new BusinessException(SearchErrorCode.SEARCH_UNAVAILABLE);
        }
    }

    // 검색어가 없으면 의미 검색을 하지 않으므로 API를 부르지 않는다
    private List<Float> keywordVector(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        return textEmbedder.embedKeyword(keyword).orElse(null);
    }

    // 추천이 쓰는 건 회원이 무엇을 찾았는지다. 검색어 없는 둘러보기와 같은 검색의 다음 페이지는 새 검색이 아니다
    private boolean isNewSearchByMember(Long memberId, ProductSearchCondition condition, ProductListRequest request) {
        return memberId != null
            && condition.keyword() != null && !condition.keyword().isEmpty()
            && request.pageOrDefault() == 0;
    }

    private SearchPerformedEvent searchPerformed(Long memberId, ProductSearchCondition condition, long resultCount,
        Long childId) {
        return new SearchPerformedEvent(UUID.randomUUID(), LocalDateTime.now(), new SearchPerformedEvent.Data(
            memberId, condition.keyword(), condition.categoryId(), condition.attributeValueIds(), resultCount,
            childId));
    }

    // 앞뒤 공백까지 검색어로 넘기지 않는다. 공백뿐이면 빈 문자열이 되어 거르지 않는다
    private String stripKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.strip();
    }

    // "COLOR:3" 형태의 문자열을 항목 code별 값 ID 목록으로 묶는다. 같은 code는 한 목록에 모으고 같은 값은 한 번만 넣는다.
    // 업무 규칙이 아니라 입력 형식 변환이다
    private Map<String, List<Long>> groupByCode(List<String> attributes) {
        Map<String, List<Long>> grouped = new LinkedHashMap<>();
        for (String attribute : attributes) {
            int separator = attribute.indexOf(':');
            String code = attribute.substring(0, separator);
            Long valueId = Long.valueOf(attribute.substring(separator + 1));
            List<Long> valueIds = grouped.computeIfAbsent(code, key -> new ArrayList<>());
            if (!valueIds.contains(valueId)) {
                valueIds.add(valueId);
            }
        }
        return grouped;
    }
}
