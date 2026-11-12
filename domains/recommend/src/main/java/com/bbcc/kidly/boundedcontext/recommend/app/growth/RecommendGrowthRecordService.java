package com.bbcc.kidly.boundedcontext.recommend.app.growth;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.shared.childprofile.event.ChildGrowthRecordedEvent;
import com.bbcc.kidly.shared.childprofile.event.ChildGrowthRecordDeletedEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RecommendGrowthRecordService {

    private final RecommendGrowthRecordRepository growthRecordRepository;

    private final RecommendReplicaChildRepository replicaChildRepository;

    public void deleteGrowthRecord(ChildGrowthRecordDeletedEvent event) {
        growthRecordRepository.deleteByGrowthRecordId(event.data().growthRecordId());
    }

    /**
     * 성장 기록 이벤트의 값을 성장 기록으로 저장한다.
     * 같은 기록 ID가 이미 있으면 다시 받은 이벤트로 보고 조용히 건너뛴다. 한 줄만 남는 것은 PK가 지킨다.
     * 자녀 복사본이 이미 삭제 표시된 자녀의 기록도 건너뛴다. 삭제된 자녀의 키 이력을 남기지 않기 위해서다.
     * 자녀 복사본이 아직 없으면 그대로 저장한다.
     */
    public void recordGrowth(ChildGrowthRecordedEvent event) {
        RecommendGrowthRecord record = RecommendGrowthRecord.create(toRecordInfo(event));
        if (growthRecordRepository.existsById(record.getGrowthRecordId())) {
            return;
        }
        if (isDeletedChild(record.getChildProfileId())) {
            return;
        }
        growthRecordRepository.save(record);
    }

    private boolean isDeletedChild(Long childProfileId) {
        Optional<RecommendReplicaChild> child = replicaChildRepository.findById(childProfileId);
        return child.isPresent() && child.get().isDeleted();
    }

    // 성장 기록의 칸은 원래 표의 칸 이름(child_profile_id)을 따르고
    // 이벤트는 계약 이름(childId)을 쓰므로 여기서 이름을 옮긴다
    private RecommendGrowthRecord.RecordInfo toRecordInfo(ChildGrowthRecordedEvent event) {
        ChildGrowthRecordedEvent.Data data = event.data();
        return new RecommendGrowthRecord.RecordInfo(
            data.growthRecordId(),
            data.childId(),
            data.heightCm(),
            data.bodyType(),
            data.recordedAt());
    }
}
