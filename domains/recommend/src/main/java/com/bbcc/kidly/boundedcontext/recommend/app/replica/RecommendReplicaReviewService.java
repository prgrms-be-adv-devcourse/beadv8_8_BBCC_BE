package com.bbcc.kidly.boundedcontext.recommend.app.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReview;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReviewRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReviewFit;
import com.bbcc.kidly.shared.community.event.ReviewChangedEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RecommendReplicaReviewService {

    private final RecommendReplicaReviewRepository replicaReviewRepository;

    /**
     * 리뷰 변경됨 이벤트의 값을 리뷰 복사본에 반영한다.
     * 처음 받은 리뷰면 복사본을 만들고, 이미 있으면 저장된 시각보다 늦게 일어난 이벤트일 때만 덮어쓴다.
     */
    public void applyReviewChanged(ReviewChangedEvent event) {
        RecommendReplicaReview.ChangeInfo info = toChangeInfo(event);
        Optional<RecommendReplicaReview> stored = replicaReviewRepository.findById(info.reviewId());
        if (stored.isEmpty()) {
            replicaReviewRepository.save(RecommendReplicaReview.create(info));
            return;
        }
        stored.get().applyIfNewer(info);
    }

    private RecommendReplicaReview.ChangeInfo toChangeInfo(ReviewChangedEvent event) {
        ReviewChangedEvent.Data data = event.data();
        return new RecommendReplicaReview.ChangeInfo(data.reviewId(), data.orderItemId(), data.productId(),
            fitOf(data.fit()), data.visible(), event.occurredAt());
    }

    // 계약에 없는 핏 값은 IllegalArgumentException이 되어, 리스너가 재시도 없이 DLT로 보낸다
    private ReviewFit fitOf(String fit) {
        if (fit == null) {
            return null;
        }
        return ReviewFit.valueOf(fit);
    }
}
