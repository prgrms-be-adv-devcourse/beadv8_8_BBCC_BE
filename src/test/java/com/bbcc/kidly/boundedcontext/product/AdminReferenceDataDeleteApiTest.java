package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProblemDetailAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자가 기준 데이터를 지우는 API를 HTTP 요청부터 응답까지 확인한다.
 * 의류 유형 · 항목 · 의류 > 상의는 {@link ClothingTopSaver}가 저장한다. 지운 행이 목록에서 빠지는지는 통합 테스트가 본다.
 */
@FunctionalTest
class AdminReferenceDataDeleteApiTest {

    private static final Long ADMIN_ID = 3L;
    private static final LocalDateTime PAST = LocalDateTime.of(2026, 10, 1, 0, 0);

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 지울 대상, 그 대상을 지우는 경로, 먼저 지워 두어 대상이 쓰이지 않게 할 것이다.
     */
    record Deletion(
        Function<SavedClothingTop, BaseSoftDeleteEntity> target,
        Function<SavedClothingTop, String> path,
        Function<SavedClothingTop, List<BaseSoftDeleteEntity>> deletedFirst) {
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deletions")
    @DisplayName("쓰이지 않는 기준 데이터를 지우면 204를 주고 지운 시각을 남긴다")
    void deleteReferenceData(Deletion deletion) {
        SavedClothingTop saved = clothingTopSaver.save();
        inTransaction(() -> deletion.deletedFirst().apply(saved).forEach(entity -> managed(entity).delete(PAST)));

        delete(deletion.path().apply(saved)).expectStatus().isNoContent();

        inTransaction(() -> assertThat(managed(deletion.target().apply(saved)).isDeleted()).isTrue());
    }

    static Stream<Named<Deletion>> deletions() {
        return Stream.of(
            Named.of("목록 값", new Deletion(
                saved -> saved.clothing().oatmeal(),
                saved -> "/api/v1/admin/attributes/" + saved.clothing().oatmeal().getAttribute().getId()
                    + "/values/" + saved.clothing().oatmeal().getId(),
                saved -> List.of())),
            Named.of("말단 카테고리", new Deletion(
                SavedClothingTop::top,
                saved -> "/api/v1/admin/categories/" + saved.top().getId(),
                saved -> List.of())),
            Named.of("카테고리가 가리키지 않는 상품 유형", new Deletion(
                saved -> saved.clothing().type(),
                saved -> "/api/v1/admin/product-types/" + saved.clothing().type().getId(),
                saved -> List.of(saved.top()))),
            Named.of("유형에 연결되지 않은 항목", new Deletion(
                saved -> saved.clothing().stripe().getAttribute(),
                saved -> "/api/v1/admin/attributes/" + saved.clothing().stripe().getAttribute().getId(),
                saved -> List.of(saved.clothing().type()))),
            Named.of("카테고리가 연결하지 않은 실측 항목", new Deletion(
                saved -> saved.top().getMeasurementItems().get(0).getMeasurementItem(),
                saved -> "/api/v1/admin/measurement-items/"
                    + saved.top().getMeasurementItems().get(0).getMeasurementItem().getId(),
                saved -> List.of(saved.top()))));
    }

    @Test
    @DisplayName("TYPE-10 카테고리가 가리키는 상품 유형을 지우면 409로 거절한다")
    void rejectDeletingInUse() {
        SavedClothingTop saved = clothingTopSaver.save();

        assertProblem(delete("/api/v1/admin/product-types/" + saved.clothing().type().getId()), HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("없는 기준 데이터나 경로의 항목에 속하지 않은 목록 값을 지우면 404로 거절한다")
    void rejectUnknownTarget() {
        SavedClothingTop saved = clothingTopSaver.save();
        Long colorId = saved.clothing().navy().getAttribute().getId();

        assertProblem(delete("/api/v1/admin/categories/999999"), HttpStatus.NOT_FOUND);
        assertProblem(delete("/api/v1/admin/attributes/" + colorId + "/values/" + saved.clothing().size110().getId()),
            HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("관리자가 아니면 기준 데이터를 지울 수 없다. 비로그인은 401, 구매자 · 판매자는 403이다")
    void rejectNonAdmin() {
        SavedClothingTop saved = clothingTopSaver.save();
        String path = "/api/v1/admin/categories/" + saved.top().getId();

        deleteWith(null, path).expectStatus().isUnauthorized();
        deleteWith(tokens.buyer(1L), path).expectStatus().isForbidden();
        deleteWith(tokens.seller(2L), path).expectStatus().isForbidden();
    }

    private RestTestClient.ResponseSpec delete(String path) {
        return deleteWith(tokens.admin(ADMIN_ID), path);
    }

    private RestTestClient.ResponseSpec deleteWith(String token, String path) {
        return client.delete().uri(path)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .exchange();
    }

    // 엔티티를 읽는 트랜잭션에서 다시 읽은 객체를 돌려준다
    private BaseSoftDeleteEntity managed(BaseSoftDeleteEntity entity) {
        Object id = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        return entityManager.find(entity.getClass(), id);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
