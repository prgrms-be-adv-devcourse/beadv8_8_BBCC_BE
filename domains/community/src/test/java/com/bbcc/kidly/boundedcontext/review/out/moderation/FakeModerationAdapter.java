package com.bbcc.kidly.boundedcontext.review.out.moderation;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 테스트용 검사기(이름 fake). 정해 둔 결과를 차례로 돌려주고, 다 쓰면 마지막 결과(처음엔 PASSED)를 계속 돌려준다.
 * 커뮤니티 모듈 테스트 설정(application-test.yml)의 moderators에 들어 있다. 테스트마다 reset으로 시작한다.
 */
@Component
public class FakeModerationAdapter implements ModerationAdapter {

    private final Deque<Result> script = new ArrayDeque<>();
    private final List<Content> calls = new ArrayList<>();
    private Result last = Result.passed();
    // 검사 중에 일어난 일(예: 작성자 수정)을 흉내 낸다. 한 번 부르고 지운다
    private Runnable duringCall;

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public Result moderate(Content content) {
        Runnable hook;
        synchronized (this) {
            calls.add(content);
            hook = duringCall;
            duringCall = null;
        }
        if (hook != null) {
            hook.run();
        }
        return next();
    }

    private synchronized Result next() {
        if (!script.isEmpty()) {
            last = script.poll();
        }
        return last;
    }

    public synchronized void willReturn(Result... results) {
        script.clear();
        script.addAll(List.of(results));
    }

    public synchronized void duringNextCall(Runnable hook) {
        duringCall = hook;
    }

    public synchronized List<Content> calls() {
        return List.copyOf(calls);
    }

    public synchronized void reset() {
        script.clear();
        calls.clear();
        duringCall = null;
        last = Result.passed();
    }
}
