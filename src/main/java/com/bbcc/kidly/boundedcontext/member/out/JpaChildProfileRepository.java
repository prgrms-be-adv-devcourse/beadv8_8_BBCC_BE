package com.bbcc.kidly.boundedcontext.member.out;

import com.bbcc.kidly.boundedcontext.member.domain.ChildProfile;
import com.bbcc.kidly.boundedcontext.member.domain.ChildProfileRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaChildProfileRepository extends JpaRepository<ChildProfile, Long>, ChildProfileRepository {

    @Override
    @EntityGraph(attributePaths = "growthRecords")
    Optional<ChildProfile> findByIdAndMemberId(Long id, Long memberId);

    @Override
    @EntityGraph(attributePaths = "growthRecords")
    @Query("SELECT c FROM ChildProfile c WHERE c.memberId = :memberId ORDER BY c.id")
    List<ChildProfile> findAllByMemberId(@Param("memberId") Long memberId);
}
