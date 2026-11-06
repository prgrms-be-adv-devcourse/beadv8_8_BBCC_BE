package com.bbcc.kidly.boundedcontext.member.out;

import com.bbcc.kidly.boundedcontext.member.domain.MemberDeadLetter;
import com.bbcc.kidly.boundedcontext.member.domain.MemberDeadLetterRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaMemberDeadLetterRepository
    extends JpaRepository<MemberDeadLetter, Long>, MemberDeadLetterRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from MemberDeadLetter d where d.id = :id")
    Optional<MemberDeadLetter> findByIdForUpdate(@Param("id") Long id);

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default MemberDeadLetter getById(Long id) {
        return MemberDeadLetterRepository.super.getById(id);
    }
}
