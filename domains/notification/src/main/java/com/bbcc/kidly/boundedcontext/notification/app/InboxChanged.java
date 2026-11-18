package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.shared.auth.PrincipalId;

public record InboxChanged(PrincipalId principal) {

    static InboxChanged of(InboxRecipient recipient) {
        return new InboxChanged(switch (recipient.type()) {
            case MEMBER -> PrincipalId.member(recipient.id());
            case CHILD -> PrincipalId.child(recipient.id());
        });
    }
}
