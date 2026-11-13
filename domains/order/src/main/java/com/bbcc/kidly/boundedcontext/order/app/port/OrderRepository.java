package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 주문 저장소. 구현은 out/persistence (Spring Data가 만든다).
 */
public interface OrderRepository {

    /**
     * 자동 구매확정 대상(주문 ID, 주문상품 ID).
     */
    interface AutoConfirmRow {

        Long getOrderId();

        Long getOrderItemId();
    }

    /**
     * 자녀 구매 내역 한 줄(#1000). 가격 · 배송지 · 자녀 칸은 읽지 않는다. 판매자 ID는 배송 상태를 맞추는 데만 쓴다(응답에 없음).
     */
    interface ChildPurchaseRow {

        Long getOrderItemId();

        Long getOrderId();

        LocalDateTime getOrderedAt();

        Long getProductId();

        Long getSellerId();

        String getProductName();

        String getOptionName();

        int getQuantity();

        OrderItem.Status getItemStatus();

        Order.Status getOrderStatus();

        LocalDateTime getDeliveredAt();
    }

    /**
     * 이 자녀가 입을 아이로 지정된 주문상품 중 결제된 적 있는 주문(paidAt 있음)의 것. 주문 일시 · 주문상품 ID 최신순(#1000).
     */
    Page<ChildPurchaseRow> findChildPurchases(Long childId, Pageable pageable);

    Order save(Order order);

    Optional<Order> findById(Long id);

    /**
     * 주문 1건을 SELECT … FOR UPDATE로 읽는다(#655). 같은 주문의 판매자 취소가 동시에 오면 뒤쪽이 앞쪽 커밋을 기다렸다가
     * 그 결과(다른 몫의 취소)를 보고 판단한다. 주문 행 → 배송 행 순서로 잠그므로 구매자 취소(주문 → 배송)와 순서가 같다.
     */
    Optional<Order> findByIdForUpdate(Long id);

    /**
     * 바뀐 주문을 지금 DB에 반영한다(#449). 받은 이벤트 처리 실패를 리스너 안에서 잡아 보관하려고 쓴다.
     */
    void flush();

    boolean existsById(Long id);

    /**
     * 회원의 주문 1건. 남의 주문이면 없는 것으로 본다.
     */
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    /**
     * 회원이 같은 요청 키로 만든 주문(#351). 키는 회원마다 하나의 주문에만 쓰인다(유니크 제약).
     */
    Optional<Order> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    /**
     * 회원의 주문 1건을 주문상품과 함께 한 번에 읽는다(타임라인, #352). 남의 주문이면 없는 것으로 본다. 주문상품은 ID 오름차순.
     */
    Optional<Order> findWithItemsByIdAndMemberId(Long id, Long memberId);

    /**
     * ID 목록의 주문을 주문상품과 함께 한 번에 읽는다(목록 화면의 건마다 조회 방지). 순서는 보장하지 않으므로 ID로 찾는다.
     * 주문상품은 ID 오름차순. 없는 ID는 결과에서 빠진다.
     */
    List<Order> findAllWithItemsByIdIn(Collection<Long> ids);

    /**
     * 판매 주문 조회용 주문상품 줄(#997): 주문 · 배송지와 주문상품 값만 한 번에 읽는다(주문상품의 자녀 칸은 select하지 않는다).
     * 주문 ID 오름차순, 그 안은 주문상품 ID 오름차순. 없는 ID는 빠진다.
     */
    List<SellerOrderItemRow> findSellerOrderItemsByOrderIdIn(Collection<Long> orderIds);

    /**
     * 판매 주문 조회의 한 줄(주문 + 주문상품 하나). 자녀 정보는 없다.
     */
    interface SellerOrderItemRow {

        Long getOrderId();

        Long getMemberId();

        LocalDateTime getOrderedAt();

        String getRecipientName();

        String getPhone();

        String getZipCode();

        String getAddress();

        Long getOrderItemId();

        Long getProductId();

        Long getOptionId();

        Long getSellerId();

        String getProductName();

        String getOptionName();

        String getSize();

        long getUnitPrice();

        int getQuantity();

        long getAmount();

        OrderItem.Status getStatus();

