package com.bbcc.kidly.boundedcontext.chatbot;

import com.bbcc.kidly.shared.search.dto.ProductHit;
import com.bbcc.kidly.shared.search.dto.ProductSearchQuery;
import com.bbcc.kidly.shared.search.out.ProductSearcher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 검색 서비스 없이 쓰는 상품 검색. 정해 둔 상품(검색어마다 따로 정할 수 있다)을 limit개까지 주고 받은 조건을 남긴다. 테스트가 끝나면 {@link #reset}으로 되돌린다.
 */
public class FakeProductSearcher implements ProductSearcher {

    private final List<ProductSearchQuery> queries = new CopyOnWriteArrayList<>();
    private final Map<String, List<ProductHit>> hitsByKeyword = new ConcurrentHashMap<>();
    private volatile List<ProductHit> hits = List.of();
    private volatile RuntimeException failure;

    public void willReturn(List<ProductHit> hits) {
        this.hits = List.copyOf(hits);
    }

    public void willReturn(String keyword, List<ProductHit> hits) {
        hitsByKeyword.put(keyword, List.copyOf(hits));
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public List<ProductSearchQuery> queries() {
        return List.copyOf(queries);
    }

    public void reset() {
        queries.clear();
        hitsByKeyword.clear();
        hits = List.of();
        failure = null;
    }

    @Override
    public List<ProductHit> search(ProductSearchQuery query) {
        queries.add(query);
        RuntimeException current = failure;
        if (current != null) {
            throw current;
        }
        return hitsByKeyword.getOrDefault(query.keyword(), hits).stream().limit(query.limit()).toList();
    }
}
