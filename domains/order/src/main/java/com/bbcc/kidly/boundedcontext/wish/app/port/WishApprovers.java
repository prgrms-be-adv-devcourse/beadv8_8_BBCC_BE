package com.bbcc.kidly.boundedcontext.wish.app.port;

import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import java.util.List;

/**
 * 구매 요청의 승인자(#999). 저장하지 않고 그때그때 묻는다(사람을 고정하지 않음). 승인자는 예약 칸으로 주문할 수 있고(결제 허용),
 * 거절할 수 있다. 조르기를 볼 수 있는 사람은 보낸 사람 + 승인자다.
 * 지금 구현(out): 자녀가 보낸 것 → 그 자녀 프로필의 보호자 회원 1명(주문이 받아 둔 자녀 복사본, #906). 삭제된 자녀 · 회원이 보낸 것은 없음.
 */
public interface WishApprovers {

    /**
     * 이 보낸 사람의 승인자. 없으면 빈 목록.
     */
    List<Principal> approversOf(Principal requester);

    /**
     * 이 사람이 승인자인 보낸 사람들(승인자 목록 보기). 없으면 빈 목록.
     */
    List<Principal> requestersApprovedBy(Principal approver);

    /**
     * @param id 회원이면 회원 ID, 자녀면 자녀 프로필 ID
     */
    record Principal(PrincipalType type, Long id) {
    }
}
