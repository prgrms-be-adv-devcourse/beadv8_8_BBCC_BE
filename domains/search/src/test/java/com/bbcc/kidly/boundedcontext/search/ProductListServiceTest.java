package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.search.app.ProductListService;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ProductListServiceTest {

    @Mock
    private ProductSearch productSearch;

    @Mock
    private SearchEventSender searchEventSender;

    @InjectMocks
    private ProductListService productListService;

    @Test
    @DisplayName("보내지 않은 값은 정렬 NEWEST · 0페이지 · 20개로 찾는다")
    void defaults() {
        givenEmptyResult();

        productListService.list(new ProductListRequest(null, null, null, null, null, null), null);

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
            List.of("COLOR:3", "SIZE:11", "COLOR:5", "COLOR:3"), "PRICE_ASC", 2, 10), null);

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

        assertThat(productListService.list(new ProductListRequest(null, null, null, null, null, null), null))
            .isSameAs(result);
    }

    @Test
    @DisplayName("검색 색인을 쓸 수 없으면 503 SEARCH_UNAVAILABLE로 알린다. 로그에 검색어 · 원인 메시지를 남기지 않는다")
    void unavailable(CapturedOutput output) {
        given(productSearch.search(any(), any()))
            .willThrow(new SearchUnavailableException("Elasticsearch에서 상품을 찾지 못했습니다. index=local-products",
                new IllegalStateException("원피스 여아용 검색 중 오류")));

        assertThatThrownBy(() -> productListService.list(
            new ProductListRequest(null, "원피스 여아용", null, null, null, null), 7L))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SearchErrorCode.SEARCH_UNAVAILABLE));
        assertThat(output.getOut()).contains("index=local-products").contains("IllegalStateException")
            .doesNotContain("원피스 여아용");
        verify(searchEventSender, never()).send(any());
    }

    @Test
    @DisplayName("로그인 회원이 검색어로 찾으면 검색어 · 조건 · 전체 결과 수를 담은 검색 이벤트를 보낸다")
    void sendsSearchPerformed() {
        given(productSearch.search(any(), any())).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 35));

        productListService.list(new ProductListRequest(3L, "  원피스 여아용 ",
            List.of("COLOR:3", "SIZE:11", "COLOR:5"), "PRICE_ASC", 0, 20), 7L);

        ArgumentCaptor<SearchPerformedEvent> captor = ArgumentCaptor.forClass(SearchPerformedEvent.class);
        verify(searchEventSender).send(captor.capture());
        SearchPerformedEvent event = captor.getValue();
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.data().memberId()).isEqualTo(7L);
        assertThat(event.data().keyword()).isEqualTo("원피스 여아용");
        assertThat(event.data().categoryId()).isEqualTo(3L);
        assertThat(event.data().attributeValueIds())
            .containsExactly(Map.entry("COLOR", List.of(3L, 5L)), Map.entry("SIZE", List.of(11L)));
        assertThat(event.data().resultCount()).isEqualTo(35L);
    }

    @Test
    @DisplayName("결과가 0건이어도 검색 이벤트를 보낸다")
    void sendsWhenNoResult() {
        givenEmptyResult();

        productListService.list(new ProductListRequest(null, "없는상품", null, null, null, null), 7L);

        ArgumentCaptor<SearchPerformedEvent> captor = ArgumentCaptor.forClass(SearchPerformedEvent.class);
        verify(searchEventSender).send(captor.capture());
        assertThat(captor.getValue().data().resultCount()).isZero();
        assertThat(captor.getValue().data().attributeValueIds()).isEmpty();
    }

    @Test
    @DisplayName("비로그인 검색은 이벤트를 보내지 않는다")
    void anonymousDoesNotSend() {
        givenEmptyResult();

        productListService.list(new ProductListRequest(null, "원피스", null, null, null, null), null);

        verify(searchEventSender, never()).send(any());
    }

    @Test
    @DisplayName("검색어가 없거나 공백뿐인 목록 조회(카테고리 둘러보기)는 이벤트를 보내지 않는다")
    void withoutKeywordDoesNotSend() {
        givenEmptyResult();

        productListService.list(new ProductListRequest(3L, null, null, null, null, null), 7L);
        productListService.list(new ProductListRequest(3L, "   ", null, null, null, null), 7L);

        verify(searchEventSender, never()).send(any());
    }

    @Test
    @DisplayName("같은 검색의 다음 페이지는 이벤트를 다시 보내지 않는다")
    void nextPageDoesNotSend() {
        givenEmptyResult();

        productListService.list(new ProductListRequest(null, "원피스", null, null, 1, null), 7L);

        verify(searchEventSender, never()).send(any());
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
