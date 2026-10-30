package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexProperties;
import com.bbcc.kidly.support.IntegrationTest;
import java.io.IOException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * 검색 서비스가 DB 없이 검색 도메인만으로 뜨는지 본다.
 */
@IntegrationTest
class SearchServiceApplicationIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private SearchIndexProperties properties;

    @Test
    @DisplayName("DB 연결 없이 뜬다")
    void noDatabase() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
    }

    @Test
    @DisplayName("기동할 때 환경별 상품 · 판매자 색인을 만든다")
    void createsIndexes() throws IOException {
        assertThat(client.indices().exists(exists -> exists.index(properties.productIndex())).value()).isTrue();
        assertThat(client.indices().exists(exists -> exists.index(properties.sellerIndex())).value()).isTrue();
        assertThat(properties.productIndex()).isEqualTo("test-products");
    }
}
