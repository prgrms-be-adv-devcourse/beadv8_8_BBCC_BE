package com.bbcc.kidly.boundedcontext.review.out.moderation;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import java.util.List;

/**
 * 금칙어 검사기는 외부를 부르지 않아 장애 · 시간 초과 · 한도 초과가 생기지 않는다(그 계약은 건너뛴다).
 */
class BannedWordsModeratorContractTest extends ContentModeratorContractTest {

    @Override
    protected ModerationAdapter adapterFor(Scenario scenario) {
        return switch (scenario) {
            case CLEAN, VIOLATING -> new BannedWordsModerator(List.of("나쁜말"));
            case FAILURE, TIMEOUT, RATE_LIMIT -> null;
        };
    }

    @Override
    protected Content violating() {
        return new Content("이건 나쁜말 이에요", List.of());
    }
}
