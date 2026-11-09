package com.bbcc.kidly.boundedcontext.wish.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishApprovers.Principal;
import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ChildWishActor;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ParentWishActor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WishPolicyTest {

    private final WishPolicy policy = new WishPolicy();
    private final ChildWishActor child = new ChildWishActor(1L, 7L);
    private final ParentWishActor guardian = new ParentWishActor(7L);

    @Test
    @DisplayName("보내기는 로그인한 자녀만(#999)")
    void sendByChildOnly() {
        assertThat(policy.canSend(child)).isTrue();
        assertThat(policy.canSend(guardian)).isFalse();
    }

    @Test
    @DisplayName("거절은 그 조르기의 승인자만, 철회는 보낸 사람만")
    void rejectByApproverAndCancelByRequester() {
        WishRequest request = WishRequest.send(PrincipalType.CHILD, 1L, 1L, null, policy.maxMessageLength());
        List<Principal> approvers = List.of(new Principal(PrincipalType.MEMBER, 7L));

        assertThat(policy.canReject(guardian, approvers)).isTrue();
        assertThat(policy.canReject(new ParentWishActor(8L), approvers)).isFalse();
        assertThat(policy.canReject(child, approvers)).isFalse();
        assertThat(policy.canCancel(child, request)).isTrue();
        assertThat(policy.canCancel(guardian, request)).isFalse();
        assertThat(policy.canCancel(new ChildWishActor(2L, 7L), request)).isFalse();
    }

    @Test
    @DisplayName("메시지는 100자, 거절 사유는 화면 순서대로 넷")
    void values() {
        assertThat(policy.maxMessageLength()).isEqualTo(100);
        assertThat(policy.rejectReasons()).extracting(Enum::name)
            .containsExactly("TOO_EXPENSIVE", "ALREADY_HAVE", "NEXT_TIME", "OTHER");
    }
}
