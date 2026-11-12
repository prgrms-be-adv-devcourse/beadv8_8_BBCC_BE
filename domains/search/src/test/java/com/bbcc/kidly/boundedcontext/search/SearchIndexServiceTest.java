package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.boundedcontext.search.app.port.TextEmbedder;
import com.bbcc.kidly.boundedcontext.search.domain.EmbeddingRefillTarget;
import com.bbcc.kidly.boundedcontext.search.domain.ProductEmbedding;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;
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

    @Mock
    private TextEmbedder textEmbedder;

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
    @DisplayName("상품명 · 브랜드 · 설명 문장을 벡터로 바꿔 문장 해시와 함께 검색 문서에 붙인다")
    void embedsProductText() {
        when(textEmbedder.embed("여아 원피스\n키들리\n봄에 입기 좋은 원피스")).thenReturn(Optional.of(List.of(0.1f, 0.2f)));

        searchIndexService.applyProductChanged(dressEvent("봄에 입기 좋은 원피스"));

        verify(productRepository).saveIfNewer(productCaptor.capture());
        SearchProduct saved = productCaptor.getValue();
        assertThat(saved.getEmbedding().vector()).containsExactly(0.1f, 0.2f);
        assertThat(saved.getEmbedding().textHash()).isEqualTo(saved.getEmbeddingTextHash());
    }

    @Test
    @DisplayName("문장이 저장된 벡터를 만든 문장과 같으면(재고 · 가격만 바뀜) 임베딩 API를 부르지 않고 저장된 벡터를 쓴다")
    void reusesStoredEmbedding() {
        String textHash = SearchProduct.create(changeInfoOf(dressEvent("봄 원피스"))).getEmbeddingTextHash();
        ProductEmbedding stored = new ProductEmbedding(textHash, List.of(0.5f, 0.6f));
        when(productRepository.findEmbedding(1L)).thenReturn(Optional.of(stored));

        searchIndexService.applyProductChanged(dressEvent("봄 원피스"));

        verify(textEmbedder, never()).embed(anyString());
        verify(productRepository).saveIfNewer(productCaptor.capture());
        assertThat(productCaptor.getValue().getEmbedding()).isEqualTo(stored);
    }

    @Test
    @DisplayName("문장이 바뀌었으면 저장된 벡터를 버리고 새로 만든다")
    void reembedsChangedText() {
        when(productRepository.findEmbedding(1L)).thenReturn(Optional.of(new ProductEmbedding("old", List.of(0.5f))));
        when(textEmbedder.embed(anyString())).thenReturn(Optional.of(List.of(0.9f)));

        searchIndexService.applyProductChanged(dressEvent("여름 원피스"));

        verify(productRepository).saveIfNewer(productCaptor.capture());
        assertThat(productCaptor.getValue().getEmbedding().vector()).containsExactly(0.9f);
    }

    @Test
    @DisplayName("임베딩 API를 쓸 수 없으면 벡터 없이 저장한다. 키워드 검색은 그대로 된다")
    void savesWithoutEmbeddingWhenUnavailable() {
        when(textEmbedder.embed(anyString())).thenReturn(Optional.empty());

        searchIndexService.applyProductChanged(dressEvent("봄 원피스"));

        verify(productRepository).saveIfNewer(productCaptor.capture());
        assertThat(productCaptor.getValue().getEmbedding()).isNull();
    }

    @Test
    @DisplayName("벡터 없는 판매중 상품은 저장해 둔 문장으로 벡터를 다시 만들어 붙인다. API를 쓸 수 없는 상품은 건너뛴다")
    void refillsMissingEmbeddings() {
        EmbeddingRefillTarget first = new EmbeddingRefillTarget(1L, "여아 원피스", OCCURRED_AT);
        EmbeddingRefillTarget second = new EmbeddingRefillTarget(2L, "남아 바지", OCCURRED_AT);
        EmbeddingRefillTarget third = new EmbeddingRefillTarget(3L, "아동 모자", OCCURRED_AT);
        when(textEmbedder.enabled()).thenReturn(true);
        when(productRepository.findOnSaleWithoutEmbedding(null, 20)).thenReturn(List.of(first, second, third));
        when(textEmbedder.embed("여아 원피스")).thenReturn(Optional.of(List.of(0.1f)));
        when(textEmbedder.embed("남아 바지")).thenReturn(Optional.empty());
        when(textEmbedder.embed("아동 모자")).thenReturn(Optional.of(List.of(0.3f)));

        int refilled = searchIndexService.refillMissingEmbeddings();

        assertThat(refilled).isEqualTo(2);
        verify(productRepository).attachEmbedding(first, new ProductEmbedding(first.textHash(), List.of(0.1f)));
        verify(productRepository).attachEmbedding(third, new ProductEmbedding(third.textHash(), List.of(0.3f)));
        verify(productRepository, never()).attachEmbedding(eq(second), any());
    }

    @Test
    @DisplayName("한 주기를 다 채워 찾으면 다음 주기는 그 뒤부터 찾고, 끝까지 가면 처음으로 돌아간다. 계속 실패하는 상품이 뒤 상품을 막지 않는다")
    void rotatesRefillCursor() {
        when(textEmbedder.enabled()).thenReturn(true);
        when(textEmbedder.embed(anyString())).thenReturn(Optional.empty());
        List<EmbeddingRefillTarget> fullBatch = LongStream.rangeClosed(1, 20)
            .mapToObj(id -> new EmbeddingRefillTarget(id, "실패하는 문장 " + id, OCCURRED_AT)).toList();
        when(productRepository.findOnSaleWithoutEmbedding(null, 20)).thenReturn(fullBatch);
        when(productRepository.findOnSaleWithoutEmbedding(20L, 20))
            .thenReturn(List.of(new EmbeddingRefillTarget(21L, "뒤 상품", OCCURRED_AT)));

        searchIndexService.refillMissingEmbeddings();
        searchIndexService.refillMissingEmbeddings();
        searchIndexService.refillMissingEmbeddings();

        verify(productRepository, times(2)).findOnSaleWithoutEmbedding(null, 20);
        verify(productRepository).findOnSaleWithoutEmbedding(20L, 20);
        verify(textEmbedder).embed("뒤 상품");
    }

    @Test
    @DisplayName("다시 만드는 벡터의 문장 해시는 이벤트로 만든 문서의 해시와 같다")
    void refillHashMatchesEventHash() {
        SearchProduct product = SearchProduct.create(changeInfoOf(dressEvent("봄 원피스")));

        EmbeddingRefillTarget target = new EmbeddingRefillTarget(1L, product.getEmbeddingText(), OCCURRED_AT);

        assertThat(target.textHash()).isEqualTo(product.getEmbeddingTextHash());
    }

    @Test
    @DisplayName("의미 검색을 끈 환경(키 없음)은 벡터 없는 상품을 찾지도 않는다")
    void skipsRefillWhenDisabled() {
        when(textEmbedder.enabled()).thenReturn(false);

        assertThat(searchIndexService.refillMissingEmbeddings()).isZero();
        verify(productRepository, never()).findOnSaleWithoutEmbedding(any(), anyInt());
        verify(textEmbedder, never()).embed(anyString());
    }

    @Test
    @DisplayName("상품 정보 변경됨의 브랜드를 검색 문서에 옮긴다. 브랜드 칸이 없는 예전 메시지면 null이다")
    void copiesBrand() {
        ProductChangedEvent withBrand = new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new ProductChangedEvent.Data(1L, 7L, "여아 원피스", 3L, ProductStatus.ON_SALE, null, PRODUCT_CREATED_AT,
                false, List.of(option(25_000L, false)), List.of(), "키들리", "봄에 입기 좋은 원피스", List.of()));
        ProductChangedEvent withoutBrand = new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new ProductChangedEvent.Data(2L, 7L, "남아 셔츠", 4L, ProductStatus.ON_SALE, null, PRODUCT_CREATED_AT,
                false, List.of(option(9_000L, false)), List.of()));

        searchIndexService.applyProductChanged(withBrand);
        searchIndexService.applyProductChanged(withoutBrand);

        verify(productRepository, times(2)).saveIfNewer(productCaptor.capture());
        assertThat(productCaptor.getAllValues()).extracting(SearchProduct::getBrand).containsExactly("키들리", null);
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

    private static ProductChangedEvent dressEvent(String description) {
        return new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new ProductChangedEvent.Data(1L, 7L, "여아 원피스", 3L, ProductStatus.ON_SALE, null, PRODUCT_CREATED_AT,
                false, List.of(option(25_000L, false)), List.of(), "키들리", description, List.of()));
    }

    // 서비스가 이벤트를 옮기는 것과 같은 값으로 검색 문서를 만든다(문장 해시를 미리 구하려는 것이다)
    private static SearchProduct.ChangeInfo changeInfoOf(ProductChangedEvent event) {
        ProductChangedEvent.Data data = event.data();
        return new SearchProduct.ChangeInfo(data.productId(), data.sellerId(), data.name(), data.categoryId(),
            data.status(), data.thumbnailUrl(), data.brand(), data.createdAt(), data.sizeFree(),
            List.of(new SearchProduct.OptionInfo(25_000L, false, OptionSaleStatus.ON_SALE, List.of())), List.of(),
            event.occurredAt(), data.description());
    }

    private static ProductChangedEvent.Option option(
        long price, boolean soldOut, ProductChangedEvent.AttributeEntry... values) {
        return new ProductChangedEvent.Option(1L, "옵션", "110", price, null, null, soldOut,
            OptionSaleStatus.ON_SALE, List.of(values));
    }
}
