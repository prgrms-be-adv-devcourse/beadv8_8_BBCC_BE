package com.bbcc.kidly.boundedcontext.notification.domain;

import java.util.Optional;

public interface ChildAccountCopyRepository {

    ChildAccountCopy save(ChildAccountCopy childAccountCopy);

    Optional<ChildAccountCopy> findById(Long accountId);

    /**
     * 프로필의 삭제되지 않은 계정. 계정을 다시 만든 직후 옛 계정의 삭제가 아직 안 왔으면 둘일 수 있어,
     * 나중에 만든(ID가 큰) 계정을 준다.
     */
    Optional<ChildAccountCopy> findFirstByChildProfileIdAndDeletedFalseOrderByAccountIdDesc(Long childProfileId);
}
