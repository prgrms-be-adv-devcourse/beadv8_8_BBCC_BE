package com.bbcc.kidly.boundedcontext.review.out.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChainedContentModeratorTest {

    private static final Content CONTENT = new Content("글", List.of());
    private final List<String> called = new ArrayList<>();

    @Test
    @DisplayName("설정 순서대로 부르고, 앞에서 걸리면 뒤는 부르지 않는다")
    void stopsAtFirstFlag() {
        ChainedContentModerator chain = new ChainedContentModerator(List.of(
            adapter("words", Result.flagged(ModerationReason.PROFANITY)), adapter("ai", Result.passed())));

        assertThat(chain.moderate(CONTENT)).isEqualTo(Result.flagged(ModerationReason.PROFANITY));
        assertThat(called).containsExactly("words");
    }

    @Test
    @DisplayName("모두 통과면 PASSED, 뒤에서 걸리면 FLAGGED")
    void passesOrFlagsLater() {
        assertThat(new ChainedContentModerator(List.of(adapter("a", Result.passed()), adapter("b", Result.passed())))
            .moderate(CONTENT)).isEqualTo(Result.passed());
        assertThat(new ChainedContentModerator(List.of(adapter("a", Result.passed()),
            adapter("b", Result.flagged(ModerationReason.HATE)))).moderate(CONTENT))
            .isEqualTo(Result.flagged(ModerationReason.HATE));
    }

    @Test
    @DisplayName("걸린 것 없이 판단 못 한 검사기가 있으면 UNDECIDED(첫 원인), 뒤에서 걸리면 FLAGGED가 이긴다")
    void undecidedMerge() {
        assertThat(new ChainedContentModerator(List.of(adapter("a", Result.passed()),
            adapter("b", Result.undecided(FailureCause.RATE_LIMITED)))).moderate(CONTENT))
            .isEqualTo(Result.undecided(FailureCause.RATE_LIMITED));
        assertThat(new ChainedContentModerator(List.of(adapter("a", Result.undecided(FailureCause.TIMEOUT)),
            adapter("b", Result.flagged(ModerationReason.SEXUAL)))).moderate(CONTENT))
            .isEqualTo(Result.flagged(ModerationReason.SEXUAL));
    }

    private ModerationAdapter adapter(String name, Result result) {
        return new ModerationAdapter() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Result moderate(Content content) {
                called.add(name);
                return result;
            }
        };
    }
}
