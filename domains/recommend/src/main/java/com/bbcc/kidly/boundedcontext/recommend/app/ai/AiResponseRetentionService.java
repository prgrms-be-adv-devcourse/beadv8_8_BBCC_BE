package com.bbcc.kidly.boundedcontext.recommend.app.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 보관 기간이 지난 AI 응답을 지운다. 응답 재사용 기간보다 오래 남겨 두어도 쓸 곳이 없어 저장 공간만 차지하기 때문이다.
 */
@Slf4j
@Service
public class AiResponseRetentionService {

    private final AiResponseStore store;
    private final Clock clock;
    private final Duration retention;

    @Autowired
    public AiResponseRetentionService(
        AiResponseStore store,
        @Value("${kidly.recommend.ai.response-retention:P30D}") Duration retention
    ) {
        this(store, Clock.system(ZoneId.of("Asia/Seoul")), retention);
    }

    public AiResponseRetentionService(AiResponseStore store, Clock clock, Duration retention) {
        this.store = store;
        this.clock = clock;
        this.retention = retention;
    }

    public void deleteExpiredResponses() {
        int deleted = store.deleteCreatedBefore(LocalDateTime.now(clock).minus(retention));
        log.info("보관 기간이 지난 AI 응답을 지웠습니다. deleted={}", deleted);
    }
}
