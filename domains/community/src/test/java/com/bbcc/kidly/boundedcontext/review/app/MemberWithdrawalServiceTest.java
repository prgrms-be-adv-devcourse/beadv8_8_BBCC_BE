package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.MemberFavorites;
import com.bbcc.kidly.boundedcontext.review.app.port.WithdrawnAuthors;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberWithdrawalServiceTest {

    private static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_123);

    private final List<String> calls = new ArrayList<>();
    private final MemberWithdrawalService service = new MemberWithdrawalService(
        (MemberFavorites) memberId -> {
            calls.add("favorites:" + memberId);
            return 3;
        },
        (WithdrawnAuthors) (memberId, withdrawnAt) -> {
            calls.add("reviews:" + memberId + "@" + withdrawnAt);
            return 2;
        });

    @Test
    @DisplayName("탈퇴: 그 회원의 찜을 모두 지우고(찜 수 차감은 찜 쪽), 리뷰는 남기며 작성자 탈퇴를 표시한다(탈퇴 시각은 마이크로초)")
    void record() {
        service.record(7L, WITHDRAWN_AT);

        assertThat(calls).containsExactly("favorites:7", "reviews:7@2026-11-02T10:00:00.123456");
    }

    @Test
    @DisplayName("탈퇴 표시 값: 탈퇴한 작성자는 닉네임 '탈퇴한 회원', 아니면 저장된 닉네임 그대로")
    void authorDisplay() {
        assertThat(AuthorDisplay.nickname("닉", true)).isEqualTo("탈퇴한 회원");
        assertThat(AuthorDisplay.nickname(null, true)).isEqualTo("탈퇴한 회원");
        assertThat(AuthorDisplay.nickname("닉", false)).isEqualTo("닉");
        assertThat(AuthorDisplay.nickname(null, false)).isNull();
    }
}
