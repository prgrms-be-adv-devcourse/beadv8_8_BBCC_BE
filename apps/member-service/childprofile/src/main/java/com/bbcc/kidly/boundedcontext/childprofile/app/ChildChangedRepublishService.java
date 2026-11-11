package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfile;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 삭제되지 않은 자녀마다 지금 값으로 ChildChangedEvent를 다시 보낸다(#1055). 자녀는 바꾸지 않는다.
 * 구독 그룹이 생기기 전 이벤트를 놓친 쪽(인증의 자녀 복사본 등)이 따라잡게 한다.
 * 받는 쪽은 없으면 만들고 있으면 같은 값으로 덮거나(occurredAt이 늦으므로) 그대로 두므로 여러 번 불러도 결과가 같다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildChangedRepublishService {

    private final ChildProfileRepository childProfileRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 삭제된 자녀는 엔티티의 @SQLRestriction으로 조회에서 빠진다
    // Kafka로는 이 트랜잭션 커밋 직전 아웃박스에 저장된 뒤 릴레이가 보낸다(ChildProfileOutboxRecorder)
    @Transactional
    public int republishAll() {
        int publishedCount = 0;
        for (ChildProfile child : childProfileRepository.findAll()) {
            eventPublisher.publishEvent(new ChildChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
                new ChildChangedEvent.Data(child.getId(), child.getMemberId(), child.getHeightCm(),
                    child.getWeightKg(), child.getGender(), child.getBirthDate(), child.getBodyType(),
                    child.getPreferredFit(), false)));
            publishedCount++;
        }
        log.info("자녀 변경 이벤트 다시 발행: publishedCount={}", publishedCount);
        return publishedCount;
    }
}
