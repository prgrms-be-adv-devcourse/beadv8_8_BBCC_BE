package com.bbcc.kidly.boundedcontext.search;

import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.changeInfo;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.option;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.bbcc.kidly.boundedcontext.search.out.ElasticsearchSearchProductRepository;
import com.bbcc.kidly.boundedcontext.search.out.ProductDocument;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexProperties;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 검색 문서를 Elasticsearch에 이벤트 발생 시각을 외부 버전으로 넘겨 저장하는지 확인한다.
 * 저장된 버전보다 크지 않으면 Elasticsearch가 409로 거절하므로, 늦은 이벤트는 그 거절로 걸러진다.
 */
@ExtendWith(MockitoExtension.class)
class ElasticsearchSearchProductRepositoryTest {

    // 2026-10-15T10:00을 UTC로 본 epoch 초(1792058400)의 마이크로초다
    private static final long OCCURRED_AT_VERSION = 1_792_058_400_000_000L;

    @Mock
    private ElasticsearchClient client;

    @Captor
    private ArgumentCaptor<IndexRequest<ProductDocument>> requestCaptor;

    private ElasticsearchSearchProductRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ElasticsearchSearchProductRepository(client, new SearchIndexProperties("local"));
    }

    @Test
    @DisplayName("상품 ID를 문서 ID로, 이벤트 발생 시각을 외부 버전으로 넘겨 환경별 상품 색인에 저장한다")
    void indexesWithOccurredAtAsExternalVersion() throws IOException {
        SearchProduct product = SearchProduct.create(changeInfo(List.of(
            option(15_000, false, OptionSaleStatus.ON_SALE, value("color", 5L), value("color", 3L)),
            option(12_000, true, OptionSaleStatus.ON_SALE, value("color", 3L))),
            List.of(value("material", 11L), value("memo", null))));

        repository.saveIfNewer(product);

        verify(client).index(requestCaptor.capture());
        IndexRequest<ProductDocument> request = requestCaptor.getValue();
        assertThat(request.index()).isEqualTo("local-products");
        assertThat(request.id()).isEqualTo("1");
        assertThat(request.versionType()).isEqualTo(VersionType.External);
        assertThat(request.version()).isEqualTo(OCCURRED_AT_VERSION);
        ProductDocument document = request.document();
        assertThat(document.productId()).isEqualTo(1L);
        assertThat(document.name()).isEqualTo("상품 A");
        assertThat(document.sellerId()).isEqualTo(7L);
        assertThat(document.categoryId()).isEqualTo(3L);
        assertThat(document.status()).isEqualTo("ON_SALE");
        assertThat(document.minPrice()).isEqualTo(15_000L);
        assertThat(document.soldOut()).isFalse();
        assertThat(document.productCreatedAt()).isEqualTo("2026-10-01T09:00:00");
        assertThat(document.occurredAt()).isEqualTo("2026-10-15T10:00:00");
        // 항목 code와 값 ID를 code:값ID 한 단어로 묶어 keyword 하나로 거른다. 값 ID가 없는 직접 입력 항목은 빠진다
        assertThat(document.attributeValues()).containsExactly("color:3", "color:5", "material:11");
    }

    @Test
    @DisplayName("저장된 문서보다 늦지 않은 이벤트라 버전 충돌(409)이 나면 예외 없이 무시한다")
    void ignoresVersionConflict() throws IOException {
        when(client.index(any(IndexRequest.class))).thenThrow(errorWithStatus(409));

        assertThatCode(() -> repository.saveIfNewer(product())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("버전 충돌이 아닌 Elasticsearch 오류는 다시 시도할 수 있게 그대로 던진다")
    void rethrowsOtherErrors() throws IOException {
        when(client.index(any(IndexRequest.class))).thenThrow(errorWithStatus(503));

        assertThatThrownBy(() -> repository.saveIfNewer(product())).isInstanceOf(ElasticsearchException.class);
    }

    @Test
    @DisplayName("Elasticsearch에 연결하지 못하면 UncheckedIOException으로 던진다")
    void wrapsIoException() throws IOException {
        when(client.index(any(IndexRequest.class))).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> repository.saveIfNewer(product())).isInstanceOf(UncheckedIOException.class);
    }

    private SearchProduct product() {
        return SearchProduct.create(changeInfo(List.of(option(15_000, false, OptionSaleStatus.ON_SALE))));
    }

    static ElasticsearchException errorWithStatus(int status) {
        return new ElasticsearchException("index", ErrorResponse.of(response -> response
            .status(status)
            .error(cause -> cause.type("error").reason("status " + status))));
    }
}
