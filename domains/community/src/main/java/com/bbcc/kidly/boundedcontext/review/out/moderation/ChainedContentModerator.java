package com.bbcc.kidly.boundedcontext.review.out.moderation;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator;
import java.util.List;

/**
 * 설정 순서대로 검사기를 부른다: 하나라도 걸리면 FLAGGED(그 뒤는 부르지 않는다), 걸린 것 없이 판단 못 한 검사기가 있으면 UNDECIDED
 * (첫 원인), 모두 통과면 PASSED.
 */
public class ChainedContentModerator implements ContentModerator {

    private final List<ModerationAdapter> chain;

    public ChainedContentModerator(List<ModerationAdapter> chain) {
        this.chain = List.copyOf(chain);
    }

    public List<String> names() {
        return chain.stream().map(ModerationAdapter::name).toList();
    }

    @Override
    public Result moderate(Content content) {
        Result undecided = null;
        for (ModerationAdapter adapter : chain) {
            Result result = adapter.moderate(content);
            if (result.verdict() == Verdict.FLAGGED) {
                return result;
            }
            if (result.verdict() == Verdict.UNDECIDED && undecided == null) {
                undecided = result;
            }
        }
        return undecided == null ? Result.passed() : undecided;
    }
}
