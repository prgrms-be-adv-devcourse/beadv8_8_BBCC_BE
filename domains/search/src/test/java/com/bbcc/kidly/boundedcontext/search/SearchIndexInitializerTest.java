package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
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
 * 기동할 때 상품 · 판매자 색인이 없으면 정해 둔 설정(nori 분석기 · 필드 타입)으로 만들고, 있으면 설정 파일의 필드를 더하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class SearchIndexInitializerTest {

    @Mock
    private ElasticsearchClient client;

    @Mock
    private ElasticsearchIndicesClient indices;

    @Captor
    private ArgumentCaptor<CreateIndexRequest> createCaptor;

    @Captor
    private ArgumentCaptor<PutMappingRequest> putMappingCaptor;

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

        initializer.createOrUpdateIndexes();

        verify(indices, times(2)).create(createCaptor.capture());
        List<CreateIndexRequest> requests = createCaptor.getAllValues();
        assertThat(requests).extracting(CreateIndexRequest::index).containsExactly("local-products", "local-sellers");
        CreateIndexRequest products = requests.get(0);
        assertThat(products.mappings().dynamic().jsonValue()).isEqualTo("strict");
        assertThat(products.mappings().properties().get("name").text().analyzer()).isEqualTo("nori");
        assertThat(products.mappings().properties().get("brand").text().analyzer()).isEqualTo("standard");
        assertThat(products.mappings().properties().get("attributeValues").isKeyword()).isTrue();
        assertThat(requests.get(1).mappings().properties()).containsKeys("sellerId", "businessName", "occurredAt");
    }

    @Test
    @DisplayName("색인이 이미 있으면 다시 만들지 않고, 설정 파일의 필드를 보내 새로 더한 필드(브랜드 등)를 넣는다")
    void addsNewFieldsToExistingIndexes() throws IOException {
        when(indices.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(true));

        initializer.createOrUpdateIndexes();

        verify(indices, never()).create(any(CreateIndexRequest.class));
        verify(indices, times(2)).putMapping(putMappingCaptor.capture());
        List<PutMappingRequest> requests = putMappingCaptor.getAllValues();
        assertThat(requests).extracting(request -> request.index().getFirst())
            .containsExactly("local-products", "local-sellers");
        PutMappingRequest products = requests.get(0);
        assertThat(products.dynamic().jsonValue()).isEqualTo("strict");
        assertThat(products.properties().get("brand").text().analyzer()).isEqualTo("standard");
        assertThat(products.properties()).containsKeys("name", "attributeValues", "occurredAt");
    }

    @Test
    @DisplayName("있는 필드의 타입 · 분석기를 바꾸려 해 Elasticsearch가 거절하면 그대로 던져 기동에 실패한다")
    void rethrowsMappingConflict() throws IOException {
        when(indices.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(true));
        when(indices.putMapping(any(PutMappingRequest.class))).thenThrow(error(400, "illegal_argument_exception"));

        assertThatThrownBy(() -> initializer.createOrUpdateIndexes()).isInstanceOf(ElasticsearchException.class);
    }

    @Test
    @DisplayName("확인한 뒤 다른 인스턴스가 먼저 만들어 이미 있다는 오류가 나면 무시하고 기동한다")
    void ignoresIndexCreatedByAnotherInstance() throws IOException {
        when(indices.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(false));
        when(indices.create(any(CreateIndexRequest.class)))
            .thenThrow(error(400, "resource_already_exists_exception"));

        assertThatCode(() -> initializer.createOrUpdateIndexes()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("그 밖의 색인 생성 오류(예: nori 플러그인 없음)는 그대로 던져 기동에 실패한다")
    void rethrowsOtherCreateErrors() throws IOException {
        when(indices.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(false));
        when(indices.create(any(CreateIndexRequest.class))).thenThrow(error(400, "illegal_argument_exception"));

        assertThatThrownBy(() -> initializer.createOrUpdateIndexes()).isInstanceOf(ElasticsearchException.class);
    }

    private static ElasticsearchException error(int status, String type) {
        return new ElasticsearchException("indices.create", ErrorResponse.of(response -> response
            .status(status)
            .error(cause -> cause.type(type).reason(type))));
    }
}
