package com.bbcc.kidly.boundedcontext.recommend.app.replica;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.dto.ChildCaller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildAccount;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildAccountRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendReplicaChildAccountService {

    private final RecommendReplicaChildAccountRepository childAccountRepository;

    /**
     * 자녀 계정 생성 · 삭제를 복사본에 반영한다. 처음 받은 계정이면 만들고, 이미 있으면 삭제만 반영한다.
     */
    @Transactional
    public void applyChildAccountChanged(ChildAccountChangedEvent event) {
        ChildAccountChangedEvent.Data data = event.data();
        var info = new RecommendReplicaChildAccount.ChangeInfo(
            data.accountId(), data.childProfileId(), data.guardianId(), data.deleted(), event.occurredAt());
        childAccountRepository.findById(data.accountId()).ifPresentOrElse(
            stored -> stored.applyDeletion(info),
            () -> childAccountRepository.save(RecommendReplicaChildAccount.create(info)));
    }

    /**
     * Gateway가 자녀 토큰에서 붙인 계정 · 보호자로 계산할 자녀 프로필을 찾는다. 요청 값의 자녀 ID는 받지 않으므로 다른 자녀를 고를 수 없다.
     * 받은 적 없는 계정, 삭제된 계정, 보호자가 다른 계정은 403이다.
     */
    @Transactional(readOnly = true)
    public ChildCaller resolve(Long accountId, Long guardianId) {
        return childAccountRepository.findById(accountId)
            .flatMap(account -> account.childProfileIdFor(guardianId))
            .map(childProfileId -> new ChildCaller(guardianId, childProfileId))
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.CHILD_ACCOUNT_FORBIDDEN));
    }
}
