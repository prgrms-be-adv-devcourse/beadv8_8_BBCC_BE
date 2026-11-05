package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaInboxNotificationRepository
    extends JpaRepository<InboxNotification, Long>, InboxNotificationRepository {
}
