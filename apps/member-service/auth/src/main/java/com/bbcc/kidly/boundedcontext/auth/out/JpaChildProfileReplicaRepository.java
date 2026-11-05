package com.bbcc.kidly.boundedcontext.auth.out;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplica;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JpaChildProfileReplicaRepository extends JpaRepository<ChildProfileReplica, Long>,
    ChildProfileReplicaRepository {

    @Override
    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO auth.child_profile_replica (id, guardian_id, created_at, updated_at)
        VALUES (:id, :guardianId, :now, :now) ON CONFLICT (id) DO NOTHING
        """, nativeQuery = true)
    void insertIfAbsent(@Param("id") Long id, @Param("guardianId") Long guardianId, @Param("now") LocalDateTime now);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChildProfileReplica r where r.id = :id")
    Optional<ChildProfileReplica> findByIdForUpdate(@Param("id") Long id);
}
