package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.Review;
import java.util.Optional;

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
}
