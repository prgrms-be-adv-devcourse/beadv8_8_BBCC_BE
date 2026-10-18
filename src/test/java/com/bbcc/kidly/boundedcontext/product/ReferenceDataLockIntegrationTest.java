package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductRegistrationService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.CategoryService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryChangeRequest;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자의 기준 데이터 변경 · 삭제와 상품 등록 · 연결이 동시에 겹칠 때 뒤의 요청이 앞의 커밋을 기다리는지 실제 스레드 두 개로 확인한다.
 * 앞의 작업은 바깥 트랜잭션 안에서 실행해 커밋 직전에 멈추고, 그동안 뒤의 작업을 시작한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ReferenceDataLockIntegrationTest {

    // 뒤의 작업이 이 시간 안에 끝나면 앞의 커밋을 기다리지 않은 것으로 본다
    private static final long WAIT_MILLIS = 700;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private ProductRegistrationService productRegistrationService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 뒤의 작업이 앞의 커밋을 기다렸는지와, 뒤의 작업이 던진 예외(없으면 null)다.
     */
    record Outcome(boolean waited, Throwable error) {
    }

    @Test
    @DisplayName("TYPE-07 첫 상품 등록이 커밋되기 전이면 카테고리 유형 변경은 등록을 기다렸다가 409로 거절된다")
    void categoryChangeWaitsForFirstRegistration() throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        ProductType hatType = savedHatType(saved);

        Outcome outcome = race(
            () -> productRegistrationService.register(ProductFixture.SELLER_ID, registerRequest(saved)),
            () -> categoryService.change(saved.top().getId(), new CategoryChangeRequest(hatType.getId(), List.of())));

        assertThat(outcome.waited()).isTrue();
        assertThat(outcome.error()).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CategoryErrorCode.CHANGE_BREAKS_PRODUCTS));
    }

    // 앞의 작업을 바깥 트랜잭션에서 실행하고 커밋 직전에 멈춘 채 뒤의 작업을 시작한다. 뒤의 작업이 끝나지 않는지 본 뒤 앞을 커밋한다
    private Outcome race(Runnable first, Runnable second) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstDone = pool.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    first.run();
                    held.countDown();
                    awaitQuietly(release);
                }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Throwable> secondDone = pool.submit(() -> {
                try {
                    second.run();
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            });
            boolean waited = isStillRunning(secondDone);
            release.countDown();
            firstDone.get(10, TimeUnit.SECONDS);
            return new Outcome(waited, secondDone.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static boolean isStillRunning(Future<?> future) throws Exception {
        try {
            future.get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
            return false;
        } catch (TimeoutException e) {
            return true;
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 색상만 필수 옵션 항목으로 둔 모자 유형을 저장한다. 의류 상품의 정보 값 · 사이즈와 맞지 않는 유형이다
    private ProductType savedHatType(SavedClothingTop saved) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Attribute color = entityManager.find(Attribute.class, saved.clothing().navy().getAttribute().getId());
            ProductType hatType = ProductType.define("모자", List.of(ProductTypeFixture.requiredOption(color)));
            entityManager.persist(hatType);
            return hatType;
        });
    }

    // 의류 > 상의에 네이비 · 110 옵션 하나와 110 실측을 채운 판매대기 상품 등록 요청이다
    private static ProductRegisterRequest registerRequest(SavedClothingTop saved) {
        Long size110 = saved.clothing().size110().getId();
        return new ProductRegisterRequest("데일리 맨투맨", "BBCC", saved.top().getId(), null, null, List.of(),
            List.of(new ProductRegisterRequest.AttributeInput(
                saved.clothing().mainMaterial().getId(), List.of(saved.clothing().cotton().getId()), null)),
            List.of(new ProductRegisterRequest.OptionInput(
                List.of(saved.clothing().navy().getId(), size110), 10, null)),
            List.of(new ProductRegisterRequest.PriceInput(List.of(), 25_000L)),
            List.of(new ProductRegisterRequest.SizeSpecInput(size110, 105, 115, Map.of(
                "TOTAL_LENGTH", BigDecimal.valueOf(42), "SHOULDER_WIDTH", BigDecimal.valueOf(30),
                "CHEST_WIDTH", BigDecimal.valueOf(34), "SLEEVE_LENGTH", BigDecimal.valueOf(38)))));
    }
}
