package com.bbcc.kidly.boundedcontext.member.in.scheduler;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 유효 기간이 지난 Access Token 무효화 기록을 10분마다 지운다.
 * 시작할 때 캐시가 유효한 기록만 읽으므로 첫 정리는 10분 뒤에 한다.
 */
@Component
@RequiredArgsConstructor
public class AccessTokenRevocationScheduler {

    private final AccessTokenRevocationService accessTokenRevocationService;

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT10M")
    public void deleteExpired() {
        accessTokenRevocationService.deleteExpired();
    }
}
