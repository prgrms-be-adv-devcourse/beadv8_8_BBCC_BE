package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import java.util.List;

/**
 * 리뷰 내용 검사. 검사기(금칙어 · AI 등)와 그 조합 · 제공자 응답 해석은 out/moderation 어댑터가 맡고, 결과는 커뮤니티 말로 돌려준다.
 * 예외를 던지지 않는다(장애 · 시간 초과 · 한도 초과는 UNDECIDED).
 */
public interface ContentModerator {

    Result moderate(Content content);

    /**
     * 검사할 내용.
     *
     * @param text 리뷰 본문
     * @param imageUrls 리뷰 사진 공개 주소(순서대로). 없으면 빈 목록
     */
    record Content(String text, List<String> imageUrls) {

        public Content {
            imageUrls = List.copyOf(imageUrls);
        }
    }

    enum Verdict {
        // 문제없음
        PASSED,
        // 걸림(이유 분류가 있다)
        FLAGGED,
        // 판단 못 함(원인이 있다). 다음 주기에 다시 검사한다
        UNDECIDED
    }

    enum FailureCause {
        // 장애(5xx · 연결 실패 · 인증 실패 · 이상한 응답)
        UNAVAILABLE,
        // 시간 초과
        TIMEOUT,
        // 한도 초과(429). 그 주기의 남은 검사를 멈춘다
        RATE_LIMITED
    }

    /**
     * @param reason FLAGGED일 때만 있다
     * @param cause UNDECIDED일 때만 있다
     */
    record Result(Verdict verdict, ModerationReason reason, FailureCause cause) {

        public static Result passed() {
            return new Result(Verdict.PASSED, null, null);
        }

        public static Result flagged(ModerationReason reason) {
            return new Result(Verdict.FLAGGED, reason, null);
        }

        public static Result undecided(FailureCause cause) {
            return new Result(Verdict.UNDECIDED, null, cause);
        }
    }
}
