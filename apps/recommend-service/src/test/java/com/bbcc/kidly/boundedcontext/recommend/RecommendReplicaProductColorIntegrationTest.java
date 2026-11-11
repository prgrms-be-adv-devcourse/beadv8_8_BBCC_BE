package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOption;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 정보 변경됨의 옵션 색상을 상품 복사본에 남기는 동작을 실제 PostgreSQL에서 확인한다.
 * 색상은 홈 맛보기 AI가 이유를 쓸 때 상품 특성으로 쓴다.
 */
@IntegrationTest
class RecommendReplicaProductColorIntegrationTest {

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("옵션 값 중 색상(COLOR)을 중복 없이 복사본에 남기고, 다시 읽어도 이름 순서로 같다")
    void saveDistinctOptionColors() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEventAt(List.of(
            optionWithColor(1L, "110", "네이비"),
            optionWithColor(2L, "120", "네이비"),
            optionWithColor(3L, "110", "아이보리")), PRODUCT_CHANGED_AT));

        List<String> colors = readColors();

        assertThat(colors).containsExactly("네이비", "아이보리");
    }

    @Test
    @DisplayName("옵션 값이 없는 예전 상품 정보 변경됨을 받으면 색상은 빈 목록이다")
    void saveNoColorsForOldEventWithoutOptionValues() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEventAt(List.of(
            productOption(1L, "110", 105, 115)), PRODUCT_CHANGED_AT));

        assertThat(readColors()).isEmpty();
    }

    @Test
    @DisplayName("더 늦게 일어난 이벤트를 받으면 색상이 새 옵션의 색상으로 바뀐다")
    void replaceColorsWithLaterEvent() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEventAt(List.of(
            optionWithColor(1L, "110", "네이비")), PRODUCT_CHANGED_AT));
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEventAt(List.of(
            optionWithColor(1L, "110", "베이지")), PRODUCT_CHANGED_AT.plusMinutes(1)));

        assertThat(readColors()).containsExactly("베이지");
    }

    // 색상은 지연 로딩이라 새 트랜잭션에서 읽는다
    private List<String> readColors() {
        return transactionTemplate.execute(status -> List.copyOf(
            replicaProductRepository.findById(PRODUCT_A_ID).orElseThrow().getColors()));
    }

    private static ProductChangedEvent.Option optionWithColor(Long optionId, String size, String color) {
        ProductChangedEvent.Option option = productOption(optionId, size, 105, 125, 25_000L, false,
            OptionSaleStatus.ON_SALE);
        return new ProductChangedEvent.Option(option.optionId(), option.optionName(), option.size(), option.price(),
            option.minHeightCm(), option.maxHeightCm(), option.soldOut(), option.saleStatus(),
            List.of(new ProductChangedEvent.AttributeEntry("COLOR", 900L + optionId, color),
                new ProductChangedEvent.AttributeEntry("SIZE", 800L + optionId, size)));
    }
}
