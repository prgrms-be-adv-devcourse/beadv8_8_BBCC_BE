package com.bbcc.kidly.boundedcontext.wish.out.actor;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishApprovers;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 지금의 승인자 규칙(#999): 자녀가 보낸 조르기 → 그 자녀 프로필의 보호자 회원 1명(주문이 받아 둔 자녀 복사본, #906).
 * 삭제된 자녀 · 복사본이 없는 자녀 · 회원이 보낸 것은 승인자가 없다. 저장하지 않으므로 보호자가 바뀌면 바로 따라간다.
 * 선물 요청 등 다른 종류가 생기면 여기서 규칙을 더한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class GuardianWishApprovers implements WishApprovers {

    private final WishCatalog wishCatalog;

    @Override
    public List<Principal> approversOf(Principal requester) {
        if (requester.type() != PrincipalType.CHILD) {
            return List.of();
        }
        return wishCatalog.findChild(requester.id())
            .filter(child -> !child.deleted())
            .map(child -> List.of(new Principal(PrincipalType.MEMBER, child.memberId())))
            .orElse(List.of());
    }

    @Override
    public List<Principal> requestersApprovedBy(Principal approver) {
        if (approver.type() != PrincipalType.MEMBER) {
            return List.of();
        }
        return wishCatalog.findChildrenOfMember(approver.id()).stream()
            .filter(child -> !child.deleted())
            .map(child -> new Principal(PrincipalType.CHILD, child.childId()))
            .toList();
    }
}
