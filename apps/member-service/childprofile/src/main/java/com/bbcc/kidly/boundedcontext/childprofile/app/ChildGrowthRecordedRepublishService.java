package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildGrowthRecordedRepublishService {

    private final ChildProfileRepository childProfileRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public int republishAll() {
        int publishedCount = 0;
        for (var child : childProfileRepository.findAllWithGrowthRecords()) {
            for (var record : child.getGrowthRecords()) {
                eventPublisher.publishEvent(record.toRecordedEvent());
                publishedCount++;
            }
        }
        log.info("자녀 성장 기록 이벤트 다시 발행: publishedCount={}", publishedCount);
        return publishedCount;
    }
}
