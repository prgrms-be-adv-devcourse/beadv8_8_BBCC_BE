package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.DisplayStatus;
import com.bbcc.kidly.boundedcontext.review.domain.Fit;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
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

    /**
     * 잠그지 않고 읽는다(수정 전 미리 확인용). 반영은 findForUpdate로 다시 읽어 한다.
     */
    Optional<Review> findById(Long reviewId);

    void delete(Review review);

    /**
     * 상품 리뷰 첫 쪽: 공개 리뷰만 최신순(created_at desc, id desc). 상품 정보는 상품 복사본에서, 없으면 null.
     */
    List<ReviewListRow> findByProduct(Long productId, Limit limit);

    /**
     * 상품 리뷰 다음 쪽: (작성 시각, id)가 커서보다 뒤인 것.
     */
    List<ReviewListRow> findByProductAfter(Long productId, LocalDateTime createdAt, Long id, Limit limit);

    /**
     * 내 리뷰 첫 쪽: 숨김 리뷰도 포함해 최신순. 상품 정보는 항목마다 상품 복사본에서.
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

        // 작성자가 탈퇴했으면 그 시각, 아니면 null
        LocalDateTime getAuthorWithdrawnAt();

        // 사이즈 핏. 남기지 않았으면 null
        Fit getFit();

        // 작성 자격에 저장된 구매 사이즈. 없으면 null
        String getPurchasedSize();

        // 검사 상태. 공개 목록에는 공개 상태만 나온다
        ModerationStatus getModerationStatus();

        LocalDateTime getCreatedAt();

        LocalDateTime getUpdatedAt();
    }
}
