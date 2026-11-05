package com.bbcc.kidly.boundedcontext.search.out;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.bbcc.kidly.boundedcontext.search.domain.ProductEmbedding;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProductRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 검색 문서를 Elasticsearch 상품 색인에 저장한다. 늦은 이벤트는 외부 버전 비교로 거른다(VersionedIndexing).
 */
@Repository
@RequiredArgsConstructor
public class ElasticsearchSearchProductRepository implements SearchProductRepository {

    private static final String EMBEDDING = "embedding";
    private static final String EMBEDDING_TEXT_HASH = "embeddingTextHash";

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    @Override
    public void saveIfNewer(SearchProduct product) {
        VersionedIndexing.indexIfNewer(client, properties.productIndex(), String.valueOf(product.getProductId()),
            product.getOccurredAt(), ProductDocument.from(product));
    }

    // Elasticsearch 9.2부터 조회 응답의 _source에서 벡터가 빠지므로 exclude_vectors=false로 달라고 한다.
    // 실패하면 그대로 던진다. 리스너가 다시 시도하고, 그래도 안 되면 DLT로 보낸다(벡터 없이 저장하면 다음에도 같은 상황이 된다)
    @Override
    public Optional<ProductEmbedding> findEmbedding(Long productId) {
        String index = properties.productIndex();
        GetRequest request = GetRequest.of(get -> get
            .index(index)
            .id(String.valueOf(productId))
            .sourceIncludes(EMBEDDING, EMBEDDING_TEXT_HASH)
            .sourceExcludeVectors(false));
        GetResponse<StoredEmbedding> response;
        try {
            response = client.get(request, StoredEmbedding.class);
        } catch (IOException e) {
            throw new UncheckedIOException("저장된 상품 벡터를 읽지 못했습니다. index=" + index + ", id=" + productId, e);
        }
        StoredEmbedding stored = response.found() ? response.source() : null;
        if (stored == null || stored.embedding() == null || stored.embedding().isEmpty()
            || stored.embeddingTextHash() == null) {
            return Optional.empty();
        }
        return Optional.of(new ProductEmbedding(stored.embeddingTextHash(), stored.embedding()));
    }

    record StoredEmbedding(List<Float> embedding, String embeddingTextHash) {
    }
}
