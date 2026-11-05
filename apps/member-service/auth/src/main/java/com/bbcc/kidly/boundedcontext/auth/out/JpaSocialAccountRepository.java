package com.bbcc.kidly.boundedcontext.auth.out;

import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccountRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JpaSocialAccountRepository extends JpaRepository<SocialAccount, Long>, SocialAccountRepository {

    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE SocialAccount a
        SET a.memberId = :memberId
        WHERE a.id = :id AND (a.memberId IS NULL OR a.memberId = :memberId)
        """)
    void link(@Param("id") Long id, @Param("memberId") Long memberId);

    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SocialAccount a WHERE a.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
