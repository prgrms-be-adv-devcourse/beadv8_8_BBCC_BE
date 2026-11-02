package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewEligibilities;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewEligibility;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 구매확정 → 작성 자격. 먼저 조회해 이미 있으면 끝낸다(중복 수신은 DB 제약까지 가지 않는다).
 * 조회 뒤 저장 사이에 같은 주문상품이 들어오면(동시 수신) 주문상품 유일 제약(23505, uk_review_eligibility_order_item_id)에
 * 막히는데, 그 위반이고 다시 조회해 자격이 있을 때만 "이미 있음"으로 끝낸다(예외 · 재시도 · DLT 없음, 로그 없음).
 * 그 밖의 위반은 그대로 던진다(재시도 → DLT).
 */
@Service
@RequiredArgsConstructor
public class ReviewEligibilityService implements ReviewEligibilityRecorder {

    // 주문상품당 1건을 지키는 유일 제약(마이그레이션 V202611021048)
    static final String ORDER_ITEM_UNIQUE = "uk_review_eligibility_order_item_id";

    private final ReviewEligibilities reviewEligibilities;
    private final ReviewSeparateTransaction separateTransaction;

    @Override
    public void record(PurchaseConfirmed confirmed) {
        if (reviewEligibilities.existsByOrderItemId(confirmed.orderItemId())) {
            return;
        }
        ReviewEligibility eligibility = ReviewEligibility.create(new ReviewEligibility.Values(
            confirmed.orderItemId(), confirmed.orderId(), confirmed.memberId(), confirmed.productId(),
            confirmed.optionId(), confirmed.size(), confirmed.confirmedAt()));
        try {
            separateTransaction.run(() -> reviewEligibilities.saveAndFlush(eligibility));
        } catch (DataIntegrityViolationException e) {
            if (!UniqueViolations.isViolationOf(e, ORDER_ITEM_UNIQUE)
                || !reviewEligibilities.existsByOrderItemId(confirmed.orderItemId())) {
                throw e;
            }
        }
    }
}
