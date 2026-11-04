package com.bbcc.kidly.boundedcontext.auth.domain;

import java.time.LocalDateTime;

public interface ChildProfileReplicaRepository {

    void insertIfAbsent(Long id, Long guardianId, LocalDateTime now);

    void deleteById(Long id);
}
