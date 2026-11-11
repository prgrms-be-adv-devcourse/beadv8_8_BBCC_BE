package com.bbcc.kidly.boundedcontext.recommend.app.growth;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import com.bbcc.kidly.shared.childprofile.event.ChildGrowthRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class GrowthRecordService {

    private final RecommendGrowthRecordRepository growthRecordRepository;

    /**
     * 성장 기록 이벤트의 값을 성장 기록으로 저장한다.
     * 같은 기록 ID가 이미 있으면 다시 받은 이벤트로 보고 조용히 건너뛴다.
     */
    public void recordGrowth(ChildGrowthRecordedEvent event) {
        RecommendGrowthRecord.RecordInfo info = toRecordInfo(event);
        if (growthRecordRepository.existsById(info.growthRecordId())) {
            return;
        }
        growthRecordRepository.save(RecommendGrowthRecord.create(info));
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
