package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProductRepository;
import com.bbcc.kidly.boundedcontext.search.domain.SearchSeller;
import com.bbcc.kidly.boundedcontext.search.domain.SearchSellerRepository;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 상품 · 판매자 변경됨을 받아 검색 문서를 만들고, 늦은 이벤트만 반영하는 저장은 저장소에 맡기는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class SearchIndexServiceTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);
    private static final LocalDateTime PRODUCT_CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Mock
    private SearchProductRepository productRepository;

    @Mock
    private SearchSellerRepository sellerRepository;

    @InjectMocks
    private SearchIndexService searchIndexService;

    @Captor
    private ArgumentCaptor<SearchProduct> productCaptor;

    @Captor
    private ArgumentCaptor<SearchSeller> sellerCaptor;

    @Test
    @DisplayName("상품 정보 변경됨의 값과 발생 시각으로 검색 문서를 만들어 늦은 것만 저장하도록 넘긴다")
    void appliesProductChanged() {
        ProductChangedEvent event = new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new ProductChangedEvent.Data(1L, 7L, "여아 원피스", 3L, ProductStatus.ON_SALE,
                "https://cdn.kidly.test/a-thumb.jpg", PRODUCT_CREATED_AT, false,
                List.of(
                    option(25_000L, false, new ProductChangedEvent.AttributeEntry("COLOR", 5L, "네이비")),
                    option(22_000L, true, new ProductChangedEvent.AttributeEntry("COLOR", 3L, "아이보리"))),
                List.of(new ProductChangedEvent.AttributeEntry("FABRIC_COMPOSITION", null, "면 100%"))));

        searchIndexService.applyProductChanged(event);

        verify(productRepository).saveIfNewer(productCaptor.capture());
        SearchProduct saved = productCaptor.getValue();
        assertThat(saved)
            .extracting(SearchProduct::getProductId, SearchProduct::getSellerId, SearchProduct::getName,
                SearchProduct::getCategoryId, SearchProduct::getStatus, SearchProduct::getThumbnailUrl,
                SearchProduct::getProductCreatedAt, SearchProduct::isSizeFree, SearchProduct::getMinPrice,
                SearchProduct::isSoldOut, SearchProduct::getOccurredAt)
            .containsExactly(1L, 7L, "여아 원피스", 3L, ProductStatus.ON_SALE, "https://cdn.kidly.test/a-thumb.jpg",
                PRODUCT_CREATED_AT, false, 25_000L, false, OCCURRED_AT);
        assertThat(saved.getAttributes()).isEqualTo(Map.of("COLOR", List.of(3L, 5L)));
    }

    @Test
    @DisplayName("판매자 변경됨의 상호명과 발생 시각으로 검색 판매자를 만들어 늦은 것만 저장하도록 넘긴다")
    void appliesSellerChanged() {
        SellerChangedEvent event = new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new SellerChangedEvent.Data(7L, "키들리 상점", SellerStatus.ACTIVE));

        searchIndexService.applySellerChanged(event);

        verify(sellerRepository).saveIfNewer(sellerCaptor.capture());
        assertThat(sellerCaptor.getValue())
            .extracting(SearchSeller::getSellerId, SearchSeller::getBusinessName, SearchSeller::getOccurredAt)
            .containsExactly(7L, "키들리 상점", OCCURRED_AT);
    }

    private static ProductChangedEvent.Option option(
        long price, boolean soldOut, ProductChangedEvent.AttributeEntry... values) {
        return new ProductChangedEvent.Option(1L, "옵션", "110", price, null, null, soldOut,
            OptionSaleStatus.ON_SALE, List.of(values));
    }
}
