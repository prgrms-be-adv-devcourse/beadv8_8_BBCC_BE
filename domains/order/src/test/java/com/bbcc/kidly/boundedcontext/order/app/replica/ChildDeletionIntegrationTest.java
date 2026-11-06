package com.bbcc.kidly.boundedcontext.order.app.replica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderPlacement;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.event.ChildProfileEventListener;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.wish.app.WishErrorCode;
import com.bbcc.kidly.boundedcontext.wish.app.WishRequestService;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ChildWishActor;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ChildDeletionIntegrationTest {

    private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 11, 3, 10, 0);

    @Autowired
    private ChildProfileEventListener listener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderPlacement placement;

    @Autowired
    private WishRequestService wishRequestService;

    @Autowired
    private ProductReplicaRepository products;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("없는 자녀의 프로필 없는 삭제를 먼저 받아도 과거 활성 이벤트로 되살아나지 않는다")
    void deletionBeforeOldProfileKeepsDeleted() {
        listener.onChildChanged(deleted(DELETED_AT));
        listener.onChildChanged(active(DELETED_AT.minusMinutes(1)));

        assertThat(jdbcTemplate.queryForObject("select is_deleted from orders.replica_child where child_id = 1",
            Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select event_occurred_at from orders.replica_child where child_id = 1",
            LocalDateTime.class)).isEqualTo(DELETED_AT);
    }

    @Test
    @DisplayName("삭제만 받아도 프로필 없이 ID · 보호자 · 삭제 시각을 저장한다")
    void storesDeletionWithoutProfile() {
        listener.onChildChanged(deleted(DELETED_AT));
        var row = jdbcTemplate.queryForMap("select * from orders.replica_child where child_id = 1");
        assertThat(row).containsEntry("child_id", 1L).containsEntry("member_id", 1L)
            .containsEntry("is_deleted", true).containsEntry("height_cm", null).containsEntry("weight_kg", null);
    }

    @Test
    @DisplayName("나노초가 있는 삭제 중복·같은 시각 활성 이벤트는 값과 수정 시각을 바꾸지 않는다")
    void duplicateAndEqualTimestampDoNotChangeDeletion() {
        LocalDateTime at = DELETED_AT.withNano(123_456_123);
        listener.onChildChanged(deleted(at));
        var before = jdbcTemplate.queryForMap("select * from orders.replica_child where child_id = 1");
        listener.onChildChanged(deleted(at));
        listener.onChildChanged(active(at));
        assertThat(jdbcTemplate.queryForMap("select * from orders.replica_child where child_id = 1")).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("select event_occurred_at from orders.replica_child",
            LocalDateTime.class)).isEqualTo(at.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("더 최신의 정상 프로필은 기존 최신 이벤트 우선 정책대로 삭제 복사본을 채운다")
    void newerCompleteProfileIsApplied() {
        listener.onChildChanged(deleted(DELETED_AT));
        listener.onChildChanged(active(DELETED_AT.plusMinutes(1)));
        assertThat(jdbcTemplate.queryForObject("select is_deleted from orders.replica_child", Boolean.class))
            .isFalse();
        assertThat(jdbcTemplate.queryForObject("select height_cm from orders.replica_child", BigDecimal.class))
            .isEqualByComparingTo("110.0");
    }

    @Test
    @DisplayName("프로필 없는 활성 행은 DB 제약이 거절한다")
    void databaseRejectsActiveWithoutProfile() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
            insert into orders.replica_child
                (child_id, member_id, is_deleted, event_occurred_at, created_at, updated_at)
            values (1, 1, false, now(), now(), now())
            """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("프로필 없는 삭제 복사본으로 주문하거나 조르기를 보낼 수 없다")
    void deletedChildCannotOrderOrWish() {
        listener.onChildChanged(deleted(DELETED_AT));
        products.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, DELETED_AT)));
        var command = new PlaceOrderCommand(List.of(new PlaceOrderCommand.Item(1L, 1L, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("수령인", "01012345678", "12345", "서울"));
        assertThatThrownBy(() -> placement.place(1L, command, null))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.CHILD_NOT_ORDERABLE));
        assertThatThrownBy(() -> wishRequestService.send(new ChildWishActor(1L, 1L), List.of(1L), null))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(WishErrorCode.CHILD_NOT_FOUND));
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.orders", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.wish_request", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("최초 삭제와 과거 프로필이 동시에 와도 시작 순서와 무관하게 삭제로 수렴한다")
    void concurrentDeletionAndOldProfile(boolean deleteFirst) throws Exception {
        Runnable deletion = () -> listener.onChildChanged(deleted(DELETED_AT));
        Runnable oldProfile = () -> listener.onChildChanged(active(DELETED_AT.minusMinutes(1)));
        firstCommitsAfterSecondWaits(deleteFirst ? deletion : oldProfile, deleteFirst ? oldProfile : deletion);
        assertThat(jdbcTemplate.queryForObject("select is_deleted from orders.replica_child", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select event_occurred_at from orders.replica_child",
            LocalDateTime.class)).isEqualTo(DELETED_AT);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.replica_child", Long.class)).isEqualTo(1);
    }

    private static ChildChangedEvent deleted(LocalDateTime at) {
        return new ChildChangedEvent(UUID.randomUUID(), at,
            new ChildChangedEvent.Data(1L, 1L, null, null, null, null, null, null, true));
    }

    private static ChildChangedEvent active(LocalDateTime at) {
        return new ChildChangedEvent(UUID.randomUUID(), at,
            new ChildChangedEvent.Data(1L, 1L, new BigDecimal("110.0"), new BigDecimal("20.0"), Gender.MALE,
                LocalDate.of(2020, 1, 1), null, null, false));
    }
    private void firstCommitsAfterSecondWaits(Runnable newer, Runnable older) throws Exception {
        CountDownLatch flushed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger olderPid = new AtomicInteger();
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(s -> {
            newer.run();
            entityManager.flush();
            flushed.countDown();
            await(release);
        }));
        CompletableFuture<Void> second = null;
        try {
            assertThat(flushed.await(10, TimeUnit.SECONDS)).isTrue();
            second = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(s -> {
                olderPid.set(jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class));
                older.run();
            }));
            AsyncWait.until(Duration.ofSeconds(10), () -> Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock')",
                Boolean.class, olderPid.get())));
        } finally {
            release.countDown();
            try {
                first.get(15, TimeUnit.SECONDS);
            } finally {
                if (second != null) {
                    second.get(15, TimeUnit.SECONDS);
                }
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
