package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.domain.RefreshTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 Refresh Token을 하루 한 번 지운다. 재발급할 때도 만료를 확인하므로 정리가 늦어도 동작은 같다.
 * 서버가 여러 대면 모두 실행되지만 같은 DELETE라 결과가 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleaner {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void deleteExpired() {
        var deleted = refreshTokenRepository.deleteExpired(LocalDateTime.now());
        log.info("만료된 Refresh Token {}개를 지웠습니다", deleted);
    }
}
