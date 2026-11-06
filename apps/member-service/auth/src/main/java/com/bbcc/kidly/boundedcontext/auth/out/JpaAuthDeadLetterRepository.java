package com.bbcc.kidly.boundedcontext.auth.out;

import com.bbcc.kidly.boundedcontext.auth.domain.AuthDeadLetter;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthDeadLetterRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAuthDeadLetterRepository
    extends JpaRepository<AuthDeadLetter, Long>, AuthDeadLetterRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from AuthDeadLetter d where d.id = :id")
    Optional<AuthDeadLetter> findByIdForUpdate(@Param("id") Long id);

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default AuthDeadLetter getById(Long id) {
        return AuthDeadLetterRepository.super.getById(id);
    }
}
