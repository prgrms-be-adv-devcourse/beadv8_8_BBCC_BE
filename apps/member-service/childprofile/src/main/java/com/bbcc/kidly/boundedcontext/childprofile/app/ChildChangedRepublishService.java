package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildChangedRepublishService {

    private final ChildProfileRepository childProfileRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public int republishAll() {
        var children = childProfileRepository.findAll();
        for (var child : children) {
            eventPublisher.publishEvent(child.toChangedEvent(LocalDateTime.now()));
        }
        log.info("자녀 변경 이벤트 다시 발행: publishedCount={}", children.size());
        return children.size();
    }
}
