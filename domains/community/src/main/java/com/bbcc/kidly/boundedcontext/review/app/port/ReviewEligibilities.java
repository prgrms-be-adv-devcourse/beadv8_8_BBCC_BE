package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ReviewEligibility;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;

/**
 * 리뷰 작성 자격 저장소. 구현은 out/persistence(Spring Data가 만든다).
 */
public interface ReviewEligibilities {

    boolean existsByOrderItemId(Long orderItemId);

    /**
     * 그 회원의 자격(리뷰를 쓸 수 있는 주문상품). 남의 주문상품이면 비어 있다.
     */
    Optional<ReviewEligibility> findByOrderItemIdAndMemberId(Long orderItemId, Long memberId);

    /**
     * 바로 DB에 반영해 유일 제약 위반을 이 호출에서 드러낸다.
     */
    ReviewEligibility saveAndFlush(ReviewEligibility eligibility);

    /**
     * 내 자격 첫 쪽: 구매확정 최신순(같은 시각이면 id 큰 것 먼저). 이미 리뷰를 쓴 자격은 뺀다.
     * 상품 이름 · 썸네일은 상품 복사본에서, 없으면 null.
     */
    List<EligibleRow> findEligible(Long memberId, Limit limit);

    /**
     * 내 자격 다음 쪽: (구매확정 시각, id)가 커서보다 뒤인 것.
     */
    List<EligibleRow> findEligibleAfter(Long memberId, LocalDateTime confirmedAt, Long id, Limit limit);

    /**
     * 목록 한 줄(자격 + 상품 복사본).
     */
    interface EligibleRow {

        Long getId();

        Long getOrderItemId();

        Long getOrderId();

        Long getProductId();

        String getProductName();

        String getThumbnailUrl();

        String getSize();

        LocalDateTime getConfirmedAt();
    }
}
