package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.DisplayStatus;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;

/**
 * 리뷰 저장소. 구현은 out/persistence(Spring Data가 만든다).
 */
public interface Reviews {

    boolean existsByOrderItemId(Long orderItemId);

    /**
     * 바로 DB에 반영한다. 새 리뷰면 주문상품 유일 제약 위반을 이 호출에서 드러내고, 수정이면 수정 시각을 채운다.
     */
    Review saveAndFlush(Review review);

    /**
     * 수정 · 삭제할 리뷰를 행 잠금(select … for update)으로 읽는다. 같은 리뷰의 수정 · 삭제가 겹쳐도 집계가 한 번씩만 바뀐다.
     */
    Optional<Review> findForUpdate(Long reviewId);

    void delete(Review review);

    /**
     * 상품 리뷰 첫 쪽: 최신순(created_at desc, id desc). 상품 정보는 상품 복사본에서, 없으면 null.
     */
    List<ReviewListRow> findByProduct(Long productId, Limit limit);

    /**
     * 상품 리뷰 다음 쪽: (작성 시각, id)가 커서보다 뒤인 것.
     */
    List<ReviewListRow> findByProductAfter(Long productId, LocalDateTime createdAt, Long id, Limit limit);

    /**
     * 내 리뷰 첫 쪽: 최신순. 상품 정보는 항목마다 상품 복사본에서.
     */
    List<ReviewListRow> findByMember(Long memberId, Limit limit);

    List<ReviewListRow> findByMemberAfter(Long memberId, LocalDateTime createdAt, Long id, Limit limit);

    /**
     * 목록 한 줄(리뷰 + 상품 복사본).
     */
    interface ReviewListRow {

        Long getId();

        Long getOrderItemId();

        Long getProductId();

        String getProductName();

        String getThumbnailUrl();

        DisplayStatus getDisplayStatus();

        // 표 컬럼이 smallint라 숫자 타입으로 받는다
        Number getRating();

        String getContent();

        String getNickname();

        LocalDateTime getCreatedAt();

        LocalDateTime getUpdatedAt();
    }
}
