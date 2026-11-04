package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/**
 * 받는 쪽이 쓰는 앱의 JsonMapper로 상품 정보 변경됨 본문의 data를 읽는지 확인한다.
 * 브랜드 · 설명 · 사이즈별 실측 칸이 생기기 전에 나간 메시지도 읽을 수 있어야 한다.
 */
@IntegrationTest
class ProductChangedEventJsonIntegrationTest {

    // 브랜드 · 설명 · 사이즈별 실측 칸이 생기기 전에 나간 본문의 data다
    private static final String DATA_WITHOUT_NEW_FIELDS = """
        {"productId":1,"sellerId":2,"name":"데일리 맨투맨","categoryId":5,"status":"ON_SALE",
         "thumbnailUrl":null,"createdAt":"2026-10-01T09:00:00","sizeFree":false,"options":[],"infoValues":[]}
        """;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("브랜드 · 설명 · 사이즈별 실측 칸이 없는 예전 본문을 읽으면 브랜드와 설명은 null, 사이즈별 실측은 빈 목록이다")
    void readDataWithoutNewFields() {
        ProductChangedEvent.Data data = jsonMapper.readValue(DATA_WITHOUT_NEW_FIELDS, ProductChangedEvent.Data.class);

        assertThat(data.productId()).isEqualTo(1L);
        assertThat(data.brand()).isNull();
        assertThat(data.description()).isNull();
        assertThat(data.sizeSpecs()).isEmpty();
    }
}
