package com.bbcc.kidly.boundedcontext.favorite.app.port.in;

import java.time.LocalDateTime;

/**
 * 자녀 계정이 지워졌을 때 그 계정의 찜을 모두 지우고 지운 만큼 상품별 찜 수를 줄인다. 그 계정을 지운 자녀 계정으로 남겨
 * 그 뒤 찜 요청을 막는다. 부르는 쪽 트랜잭션에 합류한다. 같은 계정의 삭제가 다시 와도 1회분이다(지울 찜이 없으면 아무것도 바꾸지 않는다).
 */
public interface ChildFavoritesRemover {

    /**
     * @return 지운 찜 수
     */
    int removeAll(Long accountId, Long guardianId, LocalDateTime deletedAt);
}
