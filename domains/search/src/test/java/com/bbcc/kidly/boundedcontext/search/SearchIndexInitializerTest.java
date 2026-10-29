package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexInitializer;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexProperties;
import java.io.IOException;
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
 * 기동할 때 상품 · 판매자 색인이 없으면 정해 둔 설정(nori 분석기 · 필드 타입)으로 만들고, 있으면 건드리지 않는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class SearchIndexInitializerTest {

    @Mock
    private ElasticsearchClient client;

    @Mock
    private ElasticsearchIndicesClient indices;

    @Captor
    private ArgumentCaptor<CreateIndexRequest> createCaptor;

    private SearchIndexInitializer initializer;

    @BeforeEach
    void setUp() {
        when(client.indices()).thenReturn(indices);
        initializer = new SearchIndexInitializer(client, new SearchIndexProperties("local"));
    }

    @Test
    @DisplayName("색인이 없으면 상품 · 판매자 색인을 설정 파일대로 만든다")
    void createsMissingIndexes() throws IOException {
        when(indices.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(false));

        initializer.createMissingIndexes();

        verify(indices, times(2)).create(createCaptor.capture());
        List<CreateIndexRequest> requests = createCaptor.getAllValues();
        assertThat(requests).extracting(CreateIndexRequest::index).containsExactly("local-products", "local-sellers");
        CreateIndexRequest products = requests.get(0);
        assertThat(products.mappings().dynamic().jsonValue()).isEqualTo("strict");
        assertThat(products.mappings().properties().get("name").text().analyzer()).isEqualTo("nori");
        assertThat(products.mappings().properties().get("attributeValues").isKeyword()).isTrue();
        assertThat(requests.get(1).mappings().properties()).containsKeys("sellerId", "businessName", "occurredAt");
    }

    @Test
    @DisplayName("색인이 이미 있으면 다시 만들지 않는다")
    void skipsExistingIndexes() throws IOException {
        when(indices.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(true));

        initializer.createMissingIndexes();

        verify(indices, never()).create(any(CreateIndexRequest.class));
    }
}
