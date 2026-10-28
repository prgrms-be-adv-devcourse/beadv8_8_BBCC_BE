package com.bbcc.kidly.boundedcontext.recommend.app.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RecommendReplicaSellerService {

    private final RecommendReplicaSellerRepository replicaSellerRepository;

    /**
     * 판매자 정보 변경됨 이벤트의 값을 판매자 복사본에 반영한다.
     * 처음 받은 판매자면 복사본을 만들고, 이미 있으면 저장된 시각보다 늦게 일어난 이벤트일 때만 덮어쓴다.
     */
    public void applySellerChanged(SellerChangedEvent event) {
        RecommendReplicaSeller.ChangeInfo info = toChangeInfo(event);
        Optional<RecommendReplicaSeller> stored = replicaSellerRepository.findById(info.sellerId());
        if (stored.isEmpty()) {
            replicaSellerRepository.save(RecommendReplicaSeller.create(info));
            return;
        }
        stored.get().applyIfNewer(info);
    }

    private RecommendReplicaSeller.ChangeInfo toChangeInfo(SellerChangedEvent event) {
        SellerChangedEvent.Data data = event.data();
        return new RecommendReplicaSeller.ChangeInfo(
            data.sellerId(), data.businessName(), data.status(), event.occurredAt());
    }
}
