package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.domain.MemberContact;
import com.bbcc.kidly.boundedcontext.notification.domain.MemberContactRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 이메일 복사본을 저장 · 삭제 · 조회한다. 같은 가입 이벤트를 다시 받아도 한 건만 둔다.
 * 로그에는 이메일을 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberContactService {

    private final MemberContactRepository memberContactRepository;

    // 이메일 없이 가입한 회원은 저장하지 않는다. 이미 있으면 그대로 둔다(이메일은 바뀌지 않는다)
    @Transactional
    public void save(Long memberId, String email) {
        if (email == null || email.isBlank() || memberContactRepository.existsById(memberId)) {
            return;
        }
        memberContactRepository.save(MemberContact.of(memberId, email));
    }

    @Transactional
    public void delete(Long memberId) {
        memberContactRepository.deleteById(memberId);
    }

    /**
     * 메일을 보낼 회원의 이메일. 없으면 비어 있는 값을 돌려주고 회원 ID만 INFO로 남긴다(호출한 쪽은 메일을 만들지 않는다).
     */
    @Transactional(readOnly = true)
    public Optional<String> findEmail(Long memberId) {
        var email = memberContactRepository.findById(memberId).map(MemberContact::getEmail);
        if (email.isEmpty()) {
            log.info("회원 이메일 복사본이 없어 메일을 만들지 않습니다. memberId={}", memberId);
        }
        return email;
    }
}
