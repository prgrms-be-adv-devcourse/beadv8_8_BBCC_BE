package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileDeadLetterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildProfileDeadLetterService {

    private final ChildProfileDeadLetterRepository repository;

    // DLT는 파티션 1개를 컨테이너 하나가 차례로 읽어 같은 위치를 동시에 저장하지 않는다. 겹치면 유니크 제약이 막는다
    @Transactional
    public boolean receive(ChildProfileDeadLetterMessage message) {
        if (repository.existsByDltTopicAndDltPartitionAndDltOffset(
            message.dltTopic(), message.dltPartition(), message.dltOffset())) {
            return false;
        }
        repository.save(message.toDeadLetter());
        return true;
    }
}
