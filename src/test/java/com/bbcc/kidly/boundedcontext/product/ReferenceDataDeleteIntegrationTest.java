package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기준 데이터를 지울 때 쓰는 조회와 지운 행의 저장 동작을 실제 PostgreSQL로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 * 의류 유형 · 항목 · 의류 > 상의는 {@link ClothingTopSaver}가 저장한다.
 */
@IntegrationTest
class ReferenceDataDeleteIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 10, 0);

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 「쓰이는 중인지」 찾는 조회와, 그 조회가 true가 되게 하는 쪽(가리키는 유형 · 카테고리)이다.
     */
    record InUse(
        BiFunction<ReferenceDataDeleteIntegrationTest, SavedClothingTop, Boolean> query,
        Function<SavedClothingTop, BaseSoftDeleteEntity> owner) {
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inUseQueries")
    @DisplayName("TYPE-10 가리키는 유형 · 카테고리를 지우지 않았을 때만 쓰이는 중으로 찾는다")
    void findInUseOnlyWhileOwnerIsLive(InUse inUse) {
        SavedClothingTop saved = clothingTopSaver.save();

        inTransaction(() -> assertThat(inUse.query().apply(this, saved)).isTrue());
        inTransaction(() -> managed(inUse.owner().apply(saved)).delete(NOW));
        inTransaction(() -> assertThat(inUse.query().apply(this, saved)).isFalse());
    }

    static Stream<Named<InUse>> inUseQueries() {
        return Stream.of(
            Named.of("유형에 연결된 항목", new InUse(
                (test, saved) -> test.productTypeRepository.existsByAttributesAttributeAndDeletedAtIsNull(
                    saved.clothing().navy().getAttribute()),
                saved -> saved.clothing().type())),
            Named.of("카테고리가 가리키는 상품 유형", new InUse(
                (test, saved) -> test.categoryRepository.existsByProductTypeAndDeletedAtIsNull(
                    saved.clothing().type()),
                SavedClothingTop::top)),
            Named.of("자식이 있는 중간 카테고리", new InUse(
                (test, saved) -> test.categoryRepository.existsByParentAndDeletedAtIsNull(saved.clothingCategory()),
                SavedClothingTop::top)),
            Named.of("카테고리가 필수로 연결한 실측 항목", new InUse(
                (test, saved) -> test.categoryRepository.existsByMeasurementItemsMeasurementItemAndDeletedAtIsNull(
                    saved.top().getMeasurementItems().get(0).getMeasurementItem()),
                SavedClothingTop::top)));
    }

    // 엔티티를 바꾸는 트랜잭션에서 다시 읽은 객체를 돌려준다
    private BaseSoftDeleteEntity managed(BaseSoftDeleteEntity entity) {
        Object id = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        return entityManager.find(entity.getClass(), id);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
