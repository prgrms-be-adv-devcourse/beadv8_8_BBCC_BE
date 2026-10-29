package com.bbcc.kidly.boundedcontext.search.out;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.bbcc.kidly.boundedcontext.search.domain.SearchSeller;
import com.bbcc.kidly.boundedcontext.search.domain.SearchSellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 판매자 문서를 Elasticsearch 판매자 색인에 저장한다. 늦은 이벤트는 외부 버전 비교로 거른다(VersionedIndexing).
 */
@Repository
@RequiredArgsConstructor
public class ElasticsearchSearchSellerRepository implements SearchSellerRepository {

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    @Override
    public void saveIfNewer(SearchSeller seller) {
        VersionedIndexing.indexIfNewer(client, properties.sellerIndex(), String.valueOf(seller.getSellerId()),
            seller.getOccurredAt(), SellerDocument.from(seller));
    }
}
