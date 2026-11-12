package com.bbcc.kidly.boundedcontext.search.out;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.bbcc.kidly.boundedcontext.search.domain.EmbeddingRefillTarget;
import com.bbcc.kidly.boundedcontext.search.domain.ProductEmbedding;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProductRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 검색 문서를 Elasticsearch 상품 색인에 저장한다. 늦은 이벤트는 외부 버전 비교로 거른다(VersionedIndexing).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ElasticsearchSearchProductRepository implements SearchProductRepository {

    private static final String EMBEDDING = "embedding";
    private static final String EMBEDDING_TEXT_HASH = "embeddingTextHash";
    private static final String EMBEDDING_TEXT = "embeddingText";
    private static final String ON_SALE = "ON_SALE";
    private static final int VERSION_CONFLICT = 409;

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

    @Override
    public List<EmbeddingRefillTarget> findOnSaleWithoutEmbedding(Long afterProductId, int limit) {
        String index = properties.productIndex();
        List<Query> filters = new ArrayList<>(List.of(
            Query.of(onSale -> onSale.term(term -> term.field("status").value(ON_SALE))),
            Query.of(text -> text.exists(exists -> exists.field(EMBEDDING_TEXT)))));
        if (afterProductId != null) {
            filters.add(Query.of(after -> after.range(range -> range.number(number -> number
                .field("productId").gt(afterProductId.doubleValue())))));
        }
        SearchResponse<RefillSource> response;
        try {
            response = client.search(search -> search
                .index(index)
                .size(limit)
                .query(query -> query.bool(bool -> bool
                    .filter(filters)
                    .mustNot(Query.of(vector -> vector.exists(exists -> exists.field(EMBEDDING))))))
                .sort(sort -> sort.field(field -> field.field("productId").order(SortOrder.Asc)))
                .source(source -> source.filter(filter -> filter.includes("productId", EMBEDDING_TEXT, "occurredAt"))),
                RefillSource.class);
        } catch (IOException e) {
            throw new UncheckedIOException("벡터 없는 상품을 찾지 못했습니다. index=" + index, e);
        }
        List<EmbeddingRefillTarget> targets = new ArrayList<>();
        for (Hit<RefillSource> hit : response.hits().hits()) {
            RefillSource source = hit.source();
            if (source != null && source.productId() != null && source.embeddingText() != null
                && source.occurredAt() != null) {
                targets.add(new EmbeddingRefillTarget(source.productId(), source.embeddingText(),
                    LocalDateTime.parse(source.occurredAt())));
            }
        }
        return targets;
    }

    // 찾은 문서를 다시 읽어 같은 이벤트로 만든 문서(버전 = 발생 시각)이고 아직 벡터가 없을 때만, 벡터를 붙여 같은 버전으로 다시 저장한다.
    // external_gte는 같은 버전도 받으므로 버전(발생 시각)을 바꾸지 않는다. 그사이 새 이벤트가 저장됐으면 버전이 커져 409로 거절된다
    @Override
    public void attachEmbedding(EmbeddingRefillTarget target, ProductEmbedding embedding) {
        String index = properties.productIndex();
        String id = String.valueOf(target.productId());
        long version = VersionedIndexing.toVersion(target.occurredAt());
        try {
            GetResponse<ProductDocument> stored = client.get(get -> get.index(index).id(id), ProductDocument.class);
            ProductDocument document = stored.found() ? stored.source() : null;
            if (document == null || !Objects.equals(stored.version(), version)
                || !target.embeddingText().equals(document.embeddingText())) {
                log.debug("벡터를 찾은 뒤 문서가 바뀌어 붙이지 않습니다. index={}, id={}", index, id);
                return;
            }
            client.index(request -> request
                .index(index)
                .id(id)
                .version(version)
                .versionType(VersionType.ExternalGte)
                .document(document.withEmbedding(embedding)));
        } catch (ElasticsearchException e) {
            if (e.status() != VERSION_CONFLICT) {
                throw e;
            }
            log.debug("벡터를 만드는 동안 새 이벤트가 저장되어 붙이지 않습니다. index={}, id={}", index, id);
        } catch (IOException e) {
            throw new UncheckedIOException("상품 벡터를 저장하지 못했습니다. index=" + index + ", id=" + id, e);
        }
    }

    record RefillSource(Long productId, String embeddingText, String occurredAt) {
    }

    record StoredEmbedding(List<Float> embedding, String embeddingTextHash) {
    }
}
