package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.ChildAccountChangedCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.ChildAccountCopy;
import com.bbcc.kidly.boundedcontext.notification.domain.ChildAccountCopyRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀 계정 복사본을 갱신하고, 자녀 프로필의 활성 계정을 찾는다.
 * 계정마다 있음 → 삭제됨 한 방향으로만 반영하므로, 같은 메시지를 다시 받거나 계정 사이 순서가 바뀌어도 결과가 같다.
 * 계정이 지워지면 그 계정의 자녀 알림도 함께 지운다(아동 정보는 필요한 만큼만 둔다).
 * 같은 계정의 이벤트는 같은 파티션(키 = 계정 ID)으로 한 건씩 오므로 같은 행을 동시에 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChildAccountService {

    private final ChildAccountCopyRepository childAccountCopyRepository;
    private final InboxNotificationService inboxNotificationService;

    @Transactional
    public void apply(ChildAccountChangedCommand command) {
        var existing = childAccountCopyRepository.findById(command.accountId());
        if (command.deleted()) {
            existing.ifPresentOrElse(ChildAccountCopy::delete, () -> childAccountCopyRepository.save(
                ChildAccountCopy.create(command.accountId(), command.childProfileId(), true)));
            inboxNotificationService.deleteAllOf(InboxRecipient.child(command.accountId()));
        } else if (existing.isEmpty()) {
            childAccountCopyRepository.save(ChildAccountCopy.create(command.accountId(), command.childProfileId(),
                false));
        }
    }

    /**
     * 자녀 프로필의 활성 계정 ID. 계정이 없거나 지워졌으면 비어 있다(자녀 알림을 만들지 않는다).
     */
    @Transactional(readOnly = true)
    public Optional<Long> findActiveAccountId(Long childProfileId) {
        return childAccountCopyRepository
            .findFirstByChildProfileIdAndDeletedFalseOrderByAccountIdDesc(childProfileId)
            .map(ChildAccountCopy::getAccountId);
    }
}
