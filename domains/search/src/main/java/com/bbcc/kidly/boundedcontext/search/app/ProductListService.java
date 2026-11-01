package com.bbcc.kidly.boundedcontext.search.app;

import com.bbcc.kidly.boundedcontext.search.app.dto.ProductListRequest;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearch;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.search.app.port.SearchEventSender;
import com.bbcc.kidly.boundedcontext.search.app.port.SearchUnavailableException;
import com.bbcc.kidly.boundedcontext.search.domain.SearchErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;
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
import org.springframework.stereotype.Service;

/**
 * 구매자 상품 목록 조회 유스케이스다. 요청을 검색 조건으로 바꿔 검색 색인에서 찾는다.
 * 로그인 회원이 검색어로 찾은 첫 페이지면 검색 이벤트(search.performed)를 보낸다(#611).
 * DB를 쓰지 않으므로 트랜잭션을 열지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductListService {

    private final ProductSearch productSearch;
    private final SearchEventSender searchEventSender;

    /**
     * @param memberId 로그인 회원 ID. 비로그인이면 null
     */
    public Page<ProductSearchItem> list(ProductListRequest request, Long memberId) {
        ProductSearchCondition condition = new ProductSearchCondition(
            request.categoryId(),
            stripKeyword(request.keyword()),
            groupByCode(request.attributesOrEmpty()),
            ProductSearchSort.valueOf(request.sortOrDefault()));
        Page<ProductSearchItem> result = search(condition, request);
        if (isNewSearchByMember(memberId, condition, request)) {
            searchEventSender.send(searchPerformed(memberId, condition, result.getTotalElements()));
        }
        return result;
    }

    private Page<ProductSearchItem> search(ProductSearchCondition condition, ProductListRequest request) {
        try {
            return productSearch.search(condition, PageRequest.of(request.pageOrDefault(), request.sizeOrDefault()));
        } catch (SearchUnavailableException e) {
            // 요청마다 같은 장애가 반복되므로 스택 트레이스 대신 위치(색인 · 상태)와 원인 종류만 남긴다.
            // 원인 메시지에는 Elasticsearch가 돌려준 요청 일부(검색어)가 들어갈 수 있어 남기지 않는다
            log.warn("상품 검색 색인을 쓸 수 없어 503으로 응답합니다. {} cause={}", e.getMessage(),
                e.getCause() == null ? null : e.getCause().getClass().getSimpleName());
            throw new BusinessException(SearchErrorCode.SEARCH_UNAVAILABLE);
        }
    }

    // 추천이 쓰는 건 회원이 무엇을 찾았는지다. 검색어 없는 둘러보기와 같은 검색의 다음 페이지는 새 검색이 아니다
    private boolean isNewSearchByMember(Long memberId, ProductSearchCondition condition, ProductListRequest request) {
        return memberId != null
            && condition.keyword() != null && !condition.keyword().isEmpty()
            && request.pageOrDefault() == 0;
    }

    private SearchPerformedEvent searchPerformed(Long memberId, ProductSearchCondition condition, long resultCount) {
        return new SearchPerformedEvent(UUID.randomUUID(), LocalDateTime.now(), new SearchPerformedEvent.Data(
            memberId, condition.keyword(), condition.categoryId(), condition.attributeValueIds(), resultCount));
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
