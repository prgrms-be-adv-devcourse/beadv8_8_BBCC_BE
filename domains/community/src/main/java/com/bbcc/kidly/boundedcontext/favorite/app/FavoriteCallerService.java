package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.boundedcontext.favorite.app.port.DeletedChildAccounts;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜 API를 부른 소유자가 찜을 쓸 수 있는지 본다. 지운 자녀 계정(삭제 이벤트를 받은 계정)이면 403 FAVORITE_CHILD_ACCOUNT_DELETED.
 */
@Service
@RequiredArgsConstructor
public class FavoriteCallerService {

    private final DeletedChildAccounts deletedChildAccounts;

    @Transactional(readOnly = true)
    public FavoriteActor checkActive(FavoriteActor actor) {
        if (actor.child() && deletedChildAccounts.existsById(actor.id())) {
            throw new BusinessException(FavoriteErrorCode.FAVORITE_CHILD_ACCOUNT_DELETED);
        }
        return actor;
    }
}
