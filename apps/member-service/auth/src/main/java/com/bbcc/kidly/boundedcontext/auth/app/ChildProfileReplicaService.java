package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildProfileReplicaService {

    private final ChildProfileReplicaRepository repository;

    @Transactional
    public void apply(Long childId, Long guardianId, boolean deleted) {
        if (deleted) {
            repository.deleteById(childId);
        } else {
            repository.insertIfAbsent(childId, guardianId, LocalDateTime.now());
        }
    }
}
