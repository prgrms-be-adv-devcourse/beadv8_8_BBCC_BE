package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.bbcc.kidly.boundedcontext.search.domain.SearchSeller;
import com.bbcc.kidly.boundedcontext.search.out.ElasticsearchSearchSellerRepository;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexProperties;
import com.bbcc.kidly.boundedcontext.search.out.SellerDocument;
import java.io.IOException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 판매자 문서도 상품과 같이 이벤트 발생 시각을 외부 버전으로 넘겨 늦은 이벤트를 거르는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ElasticsearchSearchSellerRepositoryTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 15, 10, 0, 0, 123_456_000);

    @Mock
    private ElasticsearchClient client;

    @Captor
    private ArgumentCaptor<IndexRequest<SellerDocument>> requestCaptor;

    private ElasticsearchSearchSellerRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ElasticsearchSearchSellerRepository(client, new SearchIndexProperties("dev"));
    }

    @Test
    @DisplayName("판매자 ID를 문서 ID로, 이벤트 발생 시각(마이크로초까지)을 외부 버전으로 넘겨 저장한다")
    void indexesWithOccurredAtAsExternalVersion() throws IOException {
        repository.saveIfNewer(SearchSeller.create(new SearchSeller.ChangeInfo(7L, "키들리 상점", OCCURRED_AT)));

        verify(client).index(requestCaptor.capture());
        IndexRequest<SellerDocument> request = requestCaptor.getValue();
        assertThat(request.index()).isEqualTo("dev-sellers");
        assertThat(request.id()).isEqualTo("7");
        assertThat(request.versionType()).isEqualTo(VersionType.External);
        assertThat(request.version()).isEqualTo(1_792_058_400_123_456L);
        assertThat(request.document()).isEqualTo(new SellerDocument(7L, "키들리 상점", "2026-10-15T10:00:00.123456"));
    }

    @Test
    @DisplayName("저장된 문서보다 늦지 않은 이벤트라 버전 충돌(409)이 나면 예외 없이 무시한다")
    void ignoresVersionConflict() throws IOException {
        when(client.index(any(IndexRequest.class)))
            .thenThrow(ElasticsearchSearchProductRepositoryTest.errorWithStatus(409));

        assertThatCode(() -> repository.saveIfNewer(
            SearchSeller.create(new SearchSeller.ChangeInfo(7L, "키들리 상점", OCCURRED_AT))))
            .doesNotThrowAnyException();
    }
}
