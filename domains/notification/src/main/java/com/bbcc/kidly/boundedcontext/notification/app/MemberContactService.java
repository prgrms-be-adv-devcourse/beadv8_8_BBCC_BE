package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.domain.MemberContact;
import com.bbcc.kidly.boundedcontext.notification.domain.MemberContactRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.WithdrawnMemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 가입 이벤트를 다시 받아도 한 건만 둔다.
 * 로그에는 이메일을 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberContactService {

    private final MemberContactRepository memberContactRepository;
    private final WithdrawnMemberRepository withdrawnMemberRepository;

    @Transactional
    public void save(Long memberId, String email) {
        if (email == null || email.isBlank() || memberContactRepository.existsById(memberId)
            || withdrawnMemberRepository.exists(memberId)) {
            return;
        }
        memberContactRepository.save(MemberContact.of(memberId, email));
    }

    @Transactional
    public void delete(Long memberId) {
        memberContactRepository.deleteById(memberId);
    }

    @Transactional(readOnly = true)
    public Optional<String> findEmail(Long memberId) {
        var email = memberContactRepository.findById(memberId).map(MemberContact::getEmail);
        if (email.isEmpty()) {
            log.info("회원 이메일 복사본이 없어 메일을 만들지 않습니다. memberId={}", memberId);
        }
        return email;
    }
}
