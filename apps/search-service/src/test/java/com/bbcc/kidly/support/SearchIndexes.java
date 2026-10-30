package com.bbcc.kidly.support;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexInitializer;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.test.context.TestComponent;

/**
 * 테스트에서 색인을 다룬다. 저장한 문서는 새로 고침(기본 1초 주기) 전에는 검색되지 않으므로 검색 전에 refresh()를 부른다.
 */
@TestComponent
public class SearchIndexes {

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;
    private final SearchIndexInitializer initializer;

    public SearchIndexes(ElasticsearchClient client, SearchIndexProperties properties,
        SearchIndexInitializer initializer) {
        this.client = client;
        this.properties = properties;
        this.initializer = initializer;
    }

    public void refresh() {
        try {
            client.indices().refresh(refresh -> refresh.index(properties.productIndex(), properties.sellerIndex()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 색인을 지우고 기동할 때와 같이 다시 만든다. 문서만 지우면(delete_by_query) Elasticsearch가 지운 문서의 외부 버전을
    // 잠시(gc_deletes, 기본 60초) 기억해, 다음 테스트가 같은 발생 시각으로 다시 색인할 때 늦은 이벤트로 보고 거절한다
    void reset() {
        try {
            client.indices().delete(delete -> delete
                .index(properties.productIndex(), properties.sellerIndex())
                .ignoreUnavailable(true));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        initializer.createMissingIndexes();
    }
}
