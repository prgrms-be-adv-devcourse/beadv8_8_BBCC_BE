package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.search.app.ProductListService;
import com.bbcc.kidly.boundedcontext.search.app.dto.ProductListRequest;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearch;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.search.app.port.SearchUnavailableException;
import com.bbcc.kidly.boundedcontext.search.domain.SearchErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductListServiceTest {

    @Mock
    private ProductSearch productSearch;

    @InjectMocks
    private ProductListService productListService;

    @Test
    @DisplayName("보내지 않은 값은 정렬 NEWEST · 0페이지 · 20개로 찾는다")
    void defaults() {
        givenEmptyResult();

        productListService.list(new ProductListRequest(null, null, null, null, null, null));

        ProductSearchCondition condition = capturedCondition();
        assertThat(condition.categoryId()).isNull();
        assertThat(condition.keyword()).isNull();
        assertThat(condition.attributeValueIds()).isEmpty();
        assertThat(condition.sort()).isEqualTo(ProductSearchSort.NEWEST);
        assertThat(capturedPageable()).isEqualTo(PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("검색어 앞뒤 공백을 빼고, 항목 값은 항목 code별로 묶어 같은 값은 한 번만 넘긴다")
    void convertsCondition() {
        givenEmptyResult();

        productListService.list(new ProductListRequest(3L, "  원피스 여아용 ",
            List.of("COLOR:3", "SIZE:11", "COLOR:5", "COLOR:3"), "PRICE_ASC", 2, 10));

        ProductSearchCondition condition = capturedCondition();
        assertThat(condition.categoryId()).isEqualTo(3L);
        assertThat(condition.keyword()).isEqualTo("원피스 여아용");
        assertThat(condition.attributeValueIds())
            .containsExactly(Map.entry("COLOR", List.of(3L, 5L)), Map.entry("SIZE", List.of(11L)));
        assertThat(condition.sort()).isEqualTo(ProductSearchSort.PRICE_ASC);
        assertThat(capturedPageable()).isEqualTo(PageRequest.of(2, 10));
    }

    @Test
    @DisplayName("찾은 결과를 그대로 돌려준다")
    void returnsResult() {
        Page<ProductSearchItem> result = new PageImpl<>(List.of(
            new ProductSearchItem(10L, "여아 원피스", null, "키들리 상점", 15_000L, false, true)), PageRequest.of(0, 20), 1);
        given(productSearch.search(any(), any())).willReturn(result);

        assertThat(productListService.list(new ProductListRequest(null, null, null, null, null, null)))
            .isSameAs(result);
    }

    @Test
    @DisplayName("검색 색인을 쓸 수 없으면 503 SEARCH_UNAVAILABLE로 알린다")
    void unavailable() {
        given(productSearch.search(any(), any()))
            .willThrow(new SearchUnavailableException("Elasticsearch에 연결하지 못했습니다", new RuntimeException()));

        assertThatThrownBy(() -> productListService.list(new ProductListRequest(null, null, null, null, null, null)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SearchErrorCode.SEARCH_UNAVAILABLE));
    }

    private void givenEmptyResult() {
        given(productSearch.search(any(), any())).willReturn(Page.empty());
    }

    private ProductSearchCondition capturedCondition() {
        ArgumentCaptor<ProductSearchCondition> captor = ArgumentCaptor.forClass(ProductSearchCondition.class);
        verify(productSearch).search(captor.capture(), any());
        return captor.getValue();
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productSearch).search(any(), captor.capture());
        return captor.getValue();
    }
}
