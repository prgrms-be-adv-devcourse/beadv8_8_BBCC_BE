package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/**
 * 받는 쪽이 쓰는 앱의 JsonMapper로 상품 정보 변경됨 본문의 data를 읽는지 확인한다.
 * 브랜드 · 설명 · 사이즈별 실측 · 카테고리 경로 칸이 생기기 전에 나간 메시지도 읽을 수 있어야 한다.
 */
@IntegrationTest
class ProductChangedEventJsonIntegrationTest {

    // 브랜드 · 설명 · 사이즈별 실측 · 카테고리 경로 칸이 생기기 전에 나간 본문의 data다
    private static final String DATA_WITHOUT_NEW_FIELDS = """
        {"productId":1,"sellerId":2,"name":"데일리 맨투맨","categoryId":5,"status":"ON_SALE",
         "thumbnailUrl":null,"createdAt":"2026-10-01T09:00:00","sizeFree":false,"options":[],"infoValues":[]}
        """;

    private static final String DATA_WITH_CATEGORY_PATH = """
        {"productId":1,"sellerId":2,"name":"데일리 맨투맨","categoryId":5,"status":"ON_SALE",
         "thumbnailUrl":null,"createdAt":"2026-10-01T09:00:00","sizeFree":false,"options":[],"infoValues":[],
         "categoryPath":[{"id":4,"name":"의류"},{"id":5,"name":"상의"}]}
        """;

    private static final String DATA_WITH_SALE_STARTED_AT = """
        {"productId":1,"sellerId":2,"name":"데일리 맨투맨","categoryId":5,"status":"ON_SALE",
         "thumbnailUrl":null,"createdAt":"2026-10-01T09:00:00","sizeFree":false,"options":[],"infoValues":[],
         "saleStartedAt":"2026-10-02T10:30:00"}
        """;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("브랜드 · 설명 · 사이즈별 실측 칸이 없는 예전 본문을 읽으면 브랜드와 설명은 null, 사이즈별 실측과 카테고리 경로는 빈 목록이다")
    void readDataWithoutNewFields() {
        ProductChangedEvent.Data data = jsonMapper.readValue(DATA_WITHOUT_NEW_FIELDS, ProductChangedEvent.Data.class);

        assertThat(data.productId()).isEqualTo(1L);
        assertThat(data.brand()).isNull();
        assertThat(data.description()).isNull();
        assertThat(data.sizeSpecs()).isEmpty();
        assertThat(data.categoryPath()).isEmpty();
        assertThat(data.saleStartedAt()).isNull();
    }

    @Test
    @DisplayName("판매 시작 시각은 한국 시간 ISO 문자열(시간대 표시 없음)로 읽고 쓴다")
    void readAndWriteSaleStartedAt() {
        ProductChangedEvent.Data data = jsonMapper.readValue(DATA_WITH_SALE_STARTED_AT, ProductChangedEvent.Data.class);

        assertThat(data.saleStartedAt()).isEqualTo(LocalDateTime.of(2026, 10, 2, 10, 30));
        assertThat(jsonMapper.readTree(jsonMapper.writeValueAsString(data)).get("saleStartedAt").asString())
            .isEqualTo("2026-10-02T10:30:00");
    }

    @Test
    @DisplayName("카테고리 경로가 있는 본문을 읽으면 대분류부터 종류까지 ID · 이름을 순서대로 읽는다")
    void readDataWithCategoryPath() {
        ProductChangedEvent.Data data = jsonMapper.readValue(DATA_WITH_CATEGORY_PATH, ProductChangedEvent.Data.class);

        assertThat(data.categoryPath()).containsExactly(
            new ProductChangedEvent.CategoryPathEntry(4L, "의류"),
            new ProductChangedEvent.CategoryPathEntry(5L, "상의"));
    }
}
