package com.bbcc.kidly.boundedcontext.recommend.app.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RecommendReplicaChildService {

    private final RecommendReplicaChildRepository replicaChildRepository;

    /**
     * 자녀 정보 변경됨 이벤트의 값을 자녀 복사본에 반영한다.
     * 처음 받은 자녀면 복사본을 만들고, 이미 있으면 저장된 시각보다 늦게 일어난 이벤트일 때만 덮어쓴다.
     */
    public void applyChildChanged(ChildChangedEvent event) {
        RecommendReplicaChild.ChangeInfo info = toChangeInfo(event);
        Optional<RecommendReplicaChild> stored = replicaChildRepository.findById(info.childProfileId());
        if (stored.isEmpty()) {
            replicaChildRepository.save(RecommendReplicaChild.create(info));
            return;
        }
        stored.get().applyIfNewer(info);
    }

    // 복사본 칸은 원래 표의 칸 이름(child_profile_id)을 따르고 이벤트는 계약 이름(childId)을 쓰므로 여기서 이름을 옮긴다
    private RecommendReplicaChild.ChangeInfo toChangeInfo(ChildChangedEvent event) {
        ChildChangedEvent.Data data = event.data();
        return new RecommendReplicaChild.ChangeInfo(
            data.childId(),
            data.memberId(),
            data.heightCm(),
            data.weightKg(),
            data.gender(),
            data.birthDate(),
            data.bodyType(),
            data.preferredFit(),
            data.deleted(),
            event.occurredAt());
    }
}
