package com.bbcc.kidly.boundedcontext.childprofile.out;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileDeadLetter;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileDeadLetterRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaChildProfileDeadLetterRepository
    extends JpaRepository<ChildProfileDeadLetter, Long>, ChildProfileDeadLetterRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ChildProfileDeadLetter d where d.id = :id")
    Optional<ChildProfileDeadLetter> findByIdForUpdate(@Param("id") Long id);

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default ChildProfileDeadLetter getById(Long id) {
        return ChildProfileDeadLetterRepository.super.getById(id);
    }
}
