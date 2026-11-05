package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.MemberContact;
import com.bbcc.kidly.boundedcontext.notification.domain.MemberContactRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 도메인 포트와 JpaRepository가 같은 메서드를 가져 이 타입으로 부를 때 모호하지 않게 다시 선언한다
public interface JpaMemberContactRepository extends JpaRepository<MemberContact, Long>, MemberContactRepository {

    @Override
    Optional<MemberContact> findById(Long memberId);

    @Override
    boolean existsById(Long memberId);

    @Override
    void deleteById(Long memberId);
}
