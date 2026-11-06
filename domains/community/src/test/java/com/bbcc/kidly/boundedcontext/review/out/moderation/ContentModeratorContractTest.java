package com.bbcc.kidly.boundedcontext.review.out.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Verdict;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 모든 검사 어댑터가 지키는 계약: 통과 · 걸림(이유 분류) · 장애 · 시간 초과 · 한도 초과를 커뮤니티 결과로 돌려주고 예외를 던지지 않는다.
 * 어댑터마다 이 클래스를 상속해 상황을 만든다. 그 어댑터에 생길 수 없는 상황(금칙어의 외부 장애 등)은 null로 건너뛴다.
 */
abstract class ContentModeratorContractTest {

    enum Scenario { CLEAN, VIOLATING, FAILURE, TIMEOUT, RATE_LIMIT }

    protected static final Content CLEAN = new Content("아이가 잘 입어요. 사이즈도 딱 맞아요",
        List.of("https://img.example/reviews/1/a.jpg"));

    /**
     * @return 그 상황을 만든 어댑터. 생길 수 없는 상황이면 null
     */
    protected abstract ModerationAdapter adapterFor(Scenario scenario);

    /**
     * 걸리는 내용(금칙어 어댑터는 금칙어가 든 글, 외부 검사기는 아무 글이나 상황이 정한다).
     */
    protected Content violating() {
        return CLEAN;
    }

    @Test
    @DisplayName("계약: 문제없는 내용은 PASSED")
    void passes() {
        assertThat(moderate(Scenario.CLEAN, CLEAN)).isEqualTo(Result.passed());
    }

    @Test
    @DisplayName("계약: 걸리는 내용은 FLAGGED + 이유 분류")
    void flags() {
        Result result = moderate(Scenario.VIOLATING, violating());

        assertThat(result.verdict()).isEqualTo(Verdict.FLAGGED);
        assertThat(result.reason()).isNotNull();
    }

    @Test
    @DisplayName("계약: 장애는 예외 없이 UNDECIDED(UNAVAILABLE)")
    void failure() {
        assertThat(moderate(Scenario.FAILURE, CLEAN)).isEqualTo(Result.undecided(FailureCause.UNAVAILABLE));
    }

    @Test
    @DisplayName("계약: 시간 초과는 예외 없이 UNDECIDED(TIMEOUT)")
    void timeout() {
        assertThat(moderate(Scenario.TIMEOUT, CLEAN)).isEqualTo(Result.undecided(FailureCause.TIMEOUT));
    }

    @Test
    @DisplayName("계약: 한도 초과(429)는 예외 없이 UNDECIDED(RATE_LIMITED)")
    void rateLimited() {
        assertThat(moderate(Scenario.RATE_LIMIT, CLEAN)).isEqualTo(Result.undecided(FailureCause.RATE_LIMITED));
    }

    private Result moderate(Scenario scenario, Content content) {
        ModerationAdapter adapter = adapterFor(scenario);
        assumeThat(adapter).as("이 어댑터에는 생길 수 없는 상황").isNotNull();
        return adapter.moderate(content);
    }
}