        LocalDateTime getDeliveredAt();

        LocalDateTime getConfirmedAt();

        OrderItem.ConfirmType getConfirmType();

        String getUnavailableReason();
    }

    /**
     * 회원의 주문 목록. 최근 주문부터.
     */
    Page<Order> findAllByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);

    /**
     * 그 회원의 이 상태 주문(#1001 회원 탈퇴: 결제대기 주문 취소). ID 순.
     */
    List<Order> findAllByMemberIdAndStatusOrderByIdAsc(Long memberId, Order.Status status);

    /**
     * 회원의 주문 수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long countByMemberId(Long memberId);

    /**
     * 모든 회원의 주문 목록(관리자). 최근 주문부터.
     */
    Page<Order> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 회원의 그 상태 주문 목록(#1280). 최근 주문부터. 건수(totalElements)도 같은 조건으로 센다.
     */
    Page<Order> findAllByMemberIdAndStatusOrderByIdDesc(Long memberId, Order.Status status, Pageable pageable);

    /**
     * 회원의 그 상태 주문 수(#1280). 목록을 읽을 수 없는 큰 page의 빈 페이지에 건수를 채운다.
     */
    long countByMemberIdAndStatus(Long memberId, Order.Status status);

    /**
     * 모든 회원의 그 상태 주문 목록(#1280, 관리자). 최근 주문부터. 건수(totalElements)도 같은 조건으로 센다.
     */
    Page<Order> findAllByStatusOrderByIdDesc(Order.Status status, Pageable pageable);

    /**
     * 그 상태 주문 수(#1280, 관리자). 목록을 읽을 수 없는 큰 page의 빈 페이지에 건수를 채운다.
     */
    long countByStatus(Order.Status status);

    /**
     * from 이후(포함)에 취소된 주문(#1325, 관리자). 지금 CANCELLED이고 주문 대상 상태 이력에 CANCELLED가 된 시각이 from 이상인 것.
     * 최근 주문부터, 건수(totalElements)도 같은 조건으로 센다.
     */
    Page<Order> findCancelledSince(LocalDateTime from, Pageable pageable);

    /**
     * from 이후(포함)에 취소된 주문 수(#1325). 목록을 읽을 수 없는 큰 page의 빈 페이지에 건수를 채운다.
     */
    long countCancelledSince(LocalDateTime from);

    /**
     * 그 판매자 상품의 주문상품 중 이 상태이고 주문이 그 상태가 아닌 수(#1120 판매자 미종결 주문상품). 주문 전체 취소(구매자 취소 ·
     * 결제 만료 · 재고 부족 · 결제 실패)는 주문 상태만 바꾸고 주문상품은 주문됨으로 남으므로 주문 상태로 함께 거른다. 건수 쿼리 한 번.
     */
    long countSellerItems(Long sellerId, OrderItem.Status itemStatus, Order.Status excludedOrderStatus);

    /**
     * 그 판매자 상품의 주문됨(ORDERED) 주문상품이 든 이 상태들의 주문 ID(#1121 판매 불가 판매자 정리). afterId보다 큰 ID만
     * 오름차순으로 limit건(ID 커서).
     */
    List<Long> findOrderIdsWithSellerItems(Long sellerId, OrderItem.Status itemStatus,
        Collection<Order.Status> statuses, Long afterId, Limit limit);

    /**
     * 전체 주문 수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long count();

    /**
     * 결제 마감이 지난(now 포함) 주문 ID. afterId보다 큰 ID만 주문 ID 오름차순으로 limit건(한 실행 안의 ID 커서).
     */
    List<Long> findOverduePaymentOrderIds(Order.Status status, LocalDateTime now, Long afterId, Limit limit);

    /**
     * 자동 구매확정 대상을 좁힌다: 주문상품 상태가 status이고 deliveredBefore 이전(포함)에 배송 완료된 것.
     * afterId보다 큰 주문상품 ID만 오름차순으로 limit건(ID 커서). 확정해도 되는지 최종 판단은 주문이 한다.
     */
    List<AutoConfirmRow> findAutoConfirmTargets(
        OrderItem.Status status, LocalDateTime deliveredBefore, Long afterId, Limit limit);
}
