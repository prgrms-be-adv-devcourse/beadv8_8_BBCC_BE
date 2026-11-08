package com.bbcc.kidly.boundedcontext.search;

import static com.bbcc.kidly.boundedcontext.search.SearchEvents.product;
import static com.bbcc.kidly.boundedcontext.search.SearchEvents.seller;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearch;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.search.out.ElasticsearchProductSearch;
import com.bbcc.kidly.boundedcontext.search.out.SearchEmbeddingProperties;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexProperties;
import com.bbcc.kidly.boundedcontext.search.out.SearchSynonyms;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import com.bbcc.kidly.support.SearchIndexes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * 검색 평가 세트(test resources의 search-quality)로 검색 정확도를 잰다(#1025).
 * 상품 · 검색어를 실제 Elasticsearch(nori)에 넣어 관련도순 첫 페이지(20개)를 보고, 결과를 로그로 남긴다.
 * 동의어 사전을 쓴 지금 검색과 사전 없는 검색(비교용)을 함께 재고, 지금 검색은 모든 검색어가 기대대로 나와야 통과한다.
 * CI에는 임베딩 API 키가 없어 키워드 검색만 본다. 의미 검색은 dev에서 따로 잰다.
 * 검색어를 더하거나 사전을 고치면 이 테스트로 결과가 나빠지지 않는지 본다.
 */
@Slf4j
@IntegrationTest
class SearchQualityIntegrationTest {

    private static final long SHOP = 7L;
    private static final long CATEGORY = 3L;
    private static final long COLOR = 5L;
    private static final int PAGE_SIZE = 20;

    @Autowired
    private SearchIndexService indexService;

    @Autowired
    private ProductSearch productSearch;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private SearchIndexProperties indexProperties;

    @Autowired
    private SearchEmbeddingProperties embeddingProperties;

    @Autowired
    private SearchIndexes indexes;

    @Test
    @DisplayName("평가 세트의 모든 검색어가 찾아야 할 상품을 찾고, 나오면 안 되는 상품을 빼고, 첫 결과가 기대한 상품이다")
    void evaluationSet() throws IOException {
        indexProducts();
        List<EvaluationQuery> queries = readQueries();

        Report current = measure(productSearch, queries);
        Report withoutSynonyms = measure(
            new ElasticsearchProductSearch(client, indexProperties, embeddingProperties, SearchSynonyms.empty()),
            queries);

        String summary = current.summary("지금 검색") + "\n" + withoutSynonyms.summary("동의어 사전 없음(비교)");
        log.info("검색 평가 세트 결과(검색어 {}개, 관련도순 상위 {}개)\n{}", queries.size(), PAGE_SIZE, summary);
        assertThat(current.failures()).as("기대와 다른 검색어. 평가 결과:%n%s", summary).isEmpty();
    }

    private void indexProducts() throws IOException {
        for (String[] columns : readRows("search-quality/products.tsv")) {
            long productId = Long.parseLong(columns[0]);
            String brand = columns.length > 2 && !columns[2].isBlank() ? columns[2].strip() : null;
            indexService.applyProductChanged(product(productId, columns[1].strip(), brand, SHOP, CATEGORY,
                ProductStatus.ON_SALE, 10_000, COLOR, (int) (productId % 28) + 1, SearchEvents.OCCURRED_AT));
        }
        indexService.applySellerChanged(seller(SHOP, "키들리 상점"));
        indexes.refresh();
    }

    private List<EvaluationQuery> readQueries() throws IOException {
        List<EvaluationQuery> queries = new ArrayList<>();
        for (String[] columns : readRows("search-quality/queries.tsv")) {
            queries.add(new EvaluationQuery(columns[0].strip(), ids(columns, 1), ids(columns, 2), ids(columns, 3)));
        }
        return queries;
    }

    private static Report measure(ProductSearch search, List<EvaluationQuery> queries) {
        List<String> failures = new ArrayList<>();
        double recallSum = 0;
        double reciprocalRankSum = 0;
        int rankedQueries = 0;
        int fullyFound = 0;
        int zeroResults = 0;
        for (EvaluationQuery query : queries) {
            List<Long> results = search.search(
                    new ProductSearchCondition(null, query.keyword(), Map.of(), ProductSearchSort.RELEVANCE, null),
                    PageRequest.of(0, PAGE_SIZE))
                .getContent().stream().map(ProductSearchItem::productId).toList();
            if (results.isEmpty()) {
                zeroResults++;
            }
            List<String> problems = new ArrayList<>();
            if (query.relevant().isEmpty()) {
                if (!results.isEmpty()) {
                    problems.add("0건이어야 함");
                }
            } else {
                rankedQueries++;
                long found = query.relevant().stream().filter(results::contains).count();
                recallSum += (double) found / query.relevant().size();
                if (found == query.relevant().size()) {
                    fullyFound++;
                } else {
                    problems.add("못 찾음 " + query.relevant().stream().filter(id -> !results.contains(id)).toList());
                }
                for (int rank = 0; rank < results.size(); rank++) {
                    if (query.relevant().contains(results.get(rank))) {
                        reciprocalRankSum += 1.0 / (rank + 1);
                        break;
                    }
                }
            }
            List<Long> forbidden = results.stream().filter(query.forbidden()::contains).toList();
            if (!forbidden.isEmpty()) {
                problems.add("나오면 안 됨 " + forbidden);
            }
            if (!query.first().isEmpty() && (results.isEmpty() || !query.first().contains(results.getFirst()))) {
                problems.add("첫 결과 " + (results.isEmpty() ? "없음" : results.getFirst()) + " (기대 " + query.first() + ")");
            }
            if (!problems.isEmpty()) {
                failures.add(query.keyword() + " → " + results + " : " + String.join(", ", problems));
            }
        }
        return new Report(queries.size(), rankedQueries, fullyFound, zeroResults,
            rankedQueries == 0 ? 0 : recallSum / rankedQueries,
            rankedQueries == 0 ? 0 : reciprocalRankSum / rankedQueries, failures);
    }

    private static List<String[]> readRows(String path) throws IOException {
        try (InputStream input = SearchQualityIntegrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return reader.lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.split("\t", -1))
                .toList();
        }
    }

    private static Set<Long> ids(String[] columns, int index) {
        if (columns.length <= index || columns[index].isBlank()) {
            return Set.of();
        }
        return Arrays.stream(columns[index].split(","))
            .map(String::strip)
            .map(Long::parseLong)
            .collect(Collectors.toUnmodifiableSet());
    }

    private record EvaluationQuery(String keyword, Set<Long> relevant, Set<Long> forbidden, Set<Long> first) {
    }

    private record Report(int queries, int rankedQueries, int fullyFound, int zeroResults, double meanRecall,
                          double meanReciprocalRank, List<String> failures) {

        String summary(String title) {
            StringBuilder summary = new StringBuilder(String.format(Locale.ROOT,
                "[%s] 기대대로 나온 검색어 %d/%d · 찾을 상품을 모두 찾은 검색어 %d/%d · 평균 재현율 %.3f · MRR %.3f · 0건 %d",
                title, queries - failures.size(), queries, fullyFound, rankedQueries, meanRecall, meanReciprocalRank,
                zeroResults));
            failures.forEach(failure -> summary.append("\n  - ").append(failure));
            return summary.toString();
        }
    }
}
