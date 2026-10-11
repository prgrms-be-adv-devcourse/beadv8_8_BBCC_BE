package com.bbcc.kidly.global.eventpublisher;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 간 이벤트를 받는 메서드에 붙인다.
 * 발행한 쪽 트랜잭션이 커밋된 뒤에만 실행되고, 받는 쪽은 새 트랜잭션에서 처리한다.
 * 트랜잭션 밖에서 발행한 이벤트는 전달되지 않으므로 발행은 {@code @Transactional} 서비스 안에서 한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public @interface DomainEventListener {
}
