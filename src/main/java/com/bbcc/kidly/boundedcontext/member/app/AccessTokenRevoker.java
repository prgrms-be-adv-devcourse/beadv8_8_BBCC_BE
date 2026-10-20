package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationCutoff;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 회원이 지금까지 받은 Access Token을 무효화한다. 탈퇴 · 정지 · 권한 회수 · Refresh 재사용 감지 트랜잭션 안에서 부른다.
 * DB에는 바로 쓰고, 메모리에는 커밋된 뒤에만 넣는다. 롤백된 정지가 멀쩡한 회원을 401로 만들지 않게 하기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessTokenRevoker {

    private final AccessTokenRevocationRepository repository;
    private final AccessTokenRevocationCache cache;

    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(Long memberId, LocalDateTime now) {
        repository.upsert(memberId, now, now);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.put(memberId, AccessTokenRevocationCutoff.of(now));
                log.info("Access Token 무효화: memberId={}", memberId);
            }
        });
    }
}
